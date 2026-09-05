// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

// Test/CI infrastructure only -- not part of the public API. Binds CEF's own
// test-only C++ helper CefExecuteJavaScriptWithUserGestureForTests()
// (include/test/cef_test_helpers.h) for tests.junittests.CefTestHelper.
//
// CEF's own ceftests suite (e.g. life_span_unittest.cc, jsdialog_unittest.cc)
// uses this to fake a real user gesture for functionality gated on Blink's
// user-activation tracking -- window.open() popups, onbeforeunload dialogs,
// etc. -- instead of relying on synthetic mouse/keyboard input, which was
// found NOT to reliably satisfy that tracking (see CefLifeSpanPopupTest's
// @Disabled note: a synthetic click was confirmed to land and run its
// onclick handler, yet window.open() still never reached OnBeforePopup).
//
// include/test/cef_test_helpers.h guards itself behind BUILDING_CEF_SHARED,
// WRAPPING_CEF_SHARED, or UNIT_TEST -- defining UNIT_TEST here, local to this
// one translation unit, is the least invasive way to satisfy that guard.
// This is safe to compile into every build (not just a special test/coverage
// configuration like CoverageTestHelper.cpp): the underlying C API symbol
// (cef_execute_java_script_with_user_gesture_for_tests) is a normal, globally
// exported symbol in CEF's redistributable libcef.so, present in both Debug
// and Release binary distributions -- confirmed directly via `nm` -- so
// linking against it carries no extra build-configuration requirement.
#define UNIT_TEST

#include "include/cef_frame.h"
#include "include/test/cef_test_helpers.h"

#include "jni_util.h"

extern "C" JNIEXPORT void JNICALL
Java_tests_junittests_CefTestHelper_N_1ExecuteJavaScriptWithUserGestureForTests(
    JNIEnv* env,
    jclass,
    jlong frameNativeRef,
    jstring javascript) {
  CefRefPtr<CefFrame> frame = reinterpret_cast<CefFrame*>(frameNativeRef);
  if (!frame)
    return;
  CefExecuteJavaScriptWithUserGestureForTests(frame,
                                              GetJNIString(env, javascript));
}
