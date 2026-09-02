// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefFindHandlerAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.awt.Rectangle;
import java.util.Vector;

// Broad, low-risk coverage sweep of CefBrowser_N.cpp (952 lines, only 13%
// covered per Track B's real gcovr run -- by far the largest remaining gap).
// See CefBrowserApiDebugSafeTest for two more methods that were originally
// here too (executeJavaScriptAndLoadRequestDoNotThrow,
// createScreenshotReturnsARealImage) -- moved out earlier because this
// method used to trigger a Debug/coverage-build-only mojo crash
// (interface_endpoint_client.cc:538 DCHECK failed: !has_pending_responders();
// see GH #27, filed against the sibling class hitting the identical
// signature). ROOT-CAUSED 2026-09-01 via gdb (launched under
// `handle SIGSEGV nostop noprint pass`, per the jpype gdb technique): the
// crashing thread's backtrace is N_Close(force=true) ->
// AlloyBrowserHostImpl::CloseContents() ->
// RenderProcessHostImpl::FastShutdown() -> ... -> CefFrameHostImpl::
// DetachRenderFrame() -> mojo::Remote<RenderFrame>::ResetWithReason() ->
// InterfaceEndpointClient::PassHandle(), which DCHECKs if the endpoint still
// has a pending responder. find()/viewSource() below used to be
// fire-and-forget on the Java side -- JCEF had no CefFindHandler binding at
// all (no onFindResult), so there was no way to actually wait for find()'s
// async completion before closing, unlike CEF's own
// find_handler_unittest.cc (~/devel/cef/tests/ceftests/), which always
// waits for OnFindResult's finalUpdate before calling StopFinding()+closing.
// FIXED (GH #32): CefFindHandler is now bound; below waits for the real
// onFindResult(finalUpdate=true) signal before closing instead of a blind
// settle delay.
@ExtendWith(TestSetupExtension.class)
class CefBrowserApiTest {
    private static final String TEST_URL = "http://test.com/browser_api.html";
    private static final String CONTENT = "<html><head><title>API Test</title></head>"
            + "<body><iframe name='child' src='" + "http://test.com/browser_api_child.html"
            + "'></iframe></body></html>";
    private static final String CHILD_URL = "http://test.com/browser_api_child.html";
    private static final String CHILD_CONTENT = "<html><body>child frame</body></html>";

    @Test
    void frameNavigationAndZoomApis() {
        CefFrame[] mainFrame = {null};
        boolean[] hasDocument = {false};
        boolean[] isPopup = {true};
        int[] frameCount = {0};
        Vector<String>[] frameIds = new Vector[1];
        Vector<String>[] frameNames = new Vector[1];
        String[] url = {null};
        CefFrame[] frameById = {null};

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                addResource(TEST_URL, CONTENT, "text/html");
                addResource(CHILD_URL, CHILD_CONTENT, "text/html");
                client_.addFindHandler(new CefFindHandlerAdapter() {
                    @Override
                    public void onFindResult(CefBrowser browser, int identifier, int count,
                            Rectangle selectionRect, int activeMatchOrdinal,
                            boolean finalUpdate) {
                        // See the class comment: only call stopFinding()
                        // once the find has actually settled (finalUpdate),
                        // matching CEF's own find_handler_unittest.cc --
                        // calling stopFinding() before that point can
                        // interrupt the search and suppress the final
                        // update, per ~/devel/cef/tests/ceftests/
                        // find_handler_unittest.cc's own comment to that
                        // effect.
                        if (finalUpdate) {
                            browser.stopFinding(true);
                            terminateTest();
                        }
                    }
                });
                createBrowser(TEST_URL, true /* useOSR */);
                super.setupTest();
            }

            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                    boolean canGoBack, boolean canGoForward) {
                if (isLoading) return;

                // Called here (on the CEF UI thread, via TestFrame's
                // CefLoadHandler callback) rather than after awaitCompletion()
                // returns on the JUnit thread -- getMainFrame() and friends
                // returned null when called from the JUnit thread immediately
                // after awaitCompletion(), even though the load had already
                // finished; real finding, not yet root-caused further.
                mainFrame[0] = browser.getMainFrame();
                hasDocument[0] = browser.hasDocument();
                isPopup[0] = browser.isPopup();
                frameCount[0] = browser.getFrameCount();
                frameIds[0] = browser.getFrameIdentifiers();
                frameNames[0] = browser.getFrameNames();
                url[0] = browser.getURL();
                if (!frameIds[0].isEmpty()) {
                    frameById[0] = browser.getFrameByIdentifier(frameIds[0].get(0));
                }

                double originalZoom = browser.getZoomLevel();
                browser.setZoomLevel(originalZoom + 1.0);
                // Zoom changes are applied asynchronously on the renderer
                // side, so just confirm the call didn't throw -- not
                // asserting the new value here.

                browser.stopLoad();
                browser.viewSource();
                browser.replaceMisspelling("test");
                browser.find("body", true, false, false);

                // stopFinding()+terminateTest() are called from the
                // CefFindHandler registered in setupTest() above, once
                // onFindResult() reports finalUpdate=true. The Watchdog
                // (see TestFrame) still force-closes if that never arrives.
            }
        };

        frame.awaitCompletion();

        assertNotNull(mainFrame[0]);
        assertTrue(frameCount[0] >= 1);
        assertNotNull(frameIds[0]);
        assertNotNull(frameNames[0]);
        if (!frameIds[0].isEmpty()) {
            assertNotNull(frameById[0]);
        }
        assertFalse(isPopup[0]);
        assertTrue(hasDocument[0]);
        assertEquals(TEST_URL, url[0]);
    }
}
