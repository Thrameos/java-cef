// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

#include "CefBeforeDownloadCallback_N.h"
#include "include/cef_download_handler.h"
#include "jni_scoped_helpers.h"
#include "jni_util.h"

namespace {

CefRefPtr<CefBeforeDownloadCallback> GetSelf(jlong self) {
  return reinterpret_cast<CefBeforeDownloadCallback*>(self);
}

void ClearSelf(JNIEnv* env, jobject obj) {
  // Clear the reference added in DownloadHandler::OnBeforeDownload.
  SetCefForJNIObject_sync<CefBeforeDownloadCallback>(env, obj, nullptr,
                                                "CefBeforeDownloadCallback");
}

}  // namespace

JNIEXPORT void JNICALL
Java_org_cef_callback_CefBeforeDownloadCallback_1N_N_1Continue(
    JNIEnv* env,
    jobject obj,
    jlong self,
    jstring jdownloadPath,
    jboolean jshowDialog) {
  // See jni_util.h's JNI_REQUIRE_CEF_ALIVE_OR_RETURN comment -- reachable
  // from CefBeforeDownloadCallback_N.java's finalize().
  JNI_REQUIRE_CEF_ALIVE_OR_RETURN();
  CefRefPtr<CefBeforeDownloadCallback> callback = GetSelf(self);
  if (!callback)
    return;
  callback->Continue(GetJNIString(env, jdownloadPath),
                     jshowDialog != JNI_FALSE);
  ClearSelf(env, obj);
}
