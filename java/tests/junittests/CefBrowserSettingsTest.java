// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.cef.CefBrowserSettings;
import org.junit.jupiter.api.Test;

// Plain Java value object -- no CEF native lifecycle involved.
class CefBrowserSettingsTest {
    @Test
    void defaultValue() {
        CefBrowserSettings settings = new CefBrowserSettings();
        assertEquals(0, settings.windowless_frame_rate);
    }

    @Test
    void cloneProducesIndependentCopy() {
        CefBrowserSettings settings = new CefBrowserSettings();
        settings.windowless_frame_rate = 30;

        CefBrowserSettings copy = settings.clone();
        assertNotSame(settings, copy);
        assertEquals(30, copy.windowless_frame_rate);

        copy.windowless_frame_rate = 60;
        assertEquals(30, settings.windowless_frame_rate);
    }
}
