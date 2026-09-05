// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefMessageRouter;
import org.cef.browser.CefMessageRouter.CefMessageRouterConfig;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// No-browser-needed coverage for CefMessageRouter's registration surface,
// including a regression test for a native DCHECK(handle) crash: see
// Thrameos/java-cef's "Fix DCHECK(handle) crash in
// CefMessageRouter.cancelPending(null, null)".
// CefMessageRouter_N.cpp's local GetHandler() used to unconditionally
// construct a ScopedJNIObject over the (nullable, per this method's own
// Javadoc) routerHandler argument before checking it -- that constructor
// DCHECKs its handle is non-null, so cancelPending(null, null) crashed the
// process. Fixed by short-circuiting GetHandler() on a null handle first.
@ExtendWith(TestSetupExtension.class)
class CefMessageRouterTest {
    @Test
    void createAddRemoveHandlerWithoutABrowser() {
        CefMessageRouter router = CefMessageRouter.create();
        assertNotNull(router);

        CefMessageRouterHandlerAdapter handler = new CefMessageRouterHandlerAdapter() {};
        assertTrue(router.addHandler(handler, true));

        router.cancelPending(null, handler);
        // Regression case: (null, null) cancels ALL pending queries
        // process-wide and used to hit the native DCHECK described above.
        router.cancelPending(null, null);

        assertTrue(router.removeHandler(handler));

        router.dispose();
    }

    @Test
    void configDefaultsMatchDocumentedJsFunctionNames() {
        CefMessageRouterConfig config = new CefMessageRouterConfig();
        assertEquals("cefQuery", config.jsQueryFunction);
        assertEquals("cefQueryCancel", config.jsCancelFunction);

        CefMessageRouter router = CefMessageRouter.create(config);
        assertEquals(config, router.getMessageRouterConfig());
        router.dispose();
    }
}
