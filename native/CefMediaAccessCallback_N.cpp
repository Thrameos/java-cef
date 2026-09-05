// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

#include "CefMediaAccessCallback_N.h"
#include "include/cef_permission_handler.h"
#include "jni_scoped_helpers.h"
#include "jni_util.h"

namespace {

CefRefPtr<CefMediaAccessCallback> GetSelf(jlong self) {
  return reinterpret_cast<CefMediaAccessCallback*>(self);
}

void ClearSelf(JNIEnv* env, jobject obj) {
  // Clear the reference added in PermissionHandler::OnRequestMediaAccessPermission.
  SetCefForJNIObject_sync<CefMediaAccessCallback>(env, obj, nullptr,
                                             "CefMediaAccessCallback");
}

}  // namespace

JNIEXPORT void JNICALL
Java_org_cef_callback_CefMediaAccessCallback_1N_N_1Continue(
    JNIEnv* env,
    jobject obj,
    jlong self,
    jint jallowedPermissions) {
  // See jni_util.h's JNI_REQUIRE_CEF_ALIVE_OR_RETURN comment -- reachable
  // from CefMediaAccessCallback_N.java's finalize().
  JNI_REQUIRE_CEF_ALIVE_OR_RETURN();
  CefRefPtr<CefMediaAccessCallback> callback = GetSelf(self);
  if (!callback)
    return;
  callback->Continue(static_cast<uint32_t>(jallowedPermissions));
  ClearSelf(env, obj);
}

JNIEXPORT void JNICALL
Java_org_cef_callback_CefMediaAccessCallback_1N_N_1Cancel(JNIEnv* env,
                                                          jobject obj,
                                                          jlong self) {
  // See jni_util.h's JNI_REQUIRE_CEF_ALIVE_OR_RETURN comment -- reachable
  // from CefMediaAccessCallback_N.java's finalize().
  JNI_REQUIRE_CEF_ALIVE_OR_RETURN();
  CefRefPtr<CefMediaAccessCallback> callback = GetSelf(self);
  if (!callback)
    return;
  callback->Cancel();
  ClearSelf(env, obj);
}
