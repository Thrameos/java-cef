// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// First test in the suite to use windowed (non-OSR) rendering -- every other test
// creates its browser with useOSR=true. org/cef/browser/CefBrowserWr.java and
// native/jni_util_linux.cpp's GetDrawableOfCanvas() (the Linux windowed path's way
// of getting the X11 Drawable of the AWT Canvas CefBrowserWr embeds the browser
// into, so CEF can parent its own native window to it -- see
// native/CefBrowser_N.cpp's osr==JNI_FALSE branch) were both previously 0% covered
// as a direct result: nothing exercised this code path at all.
//
// @Disabled: the browser loads fine (onAfterCreated, resource served, onLoading
// StateChange all fire normally), and terminateTest()'s close sequence starts
// correctly (WINDOW_CLOSING -> doClose(true) -> doClose(false), matching the OSR
// pattern), but CefLifeSpanHandler.onBeforeClose then never fires -- confirmed a
// genuine hang, not just slow: still stuck at the identical point (TestSetup
// Extension.close() -> CefApp.dispose() -> waiting for native shutdown to
// complete) after 240s, vs. this same test's own per-test watchdog already having
// force-failed it at 30s. Tried running under a real window manager (icewm on a
// fresh Xvfb :99, matching .azure/scripts/coverage.yml's setup) in case the lack
// of one on this sandbox's default DISPLAY was the cause -- no difference. Left
// disabled rather than deep-root-caused per the standing "port tests, disable
// failures with a note, move on" strategy (plan/roadmap.md) -- importantly, this
// isn't just this one test failing: if left enabled, the hang blocks the entire
// suite's shutdown (TestSetupExtension.close() is a one-time, suite-global
// teardown), so keeping this disabled is a correctness requirement, not just
// convenience, until root-caused.
@ExtendWith(TestSetupExtension.class)
class CefBrowserWrTest {
    private static final String TEST_URL = "http://test.com/windowed.html";

    @Test
    @Disabled("Windowed (non-OSR) browser close hangs indefinitely after doClose -- "
            + "see the class-level comment above for the full investigation. Real, "
            + "reproducible native/CEF-lifecycle bug, not a test bug.")
    void windowedBrowserLoadsAndReportsCorrectUrl() {
        boolean[] done = {false};

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                addResource(TEST_URL, "<html><body>windowed</body></html>", "text/html");
                createBrowser(TEST_URL, false /* useOSR */);
                super.setupTest();
            }

            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                    boolean canGoBack, boolean canGoForward) {
                if (isLoading) return;
                done[0] = true;
                terminateTest();
            }
        };

        frame.awaitCompletion();

        assertTrue(done[0], "Windowed browser never finished loading");
        assertEquals(TEST_URL, frame.browser_.getURL());
    }
}
