// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

// Reusable process-isolation harness: dispatches an IsolatedTask into a
// fresh, disposable JVM+CefApp subprocess and returns its result, so a test
// that needs to touch JVM-global/process-global CEF state (the real
// browser-process CefCommandLine, CefApp's own singleton lifecycle, ...)
// never has to do so inside the shared long-lived suite process every other
// test class depends on.
//
// Adapts ~/devel/jpype/test/jpypetest/subrun.py's pattern (spawn a fresh
// worker process, dispatch a unit of work into it, get the result back) to
// this project's constraints:
//   - Java has no equivalent of pickling an arbitrary closure across a
//     process boundary. An IsolatedTask is instead a named class (a
//     fully-qualified name, reflectively instantiated in the child --
//     see IsolatedTaskRunnerTest), not a literal lambda -- the closest faithful
//     analog available without adding a serialization framework.
//   - The child needs the exact same native-library/classpath wiring every
//     other test run needs (LD_PRELOAD=libcef.so, java.library.path,
//     the jogamp jars, ...), which tools/run_tests.sh already builds
//     correctly -- so the child is launched by shelling out to that script
//     with --select-class tests.junittests.IsolatedTaskRunnerTest, not by
//     hand-assembling a `java` command line here and duplicating that
//     logic (and its platform-specific quirks) a second time.
//   - Results are marshaled as a flat Map<String,String> via
//     java.util.Properties' text encoding between two marker lines on the
//     child's stdout (IsolatedTaskRunnerTest writes them, this class reads them
//     back) -- simple, dependency-free, and tolerant of the arbitrary
//     CEF/Chromium log noise that can otherwise interleave on stdout during
//     startup (see TestSetupExtension's own comment on that hazard).
//
// This was introduced to replace the alternative of copying LeakSweepTest's
// hand-rolled -Dleak.isolated/Runtime.halt()/stdout-marker convention again
// for each new test that needs process isolation -- see
// plan/tasks/20260903-02-coverage-gaps-table.md's CefCommandLine_N.cpp rows
// for the case that prompted this. LeakSweepTest's own isolated mode is a
// candidate to migrate onto this harness as a follow-up, so only one
// isolation mechanism has to be understood/maintained going forward.
class IsolatedRunner {
    static final String RESULT_BEGIN_MARKER = "ISOLATED_TASK_RESULT_BEGIN";
    static final String RESULT_END_MARKER = "ISOLATED_TASK_RESULT_END";
    static final String RESULT_STATUS_PREFIX = "status=";
    static final String RESULT_ERROR_PREFIX = "error=";

    static final class IsolatedTaskException extends Exception {
        IsolatedTaskException(String message) {
            super(message);
        }
    }

