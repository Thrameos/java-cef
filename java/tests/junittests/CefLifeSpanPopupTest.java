// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// Exercises native/life_span_handler.cpp's LifeSpanHandler::OnBeforePopup
// (0% covered -- no existing test triggers it), via a real window.open() call.
//
// Root cause of the original hang, now resolved: native/life_span_handler.cpp's
// OnBeforePopup has
//     if (browser->GetHost()->IsWindowRenderingDisabled()) {
//       // Cancel popups in off-screen rendering mode.
//       return true;
//     }
// at the very top, unconditionally returning before ever reaching the
// JNI_CALL_METHOD that would invoke Java's onBeforePopup(). Two synthetic-
// input techniques (a bare inline <script> at page load, then a real
// synthetic mouse click matching CefContextMenuTest's technique) and CEF's
// own test-only CefExecuteJavaScriptWithUserGestureForTests() (include/
// test/cef_test_helpers.h, bound for JCEF as CefTestHelper.java/
// native/CefTestHelper.cpp) were all tried against an OSR browser first --
// none of them helped, because the JNI dispatch is unreachable code under
// OSR no matter what triggers the JS. See git history for that investigation
// path (kept in the repeated task's own history) -- the user-gesture
// dimension was a red herring, but CefTestHelper's binding is still real,
// working, CEF-verified infrastructure, reused below to make the click
// deterministic instead of relying on window-focus/timing.
//
// Covering this requires a *windowed* (non-OSR) browser, which was itself
// blocked on CefBrowserWrTest's close hang -- see
// plan/tasks/20260903-03-issue3-windowed-close-onbeforeclose.md, now fixed
// and re-enabled. This test follows the same windowed-browser pattern.
//
// Note: running this class standalone still ends in a native SIGABRT during
// suite-level JVM shutdown (same symptom class as the already-tracked GH #10
// / #4/#23 shutdown crash -- see CefBrowserWrTest's own comment and
// plan/tasks/20260903-01-gh10-shutdown-sigsegv.md). That crash fires only
// after this test's own assertions have already completed and JUnit has
// reported it passing; it is unrelated to OnBeforePopup or popups.
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
    void clickingOpenerInvokesOnBeforePopupAndIsCancelled() {
        boolean[] fired = {false};
        String[] receivedUrl = {null};
        String[] receivedFrameName = {null};

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                addResource(TEST_URL, CONTENT, "text/html");
                createBrowser(TEST_URL, false /* useOSR */);
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
