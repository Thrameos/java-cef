// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.network.CefPostData;
import org.cef.network.CefPostDataElement;
import org.cef.network.CefRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.charset.StandardCharsets;
import java.util.Vector;

// Regression-guard test for upstream chromiumembedded/java-cef#392: a JS
// fetch() POST's body should be visible via CefRequest.getPostData() in
// CefResourceRequestHandler.getResourceHandler() -- upstream reports it
// arrives empty/missing for some real XHR/fetch POST requests (against a
// real external site, CEF version unclear from the report).
//
// CONFIRMED WORKING as of this fork's current CEF version (146.0.10) for this
// repro shape -- the POST body is fully visible and correct. Unlike
// UpstreamIssue384Test (deleted -- Accept-Language override, which this
// fork's local-resource-interception harness can't faithfully test since the
// real bug is in Chromium's network-service header canonicalization, a code
// path our synthetic responses never reach), the POST body itself is
// serialized by Blink and handed to the browser process as part of the
// CefRequest before any resource-handler interception happens, so this test
// genuinely exercises the same data upstream's report is about. No fork
// issue filed (nothing broken to track); kept as a regression guard.
@ExtendWith(TestSetupExtension.class)
class UpstreamIssue392Test {
    private static final String PAGE_URL = "http://test.com/upstream_issue_392.html";
    private static final String POST_URL = "http://test.com/upstream_issue_392_submit";
    private static final String POST_BODY = "{\"username\":\"test\",\"password\":\"secret\"}";
    private static final String CONTENT = "<html><body><script>"
            + "fetch('" + POST_URL + "', {method: 'POST',"
            + " headers: {'Content-Type': 'application/json'},"
            + " body: '" + POST_BODY.replace("\"", "\\\"") + "'})"
            + " .then(() => { document.title = 'fetch-done'; });"
            + "</script></body></html>";

    @Test
    void postBodyIsVisibleInResourceHandlerRequest() {
        byte[][] capturedBytes = {null};
        boolean[] gotPostRequest = {false};

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                addResource(PAGE_URL, CONTENT, "text/html");
                addResource(POST_URL, "{}", "application/json");

                client_.addDisplayHandler(new org.cef.handler.CefDisplayHandlerAdapter() {
                    @Override
                    public void onTitleChange(CefBrowser browser, String title) {
                        if ("fetch-done".equals(title)) terminateTest();
                    }
                });

                createBrowser(PAGE_URL, true /* useOSR */);
                super.setupTest();
            }

            @Override
            public org.cef.handler.CefResourceHandler getResourceHandler(
                    CefBrowser browser, CefFrame frame, CefRequest request) {
                if (POST_URL.equals(request.getURL()) && !gotPostRequest[0]) {
                    gotPostRequest[0] = true;
                    CefPostData postData = request.getPostData();
                    if (postData != null) {
                        Vector<CefPostDataElement> elements = new Vector<>();
                        postData.getElements(elements);
                        if (!elements.isEmpty()) {
                            CefPostDataElement element = elements.get(0);
                            int count = element.getBytesCount();
                            byte[] buf = new byte[count];
                            element.getBytes(count, buf);
                            capturedBytes[0] = buf;
                        }
                    }
                }
                return super.getResourceHandler(browser, frame, request);
            }
        };

        frame.awaitCompletion();

        assertTrue(gotPostRequest[0], "getResourceHandler was never invoked for the POST URL");
        assertNotNull(capturedBytes[0], "CefRequest.getPostData() had no elements/bytes for a "
                + "real fetch() POST body");
        assertEquals(
                POST_BODY, new String(capturedBytes[0], StandardCharsets.UTF_8));
    }
}
