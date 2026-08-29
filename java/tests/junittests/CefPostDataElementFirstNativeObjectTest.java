// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.cef.network.CefPostDataElement;
import org.cef.network.CefPostDataElement.Type;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// Regression test for a real, serious finding: CefPostDataElement.create()
// crashes with a native FATAL if it is the very first native CEF object
// created in the process (no browser created first) -- and this reproduces
// in the RELEASE build, not just the Debug/coverage build issue #9 already
// tracks ("Crash 1" there is the analogous CefPrintSettings.create() case,
// documented as Debug-only; this shows the same class of bug is NOT
// Debug-only after all).
//
// Discovered by accident: CefPostDataTest.elementSetToEmptyFilePathLeaves
// ElementEmpty() was split into its own class to let the rest of
// CefPostDataTest rejoin the ENABLE_COVERAGE Debug-build gcovr measurement
// (that one method separately crashes there, see issue #9's 2nd update).
// The new class name (alphabetically early) changed JUnit5's default test
// class execution order enough that it became the very first test class to
// run in the whole suite -- meaning CefPostDataElement.create() became the
// first native CEF call in the process, with no browser ever created first.
// That crashed the *Release* build outright:
//   FATAL:cef/libcef_dll/cpptoc/post_data_element_cpptoc.cc:171]
//   CefPostDataElement_0_CppToC called with invalid version -1
// Confirmed reproducible twice in a row (not flaky/order-dependent luck --
// deterministic given this exact class list). The split was reverted (see
// plan/roadmap.md) rather than kept, since it also means the *existing*
// full suite's test order is silently load-bearing for correctness -- a
// real fragility worth its own issue regardless of the coverage-measurement
// goal that surfaced it.
//
// @Disabled because merely being alone in a process (as this isolated class
// necessarily is when run via --select-class) triggers the crash outright,
// not a slow/bounded failure -- there's no watchdog that can recover from a
// native FATAL. Do not remove @Disabled without confirming the fix first.
@Disabled("Known bug: CefPostDataElement.create() (and likely other *_N "
        + "value-object create() methods, e.g. CefPrintSettings per issue #9) "
        + "crashes if it's the first native CEF object created in the "
        + "process -- see Thrameos/java-cef#16")
@ExtendWith(TestSetupExtension.class)
class CefPostDataElementFirstNativeObjectTest {
    @Test
    void createAsFirstNativeObjectInProcessDoesNotCrash() {
        CefPostDataElement element = CefPostDataElement.create();
        assertEquals(Type.PDE_TYPE_EMPTY, element.getType());
        element.dispose();
    }
}
