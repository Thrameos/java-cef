// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.CefSettings;
import org.cef.CefSettings.ColorType;
import org.cef.CefSettings.LogSeverity;
import org.junit.jupiter.api.Test;

// Plain Java value object -- no CEF native lifecycle involved.
class CefSettingsTest {
    @Test
    void defaultValues() {
        CefSettings settings = new CefSettings();
        assertNull(settings.cache_path);
        assertTrue(settings.windowless_rendering_enabled);
        assertFalse(settings.command_line_args_disabled);
        assertEquals(LogSeverity.LOGSEVERITY_DEFAULT, settings.log_severity);
        assertNull(settings.background_color);
    }

    @Test
    void colorTypePacksArgbComponentsWithLowAlpha() {
        // alpha < 0x80 keeps the packed int non-negative, so widening it to the
        // long-typed color_value field doesn't sign-extend -- this is the "clean"
        // case where getColor() returns the value a caller would naturally expect.
        CefSettings settings = new CefSettings();
        ColorType color = settings.new ColorType(0x7F, 0x11, 0x22, 0x33);
        assertEquals(0x7F112233L, color.getColor());
    }

    @Test
    void colorTypeHighAlphaDoesNotSignExtend() {
        // Regression test for Thrameos/java-cef#8: ColorType(int,int,int,int) used
        // to pack components via 32-bit int arithmetic ((alpha << 24) | ...) and
        // only then assign the result to the long-typed color_value field. With
        // alpha >= 0x80 the packed int was negative (top bit set), and Java's
        // implicit int->long widening sign-extended it, so getColor() returned a
        // value with all the long's upper 32 bits set to 1 instead of the plain
        // unsigned 32-bit ARGB value this field's own Javadoc describes. Fixed by
        // packing in long-typed arithmetic and masking to 32 bits.
        CefSettings settings = new CefSettings();
        ColorType color = settings.new ColorType(0xFF, 0x11, 0x22, 0x33);
        assertEquals(0xFF112233L, color.getColor());
    }

    @Test
    void colorTypeCloneProducesIndependentCopyWithSameValue() {
        CefSettings settings = new CefSettings();
        ColorType color = settings.new ColorType(0xFF, 0x01, 0x02, 0x03);
        ColorType copy = color.clone();
        assertNotSame(color, copy);
        assertEquals(color.getColor(), copy.getColor());
    }

    @Test
    void cloneProducesIndependentCopy() {
        CefSettings settings = new CefSettings();
        settings.user_agent = "jcef-test-agent";
        settings.log_severity = LogSeverity.LOGSEVERITY_VERBOSE;
        settings.remote_debugging_port = 9222;
        settings.background_color = settings.new ColorType(0xFF, 0, 0, 0);

        CefSettings copy = settings.clone();
        assertNotSame(settings, copy);
        assertEquals("jcef-test-agent", copy.user_agent);
        assertEquals(LogSeverity.LOGSEVERITY_VERBOSE, copy.log_severity);
        assertEquals(9222, copy.remote_debugging_port);
        assertNotSame(settings.background_color, copy.background_color);
        assertEquals(settings.background_color.getColor(), copy.background_color.getColor());

        // Mutating the copy must not affect the original.
        copy.user_agent = "changed";
        assertEquals("jcef-test-agent", settings.user_agent);
    }

    @Test
    void logSeverityEnumValues() {
        assertEquals(7, LogSeverity.values().length);
        assertEquals(LogSeverity.LOGSEVERITY_DISABLE, LogSeverity.valueOf("LOGSEVERITY_DISABLE"));
    }
}
