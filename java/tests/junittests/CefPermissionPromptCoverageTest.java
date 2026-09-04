// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.CefApp;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefPermissionPromptCallback;
import org.cef.handler.CefPermissionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// Coverage for CefPermissionHandler.onShowPermissionPrompt/onDismissPermissionPrompt
// (GH #33): CefPermissionHandlerCoverageTest only exercises
// OnRequestMediaAccessPermission, leaving permission_handler.cpp's
// OnShowPermissionPrompt and OnDismissPermissionPrompt at 0%. Per
// ~/devel/cef/tests/ceftests/permission_prompt_unittest.cc, the Window
// Management permission (window.getScreenDetails()) is the reference
// scenario for this path, and (unlike getUserMedia) it requires a real user
// gesture -- Blink blocks it outright ("NotAllowedError: Transient
// activation is required...") without one.
//
// Rather than synthetic mouse/keyboard input (documented elsewhere in this
// suite -- CefContextMenuTest/CefDisplayHandlerCoverageTest -- as unreliable
// for satisfying Blink's user-activation tracking), this uses
// CefTestHelper.executeJavaScriptWithUserGestureForTests, CEF's own
// test-only helper for exactly this problem (see task
// 20260903-06-issue11-user-gesture-test-helper.md).
@ExtendWith(TestSetupExtension.class)
class CefPermissionPromptCoverageTest {
    private static final String URL = "https://permission-prompt-test/prompt.html";
    private static final String ORIGIN = "https://permission-prompt-test/";
    private static final String CONTENT = "<html><body>permission prompt test</body></html>";

    // cef_permission_request_types_t::CEF_PERMISSION_TYPE_WINDOW_MANAGEMENT
    // (include/internal/cef_types.h). JCEF does not expose the full
    // cef_permission_request_types_t enum as Java constants (unlike
    // MediaAccessPermissionType/PermissionRequestResult) -- this single flag
    // is test-local rather than growing the public API for one coverage
    // test.
    private static final int PERMISSION_TYPE_WINDOW_MANAGEMENT = 1 << 23;

    private static final String TRIGGER_SCRIPT = "window.getScreenDetails().catch(function() {});";

    private static class PermissionPromptTestFrame extends TestFrame {
        final CountDownLatch prompted = new CountDownLatch(1);
        final CountDownLatch dismissed = new CountDownLatch(1);
        String seenOrigin;
        int seenPermissions;
        long seenPromptId = -1;
        long dismissPromptId = -1;
        int dismissResult = -1;

        final boolean handlePrompt;
        final int result;

        PermissionPromptTestFrame(boolean handlePrompt, int result) {
            this.handlePrompt = handlePrompt;
            this.result = result;
        }

        @Override
        protected void setupTest() {
            addResource(URL, CONTENT, "text/html");
            client_.addPermissionHandler(new CefPermissionHandler() {
                @Override
                public boolean onRequestMediaAccessPermission(CefBrowser browser, CefFrame frame,
                        String requestingOrigin, int requestedPermissions,
                        org.cef.callback.CefMediaAccessCallback callback) {
                    return false;
                }

                @Override
                public boolean onShowPermissionPrompt(CefBrowser browser, long promptId,
                        String requestingOrigin, int requestedPermissions,
                        CefPermissionPromptCallback callback) {
                    seenOrigin = requestingOrigin;
                    seenPermissions = requestedPermissions;
                    seenPromptId = promptId;
                    if (handlePrompt) {
                        callback.Continue(result);
                    }
                    prompted.countDown();
                    // false here means "proceed with default handling" (implicit
                    // IGNORE, no dismiss callback) -- exercises
                    // permission_handler.cpp's jresult==false / SetTemporary()
                    // branch on the prompt callback, distinct from the
                    // jresult==true branch taken when handlePrompt is true.
                    return handlePrompt;
                }

                @Override
                public void onDismissPermissionPrompt(
                        CefBrowser browser, long promptId, int result) {
                    dismissPromptId = promptId;
                    dismissResult = result;
                    dismissed.countDown();
                }
            });

            org.cef.browser.CefRequestContext context =
                    org.cef.browser.CefRequestContext.createContext(
                            (org.cef.handler.CefRequestContextHandler) null);
            browser_ = client_.createBrowser(
                    URL, true /* useOSR */, false /* isTransparent */, context);
            getContentPane().add(browser_.getUIComponent(), java.awt.BorderLayout.CENTER);
            pack();
            setSize(800, 600);
            setVisible(true);
        }

        @Override
        public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
            if (!frame.isMain() || !URL.equals(frame.getURL())) return;
            CefTestHelper.executeJavaScriptWithUserGestureForTests(frame, TRIGGER_SCRIPT);
        }
    }

    @Test
    void promptAcceptedFiresDismissWithAcceptResult() {
        PermissionPromptTestFrame frame = new PermissionPromptTestFrame(
                true /* handlePrompt */, CefPermissionHandler.PermissionRequestResult.ACCEPT);
        try {
            assertTrue(frame.prompted.await(10, TimeUnit.SECONDS),
                    "onShowPermissionPrompt never fired");
            assertTrue(frame.dismissed.await(10, TimeUnit.SECONDS),
                    "onDismissPermissionPrompt never fired");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertEquals(ORIGIN, frame.seenOrigin);
        assertEquals(PERMISSION_TYPE_WINDOW_MANAGEMENT, frame.seenPermissions);
        assertTrue(frame.seenPromptId > 0);
        assertEquals(frame.seenPromptId, frame.dismissPromptId);
        assertEquals(CefPermissionHandler.PermissionRequestResult.ACCEPT, frame.dismissResult);
        frame.terminateTest();
        frame.awaitCompletion();
    }

    @Test
    void promptDeniedFiresDismissWithDenyResult() {
        PermissionPromptTestFrame frame = new PermissionPromptTestFrame(
                true /* handlePrompt */, CefPermissionHandler.PermissionRequestResult.DENY);
        try {
            assertTrue(frame.prompted.await(10, TimeUnit.SECONDS),
                    "onShowPermissionPrompt never fired");
            assertTrue(frame.dismissed.await(10, TimeUnit.SECONDS),
                    "onDismissPermissionPrompt never fired");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertEquals(ORIGIN, frame.seenOrigin);
        assertEquals(CefPermissionHandler.PermissionRequestResult.DENY, frame.dismissResult);
        frame.terminateTest();
        frame.awaitCompletion();
    }

    // onShowPermissionPrompt returning false takes CEF's default handling
    // (implicit IGNORE) and never invokes onDismissPermissionPrompt -- see
    // CefPermissionHandler.onShowPermissionPrompt's own javadoc. Exercises
    // permission_handler.cpp's jresult==false branch on the prompt callback.
    @Test
    void promptNotHandledNeverFiresDismiss() {
        PermissionPromptTestFrame frame = new PermissionPromptTestFrame(
                false /* handlePrompt */, CefPermissionHandler.PermissionRequestResult.ACCEPT);
        try {
            assertTrue(frame.prompted.await(10, TimeUnit.SECONDS),
                    "onShowPermissionPrompt never fired");
            assertTrue(frame.dismissed.await(2, TimeUnit.SECONDS) == false,
                    "onDismissPermissionPrompt should not fire when the prompt is unhandled");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertEquals(ORIGIN, frame.seenOrigin);
        frame.terminateTest();
        frame.awaitCompletion();
    }
}
