// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefDevToolsClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// Exercises CefBrowser.getDevToolsClient()/CefDevToolsClient (native/
// devtools_message_observer.cpp and CefRegistration_N.cpp, previously
// untested -- the package-private CefDevToolsMessageObserver interface these
// wrap isn't directly implementable from tests.junittests, but the public
// CefDevToolsClient wrapper is).
//
// ROOT CAUSE FOUND 2026-08-29 for the long-standing Thrameos/java-cef#12
// "unrecoverable hang" on this path: it was specific to calling
// "Browser.getVersion" (a *browser*-domain DevTools command). A standalone
// diagnostic (DiagDevToolsAttach, since deleted -- see plan/roadmap.md for
// the full transcript) added CefDevToolsClient.isAgentAttached() polling
// from a plain, non-AWT thread and an independent watchdog, and found:
//   - The DevTools agent attaches near-instantly (within ~2ms of the first
//     message send), exactly as CefDevToolsMessageObserver's own
//     documentation predicts.
//   - "Browser.getVersion" specifically then NEVER receives a matching
//     OnDevToolsMethodResult callback -- confirmed not a race (explicit
//     non-zero incrementing message IDs, matching CEF's own ceftests
//     technique in devtools_message_unittest.cc, made no difference).
//   - Substituting a *page*-domain command ("Page.enable", the same command
//     CEF's own ceftests devtools_message_unittest.cc exercises) completes
//     in ~2ms with no hang at all, across repeated runs.
//   - The AWT/CEF UI thread is NOT stuck: TestFrame's own watchdog can
//     cleanly force-close the browser (windowClosing/doClose/onBeforeClose
//     all fire normally) even when the Browser.getVersion call is left
//     hanging -- correcting this file's older note that the UI thread
//     itself might be blocked.
// Likely explanation: JCEF forces CEF_RUNTIME_STYLE_ALLOY for all browsers
// (see the comment in native/CefBrowser_N.cpp's create()), and the DevTools
// Browser-domain command handler may not be fully wired for an
// Alloy-runtime-style, off-screen-rendered CefBrowserHost in this CEF
// version -- Page-domain commands, which are tied to the renderer/page
// rather than the browser-process-level Browser domain, are unaffected.
// This is a real CEF/JCEF integration limitation, not a test-harness bug;
// documented on Thrameos/java-cef#12 rather than filed as a wholly new
// issue, since it's the root cause of that one.
@ExtendWith(TestSetupExtension.class)
class CefDevToolsClientTest {
    private static final String TEST_URL = "http://test.com/devtools_client.html";

    @Test
    void executeDevToolsMethodReturnsRealResult() {
        String[] result = {null};
        boolean[] gotResult = {false};
        boolean[] wasAttachedBefore = {true};
        boolean[] wasAttachedAfter = {false};

        TestFrame frame = new TestFrame() {
            CefDevToolsClient client;

            @Override
            protected void setupTest() {
                addResource(TEST_URL, "<html><body>devtools test</body></html>", "text/html");
                createBrowser(TEST_URL, true /* useOSR */);
                super.setupTest();
            }

            @Override
            public void onAfterCreated(CefBrowser browser) {
                super.onAfterCreated(browser);
                client = browser.getDevToolsClient();
                wasAttachedBefore[0] = client.isAgentAttached();
                // "Page.enable" is a page-domain command, unlike the
                // browser-domain "Browser.getVersion" this test used to
                // call (see the class comment for why that hung).
                client.executeDevToolsMethod("Page.enable").whenComplete((res, ex) -> {
                    if (ex == null) {
                        result[0] = res;
                        gotResult[0] = true;
                    }
                    wasAttachedAfter[0] = client.isAgentAttached();
                    terminateTest();
                });
            }
        };

        frame.awaitCompletion();

        assertFalse(wasAttachedBefore[0], "Agent reported attached before any message was sent");
        assertTrue(gotResult[0], "executeDevToolsMethod never completed successfully");
        assertNotNull(result[0]);
        assertTrue(wasAttachedAfter[0], "Agent did not report attached after a message round-trip");
    }
}
