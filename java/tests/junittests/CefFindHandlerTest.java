// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.handler.CefFindHandlerAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;

// Exercises CefBrowser.find()/CefFindHandler.onFindResult() (native/
// find_handler.{h,cpp}, previously unbound -- find()/stopFinding() were
// fire-and-forget from Java with no way to observe when an in-page find
// settles). Bounded via assertTimeoutPreemptively rather than the usual
// blind TestFrame.awaitCompletion(): unlike CefDevToolsClient.
// executeDevToolsMethod() (a confirmed unrecoverable hang in this
// environment, issue #12), CefBrowser.find() is a different CEF subsystem
// with no such known hang -- but this is the first test exercising it here,
// so fail the test cleanly on a timeout rather than risk blocking the whole
// suite if that assumption turns out to be wrong.
@ExtendWith(TestSetupExtension.class)
class CefFindHandlerTest {
    private static final String TEST_URL = "http://test.com/find_handler_test.html";

    @Test
    void findInvokesOnFindResult() throws Exception {
        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            CountDownLatch gotResult = new CountDownLatch(1);

            TestFrame frame = new TestFrame() {
                @Override
                protected void setupTest() {
                    addResource(TEST_URL,
                            "<html><body>the needle is here</body></html>", "text/html");

                    client_.addFindHandler(new CefFindHandlerAdapter() {
                        @Override
                        public void onFindResult(CefBrowser browser, int identifier, int count,
                                java.awt.Rectangle selectionRect, int activeMatchOrdinal,
                                boolean finalUpdate) {
                            gotResult.countDown();
                            if (finalUpdate) terminateTest();
                        }
                    });

                    createBrowser(TEST_URL, true /* useOSR */);
                    super.setupTest();
                }

                @Override
                public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                        boolean canGoBack, boolean canGoForward) {
                    if (isLoading) return;
                    browser.find("needle", true /* forward */, false /* matchCase */,
                            false /* findNext */);
                }
            };

            frame.awaitCompletion();

            assertTrue(gotResult.getCount() == 0, "onFindResult() was never invoked");
        });
    }
}
