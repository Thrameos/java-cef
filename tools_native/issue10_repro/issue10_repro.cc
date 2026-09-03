// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.
//
// Standalone C++ diagnostic, NOT part of the JCEF product build -- see
// tools_native/leak_probe's sibling comment for the general pattern this
// follows. Minimum repro for GH issue #10 (the long-running #4/#23 family):
// SIGSEGV in libc.so.6 during process shutdown, after everything else has
// already completed successfully.
//
// Deliberately mirrors native/context.cpp's EXACT init/pump/shutdown
// sequence -- windowless_rendering_enabled=true, external_message_pump=true,
// no CefRunMessageLoop()/multi_threaded_message_loop, a manual
// CefDoMessageLoopWork() pump loop instead (matching CefApp.java's ~30fps
// EDT Timer) -- unlike tools_native/leak_probe.cc, which deliberately uses
// the OPPOSITE mode (multi_threaded_message_loop=true, CEF drives its own
// thread) and has never reproduced this crash. That's the one meaningful
// divergence between "JCEF's real config" and every prior pure-C++ probe
// this investigation has tried -- this repro closes that gap.
//
// Sequence: CefInitialize() -> pump until OnContextInitialized -> create one
// OSR browser -> pump until OnAfterCreated -> pump a bit more (~1s, letting
// the initial about:blank navigation and any async browser-context setup
// settle, matching TestSetupExtension.warmUpBrowserProcess()'s real-page-
// load-then-settle shape loosely) -> CloseBrowser() -> pump until
// OnBeforeClose -> Context::Shutdown()'s exact 10x-pump-then-CefShutdown()
// sequence -> exit normally. If this crashes with no JNI/JVM in the loop at
// all, the bug is conclusively CEF/JCEF-native-side, not a JNI boundary
// issue. If it does NOT crash, the divergence is somewhere this repro still
// doesn't match -- compare against native/context.cpp and
// native/life_span_handler.cpp's OnBeforeClose handling next.

#include <unistd.h>

#include <atomic>
#include <chrono>
#include <cstdlib>
#include <ctime>
#include <iostream>
#include <string>
#include <thread>
#include <vector>

#include "include/cef_app.h"
#include "include/cef_browser.h"
#include "include/cef_client.h"

// Same facility libjcef.so itself uses -- see this repro's own CMakeLists.txt
// for how JCEF_ENABLE_TRACE gets defined here too. Trace strings below are
// deliberately worded to match native/context.cpp's/life_span_handler.cpp's/
// client_handler.cpp's real trace points exactly (see comments at each call
// site), so a real JCEF trace and this repro's trace can be diffed directly
// line-for-line at the points that exist in both.
#include "jcef_trace.h"

// NULL_PARAM_REPRO-style env-var control (see tools_native/null_param_repro
// for why an env var, not argv -- CEF's own CefMainArgs/CommandLine::Init
// mutates argv). ISSUE10_REPRO_MODE:
//   (unset)      -- baseline: everything on the process's initial thread.
//   "thread"     -- CefInitialize/pump/CreateBrowser/CefShutdown all run on
//                   a single spawned std::thread instead (tests "does it
//                   need to not be the process's initial thread").
//   "busythreads"-- like "thread", but N idle background threads (default
//                   24, ISSUE10_REPRO_NUM_THREADS to override) are spawned
//                   first and held alive for the whole run, each doing
//                   small periodic heap churn -- tests "does it need a
//                   many-threads glibc malloc-arena layout specifically",
//                   the leading untested hypothesis per this session's
//                   writeup (real JCEF runs inside a JVM process with
//                   20-30+ other threads; this repro alone never has).
//
// ISSUE10_REPRO_RACE_CLOSE=1 -- orthogonal to ISSUE10_REPRO_MODE above.
// The one "late close" variant not yet tried: instead of calling the late
// CloseBrowser(true) strictly BEFORE (normal explicit close) or AFTER
// (ISSUE10_REPRO_LATE_CLOSE) CefShutdown() on the same thread, spawn a
// genuinely different OS thread that calls it concurrently, timed to land
// at/around the moment the main thread calls CefShutdown() -- this is the
// shape of what a real JVM Finalizer thread does (a different thread
// calling in with zero ordering guarantee relative to CefApp.dispose()),
// which no prior sequential variant has actually modeled. Leading theory
// per GH issue #10: CloseBrowser() posts a task to the UI thread's task
// runner if not already on it (CEF_POST_TASK) -- a task posted at the
// exact moment CefShutdown() is tearing that runner down is a plausible
// concrete corruption mechanism a purely sequential test can't reach.
// ISSUE10_REPRO_RACE_DELAY_US optionally fixes the racer thread's
// pre-CloseBrowser() sleep (microseconds); default is randomized per run
// (seeded from time+pid) across [0, 5000) to sweep the timing window
// across repeated invocations rather than testing one fixed offset.

