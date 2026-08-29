// Copyright (c) 2019 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import org.cef.callback.CefCommandLine;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Optional;

// Stores global test setup state for access from package classes.
class TestSetupContext {
    private static boolean debugPrint_ = false;

    // The CefCommandLine captured by TestSetupExtension's
    // onBeforeCommandLineProcessing() override during CefApp startup. There is no
    // public factory for CefCommandLine -- this callback (which fires once,
    // synchronously, before any @Test runs) is the only way to obtain a real
    // instance. See plan/roadmap.md Track A item 3.
    private static CefCommandLine capturedCommandLine_ = null;

    // Debug print statements may be enabled via `--config debugPrint=true`.
    static boolean debugPrint() {
        return debugPrint_;
    }

    static CefCommandLine getCapturedCommandLine() {
        return capturedCommandLine_;
    }

    static void setCapturedCommandLine(CefCommandLine commandLine) {
        capturedCommandLine_ = commandLine;
    }

    // Initialize from global configuration parameters.
    static void initialize(ExtensionContext context) {
        Optional<String> debugPrint = context.getConfigurationParameter("debugPrint");
        if (debugPrint.isPresent() && debugPrint.get().equalsIgnoreCase("true")) {
            debugPrint_ = true;
        }
    }
}
