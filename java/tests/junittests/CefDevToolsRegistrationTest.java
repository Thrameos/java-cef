// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefDevToolsClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// Exercises CefBrowser.getDevToolsClient()'s registration path (native/
// CefRegistration_N.cpp, devtools_message_observer.cpp's constructor --
// previously 0% covered) WITHOUT calling executeDevToolsMethod().
//
// browser.getDevToolsClient().executeDevToolsMethod(...) is a confirmed
// genuinely UNRECOVERABLE hang in this environment (see Thrameos/
// java-cef#12) -- but getDevToolsClient() itself (which internally calls
// addDevToolsMessageObserver(), constructing a DevToolsMessageObserver and
// a CefRegistration on the native side) is a *separate* code path,
// confirmed safe in isolation via a standalone diagnostic before writing
// this test: it completes in ~2s with no hang, across repeated runs.
@ExtendWith(TestSetupExtension.class)
class CefDevToolsRegistrationTest {
    private static final String TEST_URL = "http://test.com/devtools_registration.html";

    @Test
    void registrationAndCloseDoNotHangOrThrow() {
        boolean[] done = {false};
        boolean[] wasClosedBefore = {true};
        boolean[] wasClosedAfter = {false};

        TestFrame frame = new TestFrame() {
            CefDevToolsClient client;

            @Override
            protected void setupTest() {
                addResource(TEST_URL, "<html><body>devtools registration test</body></html>",
                        "text/html");
                createBrowser(TEST_URL, true /* useOSR */);
                super.setupTest();
            }

            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                    boolean canGoBack, boolean canGoForward) {
                if (isLoading || done[0]) return;
                client = browser.getDevToolsClient();
                wasClosedBefore[0] = client.isClosed();
                client.close();
                wasClosedAfter[0] = client.isClosed();
                done[0] = true;
                terminateTest();
            }
        };

        frame.awaitCompletion();

        assertTrue(done[0], "getDevToolsClient()/close() never completed");
        assertFalse(wasClosedBefore[0], "Freshly obtained CefDevToolsClient reported closed");
        assertTrue(wasClosedAfter[0], "CefDevToolsClient did not report closed after close()");
    }
}
