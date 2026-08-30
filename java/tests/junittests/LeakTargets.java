// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefDevToolsClient;
import org.cef.browser.CefRequestContext;
import org.cef.callback.CefDragData;
import org.cef.misc.CefPrintSettings;
import org.cef.network.CefPostData;
import org.cef.network.CefPostDataElement;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// Test/CI infrastructure only -- not part of the public API. Curated
// leak-sweep target registry, the JCEF-side equivalent of jpype's
// leak_targets.txt (see plan/LeakCheckerPort.md Phase 2). Hand-curated and
// reviewed like real tests, not auto-generated -- add a new entry when a
// leak is found and fixed, so the fix gets permanent, systematic regression
// coverage instead of relying on another investigation getting lucky again
// (same discipline jpype's own list documents).
//
// Skipped, with reason (not added -- matching jpype's own practice of
// recording exclusions, not just silently omitting candidates):
// - CefCookieManager.getGlobalManager(): unlike CefRequestContext, there is
//   no per-call "create a fresh instance" factory -- only the cached
//   process-global singleton exists. CefCookieManager_N.globalInstance's
//   own get-or-cache logic (see java/org/cef/network/CefCookieManager_N.java)
//   has a real bug if dispose() is called on it outside the one place it's
//   meant to run (CefApp.shutdown()): the cached instance is left in a
//   stale, half-disposed state and every subsequent getGlobalManager() call
//   leaks its own fresh AddRef with no way to release it -- calling
//   dispose() repeatedly in a sweep target would introduce a *new* leak
//   into the running process, not exercise an existing one safely. Needs
//   its own createManager()-style non-global factory (or a fix to the
//   get-or-cache logic to tolerate external disposal) before it can be a
//   sweep target at all.
class LeakTargets {
    // EXPERIMENT (2026-08-30), not a permanent feature: -Dleak.pump.everyNCalls=N
    // makes the two rapid-churn targets below call
    // CefApp.getInstance().doMessageLoopWork(0) every N calls, to test the open
    // "growing async cleanup-task backlog" hypothesis from finding 1/2 in
    // plan/LeakCheckerPort.md's Phase 2 status -- external_message_pump mode
    // (native/context.cpp, active whenever windowless_rendering_enabled, the
    // default) means CEF's own task queue only drains when Java calls
    // doMessageLoopWork(), normally driven by a ~30fps EDT timer
    // (CefApp.doMessageLoopWork's kMaxTimerDelay). A tight non-EDT loop calling
    // native create/dispose thousands of times per second may outrun that
    // 30fps drain rate, leaving scheduled native cleanup work queued up. Absent
    // (default 0/disabled), behavior is unchanged from before this experiment.
    private static int pumpEveryNCalls() {
        String override = System.getProperty("leak.pump.everyNCalls");
        if (override != null) {
            try {
                return Integer.parseInt(override);
            } catch (NumberFormatException e) {
                // Fall through to disabled.
            }
        }
        return 0;
    }

