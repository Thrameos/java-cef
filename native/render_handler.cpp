// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

#include "render_handler.h"
#include "jcef_trace.h"

#include "client_handler.h"
#include "jni_util.h"

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

jobject NewJNIScreenInfo(JNIEnv* env, CefScreenInfo& screenInfo) {
  ScopedJNIClass cls(env, "org/cef/handler/CefScreenInfo");
  if (!cls) {
    return nullptr;
  }

  ScopedJNIObjectLocal obj(env, NewJNIObject(env, cls));
  if (!obj) {
    return nullptr;
  }

  if (SetJNIFieldDouble(env, cls, obj, "device_scale_factor",
                        (double)screenInfo.device_scale_factor) &&
      SetJNIFieldInt(env, cls, obj, "depth", screenInfo.depth) &&
      SetJNIFieldInt(env, cls, obj, "depth_per_component",
                     screenInfo.depth_per_component) &&
      SetJNIFieldBoolean(env, cls, obj, "is_monochrome",
                         screenInfo.is_monochrome) &&
      SetJNIFieldInt(env, cls, obj, "x", screenInfo.rect.x) &&
      SetJNIFieldInt(env, cls, obj, "y", screenInfo.rect.y) &&
      SetJNIFieldInt(env, cls, obj, "width", screenInfo.rect.width) &&
      SetJNIFieldInt(env, cls, obj, "height", screenInfo.rect.height) &&
      SetJNIFieldInt(env, cls, obj, "available_x",
                     screenInfo.available_rect.x) &&
      SetJNIFieldInt(env, cls, obj, "available_y",
                     screenInfo.available_rect.y) &&
      SetJNIFieldInt(env, cls, obj, "available_width",
                     screenInfo.available_rect.width) &&
      SetJNIFieldInt(env, cls, obj, "available_height",
                     screenInfo.available_rect.height)) {
    return obj.Release();
  }

  return nullptr;
}

bool GetJNIScreenInfo(JNIEnv* env, jobject jScreenInfo, CefScreenInfo& dest) {
  ScopedJNIClass cls(env, "org/cef/handler/CefScreenInfo");
  if (!cls) {
    return false;
  }

  // jScreenInfo is a local ref owned by the caller (e.g. GetScreenInfo()'s own
  // ScopedJNIObjectLocal) -- don't wrap it in a second ScopedJNIObjectLocal
  // here, which would DeleteLocalRef it a second time when this function
  // returns (undefined behavior per the JNI spec). Only field reads are
  // needed below, which don't require owning the ref.
  if (!jScreenInfo) {
    return false;
  }
  double tmp;
  if (!GetJNIFieldDouble(env, cls, jScreenInfo, "device_scale_factor", &tmp)) {
    return false;
  }
  dest.device_scale_factor = (float)tmp;

  if (GetJNIFieldInt(env, cls, jScreenInfo, "depth", &(dest.depth)) &&
      GetJNIFieldInt(env, cls, jScreenInfo, "depth_per_component",
                     &(dest.depth_per_component)) &&
      GetJNIFieldBoolean(env, cls, jScreenInfo, "is_monochrome",
                         &(dest.is_monochrome)) &&
      GetJNIFieldInt(env, cls, jScreenInfo, "x", &(dest.rect.x)) &&
      GetJNIFieldInt(env, cls, jScreenInfo, "y", &(dest.rect.y)) &&
      GetJNIFieldInt(env, cls, jScreenInfo, "width", &(dest.rect.width)) &&
      GetJNIFieldInt(env, cls, jScreenInfo, "height", &(dest.rect.height)) &&
      GetJNIFieldInt(env, cls, jScreenInfo, "available_x",
                     &(dest.available_rect.x)) &&
      GetJNIFieldInt(env, cls, jScreenInfo, "available_y",
                     &(dest.available_rect.y)) &&
      GetJNIFieldInt(env, cls, jScreenInfo, "available_width",
                     &(dest.available_rect.width)) &&
      GetJNIFieldInt(env, cls, jScreenInfo, "available_height",
                     &(dest.available_rect.height))

  ) {
    return true;
  } else {
    return false;
  }
}

// create a new array of java.awt.Rectangle.
jobjectArray NewJNIRectArray(JNIEnv* env, const std::vector<CefRect>& vals) {
  if (vals.empty())
    return nullptr;

  ScopedJNIClass cls(env, "java/awt/Rectangle");
  if (!cls)
    return nullptr;

  const jsize size = static_cast<jsize>(vals.size());
  jobjectArray arr = env->NewObjectArray(size, cls, nullptr);

  for (jsize i = 0; i < size; i++) {
    ScopedJNIObjectLocal rect_obj(env, NewJNIRect(env, vals[i]));
    env->SetObjectArrayElement(arr, i, rect_obj);
  }

  return arr;
}

