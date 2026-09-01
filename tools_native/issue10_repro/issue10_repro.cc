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

#include <atomic>
#include <iostream>

#include "include/cef_app.h"
#include "include/cef_browser.h"
#include "include/cef_client.h"

namespace {

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
    std::cout << "OnAfterCreated\n";
    browser = b;
    after_created = true;
  }
  void OnBeforeClose(CefRefPtr<CefBrowser> b) override {
    std::cout << "OnBeforeClose\n";
    before_close = true;
  }

 private:
  CefRefPtr<MinimalRenderHandler> render_handler_ = new MinimalRenderHandler();

  IMPLEMENT_REFCOUNTING(MinimalClient);
};

// Matches CefApp.java's ~30fps EDT Timer / Context::DoMessageLoopWork()'s
// caller -- pump for up to |timeout_ms|, checking |done| after every pump.
void PumpUntil(std::atomic<bool>& done, int timeout_ms) {
  int elapsed = 0;
  while (!done && elapsed < timeout_ms) {
    CefDoMessageLoopWork();
    usleep(10 * 1000);  // ~10ms between pumps, finer-grained than the real
                        // ~33ms Timer -- fine, just needs to be "external,
                        // repeated, bounded" like the real one.
    elapsed += 10;
  }
}

}  // namespace

int main(int argc, char* argv[]) {
  CefMainArgs main_args(argc, argv);

  int exit_code = CefExecuteProcess(main_args, nullptr, nullptr);
  if (exit_code >= 0) {
    return exit_code;
  }

  CefSettings settings;
  settings.no_sandbox = true;
  settings.windowless_rendering_enabled = true;
  settings.external_message_pump = true;
  // multi_threaded_message_loop left false (default) -- external pump mode,
  // matching native/context.cpp exactly.

  if (!CefInitialize(main_args, settings, nullptr, nullptr)) {
    std::cerr << "CefInitialize failed, exit code " << CefGetExitCode()
              << "\n";
    return 1;
  }
  std::cout << "CefInitialize returned true\n";

  // A few pumps to let OnContextInitialized-driven setup (temp window, etc,
  // matching Context::OnContextInitialized()) settle before creating a
  // browser -- real embedding apps have UI-driven delay here for free.
  for (int i = 0; i < 20; ++i) {
    CefDoMessageLoopWork();
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
    CefDoMessageLoopWork();
    usleep(10 * 1000);
  }

  client->browser->GetHost()->CloseBrowser(true);
  PumpUntil(client->before_close, 5000);
  if (!client->before_close) {
    std::cerr << "OnBeforeClose never fired within 5s\n";
    return 1;
  }

  client->browser = nullptr;
  client = nullptr;

  // Matches Context::Shutdown()'s exact sequence: 10x pump, then
  // CefShutdown() with no further delay.
  for (int i = 0; i < 10; ++i) {
    CefDoMessageLoopWork();
  }

  std::cout << "Calling CefShutdown()...\n";
  CefShutdown();
  std::cout << "SURVIVED: CefShutdown() returned\n";

  return 0;
}
