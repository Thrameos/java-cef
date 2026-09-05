// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefCookieAccessFilter;
import org.cef.network.CefCookie;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashMap;

// Exercises CefCookieAccessFilter (native/cookie_access_filter.cpp, previously
// untested). TestFrame.getCookieAccessFilter() already exists as an override
// point (mirrors getResourceHandler()'s pattern); this test overrides it to
// return a filter and confirms canSaveCookie() fires when the response sets a
// cookie via a Set-Cookie header. See plan/roadmap.md Phase 4 (post-Track B
// real-0%-file list).
@ExtendWith(TestSetupExtension.class)
class CefCookieAccessFilterTest {
    private static final String TEST_URL = "http://test.com/cookie_access_filter.html";

    @Test
    void canSaveCookieFiresForSetCookieHeader() {
        boolean[] gotCanSaveCookie = {false};

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                HashMap<String, String> headers = new HashMap<>();
                headers.put("Set-Cookie", "jcef_filter_test=1; Path=/");
                addResource(TEST_URL, "<html><body>cookie filter test</body></html>", "text/html",
                        headers);
                createBrowser(TEST_URL, true /* useOSR */);
                super.setupTest();
            }

            @Override
            public CefCookieAccessFilter getCookieAccessFilter(
                    CefBrowser browser, CefFrame frame, CefRequest request) {
                return new CefCookieAccessFilter() {
                    @Override
                    public boolean canSendCookie(CefBrowser browser, CefFrame frame,
                            CefRequest request, CefCookie cookie) {
                        return true;
                    }

                    @Override
                    public boolean canSaveCookie(CefBrowser browser, CefFrame frame,
                            CefRequest request, CefResponse response, CefCookie cookie) {
                        gotCanSaveCookie[0] = true;
                        return true;
                    }
                };
            }

            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                    boolean canGoBack, boolean canGoForward) {
                if (!isLoading) terminateTest();
            }
        };

        frame.awaitCompletion();

        assertTrue(gotCanSaveCookie[0], "canSaveCookie was never invoked");
    }
}
