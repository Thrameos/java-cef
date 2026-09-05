// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

// Per-target process-isolated leak sweep driver -- the migrated replacement
// for tools/run_leak_sweep_isolated.sh's bash-side pooling logic (kept as a
// thin wrapper around this class now, for CLI compatibility) and
// LeakSweepTest.java's old -Dleak.isolated=true/-Dleak.target.filter mode
// (removed -- see LeakSweepTest's class comment). See
// plan/study/leak-checker-port.md's 2026-08-30 pooling-design status for
// the full rationale: LeakSweepTest's normal sweepAllTargetsForLeaks() runs
// every LeakTarget serially in one long-lived CefApp session, which was
// proven to produce real carryover-contamination between targets. This
// class instead dispatches one IsolatedRunner.run(LeakSweepTargetTask.class)
// call per target -- a fresh, disposable JVM+CefApp subprocess each time --
// up to leak.poolSize at a time.
//
// Unlike the old bash driver, this needs no separate -Dleak.listTargets=true
// subprocess just to enumerate targets: LeakTargets.ALL is read directly,
// in-process, since this class already runs inside a JVM with the full
// classpath (TestSetupExtension is still applied so touching LeakTarget/
// LeakTargets -- which only reference CEF classes inside lazy Supplier
// lambdas, never eagerly -- is exactly as safe as it already was inside
// LeakSweepTest's own existing full-sweep loop).
//
// Tagged "leak-sweep", same as LeakSweepTest, so a normal full-suite/
// coverage run's --exclude-tag leak-sweep also skips this (it would
// otherwise spawn one subprocess per target on every ordinary test run).
//
// Usage (mirrors the old script's contract):
//   tools/run_tests.sh <platform> <Debug|Release> \
//     --select-class tests.junittests.LeakSweepIsolatedTest \
//     -Dleak.poolSize=<N> [-Dleak.target.filter=<substring>] \
//     [-Dleak.numBatches=<N>] [-Dleak.abortRssBytes=<N>] [-Dleak.printGrowth=true]
//
// leak.poolSize defaults to Runtime.availableProcessors() (see
// DEFAULT_POOL_SIZE below), not 1 -- unlike the old bash driver, which
// defaulted conservatively to fully serial until the mechanism itself had
// been proven. Each target's subprocess is now genuinely independent (own
// JVM, own CefApp, own cache dir), so there's no correctness reason left to
// throttle parallelism by default; a real machine-core-count soak is the
// normal way to run this, not the exception. Override down (e.g.
// -Dleak.poolSize=1) on a resource-constrained box, since every concurrent
// pool member is a full CEF browser-process startup.
@Tag("leak-sweep")
@ExtendWith(TestSetupExtension.class)
class LeakSweepIsolatedTest {
    private static final int DEFAULT_POOL_SIZE =
            Math.max(1, Runtime.getRuntime().availableProcessors());

