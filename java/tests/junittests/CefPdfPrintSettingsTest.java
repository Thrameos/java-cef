// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.misc.CefPdfPrintSettings;
import org.cef.misc.CefPdfPrintSettings.MarginType;
import org.junit.jupiter.api.Test;

// CefPdfPrintSettings is a plain Java value object -- no CEF native lifecycle
// involved, so unlike most tests in this package this does not need
// @ExtendWith(TestSetupExtension.class).
class CefPdfPrintSettingsTest {
    @Test
    void defaultFieldValues() {
        CefPdfPrintSettings settings = new CefPdfPrintSettings();
        assertTrue(!settings.landscape);
        assertTrue(!settings.print_background);
        assertEquals(0.0, settings.scale);
        assertEquals(null, settings.margin_type);
        assertEquals(null, settings.page_ranges);
    }

    @Test
    void fieldsAreDirectlyAssignable() {
        CefPdfPrintSettings settings = new CefPdfPrintSettings();
        settings.landscape = true;
        settings.print_background = true;
        settings.scale = 0.5;
        settings.paper_width = 8.5;
        settings.paper_height = 11.0;
        settings.prefer_css_page_size = true;
        settings.margin_type = MarginType.CUSTOM;
        settings.margin_top = 1.0;
        settings.margin_right = 1.0;
        settings.margin_bottom = 1.0;
        settings.margin_left = 1.0;
        settings.page_ranges = "1-5, 8";
        settings.display_header_footer = true;
        settings.header_template = "<span class=title></span>";
        settings.footer_template = "<span class=pageNumber></span>";
        settings.generate_tagged_pdf = true;
        settings.generate_document_outline = true;

        assertTrue(settings.landscape);
        assertEquals(0.5, settings.scale);
        assertEquals(MarginType.CUSTOM, settings.margin_type);
        assertEquals("1-5, 8", settings.page_ranges);
        assertEquals("<span class=title></span>", settings.header_template);
        assertTrue(settings.generate_tagged_pdf);
        assertTrue(settings.generate_document_outline);
    }

    @Test
    void cloneProducesIndependentCopyWithEqualFields() {
        CefPdfPrintSettings settings = new CefPdfPrintSettings();
        settings.landscape = true;
        settings.scale = 0.75;
        settings.margin_type = MarginType.NONE;
        settings.page_ranges = "2-4";

        CefPdfPrintSettings copy = settings.clone();
        assertNotSame(settings, copy);
        assertEquals(settings.landscape, copy.landscape);
        assertEquals(settings.scale, copy.scale);
        assertEquals(settings.margin_type, copy.margin_type);
        assertEquals(settings.page_ranges, copy.page_ranges);

        // Mutating the copy must not affect the original.
        copy.scale = 1.0;
        assertEquals(0.75, settings.scale);
    }

    @Test
    void marginTypeEnumValues() {
        assertEquals(3, MarginType.values().length);
        assertEquals(MarginType.DEFAULT, MarginType.valueOf("DEFAULT"));
        assertEquals(MarginType.NONE, MarginType.valueOf("NONE"));
        assertEquals(MarginType.CUSTOM, MarginType.valueOf("CUSTOM"));
    }
}
