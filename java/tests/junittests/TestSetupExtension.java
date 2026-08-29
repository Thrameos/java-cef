// Copyright (c) 2019 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

import org.cef.CefApp;
import org.cef.CefApp.CefAppState;
import org.cef.CefSettings;
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
        // reach the real Chromium command line -- discovered via CefCommandLineTest
        // asserting on switches that turned out to never actually be set. Passing
        // `args` through and delegating to super.onBeforeCommandLineProcessing()
        // restores the forwarding this adapter's presence otherwise disables.
        CefApp.addAppHandler(new CefAppHandlerAdapter(args) {
            @Override
            public void stateHasChanged(org.cef.CefApp.CefAppState state) {
                if (state == CefAppState.TERMINATED) {
                    // Signal completion of CEF shutdown.
                    countdown_.countDown();
                }
            }

            @Override
            public void onBeforeCommandLineProcessing(
                    String process_type, org.cef.callback.CefCommandLine command_line) {
                super.onBeforeCommandLineProcessing(process_type, command_line);

                // Fires once, synchronously, before any @Test runs -- the only way
                // to obtain a real CefCommandLine (no public factory exists). Only
                // snapshot the browser-process instance (process_type is null/empty
                // there; helper/renderer processes would also invoke this). The
                // live command_line object is not safe to hold onto past this
                // callback (see TestSetupContext.CommandLineSnapshot's comment), so
                // its getters are called and copied into plain data right here.
                if ((process_type == null || process_type.isEmpty())
                        && TestSetupContext.getCapturedCommandLineSnapshot() == null) {
                    TestSetupContext.setCapturedCommandLineSnapshot(
                            new TestSetupContext.CommandLineSnapshot(command_line.hasSwitches(),
                                    command_line.getSwitches(), command_line.hasArguments(),
                                    command_line.getArguments()));
                }
            }

            @Override
            public void onRegisterCustomSchemes(org.cef.callback.CefSchemeRegistrar registrar) {
                // Fires once, synchronously, before any @Test runs -- the only way
                // to obtain a real CefSchemeRegistrar (no public factory exists).
                // Register a scheme once (expect true), then again with the same
                // name (expect false, per this method's own Javadoc: "It should
                // only be called once per unique schemeName value ... if
                // schemeName is already registered ... this method will return
                // false") -- exercises both the happy and unhappy path in one
                // capture. See plan/roadmap.md Track A item 6.
                boolean firstResult = registrar.addCustomScheme("jceftestscheme",
                        true /* isStandard */, false /* isLocal */,
                        false /* isDisplayIsolated */, false /* isSecure */,
                        true /* isCorsEnabled */, false /* isCspBypassing */,
                        true /* isFetchEnabled */);
                boolean secondResult = registrar.addCustomScheme("jceftestscheme",
                        true /* isStandard */, false /* isLocal */,
                        false /* isDisplayIsolated */, false /* isSecure */,
                        true /* isCorsEnabled */, false /* isCspBypassing */,
                        true /* isFetchEnabled */);
                TestSetupContext.setCapturedSchemeRegistrationResults(firstResult, secondResult);
            }
        });

        // Initialize the singleton CefApp instance.
        CefSettings settings = new CefSettings();
        CefApp.getInstance(args, settings);
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