    // ORDERING NOTE (2026-08-29): the browser-needing DevTools target is
    // listed FIRST, before the two rapid native-object-churn targets below
    // -- not just for variety. Running createContext()/dispose() or
    // CefRequest.create()/dispose() in an ~8800-calls/3s tight loop first
    // was confirmed to starve the DevTools target's subsequent browser
    // creation: onAfterCreated/onLoadingStateChange never fired at all
    // within a 30s wait afterward, while the identical target passed
    // cleanly (13 batches) when run alone. This is itself a real, useful
    // data point for the open RSS-growth investigation below (consistent
    // with a growing backlog of async native cleanup work overwhelming
    // CEF's own task queue under heavy churn, not just a classic
    // unbounded-leak shape) -- but the immediate, pragmatic fix here is
    // just sequencing, not yet a real solution to either problem. See
    // plan/LeakCheckerPort.md Phase 2 status for the full writeup.
    // CONTROLS (2026-08-30), permanent, listed FIRST -- see
    // java/tests/junittests/JniNoOpProbe.java's own comment and
    // plan/LeakCheckerPort.md's 2026-08-30 status. Originally built to test
    // whether generic JNI-crossing overhead alone (zero CEF involvement)
    // produces growth (answer: no, when run uncontaminated) -- but its more
    // important role turned out to be a live carryover-contamination
    // detector: a literally empty native function read as a sustained,
    // uniform "leak" when run *after* several real leaky targets in the
    // same process, in a completely different, false-signal magnitude and
    // consistency than the genuine noise it shows here at the front of the
    // sweep. Marked .asControl() (not knownOpenFinding() -- these are
    // expected to be clean, not suspected defects) so a LEAK verdict here
    // never fails the suite but is always reported: it's evidence of a
    // measurement/ordering problem elsewhere in the sweep, not a defect in
    // JniNoOpProbe itself. Do not delete these because they're
    // occasionally noisy -- that throws away exactly the signal they exist
    // to provide (a genuinely noisy/failing control needs a better
    // control, or an investigation into why, not removal).
    static final List<LeakTarget> ALL = Arrays.asList(
            new LeakTarget("JniNoOpProbe.bareCall",
                    "CONTROL: bare native call, zero JNI callbacks, zero CEF involvement -- "
                            + "expected clean; a LEAK here flags carryover contamination "
                            + "elsewhere in the sweep, not a defect in this target.",
                    200, () -> JniNoOpProbe::noop)
                    .asControl(),

            new LeakTarget("JniNoOpProbe.probeCallback",
                    "CONTROL: mirrors SetCefForJNIObject_sync's exact JNI call shape with "
                            + "zero CEF involvement -- expected clean; a LEAK here flags "
                            + "carryover contamination elsewhere in the sweep.",
                    200,
                    () -> () -> {
                        JniNoOpProbe probe = new JniNoOpProbe();
                        probe.probeCallback();
                    })
                    .asControl(),

            devToolsClientTarget(),

            // OPEN FINDING, not yet root-caused (2026-08-29) -- see
            // plan/LeakCheckerPort.md's Phase 2 status section for the full
            // investigation. Shows sustained, non-decaying RSS growth
            // (~1300-2000 B/call) over 365+ batches (73,000+ calls) at a
            // 25s budget, with Java heap staying flat throughout. Ruled
            // out as harness noise: a true no-op action run inside the
            // same kind of live CEF session (a throwaway control target,
            // since removed) stayed clean, so the growth is real and
            // specific to actually calling createContext()/dispose(), not
            // background CEF process activity or the measurement loop
            // itself. Not yet root-caused *which* allocation this is (CEF
            // process-wide caches genuinely tied to CefRequestContext's
            // own document/profile/network-service objects that
            // legitimately can't shrink until CefShutdown(), vs. a true
            // per-call leak, vs. a growing async-cleanup-task backlog --
            // see the ORDERING NOTE above, which points toward that third
            // possibility) -- marked knownOpenFinding() so this still
            // runs and reports every sweep (visibility) without failing
            // the suite over an unconfirmed investigation. Regression
            // coverage target for issue #23's original leak shape once
            // this is understood either way.
            new LeakTarget("CefRequestContext.createContext/dispose",
                    "OPEN: sustained non-decaying RSS growth, not yet root-caused. "
                            + "Regression coverage for issue #23's original leak shape once "
                            + "understood.",
                    200,
                    () -> {
                        int pumpEvery = pumpEveryNCalls();
                        int[] callCount = {0};
                        return () -> {
                            CefRequestContext ctx = CefRequestContext.createContext(null);
                            ctx.dispose();
                            if (pumpEvery > 0 && ++callCount[0] % pumpEvery == 0) {
                                CefApp.getInstance().doMessageLoopWork(0);
                            }
                        };
                    })
                    .knownOpenFinding(),

            // Same OPEN FINDING as above, same magnitude, same investigation
            // -- this was meant as the "known good" baseline calibration
            // target (a value object with no known leak history), but
            // showed the identical sustained growth pattern. That's actually
            // useful signal in itself: since CefRequest.create()/dispose()
            // is exercised constantly throughout the rest of this suite with
            // no prior sign of runaway growth, whatever's happening here is
            // more likely a per-call cost that doesn't show up as a problem
            // at the existing suite's much smaller call volume, not a
            // classic unbounded leak -- but that's a hypothesis, not a
            // conclusion. Left marked open rather than reclassified as
            // "expected", since that hasn't been confirmed either.
            new LeakTarget("CefRequest.create/dispose",
                    "OPEN: same sustained-growth pattern as CefRequestContext above, not yet "
                            + "root-caused. No longer usable as a 'known good' baseline until "
                            + "resolved.",
                    200,
                    () -> {
                        int pumpEvery = pumpEveryNCalls();
                        int[] callCount = {0};
                        return () -> {
                            CefRequest request = CefRequest.create();
                            request.dispose();
                            if (pumpEvery > 0 && ++callCount[0] % pumpEvery == 0) {
                                CefApp.getInstance().doMessageLoopWork(0);
                            }
                        };
                    })
                    .knownOpenFinding(),

            // BREADTH PASS (2026-08-30): the remaining targets below are a
            // first cut at Phase 4's "curate + sweep + fix" breadth pass
            // (plan/LeakCheckerPort.md) -- deliberately shallow, smoke-budget
            // create()/dispose() coverage across several distinct create()/
            // dispose() API shapes that don't need a live browser, to build a
            // map of which call patterns leak and which don't rather than
            // root-causing the first one found (see that status section for
            // why this pass exists). Each is expectClean=true by default; a
            // LEAK finding here should get knownOpenFinding() plus a short
            // note, not an immediate deep investigation -- triage after the
            // full breadth pass, not during it.
            // OPEN FINDING (2026-08-30): LEAK under the fixed-batch-count
            // model -- was misreported "clean" under this harness's earlier,
            // wrongly time-budgeted memTestBudget() (see plan doc's
            // 2026-08-30 course-correction); at only 2,000 calls (10
            // batches x 200) it turns out to leak just as reliably as the
            // other value-object targets. Never structurally different --
            // just needed a correct test.
            new LeakTarget("CefPostData.create/dispose",
                    "OPEN: part of the shared breadth-pass finding (see plan doc) -- "
                            + "previously misreported clean under a since-fixed harness bug.",
                    200,
                    () -> () -> {
                        CefPostData data = CefPostData.create();
                        data.dispose();
                    })
                    .knownOpenFinding(),

            // OPEN FINDING (2026-08-30): LEAK, ~1300-2000 B/call, 41 batches
            // at a 3s smoke budget -- part of the shared breadth-pass finding,
            // see plan/LeakCheckerPort.md's 2026-08-30 status. Not
            // root-caused per-target; likely the same shared
            // off-UI-thread-Release() mechanism as CefRequestContext/
            // CefRequest above.
            new LeakTarget("CefPostDataElement.create/dispose",
                    "OPEN: part of the shared breadth-pass finding (see plan doc) -- "
                            + "likely the same off-UI-thread dispose() mechanism as "
                            + "CefRequestContext/CefRequest above, not investigated per-target.",
                    200,
                    () -> () -> {
                        CefPostDataElement element = CefPostDataElement.create();
                        element.dispose();
                    })
                    .knownOpenFinding(),

            // OPEN FINDING (2026-08-30): same as CefPostDataElement above.
            new LeakTarget("CefResponse.create/dispose",
                    "OPEN: part of the shared breadth-pass finding (see plan doc) -- "
                            + "likely the same off-UI-thread dispose() mechanism as "
                            + "CefRequestContext/CefRequest above, not investigated per-target.",
                    200,
                    () -> () -> {
                        CefResponse response = CefResponse.create();
                        response.dispose();
                    })
                    .knownOpenFinding(),

            // OPEN FINDING (2026-08-30): same as CefPostDataElement above.
            new LeakTarget("CefDragData.create/dispose",
                    "OPEN: part of the shared breadth-pass finding (see plan doc) -- "
                            + "likely the same off-UI-thread dispose() mechanism as "
                            + "CefRequestContext/CefRequest above, not investigated per-target.",
                    200,
                    () -> () -> {
                        CefDragData data = CefDragData.create();
                        data.dispose();
                    })
                    .knownOpenFinding(),

            // OPEN FINDING (2026-08-30): same as CefPostDataElement above.
            new LeakTarget("CefPrintSettings.create/dispose",
                    "OPEN: part of the shared breadth-pass finding (see plan doc) -- "
                            + "likely the same off-UI-thread dispose() mechanism as "
                            + "CefRequestContext/CefRequest above, not investigated per-target.",
                    200,
                    () -> () -> {
                        CefPrintSettings settings = CefPrintSettings.create();
                        settings.dispose();
                    })
                    .knownOpenFinding(),

            // Distinct shape from the value objects above: CefApp.createClient()
            // exercises CefClient's own handler-registration lifecycle (the same
            // add/remove-handler cycle issue #22's GetHandler<T>() lazy-create
            // bug lived in, now _sync-fixed) rather than a plain native value
            // object, and goes through CefApp's clients_ registry
            // (CefApp.java's createClient()/clientWasDisposed()) instead of a
            // standalone native ref.
            // OPEN FINDING (2026-08-30): LEAK, ~1300-2000 B/call, 39 batches at
            // a 3s smoke budget -- part of the shared breadth-pass finding, see
            // plan/LeakCheckerPort.md's 2026-08-30 status.
            new LeakTarget("CefApp.createClient/dispose",
                    "OPEN: part of the shared breadth-pass finding (see plan doc) -- "
                            + "exercises CefClient's handler add/remove cycle and "
                            + "CefApp.clients_ registry, not investigated per-target.",
                    200,
                    () -> () -> {
                        CefClient client = CefApp.getInstance().createClient();
                        client.dispose();
                    })
                    .knownOpenFinding());