// Create a new java.awt.Point.
jobject NewJNIPoint(JNIEnv* env, int x, int y) {
  ScopedJNIClass cls(env, "java/awt/Point");
  if (!cls)
    return nullptr;

  ScopedJNIObjectLocal obj(env, NewJNIObject(env, cls));
  if (!obj)
    return nullptr;

  if (SetJNIFieldInt(env, cls, obj, "x", x) &&
      SetJNIFieldInt(env, cls, obj, "y", y)) {
    return obj.Release();
  }

  return nullptr;
}

}  // namespace

RenderHandler::RenderHandler(JNIEnv* env, jobject handler)
    : handle_(env, handler) {}

bool RenderHandler::GetRootScreenRect(CefRefPtr<CefBrowser> browser,
                                      CefRect& rect) {
  JCEF_TRACE("RenderHandler::GetRootScreenRect() ENTER");
  ScopedJNIEnv env;
  if (!env)
    return false;

  ScopedJNIBrowser jbrowser(env, browser);
  bool result = GetViewRect(jbrowser, rect);
  return result;
}

void RenderHandler::GetViewRect(CefRefPtr<CefBrowser> browser, CefRect& rect) {
  JCEF_TRACE("RenderHandler::GetViewRect() ENTER");
  ScopedJNIEnv env;
  if (!env)
    return;

  ScopedJNIBrowser jbrowser(env, browser);
  if (!GetViewRect(jbrowser, rect)) {
    rect = CefRect(0, 0, 1, 1);
  }
}

///
// Called to allow the client to fill in the CefScreenInfo object with
// appropriate values. Return true if the |screen_info| structure has been
// modified.
//
// If the screen info rectangle is left empty the rectangle from GetViewRect
// will be used. If the rectangle is still empty or invalid popups may not be
// drawn correctly.
///
/*--cef()--*/
bool RenderHandler::GetScreenInfo(CefRefPtr<CefBrowser> browser,
                                  CefScreenInfo& screen_info) {
  JCEF_TRACE("RenderHandler::GetScreenInfo() ENTER");
  ScopedJNIEnv env;
  if (!env) {
    return false;
  }

  ScopedJNIObjectLocal jScreenInfo(env, NewJNIScreenInfo(env, screen_info));
  if (!jScreenInfo) {
    return false;
  }
  ScopedJNIBrowser jbrowser(env, browser);
  jboolean jresult = 0;

  JNI_CALL_BOOLEAN_METHOD(
      jresult, env, jbrowser.get(), "getScreenInfo",
      "(Lorg/cef/browser/CefBrowser;Lorg/cef/handler/CefScreenInfo;)Z",
      jbrowser.get(), jScreenInfo.get());

  if (jresult) {
    if (GetJNIScreenInfo(env, jScreenInfo.get(), screen_info)) {
      return true;
    }
  }

  return false;
}

bool RenderHandler::GetScreenPoint(CefRefPtr<CefBrowser> browser,
                                   int viewX,
                                   int viewY,
                                   int& screenX,
                                   int& screenY) {
  JCEF_TRACE("RenderHandler::GetScreenPoint() ENTER");
  ScopedJNIEnv env;
  if (!env)
    return false;

  ScopedJNIBrowser jbrowser(env, browser);
  return GetScreenPoint(jbrowser, viewX, viewY, screenX, screenY);
}

void RenderHandler::OnPopupShow(CefRefPtr<CefBrowser> browser, bool show) {
  JCEF_TRACE("RenderHandler::OnPopupShow() ENTER");
  ScopedJNIEnv env;
  if (!env)
    return;

  ScopedJNIBrowser jbrowser(env, browser);
  JNI_CALL_VOID_METHOD(env, handle_, "onPopupShow",
                       "(Lorg/cef/browser/CefBrowser;Z)V", jbrowser.get(),
                       (jboolean)show);
}

