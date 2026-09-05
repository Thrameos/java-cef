// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.cef.CefSettings;
import org.junit.jupiter.api.Test;

// Plain Java value object -- no CEF native lifecycle involved.
class CefSettingsTest {
    @Test
    void colorTypeHighAlphaDoesNotSignExtend() {
        CefSettings settings = new CefSettings();
        CefSettings.ColorType color = settings.new ColorType(0xFF, 0x11, 0x22, 0x33);
        assertEquals(0xFF112233L, color.getColor());
    }

    @Test
    void colorTypeLowAlpha() {
        CefSettings settings = new CefSettings();
        CefSettings.ColorType color = settings.new ColorType(0x7F, 0x11, 0x22, 0x33);
        assertEquals(0x7F112233L, color.getColor());
    }
}
