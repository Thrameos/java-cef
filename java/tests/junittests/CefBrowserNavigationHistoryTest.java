// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// Round 2 of the CefBrowser_N.cpp API sweep (still the single largest
// remaining gap after CefBrowserApiTest/CefBrowserApiDebugSafeTest -- see
// plan/roadmap.md's Tier B item 3). Exercises real back/forward navigation
// history (canGoBack/goBack/canGoForward/goForward via two real page loads),
// reload()/reloadIgnoreCache(), and setFocus()/setWindowVisibility() -- all
// plain synchronous API calls against a live browser, same low-risk pattern
// as the earlier sweep.
//
// Sequenced via onLoadingStateChange() (which hands canGoBack/canGoForward
// directly as parameters) rather than onTitleChange() -- an earlier version
// of this test called browser.canGoBack() itself from inside onTitleChange()
// and got a stale/false answer, apparently because history-entry commit can
// lag the title-change event slightly; onLoadingStateChange's own
// isLoading=false transition is the more reliable signal already used
// elsewhere in this suite for sequencing multi-step navigation flows.
@ExtendWith(TestSetupExtension.class)
class CefBrowserNavigationHistoryTest {
    private static final String PAGE_ONE_URL = "http://test.com/nav_history_one.html";
    private static final String PAGE_TWO_URL = "http://test.com/nav_history_two.html";

    @Test
    void backAndForwardNavigationWorks() {
        boolean[] done = {false};
        boolean[] couldGoBackAfterSecondLoad = {false};
        boolean[] couldGoForwardAfterSecondLoad = {true};
        boolean[] couldGoForwardAfterBack = {false};
        String[] urlAfterBack = {null};
        String[] urlAfterForward = {null};

        TestFrame frame = new TestFrame() {
            int state = 0;

            @Override
            protected void setupTest() {
                addResource(PAGE_ONE_URL, "<html><body>one</body></html>", "text/html");
                addResource(PAGE_TWO_URL, "<html><body>two</body></html>", "text/html");
                createBrowser(PAGE_ONE_URL, true /* useOSR */);
                super.setupTest();
            }

            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                    boolean canGoBack, boolean canGoForward) {
                if (isLoading) return;
                switch (state) {
                    case 0:
                        state = 1;
                        browser.loadURL(PAGE_TWO_URL);
                        break;
                    case 1:
                        state = 2;
                        couldGoBackAfterSecondLoad[0] = canGoBack;
                        couldGoForwardAfterSecondLoad[0] = canGoForward;
                        browser.goBack();
                        break;
                    case 2:
                        state = 3;
                        urlAfterBack[0] = browser.getURL();
                        couldGoForwardAfterBack[0] = canGoForward;
                        browser.goForward();
                        break;
                    case 3:
                        state = 4;
                        urlAfterForward[0] = browser.getURL();
                        done[0] = true;
                        terminateTest();
                        break;
                    default:
                        break;
                }
            }
        };

        frame.awaitCompletion();

        assertTrue(done[0], "Navigation history sequence never completed");
        assertTrue(couldGoBackAfterSecondLoad[0], "canGoBack() should be true after two loads");
        assertFalse(couldGoForwardAfterSecondLoad[0],
                "canGoForward() should be false before going back");
        assertTrue(couldGoForwardAfterBack[0], "canGoForward() should be true after going back");
        assertTrue(PAGE_ONE_URL.equals(urlAfterBack[0]), "Expected " + PAGE_ONE_URL + " after "
                + "goBack(), got " + urlAfterBack[0]);
        assertTrue(PAGE_TWO_URL.equals(urlAfterForward[0]), "Expected " + PAGE_TWO_URL + " after "
                + "goForward(), got " + urlAfterForward[0]);
    }

    @Test
    void reloadAndVisibilityApisDoNotThrow() {
        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                addResource(PAGE_ONE_URL, "<html><body>reload test</body></html>", "text/html");
                createBrowser(PAGE_ONE_URL, true /* useOSR */);
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
        browser.setFocus(true);
        browser.setWindowVisibility(true);
        browser.reload();
        browser.reloadIgnoreCache();
    }
}
