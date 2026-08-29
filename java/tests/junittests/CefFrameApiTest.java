// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// Broad, low-risk coverage sweep of CefFrame_N.cpp (only 6% covered per Track
// B's real gcovr run -- a large remaining gap for a small interface). Plain
// synchronous calls against a live main frame, all invoked from the CEF UI
// thread (see plan/roadmap.md's CefBrowserApiTest for why -- getMainFrame()
// and friends were found to return null when called from the JUnit thread
// after the fact).
@ExtendWith(TestSetupExtension.class)
class CefFrameApiTest {
    private static final String TEST_URL = "http://test.com/frame_api.html";
    private static final String CONTENT = "<html><body>frame api test</body></html>";

    @Test
    void frameGettersAndEditCommandsDoNotThrow() {
        String[] identifier = {null};
        String[] url = {null};
        String[] name = {null};
        boolean[] isMain = {false};
        boolean[] isValid = {false};
        CefFrame[] parent = {null};

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                addResource(TEST_URL, CONTENT, "text/html");
                createBrowser(TEST_URL, true /* useOSR */);
                super.setupTest();
            }

            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                    boolean canGoBack, boolean canGoForward) {
                if (isLoading) return;

                CefFrame mainFrame = browser.getMainFrame();
                identifier[0] = mainFrame.getIdentifier();
                url[0] = mainFrame.getURL();
                name[0] = mainFrame.getName();
                isMain[0] = mainFrame.isMain();
                isValid[0] = mainFrame.isValid();
                parent[0] = mainFrame.getParent();

                mainFrame.executeJavaScript("1+1;", url[0], 1);
                mainFrame.undo();
                mainFrame.redo();
                mainFrame.cut();
                mainFrame.copy();
                mainFrame.paste();
                mainFrame.selectAll();

                terminateTest();
            }
        };

        frame.awaitCompletion();

        assertNotNull(identifier[0]);
        assertFalse(identifier[0].isEmpty());
        assertEquals(TEST_URL, url[0]);
        assertNotNull(name[0]);
        assertTrue(isMain[0]);
        assertTrue(isValid[0]);
        assertNull(parent[0], "The main frame should have no parent");
    }
}
