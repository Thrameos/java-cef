// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.misc.BoolRef;
import org.cef.network.CefCookie;
import org.cef.network.CefCookieManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// Regression-guard test for upstream chromiumembedded/java-cef#405: cookies
// set via a real Set-Cookie response header during an actual page
// navigation (as in a real login flow) should be visible via
// CefCookieManager.visitAllCookies()/visitUrlCookies() shortly afterward --
// upstream reports they never appear. Unlike CefCookieManagerTest's existing
// coverage (which only exercises cookies set directly via
// CefCookieManager.setCookie(), a different code path), this test sets the
// cookie the same way the upstream report does: via a real Set-Cookie header
// on a real navigation response.
//
// CONFIRMED WORKING for this basic same-origin case as of this fork's
// current CEF version (146.0.10). Not a full confirmation the upstream bug
// is fixed, though: the original report used a real external HTTPS site
// (spigotmc.org) via a third-party wrapper library (Pandomium) and a
// same-origin form POST + JS-driven navigation -- there may be a real
// difference for a genuinely cross-origin/third-party-cookie scenario (e.g.
// SameSite policy interactions) that this fork's local, same-origin,
// locally-served-response test doesn't exercise. Kept as a regression guard
// for the case it does cover; no fork issue filed.
@ExtendWith(TestSetupExtension.class)
class UpstreamIssue405Test {
    private static final String LOGIN_URL = "http://test.com/upstream_issue_405_login.html";
    private static final String COOKIE_NAME = "jcef_login_session";
    private static final String COOKIE_VALUE = "abc123";

    @Test
    void cookieSetViaRealNavigationResponseIsVisibleAfterward() throws InterruptedException {
        AtomicBoolean found = new AtomicBoolean(false);
        CountDownLatch visited = new CountDownLatch(1);

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                HashMap<String, String> headers = new HashMap<>();
                headers.put(
                        "Set-Cookie", COOKIE_NAME + "=" + COOKIE_VALUE + "; Path=/; SameSite=Lax");
                addResource(LOGIN_URL, "<html><body>logged in</body></html>", "text/html",
                        headers);
                createBrowser(LOGIN_URL, true /* useOSR */);
                super.setupTest();
            }

            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                    boolean canGoBack, boolean canGoForward) {
                if (!isLoading) terminateTest();
            }
        };

        frame.awaitCompletion();

        // Same as the upstream report's own repro shape: query cookies
        // shortly after the navigation that set them, on a fresh call to
        // the global cookie manager (not the same objects/handles used
        // during the navigation itself).
        CefCookieManager.getGlobalManager().visitUrlCookies(
                LOGIN_URL, false, (cookie, count, total, delete) -> {
                    if (COOKIE_NAME.equals(cookie.name) && COOKIE_VALUE.equals(cookie.value)) {
                        found.set(true);
                    }
                    visited.countDown();
                    return true;
                });

        assertTrue(visited.await(10, TimeUnit.SECONDS), "Cookie visitor callback never fired");
        assertTrue(found.get(), "Cookie set via a real Set-Cookie response header during "
                + "navigation was not visible afterward via visitUrlCookies()");
    }
}
