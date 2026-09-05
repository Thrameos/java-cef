// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

// Permanent, always-available native instrumentation facility -- the lesson
// carried over from jpype's own jp_tracer.h: don't hand-add and later remove
// fprintf() calls each time a new native crash needs investigating (this repo
// did that repeatedly across many sessions -- see plan/findings.md). Instead,
// place JCEF_TRACE() calls at the lifecycle/teardown points that matter once,
// and leave them in the codebase permanently.
//
// Two independent gates keep this at zero cost everywhere except an explicit
// investigation:
//   1. Compile-time: JCEF_TRACE() expands to nothing at all unless the build
//      was configured with -DJCEF_ENABLE_TRACE=ON (see the JCEF_ENABLE_TRACE
//      CMake option in the top-level CMakeLists.txt). Off by default, exactly
//      like ENABLE_COVERAGE.
//   2. Runtime: even in a JCEF_ENABLE_TRACE build, each call first checks the
//      JCEF_TRACE environment variable (cached after the first check) and is
//      a no-op unless it's set -- so a JCEF_ENABLE_TRACE build behaves
//      identically to a normal one until a session deliberately opts in.
//
// Usage: JCEF_TRACE("Shutdown() ENTER"); or with printf-style args:
// JCEF_TRACE("DoMessageLoopWork() call #%lld", call_count);
//
// Output goes to stderr, one line per call, prefixed with a millisecond
// wall-clock timestamp (steady_clock, not wall-clock-adjustable) so traces
// from different subsystems can be interleaved and ordered by hand.

#ifndef JCEF_NATIVE_JCEF_TRACE_H_
#define JCEF_NATIVE_JCEF_TRACE_H_
#pragma once

#if defined(JCEF_ENABLE_TRACE)

#include <chrono>
#include <cstdio>
#include <cstdlib>

namespace jcef_trace {

inline bool Enabled() {
  static const bool enabled = (std::getenv("JCEF_TRACE") != nullptr);
  return enabled;
}

inline long long NowMs() {
  return std::chrono::duration_cast<std::chrono::milliseconds>(
             std::chrono::steady_clock::now().time_since_epoch())
      .count();
}

}  // namespace jcef_trace

#define JCEF_TRACE(fmt, ...)                                              \
  do {                                                                    \
    if (jcef_trace::Enabled()) {                                          \
      fprintf(stderr, "[jcef-trace %lld] " fmt "\n", jcef_trace::NowMs(), \
              ##__VA_ARGS__);                                             \
      fflush(stderr);                                                     \
    }                                                                     \
  } while (0)

#else  // !defined(JCEF_ENABLE_TRACE)

#define JCEF_TRACE(fmt, ...) \
  do {                       \
  } while (0)

#endif  // defined(JCEF_ENABLE_TRACE)

#endif  // JCEF_NATIVE_JCEF_TRACE_H_
