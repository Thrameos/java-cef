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
