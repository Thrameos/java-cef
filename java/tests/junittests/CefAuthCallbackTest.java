// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefAuthCallback;
import org.cef.callback.CefCallback;
import org.cef.handler.CefResourceHandler;
import org.cef.handler.CefResourceHandlerAdapter;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

// Exercises CefRequestHandler.getAuthCredentials()/CefAuthCallback (native/
// CefAuthCallback_N.cpp, 0% covered per Track B's real gcovr run). Attempts
// to trigger CEF's HTTP-auth-challenge machinery via a locally served 401
// response with a WWW-Authenticate header.
//
// @Disabled: confirmed by running this test un-@Disabled that it currently
// fails cleanly (via TestFrame's 15s watchdog, not a hang) -- getAuthCredentials
// is never invoked. Same class of limitation as upstream issue #384's
// Accept-Language finding (see plan/roadmap.md's Phase 5 section -- that
// finding's own test was deleted rather than kept, since it passed without
// actually validating the real upstream claim; not restored as a reproducer
// since it isn't one): this suite's entire resource-serving mechanism
// (TestFrame.addResource()/a custom CefResourceHandler) intercepts requests
// locally, never touching Chromium's real net::URLLoader/network-service
// layer -- HTTP-auth-challenge handling likely lives in that real network
// layer and never gets exercised by a locally-intercepted response object,
// regardless of what status/headers it sets. Not filed as a fork issue: this
// looks like a test-harness/architecture limitation, not a confirmed
// JCEF/CEF bug.
@Disabled("getAuthCredentials never fires for a locally-intercepted 401 "
        + "response -- likely requires a real net::URLLoader round trip this "
        + "suite's resource-interception harness structurally can't reach, "
        + "same limitation class as upstream issue #384 (Accept-Language). "
        + "Retested 2026-08-29, unchanged.")
@ExtendWith(TestSetupExtension.class)
class CefAuthCallbackTest {
    private static final String TEST_URL = "http://test.com/auth_callback_test.html";
    private static final String BODY = "auth required";

    private static class UnauthorizedHandler extends CefResourceHandlerAdapter {
        private int offset_ = 0;

        @Override
        public boolean processRequest(CefRequest request, CefCallback callback) {
            callback.Continue();
            return true;
        }

        @Override
        public void getResponseHeaders(
                CefResponse response, IntRef responseLength, StringRef redirectUrl) {
            responseLength.set(BODY.length());
            response.setMimeType("text/html");
            response.setStatus(401);
            response.setHeaderByName("WWW-Authenticate", "Basic realm=\"jcef-test-realm\"", true);
        }

        @Override
        public boolean readResponse(
                byte[] dataOut, int bytesToRead, IntRef bytesRead, CefCallback callback) {
            int length = BODY.length();
            if (offset_ >= length) return false;
            int endPos = Math.min(offset_ + bytesToRead, length);
            String chunk = BODY.substring(offset_, endPos);
            ByteBuffer.wrap(dataOut).put(chunk.getBytes());
            bytesRead.set(chunk.length());
            offset_ = endPos;
            return true;
        }
    }

    @Test
    void authChallengeInvokesGetAuthCredentials() {
        boolean[] gotCallback = {false};
        String[] realm = {null};
        String[] scheme = {null};
        boolean[] wasProxy = {true};

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                createBrowser(TEST_URL, true /* useOSR */);
                super.setupTest();
            }

            @Override
            public CefResourceHandler getResourceHandler(
                    CefBrowser browser, CefFrame frame, CefRequest request) {
                if (TEST_URL.equals(request.getURL())) {
                    return new UnauthorizedHandler();
                }
                return super.getResourceHandler(browser, frame, request);
            }

            @Override
            public boolean getAuthCredentials(CefBrowser browser, String origin_url,
                    boolean isProxy, String host, int port, String realm_, String scheme_,
                    CefAuthCallback callback) {
                if (gotCallback[0]) return false;
                gotCallback[0] = true;
                realm[0] = realm_;
                scheme[0] = scheme_;
                wasProxy[0] = isProxy;
                callback.cancel();
                terminateTest();
                return true;
            }
        };

        frame.awaitCompletion(15, TimeUnit.SECONDS);

        assertTrue(gotCallback[0], "getAuthCredentials was never invoked");
        assertEquals("jcef-test-realm", realm[0]);
        assertEquals("basic", scheme[0].toLowerCase());
        assertTrue(!wasProxy[0], "This was a server auth challenge, not a proxy one");
    }
}
