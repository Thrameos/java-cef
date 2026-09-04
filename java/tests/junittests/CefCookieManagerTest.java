// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.misc.BoolRef;
import org.cef.network.CefCookie;
import org.cef.network.CefCookieManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@ExtendWith(TestSetupExtension.class)
class CefCookieManagerTest {
    private static final String TEST_URL = "http://cookietest.example.com/";

    @Test
    void getGlobalManagerReturnsInstance() {
        CefCookieManager manager = CefCookieManager.getGlobalManager();
        assertNotNull(manager);
    }

    @Test
    void setAndVisitCookie() throws InterruptedException {
        CefCookieManager manager = CefCookieManager.getGlobalManager();

        CefCookie cookie = new CefCookie("jcef_test_cookie", "jcef_test_value", "", "/", false,
                false, new Date(), new Date(), false, null);
        assertTrue(manager.setCookie(TEST_URL, cookie));

        AtomicBoolean found = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        manager.visitUrlCookies(TEST_URL, true, (visited, count, total, delete) -> {
            if ("jcef_test_cookie".equals(visited.name)) {
                found.set(true);
            }
            latch.countDown();
            return true;
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Cookie visitor callback timed out");
        assertTrue(found.get(), "Previously-set cookie was not visited");

        // Cleanup so this test doesn't leak state into other tests.
        deleteAndAwait(manager, TEST_URL, "jcef_test_cookie");
    }

    // CefCookieManager_N.cpp's N_VisitAllCookies was 0% covered -- every other
    // test here uses visitUrlCookies(), a different downcall.
    @Test
    void setAndVisitAllCookies() throws InterruptedException {
        CefCookieManager manager = CefCookieManager.getGlobalManager();

        CefCookie cookie = new CefCookie("jcef_test_cookie_all", "jcef_test_value", "", "/",
                false, false, new Date(), new Date(), false, null);
        assertTrue(manager.setCookie(TEST_URL, cookie));

        AtomicBoolean found = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        manager.visitAllCookies((visited, count, total, delete) -> {
            if ("jcef_test_cookie_all".equals(visited.name)) {
                found.set(true);
                latch.countDown();
            }
            return true;
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS), "visitAllCookies callback timed out");
        assertTrue(found.get(), "Previously-set cookie was not visited by visitAllCookies");

        deleteAndAwait(manager, TEST_URL, "jcef_test_cookie_all");
    }

    @Test
    void deleteCookies() throws InterruptedException {
        CefCookieManager manager = CefCookieManager.getGlobalManager();

        CefCookie cookie = new CefCookie("jcef_delete_test", "value", "", "/", false, false,
                new Date(), new Date(), false, null);
        assertTrue(manager.setCookie(TEST_URL, cookie));

        assertTrue(manager.deleteCookies(TEST_URL, "jcef_delete_test"));

        AtomicBoolean found = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        boolean hasVisitor = manager.visitUrlCookies(TEST_URL, true, (visited, count, total, delete) -> {
            if ("jcef_delete_test".equals(visited.name)) {
                found.set(true);
            }
            if (count == total - 1) latch.countDown();
            return true;
        });
        // If there are now no cookies at all for this URL the visitor may never be
        // invoked (per CefCookieVisitor's own documentation), so don't block forever.
        if (hasVisitor) {
            latch.await(5, TimeUnit.SECONDS);
        }
        assertTrue(!found.get(), "Deleted cookie was still visited");
    }

    @Test
    void visitorReturningFalseStopsEarly() throws InterruptedException {
        CefCookieManager manager = CefCookieManager.getGlobalManager();

        CefCookie cookieA = new CefCookie("jcef_stop_early_a", "a", "", "/", false, false,
                new Date(), new Date(), false, null);
        CefCookie cookieB = new CefCookie("jcef_stop_early_b", "b", "", "/", false, false,
                new Date(), new Date(), false, null);
        assertTrue(manager.setCookie(TEST_URL, cookieA));
        assertTrue(manager.setCookie(TEST_URL, cookieB));

        AtomicBoolean visitedMoreThanOne = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        manager.visitUrlCookies(TEST_URL, true, (visited, count, total, delete) -> {
            if (count > 0) visitedMoreThanOne.set(true);
            latch.countDown();
            // Returning false must stop visiting after just this one cookie.
            return false;
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Cookie visitor callback timed out");
        assertTrue(!visitedMoreThanOne.get(),
                "Visitor was invoked again after returning false from visit()");

        deleteAndAwait(manager, TEST_URL, "jcef_stop_early_a");
        deleteAndAwait(manager, TEST_URL, "jcef_stop_early_b");
    }

    @Test
    void visitorSettingDeleteRefRemovesCookie() throws InterruptedException {
        CefCookieManager manager = CefCookieManager.getGlobalManager();

        CefCookie cookie = new CefCookie("jcef_delete_via_visitor", "value", "", "/", false,
                false, new Date(), new Date(), false, null);
        assertTrue(manager.setCookie(TEST_URL, cookie));

        CountDownLatch visitLatch = new CountDownLatch(1);
        manager.visitUrlCookies(TEST_URL, true, (visited, count, total, delete) -> {
            if ("jcef_delete_via_visitor".equals(visited.name)) {
                delete.set(true);
            }
            visitLatch.countDown();
            return true;
        });
        assertTrue(visitLatch.await(10, TimeUnit.SECONDS), "Cookie visitor callback timed out");

        AtomicBoolean stillFound = new AtomicBoolean(false);
        CountDownLatch confirmLatch = new CountDownLatch(1);
        boolean hasVisitor = manager.visitUrlCookies(TEST_URL, true, (visited, count, total, delete) -> {
            if ("jcef_delete_via_visitor".equals(visited.name)) stillFound.set(true);
            if (count == total - 1) confirmLatch.countDown();
            return true;
        });
        if (hasVisitor) {
            confirmLatch.await(5, TimeUnit.SECONDS);
        }
        assertTrue(!stillFound.get(),
                "Cookie was still present after visitor set delete=true");
    }

    @Test
    void flushStore() throws InterruptedException {
        CefCookieManager manager = CefCookieManager.getGlobalManager();
        CountDownLatch latch = new CountDownLatch(1);
        assertTrue(manager.flushStore(() -> latch.countDown()));
        assertTrue(latch.await(10, TimeUnit.SECONDS), "flushStore completion callback timed out");
    }

    private void deleteAndAwait(CefCookieManager manager, String url, String name)
            throws InterruptedException {
        manager.deleteCookies(url, name);
    }
}
