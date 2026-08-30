// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

// Test/CI infrastructure only -- not part of the public API. Diagnostic for
// plan/LeakCheckerPort.md's 2026-08-30 "6 of 7 findings are JNI-boundary
// problems" status: isolates the generic JNI-crossing mechanism from any
// CEF object at all, to test whether that mechanism alone (independent of
// CefRefPtr/AddRef/Release) produces the same sustained RSS growth seen for
// CefResponse/CefPostDataElement/CefDragData/CefPrintSettings.
//
// probeCallback()'s shape deliberately mirrors jni_scoped_helpers.h's
// SetCefForJNIObject_sync() exactly -- lock/get, set, unlock, three
// uncached JNI_CALL_METHOD/JNI_CALL_VOID_METHOD round trips per call -- but
// touches only a plain `long` field, never a real native pointer or CEF
// object. If this leaks at the same rate as the real dispose() paths, the
// mechanism is generic JNI-crossing overhead, not anything CEF-specific,
// and the fix is method-ID caching. If it stays clean, the leak is
// specific to what CEF's AddRef()/Release() do when called through this
// pattern, not the pattern itself.
//
// Its native implementation is its own separate library
// (tools_native/jni_noop_probe/JniNoOpProbe.cpp, built as
// libjni_noop_probe.so) rather than being bundled into libjcef.so --
// diagnostic-only native code must never ship inside the real product
// library. Loaded explicitly here since nothing else triggers loading it
// the way CefApp/SystemBootstrap load "jcef".
class JniNoOpProbe {
    static {
        System.loadLibrary("jni_noop_probe");
    }

    private volatile long dummyHandle = 0;
    private final Lock lock_ = new ReentrantLock();

    long lockAndGetHandle() {
        lock_.lock();
        return dummyHandle;
    }

    void setHandle(long h) {
        dummyHandle = h;
    }

    void unlock() {
        lock_.unlock();
    }

    // True no-op: zero JNI calls back into Java, tests bare native-call
    // crossing overhead only.
    static native void noop();

    // Mirrors SetCefForJNIObject_sync's exact call shape (lock+get, set,
    // unlock) against this object's own dummy fields -- no CEF object
    // anywhere in the loop.
    native void probeCallback();
}
