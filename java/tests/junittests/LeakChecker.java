// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.ArrayList;
import java.util.List;

// Test/CI infrastructure only -- not part of the public API. Growth-trend
// leak detector, ported from jpype's LeakChecker
// (test/jpypetest/leakharness.py on Thrameos/jpype's backport-leak-checker
// branch -- see plan/LeakCheckerPort.md for the full design mapping).
//
// jpype tracks native-process RSS (its own C-heap leaks) alongside embedded
// JVM heap (the JVM it hosts). Here the roles are inverted -- Java is the
// host, native CEF/Chromium is the guest -- so this tracks the same two
// signals with the sides swapped: process RSS (native CEF/C++-side leaks,
// e.g. a CefRefPtr whose Release() never runs) and JVM heap used (Java-side
// leaks, e.g. issue #23's CefRequestContext_N.globalInstance never being
// cleared -- a leaked *Java* wrapper object that keeps a *native* object
// alive as a side effect).
//
// Both signals matter for the same reason jpype tracks both: a leak can
// live on either side of the JNI boundary, and a single-signal harness
// would only ever see half of it.
class LeakChecker {
    private final MemoryMXBean memBean_ = ManagementFactory.getMemoryMXBean();

    // Result of one growth-trend check.
    static final class Result {
        final boolean leaky;
        final int batches;
        // True if this run was cut short by the RSS abort ceiling
        // (ABORT_RSS_GROWTH_BYTES below) rather than running its normal
        // course -- always leaky=true when this is true, but callers may
        // want to report it distinctly (this wasn't a considered verdict,
        // it was an emergency stop).
        final boolean aborted;
        // Per-batch (rssGrowthBytesPerCall, heapGrowthBytesPerCall,
        // nanosPerCall) triples, in batch order -- for diagnostic printing on
        // a LEAK finding, mirroring jpype's own "Pass%d: %f %f" printout.
        // nanosPerCall times only the action loop itself (System.nanoTime()
        // around the `for (i < size) action.run()` loop), excluding
        // freeResources()'s GC/settle overhead -- added 2026-08-30 to
        // correlate leak rate with call latency across the six JNI-boundary
        // findings (CefRequestContext excluded, has its own separate,
        // already-diagnosed C++-side leak) -- see plan/LeakCheckerPort.md's
        // latest status.
        final List<double[]> growth;

        Result(boolean leaky, int batches, List<double[]> growth) {
            this(leaky, batches, growth, false);
        }

        Result(boolean leaky, int batches, List<double[]> growth, boolean aborted) {
            this.leaky = leaky;
            this.batches = batches;
            this.growth = growth;
            this.aborted = aborted;
        }
    }

    // Returns true if VmRSS is readable on this platform (Linux only, via
    // /proc/self/status -- see readRssBytes()'s own comment). A caller
    // should skip/report-unavailable rather than fail outright on a
    // platform where this is false, matching this repo's Linux-first CI
    // environment (see CLAUDE.md) -- Windows/macOS RSS readers are
    // follow-up work, not implemented here yet (see plan/LeakCheckerPort.md
    // Phase 1).
    static boolean haveRss() {
        return readRssBytes() >= 0;
    }