namespace {

// Matches native/context.cpp's OnContextInitialized() trace point exactly
// (that one fires from Context, a CefBrowserProcessHandler subclass's own
// OnContextInitialized -- see native/browser_process_handler.cpp).
class MinimalApp : public CefApp, public CefBrowserProcessHandler {
 public:
  CefRefPtr<CefBrowserProcessHandler> GetBrowserProcessHandler() override {
    return this;
  }
  void OnContextInitialized() override {
    JCEF_TRACE("OnContextInitialized() ENTER");
    JCEF_TRACE("OnContextInitialized() EXIT");
  }

  IMPLEMENT_REFCOUNTING(MinimalApp);
};

class MinimalRenderHandler : public CefRenderHandler {
 public:
  void GetViewRect(CefRefPtr<CefBrowser> browser, CefRect& rect) override {
    rect = CefRect(0, 0, 800, 600);
  }
  void OnPaint(CefRefPtr<CefBrowser> browser,
               PaintElementType type,
               const RectList& dirtyRects,
               const void* buffer,
               int width,
               int height) override {}

  IMPLEMENT_REFCOUNTING(MinimalRenderHandler);
};

class MinimalClient : public CefClient, public CefLifeSpanHandler {
 public:
  std::atomic<bool> after_created{false};
  std::atomic<bool> before_close{false};
  CefRefPtr<CefBrowser> browser;  // set from OnAfterCreated -- CreateBrowser()
                                  // is async, this is the only way to get the
                                  // real browser object, matching how real
                                  // JCEF only learns of it via this callback.

  CefRefPtr<CefRenderHandler> GetRenderHandler() override {
    return render_handler_;
  }
  CefRefPtr<CefLifeSpanHandler> GetLifeSpanHandler() override { return this; }

  void OnAfterCreated(CefRefPtr<CefBrowser> b) override {
    // Matches native/client_handler.cpp's + native/life_span_handler.cpp's
    // real OnAfterCreated trace points exactly.
    JCEF_TRACE("ClientHandler::OnAfterCreated() ENTER");
    JCEF_TRACE("LifeSpanHandler::OnAfterCreated() ENTER browser_id=%d",
               b->GetIdentifier());
    std::cout << "OnAfterCreated\n";
    browser = b;
    after_created = true;
  }
  void OnBeforeClose(CefRefPtr<CefBrowser> b) override {
    // Matches native/client_handler.cpp's + native/life_span_handler.cpp's
    // real OnBeforeClose trace points exactly.
    JCEF_TRACE("ClientHandler::OnBeforeClose() ENTER");
    JCEF_TRACE("LifeSpanHandler::OnBeforeClose() ENTER browser_id=%d",
               b->GetIdentifier());
    std::cout << "OnBeforeClose\n";
    before_close = true;
    JCEF_TRACE("LifeSpanHandler::OnBeforeClose() EXIT browser_id=%d",
               b->GetIdentifier());
  }

