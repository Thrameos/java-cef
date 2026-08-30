// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.cef.browser.CefBrowser;
import org.cef.handler.CefRequestHandler.TerminationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// native/request_handler.cpp (96 lines, 34% covered per this session's baseline):
// exercises RequestHandler::OnRenderProcessTerminated via Chromium's own
// `chrome://crash` debug URL -- a real, Chromium-blessed mechanism for
// deterministically crashing a renderer process for testing purposes (not a
// synthesized/simulated crash), reliably delivering a real TerminationStatus.
// The other uncovered paths in this file (OnOpenURLFromTab, GetAuthCredentials,
// OnCertificateError) all need either real popup-window handling, an HTTP 401
// challenge, or an invalid HTTPS certificate to trigger -- each meaningfully
// harder to synthesize safely/reliably in this headless OSR environment than
// this one, left as an open gap rather than a flaky test.
@ExtendWith(TestSetupExtension.class)
class CefRequestHandlerCoverageTest {
    private static final String TEST_URL = "http://test.com/request_handler_coverage.html";
    private static final String CONTENT = "<html><body>request handler coverage</body></html>";

    @Test
    void renderProcessTerminatedFiresForARealRendererCrash() {
        TerminationStatus[] lastStatus = {null};

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                addResource(TEST_URL, CONTENT, "text/html");
                createBrowser(TEST_URL, true /* useOSR */);
                super.setupTest();
            }

            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                    boolean canGoBack, boolean canGoForward) {
                if (isLoading || lastStatus[0] != null) return;
                // Only navigate once, on the initial page's completed load --
                // chrome://crash never itself finishes loading (that's the
                // point), so this guard (lastStatus[0] != null once the
                // crash is observed) also prevents re-navigating on any
                // loading-state churn the crash itself might produce.
                browser.loadURL("chrome://crash");
            }

            @Override
            public void onRenderProcessTerminated(CefBrowser browser, TerminationStatus status,
                    int error_code, String error_string) {
                lastStatus[0] = status;
                terminateTest();
            }
        };

        frame.awaitCompletion();

        assertNotNull(lastStatus[0], "onRenderProcessTerminated was never invoked");
    }
}
