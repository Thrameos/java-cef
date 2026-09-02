// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

#include "permission_handler.h"
#include "jcef_trace.h"

#include "client_handler.h"
#include "jni_util.h"
#include "util.h"

namespace {

// JNI CefMediaAccessCallback object.
class ScopedJNIMediaAccessCallback
    : public ScopedJNIObject<CefMediaAccessCallback> {
 public:
  ScopedJNIMediaAccessCallback(JNIEnv* env, CefRefPtr<CefMediaAccessCallback> obj)
      : ScopedJNIObject<CefMediaAccessCallback>(
            env,
            obj,
            "org/cef/callback/CefMediaAccessCallback_N",
            "CefMediaAccessCallback") {}
};

// JNI CefPermissionPromptCallback object.
class ScopedJNIPermissionPromptCallback
    : public ScopedJNIObject<CefPermissionPromptCallback> {
 public:
  ScopedJNIPermissionPromptCallback(JNIEnv* env,
                                    CefRefPtr<CefPermissionPromptCallback> obj)
      : ScopedJNIObject<CefPermissionPromptCallback>(
            env,
            obj,
            "org/cef/callback/CefPermissionPromptCallback_N",
            "CefPermissionPromptCallback") {}
};

}  // namespace

PermissionHandler::PermissionHandler(JNIEnv* env, jobject handler)
    : handle_(env, handler) {}

bool PermissionHandler::OnRequestMediaAccessPermission(
    CefRefPtr<CefBrowser> browser,
    CefRefPtr<CefFrame> frame,
    const CefString& requesting_origin,
    uint32_t requested_permissions,
    CefRefPtr<CefMediaAccessCallback> callback) {
  JCEF_TRACE("PermissionHandler::OnRequestMediaAccessPermission() ENTER");
  ScopedJNIEnv env;
  if (!env)
    return false;

  ScopedJNIBrowser jbrowser(env, browser);
  ScopedJNIFrame jframe(env, frame);
  jframe.SetTemporary();
  ScopedJNIString jrequestingOrigin(env, requesting_origin);
  ScopedJNIMediaAccessCallback jcallback(env, callback);

  jboolean jresult = JNI_FALSE;

  JNI_CALL_METHOD(env, handle_, "onRequestMediaAccessPermission",
                  "(Lorg/cef/browser/CefBrowser;Lorg/cef/browser/CefFrame;"
                  "Ljava/lang/String;ILorg/cef/callback/"
                  "CefMediaAccessCallback;)Z",
                  Boolean, jresult, jbrowser.get(), jframe.get(),
                  jrequestingOrigin.get(), (jint)requested_permissions,
                  jcallback.get());

  if (jresult == JNI_FALSE) {
    // If the Java method returns "false" the callback won't be used and
    // the reference can therefore be removed.
    jcallback.SetTemporary();
  }

  return (jresult != JNI_FALSE);
}

bool PermissionHandler::OnShowPermissionPrompt(
    CefRefPtr<CefBrowser> browser,
    uint64_t prompt_id,
    const CefString& requesting_origin,
    uint32_t requested_permissions,
    CefRefPtr<CefPermissionPromptCallback> callback) {
  JCEF_TRACE("PermissionHandler::OnShowPermissionPrompt() ENTER");
  ScopedJNIEnv env;
  if (!env)
    return false;

  ScopedJNIBrowser jbrowser(env, browser);
  ScopedJNIString jrequestingOrigin(env, requesting_origin);
  ScopedJNIPermissionPromptCallback jcallback(env, callback);

  jboolean jresult = JNI_FALSE;

  JNI_CALL_METHOD(env, handle_, "onShowPermissionPrompt",
                  "(Lorg/cef/browser/CefBrowser;JLjava/lang/String;ILorg/cef/"
                  "callback/CefPermissionPromptCallback;)Z",
                  Boolean, jresult, jbrowser.get(), (jlong)prompt_id,
                  jrequestingOrigin.get(), (jint)requested_permissions,
                  jcallback.get());

  if (jresult == JNI_FALSE) {
    // If the Java method returns "false" the callback won't be used and
    // the reference can therefore be removed.
    jcallback.SetTemporary();
  }

  return (jresult != JNI_FALSE);
}

void PermissionHandler::OnDismissPermissionPrompt(
    CefRefPtr<CefBrowser> browser,
    uint64_t prompt_id,
    cef_permission_request_result_t result) {
  JCEF_TRACE("PermissionHandler::OnDismissPermissionPrompt() ENTER");
  ScopedJNIEnv env;
  if (!env)
    return;

  ScopedJNIBrowser jbrowser(env, browser);

  JNI_CALL_VOID_METHOD(env, handle_, "onDismissPermissionPrompt",
                       "(Lorg/cef/browser/CefBrowser;JI)V", jbrowser.get(),
                       (jlong)prompt_id, (jint)result);
}
