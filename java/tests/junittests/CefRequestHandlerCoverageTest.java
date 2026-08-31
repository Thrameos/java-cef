// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.cef.browser.CefBrowser;
import org.cef.handler.CefRequestHandler.TerminationStatus;
import org.cef.handler.CefRequestHandlerAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.CountDownLatch;

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
//
// Migrated to the shared-browser (Tier 1) harness -- see plan/roadmap.md's
// "two-tier test harness" entry.
@ExtendWith({TestSetupExtension.class, SharedBrowserExtension.class})
class CefRequestHandlerCoverageTest {
    private static final String CONTENT = "<html><body>request handler coverage</body></html>";

    @Test
    void renderProcessTerminatedFiresForARealRendererCrash() {
        TerminationStatus[] lastStatus = {null};
        CountDownLatch terminated = new CountDownLatch(1);

        SharedBrowserExtension.addRequestHandler(new CefRequestHandlerAdapter() {
            @Override
            public void onRenderProcessTerminated(CefBrowser browser, TerminationStatus status,
                    int errorCode, String errorString) {
                if (lastStatus[0] != null) return;
                lastStatus[0] = status;
                terminated.countDown();
            }
        });

        SharedBrowserExtension.loadPage(CONTENT);
        // chrome://crash never itself finishes loading (that's the point) --
        // just fire the navigation and wait directly on the termination
        // callback rather than any loading-state signal.
        SharedBrowserExtension.browser().loadURL("chrome://crash");
        SharedBrowserExtension.awaitLatch(terminated, 15);

        assertNotNull(lastStatus[0], "onRenderProcessTerminated was never invoked");
    }
}
