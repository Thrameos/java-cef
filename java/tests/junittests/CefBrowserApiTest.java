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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Vector;

// Broad, low-risk coverage sweep of CefBrowser_N.cpp (952 lines, only 13%
// covered per Track B's real gcovr run -- by far the largest remaining gap).
// See CefBrowserApiDebugSafeTest for two more methods that were originally
// here too (executeJavaScriptAndLoadRequestDoNotThrow,
// createScreenshotReturnsARealImage) -- moved out because THIS method
// (viewSource()/find()/stopFinding(), bisected and confirmed via
// --select-method isolation) is the one that triggers a Debug/coverage-build
// -only mojo crash (see plan/roadmap.md's Tier A item 2), so this class stays
// excluded from that measurement run while the other two rejoin it.
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
                browser.stopFinding(true);

                terminateTest();
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
