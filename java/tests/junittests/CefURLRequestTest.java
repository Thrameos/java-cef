// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.cef.callback.CefAuthCallback;
import org.cef.callback.CefNativeAdapter;
import org.cef.callback.CefURLRequestClient;
import org.cef.network.CefRequest;
import org.cef.network.CefURLRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

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
}
