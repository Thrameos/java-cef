// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

// Diagnostic only -- see java/tests/junittests/JniNoOpProbe.java's own
// comment and plan/LeakCheckerPort.md's 2026-08-30 status. Hand-written
// header (matches javah/javac -h's naming convention exactly -- see that
// tooling's own limitation note below) since javah is unavailable on
// current JDKs and this is a single trivial diagnostic class, not worth
// wiring into tools/make_all_jni_headers.sh for a one-off probe.

#include <jni.h>

// Deliberately does NOT include jni_scoped_helpers.h -- that header pulls
// in a long list of CEF headers (cef_browser.h, cef_drag_data.h, etc.),
// which would reintroduce a CEF dependency into what's supposed to be a
// zero-CEF probe. Instead, the three lines below are the exact same
// uncached FindClass/GetMethodID/Call pattern that
// jni_scoped_helpers.h's JNI_CALL_METHOD/JNI_CALL_VOID_METHOD macros
// expand to (see that file, native/jni_scoped_helpers.h) -- inlined here
// so this probe has zero CEF include/link dependency at all.

extern "C" {

JNIEXPORT void JNICALL Java_tests_junittests_JniNoOpProbe_noop(JNIEnv*,
                                                                jclass) {
  // Intentionally empty.
}

JNIEXPORT void JNICALL
Java_tests_junittests_JniNoOpProbe_probeCallback(JNIEnv* env, jobject obj) {
  // Mirrors jni_scoped_helpers.h's SetCefForJNIObject_sync() exactly:
  // lock+get, set, unlock -- three uncached FindClass+GetMethodID+Call
  // round trips -- but against JniNoOpProbe's own plain `long` field,
  // never a CefRefPtr or any CEF object.
  jclass cls = env->GetObjectClass(obj);

  jmethodID lockAndGetId = env->GetMethodID(cls, "lockAndGetHandle", "()J");
  jlong previousValue = env->CallLongMethod(obj, lockAndGetId);

  jmethodID setId = env->GetMethodID(cls, "setHandle", "(J)V");
  env->CallVoidMethod(obj, setId, previousValue + 1);

  jmethodID unlockId = env->GetMethodID(cls, "unlock", "()V");
  env->CallVoidMethod(obj, unlockId);

  env->DeleteLocalRef(cls);
}

}  // extern "C"
