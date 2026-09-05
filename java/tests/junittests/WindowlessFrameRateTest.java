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
//
// Migrated to the shared-browser (Tier 1) harness -- see plan/roadmap.md's
// "two-tier test harness" entry. Despite the "windowless" name this is an
// OSR (not windowed) API, same as every other Tier 1 test; the frame-rate
// value it sets is a real, persistent property of the shared browser, but
// nothing else in this suite asserts on it, so leaving it at 45 afterward is
// harmless.
@ExtendWith({TestSetupExtension.class, SharedBrowserExtension.class})
class WindowlessFrameRateTest {
    private static final String CONTENT = "<html><body>frame rate test</body></html>";

    @Test
    void setAndGetWindowlessFrameRateRoundTrips() throws Exception {
        Integer[] result = {null};

        SharedBrowserExtension.loadPage(CONTENT);

        CefBrowser browser = SharedBrowserExtension.browser();
        browser.setWindowlessFrameRate(45);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        browser.getWindowlessFrameRate().whenComplete((rate, error) -> {
            result[0] = rate;
            done.countDown();
        });
        SharedBrowserExtension.awaitLatch(done, 15);

        assertNotNull(result[0]);
        assertEquals(45, result[0]);
    }
}
