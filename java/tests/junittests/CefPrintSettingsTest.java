// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.misc.CefPageRange;
import org.cef.misc.CefPrintSettings;
import org.cef.misc.CefPrintSettings.ColorModel;
import org.cef.misc.CefPrintSettings.DuplexMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.Vector;

@ExtendWith(TestSetupExtension.class)
class CefPrintSettingsTest {
    @Test
    void createDefault() {
        CefPrintSettings settings = CefPrintSettings.create();
        assertNotNull(settings);
        assertTrue(settings.isValid());
        assertFalse(settings.isReadOnly());
        settings.dispose();
    }

    @Test
    void setAndGetOrientation() {
        CefPrintSettings settings = CefPrintSettings.create();
        settings.setOrientation(true);
        assertTrue(settings.isLandscape());
        settings.setOrientation(false);
        assertFalse(settings.isLandscape());
        settings.dispose();
    }

    @Test
    void setAndGetDeviceName() {
        CefPrintSettings settings = CefPrintSettings.create();
        settings.setDeviceName("Test Printer");
        assertEquals("Test Printer", settings.getDeviceName());
        settings.dispose();
    }

    @Test
    void setAndGetDPI() {
        CefPrintSettings settings = CefPrintSettings.create();
        settings.setDPI(300);
        assertEquals(300, settings.getDPI());
        settings.dispose();
    }

    @Test
    void setAndGetPageRanges() {
        CefPrintSettings settings = CefPrintSettings.create();
        Vector<CefPageRange> ranges = new Vector<>();
        ranges.add(new CefPageRange(1, 3));
        ranges.add(new CefPageRange(5, 7));
        settings.setPageRanges(ranges);

        assertEquals(2, settings.getPageRangesCount());

        Vector<CefPageRange> readBack = new Vector<>();
        settings.getPageRanges(readBack);
        assertEquals(2, readBack.size());
        assertEquals(1, readBack.get(0).from);
        assertEquals(3, readBack.get(0).to);
        assertEquals(5, readBack.get(1).from);
        assertEquals(7, readBack.get(1).to);

        settings.dispose();
    }

    @Test
    void setAndGetSelectionOnly() {
        CefPrintSettings settings = CefPrintSettings.create();
        settings.setSelectionOnly(true);
        assertTrue(settings.isSelectionOnly());
        settings.dispose();
    }

    @Test
    void setAndGetCollate() {
        CefPrintSettings settings = CefPrintSettings.create();
        settings.setCollate(true);
        assertTrue(settings.willCollate());
        settings.dispose();
    }

    @Test
    void setAndGetColorModel() {
        CefPrintSettings settings = CefPrintSettings.create();
        settings.setColorModel(ColorModel.COLOR_MODEL_GRAY);
        assertEquals(ColorModel.COLOR_MODEL_GRAY, settings.getColorModel());
        settings.dispose();
    }

    @Test
    void setAndGetCopies() {
        CefPrintSettings settings = CefPrintSettings.create();
        settings.setCopies(3);
        assertEquals(3, settings.getCopies());
        settings.dispose();
    }

    @Test
    void setAndGetDuplexMode() {
        CefPrintSettings settings = CefPrintSettings.create();
        settings.setDuplexMode(DuplexMode.DUPLEX_MODE_LONG_EDGE);
        assertEquals(DuplexMode.DUPLEX_MODE_LONG_EDGE, settings.getDuplexMode());
        settings.dispose();
    }

    @Test
    void setPrinterPrintableArea() {
        CefPrintSettings settings = CefPrintSettings.create();
        // Just verify this doesn't throw -- there's no getter to read the values back.
        settings.setPrinterPrintableArea(
                new Dimension(850, 1100), new Rectangle(0, 0, 850, 1100), true);
        settings.dispose();
    }
}
