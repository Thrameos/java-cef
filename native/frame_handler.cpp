// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

#include "frame_handler.h"
#include "jcef_trace.h"

#include "client_handler.h"
#include "jni_util.h"
#include "util.h"

FrameHandler::FrameHandler(JNIEnv* env, jobject handler) : handle_(env, handler) {}

void FrameHandler::OnFrameCreated(CefRefPtr<CefBrowser> browser,
                                  CefRefPtr<CefFrame> frame) {
  JCEF_TRACE("FrameHandler::OnFrameCreated() ENTER");
  ScopedJNIEnv env;
  if (!env)
    return;

  ScopedJNIBrowser jbrowser(env, browser);
  ScopedJNIFrame jframe(env, frame);
  jframe.SetTemporary();

  JNI_CALL_VOID_METHOD(env, handle_, "onFrameCreated",
                       "(Lorg/cef/browser/CefBrowser;Lorg/cef/browser/CefFrame;)V",
                       jbrowser.get(), jframe.get());
}

void FrameHandler::OnFrameDestroyed(CefRefPtr<CefBrowser> browser,
                                    CefRefPtr<CefFrame> frame) {
  JCEF_TRACE("FrameHandler::OnFrameDestroyed() ENTER");
  ScopedJNIEnv env;
  if (!env)
    return;

  ScopedJNIBrowser jbrowser(env, browser);
  ScopedJNIFrame jframe(env, frame);
  jframe.SetTemporary();

  JNI_CALL_VOID_METHOD(env, handle_, "onFrameDestroyed",
                       "(Lorg/cef/browser/CefBrowser;Lorg/cef/browser/CefFrame;)V",
                       jbrowser.get(), jframe.get());
}

void FrameHandler::OnFrameAttached(CefRefPtr<CefBrowser> browser,
                                   CefRefPtr<CefFrame> frame,
                                   bool reattached) {
  JCEF_TRACE("FrameHandler::OnFrameAttached() ENTER");
  ScopedJNIEnv env;
  if (!env)
    return;

  ScopedJNIBrowser jbrowser(env, browser);
  ScopedJNIFrame jframe(env, frame);
  jframe.SetTemporary();

  JNI_CALL_VOID_METHOD(env, handle_, "onFrameAttached",
                       "(Lorg/cef/browser/CefBrowser;Lorg/cef/browser/CefFrame;Z)V",
                       jbrowser.get(), jframe.get(),
                       (reattached ? JNI_TRUE : JNI_FALSE));
}

void FrameHandler::OnFrameDetached(CefRefPtr<CefBrowser> browser,
                                   CefRefPtr<CefFrame> frame) {
  JCEF_TRACE("FrameHandler::OnFrameDetached() ENTER");
  ScopedJNIEnv env;
  if (!env)
    return;

  ScopedJNIBrowser jbrowser(env, browser);
  ScopedJNIFrame jframe(env, frame);
  jframe.SetTemporary();

  JNI_CALL_VOID_METHOD(env, handle_, "onFrameDetached",
                       "(Lorg/cef/browser/CefBrowser;Lorg/cef/browser/CefFrame;)V",
                       jbrowser.get(), jframe.get());
}

void FrameHandler::OnMainFrameChanged(CefRefPtr<CefBrowser> browser,
                                      CefRefPtr<CefFrame> old_frame,
                                      CefRefPtr<CefFrame> new_frame) {
  JCEF_TRACE("FrameHandler::OnMainFrameChanged() ENTER");
  ScopedJNIEnv env;
  if (!env)
    return;

  ScopedJNIBrowser jbrowser(env, browser);
  // old_frame/new_frame are optional (CEF_INCLUDE_CEF_FRAME_HANDLER_H_'s
  // optional_param annotation) -- SetTemporary() DCHECKs on a
  // never-created handle, so only call it when the frame is non-null.
  ScopedJNIFrame joldFrame(env, old_frame);
  if (old_frame)
    joldFrame.SetTemporary();
  ScopedJNIFrame jnewFrame(env, new_frame);
  if (new_frame)
    jnewFrame.SetTemporary();

  JNI_CALL_VOID_METHOD(
      env, handle_, "onMainFrameChanged",
      "(Lorg/cef/browser/CefBrowser;Lorg/cef/browser/CefFrame;Lorg/cef/browser/"
      "CefFrame;)V",
      jbrowser.get(), joldFrame.get(), jnewFrame.get());
}
