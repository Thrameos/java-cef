// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import org.cef.browser.CefFrame;
import org.cef.callback.CefNative;

// Test/CI infrastructure only -- not part of the public API. Wraps CEF's
// test-only C++ helper CefExecuteJavaScriptWithUserGestureForTests()
// (include/test/cef_test_helpers.h), which JCEF does not otherwise expose
// anywhere in its Java API. CEF's own ceftests suite uses this to fake a
// real user gesture for functionality gated on Blink's user-activation
// tracking (window.open() popups, onbeforeunload dialogs, ...) -- see
// life_span_unittest.cc/jsdialog_unittest.cc -- instead of relying on
// synthetic mouse/keyboard input, which does not reliably satisfy that
// tracking (see CefLifeSpanPopupTest's history/native/CefTestHelper.cpp).
class CefTestHelper {
    // |frame| must be the real CefFrame_N JCEF hands out (it implements the
    // public org.cef.callback.CefNative interface, which is how its native
    // handle is reached from here without needing package access to
    // CefFrame_N itself).
    static void executeJavaScriptWithUserGestureForTests(CefFrame frame, String javascript) {
        long nativeRef = ((CefNative) frame).getNativeRef(null);
        N_ExecuteJavaScriptWithUserGestureForTests(nativeRef, javascript);
    }

    private static native void N_ExecuteJavaScriptWithUserGestureForTests(
            long frameNativeRef, String javascript);
}
