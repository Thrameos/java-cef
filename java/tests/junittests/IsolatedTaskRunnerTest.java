// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;
import java.util.Properties;

// The single, shared CHILD-process entry point for IsolatedRunner.run() --
// see that class's comment for the full design. Never invoked directly by a
// human/CI run: IsolatedRunner launches this exact class (via
// tools/run_tests.sh --select-class) as a disposable subprocess, naming the
// actual IsolatedTask to execute via the ISOLATED_TASK_CLASS env var
// (fully-qualified class name).
//
// Tagged "process-isolated" (same convention LeakSweepTest's "leak-sweep"
// tag already established) so a normal full-suite/coverage
// --select-package/--select-class batch never picks this class up by
// accident -- it would fail fast (ISOLATED_TASK_CLASS unset) rather than
// run any real task, which only IsolatedRunner ever sets.
@Tag("process-isolated")
@ExtendWith(TestSetupExtension.class)
class IsolatedTaskRunnerTest {
    @Test
    void runOneIsolatedTask() {
        String taskClassName = System.getenv("ISOLATED_TASK_CLASS");
        Map<String, String> result = null;
        Throwable failure = null;

        if (taskClassName == null || taskClassName.isEmpty()) {
            failure = new IllegalStateException(
                    "ISOLATED_TASK_CLASS env var was not set -- "
                    + "IsolatedTaskRunnerTest must only be launched by IsolatedRunner.run(), "
                    + "never directly.");
        } else {
            try {
                Class<?> cls = Class.forName(taskClassName);
                IsolatedTask task = (IsolatedTask) cls.getDeclaredConstructor().newInstance();
                result = task.run();
                if (result == null) {
                    result = java.util.Collections.emptyMap();
                }
            } catch (Exception e) {
                failure = e;
            }
        }

        // Marker-delimited so this survives arbitrary CEF/Chromium log noise
        // interleaved on stdout (see TestSetupExtension's own comment on this
        // exact hazard) -- IsolatedRunner scans for these lines rather than
        // trusting stdout to be otherwise clean.
        System.out.println(IsolatedRunner.RESULT_BEGIN_MARKER);
        if (failure != null) {
            System.out.println(IsolatedRunner.RESULT_STATUS_PREFIX + "ERROR");
            System.out.println(IsolatedRunner.RESULT_ERROR_PREFIX + failure);
        } else {
            System.out.println(IsolatedRunner.RESULT_STATUS_PREFIX + "OK");
            try {
                Properties props = new Properties();
                props.putAll(result);
                // No comment/timestamp line -- keep the block pure key=value
                // pairs so IsolatedRunner's Properties.load() sees nothing
                // but this task's own data between the markers.
                java.io.StringWriter sw = new java.io.StringWriter();
                props.store(sw, null);
                for (String line : sw.toString().split("\n")) {
                    if (!line.startsWith("#")) {
                        System.out.println(line);
                    }
                }
            } catch (Exception e) {
                System.out.println(IsolatedRunner.RESULT_STATUS_PREFIX + "ERROR");
                System.out.println(IsolatedRunner.RESULT_ERROR_PREFIX
                        + "Failed to serialize task result: " + e);
            }
        }
        System.out.println(IsolatedRunner.RESULT_END_MARKER);
        System.out.flush();

        // This process is disposable by design (one task, then gone) -- skip
        // TestSetupExtension.close()'s CefApp.dispose(), the known-crashing
        // native shutdown path (issue java-cef#4 / #22/#23), the same way
        // LeakSweepTest's isolated mode already does. The result has already
        // been printed above, so a clean shutdown buys nothing.
        Runtime.getRuntime().halt(failure != null ? 1 : 0);
    }
}