    // Regression coverage for issue #12's fix: getDevToolsClient() lazily
    // creates a new native DevToolsMessageObserver registration
    // (CefRegistration_N.cpp, native/devtools_message_observer.cpp)
    // whenever the cached client is null or closed() -- exercising
    // get/close in a loop repeatedly exercises that create-and-release
    // cycle. Needs one live browser, created once and reused for the whole
    // budget (see plan/LeakCheckerPort.md's isolation decision -- no
    // CEF-process-per-target restart). See the ORDERING NOTE on ALL above
    // for why this is listed first, not last.
    private static LeakTarget devToolsClientTarget() {
        // Holds the live TestFrame across setUp()/tearDown() -- setUp()
        // creates and blocks until the browser is ready, tearDown() closes
        // it. Method-level (not setUp()-local) so both lambdas can see it.
        TestFrame[] frameHolder = {null};

        // OPEN FINDING (2026-08-30): occasional LEAK under the fixed-batch
        // model at this target's small batchSize=20 -- plausibly just
        // measurement noise made more visible by the smaller per-call
        // denominator (growth = delta-bytes / 20, so the same absolute
        // jitter reads as a much bigger B/call number here than at the
        // other targets' batchSize=200), not necessarily a real regression
        // of issue #12's fix. Not yet distinguished from a real leak --
        // marked open rather than assumed either way.
        LeakTarget target = new LeakTarget("CefBrowser.getDevToolsClient/close",
                "OPEN: occasional LEAK at this target's small batchSize -- not yet "
                        + "distinguished from measurement noise vs. a real regression of "
                        + "issue #12's fix.",
                20, () -> {
                    CountDownLatch ready = new CountDownLatch(1);
                    CefBrowser[] browserHolder = {null};

                    TestFrame frame = new TestFrame() {
                        private static final String TEST_URL =
                                "http://test.com/leak_sweep_devtools.html";

                        @Override
                        protected void setupTest() {
                            addResource(TEST_URL, "<html><body>leak sweep</body></html>",
                                    "text/html");
                            createBrowser(TEST_URL, true /* useOSR */);
                            super.setupTest();
                        }

                        @Override
                        public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                                boolean canGoBack, boolean canGoForward) {
                            if (isLoading || browserHolder[0] != null) return;
                            browserHolder[0] = browser;
                            ready.countDown();
                        }
                    };
                    frameHolder[0] = frame;

                    try {
                        if (!ready.await(30, TimeUnit.SECONDS)) {
                            throw new IllegalStateException(
                                    "leak-sweep DevTools target: browser never finished loading");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }

                    CefBrowser browser = browserHolder[0];
                    return (Runnable) () -> {
                        CefDevToolsClient client = browser.getDevToolsClient();
                        client.close();
                    };
                });
        return target.knownOpenFinding().withTearDown(() -> {
            TestFrame frame = frameHolder[0];
            if (frame != null) {
                frame.terminateTest();
                frame.awaitCompletion();
            }
        });
    }
}
