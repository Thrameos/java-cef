// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

#include "CefPrintJobCallback_N.h"
#include "jni_scoped_helpers.h"
#include "jni_util.h"

#include "include/cef_print_handler.h"

namespace {

CefRefPtr<CefPrintJobCallback> GetSelf(jlong self) {
  return reinterpret_cast<CefPrintJobCallback*>(self);
}

void ClearSelf(JNIEnv* env, jobject obj) {
  // Clear the reference added in PrintJobHandler::OnPrintJob.
  SetCefForJNIObject<CefPrintJobCallback>(env, obj, nullptr,
                                          "CefPrintJobCallback");
}

}  // namespace

JNIEXPORT void JNICALL
Java_org_cef_callback_CefPrintJobCallback_1N_N_1Continue(JNIEnv* env,
                                                         jobject obj,
                                                         jlong self) {
  // See jni_util.h's JNI_REQUIRE_CEF_ALIVE_OR_RETURN comment -- reachable
  // from CefPrintJobCallback_N.java's finalize().
  JNI_REQUIRE_CEF_ALIVE_OR_RETURN();
  CefRefPtr<CefPrintJobCallback> callback = GetSelf(self);
  if (!callback)
    return;
  callback->Continue();
  ClearSelf(env, obj);
}
