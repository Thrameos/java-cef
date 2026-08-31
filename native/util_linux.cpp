// Copyright (c) 2015 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

#include "util.h"

#include <X11/Xlib.h>
#undef Success

#include "include/base/cef_callback.h"
#include "include/cef_task.h"
#include "include/wrapper/cef_closure_task.h"

#include "client_handler.h"
#include "jcef_trace.h"
#include "jni_util.h"
#include "temp_window.h"

namespace util {

namespace {

// Windowed (X11) browsers on Linux can hang indefinitely on close: CEF's own
// close teardown for a windowed browser depends on a synthetic X11 event (a
// WM_DELETE_WINDOW ClientMessage CEF sends to its own window) that has been
// confirmed -- via gdb and strace, re-sending the identical message through
// a separate, definitely-working X11 connection included -- to never be
// processed under this embedding, regardless of how it is delivered. See
// plan/roadmap.md's windowed-close investigation for the full trace.
//
// Rather than leave the browser (and, transitively, the whole app's
// eventual CefApp/CefShutdown()) hung forever waiting for a native callback
// that isn't coming, if CEF's own real LifeSpanHandler::OnBeforeClose()
// hasn't arrived within this delay, simulate it -- calling the exact same
// public CefLifeSpanHandler::OnBeforeClose() entry point CEF itself would
// have called, satisfying the same downstream JNI contract Java code
// expects, just triggered by JCEF instead of by CEF's own (stuck) event
// source. The underlying native browser object is left alone; if CEF's
// real close ever does complete later, LifeSpanHandler::OnBeforeClose()'s
// own idempotency guard (g_closed_browser_ids) makes that late, genuine call
// a no-op instead of double-invoking user handlers or double-disposing
// resources.
const int64_t kWindowedCloseFallbackDelayMs = 2000;

void FakeOnBeforeCloseIfNeeded(CefRefPtr<CefBrowser> browser) {
  if (!browser || !browser->GetHost()) {
    return;
  }
  CefRefPtr<ClientHandler> client =
      (ClientHandler*)browser->GetHost()->GetClient().get();
  if (!client) {
    return;
  }
  CefRefPtr<CefLifeSpanHandler> handler = client->GetLifeSpanHandler();
  if (!handler) {
    return;
  }
  JCEF_TRACE(
      "util::FakeOnBeforeCloseIfNeeded() browser_id=%d -- calling "
      "OnBeforeClose() (idempotency-guarded, a no-op if the real one "
      "already ran)",
      browser->GetIdentifier());
  handler->OnBeforeClose(browser);
}

void X_XMoveResizeWindow(unsigned long browserHandle,
                         int x,
                         int y,
                         unsigned int width,
                         unsigned int height) {
  ::Display* xdisplay = (::Display*)TempWindow::GetDisplay();
  XMoveResizeWindow(xdisplay, browserHandle, 0, 0, width, height);
  XFlush(xdisplay);
}

void X_XReparentWindow(unsigned long browserHandle,
                       unsigned long parentDrawable) {
  ::Display* xdisplay = (::Display*)TempWindow::GetDisplay();
  XReparentWindow(xdisplay, browserHandle, parentDrawable, 0, 0);
  XFlush(xdisplay);
}

void X_XSync(bool discard) {
  ::Display* xdisplay = (::Display*)TempWindow::GetDisplay();
  XSync(xdisplay, discard);
}

}  // namespace

// This function is called by LifeSpanHandler::OnAfterCreated().
void AddCefBrowser(CefRefPtr<CefBrowser> browser) {
  // TODO(jcef): Implement this function stub to do some platform dependent
  // tasks for the browser reference like registering mouse events.

  UNUSED(browser);
}

// This function is called by LifeSpanHandler::DoClose().
void DestroyCefBrowser(CefRefPtr<CefBrowser> browser) {
  JCEF_TRACE(
      "util::DestroyCefBrowser() ENTER browser_id=%d, calling "
      "CloseBrowser(true)",
      browser->GetIdentifier());
  browser->GetHost()->CloseBrowser(true);
  CefPostDelayedTask(TID_UI,
                     base::BindOnce(&FakeOnBeforeCloseIfNeeded, browser),
                     kWindowedCloseFallbackDelayMs);
  JCEF_TRACE(
      "util::DestroyCefBrowser() EXIT browser_id=%d (CloseBrowser(true) "
      "returned, fallback OnBeforeClose() scheduled in %lldms)",
      browser->GetIdentifier(), (long long)kWindowedCloseFallbackDelayMs);
}

CefWindowHandle GetWindowHandle(JNIEnv* env, jobject canvas) {
  return GetDrawableOfCanvas(canvas, env);
}

void SetParent(CefWindowHandle browserHandle,
               CefWindowHandle parentHandle,
               base::OnceClosure callback) {
  SetParentSync(browserHandle, parentHandle, nullptr, std::move(callback));
}

void SetParentSync(CefWindowHandle browserHandle,
                   CefWindowHandle parentHandle,
                   CriticalWait* waitCond,
                   base::OnceClosure callback) {
  if (waitCond) {
    waitCond->lock()->Lock();
  }
  if (parentHandle == kNullWindowHandle)
    parentHandle = TempWindow::GetWindowHandle();
  if (parentHandle != kNullWindowHandle && browserHandle != kNullWindowHandle)
    X_XReparentWindow(browserHandle, parentHandle);

  if (waitCond) {
    X_XSync(false);
    waitCond->WakeUp();
    waitCond->lock()->Unlock();
  }
  std::move(callback).Run();
}

void SetWindowBounds(CefWindowHandle browserHandle,
                     const CefRect& contentRect) {
  X_XMoveResizeWindow(browserHandle, contentRect.x, contentRect.y,
                      contentRect.width, contentRect.height);
}

void SetWindowSize(CefWindowHandle browserHandle, int width, int height) {
  X_XMoveResizeWindow(browserHandle, 0, 0, width, height);
}

}  // namespace util