    // Reads this process's resident set size from /proc/self/status.
    // Linux-only: returns -1 (not a leak indicator, just "unavailable") on
    // any other platform or if the read fails for any reason. Deliberately
    // not thread-safe/cached -- called only from freeResources(), which is
    // already meant to run rarely (once per batch), not on a hot path.
    private static long readRssBytes() {
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/self/status"))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("VmRSS:")) {
                    // Format: "VmRSS:\t   12345 kB"
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 2) {
                        return Long.parseLong(parts[1]) * 1024L;
                    }
                }
            }
        } catch (IOException | NumberFormatException e) {
            // Unavailable -- not a Linux /proc filesystem, or a transient
            // read failure. Treated the same as "not supported" by the
            // caller (see haveRss()).
        }
        return -1;
    }

    // A single (rss, javaHeapUsed) reading, taken after forcing a GC pass.
    private static final class Snapshot {
        final long rssBytes;
        final long javaHeapUsedBytes;

        Snapshot(long rssBytes, long javaHeapUsedBytes) {
            this.rssBytes = rssBytes;
            this.javaHeapUsedBytes = javaHeapUsedBytes;
        }
    }

    // Forces a best-effort GC pass and reads both signals. Unlike jpype's
    // gc.collect() (deterministic for CPython's refcounting GC),
    // System.gc() is only ever a *request* -- calling it twice with a short
    // settle delay between readings is a pragmatic attempt to get a stable
    // number, not a guarantee. If this turns out too noisy in practice,
    // revisit (e.g. a longer settle delay, or more repetitions) rather than
    // assuming the growth-trend tolerance below alone will absorb it.
    private Snapshot freeResources() {
        System.gc();
        System.runFinalization();
        System.gc();
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long rss = readRssBytes();
        long heapUsed = memBean_.getHeapMemoryUsage().getUsed();
        return new Snapshot(rss, heapUsed);
    }

    // Same tolerance shape as jpype's LeakChecker.memTest:
    // growth is measured per-batch (bytes grown per call, since the last
    // batch), and "not leaky" is only declared once several batches show
    // growth under the threshold -- not on a single clean reading. This is
    // deliberate, not an arbitrary noise allowance: a genuine leak grows in
    // nearly every batch, while GC-timing instability produces occasional
    // bad batches but never a persistent majority the way a real leak
    // does. See plan/archive/LeakCheckHarness.md (jpype repo) for the
    // original calibration rationale -- ported here as the same shape,
    // not re-derived from JCEF-specific data yet.
    private static final int CLEAN_BATCHES_REQUIRED = 4;
    private static final double GROWTH_THRESHOLD_BYTES_PER_CALL = 64.0;

    // Default number of batches, matching jpype's test_leak.py LeakChecker.
    // memTest EXACTLY: `for j in range(10)`. Deliberately NOT time-budgeted
    // -- see the 2026-08-30 course-correction in plan/LeakCheckerPort.md.
    // An earlier version of this method ran batches until a wall-clock
    // budget elapsed instead of a fixed count; that let batch *count* (and
    // therefore total call volume before a verdict) vary with whatever
    // happened to make calls faster or slower on a given run -- pumping the
    // CEF message loop, MALLOC_ARENA_MAX, running one target in isolation
    // vs. the full suite -- which produced several rounds of the exact
    // false-confidence problem a fixed-batch-count design avoids by
    // construction: total churn is bounded and deterministic
    // (numBatches * size calls, max), independent of throughput.
    private static final int DEFAULT_NUM_BATCHES = 10;

    // Hard safety ceiling, checked every batch regardless of numBatches or
    // any growth-rate classification: if this process's own measured RSS
    // ever exceeds this many bytes during a memTest() call, abort
    // immediately rather than keep running. Added 2026-08-30 after a
    // genuine incident: an earlier deliberate long-soak confirmation run
    // (MALLOC_ARENA_MAX=1, no batch cap yet) drove this process to
    // ~6.86GB anon-RSS on a 7.7GB machine and got OOM-killed by the
    // kernel -- see plan/LeakCheckerPort.md's 2026-08-30 status. A
    // leak-sweep target is expected to prove itself clean or dirty within
    // a modest amount of growth; it should never be able to take down the
    // machine it's running on while doing so, no matter how a future
    // caller configures numBatches/size for a deeper sweep. 3GB leaves
    // generous headroom under this repo's dev-environment memory (see
    // CLAUDE.md -- Linux-first) while being far above any legitimate
    // CEF+JVM baseline plus real test churn. Override via
    // -Dleak.abortRssBytes=NNN if a specific environment genuinely needs a
    // different ceiling; the default must stay conservative.
    private static final long DEFAULT_ABORT_RSS_BYTES = 3_000_000_000L;

    private static long abortRssBytes() {
        // Checked as a system property first (the normal in-process full
        // sweep's convention), falling back to the identically-named
        // environment variable -- IsolatedRunner-dispatched child processes
        // (see LeakSweepTargetTask) can only pass extra config as env vars,
        // since tools/run_tests.sh has no route for a `-D` JVM flag (see
        // IsolatedRunner's class comment).
        String override = System.getProperty("leak.abortRssBytes");
        if (override == null) {
            override = System.getenv("leak.abortRssBytes");
        }
        if (override != null) {
            try {
                return Long.parseLong(override);
            } catch (NumberFormatException e) {
                // Fall through to the default.
            }
        }
        return DEFAULT_ABORT_RSS_BYTES;
    }

    // Runs action() in up to `numBatches` batches of `size` calls each,
    // measuring RSS + Java heap growth per call after every batch. Returns
    // not-leaky as soon as CLEAN_BATCHES_REQUIRED batches show growth under
    // threshold (may be before numBatches is reached, matching jpype's own
    // `if success > 3: return False` early-out); otherwise, once all
    // numBatches have run without reaching that many clean readings,
    // returns leaky. There is no time budget -- `size` (calls/batch) and
    // `numBatches` (batches) together are what determine both sensitivity
    // and total runtime, exactly as in jpype's memTest(func, size).
    Result memTest(Runnable action, int size) {
        return memTest(action, size, DEFAULT_NUM_BATCHES);
    }

    Result memTest(Runnable action, int size, int numBatches) {
        // Discarded warmup batch, run and measured by nothing, before the
        // real baseline snapshot is taken. Added 2026-08-30 after isolating
        // JniNoOpProbe.probeCallback (a JNI FindClass/GetMethodID/Call round
        // trip, unlike bareCall's truly empty native function) alone in its
        // own fresh process (see tools/run_leak_sweep_isolated.sh) and
        // finding it reliably read ~655 B/call growth in batch 0 only, never
        // again in any later batch, across repeated runs -- classic one-time
        // JIT-compilation/classloading/JNI-method-cache warmup cost on a
        // fresh JVM, not a per-call leak, but indistinguishable from one if
        // batch 0 is folded into the measured baseline the way it used to
        // be. Without this, a genuinely clean but JNI-heavy target could
        // spend one of its few CLEAN_BATCHES_REQUIRED "dirty budget" slots
        // on pure process-startup noise, matching what was observed here.
        for (int i = 0; i < size; i++) {
            action.run();
        }

        List<double[]> growth = new ArrayList<>();
        Snapshot prev = freeResources();
        int cleanBatches = 0;
        long abortRssBytes = abortRssBytes();

        for (int batches = 1; batches <= numBatches; batches++) {
            long callStart = System.nanoTime();
            for (int i = 0; i < size; i++) {
                action.run();
            }
            double nanosPerCall = (System.nanoTime() - callStart) / (double) size;
            Snapshot cur = freeResources();

            if (cur.rssBytes >= 0 && cur.rssBytes > abortRssBytes) {
                System.out.println("LEAK CHECKER ABORT: process RSS " + cur.rssBytes
                        + " bytes exceeded the " + abortRssBytes
                        + " byte safety ceiling after " + batches
                        + " batches -- stopping immediately rather than risk an OOM kill. "
                        + "See LeakChecker.DEFAULT_ABORT_RSS_BYTES.");
                growth.add(new double[] {(cur.rssBytes - prev.rssBytes) / (double) size,
                        (cur.javaHeapUsedBytes - prev.javaHeapUsedBytes) / (double) size,
                        nanosPerCall});
                return new Result(true, batches, growth, true);
            }

            double rssGrowth = (cur.rssBytes - prev.rssBytes) / (double) size;
            double heapGrowth = (cur.javaHeapUsedBytes - prev.javaHeapUsedBytes) / (double) size;
            growth.add(new double[] {rssGrowth, heapGrowth, nanosPerCall});
            prev = cur;

            // A negative reading (memory went down, e.g. an unrelated GC
            // finally reclaiming something from before this batch started)
            // is not itself evidence of a leak -- skip it rather than
            // counting it as either a clean or dirty batch, same as
            // jpype's `if growth0 < 0 or growth1 < 0: continue`.
            boolean rssUnavailable = prev.rssBytes < 0;
            if ((!rssUnavailable && rssGrowth < 0) || heapGrowth < 0) {
                continue;
            }

            boolean rssClean = rssUnavailable || rssGrowth < GROWTH_THRESHOLD_BYTES_PER_CALL;
            if (rssClean && heapGrowth < GROWTH_THRESHOLD_BYTES_PER_CALL) {
                cleanBatches++;
            }
            if (cleanBatches > CLEAN_BATCHES_REQUIRED - 1) {
                return new Result(false, batches, growth);
            }
        }
        return new Result(true, numBatches, growth);
    }

    // Prints per-batch growth, for a LEAK finding's diagnostic output --
    // mirrors jpype's own "Pass%d: %f %f - ..." printout.
    static void printGrowth(Result result) {
        System.out.println();
        for (int i = 0; i < result.growth.size(); i++) {
            double[] g = result.growth.get(i);
            System.out.printf("  Batch%d: rss=%.1f B/call  heap=%.1f B/call  time=%.0f ns/call%n",
                    i, g[0], g[1], g[2]);
        }
        System.out.println();
    }
}
