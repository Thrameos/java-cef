// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import java.util.LinkedHashMap;
import java.util.Map;

// Runs inside IsolatedTaskRunner's disposable child process (see
// CefCommandLineMutationTest, which dispatches this via IsolatedRunner).
// The actual work already happened by the time run() is called:
// TestSetupExtension's onBeforeCommandLineProcessing() override only calls
// TestSetupExtension.exerciseAndRestoreCommandLine() -- which exercises
// CefCommandLine_N.cpp's N_Reset/N_GetProgram/N_SetProgram/N_HasSwitch/
// N_GetSwitchValue/N_AppendArgument on the real, live browser-process
// CefCommandLine, then restores it to an equivalent state -- when
// -Dcefcmdline.mutate=true is set, which CefCommandLineMutationTest passes
// as one of IsolatedRunner's extraSystemProperties. That fires once,
// synchronously, during TestSetupExtension.beforeAll(), before this task's
// run() is ever invoked. This class just surfaces the captured probe.
class CefCommandLineMutationTask implements IsolatedTask {
    @Override
    public Map<String, String> run() throws Exception {
        TestSetupContext.CommandLineMutationProbe probe =
                TestSetupContext.getCapturedCommandLineMutationProbe();
        if (probe == null) {
            throw new IllegalStateException(
                    "-Dcefcmdline.mutate=true was not forwarded to this child process -- "
                    + "TestSetupExtension.exerciseAndRestoreCommandLine() never ran.");
        }

        Map<String, String> result = new LinkedHashMap<>();
        result.put("originalProgram", probe.originalProgram);
        result.put("hadNoSandboxSwitch", Boolean.toString(probe.hadNoSandboxSwitch));
        result.put("useGlSwitchValue", probe.useGlSwitchValue);
        result.put("restoredSwitchesMatch", Boolean.toString(probe.restoredSwitchesMatch));
        result.put("restoredArgumentsMatch", Boolean.toString(probe.restoredArgumentsMatch));
        return result;
    }
}
