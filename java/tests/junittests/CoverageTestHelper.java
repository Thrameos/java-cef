// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import java.lang.reflect.Method;

// Test/CI infrastructure only -- not part of the public API. See
// native/CoverageTestHelper.cpp and java-cef#4 / plan/findings.md.
class CoverageTestHelper {
    // Flushes both native (LLVM) and Java (JaCoCo) coverage data before the
    // known-crashing native shutdown call in TestSetupExtension.close(). A no-op
    // on any build that isn't instrumented for the corresponding kind of coverage.
    static void flush() {
        flushNative();
        flushJacoco();
    }

    // Flushes LLVM coverage data, if this was built with -DENABLE_LLVM_COVERAGE=ON
    // (the native method only exists in that build configuration -- see
    // native/CMakeLists.txt). A no-op, not an error, on any other build: libjcef.so
    // is already loaded by this point (via org.cef.CefApp), so an
    // UnsatisfiedLinkError here means coverage instrumentation wasn't compiled in,
    // not that anything is wrong.
    private static void flushNative() {
        try {
            N_FlushCoverage();
        } catch (UnsatisfiedLinkError e) {
            // Expected on a non-coverage build.
        }
    }

    // Flushes JaCoCo's own coverage buffer to destfile via its runtime "controller"
    // API (org.jacoco.agent.rt.RT), reached through reflection so this class still
    // compiles/runs fine when jacocoagent.jar isn't on the classpath (every build
    // except the coverage CI job -- see .github/workflows/ci.yml's CLS_PATH).
    //
    // Needed for the same reason as flushNative() above: JaCoCo's default
    // dumponexit=true relies on a JVM shutdown hook, which does NOT run when the
    // process is killed by the known browser_context.cc:44 DCHECK's abort() --
    // that's an abrupt process termination, not a normal JVM exit. Verified
    // directly (2026-09-05): a Runtime.halt() right after this dump call still
    // leaves a valid, non-empty jacoco.exec on disk; without the explicit dump,
    // dumponexit never gets a chance to run and the whole coverage run's Java
    // numbers are lost even though every test already completed.
    private static void flushJacoco() {
        try {
            Class<?> rt = Class.forName("org.jacoco.agent.rt.RT");
            Object agent = rt.getMethod("getAgent").invoke(null);
            Method dump = agent.getClass().getMethod("dump", boolean.class);
            dump.invoke(agent, false /* don't reset -- this isn't the final dump */);
        } catch (ReflectiveOperationException e) {
            // Expected when the JaCoCo runtime agent jar isn't on the classpath
            // (i.e. every build except the coverage job).
        }
    }

    private static native void N_FlushCoverage();
}
