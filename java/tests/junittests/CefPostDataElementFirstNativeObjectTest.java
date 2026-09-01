// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.cef.network.CefPostDataElement;
import org.cef.network.CefPostDataElement.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// Regression test for issue #16: CefPostDataElement.create() (and likely
// other *_N value-object create() methods) crashed with a native FATAL
// ("CppToC called with invalid version -1") if it was the very first native
// CEF object created in the process, with no browser ever created first --
// reproduced in the RELEASE build, not just a Debug/coverage build.
//
// Root cause: this repo's real default (windowless_rendering_enabled=true,
// external_message_pump mode) drives CEF's browser-process/IO-thread
// startup forward only via explicit doMessageLoopWork() calls -- some CEF-
// internal state only actually finishes initializing as a side effect of
// creating a browser, which every real embedding app does before touching
// any of these value-object types anyway. FIXED by making
// TestSetupExtension.initialize()'s warmUpBrowserProcess() call
// unconditional (previously only ran for the isolated leak-sweep case) --
// see that method's own comment for the full mechanism and the pure-C++
// evidence (tools_native/leak_probe.cc, tools_native/null_param_repro/)
// that isolates it. Verified: this test now passes 3/3 in complete
// isolation (`--select-class`, guaranteeing no other browser could have run
// first), and the full suite still passes clean (187/187) with the fix
// applied.
@ExtendWith(TestSetupExtension.class)
class CefPostDataElementFirstNativeObjectTest {
    @Test
    void createAsFirstNativeObjectInProcessDoesNotCrash() {
        CefPostDataElement element = CefPostDataElement.create();
        assertEquals(Type.PDE_TYPE_EMPTY, element.getType());
        element.dispose();
    }
}
