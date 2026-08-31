// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.handler.CefLoadHandlerAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.CountDownLatch;
import javax.swing.SwingUtilities;

// Round 2 of the CefBrowser_N.cpp API sweep (still the single largest
// remaining gap after CefBrowserApiTest/CefBrowserApiDebugSafeTest -- see
// plan/roadmap.md's Tier B item 3). Exercises real back/forward navigation
// history (canGoBack/goBack/canGoForward/goForward via two real page loads),
// reload()/reloadIgnoreCache(), and setFocus()/setWindowVisibility() -- all
// plain synchronous API calls against a live browser, same low-risk pattern
// as the earlier sweep.
//
// Migrated to the shared-browser (Tier 1) harness -- see plan/roadmap.md's
// "two-tier test harness" entry. Unlike the original TestFrame version
// (which drove the whole back/forward/back/forward sequence from inside a
// single onLoadingStateChange() callback, cascading each next step off the
// previous one's completion), this version drives each navigation step from
// the JUnit thread itself, waiting on a fresh latch per step -- closer to
// how a real embedding application actually issues navigation commands (in
// response to independent, separate UI events), not a pattern real usage
// exhibits. See SharedBrowserExtension's own class comment on this
// distinction.
@ExtendWith({TestSetupExtension.class, SharedBrowserExtension.class})
class CefBrowserNavigationHistoryTest {
    private static void awaitNavigation(Runnable navigate) {
        CountDownLatch done = new CountDownLatch(1);
        SharedBrowserExtension.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                    boolean canGoBack, boolean canGoForward) {
                if (!isLoading) done.countDown();
            }
        });
        navigate.run();
        SharedBrowserExtension.awaitLatch(done, 15);
    }

    @Test
    void backAndForwardNavigationWorks() {
        CefBrowser browser = SharedBrowserExtension.browser();

        String pageOneUrl = SharedBrowserExtension.loadPage("<html><body>one</body></html>");
        String pageTwoUrl = SharedBrowserExtension.loadPage("<html><body>two</body></html>");

        boolean couldGoBackAfterSecondLoad = browser.canGoBack();
        boolean couldGoForwardAfterSecondLoad = browser.canGoForward();

        awaitNavigation(browser::goBack);
        String urlAfterBack = browser.getURL();
        boolean couldGoForwardAfterBack = browser.canGoForward();

        awaitNavigation(browser::goForward);
        String urlAfterForward = browser.getURL();

        assertTrue(couldGoBackAfterSecondLoad, "canGoBack() should be true after two loads");
        assertFalse(couldGoForwardAfterSecondLoad,
                "canGoForward() should be false before going back");
        assertTrue(couldGoForwardAfterBack, "canGoForward() should be true after going back");
        assertTrue(pageOneUrl.equals(urlAfterBack), "Expected " + pageOneUrl + " after "
                + "goBack(), got " + urlAfterBack);
        assertTrue(pageTwoUrl.equals(urlAfterForward), "Expected " + pageTwoUrl + " after "
                + "goForward(), got " + urlAfterForward);
    }

    @Test
    void reloadAndVisibilityApisDoNotThrow()
            throws InterruptedException, java.lang.reflect.InvocationTargetException {
        SharedBrowserExtension.loadPage("<html><body>reload test</body></html>");

        CefBrowser browser = SharedBrowserExtension.browser();
        // setFocus() must be EDT-consistent -- see CefFocusHandlerCoverageTest
        // and SharedBrowserExtension's own class comment for why this isn't
        // optional when called from the JUnit thread (unlike TestFrame's
        // pattern, loadPage() returns control to the calling thread, not the
        // EDT).
        SwingUtilities.invokeAndWait(() -> browser.setFocus(true));
        browser.setWindowVisibility(true);
        browser.reload();
        browser.reloadIgnoreCache();
    }
}
