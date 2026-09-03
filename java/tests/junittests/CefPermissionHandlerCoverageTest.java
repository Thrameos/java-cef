// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.CefApp;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefRequestContext;
import org.cef.callback.CefMediaAccessCallback;
import org.cef.handler.CefPermissionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// Coverage for the CefPermissionHandler binding (GH #33): 0% covered before
// this test -- native/permission_handler.cpp's OnRequestMediaAccessPermission
// had never been exercised at all. Per this project's own reference
// (~/devel/cef/tests/ceftests/media_access_unittest.cc): getUserMedia() does
// NOT require a user gesture (unlike window.getScreenDetails(), which does --
// deliberately avoided here since synthetic-gesture delivery is documented
// elsewhere in this suite as unreliable, see CefContextMenuTest/
// CefDisplayHandlerCoverageTest), so the request can be triggered directly
// from a <script> on page load. --use-fake-device-for-media-stream
// (TestSetupExtension) supplies a synthetic camera/mic so this works on a
// CI agent with no real hardware, WITHOUT bypassing CefPermissionHandler --
// the request still reaches onRequestMediaAccessPermission and must be
// explicitly resolved.
//
// Each test uses its own TestFrame + a fresh in-memory CefRequestContext
// (matching the ceftest reference's own per-scenario CefRequestContext,
// rather than SharedBrowserExtension's long-lived shared one) so that
// accept/deny results from one scenario can't leak into another via
// per-origin permission state persisted in a shared profile.
@ExtendWith(TestSetupExtension.class)
class CefPermissionHandlerCoverageTest {
    private static final String URL = "https://permission-test/media.html";
    private static final String ORIGIN = "https://permission-test/";
    private static final String CONTENT = "<html><body><script>"
            + "navigator.mediaDevices.getUserMedia({audio: true, video: true})"
            + ".then(function() {})"
            + ".catch(function() {});"
            + "</script></body></html>";

    private static class PermissionTestFrame extends TestFrame {
        final CountDownLatch requested = new CountDownLatch(1);
        String seenOrigin;
        int seenPermissions;
        boolean accept;

        PermissionTestFrame(boolean accept) {
            this.accept = accept;
        }

        @Override
        protected void setupTest() {
            addResource(URL, CONTENT, "text/html");
            client_.addPermissionHandler(new CefPermissionHandler() {
                @Override
                public boolean onRequestMediaAccessPermission(CefBrowser browser,
                        CefFrame frame, String requestingOrigin, int requestedPermissions,
                        CefMediaAccessCallback callback) {
                    seenOrigin = requestingOrigin;
                    seenPermissions = requestedPermissions;
                    if (accept) {
                        callback.Continue(requestedPermissions);
                    }
                    requested.countDown();
                    // Per this method's own contract: true means "handled here"
                    // (accept path, callback used); false means "proceed with
                    // default handling" (deny path, callback intentionally
                    // unused) -- exercises permission_handler.cpp's other
                    // branch (jresult==false -> jcallback.SetTemporary()).
                    return accept;
                }

                @Override
                public boolean onShowPermissionPrompt(CefBrowser browser, long promptId,
                        String requestingOrigin, int requestedPermissions,
                        org.cef.callback.CefPermissionPromptCallback callback) {
                    return false;
                }

                @Override
                public void onDismissPermissionPrompt(
                        CefBrowser browser, long promptId, int result) {}
            });

            CefRequestContext context =
                    CefRequestContext.createContext((org.cef.handler.CefRequestContextHandler) null);
            // Bypasses TestFrame.createBrowser(String, boolean) (no CefRequestContext
            // overload) -- must replicate its UI-attachment steps here, otherwise
            // the browser never actually attaches to a visible window and
            // navigation silently never completes (found by direct debugging: no
            // resource request ever reached getResourceHandler()).
            browser_ = client_.createBrowser(URL, true /* useOSR */, false /* isTransparent */,
                    context);
            getContentPane().add(browser_.getUIComponent(), java.awt.BorderLayout.CENTER);
            pack();
            setSize(800, 600);
            setVisible(true);
        }
    }

    @Test
    void getUserMediaReachesOnRequestMediaAccessPermissionAndCanBeAccepted() {
        PermissionTestFrame frame = new PermissionTestFrame(true /* accept */);
        try {
            assertTrue(frame.requested.await(10, TimeUnit.SECONDS),
                    "onRequestMediaAccessPermission never fired");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertEquals(ORIGIN, frame.seenOrigin);
        assertEquals(
                CefPermissionHandler.MediaAccessPermissionType.PERMISSION_DEVICE_AUDIO_CAPTURE
                        | CefPermissionHandler.MediaAccessPermissionType
                                  .PERMISSION_DEVICE_VIDEO_CAPTURE,
                frame.seenPermissions);
        frame.terminateTest();
        frame.awaitCompletion();
    }

    @Test
    void onRequestMediaAccessPermissionReturningFalseTakesTheDenyPath() {
        PermissionTestFrame frame = new PermissionTestFrame(false /* accept */);
        try {
            assertTrue(frame.requested.await(10, TimeUnit.SECONDS),
                    "onRequestMediaAccessPermission never fired");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertEquals(ORIGIN, frame.seenOrigin);
        frame.terminateTest();
        frame.awaitCompletion();
    }
}
