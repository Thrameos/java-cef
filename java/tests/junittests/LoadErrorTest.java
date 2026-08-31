// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandler.ErrorCode;
import org.cef.handler.CefLoadHandlerAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.CountDownLatch;

// Unhappy-path coverage for CefLoadHandler.onLoadError (native/load_handler.cpp),
// previously never exercised -- every other test in this suite only follows the
// happy load path. Navigating to an unregistered URL under our own intercepted
// scheme (no addResource() entry, so getResourceHandler() returns null and CEF
// fails the load) triggers a real ERR_* failure without any real network access.
// See plan/roadmap.md Phase 3.
//
// Migrated to the shared-browser (Tier 1) harness -- see plan/roadmap.md's
// "two-tier test harness" entry. This test is what motivated
// SharedBrowserExtension.addLoadHandler()/navigateTo(): the original,
// simpler loadPage()-only design had no way for a test to observe
// onLoadError itself.
@ExtendWith({TestSetupExtension.class, SharedBrowserExtension.class})
class LoadErrorTest {
    private static final String MISSING_URL = "http://test.com/shared/does-not-exist.html";

    @Test
    void navigatingToUnregisteredResourceInvokesOnLoadError() {
        ErrorCode[] errorCode = {null};
        String[] failedUrl = {null};
        boolean[] gotError = {false};
        CountDownLatch done = new CountDownLatch(1);

        // Deliberately not calling loadPage()/registering any content for
        // MISSING_URL -- SharedBrowserExtension's shared resource handler
        // returns null for it, so CEF fails the navigation with a real
        // error code.
        SharedBrowserExtension.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadError(CefBrowser browser, CefFrame frame, ErrorCode errorCode_,
                    String errorText, String failedUrl_) {
                if (gotError[0]) return;
                gotError[0] = true;
                errorCode[0] = errorCode_;
                failedUrl[0] = failedUrl_;
                done.countDown();
            }
        });

        SharedBrowserExtension.navigateTo(MISSING_URL);
        if (!gotError[0]) {
            SharedBrowserExtension.awaitLatch(done, 10);
        }

        assertTrue(gotError[0], "onLoadError was never invoked");
        assertNotNull(errorCode[0]);
        assertEquals(MISSING_URL, failedUrl[0]);
    }
}
