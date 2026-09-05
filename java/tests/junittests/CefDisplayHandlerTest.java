// Copyright (c) 2019 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.CountDownLatch;

// Test the DisplayHandler implementation.
//
// Migrated to the shared-browser (Tier 1) harness -- see plan/roadmap.md's
// "two-tier test harness" entry -- as one of the two proof-of-concept
// classes: this test (with CefFocusHandlerCoverageTest) is what originally
// reproduced issue #4/#23's second, still-unfixed all_.empty() mechanism
// when both ran under TestFrame's one-browser-per-test model, so it doubles
// as regression coverage that the shared-browser harness actually avoids
// that exposure.
@ExtendWith({TestSetupExtension.class, SharedBrowserExtension.class})
class CefDisplayHandlerTest {
    private final String testContent_ =
            "<html><head><title>Test Title</title></head><body>Test!</body></html>";

    private boolean gotCallback_ = false;

    @Test
    void onTitleChange() {
        CountDownLatch done = new CountDownLatch(1);
        SharedBrowserExtension.addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override
            public void onTitleChange(CefBrowser browser, String title) {
                // onTitleChange can legitimately fire more than once with the
                // same title -- treat gotCallback_ as an idempotency guard, not
                // a strict single-call assertion (see the original TestFrame-
                // based version of this test for the fuller history: an
                // uncaught AssertionError thrown from inside this native
                // callback can corrupt later browser teardown).
                if (gotCallback_ || !"Test Title".equals(title)) {
                    return;
                }
                gotCallback_ = true;
                done.countDown();
            }
        });

        SharedBrowserExtension.loadPage(testContent_);
        SharedBrowserExtension.awaitLatch(done, 10);

        assertTrue(gotCallback_);
    }

    @Test
    void onAddressChange() {
        CountDownLatch done = new CountDownLatch(1);
        String[] receivedUrl = {null};
        SharedBrowserExtension.addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override
            public void onAddressChange(CefBrowser browser, CefFrame frame, String url) {
                if (gotCallback_) return;
                gotCallback_ = true;
                receivedUrl[0] = url;
                done.countDown();
            }
        });

        // onAddressChange typically fires at navigation start, before
        // loadPage() returns -- but the URL it'll fire with isn't known
        // until loadPage() assigns it internally, so the comparison has to
        // happen after the fact regardless of exactly when the callback ran.
        String expectedUrl = SharedBrowserExtension.loadPage(testContent_);
        if (!gotCallback_) {
            SharedBrowserExtension.awaitLatch(done, 10);
        }

        assertTrue(gotCallback_);
        assertEquals(expectedUrl, receivedUrl[0]);
    }

    @Test
    void onConsoleMessage() {
        String consoleContent = "<html><body><script>"
                + "console.log('jcef-console-test-marker');"
                + "</script></body></html>";
        boolean[] gotMessage = {false};
        CountDownLatch done = new CountDownLatch(1);

        SharedBrowserExtension.addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override
            public boolean onConsoleMessage(CefBrowser browser, CefSettings.LogSeverity level,
                    String message, String source, int line) {
                if (gotMessage[0] || !message.contains("jcef-console-test-marker")) {
                    return false;
                }
                gotMessage[0] = true;
                done.countDown();
                return false;
            }
        });

        SharedBrowserExtension.loadPage(consoleContent);
        if (!gotMessage[0]) {
            SharedBrowserExtension.awaitLatch(done, 10);
        }

        assertTrue(gotMessage[0], "onConsoleMessage never fired for the page's console.log()");
    }
}
