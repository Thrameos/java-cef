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
// standalone script run outside pytest, with per-target multi-minute/hour
// budgets and process-pool parallelism), this runs as an ordinary JUnit
// test with smoke-sized default budgets (LeakTarget.defaultBudgetSeconds)
// so it participates in the normal full-suite run as real regression
// coverage rather than needing a separate invocation path -- matching how
// every other cross-cutting concern in this suite already works. Targets
// run strictly serially (no parallelism), per plan/LeakCheckerPort.md's
// isolation decision: no CEF-process-per-target restart, one long-lived
// CefApp session for the whole sweep.
//
// For a deliberate, longer/more-sensitive sweep (investigating a
// suspected leak, not just fast regression coverage), override every
// target's budget via -Dleak.budget.seconds=NNN, e.g.:
//   ... -Dleak.budget.seconds=300 ... --select-class tests.junittests.LeakSweepTest
@Tag("leak-sweep")
@ExtendWith(TestSetupExtension.class)
class LeakSweepTest {
    @Test
    void sweepAllTargetsForLeaks() {
        LeakChecker checker = new LeakChecker();
        StringBuilder failures = new StringBuilder();

        String nameFilter = System.getProperty("leak.target.filter");

        for (LeakTarget target : LeakTargets.ALL) {
            if (nameFilter != null && !target.name.contains(nameFilter)) continue;
            System.out.println("=== leak-sweep: " + target.name + " (budget="
                    + target.budgetSeconds() + "s, batchSize=" + target.batchSize + ") ===");
            Runnable action;
            try {
                action = target.setUp();
            } catch (RuntimeException e) {
                failures.append(target.name).append(": setUp() failed: ").append(e).append('\n');
                continue;
            }

            LeakChecker.Result result;
            try {
                result = checker.memTestBudget(action, target.batchSize, target.budgetSeconds());
            } finally {
                target.tearDown();
            }

            String status = result.leaky ? "LEAK" : "PASS";
            if (result.leaky && !target.expectClean) status += " (known open finding)";
            System.out.println(target.name + ": " + status + " (" + result.batches + " batches)");

            if (result.leaky) {
                LeakChecker.printGrowth(result);
                if (target.expectClean) {
                    failures.append(target.name)
                            .append(": LEAK over ")
                            .append(result.batches)
                            .append(" batches\n");
                }
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
