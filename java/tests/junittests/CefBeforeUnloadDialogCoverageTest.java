// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefJSDialogCallback;
import org.cef.handler.CefJSDialogHandlerAdapter;
import org.cef.handler.CefJSDialogHandler.JSDialogType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// Coverage for jsdialog_handler.cpp's OnBeforeUnloadDialog (0% covered --
// CefJSDialogHandlerTest only exercises the alert()/OnJSDialog path). Per
// ~/devel/cef/tests/ceftests/jsdialog_unittest.cc's OnBeforeUnloadRunImmediate,
// firing window.onbeforeunload requires a real user gesture on the page
// (https://crbug.com/707007) before navigating away -- this uses CEF's own
// test-only CefExecuteJavaScriptWithUserGestureForTests helper (already
// wrapped by CefTestHelper, see CefPermissionPromptCoverageTest) rather than
// synthetic mouse/keyboard input.
//
// Uses a dedicated TestFrame-based browser rather than the shared-browser
// (Tier 1) harness -- navigating the long-lived shared browser away mid-suite
// via an onbeforeunload-guarded navigation was flagged as too invasive to
// verify safely across the whole suite (see
// plan/tasks/20260903-16-uncovered-paths-cover-or-disable.md's "Not done"
// section); a throwaway browser sidesteps that entirely.
@ExtendWith(TestSetupExtension.class)
class CefBeforeUnloadDialogCoverageTest {
    private static final String START_URL = "https://before-unload-test/start.html";
    private static final String END_URL = "https://before-unload-test/end.html";
    private static final String START_CONTENT = "<html><body><script>"
            + "window.onbeforeunload = function() { return 'stay?'; };"
            + "</script></body></html>";
    private static final String END_CONTENT = "<html><body>END</body></html>";

    private static class BeforeUnloadTestFrame extends TestFrame {
        final CountDownLatch dialogSeen = new CountDownLatch(1);
        final CountDownLatch navigatedAway = new CountDownLatch(1);
        boolean sawReload;

        @Override
        protected void setupTest() {
            addResource(START_URL, START_CONTENT, "text/html");
            addResource(END_URL, END_CONTENT, "text/html");

            client_.addJSDialogHandler(new CefJSDialogHandlerAdapter() {
                @Override
                public boolean onBeforeUnloadDialog(CefBrowser browser, String message_text,
                        boolean is_reload, CefJSDialogCallback callback) {
                    sawReload = is_reload;
                    dialogSeen.countDown();
                    callback.Continue(true, null);
                    return true;
                }
            });

            createBrowser(START_URL, true /* useOSR */);
        }

        @Override
        public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
            if (!frame.isMain()) return;
            String url = frame.getURL();
            if (START_URL.equals(url)) {
                // A synthetic user gesture is required before onbeforeunload will
                // fire (Blink blocks it otherwise) -- see class comment.
                CefTestHelper.executeJavaScriptWithUserGestureForTests(frame, "");
                browser.loadURL(END_URL);
            } else if (END_URL.equals(url)) {
                navigatedAway.countDown();
            }
        }
    }

    @Test
    void navigatingAwayInvokesOnBeforeUnloadDialogAndResumesAfterContinue() {
        BeforeUnloadTestFrame frame = new BeforeUnloadTestFrame();
        try {
            assertTrue(frame.dialogSeen.await(10, TimeUnit.SECONDS),
                    "onBeforeUnloadDialog never fired");
            assertTrue(frame.navigatedAway.await(10, TimeUnit.SECONDS),
                    "Navigation never resumed after callback.Continue()");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertTrue(!frame.sawReload, "is_reload should be false for a cross-page navigation");
        frame.terminateTest();
        frame.awaitCompletion();
    }
}