 private:
  CefRefPtr<MinimalRenderHandler> render_handler_ = new MinimalRenderHandler();

  IMPLEMENT_REFCOUNTING(MinimalClient);
};

// Matches native/context.cpp's DoMessageLoopWork() exactly, including its
// trace cadence (first 5 calls, then every 100th) -- a single shared call
// counter across the whole process, same as Context's own static
// call_count, so this repro's trace tail is directly comparable to a real
// JCEF trace's DoMessageLoopWork() call-number progression.
long long g_pump_call_count = 0;
void Pump() {
  ++g_pump_call_count;
  if (g_pump_call_count <= 5 || g_pump_call_count % 100 == 0) {
    JCEF_TRACE("DoMessageLoopWork() call #%lld", g_pump_call_count);
  }
  CefDoMessageLoopWork();
}

// Matches CefApp.java's ~30fps EDT Timer / Context::DoMessageLoopWork()'s
// caller -- pump for up to |timeout_ms|, checking |done| after every pump.
void PumpUntil(std::atomic<bool>& done, int timeout_ms) {
  int elapsed = 0;
  while (!done && elapsed < timeout_ms) {
    Pump();
    usleep(10 * 1000);  // ~10ms between pumps, finer-grained than the real
                        // ~33ms Timer -- fine, just needs to be "external,
                        // repeated, bounded" like the real one.
    elapsed += 10;
  }
}

// Idle background thread for "busythreads" mode -- never touches any CEF
// API (so it cannot violate CEF's single-UI-thread contract), just churns
// small heap allocations periodically to keep glibc's per-thread arena for
// this thread active for the run's duration.
std::atomic<bool> g_stop_busy_threads{false};
void BusyThreadLoop() {
  while (!g_stop_busy_threads) {
    std::vector<char> junk(4096);
    for (auto& c : junk) c = 0;
    std::this_thread::sleep_for(std::chrono::milliseconds(5));
  }
}

// The actual scenario -- every CEF UI-thread-affine call in here must run
// on the SAME thread, whichever thread calls this function. Returns the
// process exit code.
int RunScenario(CefMainArgs& main_args) {
  CefSettings settings;
  settings.no_sandbox = true;
  settings.windowless_rendering_enabled = true;
  settings.external_message_pump = true;
  // multi_threaded_message_loop left false (default) -- external pump mode,
  // matching native/context.cpp exactly.

  CefRefPtr<MinimalApp> app = new MinimalApp();
  JCEF_TRACE("Initialize() calling CefInitialize(), external_message_pump_=%d",
             1);
  if (!CefInitialize(main_args, settings, app.get(), nullptr)) {
    std::cerr << "CefInitialize failed, exit code " << CefGetExitCode()
              << "\n";
    return 1;
  }
  JCEF_TRACE("Initialize() EXIT res=%d", 1);
  std::cout << "CefInitialize returned true\n";

  // A few pumps to let OnContextInitialized-driven setup (temp window, etc,
  // matching Context::OnContextInitialized()) settle before creating a
  // browser -- real embedding apps have UI-driven delay here for free.
  for (int i = 0; i < 20; ++i) {
    Pump();
    usleep(10 * 1000);
  }

  CefRefPtr<MinimalClient> client = new MinimalClient();
  CefWindowInfo window_info;
  window_info.SetAsWindowless(0);
  // Matches native/CefBrowser_N.cpp's N_CreateBrowser exactly -- JCEF
  // requires Alloy runtime style for "normal" browsers (see that file's own
  // comment). Confirmed via direct code inspection this was NOT set in the
  // first version of this repro (which used the CEF default runtime style
  // instead, a real, confirmed divergence -- not a minor detail).
  window_info.runtime_style = CEF_RUNTIME_STYLE_ALLOY;
  CefBrowserSettings browser_settings;

  // Matches N_CreateBrowser exactly: the ASYNCHRONOUS CreateBrowser(), not
  // CreateBrowserSync() -- the first version of this repro used the sync
  // variant, another real, confirmed divergence. OnAfterCreated (below)
  // is what actually signals the browser is ready, same as real JCEF.
  bool create_result = CefBrowserHost::CreateBrowser(
      window_info, client.get(), "about:blank", browser_settings, nullptr,
      nullptr);
  if (!create_result) {
    std::cerr << "CreateBrowser() returned false\n";
    return 1;
  }
  std::cout << "CreateBrowser() returned true (async)\n";

  PumpUntil(client->after_created, 5000);
  if (!client->after_created) {
    std::cerr << "OnAfterCreated never fired within 5s\n";
    return 1;
  }

  // Settle period -- matches warmUpBrowserProcess()'s real-page-load-then-
  // settle shape loosely (no real page here, just letting async browser-
  // context setup finish, same rationale as that method's own comment).
  for (int i = 0; i < 100; ++i) {
    Pump();
    usleep(10 * 1000);
  }

  // ISSUE10_REPRO_LATE_CLOSE=1: per the user's direct suggestion (2026-08-31)
  // -- don't wait for GC/finalizer timing at all, just directly simulate
  // what CefBrowser_N.java's finalize() does (close(true), i.e.
  // GetHost()->CloseBrowser(true)) on an object that was NEVER explicitly
  // closed before shutdown, called AFTER CefShutdown() instead of before.
  // This is exactly the shape of a finalizer firing late: the Java wrapper
  // (here, just our own held CefRefPtr<CefBrowser>) was still reachable/
  // alive right up until the JVM's own GC discretion decided to collect and
  // finalize it -- if that happens to land after CefShutdown() has already
  // torn down CEF's UI-thread/task-runner bookkeeping, this is the call
  // that would touch it.
  bool late_close = getenv("ISSUE10_REPRO_LATE_CLOSE") != nullptr;
  bool race_close = getenv("ISSUE10_REPRO_RACE_CLOSE") != nullptr;
  CefRefPtr<CefBrowser> browser_ref;  // populated in late_close/race_close

  if (!late_close && !race_close) {
    client->browser->GetHost()->CloseBrowser(true);
    PumpUntil(client->before_close, 5000);
    if (!client->before_close) {
      std::cerr << "OnBeforeClose never fired within 5s\n";
      return 1;
    }
    client->browser = nullptr;
  } else {
    browser_ref = client->browser;  // keep alive across shutdown, deliberately
  }
  client = nullptr;

  // Racer thread: a genuinely different OS thread than whichever thread is
  // about to call CefShutdown() below. Started here (before the shutdown
  // sequence) and released to call CloseBrowser(true) after a short,
  // randomized (or fixed, via ISSUE10_REPRO_RACE_DELAY_US) sleep -- close
  // enough to CefShutdown()'s own call below to actually race it, since
  // both are started with no synchronization between them beyond this.
  std::thread racer;
  if (race_close) {
    int delay_us;
    if (const char* d = getenv("ISSUE10_REPRO_RACE_DELAY_US")) {
      delay_us = atoi(d);
    } else {
      unsigned seed = static_cast<unsigned>(time(nullptr)) ^
                      static_cast<unsigned>(getpid());
      srand(seed);
      delay_us = rand() % 5000;
    }
    std::cout << "race_close: racer thread will sleep " << delay_us
              << "us then call CloseBrowser(true)\n";
    racer = std::thread([browser_ref, delay_us] {
      usleep(delay_us);
      JCEF_TRACE(
          "Repro: racer thread calling browser->GetHost()->CloseBrowser(true), "
          "racing against CefShutdown()");
      browser_ref->GetHost()->CloseBrowser(true);
      JCEF_TRACE("Repro: racer thread's CloseBrowser(true) returned");
      std::cout << "racer thread: CloseBrowser(true) returned\n";
    });
  }

  // Matches Context::Shutdown() exactly, including its trace points.
  JCEF_TRACE("Shutdown() ENTER");
  for (int i = 0; i < 10; ++i) {
    Pump();
  }

  JCEF_TRACE("Shutdown() calling CefShutdown()...");
  std::cout << "Calling CefShutdown()...\n";
  CefShutdown();
  JCEF_TRACE("Shutdown() CefShutdown() returned");
  std::cout << "SURVIVED: CefShutdown() returned\n";

  if (racer.joinable()) {
    racer.join();
    std::cout << "SURVIVED: racer thread joined\n";
    browser_ref = nullptr;
  }

  if (late_close) {
    std::cout << "Now calling CloseBrowser(true) AFTER CefShutdown() -- "
                 "simulating a late finalizer...\n";
    JCEF_TRACE(
        "Repro: calling browser->GetHost()->CloseBrowser(true) AFTER "
        "CefShutdown() (simulating a late CefBrowser_N.finalize())");
    browser_ref->GetHost()->CloseBrowser(true);
    JCEF_TRACE("Repro: post-shutdown CloseBrowser(true) returned");
    std::cout << "SURVIVED: post-shutdown CloseBrowser(true) returned\n";
    browser_ref = nullptr;
  }

  // Per the user's "free after shutdown" hypothesis (2026-08-31): the
  // outstanding CefRefPtr<MinimalApp> `app` is still alive here and about
  // to be destroyed by an ordinary C++ local-variable/static-destructor
  // unwind as main() returns -- exactly the shape of "some reference
  // outlives shutdown, then benignly gets cleared, causing an order
  // problem." Trace its release explicitly so a hung/crashing destructor
  // shows up in the trace tail rather than after it.
  JCEF_TRACE("Repro: releasing CefRefPtr<MinimalApp> app after CefShutdown()");
  app = nullptr;
  JCEF_TRACE("Repro: app released, returning from RunScenario()");

  return 0;
}

}  // namespace

