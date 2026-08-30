// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.CefApp;
import org.cef.callback.CefAuthCallback;
import org.cef.callback.CefCallback;
import org.cef.callback.CefNativeAdapter;
import org.cef.callback.CefURLRequestClient;
import org.cef.handler.CefResourceHandlerAdapter;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;
import org.cef.network.CefURLRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// CefURLRequest is explicitly documented as "not associated with a browser
// instance", so unlike Phase 2's browser-lifecycle tests this only needs CEF's
// native side initialized (via TestSetupExtension), not a live CefBrowser/TestFrame.
@ExtendWith(TestSetupExtension.class)
class CefURLRequestTest {
    // Minimal no-op client -- these tests only exercise creation/getters/cancel,
    // not an actual network round-trip, so none of these callbacks need to do
    // anything.
    private static class NoOpClient extends CefNativeAdapter implements CefURLRequestClient {
        @Override
        public void onRequestComplete(CefURLRequest request) {}
        @Override
        public void onUploadProgress(CefURLRequest request, int current, int total) {}
        @Override
        public void onDownloadProgress(CefURLRequest request, int current, int total) {}
        @Override
        public void onDownloadData(CefURLRequest request, byte[] data, int data_length) {}
        @Override
        public boolean getAuthCredentials(boolean isProxy, String host, int port, String realm,
                String scheme, CefAuthCallback callback) {
            return false;
        }
    }

    @Test
    void createReturnsRequestAndClient() {
        CefRequest request = CefRequest.create();
        request.setURL("http://jcef-test-urlrequest.invalid/");
        request.setMethod("GET");
        NoOpClient client = new NoOpClient();

        CefURLRequest urlRequest = CefURLRequest.create(request, client);
        assertNotNull(urlRequest);
        assertSame(client, urlRequest.getClient());

        // The request object becomes read-only once handed to CefURLRequest.create()
        // (per its own documentation), so the URL should still be readable.
        assertEquals("http://jcef-test-urlrequest.invalid/", urlRequest.getRequest().getURL());

        urlRequest.cancel();
        urlRequest.dispose();
    }

    @Test
    void cancelDoesNotThrow() {
        CefRequest request = CefRequest.create();
        request.setURL("http://jcef-test-urlrequest-cancel.invalid/");
        CefURLRequest urlRequest = CefURLRequest.create(request, new NoOpClient());

        urlRequest.cancel();
        // A second cancel() on an already-canceled request must also not throw.
        urlRequest.cancel();

        urlRequest.dispose();
    }

    @Test
    void requestStatusIsNotNull() {
        CefRequest request = CefRequest.create();
        request.setURL("http://jcef-test-urlrequest-status.invalid/");
        CefURLRequest urlRequest = CefURLRequest.create(request, new NoOpClient());

        // Whatever the exact status value is at this point (timing-dependent, this
        // request was never awaited), the accessor itself must not fail.
        assertNotNull(urlRequest.getRequestStatus());

        urlRequest.cancel();
        urlRequest.dispose();
    }

    // Everything above uses a .invalid TLD, so the request always fails fast
    // without any real network activity -- meaning native/url_request_client.
    // cpp's own callback forwarders (OnRequestComplete/OnDownloadData/
    // OnUploadProgress/OnDownloadProgress) never actually fire. CefURLRequest
    // is browser-independent, so it doesn't go through TestFrame's
    // addResource()/per-browser resource-request-handler mechanism every
    // other test in this suite relies on -- but CefApp.
    // registerSchemeHandlerFactory() (see CefSchemeHandlerFactoryTest.java)
    // is registered at the CefApp level, not per-browser, so it intercepts
    // this too. Use it to give this request something real to complete
    // against without touching the real network.
    private static final String DOMAIN = "jcef-urlrequest-factory-test.invalid";
    private static final String TEST_URL = "http://" + DOMAIN + "/data.bin";
    private static final String CONTENT = "url-request-factory-content";

    private static class FixedContentHandler extends CefResourceHandlerAdapter {
        private int offset_ = 0;

        @Override
        public boolean processRequest(CefRequest request, CefCallback callback) {
            callback.Continue();
            return true;
        }

        @Override
        public void getResponseHeaders(
                CefResponse response, IntRef responseLength, StringRef redirectUrl) {
            responseLength.set(CONTENT.length());
            response.setMimeType("application/octet-stream");
            response.setStatus(200);
        }

        @Override
        public boolean readResponse(
                byte[] dataOut, int bytesToRead, IntRef bytesRead, CefCallback callback) {
            int length = CONTENT.length();
            if (offset_ >= length) return false;
            int endPos = Math.min(offset_ + bytesToRead, length);
            String chunk = CONTENT.substring(offset_, endPos);
            ByteBuffer.wrap(dataOut).put(chunk.getBytes());
            bytesRead.set(chunk.length());
            offset_ = endPos;
            return true;
        }
    }

    // @Tag, not @Disabled: this passes cleanly in the Release build (no
    // DCHECKs compiled in), confirmed via repeated full-suite runs -- only
    // the Debug/ENABLE_COVERAGE build crashes (see Thrameos/java-cef#24).
    // Excluded from Track B's coverage-measurement runs via
    // --exclude-tag debug-build-crash (see plan/roadmap.md) rather than
    // @Disabled, which would incorrectly skip it in Release CI too.
    @Tag("debug-build-crash")
    @Test
    void realCompletionFiresRequestCompleteAndDownloadData() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        boolean[] gotComplete = {false};
        ByteArrayOutputStream received = new ByteArrayOutputStream();

        CefApp.getInstance().registerSchemeHandlerFactory(
                "http", DOMAIN, (browser, frame, schemeName, request) -> new FixedContentHandler());
        try {
            CefRequest request = CefRequest.create();
            request.setURL(TEST_URL);
            request.setMethod("GET");

            CefURLRequest[] urlRequest = {null};
            urlRequest[0] = CefURLRequest.create(request, new NoOpClient() {
                @Override
                public void onDownloadData(CefURLRequest req, byte[] data, int dataLength) {
                    received.write(data, 0, dataLength);
                }

                @Override
                public void onRequestComplete(CefURLRequest req) {
                    if (gotComplete[0]) return;
                    gotComplete[0] = true;
                    latch.countDown();
                }
            });

            assertTrue(latch.await(15, TimeUnit.SECONDS),
                    "onRequestComplete was never invoked for a real scheme-factory-backed request");
            assertEquals(CONTENT, received.toString());

            urlRequest[0].dispose();
        } finally {
            CefApp.getInstance().clearSchemeHandlerFactories();
        }
    }
}
