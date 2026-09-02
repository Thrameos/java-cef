// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

#ifndef JCEF_NATIVE_FRAME_HANDLER_H_
#define JCEF_NATIVE_FRAME_HANDLER_H_
#pragma once

#include <jni.h>
#include "include/cef_frame_handler.h"

#include "jni_scoped_helpers.h"

// FrameHandler implementation.
class FrameHandler : public CefFrameHandler {
 public:
  FrameHandler(JNIEnv* env, jobject handler);

  // CefFrameHandler methods
  virtual void OnFrameCreated(CefRefPtr<CefBrowser> browser,
                              CefRefPtr<CefFrame> frame) override;
  virtual void OnFrameDestroyed(CefRefPtr<CefBrowser> browser,
                                CefRefPtr<CefFrame> frame) override;
  virtual void OnFrameAttached(CefRefPtr<CefBrowser> browser,
                               CefRefPtr<CefFrame> frame,
                               bool reattached) override;
  virtual void OnFrameDetached(CefRefPtr<CefBrowser> browser,
                               CefRefPtr<CefFrame> frame) override;
  virtual void OnMainFrameChanged(CefRefPtr<CefBrowser> browser,
                                  CefRefPtr<CefFrame> old_frame,
                                  CefRefPtr<CefFrame> new_frame) override;

 protected:
  ScopedJNIObjectGlobal handle_;

  // Include the default reference counting implementation.
  IMPLEMENT_REFCOUNTING(FrameHandler);
};

#endif  // JCEF_NATIVE_FRAME_HANDLER_H_
