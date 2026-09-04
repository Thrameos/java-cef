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
// plan/study/leak-checker-port.md Phase 2. Unlike jpype's leaksweep.py (a
// standalone script run outside pytest, with process-pool parallelism),
// this runs as an ordinary JUnit test so it participates in the normal
// full-suite run as real regression coverage rather than needing a
// separate invocation path -- matching how every other cross-cutting
// concern in this suite already works. Targets run strictly serially (no
// parallelism) in ONE long-lived CefApp session for the whole sweep --
// which is exactly the shape that was proven to produce real
// carryover-contamination between targets (see
// plan/study/leak-checker-port.md's 2026-08-30 status). Use
// LeakSweepIsolatedTest instead for a real per-target-process-isolated run
// (one fresh JVM+CefApp per target, no carryover possible by construction);
// this class's in-process sweep still has value as ordinary fast regression
// coverage that participates in a normal full-suite run, it's just not the
// isolated/authoritative signal for a genuine leak finding.
//
// Batch count is a FIXED number (LeakChecker.DEFAULT_NUM_BATCHES, matching
// jpype's own `for j in range(10)` exactly), not a time budget -- an
// earlier version of this harness used a wall-clock budget instead, which
// let total call volume before a verdict vary with how fast calls
// happened to run; see plan/study/leak-checker-port.md's 2026-08-30
// course-correction for the several rounds of false confidence that
// produced. For a deliberate, more-sensitive sweep, override via
// -Dleak.numBatches=NNN, e.g.:
//   ... -Dleak.numBatches=100 ... --select-class tests.junittests.LeakSweepTest
// LeakChecker's own RSS abort ceiling (DEFAULT_ABORT_RSS_BYTES) is what
// makes a large override here safe to actually run unattended.
//
// This class used to also serve as the child-process entry point for a
// hand-rolled -Dleak.isolated=true/-Dleak.target.filter/Runtime.halt()
// isolation mode, invoked by tools/run_leak_sweep_isolated.sh. That's been
// migrated onto the general IsolatedTask/IsolatedRunner harness (see
// LeakSweepIsolatedTest/LeakSweepTargetTask/IsolatedRunner) so this project
// has only one process-isolation mechanism to understand, not two -- this
// class is back to being a plain, single-purpose in-process sweep.
@Tag("leak-sweep")
@ExtendWith(TestSetupExtension.class)
class LeakSweepTest {
    @Test
    void sweepAllTargetsForLeaks() {
        LeakChecker checker = new LeakChecker();
        StringBuilder failures = new StringBuilder();
        int numBatchesOverride = LeakTarget.numBatches();

        for (LeakTarget target : LeakTargets.ALL) {
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
                status += " (CONTROL FLAGGED -- see plan/study/leak-checker-port.md's "
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
        }

        if (failures.length() > 0) {
            fail("Leak-sweep found growth in one or more targets:\n" + failures);
        }
    }
}
