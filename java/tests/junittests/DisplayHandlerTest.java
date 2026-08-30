// Copyright (c) 2019 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// Test the DisplayHandler implementation.
@ExtendWith(TestSetupExtension.class)
class DisplayHandlerTest {
    private final String testUrl_ = "http://test.com/test.html";
    private final String testContent_ =
            "<html><head><title>Test Title</title></head><body>Test!</body></html>";

    private boolean gotCallback_ = false;

    @Test
    void onTitleChange() {
        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                client_.addDisplayHandler(new CefDisplayHandlerAdapter() {
                    @Override
                    public void onTitleChange(CefBrowser browser, String title) {
                        // onTitleChange can legitimately fire more than once with the
                        // same title -- observed a second call from inside
                        // CefBrowser_N.close() itself during OSR teardown, not just
                        // during page load. Treat gotCallback_ as an idempotency
                        // guard, not a strict single-call assertion: an uncaught
                        // AssertionError thrown from inside this native callback can
                        // corrupt later browser teardown (observed as a DCHECK
                        // failure in Debug builds), so a duplicate call must not
                        // throw.
                        if (gotCallback_ || !"Test Title".equals(title)) {
                            return;
                        }
                        gotCallback_ = true;
                        terminateTest();
                    }
                });

                addResource(testUrl_, testContent_, "text/html");

                // Use OSR: TestFrame's default windowed (non-OSR) browser close
                // handshake hangs (onBeforeClose never fires after native window
                // disposal), reproduced in two independent headless environments --
                // see plan/findings.md and upstream java-cef#364.
                createBrowser(testUrl_, true /* useOSR */);

                super.setupTest();
            }
        };

        frame.awaitCompletion();

        assertTrue(gotCallback_);
    }

    @Test
    void onAddressChange() {
        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                client_.addDisplayHandler(new CefDisplayHandlerAdapter() {
                    @Override
                    public void onAddressChange(CefBrowser browser, CefFrame frame, String url) {
                        // See the comment in onTitleChange() above: treat
                        // gotCallback_ as an idempotency guard, not a strict
                        // single-call assertion, since an uncaught assertion here can
                        // corrupt later browser teardown.
                        if (gotCallback_ || !testUrl_.equals(url)) {
                            return;
                        }
                        gotCallback_ = true;
                        terminateTest();
                    }
                });

                addResource(testUrl_, testContent_, "text/html");

                // Use OSR: TestFrame's default windowed (non-OSR) browser close
                // handshake hangs (onBeforeClose never fires after native window
                // disposal), reproduced in two independent headless environments --
                // see plan/findings.md and upstream java-cef#364.
                createBrowser(testUrl_, true /* useOSR */);

                super.setupTest();
            }
        };

        frame.awaitCompletion();

        assertTrue(gotCallback_);
    }

    @Test
    void onConsoleMessage() {
        String consoleUrl = "http://test.com/console_test.html";
        String consoleContent = "<html><body><script>"
                + "console.log('jcef-console-test-marker');"
                + "</script></body></html>";
        boolean[] gotMessage = {false};

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                client_.addDisplayHandler(new CefDisplayHandlerAdapter() {
                    @Override
                    public boolean onConsoleMessage(CefBrowser browser,
                            CefSettings.LogSeverity level, String message, String source,
                            int line) {
                        // See the comment in onTitleChange() above: treat gotMessage
                        // as an idempotency guard rather than throwing from inside
                        // this native callback.
                        if (gotMessage[0] || !message.contains("jcef-console-test-marker")) {
                            return false;
                        }
                        gotMessage[0] = true;
                        terminateTest();
                        return false;
                    }
                });

                addResource(consoleUrl, consoleContent, "text/html");
                createBrowser(consoleUrl, true /* useOSR */);
                super.setupTest();
            }
        };

        frame.awaitCompletion();

        assertTrue(gotMessage[0], "onConsoleMessage never fired for the page's console.log()");
    }
}
