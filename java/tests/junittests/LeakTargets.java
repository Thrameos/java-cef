// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefDevToolsClient;
import org.cef.browser.CefRequestContext;
import org.cef.network.CefRequest;

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
    static final List<LeakTarget> ALL = Arrays.asList(devToolsClientTarget(),

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
                    3.0, 200,
                    () -> () -> {
                        CefRequestContext ctx = CefRequestContext.createContext(null);
                        ctx.dispose();
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
                    3.0, 200, () -> () -> {
                        CefRequest request = CefRequest.create();
                        request.dispose();
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

        LeakTarget target = new LeakTarget("CefBrowser.getDevToolsClient/close",
                "Regression coverage for issue #12's fix -- DevTools "
                        + "registration create/release cycle.",
                5.0, 20, () -> {
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
        return target.withTearDown(() -> {
            TestFrame frame = frameHolder[0];
            if (frame != null) {
                frame.terminateTest();
                frame.awaitCompletion();
            }
        });
    }
}
