// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

#ifndef JCEF_NATIVE_FIND_HANDLER_H_
#define JCEF_NATIVE_FIND_HANDLER_H_
#pragma once

#include <jni.h>
#include "include/cef_find_handler.h"

#include "jni_scoped_helpers.h"

// FindHandler implementation.
class FindHandler : public CefFindHandler {
 public:
  FindHandler(JNIEnv* env, jobject handler);

  // CefFindHandler methods
  virtual void OnFindResult(CefRefPtr<CefBrowser> browser,
                            int identifier,
                            int count,
                            const CefRect& selectionRect,
                            int activeMatchOrdinal,
                            bool finalUpdate) override;

 protected:
  ScopedJNIObjectGlobal handle_;

  // Include the default reference counting implementation.
  IMPLEMENT_REFCOUNTING(FindHandler);
};

#endif  // JCEF_NATIVE_FIND_HANDLER_H_
