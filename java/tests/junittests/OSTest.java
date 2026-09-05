// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.OS;
import org.junit.jupiter.api.Test;

// OS detection is a plain static utility -- no CEF native lifecycle involved.
class OSTest {
    @Test
    void exactlyOnePlatformIsDetected() {
        // This test bench only ever runs on Linux, macOS, or Windows -- exactly one
        // of these three must be true (OS.java's OSUnknown fallback would make all
        // three false, which would itself be a bug worth catching here).
        int trueCount = 0;
        if (OS.isLinux()) trueCount++;
        if (OS.isMacintosh()) trueCount++;
        if (OS.isWindows()) trueCount++;
        assertEquals(1, trueCount);
    }

    @Test
    void detectionIsConsistentAcrossCalls() {
        // OS caches its detection result internally -- verify repeated calls agree.
        assertEquals(OS.isLinux(), OS.isLinux());
        assertEquals(OS.isMacintosh(), OS.isMacintosh());
        assertEquals(OS.isWindows(), OS.isWindows());
    }

    @Test
    void currentOsNameMatchesDetection() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.startsWith("linux")) {
            assertTrue(OS.isLinux());
        } else if (osName.startsWith("mac")) {
            assertTrue(OS.isMacintosh());
        } else if (osName.startsWith("windows")) {
            assertTrue(OS.isWindows());
        }
    }
}
