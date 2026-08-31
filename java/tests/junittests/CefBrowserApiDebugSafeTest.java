// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.network.CefRequest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.awt.image.BufferedImage;
import java.util.concurrent.TimeUnit;

// Split out of CefBrowserApiTest (see the comment there): these two methods
// were bisected via --select-method isolation and confirmed to NOT trigger
// the Debug/coverage-build mojo crash that CefBrowserApiTest's
// frameNavigationAndZoomApis() does, so they're safe to include in the
// ENABLE_COVERAGE Debug-build gcovr measurement run. Unlike the earlier
// CefPostDataTest split (reverted -- see plan/roadmap.md's "FINDING" section
// and issue #16), this split does NOT carry the same
// first-native-object-in-the-process risk, since both methods here create a
// real browser via TestFrame before doing anything else -- confirmed safe by
// running the full suite in both Release and the coverage build after this
// change, not just assumed.
//
// CORRECTION (2026-08-30, coverage-stabilization session): that "confirmed
// safe" claim does NOT hold in isolation. Verified deterministically, 2/2
// runs with no other class selected (and reproduced again as part of a small
// 6-class --select-class run alongside 5 other classes that are each
// independently reliable): this class crashes the whole process every time
// before any @Test method even starts -- FATAL:mojo/public/cpp/bindings/lib/
// interface_endpoint_client.cc:538] DCHECK failed: !has_pending_responders(),
// preceded by several "Exception in thread AWT-EventQueue-0" with no visible
// stack trace. Not root-caused; disabled per this project's standing
// strategy (disable flaky/broken ported tests rather than chase the
// underlying native crash -- see [[coverage_strategy_port_ceftests]] user
// memory) rather than trusted as "safe" on unverified prior-session say-so.
@Disabled("Deterministically crashes the process before any test runs when "
        + "not preceded by enough other classes in the same run (mojo "
        + "interface_endpoint_client.cc:538 DCHECK) -- not root-caused, see "
        + "class comment")
@ExtendWith(TestSetupExtension.class)
class CefBrowserApiDebugSafeTest {
    private static final String TEST_URL = "http://test.com/browser_api_debug_safe.html";
    private static final String CONTENT = "<html><head><title>API Test</title></head>"
            + "<body>debug-safe browser API test</body></html>";

    @Test
    void executeJavaScriptAndLoadRequestDoNotThrow() {
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
                terminateTest();
            }
        };

        frame.awaitCompletion();

        CefBrowser browser = frame.browser_;
        browser.executeJavaScript("1+1;", browser.getURL(), 1);

        CefRequest request = CefRequest.create();
        request.setURL(TEST_URL);
        browser.loadRequest(request);
    }

    @Test
    void createScreenshotReturnsARealImage() {
        BufferedImage[] image = {null};
        boolean[] gotImage = {false};

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
                browser.createScreenshot(false /* nativeResolution */)
                        .whenComplete((img, ex) -> {
                            if (ex == null) {
                                image[0] = img;
                                gotImage[0] = true;
                            }
                            terminateTest();
                        });
            }
        };

        // A longer timeout than the 30s default: an isolated run showed the
        // default get(15, SECONDS) inline wasn't enough headroom in this
        // headless OSR/GL environment.
        frame.awaitCompletion(45, TimeUnit.SECONDS);

        assertTrue(gotImage[0], "createScreenshot's future never completed successfully");
        assertNotNull(image[0]);
        assertTrue(image[0].getWidth() > 0);
        assertTrue(image[0].getHeight() > 0);
    }
}
