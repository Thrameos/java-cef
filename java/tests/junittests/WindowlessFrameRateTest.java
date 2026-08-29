// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.cef.browser.CefBrowser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// Exercises native/int_callback.cpp (0% covered per this session's baseline gcovr
// run; see plan/roadmap.md Phase 2) via CefBrowser.getWindowlessFrameRate(), whose
// async result is delivered through an IntCallback.
@ExtendWith(TestSetupExtension.class)
class WindowlessFrameRateTest {
    private static final String TEST_URL = "http://test.com/windowless_frame_rate.html";
    private static final String CONTENT = "<html><body>frame rate test</body></html>";

    @Test
    void setAndGetWindowlessFrameRateRoundTrips() throws Exception {
        Integer[] result = {null};

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
                browser.setWindowlessFrameRate(45);
                browser.getWindowlessFrameRate().whenComplete((rate, error) -> {
                    result[0] = rate;
                    terminateTest();
                });
            }
        };

        frame.awaitCompletion();

        assertNotNull(result[0]);
        assertEquals(45, result[0]);
    }
}
