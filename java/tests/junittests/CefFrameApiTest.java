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
import org.cef.handler.CefLoadHandlerAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.CountDownLatch;

// Broad, low-risk coverage sweep of CefFrame_N.cpp (only 6% covered per Track
// B's real gcovr run -- a large remaining gap for a small interface). Plain
// synchronous calls against a live main frame, all invoked from the CEF UI
// thread (see CefBrowserApiTest for why -- getMainFrame() and friends were
// found to return null when called from the JUnit thread after the fact) --
// captured here from inside the shared harness's onLoadEnd forwarding
// (SharedBrowserExtension.addLoadHandler()), which runs on that same CEF UI
// thread, rather than from the JUnit thread after loadPage() returns.
//
// Migrated to the shared-browser (Tier 1) harness -- see plan/roadmap.md's
// "two-tier test harness" entry.
@ExtendWith({TestSetupExtension.class, SharedBrowserExtension.class})
class CefFrameApiTest {
    private static final String CONTENT = "<html><body>frame api test</body></html>";

    @Test
    void frameGettersAndEditCommandsDoNotThrow() {
        String[] identifier = {null};
        String[] url = {null};
        String[] name = {null};
        boolean[] isMain = {false};
        boolean[] isValid = {false};
        CefFrame[] parent = {null};
        CountDownLatch done = new CountDownLatch(1);

        SharedBrowserExtension.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
                if (!frame.isMain()) return;

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

                done.countDown();
            }
        });

        String loadedUrl = SharedBrowserExtension.loadPage(CONTENT);
        SharedBrowserExtension.awaitLatch(done, 15);

        assertNotNull(identifier[0]);
        assertFalse(identifier[0].isEmpty());
        assertEquals(loadedUrl, url[0]);
        assertNotNull(name[0]);
        assertTrue(isMain[0]);
        assertTrue(isValid[0]);
        assertNull(parent[0], "The main frame should have no parent");
    }
}
