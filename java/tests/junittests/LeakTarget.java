// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import java.util.function.Supplier;

// Test/CI infrastructure only -- not part of the public API. One entry in
// the leak-sweep target list (java/tests/junittests/LeakTargets.java), the
// JCEF-side equivalent of jpype's leak_targets.txt (see
// plan/LeakCheckerPort.md Phase 2 -- hand-written closures, not a
// text-config/reflection-based "wrap an existing test method" mechanism,
// matching that plan's recommendation to start with jpype's own original,
// simpler approach).
//
// setUp() runs once, before the batch loop starts, and returns the
// repeatable action to run every call; tearDown() runs once, after the
// budget elapses (or setUp() throws), regardless of leaky/not-leaky.
class LeakTarget {
    final String name;
    final int batchSize;
    final String comment;
    // False for a target with a known-open, not-yet-confirmed-or-fixed LEAK
    // finding (see LeakTargets.java's per-target comments) -- LeakSweepTest
    // still runs and reports it every time (visibility), but doesn't fail
    // the suite over an open investigation, same reasoning this whole
    // multi-session effort has used throughout for other not-yet-resolved
    // findings ("test, document honestly, don't block on it"). Once
    // confirmed fixed (or confirmed not a real leak), flip back to true so
    // a real regression starts failing the suite again.
    boolean expectClean = true;
    // True for a control -- distinct from a known-open finding. A control
    // is expected to genuinely be clean (e.g. JniNoOpProbe: zero CEF
    // involvement, nothing to leak); its purpose is to calibrate the rest
    // of the sweep, not to track a real suspected defect. A LEAK verdict
    // on a control is itself the finding -- evidence of a harness/ordering
    // problem (see plan/LeakCheckerPort.md's 2026-08-30
    // carryover-contamination status, discovered exactly this way) -- so
    // it must never be silently deleted just because it's noisy; that
    // throws away the signal instead of controlling for it. Like
    // knownOpenFinding(), a control never fails the suite on its own, but
    // LeakSweepTest reports it with different, non-"go fix this" wording.
    boolean isControl = false;
    private final Supplier<Runnable> setUp_;
    private Runnable tearDown_ = () -> {};

    // batchSize is the only per-target tunable now -- see numBatches()
    // below for why batch *count* is fixed globally (via
    // -Dleak.numBatches), not per-target and not time-budgeted.
    LeakTarget(String name, String comment, int batchSize, Supplier<Runnable> setUp) {
        this.name = name;
        this.comment = comment;
        this.batchSize = batchSize;
        this.setUp_ = setUp;
    }

    LeakTarget knownOpenFinding() {
        this.expectClean = false;
        return this;
    }

    LeakTarget asControl() {
        this.isControl = true;
        this.expectClean = false;
        return this;
    }

    // Fluent: attach a one-time teardown, run once after the sweep for this
    // target finishes (leaky or not). Returns this for chaining at
    // registration sites.
    LeakTarget withTearDown(Runnable tearDown) {
        this.tearDown_ = tearDown;
        return this;
    }

    Runnable setUp() {
        return setUp_.get();
    }

    void tearDown() {
        tearDown_.run();
    }

    // Batch-count override: -Dleak.numBatches=NNN applies to every target,
    // for a deliberate deep sweep -- matches jpype's own smoke-vs-dedicated
    // split (leak_targets.txt's comment: "smoke-test sized... A dedicated
    // overnight/opt-in run should use much larger budgets"), except the
    // knob that grows is batch *count* (LeakChecker.DEFAULT_NUM_BATCHES),
    // not wall-clock time -- see plan/LeakCheckerPort.md's 2026-08-30
    // course-correction for why a time budget was the wrong knob (it made
    // total call volume before a verdict depend on how fast calls
    // happened to run, which produced several rounds of false confidence
    // this session). LeakChecker's own RSS abort ceiling
    // (DEFAULT_ABORT_RSS_BYTES) is what keeps a large override here safe
    // to actually run.
    static int numBatches() {
        String override = System.getProperty("leak.numBatches");
        if (override != null) {
            try {
                return Integer.parseInt(override);
            } catch (NumberFormatException e) {
                // Fall through to the default.
            }
        }
        return -1; // sentinel: caller uses LeakChecker.DEFAULT_NUM_BATCHES.
    }
}
