// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package org.cef.misc;

/**
 * Java-side counterpart to native/jcef_trace.h -- the permanent, always-
 * available instrumentation facility for tracing the whole cross-language
 * lifecycle path (Java handler dispatch, CefApp/CefClient state transitions,
 * browser create/dispose) alongside the native Context/ref-counting trace, so
 * a single interleaved log shows the full incoming-event sequence on both
 * sides of the JNI boundary. See plan/findings.md's issue #4/#22/#23
 * investigation for why this exists: hand-adding and removing System.err
 * prints each investigation (which nearly every prior crash investigation in
 * this repo did) loses the instrumentation as soon as the investigation ends.
 *
 * Unlike the native facility, there is no separate compile-time gate here
 * (Java has no preprocessor, and the runtime check below is already cheap
 * enough -- a single cached boolean read -- to leave permanently compiled
 * in). The single gate is the JCEF_TRACE environment variable, matching the
 * native side exactly: set it and both native and Java traces appear
 * interleaved in the same stderr stream, unset (the default) and this class
 * costs one static boolean check per call site and prints nothing.
 */
public final class JCefTrace {
    private static final boolean ENABLED = System.getenv("JCEF_TRACE") != null;

    public static boolean isEnabled() {
        return ENABLED;
    }

    /** printf-style trace line, prefixed with a millisecond timestamp to
     *  interleave with native/jcef_trace.h's [jcef-trace TS] lines by eye. */
    public static void trace(String fmt, Object... args) {
        if (!ENABLED) return;
        System.err.println(
                "[jcef-trace-java " + System.currentTimeMillis() + "] " + String.format(fmt, args));
        System.err.flush();
    }

    private JCefTrace() {}
}
