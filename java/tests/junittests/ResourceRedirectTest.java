// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefCallback;
import org.cef.handler.CefResourceHandlerAdapter;
import org.cef.handler.CefResourceHandler;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// Exercises CefResourceRequestHandler.onResourceRedirect (TestFrame's own no-op
// override, native/resource_request_handler.cpp), previously never triggered --
// every other test's resource handler serves content directly with no redirect.
// A CefResourceHandler.getResponseHeaders() can redirect a request by setting its
// StringRef redirectUrl parameter instead of serving content; this test does that
// for one intercepted URL and lets a normal addResource() entry serve the target.
@ExtendWith(TestSetupExtension.class)
class ResourceRedirectTest {
    private static final String FROM_URL = "http://test.com/redirect_from.html";
    private static final String TO_URL = "http://test.com/redirect_to.html";
    private static final String CONTENT = "<html><head><title>redirected</title></head></html>";

    private static class RedirectingResourceHandler extends CefResourceHandlerAdapter {
        @Override
        public boolean processRequest(CefRequest request, CefCallback callback) {
            callback.Continue();
            return true;
        }

        @Override
        public void getResponseHeaders(
                CefResponse response, IntRef response_length, StringRef redirectUrl) {
            response_length.set(0);
            redirectUrl.set(TO_URL);
        }
    }

    @Test
    void handlerRedirectInvokesOnResourceRedirect() {
        String[] redirectedFrom = {null};
        String[] redirectedTo = {null};
        boolean[] gotTitle = {false};

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                addResource(TO_URL, CONTENT, "text/html");
                createBrowser(FROM_URL, true /* useOSR */);
                super.setupTest();
            }

            @Override
            public CefResourceHandler getResourceHandler(
                    CefBrowser browser, CefFrame frame, CefRequest request) {
                if (FROM_URL.equals(request.getURL())) {
                    return new RedirectingResourceHandler();
                }
                return super.getResourceHandler(browser, frame, request);
            }

            @Override
            public void onResourceRedirect(CefBrowser browser, CefFrame frame,
                    CefRequest request, CefResponse response, StringRef new_url) {
                redirectedFrom[0] = request.getURL();
                redirectedTo[0] = new_url.get();
            }

            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                    boolean canGoBack, boolean canGoForward) {
                if (isLoading || gotTitle[0]) return;
                gotTitle[0] = true;
                terminateTest();
            }
        };

        frame.awaitCompletion();

        assertTrue(gotTitle[0], "Page never finished loading after redirect");
        assertEquals(FROM_URL, redirectedFrom[0]);
        assertEquals(TO_URL, redirectedTo[0]);
    }
}
