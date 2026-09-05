// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

// Only compiled into the build when ENABLE_LLVM_COVERAGE is on (see
// native/CMakeLists.txt) -- test/CI infrastructure only, not part of any
// normal build or the public API. See java-cef#4 / plan/findings.md: CEF's
// native shutdown reliably crashes in Debug builds (which coverage
// instrumentation requires), before the coverage runtime's own exit-time flush
// can run. Explicitly flushing here, right before that known-crashing shutdown
// call, lets CI still collect real coverage data for everything that ran up to
// that point instead of losing it entirely.
//
// Uses Clang's source-based coverage runtime (__llvm_profile_write_file) --
// see CMakeLists.txt's ENABLE_LLVM_COVERAGE option comment for why: each
// process gets its own raw profile file (LLVM_PROFILE_FILE's %p pattern
// embeds the PID), which sidesteps the multi-process-unsafety that gcov-style
// instrumentation would hit against CEF's zygote-style fork()s -- the same
// fix Chromium's own coverage builds use.

#include <jni.h>

#include <csignal>
#include <cstdlib>
#include <cstring>

extern "C" int __llvm_profile_write_file(void);
#define JCEF_COVERAGE_DUMP() __llvm_profile_write_file()

extern "C" JNIEXPORT void JNICALL
Java_tests_junittests_CoverageTestHelper_N_1FlushCoverage(JNIEnv*, jclass) {
  JCEF_COVERAGE_DUMP();
}

namespace {

// The explicit flush above only covers the one specific, previously-known
// crash point (CefApp.dispose()'s native shutdown -- see the file comment
// above). In practice this multi-session coverage effort has hit several
// *other*, unrelated native crashes too (mid-test, not just at shutdown --
// e.g. a pthread_mutex_lock SIGSEGV inside Context::DoMessageLoopWork() hit
// while measuring CefBrowser_N.cpp's SendKeyEvent coverage), each of which
// silently discarded that whole run's coverage counters: a SIGSEGV/SIGABRT
// bypasses both the explicit flush above and the coverage runtime's own
// atexit-registered flush, since neither runs on abnormal termination.
// Rather than special-case each crash as it's discovered, install a signal
// handler once (only in this ENABLE_LLVM_COVERAGE-only file, loaded
// automatically via the
// constructor attribute below -- no Java-side wiring needed) that dumps
// whatever counters exist so far before the crash takes down the process,
// so a mid-run crash costs that run's *remaining* coverage, not everything
// collected up to that point.
//
// The JVM installs its own sigaction-based handlers for these same signals
// at startup (that's what produces hs_err_pid*.log). This library loads
// via System.loadLibrary(), well after that -- so this constructor runs
// *after* the JVM's handlers are already in place, and a naive
// signal()-based install here would silently replace them, permanently
// losing hs_err generation for the rest of the process's life (confirmed
// directly: an early version of this handler that did `signal(signum,
// SIG_DFL); raise(signum);` produced no hs_err_pid log on a real crash,
// where one had always appeared before). Saving the previously-installed
// sigaction and re-invoking *it* (not SIG_DFL) after flushing preserves
// the JVM's own crash reporting exactly as before, with a gcov dump now
// also happening first.
struct sigaction g_previous_action[NSIG];

void FlushCoverageOnCrash(int signum, siginfo_t* info, void* context) {
  JCEF_COVERAGE_DUMP();

  struct sigaction& previous = g_previous_action[signum];
  if (previous.sa_flags & SA_SIGINFO) {
    if (previous.sa_sigaction) {
      previous.sa_sigaction(signum, info, context);
      return;
    }
  } else if (previous.sa_handler != SIG_DFL && previous.sa_handler != SIG_IGN
             && previous.sa_handler != nullptr) {
    previous.sa_handler(signum);
    return;
  }

  // No real previous handler (SIG_DFL/SIG_IGN/unset) -- fall through to the
  // actual OS default action ourselves.
  std::signal(signum, SIG_DFL);
  std::raise(signum);
}

void InstallOne(int signum) {
  struct sigaction action;
  std::memset(&action, 0, sizeof(action));
  action.sa_sigaction = FlushCoverageOnCrash;
  action.sa_flags = SA_SIGINFO;
  sigemptyset(&action.sa_mask);
  sigaction(signum, &action, &g_previous_action[signum]);
}

void __attribute__((constructor)) InstallCoverageCrashHandler() {
  // Deliberately NOT SIGABRT: TestSetupExtension.close() already calls
  // Java_..._CoverageTestHelper_N_1FlushCoverage() (a normal, non-signal-
  // context dump call, safe to use the coverage runtime's file I/O) right
  // before the one well-understood, expected SIGABRT this codebase hits on
  // every coverage run (CefApp.dispose()'s native-shutdown DCHECK -- see
  // the file comment above). Confirmed directly (with the earlier gcov-based
  // instrumentation this file originally used, which had the same hazard):
  // adding SIGABRT here too made even a *successful*, crash-free-per-JUnit
  // test run show 0% coverage for the file it touched -- a second, signal-
  // context dump call (async-signal-unsafe: the coverage runtime's dump
  // internally does buffered file I/O) firing microseconds after the first,
  // safe one corrupts the very profile file the safe call just wrote. This
  // handler exists for the *other* crash signals that have no such existing
  // coverage, which never double-fire against the safe path.
  InstallOne(SIGSEGV);
  InstallOne(SIGBUS);
  InstallOne(SIGILL);
  InstallOne(SIGFPE);
}

}  // namespace
