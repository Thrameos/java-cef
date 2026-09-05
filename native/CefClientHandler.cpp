// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

#include "CefClientHandler.h"
#include "client_handler.h"
#include "context_menu_handler.h"
#include "dialog_handler.h"
#include "display_handler.h"
#include "download_handler.h"
#include "drag_handler.h"
#include "focus_handler.h"
#include "jni_util.h"
#include "jsdialog_handler.h"
#include "keyboard_handler.h"
#include "life_span_handler.h"
#include "load_handler.h"
#include "message_router_handler.h"
#include "print_handler.h"
#include "render_handler.h"
#include "request_handler.h"

JNIEXPORT void JNICALL
Java_org_cef_handler_CefClientHandler_N_1CefClientHandler_1CTOR(
    JNIEnv* env,
    jobject clientHandler) {
  CefRefPtr<ClientHandler> client = new ClientHandler(env, clientHandler);
  SetCefForJNIObject_sync(env, clientHandler, client.get(), "CefClientHandler");
}

JNIEXPORT void JNICALL
Java_org_cef_handler_CefClientHandler_N_1addMessageRouter(
    JNIEnv* env,
    jobject clientHandler,
    jobject jmessageRouter) {
  CefRefPtr<ClientHandler> client = GetCefFromJNIObject_sync<ClientHandler>(
      env, clientHandler, "CefClientHandler");
  if (!client.get())
    return;
  client->AddMessageRouter(env, jmessageRouter);
}

JNIEXPORT void JNICALL
Java_org_cef_handler_CefClientHandler_N_1removeContextMenuHandler(
    JNIEnv* env,
    jobject clientHandler,
    jobject contextMenuHandler) {
  // Use the concrete wrapper type (matching ClientHandler::GetHandler<T>'s
  // T on the SET side, via ScopedJNIObject<T>::GetOrCreateCefObject()) --
  // not the abstract CefContextMenuHandler interface. See
  // Thrameos/java-cef#22's follow-up: removeWindowHandler below already did
  // this correctly (SetCefForJNIObject_sync<WindowHandler>, not
  // <CefWindowHandler>); every other remove*Handler here didn't, an
  // apparent copy-paste inconsistency, not an intentional choice.
  SetCefForJNIObject_sync<ContextMenuHandler>(env, contextMenuHandler, nullptr,
                                            "CefContextMenuHandler");
}

JNIEXPORT void JNICALL
Java_org_cef_handler_CefClientHandler_N_1removeDialogHandler(
    JNIEnv* env,
    jobject clientHandler,
    jobject dialogHandler) {
  SetCefForJNIObject_sync<DialogHandler>(env, dialogHandler, nullptr,
                                       "CefDialogHandler");
}

JNIEXPORT void JNICALL
Java_org_cef_handler_CefClientHandler_N_1removeDisplayHandler(
    JNIEnv* env,
    jobject clientHandler,
    jobject displayHandler) {
  SetCefForJNIObject_sync<DisplayHandler>(env, displayHandler, nullptr,
                                        "CefDisplayHandler");
}

JNIEXPORT void JNICALL
Java_org_cef_handler_CefClientHandler_N_1removeDownloadHandler(
    JNIEnv* env,
    jobject clientHandler,
    jobject downloadHandler) {
  SetCefForJNIObject_sync<DownloadHandler>(env, downloadHandler, nullptr,
                                         "CefDownloadHandler");
}

JNIEXPORT void JNICALL
Java_org_cef_handler_CefClientHandler_N_1removeDragHandler(
    JNIEnv* env,
    jobject clientHandler,
    jobject dragHandler) {
  SetCefForJNIObject_sync<DragHandler>(env, dragHandler, nullptr,
                                     "CefDragHandler");
}

