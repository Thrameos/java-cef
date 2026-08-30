// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// Growth-trend leak-detection sweep, the JCEF-side driver for
// java/tests/junittests/LeakChecker.java + LeakTargets.java -- see
// plan/LeakCheckerPort.md Phase 2. Unlike jpype's leaksweep.py (a
// standalone script run outside pytest, with process-pool parallelism),
// this runs as an ordinary JUnit test so it participates in the normal
// full-suite run as real regression coverage rather than needing a
// separate invocation path -- matching how every other cross-cutting
// concern in this suite already works. Targets run strictly serially (no
// parallelism), per plan/LeakCheckerPort.md's isolation decision: no
// CEF-process-per-target restart, one long-lived CefApp session for the
// whole sweep.
//
// Batch count is a FIXED number (LeakChecker.DEFAULT_NUM_BATCHES, matching
// jpype's own `for j in range(10)` exactly), not a time budget -- an
// earlier version of this harness used a wall-clock budget instead, which
// let total call volume before a verdict vary with how fast calls
// happened to run; see plan/LeakCheckerPort.md's 2026-08-30
// course-correction for the several rounds of false confidence that
// produced. For a deliberate, more-sensitive sweep, override via
// -Dleak.numBatches=NNN, e.g.:
//   ... -Dleak.numBatches=100 ... --select-class tests.junittests.LeakSweepTest
// LeakChecker's own RSS abort ceiling (DEFAULT_ABORT_RSS_BYTES) is what
// makes a large override here safe to actually run unattended.
@Tag("leak-sweep")
@ExtendWith(TestSetupExtension.class)
class LeakSweepTest {
    @Test
    void sweepAllTargetsForLeaks() {
        // -Dleak.listTargets=true: print one target name per line and exit,
        // nothing else -- lets an external driver (see
        // tools/run_leak_sweep_isolated.sh) enumerate targets without
        // duplicating LeakTargets.ALL's contents or paying CEF startup cost
        // just to get the list. Deliberately checked before CEF/JUnit setup
        // has any chance to matter -- TestSetupExtension.beforeAll() already
        // ran by the time this @Test body executes, but listing needs none
        // of what it set up.
        if (Boolean.getBoolean("leak.listTargets")) {
            // Prefixed and machine-readable, like LEAK_SWEEP_RESULT below --
            // CEF/Chromium logging can interleave arbitrary lines on stdout
            // during TestSetupExtension's startup, which already ran by the
            // time this @Test body executes, so a bare name-per-line format
            // would not be reliably greppable.
            for (LeakTarget target : LeakTargets.ALL) {
                System.out.println("LEAK_SWEEP_TARGET: " + target.name);
            }
            return;
        }

        // -Dleak.isolated=true: this JVM is one disposable pool member in
        // tools/run_leak_sweep_isolated.sh's per-target process-isolation
        // driver (see plan/LeakCheckerPort.md's 2026-08-30 pooling-design
        // status), not a normal full-suite sweep run. Expects
        // -Dleak.target.filter=<exact name> to also be set, selecting
        // exactly one target. After that target's result is known, this
        // process hard-exits via Runtime.halt() instead of returning from
        // the @Test method -- skipping JUnit's normal AfterAll teardown,
        // which for this test class means TestSetupExtension.close()'s
        // CefApp.getInstance().dispose() call. That call is the same
        // known-crashing native shutdown path already documented on
        // TestSetupExtension.close() (issue java-cef#4 / issues #22/#23):
        // routing every isolated pool member through it on every single
        // target would trade the carryover-contamination problem this
        // isolation exists to fix for a shutdown-crash problem instead.
        // Since this process is disposable by design (never reused across
        // targets -- see plan doc), it doesn't need a clean shutdown, only
        // to get its result out before it dies; halt() guarantees the
        // System.out above it has already been flushed by the time it
        // runs, and skips shutdown hooks entirely (unlike System.exit()),
        // so CefApp's own shutdown machinery never starts.
        boolean isolated = Boolean.getBoolean("leak.isolated");

        LeakChecker checker = new LeakChecker();
        StringBuilder failures = new StringBuilder();

        String nameFilter = System.getProperty("leak.target.filter");
        int numBatchesOverride = LeakTarget.numBatches();

        if (isolated && (nameFilter == null || nameFilter.isEmpty())) {
            System.out.println("LEAK_SWEEP_RESULT: ERROR -- leak.isolated=true requires "
                    + "leak.target.filter=<exact target name>");
            Runtime.getRuntime().halt(2);
        }

        for (LeakTarget target : LeakTargets.ALL) {
            if (nameFilter != null && !target.name.contains(nameFilter)) continue;
            System.out.println("=== leak-sweep: " + target.name + " (batchSize="
                    + target.batchSize + ") ===");
            Runnable action;
            try {
                action = target.setUp();
            } catch (RuntimeException e) {
                failures.append(target.name).append(": setUp() failed: ").append(e).append('\n');
                continue;
            }

            LeakChecker.Result result;
            try {
                result = numBatchesOverride > 0
                        ? checker.memTest(action, target.batchSize, numBatchesOverride)
                        : checker.memTest(action, target.batchSize);
            } finally {
                target.tearDown();
            }

            String status = result.leaky ? "LEAK" : "PASS";
            if (result.aborted) {
                status += " (SAFETY ABORT -- RSS ceiling hit)";
            } else if (result.leaky && target.isControl) {
                status += " (CONTROL FLAGGED -- see plan/LeakCheckerPort.md's "
                        + "carryover-contamination status; this target should be clean, "
                        + "investigate what's contaminating it, not the control itself)";
            } else if (result.leaky && !target.expectClean) {
                status += " (known open finding)";
            }
            System.out.println(target.name + ": " + status + " (" + result.batches + " batches)");

            boolean forceGrowthPrint = Boolean.getBoolean("leak.printGrowth");
            if (result.leaky || forceGrowthPrint) {
                LeakChecker.printGrowth(result);
                if (result.leaky && target.expectClean) {
                    failures.append(target.name)
                            .append(": LEAK over ")
                            .append(result.batches)
                            .append(" batches\n");
                }
            } else if (target.isControl) {
                // Controls are expected to pass -- this is the normal,
                // unremarkable case, not something to flag.
            } else if (!target.expectClean) {
                // A target marked as a known open finding came back clean --
                // worth a loud note, since that's grounds to investigate
                // clearing knownOpenFinding() rather than something to miss
                // in scrollback.
                System.out.println(target.name
                        + ": now PASSING despite knownOpenFinding() -- investigate promoting "
                        + "back to expectClean.");
            }

            if (isolated) {
                // Machine-readable line for tools/run_leak_sweep_isolated.sh
                // to parse -- deliberately separate from the human-readable
                // "status" line above rather than reusing it, so the driver
                // doesn't have to parse prose. See the comment above the
                // isolated flag's declaration for why this process exits
                // via halt() here instead of returning normally.
                System.out.println("LEAK_SWEEP_RESULT: " + target.name + " leaky=" + result.leaky
                        + " aborted=" + result.aborted + " batches=" + result.batches);
                System.out.flush();
                Runtime.getRuntime().halt(result.leaky ? 1 : 0);
            }
        }

        if (failures.length() > 0) {
            fail("Leak-sweep found growth in one or more targets:\n" + failures);
        }
    }
}
