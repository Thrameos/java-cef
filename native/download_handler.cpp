// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

#include "download_handler.h"

#include "jni_util.h"

namespace {

// JNI CefDownloadItem object.
class ScopedJNIDownloadItem : public ScopedJNIObject<CefDownloadItem> {
 public:
  ScopedJNIDownloadItem(JNIEnv* env, CefRefPtr<CefDownloadItem> obj)
      : ScopedJNIObject<CefDownloadItem>(env,
                                         obj,
                                         "org/cef/callback/CefDownloadItem_N",
                                         "CefDownloadItem") {}
};

// JNI CefBeforeDownloadCallback object.
class ScopedJNIBeforeDownloadCallback
    : public ScopedJNIObject<CefBeforeDownloadCallback> {
 public:
  ScopedJNIBeforeDownloadCallback(JNIEnv* env,
                                  CefRefPtr<CefBeforeDownloadCallback> obj)
      : ScopedJNIObject<CefBeforeDownloadCallback>(
            env,
            obj,
            "org/cef/callback/CefBeforeDownloadCallback_N",
            "CefBeforeDownloadCallback") {}
};

// JNI CefDownloadItemCallback object.
class ScopedJNIDownloadItemCallback
    : public ScopedJNIObject<CefDownloadItemCallback> {
 public:
  ScopedJNIDownloadItemCallback(JNIEnv* env,
                                CefRefPtr<CefDownloadItemCallback> obj)
      : ScopedJNIObject<CefDownloadItemCallback>(
            env,
            obj,
            "org/cef/callback/CefDownloadItemCallback_N",
            "CefDownloadItemCallback") {}
};

}  // namespace

DownloadHandler::DownloadHandler(JNIEnv* env, jobject handler)
    : handle_(env, handler) {}

bool DownloadHandler::OnBeforeDownload(
    CefRefPtr<CefBrowser> browser,
    CefRefPtr<CefDownloadItem> download_item,
    const CefString& suggested_name,
    CefRefPtr<CefBeforeDownloadCallback> callback) {
  // Always return true to CEF, regardless of what Java's onBeforeDownload
  // returns below (see plan/tasks/20260905-26-download-shelf-check-crash.md):
  // returning false to CEF here would defer to Chrome-style default
  // handling (the download shelf), which this project's embedding has
  // never implemented and which can crash the process with an internal
  // CHECK if the browser is torn down before that deferred handling runs.
  // Taking ownership unconditionally (true) and simply never invoking
  // |callback| when Java's handler returns false is the same
  // already-safe "drop the callback un-run" cancel path, just applied
  // uniformly instead of leaving false as a latent trap.
  ScopedJNIEnv env;
  if (!env)
    return true;

  ScopedJNIBrowser jbrowser(env, browser);
  ScopedJNIDownloadItem jdownloadItem(env, download_item);
  jdownloadItem.SetTemporary();
  ScopedJNIString jsuggestedName(env, suggested_name);
  ScopedJNIBeforeDownloadCallback jcallback(env, callback);

  jboolean jresult = 0;

  JNI_CALL_BOOLEAN_METHOD(
      jresult, env, handle_, "onBeforeDownload",
      "(Lorg/cef/browser/CefBrowser;Lorg/cef/callback/CefDownloadItem;"
      "Ljava/lang/String;Lorg/cef/callback/CefBeforeDownloadCallback;)Z",
      jbrowser.get(), jdownloadItem.get(), jsuggestedName.get(),
      jcallback.get());

  // jresult only controls whether Java's own onBeforeDownload call above
  // was expected to invoke callback->Continue() itself (true) or leave it
  // un-run (false) -- either way the callback's fate is already decided
  // by the time we get here, and CEF's own return value is now always
  // true (see the file-level comment above).
  (void)jresult;
  return true;
}

void DownloadHandler::OnDownloadUpdated(
    CefRefPtr<CefBrowser> browser,
    CefRefPtr<CefDownloadItem> download_item,
    CefRefPtr<CefDownloadItemCallback> callback) {
  ScopedJNIEnv env;
  if (!env)
    return;

  ScopedJNIBrowser jbrowser(env, browser);
  ScopedJNIDownloadItem jdownloadItem(env, download_item);
  jdownloadItem.SetTemporary();
  ScopedJNIDownloadItemCallback jcallback(env, callback);

  JNI_CALL_VOID_METHOD(
      env, handle_, "onDownloadUpdated",
      "(Lorg/cef/browser/CefBrowser;Lorg/cef/callback/CefDownloadItem;"
      "Lorg/cef/callback/CefDownloadItemCallback;)V",
      jbrowser.get(), jdownloadItem.get(), jcallback.get());
}