void RenderHandler::OnPopupSize(CefRefPtr<CefBrowser> browser,
                                const CefRect& rect) {
  JCEF_TRACE("RenderHandler::OnPopupSize() ENTER");
  ScopedJNIEnv env;
  if (!env)
    return;

  ScopedJNIObjectLocal jrect(env, NewJNIRect(env, rect));
  if (!jrect)
    return;

  ScopedJNIBrowser jbrowser(env, browser);
  JNI_CALL_VOID_METHOD(env, handle_, "onPopupSize",
                       "(Lorg/cef/browser/CefBrowser;Ljava/awt/Rectangle;)V",
                       jbrowser.get(), jrect.get());
}

void RenderHandler::OnPaint(CefRefPtr<CefBrowser> browser,
                            PaintElementType type,
                            const RectList& dirtyRects,
                            const void* buffer,
                            int width,
                            int height) {
  JCEF_TRACE("RenderHandler::OnPaint() ENTER");
  ScopedJNIEnv env;
  if (!env)
    return;

  ScopedJNIBrowser jbrowser(env, browser);
  jboolean jtype = type == PET_VIEW ? JNI_FALSE : JNI_TRUE;
  ScopedJNIObjectLocal jrectArray(env, NewJNIRectArray(env, dirtyRects));
  ScopedJNIObjectLocal jdirectBuffer(
      env,
      env->NewDirectByteBuffer(const_cast<void*>(buffer), width * height * 4));
  JNI_CALL_VOID_METHOD(env, handle_, "onPaint",
                       "(Lorg/cef/browser/CefBrowser;Z[Ljava/awt/"
                       "Rectangle;Ljava/nio/ByteBuffer;II)V",
                       jbrowser.get(), jtype, jrectArray.get(),
                       jdirectBuffer.get(), width, height);
}

bool RenderHandler::StartDragging(CefRefPtr<CefBrowser> browser,
                                  CefRefPtr<CefDragData> drag_data,
                                  DragOperationsMask allowed_ops,
                                  int x,
                                  int y) {
  JCEF_TRACE("RenderHandler::StartDragging() ENTER");
  ScopedJNIEnv env;
  if (!env)
    return false;

  ScopedJNIBrowser jbrowser(env, browser);
  ScopedJNIDragData jdragdata(env, drag_data);
  jdragdata.SetTemporary();
  jboolean jresult = JNI_FALSE;
  JNI_CALL_METHOD(
      env, handle_, "startDragging",
      "(Lorg/cef/browser/CefBrowser;Lorg/cef/callback/CefDragData;III)Z",
      Boolean, jresult, jbrowser.get(), jdragdata.get(), (jint)allowed_ops,
      (jint)x, (jint)y);

  return (jresult != JNI_FALSE);
}

void RenderHandler::UpdateDragCursor(CefRefPtr<CefBrowser> browser,
                                     DragOperation operation) {
  JCEF_TRACE("RenderHandler::UpdateDragCursor() ENTER");
  ScopedJNIEnv env;
  if (!env)
    return;

  ScopedJNIBrowser jbrowser(env, browser);
  JNI_CALL_VOID_METHOD(env, handle_, "updateDragCursor",
                       "(Lorg/cef/browser/CefBrowser;I)V", jbrowser.get(),
                       (jint)operation);
}

bool RenderHandler::GetViewRect(jobject browser, CefRect& rect) {
  JCEF_TRACE("RenderHandler::GetViewRect() ENTER");
  ScopedJNIEnv env;
  if (!env)
    return false;

  ScopedJNIObjectResult jreturn(env);
  JNI_CALL_METHOD(env, handle_, "getViewRect",
                  "(Lorg/cef/browser/CefBrowser;)Ljava/awt/Rectangle;", Object,
                  jreturn, browser);
  if (jreturn) {
    rect = GetJNIRect(env, jreturn);
    return true;
  }
  return false;
}

bool RenderHandler::GetScreenPoint(jobject browser,
                                   int viewX,
                                   int viewY,
                                   int& screenX,
                                   int& screenY) {
  JCEF_TRACE("RenderHandler::GetScreenPoint() ENTER");
  ScopedJNIEnv env;
  if (!env)
    return false;

  ScopedJNIObjectLocal jpoint(env, NewJNIPoint(env, viewX, viewY));
  if (!jpoint)
    return false;

  ScopedJNIObjectResult jreturn(env);
  JNI_CALL_METHOD(
      env, handle_, "getScreenPoint",
      "(Lorg/cef/browser/CefBrowser;Ljava/awt/Point;)Ljava/awt/Point;", Object,
      jreturn, browser, jpoint.get());

  if (jreturn) {
    GetJNIPoint(env, jreturn, &screenX, &screenY);
    return true;
  }
  return false;
}