int main(int argc, char* argv[]) {
  CefMainArgs main_args(argc, argv);

  // Subprocess re-exec check must happen on this thread (whichever thread
  // owns process startup) before any mode dispatch -- matches every other
  // repro/example's structure, not mode-specific.
  int exit_code = CefExecuteProcess(main_args, nullptr, nullptr);
  if (exit_code >= 0) {
    return exit_code;
  }

  const char* mode = getenv("ISSUE10_REPRO_MODE");
  if (!mode || std::string(mode).empty()) {
    std::cout << "mode: baseline (main thread)\n";
    return RunScenario(main_args);
  }

  std::vector<std::thread> busy_threads;
  if (std::string(mode) == "busythreads") {
    int n = 24;
    if (const char* n_str = getenv("ISSUE10_REPRO_NUM_THREADS")) {
      n = atoi(n_str);
    }
    std::cout << "mode: busythreads (" << n << " idle threads + 1 CEF thread)\n";
    for (int i = 0; i < n; ++i) {
      busy_threads.emplace_back(BusyThreadLoop);
    }
  } else if (std::string(mode) == "thread") {
    std::cout << "mode: thread (single spawned thread for all CEF calls)\n";
  } else {
    std::cerr << "Unknown ISSUE10_REPRO_MODE: " << mode << "\n";
    return 2;
  }

  // Every CEF UI-thread-affine call (CefInitialize/DoMessageLoopWork/
  // CreateBrowser/CloseBrowser/CefShutdown) happens inside RunScenario(),
  // consistently on THIS one spawned thread -- matching CEF's real
  // contract (one consistent thread, not necessarily the process's main
  // thread -- exactly how real JCEF uses AWT-EventQueue-0, never the JVM's
  // actual main thread). main() itself never touches CEF again after this.
  int result = 0;
  std::thread cef_thread([&] { result = RunScenario(main_args); });
  cef_thread.join();

  g_stop_busy_threads = true;
  for (auto& t : busy_threads) t.join();

  return result;
}