    @Test
    void sweepAllTargetsInIsolatedSubprocesses() throws InterruptedException {
        String nameFilter = propertyOrEnv("leak.target.filter");
        List<LeakTarget> targets = new ArrayList<>();
        for (LeakTarget target : LeakTargets.ALL) {
            if (nameFilter == null || nameFilter.isEmpty() || target.name.contains(nameFilter)) {
                targets.add(target);
            }
        }
        if (targets.isEmpty()) {
            fail("No LeakTargets matched leak.target.filter='" + nameFilter + "'");
        }

        int poolSize = parseIntOr(propertyOrEnv("leak.poolSize"), DEFAULT_POOL_SIZE);
        long perTargetTimeoutSeconds = parseLongOr(propertyOrEnv("leak.isolatedTimeoutSeconds"), 300L);
        System.out.println("Running " + targets.size() + " leak-sweep target(s) in isolated "
                + "subprocesses, pool size " + poolSize + ".");

        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        List<Future<TargetOutcome>> futures = new ArrayList<>();
        try {
            for (LeakTarget target : targets) {
                futures.add(pool.submit(() -> runOneTarget(target, perTargetTimeoutSeconds)));
            }

            StringBuilder summary = new StringBuilder("\n=== Isolated leak-sweep summary ===\n");
            StringBuilder failures = new StringBuilder();
            for (Future<TargetOutcome> future : futures) {
                TargetOutcome outcome;
                try {
                    outcome = future.get();
                } catch (Exception e) {
                    outcome = TargetOutcome.error("?", e.toString());
                }
                summary.append(outcome).append('\n');
                if (outcome.isFailure()) {
                    failures.append(outcome).append('\n');
                }
            }
            System.out.println(summary);

            if (failures.length() > 0) {
                fail("Isolated leak-sweep found growth (or errors) in one or more targets:\n"
                        + failures);
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private static TargetOutcome runOneTarget(LeakTarget target, long timeoutSeconds) {
        File cacheDir;
        try {
            // Own cache dir per subprocess -- see TestSetupExtension.java's
            // leak.cachePath comment: sharing the default profile dir across
            // hard-exiting subprocesses causes the next one to trip CEF's
            // singleton-collision fatal path.
            cacheDir = Files.createTempDirectory("jcef_leak_cache_").toFile();
        } catch (Exception e) {
            return TargetOutcome.error(target.name, "Failed to create cache dir: " + e);
        }

        Map<String, String> env = new HashMap<>();
        env.put("LEAK_TARGET_NAME", target.name);
        env.put("leak.cachePath", cacheDir.getAbsolutePath());
        copyIfSet(env, "leak.numBatches");
        copyIfSet(env, "leak.abortRssBytes");
        copyIfSet(env, "leak.printGrowth");

        try {
            Map<String, String> result =
                    IsolatedRunner.run(LeakSweepTargetTask.class, env, timeoutSeconds);
            boolean leaky = Boolean.parseBoolean(result.get("leaky"));
            boolean aborted = Boolean.parseBoolean(result.get("aborted"));
            boolean isControl = Boolean.parseBoolean(result.get("isControl"));
            boolean expectClean = Boolean.parseBoolean(result.get("expectClean"));
            String batches = result.get("batches");
            return TargetOutcome.completed(
                    target.name, leaky, aborted, isControl, expectClean, batches);
        } catch (Exception e) {
            return TargetOutcome.error(target.name, e.toString());
        }
    }

    // <key>, if set on THIS (parent) process (as a system property OR an
    // identically-named env var -- see propertyOrEnv()), is forwarded to
    // the child as an env var -- IsolatedRunner.run()'s extraChildEnv, not
    // a `-D` flag (see IsolatedRunner's class comment for why only env
    // vars reach the child).
    private static void copyIfSet(Map<String, String> env, String key) {
        String value = propertyOrEnv(key);
        if (value != null && !value.isEmpty()) {
            env.put(key, value);
        }
    }

    // tools/run_leak_sweep_isolated.sh (this class's driver script) launches
    // this JVM via tools/run_tests.sh, which has no route for a `-D` flag
    // (see IsolatedRunner's class comment) -- so the script sets env vars
    // instead. A direct manual/IDE invocation of this test may still use
    // -D, so system property takes priority when both are set.
    private static String propertyOrEnv(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isEmpty()) {
            value = System.getenv(key);
        }
        return value;
    }

    private static int parseIntOr(String value, int fallback) {
        if (value == null || value.isEmpty()) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long parseLongOr(String value, long fallback) {
        if (value == null || value.isEmpty()) return fallback;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static final class TargetOutcome {
        final String name;
        final String verdict;
        final boolean failure;

        private TargetOutcome(String name, String verdict, boolean failure) {
            this.name = name;
            this.verdict = verdict;
            this.failure = failure;
        }

        boolean isFailure() {
            return failure;
        }

        static TargetOutcome completed(String name, boolean leaky, boolean aborted,
                boolean isControl, boolean expectClean, String batches) {
            String status = leaky ? "LEAK" : "PASS";
            if (aborted) {
                status += " (SAFETY ABORT -- RSS ceiling hit)";
            } else if (leaky && isControl) {
                status += " (CONTROL FLAGGED -- see plan/study/leak-checker-port.md's "
                        + "carryover-contamination status)";
            } else if (leaky && !expectClean) {
                status += " (known open finding)";
            }
            // Only a genuine, unexpected LEAK (not a control, not an already-
            // tracked open finding) fails the overall sweep -- same policy
            // LeakSweepTest's in-process mode already applies.
            boolean isFailure = leaky && expectClean;
            return new TargetOutcome(
                    name, status + " (" + batches + " batches)", isFailure);
        }

        static TargetOutcome error(String name, String message) {
            return new TargetOutcome(name, "ERROR -- " + message, true);
        }

        @Override
        public String toString() {
            return name + ": " + verdict;
        }
    }
}
