// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefFrameHandlerAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;

// Coverage for the CefFrameHandler binding (GH #33): a data: URL iframe is
// enough to create a real sub-frame without needing a second addResource()'d
// URL, so onFrameCreated/onFrameAttached are asserted directly.
// onMainFrameChanged(old!=null, new!=null) only fires for a cross-origin
// main-frame navigation, which this test's single addResource()'d page
// never exercises, so it's intentionally not asserted here.
@ExtendWith(TestSetupExtension.class)
class CefFrameHandlerTest {
    private static final String TEST_URL = "http://test.com/frame_handler_test.html";
    private static final String CONTENT = "<html><body><iframe src=\"data:text/html,"
            + "<html><body>child</body></html>\"></iframe></body></html>";

    @Test
    void subFrameLifecycleCallbacksFire() throws Exception {
        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            boolean[] sawCreated = {false};
            // onFrameAttached always follows onFrameCreated for a given
            // frame, so waiting on this latch alone is enough to know both
            // fired.
            CountDownLatch subFrameAttached = new CountDownLatch(1);

            TestFrame frame = new TestFrame() {
                @Override
                protected void setupTest() {
                    addResource(TEST_URL, CONTENT, "text/html");

                    client_.addFrameHandler(new CefFrameHandlerAdapter() {
                        @Override
                        public void onFrameCreated(CefBrowser browser, CefFrame f) {
                            if (!f.isMain()) sawCreated[0] = true;
                        }

                        @Override
                        public void onFrameAttached(
                                CefBrowser browser, CefFrame f, boolean reattached) {
                            if (!f.isMain()) {
                                subFrameAttached.countDown();
                                terminateTest();
                            }
                        }
                    });

                    createBrowser(TEST_URL, true /* useOSR */);
                    super.setupTest();
                }
            };

            frame.awaitCompletion();

            assertTrue(subFrameAttached.getCount() == 0,
                    "onFrameAttached() was never invoked for the iframe's sub-frame");
            assertTrue(sawCreated[0], "onFrameCreated() never fired for the iframe's sub-frame");
        });
    }
}
