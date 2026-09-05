// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.cef.misc.BoolRef;
import org.cef.misc.CefPageRange;
import org.cef.misc.IntRef;
import org.cef.misc.LongRef;
import org.cef.misc.StringRef;
import org.junit.jupiter.api.Test;

// Plain Java value-holder types -- no CEF native lifecycle involved, so unlike most
// tests in this package these do not need @ExtendWith(TestSetupExtension.class).
class RefTypesTest {
    @Test
    void stringRefDefaultsToNull() {
        StringRef ref = new StringRef();
        assertNull(ref.get());
    }

    @Test
    void stringRefConstructorAndSet() {
        StringRef ref = new StringRef("initial");
        assertEquals("initial", ref.get());
        ref.set("updated");
        assertEquals("updated", ref.get());
    }

    @Test
    void intRefDefaultsToZero() {
        IntRef ref = new IntRef();
        assertEquals(0, ref.get());
    }

    @Test
    void intRefConstructorAndSet() {
        IntRef ref = new IntRef(42);
        assertEquals(42, ref.get());
        ref.set(7);
        assertEquals(7, ref.get());
    }

    @Test
    void boolRefDefaultsToFalse() {
        BoolRef ref = new BoolRef();
        assertFalse(ref.get());
    }

    @Test
    void boolRefConstructorAndSet() {
        BoolRef ref = new BoolRef(true);
        assertTrue(ref.get());
        ref.set(false);
        assertFalse(ref.get());
    }

    @Test
    void longRefDefaultsToZero() {
        LongRef ref = new LongRef();
        assertEquals(0L, ref.get());
    }

    @Test
    void longRefConstructorAndSet() {
        LongRef ref = new LongRef(123456789012L);
        assertEquals(123456789012L, ref.get());
        ref.set(1L);
        assertEquals(1L, ref.get());
    }

    @Test
    void pageRangeDefaultConstructor() {
        CefPageRange range = new CefPageRange();
        assertEquals(0, range.from);
        assertEquals(0, range.to);
    }

    @Test
    void pageRangeConstructor() {
        CefPageRange range = new CefPageRange(2, 9);
        assertEquals(2, range.from);
        assertEquals(9, range.to);
    }
}
