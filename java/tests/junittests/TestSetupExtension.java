// Copyright (c) 2019 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

import org.cef.CefApp;
import org.cef.CefApp.CefAppState;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.handler.CefAppHandlerAdapter;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.concurrent.CountDownLatch;

// All test cases must install this extension for CEF to be properly initialized
// and shut down.
//
// For example:
//
//   @ExtendWith(TestSetupExtension.class)
//   class FooTest {
//        @Test
//        void testCaseThatRequiresCEF() {}
//   }
//
// This code is based on https://stackoverflow.com/a/51556718.
public class TestSetupExtension
        implements BeforeAllCallback, ExtensionContext.Store.CloseableResource {
    private static boolean initialized_ = false;
    private static CountDownLatch countdown_ = new CountDownLatch(1);

    @Override
    public void beforeAll(ExtensionContext context) {
        if (!initialized_) {
            initialized_ = true;
            initialize(context);
        }
    }

    // Executed before any tests are run.
    private void initialize(ExtensionContext context) {
        TestSetupContext.initialize(context);

        if (TestSetupContext.debugPrint()) {
            System.out.println("TestSetupExtension.initialize");
        }

        // Register a callback hook for when the root test context is shut down.
        context.getRoot().getStore(GLOBAL).put("jcef_test_setup", this);

        // Perform startup initialization on platforms that require it.
        if (!CefApp.startup(null)) {
            System.out.println("Startup initialization failed!");
            return;
        }

        // These CI/headless-environment flags are required for the test bench to
        // run on a display-less CI agent (no real GPU, no Vulkan driver): without
        // them a GPU-process crash during browser teardown triggers a slow (multi-
        // minute) Vulkan/on-device-model probing fallback that blows past any
        // reasonable test timeout, rather than a fast, clean failure.
        String[] args = {"--disable-gpu", "--disable-gpu-compositing", "--disable-dev-shm-usage",
                "--no-sandbox", "--use-gl=disabled", "--disable-software-rasterizer",
                "--disable-features=OnDeviceModel,OptimizationGuideOnDeviceModel,Vulkan,"
                        + "VulkanFromANGLE,DefaultANGLEVulkan"};

        // IMPORTANT: pass `args` here, not null. CefApp.getInstance(args, settings)
        // below only forwards `args` onto the command line via the *default*
        // CefAppHandlerAdapter.onBeforeCommandLineProcessing() that CefApp installs
        // on itself as appHandler_ -- but only if appHandler_ is still unset at
        // that point. addAppHandler() here runs first and permanently replaces
        // appHandler_, so if this adapter were constructed with `null` (as it
        // originally was), the args above would be silently discarded and never
        // reach the real Chromium command line. Base-class CefAppHandlerAdapter's
        // own onBeforeCommandLineProcessing(), inherited unmodified here, does the
        // forwarding as long as args_ is non-null.
        CefApp.addAppHandler(new CefAppHandlerAdapter(args) {
            @Override
            public void stateHasChanged(org.cef.CefApp.CefAppState state) {
                if (state == CefAppState.TERMINATED) {
                    // Signal completion of CEF shutdown.
                    countdown_.countDown();
                }
            }
        });

        // Initialize the singleton CefApp instance.
        CefSettings settings = new CefSettings();

        // tools/run_leak_sweep_isolated.sh (see plan/LeakCheckerPort.md's
        // pooling-design status) launches one fresh JVM+CEF subprocess per
        // leak-sweep target and hard-exits via Runtime.halt() as soon as
        // that target's result is known, skipping this class's own close()
        // (CefApp.dispose()) deliberately -- see LeakSweepTest.java's
        // isolated-mode comment for why. Left at the default (null)
        // root_cache_path, every subprocess shares the same profile
        // directory and its SingletonLock; a process that hard-exits
        // instead of shutting down gracefully doesn't get a chance to
        // release that lock the normal way, so the *next* subprocess to
        // start can trip CEF's singleton-collision fatal path (observed
        // directly: 7 of 8 non-control targets crashed with SIGTRAP on
        // startup, no output, in the first full isolated-sweep run before
        // this fix). -Dleak.cachePath=<dir>, set by the driver script to a
        // fresh per-subprocess directory, routes around the shared profile
        // entirely rather than trying to make hard-exit-then-immediately-
        // restart safe against a lock this process never releases.
        String isolatedCachePath = System.getProperty("leak.cachePath");
        if (isolatedCachePath != null && !isolatedCachePath.isEmpty()) {
            settings.root_cache_path = isolatedCachePath;
            settings.cache_path = isolatedCachePath;
        }

        CefApp.getInstance(args, settings);

        // Isolated single-target leak-sweep processes (see leak.cachePath
        // above) can reach here having never created a real CefBrowser --
        // unlike the ordinary full sweep, where an earlier target (the
        // DevTools one) always creates one first. This turned out to
        // matter: 7 of 9 non-control targets crashed (SIGTRAP inside
        // libcef.so, confirmed via dmesg) when run as the very first real
        // CEF call in such a process. A pure-C++ probe
        // (tools_native/leak_probe/leak_probe.cc) makes the same calls
        // (CefRequestContext::CreateContext() etc.) with no browser ever
        // created and never crashes -- but it deliberately configures
        // multi_threaded_message_loop=true, windowless_rendering_enabled=
        // false, letting CEF drive its own native UI thread and pump its
        // own message loop. This repo's real default (windowless_rendering_
        // enabled=true, external_message_pump mode -- see CLAUDE.md) has no
        // such thing: nothing but explicit doMessageLoopWork() calls (an
        // app's ~30fps EDT Timer, normally) drives CEF's browser-process/
        // IO-thread startup forward, and a manual pump loop tried here
        // first wasn't enough to reach whatever state actually only
        // finishes as a side effect of creating a browser. No real JCEF
        // app calls CefRequestContext.createContext() before ever creating
        // a browser anyway, so this isn't a synthetic workaround so much as
        // restoring the one precondition every real caller already
        // satisfies: create and close one throwaway real CefBrowser here so
        // an isolated process reaches the same browser-process-ready state
        // the full sweep always incidentally had by this point.
        if (isolatedCachePath != null) {
            warmUpBrowserProcess();
        }
    }

    private static void warmUpBrowserProcess() {
        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(1);
        final String warmupUrl = "http://test.com/leak_sweep_warmup.html";
        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                addResource(warmupUrl, "<html><body>warmup</body></html>", "text/html");
                createBrowser(warmupUrl, true /* useOSR */);
                super.setupTest();
            }

            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                    boolean canGoBack, boolean canGoForward) {
                if (!isLoading) {
                    ready.countDown();
                }
            }
        };
        try {
            if (!ready.await(30, java.util.concurrent.TimeUnit.SECONDS)) {
                System.out.println(
                        "TestSetupExtension.warmUpBrowserProcess: browser never finished "
                        + "loading -- proceeding anyway, the isolated target run may crash.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        frame.terminateTest();
        frame.awaitCompletion();

        // Settle period, added 2026-08-30 after direct observation: without
        // this, the very first target measured right after this warmup
        // browser closes (even JniNoOpProbe.bareCall -- a truly empty
        // native function, unrelated to browsers entirely) reliably read
        // large, decaying RSS growth in its first several batches (~1966
        // B/call in batch 0, tapering afterward). frame.awaitCompletion()
        // only waits for the Java-side onBeforeClose/close handshake, not
        // for CEF's own async native cleanup tasks posted as a side effect
        // of browser teardown to actually finish running -- the exact same
        // carryover-contamination mechanism already proven for the
        // full-sweep case (see plan/LeakCheckerPort.md), just shrunk down
        // to "warmup step -> the one real target" instead of "one leaky
        // target -> the next". Pumping the external message loop gives any
        // pending cleanup tasks a chance to actually run rather than sit
        // queued.
        long settleUntil = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(6);
        while (System.nanoTime() < settleUntil) {
            CefApp.getInstance().doMessageLoopWork(0);
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.gc();
        System.runFinalization();
        System.gc();
    }

    // Executed after all tests have completed.
    @Override
    public void close() {
        if (TestSetupContext.debugPrint()) {
            System.out.println("TestSetupExtension.close");
        }

        // See java-cef#4 / plan/findings.md: CefApp.dispose() below reliably
        // crashes native shutdown in Debug/coverage builds. Flush coverage data
        // (a no-op on non-coverage builds) before that known-crashing call so a
        // coverage CI run still captures real numbers for everything that ran.
        CoverageTestHelper.flush();

        CefApp.getInstance().dispose();

        // Wait for CEF shutdown to complete.
        try {
            countdown_.await();
        } catch (InterruptedException e) {
        }
    }
}
