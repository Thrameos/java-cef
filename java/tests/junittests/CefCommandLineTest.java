// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// Exercises CefCommandLine (native/jni_util.cpp's string-vector/map marshaling
// for it, previously untested -- no public Java factory exists for this type).
// TestSetupExtension captures a snapshot of the real, browser-process
// CefCommandLine's getters via CefAppHandler.onBeforeCommandLineProcessing()
// during CefApp startup, before any @Test runs; this test reads that snapshot
// back and confirms it reflects the flags TestSetupExtension itself passes.
//
// The snapshot is plain data (Map/Vector/booleans), not the live CefCommandLine
// object: confirmed empirically that Chromium resets the underlying native
// command-line object sometime after onBeforeCommandLineProcessing returns
// (System.identityHashCode() of the Java wrapper stayed the same, but
// hasSwitches() flipped from true during the callback to false afterward) --
// see TestSetupExtension/TestSetupContext for the capture-time snapshotting
// this required. See plan/roadmap.md Track A item 3.
@ExtendWith(TestSetupExtension.class)
class CefCommandLineTest {
    @Test
    void capturedSnapshotIsNotNull() {
        assertNotNull(TestSetupContext.getCapturedCommandLineSnapshot(),
                "TestSetupExtension.onBeforeCommandLineProcessing never fired for the "
                        + "browser process");
    }

    @Test
    void switchesReflectFlagsPassedAtStartup() {
        TestSetupContext.CommandLineSnapshot snapshot =
                TestSetupContext.getCapturedCommandLineSnapshot();
        assertNotNull(snapshot);

        // These are exactly the flags TestSetupExtension.initialize() passes as
        // CefApp.getInstance(args, settings)'s args array.
        assertTrue(snapshot.hasSwitches);
        assertTrue(snapshot.switches.containsKey("disable-gpu"));
        assertTrue(snapshot.switches.containsKey("no-sandbox"));
    }

    @Test
    void switchWithEqualsHasItsValueInTheMap() {
        TestSetupContext.CommandLineSnapshot snapshot =
                TestSetupContext.getCapturedCommandLineSnapshot();
        assertNotNull(snapshot);

        // --use-gl=disabled has an explicit '=value' component.
        assertEquals("disabled", snapshot.switches.get("use-gl"));
    }

    @Test
    void switchesMapDoesNotContainUnrelatedName() {
        TestSetupContext.CommandLineSnapshot snapshot =
                TestSetupContext.getCapturedCommandLineSnapshot();
        assertNotNull(snapshot);

        assertTrue(!snapshot.switches.containsKey("this-switch-does-not-exist"));
    }
}
