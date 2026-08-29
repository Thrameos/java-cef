// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefCallback;
import org.cef.callback.CefResourceReadCallback;
import org.cef.callback.CefResourceSkipCallback;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefResourceHandler;
import org.cef.misc.BoolRef;
import org.cef.misc.IntRef;
import org.cef.misc.LongRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.ByteBuffer;

// Exercises CefResourceHandler's "new" API (open()/read()/skip(), which
// natively marshal through CefResourceReadCallback_N.cpp/
// CefResourceSkipCallback_N.cpp, both 0% covered per Track B's real gcovr
// run) -- distinct from the "old"/deprecated API (processRequest()/
// readResponse(), used by TestResourceHandler and every other test in this
// suite). Per CefResourceHandlerAdapter's own default open() implementation
// ("Enables backwards compatibility by default by calling processRequest"),
// nothing else in this suite ever exercises the new API path at all: the
// default open() always signals handleRequest=false, which routes straight
// back to the legacy processRequest()/readResponse() methods.
//
// skip() is exercised via a real HTTP Range request (a JS fetch() with an
// explicit Range header) -- skip() is CEF's mechanism for a resource handler
// to fast-forward past bytes without actually transferring them, used for
// exactly this case.
@ExtendWith(TestSetupExtension.class)
class CefResourceHandlerModernApiTest {
    private static final String PAGE_URL = "http://test.com/modern_resource_api.html";
    private static final String RESOURCE_URL = "http://test.com/modern_resource_api_data.bin";
    private static final String RESOURCE_CONTENT = "0123456789ABCDEF";
    private static final String CONTENT = "<html><body><script>"
            + "Promise.all(["
            + "  fetch('" + RESOURCE_URL + "').then(r => r.text()),"
            + "  fetch('" + RESOURCE_URL + "', {headers: {'Range': 'bytes=10-'}})"
            + "    .then(r => r.text())"
            + "]).then(function(results) {"
            + "  document.title = 'done:' + results[0] + ':' + results[1];"
            + "});"
            + "</script></body></html>";

    // Implements the "new" API directly (open/read/skip), not extending
    // CefResourceHandlerAdapter, so there's no legacy-fallback default to
    // accidentally rely on.
    private static class ModernResourceHandler implements CefResourceHandler {
        private int offset_ = 0;

        @Override
        public boolean processRequest(CefRequest request, CefCallback callback) {
            return false;
        }

        @Override
        public boolean open(CefRequest request, BoolRef handleRequest, CefCallback callback) {
            handleRequest.set(true);
            return true;
        }

        @Override
        public void getResponseHeaders(
                CefResponse response, IntRef responseLength, StringRef redirectUrl) {
            responseLength.set(RESOURCE_CONTENT.length());
            response.setMimeType("text/plain");
            response.setStatus(200);
        }

        @Override
        public boolean readResponse(
                byte[] dataOut, int bytesToRead, IntRef bytesRead, CefCallback callback) {
            return false;
        }

        @Override
        public boolean read(byte[] dataOut, int bytesToRead, IntRef bytesRead,
                CefResourceReadCallback callback) {
            int length = RESOURCE_CONTENT.length();
            if (offset_ >= length) {
                bytesRead.set(0);
                return false;
            }
            int endPos = Math.min(offset_ + bytesToRead, length);
            String chunk = RESOURCE_CONTENT.substring(offset_, endPos);
            ByteBuffer.wrap(dataOut).put(chunk.getBytes());
            bytesRead.set(chunk.length());
            offset_ = endPos;
            return true;
        }

        @Override
        public boolean skip(
                long bytesToSkip, LongRef bytesSkipped, CefResourceSkipCallback callback) {
            int length = RESOURCE_CONTENT.length();
            int actualSkip = (int) Math.min(bytesToSkip, length - offset_);
            offset_ += actualSkip;
            bytesSkipped.set(actualSkip);
            return true;
        }

        @Override
        public void cancel() {}
    }

    @Test
    void openReadAndSkipAreInvokedForANormalAndARangeRequest() {
        boolean[] gotTitle = {false};
        String[] finalTitle = {null};

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                addResource(PAGE_URL, CONTENT, "text/html");

                client_.addDisplayHandler(new CefDisplayHandlerAdapter() {
                    @Override
                    public void onTitleChange(CefBrowser browser, String title) {
                        if (gotTitle[0] || !title.startsWith("done:")) return;
                        gotTitle[0] = true;
                        finalTitle[0] = title;
                        terminateTest();
                    }
                });

                createBrowser(PAGE_URL, true /* useOSR */);
                super.setupTest();
            }

            @Override
            public CefResourceHandler getResourceHandler(
                    CefBrowser browser, CefFrame frame, CefRequest request) {
                if (RESOURCE_URL.equals(request.getURL())) {
                    return new ModernResourceHandler();
                }
                return super.getResourceHandler(browser, frame, request);
            }
        };

        frame.awaitCompletion();

        assertTrue(gotTitle[0], "The page's fetch() calls never completed");
        // Full-content fetch should read the whole string; the Range fetch
        // (bytes=10-) should skip the first 10 bytes and read the rest.
        assertTrue(finalTitle[0].contains(RESOURCE_CONTENT), "Full-content fetch result "
                + "missing from title: " + finalTitle[0]);
        assertTrue(finalTitle[0].endsWith(RESOURCE_CONTENT.substring(10)), "Range-fetch "
                + "result missing/wrong from title: " + finalTitle[0]);
    }
}