JNIEXPORT void JNICALL
Java_org_cef_handler_CefClientHandler_N_1removeFocusHandler(
    JNIEnv* env,
    jobject clientHandler,
    jobject focusHandler) {
  // See Thrameos/java-cef#22 follow-up (comment on removeContextMenuHandler
  // above): this exact function's crash (SIGSEGV in
  // SetCefForJNIObjectHelper::Release, confirmed live via hs_err during
  // ordinary browser teardown) is what surfaced this whole class of bug.
  SetCefForJNIObject_sync<FocusHandler>(env, focusHandler, nullptr,
                                      "CefFocusHandler");
}

JNIEXPORT void JNICALL
Java_org_cef_handler_CefClientHandler_N_1removeJSDialogHandler(
    JNIEnv* env,
    jobject clientHandler,
    jobject jsdialogHandler) {
  SetCefForJNIObject_sync<JSDialogHandler>(env, jsdialogHandler, nullptr,
                                         "CefJSDialogHandler");
}

JNIEXPORT void JNICALL
Java_org_cef_handler_CefClientHandler_N_1removeKeyboardHandler(
    JNIEnv* env,
    jobject clientHandler,
    jobject keyboardHandler) {
  SetCefForJNIObject_sync<KeyboardHandler>(env, keyboardHandler, nullptr,
                                         "CefKeyboardHandler");
}

JNIEXPORT void JNICALL
Java_org_cef_handler_CefClientHandler_N_1removeLifeSpanHandler(
    JNIEnv* env,
    jobject clientHandler,
    jobject lifeSpanHandler) {
  SetCefForJNIObject_sync<LifeSpanHandler>(env, lifeSpanHandler, nullptr,
                                         "CefLifeSpanHandler");
}

JNIEXPORT void JNICALL
Java_org_cef_handler_CefClientHandler_N_1removeLoadHandler(
    JNIEnv* env,
    jobject clientHandler,
    jobject loadHandler) {
  SetCefForJNIObject_sync<LoadHandler>(env, loadHandler, nullptr,
                                     "CefLoadHandler");
}

JNIEXPORT void JNICALL
Java_org_cef_handler_CefClientHandler_N_1removePrintHandler(
    JNIEnv* env,
    jobject clientHandler,
    jobject printHandler) {
  SetCefForJNIObject_sync<PrintHandler>(env, printHandler, nullptr,
                                      "CefPrintHandler");
}

JNIEXPORT void JNICALL
Java_org_cef_handler_CefClientHandler_N_1removeMessageRouter(
    JNIEnv* env,
    jobject clientHandler,
    jobject jmessageRouter) {
  CefRefPtr<ClientHandler> client = GetCefFromJNIObject_sync<ClientHandler>(
      env, clientHandler, "CefClientHandler");
  if (!client.get())
    return;
  client->RemoveMessageRouter(env, jmessageRouter);
}

JNIEXPORT void JNICALL
Java_org_cef_handler_CefClientHandler_N_1removeRenderHandler(
    JNIEnv* env,
    jobject clientHandler,
    jobject renderHandler) {
  SetCefForJNIObject_sync<RenderHandler>(env, renderHandler, nullptr,
                                       "CefRenderHandler");
}

JNIEXPORT void JNICALL
Java_org_cef_handler_CefClientHandler_N_1removeRequestHandler(
    JNIEnv* env,
    jobject clientHandler,
    jobject requestHandler) {
  SetCefForJNIObject_sync<RequestHandler>(env, requestHandler, nullptr,
                                        "CefRequestHandler");
}

JNIEXPORT void JNICALL
Java_org_cef_handler_CefClientHandler_N_1removeWindowHandler(
    JNIEnv* env,
    jobject clientHandler,
    jobject windowHandler) {
  SetCefForJNIObject_sync<WindowHandler>(env, windowHandler, nullptr,
                                    "CefWindowHandler");
}

JNIEXPORT void JNICALL
Java_org_cef_handler_CefClientHandler_N_1CefClientHandler_1DTOR(
    JNIEnv* env,
    jobject clientHandler) {
  // delete reference to the native client handler
  SetCefForJNIObject_sync<ClientHandler>(env, clientHandler, nullptr,
                                    "CefClientHandler");
}
