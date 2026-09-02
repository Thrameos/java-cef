// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

#include "find_handler.h"
#include "jcef_trace.h"

#include "client_handler.h"
#include "jni_util.h"
#include "util.h"

namespace {

// Create a new java.awt.Rectangle.
jobject NewJNIRect(JNIEnv* env, const CefRect& rect) {
  ScopedJNIClass cls(env, "java/awt/Rectangle");
  if (!cls)
    return nullptr;

  ScopedJNIObjectLocal obj(env, NewJNIObject(env, cls));
  if (!obj)
    return nullptr;

  if (SetJNIFieldInt(env, cls, obj, "x", rect.x) &&
      SetJNIFieldInt(env, cls, obj, "y", rect.y) &&
      SetJNIFieldInt(env, cls, obj, "width", rect.width) &&
      SetJNIFieldInt(env, cls, obj, "height", rect.height)) {
    return obj.Release();
  }

  return nullptr;
}

}  // namespace

FindHandler::FindHandler(JNIEnv* env, jobject handler) : handle_(env, handler) {}

void FindHandler::OnFindResult(CefRefPtr<CefBrowser> browser,
                               int identifier,
                               int count,
                               const CefRect& selectionRect,
                               int activeMatchOrdinal,
                               bool finalUpdate) {
  JCEF_TRACE("FindHandler::OnFindResult() ENTER");
  ScopedJNIEnv env;
  if (!env)
    return;

  ScopedJNIBrowser jbrowser(env, browser);
  ScopedJNIObjectLocal jselectionRect(env, NewJNIRect(env, selectionRect));

  JNI_CALL_VOID_METHOD(env, handle_, "onFindResult",
                       "(Lorg/cef/browser/CefBrowser;IILjava/awt/Rectangle;"
                       "IZ)V",
                       jbrowser.get(), (jint)identifier, (jint)count,
                       jselectionRect.get(), (jint)activeMatchOrdinal,
                       (finalUpdate ? JNI_TRUE : JNI_FALSE));
}
