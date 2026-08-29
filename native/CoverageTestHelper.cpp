// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

// Only compiled into the build when ENABLE_COVERAGE is on (see
// native/CMakeLists.txt) -- test/CI infrastructure only, not part of any normal
// build or the public API. See java-cef#4 / plan/findings.md: CEF's native
// shutdown reliably crashes in Debug builds (which coverage instrumentation
// requires), before gcov's exit-time flush can run. Explicitly flushing here, right
// before that known-crashing shutdown call, lets CI still collect real coverage
// data for everything that ran up to that point instead of losing it entirely.

#include <jni.h>

extern "C" void __gcov_dump(void);

extern "C" JNIEXPORT void JNICALL
Java_tests_junittests_CoverageTestHelper_N_1FlushCoverage(JNIEnv*, jclass) {
  __gcov_dump();
}
