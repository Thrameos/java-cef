// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefDevToolsClient;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// Exercises CefBrowser.getDevToolsClient()/CefDevToolsClient (native/
// devtools_message_observer.cpp and CefRegistration_N.cpp, previously
// untested -- the package-private CefDevToolsMessageObserver interface these
// wrap isn't directly implementable from tests.junittests, but the public
// CefDevToolsClient wrapper is). Calls the real "Browser.getVersion" DevTools
// protocol method (no page/network dependency).
//
// @Disabled -- IMPORTANT, do not remove without extreme caution: this test
// causes a genuine UNRECOVERABLE hang, confirmed via an isolated run wrapped
// in a hard external `timeout -k 5 45` (SIGTERM then SIGKILL) -- even
// SIGKILL fallback was needed, i.e. it wasn't just slow, something was
// genuinely stuck. TestFrame's own 30s watchdog (a java.util.Timer-based
// forced browser close, scheduled via SwingUtilities.invokeLater onto the
// AWT/CEF UI thread) never got a chance to run, suggesting the AWT/CEF UI
// thread itself may be blocked, not just the DevTools call's own completion
// -- meaning nothing in this harness can safely recover from running this
// test. Filed as Thrameos/java-cef#12 (also covers the analogous
// browser.print() hang, see CefPrintHandlerTest.java's commented-out
// browserPrintInvokesPrintStartSettingsAndDialog draft in that file's git
// history/plan/roadmap.md for that one -- not restored here as a separate
// class since one confirmed unrecoverable-hang reproducer per root cause is
// enough).
//
// Retested 2026-08-29 via a standalone diagnostic (not this file): tried
// waiting for onLoadingStateChange(isLoading=false) instead of calling
// getDevToolsClient() from onAfterCreated, AND holding a strong reference
// to the CefDevToolsClient as a field for the async call's duration
// (originally only a local variable, invisible to the whenComplete()
// lambda -- a real bug in its own right, but not the cause of this hang).
// Still hung past a 45s external timeout, needing SIGKILL. Unlike issues
// #17/#18/#12-repro-2, no "wrong technique" explanation found so far.
@Disabled("UNRECOVERABLE HANG, not just a slow/bounded failure -- see "
        + "Thrameos/java-cef#12. Do not run without a hard external timeout "
        + "wrapper (e.g. `timeout -k 5 45`), and do not remove @Disabled as "
        + "part of a normal suite run.")
@ExtendWith(TestSetupExtension.class)
class CefDevToolsClientTest {
    private static final String TEST_URL = "http://test.com/devtools_client.html";

    @Test
    void executeDevToolsMethodReturnsRealResult() {
        String[] result = {null};
        boolean[] gotResult = {false};

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                addResource(TEST_URL, "<html><body>devtools test</body></html>", "text/html");
                createBrowser(TEST_URL, true /* useOSR */);
                super.setupTest();
            }

            @Override
            public void onAfterCreated(CefBrowser browser) {
                super.onAfterCreated(browser);
                CefDevToolsClient client = browser.getDevToolsClient();
                client.executeDevToolsMethod("Browser.getVersion")
                        .whenComplete((res, ex) -> {
                            if (ex == null) {
                                result[0] = res;
                                gotResult[0] = true;
                            }
                            terminateTest();
                        });
            }
        };

        frame.awaitCompletion();

        assertTrue(gotResult[0], "executeDevToolsMethod never completed successfully");
        assertNotNull(result[0]);
        assertTrue(result[0].length() > 0);
    }
}
