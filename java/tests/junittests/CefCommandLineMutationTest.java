// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

// Coverage for CefCommandLine_N.cpp's N_Reset/N_GetProgram/N_SetProgram/
// N_HasSwitch/N_GetSwitchValue/N_AppendArgument (0% covered per the
// 2026-09-03 gap-table run). Unlike CefCommandLineTest (which only reads
// the browser-process CefCommandLine's getters via a passive startup
// snapshot), these six are mutators/lookups CefCommandLineTest never
// called -- and the real, live CefCommandLine object is only reachable
// once, synchronously, from CefAppHandler.onBeforeCommandLineProcessing()
// during CefApp startup (no public Java factory exists, see
// CefCommandLineTest's class comment).
//
// Exercising them in place would mean mutating the SHARED browser-process
// command line every other test class in this JVM depends on
// (--disable-gpu/--no-sandbox/etc., which CefCommandLineTest itself asserts
// on) -- flagged too risky for a normal suite run in
// plan/tasks/20260903-16-uncovered-paths-cover-or-disable.md's "Not done"
// section. This class sidesteps that with IsolatedRunner: the actual
// mutate-then-restore exercise (TestSetupExtension.exerciseAndRestoreCommandLine(),
// gated behind -Dcefcmdline.mutate=true) runs inside a disposable,
// process-isolated child JVM+CefApp instead of this shared suite process --
// see IsolatedRunner's class comment for the general harness this uses, and
// CefCommandLineMutationTask for what the child actually reports back.
//
// This test itself is a completely ordinary, untagged @Test -- no special
// invocation needed, no --exclude-tag required for a normal suite run to
// skip it. IsolatedRunner does the process-spawning internally; only the
// generic child-side driver it launches (IsolatedTaskRunner) needs the
// "process-isolated" tag exclusion.
class CefCommandLineMutationTest {
    @Test
    void mutatorsAndLookupsExerciseRealCommandLineThenRestoreIt() {
        Map<String, String> result;
        try {
            result = IsolatedRunner.run(CefCommandLineMutationTask.class,
                    Collections.singletonMap("cefcmdline.mutate", "true"), 90);
        } catch (Exception e) {
            fail("IsolatedRunner.run(CefCommandLineMutationTask) failed: " + e, e);
            return;
        }

        assertEquals("true", result.get("hadNoSandboxSwitch"),
                "N_HasSwitch(\"no-sandbox\") was false -- expected true, "
                        + "TestSetupExtension always passes --no-sandbox at startup");
        assertEquals("disabled", result.get("useGlSwitchValue"),
                "N_GetSwitchValue(\"use-gl\") -- expected \"disabled\", "
                        + "TestSetupExtension always passes --use-gl=disabled at startup");
        // Empty is a legitimate return value here (CEF's browser-process
        // CefCommandLine doesn't necessarily have a "program" component set
        // this early) -- non-null alone proves N_GetProgram actually ran
        // rather than throwing/short-circuiting.
        assertTrue(result.containsKey("originalProgram"),
                "N_GetProgram's result was missing from the probe entirely");
        assertEquals("true", result.get("restoredSwitchesMatch"),
                "Switches after N_Reset()+re-append via appendSwitch/appendSwitchWithValue no "
                        + "longer match the original set -- the restore-to-identical-state step "
                        + "is broken");
        assertEquals("true", result.get("restoredArgumentsMatch"),
                "Arguments after N_Reset()+N_AppendArgument()+restore no longer match the "
                        + "original set");
    }
}
