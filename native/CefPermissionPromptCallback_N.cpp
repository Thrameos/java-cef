// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

#include "CefPermissionPromptCallback_N.h"
#include "include/cef_permission_handler.h"
#include "jni_scoped_helpers.h"
#include "jni_util.h"

namespace {

CefRefPtr<CefPermissionPromptCallback> GetSelf(jlong self) {
  return reinterpret_cast<CefPermissionPromptCallback*>(self);
}

void ClearSelf(JNIEnv* env, jobject obj) {
  // Clear the reference added in PermissionHandler::OnShowPermissionPrompt.
  SetCefForJNIObject_sync<CefPermissionPromptCallback>(
      env, obj, nullptr, "CefPermissionPromptCallback");
}

}  // namespace

JNIEXPORT void JNICALL
Java_org_cef_callback_CefPermissionPromptCallback_1N_N_1Continue(
    JNIEnv* env,
    jobject obj,
    jlong self,
    jint jresult) {
  // See jni_util.h's JNI_REQUIRE_CEF_ALIVE_OR_RETURN comment -- reachable
  // from CefPermissionPromptCallback_N.java's finalize().
  JNI_REQUIRE_CEF_ALIVE_OR_RETURN();
  CefRefPtr<CefPermissionPromptCallback> callback = GetSelf(self);
  if (!callback)
    return;
  // jresult's values are defined in CefPermissionHandler.
  // PermissionRequestResult to match cef_permission_request_result_t's
  // declaration order (ACCEPT=0, DENY=1, DISMISS=2, IGNORE=3) exactly.
  callback->Continue(static_cast<cef_permission_request_result_t>(jresult));
  ClearSelf(env, obj);
}
