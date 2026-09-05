// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefRequestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(TestSetupExtension.class)
class CefRequestContextTest {
    @Test
    void globalContextIsGlobal() {
        CefRequestContext context = CefRequestContext.getGlobalContext();
        assertNotNull(context);
        assertTrue(context.isGlobal());
        // The global context has no explicit handler.
        assertNull(context.getHandler());
    }

    @Test
    void createContextWithNullHandler() {
        CefRequestContext context = CefRequestContext.createContext(null);
        assertNotNull(context);
        assertFalse(context.isGlobal());
        assertNull(context.getHandler());
        context.dispose();
    }

    @Test
    void preferenceAccessorsOffUiThreadDoNotThrow() {
        // Per this class's own documentation, preference accessors must be called on
        // the browser process UI thread to do anything meaningful; off that thread
        // (as here, the JUnit test thread) they're documented to always return a
        // safe default rather than throw. Exercising that documented off-thread path
        // still covers the JNI marshaling for these methods.
        CefRequestContext context = CefRequestContext.getGlobalContext();

        assertFalse(context.hasPreference("does-not-matter"));
        assertNull(context.getPreference("does-not-matter"));
        assertFalse(context.canSetPreference("does-not-matter"));
        assertNotNull(context.setPreference("does-not-matter", "value"));
    }
}
