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
        // Per-batch (rssGrowthBytesPerCall, heapGrowthBytesPerCall) pairs, in
        // batch order -- for diagnostic printing on a LEAK finding, mirroring
        // jpype's own "Pass%d: %f %f" printout.
        final List<double[]> growth;

        Result(boolean leaky, int batches, List<double[]> growth) {
            this.leaky = leaky;
            this.batches = batches;
            this.growth = growth;
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

    // Same tolerance shape as jpype's LeakChecker.memTest/memTestBudget:
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

    // Runs action() in batches of `size` calls, measuring RSS + Java heap
    // growth per call after each batch, until either CLEAN_BATCHES_REQUIRED
    // batches show growth under threshold (returns not-leaky) or
    // budgetSeconds of wall-clock elapses without that happening (returns
    // leaky). A larger budget means more batches sampled (more
    // sensitivity), not a longer/slower single batch -- same shape as
    // jpype's memTestBudget.
    Result memTestBudget(Runnable action, int size, double budgetSeconds) {
        List<double[]> growth = new ArrayList<>();
        Snapshot prev = freeResources();
        int cleanBatches = 0;
        int batches = 0;
        long start = System.nanoTime();
        double budgetNanos = budgetSeconds * 1_000_000_000.0;

        while (System.nanoTime() - start < budgetNanos) {
            for (int i = 0; i < size; i++) {
                action.run();
            }
            batches++;
            Snapshot cur = freeResources();

            double rssGrowth = (cur.rssBytes - prev.rssBytes) / (double) size;
            double heapGrowth = (cur.javaHeapUsedBytes - prev.javaHeapUsedBytes) / (double) size;
            growth.add(new double[] {rssGrowth, heapGrowth});
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
        return new Result(true, batches, growth);
    }

    // Prints per-batch growth, for a LEAK finding's diagnostic output --
    // mirrors jpype's own "Pass%d: %f %f - ..." printout.
    static void printGrowth(Result result) {
        System.out.println();
        for (int i = 0; i < result.growth.size(); i++) {
            double[] g = result.growth.get(i);
            System.out.printf("  Batch%d: rss=%.1f B/call  heap=%.1f B/call%n", i, g[0], g[1]);
        }
        System.out.println();
    }
}
