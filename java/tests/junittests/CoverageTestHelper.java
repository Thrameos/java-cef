// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

// Test/CI infrastructure only -- not part of the public API. See
// native/CoverageTestHelper.cpp and java-cef#4 / plan/findings.md.
class CoverageTestHelper {
    // Flushes gcov coverage data, if this was built with -DENABLE_COVERAGE=ON (the
    // native method only exists in that build configuration -- see
    // native/CMakeLists.txt). A no-op, not an error, on any other build: libjcef.so
    // is already loaded by this point (via org.cef.CefApp), so an
    // UnsatisfiedLinkError here means coverage instrumentation wasn't compiled in,
    // not that anything is wrong.
    static void flush() {
        try {
            N_FlushCoverage();
        } catch (UnsatisfiedLinkError e) {
            // Expected on a non-coverage build.
        }
    }

    private static native void N_FlushCoverage();
}
