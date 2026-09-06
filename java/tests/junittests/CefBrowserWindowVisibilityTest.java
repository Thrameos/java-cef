// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.cef.browser.CefBrowser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// Regression coverage for native/CefBrowser_N.cpp's N_SetWindowVisibility:
// previously the whole function body was gated on OS_MACOSX, and even there
// only ran when windowed (the IsWindowRenderingDisabled() check was
// backwards for that branch's intent) -- so on every platform, for every
// OSR (windowless) browser, setWindowVisibility() silently did nothing.
// Fixed by routing windowless browsers to CefBrowserHost::WasHidden()
// instead, CEF's own signal to pause/resume rendering and GPU resource
// usage for a browser with no native window.
//
// This is a smoke test (doesn't throw, browser stays usable) rather than a
// direct assertion on paused/resumed rendering state, since that internal
// state isn't observable from the Java side.
@ExtendWith(TestSetupExtension.class)
class CefBrowserWindowVisibilityTest {
    private static final String TEST_URL = "http://test.com/window_visibility_test.html";

    @Test
    void setWindowVisibilityOnOsrBrowserDoesNotThrow() {
        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                addResource(TEST_URL, "<html><body>Visibility Test</body></html>", "text/html");
                createBrowser(TEST_URL, true /* useOSR */);
                super.setupTest();
            }

            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                    boolean canGoBack, boolean canGoForward) {
                if (isLoading) return;
                terminateTest();
            }
        };

        frame.awaitCompletion();

        CefBrowser browser = frame.browser_;
        assertDoesNotThrow(() -> browser.setWindowVisibility(false));
        assertDoesNotThrow(() -> browser.setWindowVisibility(true));
    }
}
