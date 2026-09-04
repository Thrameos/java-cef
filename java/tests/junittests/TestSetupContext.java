// Copyright (c) 2019 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Map;
import java.util.Optional;
import java.util.Vector;

// Stores global test setup state for access from package classes.
class TestSetupContext {
    private static boolean debugPrint_ = false;

    // An eager snapshot of the values read off the browser-process CefCommandLine
    // during TestSetupExtension's onBeforeCommandLineProcessing() override. There
    // is no public factory for CefCommandLine -- that callback is the only way to
    // obtain a real instance -- but the live object is not safe to hold onto past
    // the callback: confirmed empirically (via System.identityHashCode() matching
    // but hasSwitches() flipping from true to false) that Chromium resets the same
    // underlying native command-line object sometime after the callback returns,
    // once it has consumed the switches internally. So the getters are called and
    // their results copied into plain data right there in the callback, not
    // exposed as the live CefCommandLine. See plan/roadmap.md Track A item 3.
    static final class CommandLineSnapshot {
        final boolean hasSwitches;
        final Map<String, String> switches;
        final boolean hasArguments;
        final Vector<String> arguments;

        CommandLineSnapshot(boolean hasSwitches, Map<String, String> switches,
                boolean hasArguments, Vector<String> arguments) {
            this.hasSwitches = hasSwitches;
            this.switches = switches;
            this.hasArguments = hasArguments;
            this.arguments = arguments;
        }
    }

    private static CommandLineSnapshot capturedCommandLineSnapshot_ = null;

    // Debug print statements may be enabled via `--config debugPrint=true`.
    static boolean debugPrint() {
        return debugPrint_;
    }

    static CommandLineSnapshot getCapturedCommandLineSnapshot() {
        return capturedCommandLineSnapshot_;
    }

    static void setCapturedCommandLineSnapshot(CommandLineSnapshot snapshot) {
        capturedCommandLineSnapshot_ = snapshot;
    }

    // Results of the two CefSchemeRegistrar.addCustomScheme() calls
    // TestSetupExtension's onRegisterCustomSchemes() override makes for the same
    // scheme name, back to back: the first is expected true (new registration),
    // the second false (duplicate). See plan/roadmap.md Track A item 6.
    static final class SchemeRegistrationResults {
        final boolean firstResult;
        final boolean secondResult;

        SchemeRegistrationResults(boolean firstResult, boolean secondResult) {
            this.firstResult = firstResult;
            this.secondResult = secondResult;
        }
    }

    private static SchemeRegistrationResults capturedSchemeRegistrationResults_ = null;

    static SchemeRegistrationResults getCapturedSchemeRegistrationResults() {
        return capturedSchemeRegistrationResults_;
    }

    static void setCapturedSchemeRegistrationResults(boolean firstResult, boolean secondResult) {
        capturedSchemeRegistrationResults_ =
                new SchemeRegistrationResults(firstResult, secondResult);
    }

    // Results of TestSetupExtension's -Dcefcmdline.mutate=true probe, which
    // exercises CefCommandLine_N.cpp's N_Reset/N_GetProgram/N_SetProgram/
    // N_HasSwitch/N_GetSwitchValue/N_AppendArgument directly on the real
    // browser-process CefCommandLine, then restores it to its original
    // switches/arguments before the callback returns. See
    // CefCommandLineMutationCoverageTest's class comment for why this is
    // gated behind that system property (only ever run in a disposable
    // isolated subprocess) and plan/tasks/20260903-02-coverage-gaps-table.md
    // for which rows this closes.
    static final class CommandLineMutationProbe {
        final String originalProgram;
        final boolean hadNoSandboxSwitch;
        final String useGlSwitchValue;
        final boolean restoredSwitchesMatch;
        final boolean restoredArgumentsMatch;

        CommandLineMutationProbe(String originalProgram, boolean hadNoSandboxSwitch,
                String useGlSwitchValue, boolean restoredSwitchesMatch,
                boolean restoredArgumentsMatch) {
            this.originalProgram = originalProgram;
            this.hadNoSandboxSwitch = hadNoSandboxSwitch;
            this.useGlSwitchValue = useGlSwitchValue;
            this.restoredSwitchesMatch = restoredSwitchesMatch;
            this.restoredArgumentsMatch = restoredArgumentsMatch;
        }
    }

    private static CommandLineMutationProbe capturedCommandLineMutationProbe_ = null;

    static CommandLineMutationProbe getCapturedCommandLineMutationProbe() {
        return capturedCommandLineMutationProbe_;
    }

    static void setCapturedCommandLineMutationProbe(CommandLineMutationProbe probe) {
        capturedCommandLineMutationProbe_ = probe;
    }

    // Initialize from global configuration parameters.
    static void initialize(ExtensionContext context) {
        Optional<String> debugPrint = context.getConfigurationParameter("debugPrint");
        if (debugPrint.isPresent() && debugPrint.get().equalsIgnoreCase("true")) {
            debugPrint_ = true;
        }
    }
}
