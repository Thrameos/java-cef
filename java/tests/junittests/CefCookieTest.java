// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.network.CefCookie;
import org.junit.jupiter.api.Test;

import java.util.Date;

// CefCookie is a plain Java value object -- no CEF native lifecycle involved.
class CefCookieTest {
    @Test
    void fieldsRoundTripThroughConstructor() {
        Date creation = new Date(1000);
        Date lastAccess = new Date(2000);
        Date expires = new Date(3000);

        CefCookie cookie = new CefCookie("name", "value", "example.com", "/path", true, false,
                creation, lastAccess, true, expires);

        assertEquals("name", cookie.name);
        assertEquals("value", cookie.value);
        assertEquals("example.com", cookie.domain);
        assertEquals("/path", cookie.path);
        assertTrue(cookie.secure);
        assertFalse(cookie.httponly);
        assertEquals(creation, cookie.creation);
        assertEquals(lastAccess, cookie.lastAccess);
        assertTrue(cookie.hasExpires);
        assertEquals(expires, cookie.expires);
    }

    @Test
    void noExpiresCookie() {
        Date creation = new Date();
        CefCookie cookie = new CefCookie(
                "session", "abc", "", "/", false, true, creation, creation, false, null);

        assertFalse(cookie.hasExpires);
        assertEquals(null, cookie.expires);
        assertTrue(cookie.httponly);
        // Empty domain means a host cookie, per the class's own documentation.
        assertEquals("", cookie.domain);
    }
}