    // platform/buildType/repoRoot are all independently overridable via
    // -Disolated.platform=/-Disolated.buildType=/-Disolated.repoRoot=, but
    // have sane defaults inferred from this (parent) JVM's own launch
    // config for the common case -- see the detect*() methods below.
    static Map<String, String> run(Class<? extends IsolatedTask> taskClass,
            Map<String, String> extraChildEnv, long timeoutSeconds) throws IOException,
            InterruptedException, IsolatedTaskException {
        File repoRoot = detectRepoRoot();
        String platform = detectPlatform();
        String buildType = detectBuildType();

        File runTestsSh = new File(repoRoot, "tools/run_tests.sh");
        if (!runTestsSh.isFile()) {
            throw new IsolatedTaskException(
                    "Could not locate tools/run_tests.sh under detected repo root "
                    + repoRoot + " -- pass -Disolated.repoRoot=<path> explicitly.");
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(runTestsSh.getAbsolutePath());
        cmd.add(platform);
        cmd.add(buildType);
        cmd.add("--select-class");
        cmd.add("tests.junittests.IsolatedTaskRunnerTest");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(repoRoot);
        pb.redirectErrorStream(true);
        // tools/run_tests.sh forwards every trailing CLI arg straight to the
        // JUnit console launcher jar, which has no notion of `-D` (that's a
        // `java` launcher flag, needed before `-jar`, which the script gives
        // us no way to inject) -- so extra parameters go to the child as
        // environment variables instead, which ProcessBuilder can set
        // regardless of how the child process is actually invoked.
        // IsolatedTaskRunnerTest/the task's own run() read these back via
        // System.getenv(), not System.getProperty().
        Map<String, String> childEnv = pb.environment();
        childEnv.put("ISOLATED_TASK_CLASS", taskClass.getName());
        if (extraChildEnv != null) {
            childEnv.putAll(extraChildEnv);
        }
        Process process = pb.start();

        // Drain stdout on a background thread while waiting -- the child's
        // pipe buffer can fill and deadlock the child if nothing reads it
        // concurrently with waitFor() (a real risk here: CEF/Chromium
        // startup logging plus the whole JUnit console launcher's own
        // output is not small).
        StringBuilder output = new StringBuilder();
        Thread drain = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            } catch (IOException e) {
                // Process died/pipe closed -- nothing more to read.
            }
        }, "IsolatedRunner-stdout-drain");
        drain.setDaemon(true);
        drain.start();

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            drain.join(TimeUnit.SECONDS.toMillis(5));
            throw new IsolatedTaskException("Isolated task " + taskClass.getName()
                    + " timed out after " + timeoutSeconds + "s. Output so far:\n" + output);
        }
        drain.join(TimeUnit.SECONDS.toMillis(10));

        return parseResult(taskClass, output.toString());
    }

    private static Map<String, String> parseResult(Class<? extends IsolatedTask> taskClass,
            String output) throws IsolatedTaskException {
        int begin = output.indexOf(RESULT_BEGIN_MARKER);
        int end = output.indexOf(RESULT_END_MARKER);
        if (begin < 0 || end < 0 || end < begin) {
            throw new IsolatedTaskException("Isolated task " + taskClass.getName()
                    + " produced no result markers -- child process likely crashed/hung "
                    + "before IsolatedTaskRunnerTest could report. Full output:\n" + output);
        }
        String block = output.substring(begin + RESULT_BEGIN_MARKER.length(), end).trim();
        String[] lines = block.split("\n", 2);
        String statusLine = lines.length > 0 ? lines[0].trim() : "";
        if (!statusLine.startsWith(RESULT_STATUS_PREFIX)) {
            throw new IsolatedTaskException("Isolated task " + taskClass.getName()
                    + " result block missing status= line. Block:\n" + block);
        }
        String status = statusLine.substring(RESULT_STATUS_PREFIX.length());
        String rest = lines.length > 1 ? lines[1] : "";

        if ("ERROR".equals(status)) {
            String errorLine = rest.trim();
            String message = errorLine.startsWith(RESULT_ERROR_PREFIX)
                    ? errorLine.substring(RESULT_ERROR_PREFIX.length())
                    : errorLine;
            throw new IsolatedTaskException(
                    "Isolated task " + taskClass.getName() + " failed in the child process: "
                    + message);
        }
        if (!"OK".equals(status)) {
            throw new IsolatedTaskException(
                    "Isolated task " + taskClass.getName() + " reported unknown status '"
                    + status + "'");
        }

        try {
            Properties props = new Properties();
            props.load(new StringReader(rest));
            Map<String, String> result = new LinkedHashMap<>();
            for (String name : props.stringPropertyNames()) {
                result.put(name, props.getProperty(name));
            }
            return result;
        } catch (IOException e) {
            throw new IsolatedTaskException("Isolated task " + taskClass.getName()
                    + " result block failed to parse as properties: " + e);
        }
    }

    private static File detectRepoRoot() {
        String override = System.getProperty("isolated.repoRoot");
        if (override != null && !override.isEmpty()) {
            return new File(override);
        }
        // This (parent) JVM was itself launched the same way a child will
        // be -- via tools/run_tests.sh, which passes
        // -Djava.library.path=<repoRoot>/jcef_build[_llvmcov]/native/<BuildType>.
        // Walk up past "native/<BuildType>" and the jcef_build* directory.
        String libPath = System.getProperty("java.library.path");
        if (libPath != null && !libPath.isEmpty()) {
            // java.library.path may contain multiple ':'-separated entries;
            // the one we set ourselves is always first (see run_tests.sh).
            String first = libPath.split(File.pathSeparator)[0];
            File nativeDir = new File(first).getParentFile(); // .../jcef_build*/native
            if (nativeDir != null && "native".equals(nativeDir.getName())) {
                File buildDir = nativeDir.getParentFile(); // .../jcef_build*
                if (buildDir != null) {
                    File root = buildDir.getParentFile();
                    if (root != null) {
                        return root;
                    }
                }
            }
        }
        // Last resort: assume the JVM's working directory is the repo root
        // (true for a manual/IDE invocation from the repo root).
        return new File(System.getProperty("user.dir", "."));
    }

    private static String detectBuildType() {
        String override = System.getProperty("isolated.buildType");
        if (override != null && !override.isEmpty()) {
            return override;
        }
        String libPath = System.getProperty("java.library.path");
        if (libPath != null && !libPath.isEmpty()) {
            String first = libPath.split(File.pathSeparator)[0];
            String buildType = new File(first).getName(); // "Release"/"Debug"
            if (!buildType.isEmpty()) {
                return buildType;
            }
        }
        return "Release";
    }

    private static String detectPlatform() {
        String override = System.getProperty("isolated.platform");
        if (override != null && !override.isEmpty()) {
            return override;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "");
        boolean is64 = arch.contains("64");
        if (os.contains("linux")) {
            return is64 ? "linux64" : "linux32";
        }
        if (os.contains("mac")) {
            return "macosx64";
        }
        if (os.contains("win")) {
            return is64 ? "win64" : "win32";
        }
        throw new IllegalStateException("Cannot auto-detect a tools/run_tests.sh platform "
                + "string for os.name=" + os + " os.arch=" + arch
                + " -- pass -Disolated.platform=<linux64|linux32|macosx64|win64|win32> "
                + "explicitly.");
    }

    private IsolatedRunner() {}
}
