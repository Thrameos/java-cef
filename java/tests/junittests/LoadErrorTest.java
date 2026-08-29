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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// Unhappy-path coverage for CefLoadHandler.onLoadError (native/load_handler.cpp),
// previously never exercised -- every other test in this suite only follows the
// happy load path. Navigating to an unregistered URL under our own intercepted
// scheme (no addResource() entry, so TestFrame.getResourceHandler() returns null
// and CEF fails the load) triggers a real ERR_* failure without any real network
// access. See plan/roadmap.md Phase 3.
@ExtendWith(TestSetupExtension.class)
class LoadErrorTest {
    private static final String MISSING_URL = "http://test.com/does-not-exist.html";

    @Test
    void navigatingToUnregisteredResourceInvokesOnLoadError() {
        ErrorCode[] errorCode = {null};
        String[] failedUrl = {null};
        boolean[] gotError = {false};

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                // Deliberately not calling addResource() -- this URL is never
                // registered, so getResourceHandler() returns null and CEF fails
                // the navigation with a real error code.
                createBrowser(MISSING_URL, true /* useOSR */);
                super.setupTest();
            }

            @Override
            public void onLoadError(CefBrowser browser, CefFrame frame, ErrorCode errorCode_,
                    String errorText, String failedUrl_) {
                if (gotError[0]) return;
                gotError[0] = true;
                errorCode[0] = errorCode_;
                failedUrl[0] = failedUrl_;
                terminateTest();
            }
        };

        frame.awaitCompletion();

        assertTrue(gotError[0], "onLoadError was never invoked");
        assertNotNull(errorCode[0]);
        assertEquals(MISSING_URL, failedUrl[0]);
    }
}
