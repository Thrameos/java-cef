// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// Exercises native/life_span_handler.cpp's LifeSpanHandler::OnBeforePopup
// (0% covered -- no existing test triggers it), via a real window.open() call.
//
// @Disabled: root-caused, and it's not fixable by changing anything about how
// this test triggers window.open(). Two synthetic-input techniques were tried
// first (a bare inline <script> at page load, then a real synthetic mouse
// click matching CefContextMenuTest's technique) and both hung. A diagnostic
// (document.title changes, observed via CefDisplayHandler.onTitleChange)
// showed the synthetic click *does* land and *does* run the onclick handler.
// Suspecting Blink's user-activation tracking wasn't satisfied by synthetic
// OSR input, CEF's own test-only CefExecuteJavaScriptWithUserGestureForTests()
// (include/test/cef_test_helpers.h -- see life_span_unittest.cc in
// ~/devel/cef/tests/ceftests/, which uses exactly this instead of synthetic
// input) was bound for JCEF (CefTestHelper.java/native/CefTestHelper.cpp) and
// tried too. Same result: the JS ran to completion (title changed all the way
// to 'clicked'), yet OnBeforePopup still never fired.
//
// The real root cause: native/life_span_handler.cpp's OnBeforePopup has
//     if (browser->GetHost()->IsWindowRenderingDisabled()) {
//       // Cancel popups in off-screen rendering mode.
//       return true;
//     }
// at the very top, unconditionally returning before ever reaching the
// JNI_CALL_METHOD that would invoke Java's onBeforePopup(). window.open() DID
// fire in the renderer (confirmed by the title-change diagnostic completing);
// CEF's browser-process side simply never surfaces it to JCEF for OSR
// browsers. This has nothing to do with user gestures -- that whole
// investigation path was a red herring. Since createBrowser(..., true) (OSR)
// is what every popup test uses, this JNI dispatch is unreachable code under
// OSR no matter what triggers the JS. Covering it requires a *windowed*
// (non-OSR) browser -- but CefBrowserWrTest (added specifically as the first
// windowed-browser test) found windowed browsers hang on close, so this test
// is blocked on that same root cause. See plan/roadmap.md's NEXT-UP PLAN item
// 2 and CefBrowserWrTest's own @Disabled note.
//
// CefTestHelper's user-gesture binding is left in place (it's real, working,
// CEF-verified infrastructure -- confirmed executing JS under a genuine faked
// gesture) even though it turned out not to be this test's actual blocker; it
// may still be needed for other user-activation-gated coverage later (e.g.
// onbeforeunload dialogs).
@ExtendWith(TestSetupExtension.class)
class CefLifeSpanPopupTest {
    private static final String TEST_URL = "http://test.com/life_span_popup.html";
    private static final String POPUP_URL = "http://test.com/popup_target.html";
    private static final String CONTENT = "<html><body>"
            + "<button id='opener' style='position:absolute; left:5px; top:5px; "
            + "width:100px; height:50px;' onclick=\"window.open('" + POPUP_URL
            + "', 'mypopup')\">open</button>"
            + "</body></html>";

    @Test
    @Disabled("OnBeforePopup is unreachable for OSR browsers -- native/life_span_handler.cpp "
            + "unconditionally cancels+returns before the JNI dispatch when "
            + "IsWindowRenderingDisabled(). Needs a windowed browser, which is itself blocked "
            + "on CefBrowserWrTest's close hang. See the class-level comment.")
    void clickingOpenerInvokesOnBeforePopupAndIsCancelled() {
        boolean[] fired = {false};
        String[] receivedUrl = {null};
        String[] receivedFrameName = {null};

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                addResource(TEST_URL, CONTENT, "text/html");
                createBrowser(TEST_URL, true /* useOSR */);
                super.setupTest();
            }

            @Override
            public boolean onBeforePopup(
                    CefBrowser browser, CefFrame frame, String targetUrl, String targetFrameName) {
                fired[0] = true;
                receivedUrl[0] = targetUrl;
                receivedFrameName[0] = targetFrameName;
                terminateTest();
                return true; // Cancel -- no second browser is ever created.
            }

            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                    boolean canGoBack, boolean canGoForward) {
                if (isLoading) return;
                CefTestHelper.executeJavaScriptWithUserGestureForTests(
                        browser.getMainFrame(), "document.getElementById('opener').click()");
            }
        };

        frame.awaitCompletion();

        assertTrue(fired[0], "onBeforePopup never fired for the click on #opener");
        assertEquals(POPUP_URL, receivedUrl[0]);
        assertEquals("mypopup", receivedFrameName[0]);
    }
}
