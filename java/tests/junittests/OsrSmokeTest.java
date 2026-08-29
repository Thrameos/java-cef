// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// Smoke test using an off-screen-rendered (OSR) browser, whose close handshake does
// not depend on native window-destroy notification (see TestFrame's windowClosing
// comment). Used as an environment sanity check that avoids the windowed-browser
// close hang tracked separately (see plan/findings.md, upstream java-cef#364).
@ExtendWith(TestSetupExtension.class)
class OsrSmokeTest {
    private boolean gotLoadingStateChange_ = false;

    @Test
    void minimal() {
        final String testUrl = "http://test.com/osr_test.html";
        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                addResource(testUrl, "<html><body>OSR Test!</body></html>", "text/html");
                createBrowser(testUrl, true /* useOSR */);
                super.setupTest();
            }

            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                    boolean canGoBack, boolean canGoForward) {
                if (!isLoading) {
                    gotLoadingStateChange_ = true;
                    terminateTest();
                }
            }
        };

        frame.awaitCompletion();

        assertTrue(gotLoadingStateChange_);
    }
}
