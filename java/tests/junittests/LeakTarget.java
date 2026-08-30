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
    final double defaultBudgetSeconds;
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
    private final Supplier<Runnable> setUp_;
    private Runnable tearDown_ = () -> {};

    LeakTarget(String name, String comment, double defaultBudgetSeconds, int batchSize,
            Supplier<Runnable> setUp) {
        this.name = name;
        this.comment = comment;
        this.defaultBudgetSeconds = defaultBudgetSeconds;
        this.batchSize = batchSize;
        this.setUp_ = setUp;
    }

    LeakTarget knownOpenFinding() {
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

    // Budget override: -Dleak.budget.seconds=NNN applies to every target,
    // for a deliberate deep sweep -- matches jpype's own smoke-vs-dedicated
    // budget split (leak_targets.txt's comment: "smoke-test sized... A
    // dedicated overnight/opt-in run should use much larger budgets").
    double budgetSeconds() {
        String override = System.getProperty("leak.budget.seconds");
        if (override != null) {
            try {
                return Double.parseDouble(override);
            } catch (NumberFormatException e) {
                // Fall through to the default.
            }
        }
        return defaultBudgetSeconds;
    }
}
