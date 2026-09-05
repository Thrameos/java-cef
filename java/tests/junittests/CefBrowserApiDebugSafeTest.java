// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.network.CefRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;

// Split out of CefBrowserApiTest (see the comment there): these two methods
// were bisected via --select-method isolation and confirmed to NOT trigger
// the Debug/coverage-build mojo crash that CefBrowserApiTest's
// frameNavigationAndZoomApis() does, so they're safe to include in the
// ENABLE_COVERAGE Debug-build gcovr measurement run.
//
// Migrated to the shared-browser (Tier 1) harness -- see plan/roadmap.md's
// "two-tier test harness" entry. This class was previously @Disabled: it
// deterministically crashed the whole process (mojo interface_endpoint_
// client.cc:538 DCHECK) when it wasn't preceded by enough other classes in
// the same run -- i.e. exactly the "first real browser usage in the
// process" cold-start hazard class documented for LoadErrorTest's cold-
// start bug and CefBrowserApiDebugSafeTest's own class comment. Moving this
// onto SharedBrowserExtension means it now runs against the harness's own
// warmed-up shared browser (see initializeSharedBrowser()) instead of a
// freshly created one -- re-verified clean (no crash) in isolated
// --select-class runs before removing @Disabled; if this class starts
// crashing again in some future environment, re-add @Disabled rather than
// chasing the underlying native crash further (see [[coverage_strategy_
// port_ceftests]] user memory).
@ExtendWith({TestSetupExtension.class, SharedBrowserExtension.class})
class CefBrowserApiDebugSafeTest {
    private static final String CONTENT = "<html><head><title>API Test</title></head>"
            + "<body>debug-safe browser API test</body></html>";

    @Test
    void executeJavaScriptAndLoadRequestDoNotThrow() {
        String loadedUrl = SharedBrowserExtension.loadPage(CONTENT);

        CefBrowser browser = SharedBrowserExtension.browser();
        browser.executeJavaScript("1+1;", browser.getURL(), 1);

        CefRequest request = CefRequest.create();
        request.setURL(loadedUrl);
        browser.loadRequest(request);
    }

    @Test
    void createScreenshotReturnsARealImage() {
        BufferedImage[] image = {null};
        boolean[] gotImage = {false};
        CountDownLatch done = new CountDownLatch(1);

        SharedBrowserExtension.loadPage(CONTENT);

        CefBrowser browser = SharedBrowserExtension.browser();
        browser.createScreenshot(false /* nativeResolution */).whenComplete((img, ex) -> {
            if (ex == null) {
                image[0] = img;
                gotImage[0] = true;
            }
            done.countDown();
        });

        // A longer timeout than the 15s default: an isolated run showed the
        // default headroom wasn't enough in this headless OSR/GL
        // environment.
        SharedBrowserExtension.awaitLatch(done, 45);

        assertTrue(gotImage[0], "createScreenshot's future never completed successfully");
        assertNotNull(image[0]);
        assertTrue(image[0].getWidth() > 0);
        assertTrue(image[0].getHeight() > 0);
    }
}
