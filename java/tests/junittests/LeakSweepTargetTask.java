// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import java.util.LinkedHashMap;
import java.util.Map;

// Runs exactly one LeakTarget inside IsolatedTaskRunnerTest's disposable
// child process -- see LeakSweepIsolatedTest, which dispatches this via
// IsolatedRunner (one dispatch per target, so each target gets its own
// fresh JVM+CefApp instance and never sees another target's carryover
// state -- the whole reason this isolated mode exists, see
// plan/study/leak-checker-port.md's 2026-08-30 carryover-contamination
// status). Selects its target by exact name via the LEAK_TARGET_NAME env
// var (LeakSweepIsolatedTest already resolved the exact name from
// LeakTargets.ALL before dispatching, so no substring ambiguity to guard
// against here, unlike the old tools/run_leak_sweep_isolated.sh's
// -Dleak.target.filter).
class LeakSweepTargetTask implements IsolatedTask {
    @Override
    public Map<String, String> run() throws Exception {
        String targetName = System.getenv("LEAK_TARGET_NAME");
        if (targetName == null || targetName.isEmpty()) {
            throw new IllegalStateException(
                    "LEAK_TARGET_NAME env var was not set -- LeakSweepTargetTask must only "
                    + "be dispatched by LeakSweepIsolatedTest, never directly.");
        }

        LeakTarget target = null;
        for (LeakTarget candidate : LeakTargets.ALL) {
            if (candidate.name.equals(targetName)) {
                target = candidate;
                break;
            }
        }
        if (target == null) {
            throw new IllegalStateException(
                    "No LeakTarget named exactly '" + targetName + "' found in LeakTargets.ALL");
        }

        System.out.println(
                "=== leak-sweep: " + target.name + " (batchSize=" + target.batchSize + ") ===");

        LeakChecker checker = new LeakChecker();
        int numBatchesOverride = LeakTarget.numBatches();
        Runnable action = target.setUp();
        LeakChecker.Result result;
        try {
            result = numBatchesOverride > 0
                    ? checker.memTest(action, target.batchSize, numBatchesOverride)
                    : checker.memTest(action, target.batchSize);
        } finally {
            target.tearDown();
        }

        boolean printGrowth = Boolean.parseBoolean(System.getenv("leak.printGrowth"));
        if (result.leaky || printGrowth) {
            LeakChecker.printGrowth(result);
        }

        Map<String, String> out = new LinkedHashMap<>();
        out.put("name", target.name);
        out.put("leaky", Boolean.toString(result.leaky));
        out.put("aborted", Boolean.toString(result.aborted));
        out.put("batches", Integer.toString(result.batches));
        out.put("isControl", Boolean.toString(target.isControl));
        out.put("expectClean", Boolean.toString(target.expectClean));
        return out;
    }
}
