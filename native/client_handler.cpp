// Copyright (c) 2013 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

#include "client_handler.h"
#include "jcef_trace.h"

#include <stdio.h>
#include <algorithm>
#include <set>
#include <sstream>
#include <string>
#include <vector>

#include "browser_process_handler.h"
#include "context_menu_handler.h"
#include "dialog_handler.h"
#include "display_handler.h"
#include "download_handler.h"
#include "drag_handler.h"
#include "find_handler.h"
#include "focus_handler.h"
#include "frame_handler.h"
#include "jsdialog_handler.h"
#include "keyboard_handler.h"
#include "life_span_handler.h"
#include "load_handler.h"
#include "message_router_handler.h"
#include "permission_handler.h"
#include "print_handler.h"
#include "render_handler.h"
#include "request_handler.h"

#include "include/cef_browser.h"
#include "include/cef_frame.h"
#include "include/cef_parser.h"
#include "include/cef_path_util.h"
#include "include/cef_process_util.h"
#include "include/cef_trace.h"
#include "include/wrapper/cef_stream_resource_handler.h"
#include "jni_util.h"
#include "util.h"

namespace {

CefRefPtr<CefMessageRouter> GetMessageRouter(JNIEnv* env,
                                             jobject jmessageRouter) {
  ScopedJNIMessageRouter messageRouter(env);
  messageRouter.SetHandle(jmessageRouter, false /* should_delete */);
  return messageRouter.GetCefObject();
}

CefMessageRouterConfig GetMessageRouterConfig(JNIEnv* env,
                                              jobject jmessageRouter) {
  ScopedJNIObjectResult jrouterConfig(env);
  JNI_CALL_METHOD(env, jmessageRouter, "getMessageRouterConfig",
                  "()Lorg/cef/browser/CefMessageRouter$CefMessageRouterConfig;",
                  Object, jrouterConfig);
  return GetJNIMessageRouterConfig(env, jrouterConfig);
}

}  // namespace

ClientHandler::ClientHandler(JNIEnv* env, jobject handler)
    : handle_(env, handler) {}

template <class T>
CefRefPtr<T> ClientHandler::GetHandler(const char* class_name) {
  JCEF_TRACE("ClientHandler::GetHandler() ENTER");
  std::string methodName, className, methodSig;

  std::stringstream ss;
  ss << "get" << class_name;
  methodName = ss.str();

  ss.str(std::string());
  ss << "Cef" << class_name;
  className = ss.str();

  ss.str(std::string());
  ss << "()Lorg/cef/handler/" << className << ";";
  methodSig = ss.str();

  ScopedJNIEnv env;
  if (!env)
    return nullptr;

  CefRefPtr<T> result = nullptr;
  ScopedJNIObjectResult jresult(env);
  JNI_CALL_METHOD(env, handle_, methodName.c_str(), methodSig.c_str(), Object,
                  jresult);
  if (jresult) {
    ScopedJNIObject<T> jhandler(env, jresult.Release(),
                                true /* should_delete */, className.c_str());
    result = jhandler.GetOrCreateCefObject();
  }

  return result;
}

CefRefPtr<CefContextMenuHandler> ClientHandler::GetContextMenuHandler() {
  JCEF_TRACE("ClientHandler::GetContextMenuHandler() ENTER");
  return GetHandler<ContextMenuHandler>("ContextMenuHandler");
}

CefRefPtr<CefDialogHandler> ClientHandler::GetDialogHandler() {
  JCEF_TRACE("ClientHandler::GetDialogHandler() ENTER");
  return GetHandler<DialogHandler>("DialogHandler");
}

CefRefPtr<CefDisplayHandler> ClientHandler::GetDisplayHandler() {
  JCEF_TRACE("ClientHandler::GetDisplayHandler() ENTER");
  return GetHandler<DisplayHandler>("DisplayHandler");
}

CefRefPtr<CefDownloadHandler> ClientHandler::GetDownloadHandler() {
  JCEF_TRACE("ClientHandler::GetDownloadHandler() ENTER");
  return GetHandler<DownloadHandler>("DownloadHandler");
}

CefRefPtr<CefDragHandler> ClientHandler::GetDragHandler() {
  JCEF_TRACE("ClientHandler::GetDragHandler() ENTER");
  return GetHandler<DragHandler>("DragHandler");
}

CefRefPtr<CefFindHandler> ClientHandler::GetFindHandler() {
  JCEF_TRACE("ClientHandler::GetFindHandler() ENTER");
  return GetHandler<FindHandler>("FindHandler");
}

CefRefPtr<CefFocusHandler> ClientHandler::GetFocusHandler() {
  JCEF_TRACE("ClientHandler::GetFocusHandler() ENTER");
  return GetHandler<FocusHandler>("FocusHandler");
}

CefRefPtr<CefFrameHandler> ClientHandler::GetFrameHandler() {
  JCEF_TRACE("ClientHandler::GetFrameHandler() ENTER");
  return GetHandler<FrameHandler>("FrameHandler");
}

CefRefPtr<CefJSDialogHandler> ClientHandler::GetJSDialogHandler() {
  JCEF_TRACE("ClientHandler::GetJSDialogHandler() ENTER");
  return GetHandler<JSDialogHandler>("JSDialogHandler");
}

CefRefPtr<CefKeyboardHandler> ClientHandler::GetKeyboardHandler() {
  JCEF_TRACE("ClientHandler::GetKeyboardHandler() ENTER");
  return GetHandler<KeyboardHandler>("KeyboardHandler");
}

CefRefPtr<CefLifeSpanHandler> ClientHandler::GetLifeSpanHandler() {
  JCEF_TRACE("ClientHandler::GetLifeSpanHandler() ENTER");
  return GetHandler<LifeSpanHandler>("LifeSpanHandler");
}

CefRefPtr<CefLoadHandler> ClientHandler::GetLoadHandler() {
  JCEF_TRACE("ClientHandler::GetLoadHandler() ENTER");
  return GetHandler<LoadHandler>("LoadHandler");
}

CefRefPtr<CefPermissionHandler> ClientHandler::GetPermissionHandler() {
  JCEF_TRACE("ClientHandler::GetPermissionHandler() ENTER");
  return GetHandler<PermissionHandler>("PermissionHandler");
}

CefRefPtr<CefPrintHandler> ClientHandler::GetPrintHandler() {
  JCEF_TRACE("ClientHandler::GetPrintHandler() ENTER");
  return GetHandler<PrintHandler>("PrintHandler");
}

CefRefPtr<CefRenderHandler> ClientHandler::GetRenderHandler() {
  JCEF_TRACE("ClientHandler::GetRenderHandler() ENTER");
  return GetHandler<RenderHandler>("RenderHandler");
}

CefRefPtr<CefRequestHandler> ClientHandler::GetRequestHandler() {
  JCEF_TRACE("ClientHandler::GetRequestHandler() ENTER");
  return GetHandler<RequestHandler>("RequestHandler");
}

bool ClientHandler::OnProcessMessageReceived(
    CefRefPtr<CefBrowser> browser,
    CefRefPtr<CefFrame> frame,
    CefProcessId source_process,
    CefRefPtr<CefProcessMessage> message) {
  JCEF_TRACE("ClientHandler::OnProcessMessageReceived() ENTER");
  bool handled = false;

  // Iterate on a copy of |message_routers_| to avoid re-entrancy of
  // |message_router_lock_| if the client CefMessageRouterHandler impl
  // calls CefClientHandler.addMessageRouter/removeMessageRouter.
  MessageRouterSet message_routers;
  {
    base::AutoLock lock_scope(message_router_lock_);
    message_routers = message_routers_;
  }

  for (auto& router : message_routers) {
    handled = router->OnProcessMessageReceived(browser, frame, source_process,
                                               message);
    if (handled)
      break;
  }
  return handled;
}

CefRefPtr<WindowHandler> ClientHandler::GetWindowHandler() {
  JCEF_TRACE("ClientHandler::GetWindowHandler() ENTER");
  return GetHandler<WindowHandler>("WindowHandler");
}

void ClientHandler::AddMessageRouter(JNIEnv* env, jobject jmessageRouter) {
  JCEF_TRACE("ClientHandler::AddMessageRouter() ENTER");
  CefRefPtr<CefMessageRouter> router = GetMessageRouter(env, jmessageRouter);
  if (!router)
    return;

  CefMessageRouterConfig config = GetMessageRouterConfig(env, jmessageRouter);

  // 1) Add CefMessageRouterBrowserSide into the list.
  {
    base::AutoLock lock_scope(message_router_lock_);
    message_routers_.insert(router);
  }

  // 2) Update CefApp for new render-processes.
  BrowserProcessHandler::AddMessageRouterConfig(config);

  // 3) Update running render-processes.
  BrowserSet allBrowsers = GetAllBrowsers(env);
  if (allBrowsers.empty())
    return;

  CefRefPtr<CefProcessMessage> message =
      CefProcessMessage::Create("AddMessageRouter");
  CefRefPtr<CefListValue> args = message->GetArgumentList();
  args->SetString(0, config.js_query_function);
  args->SetString(1, config.js_cancel_function);

  BrowserSet::const_iterator it = allBrowsers.begin();
  for (; it != allBrowsers.end(); ++it) {
    (*it)->GetMainFrame()->SendProcessMessage(PID_RENDERER, message);
  }
}

void ClientHandler::RemoveMessageRouter(JNIEnv* env, jobject jmessageRouter) {
  JCEF_TRACE("ClientHandler::RemoveMessageRouter() ENTER");
  CefRefPtr<CefMessageRouter> router = GetMessageRouter(env, jmessageRouter);
  if (!router)
    return;

  CefMessageRouterConfig config = GetMessageRouterConfig(env, jmessageRouter);

  // Cancel any outstanding queries before dropping the router. A
  // *persistent* query's callback holds a CefRefPtr back to the router
  // (see CefMessageRouterBrowserSideImpl::CallbackImpl::router_) that is
  // only released by CancelPending()/OnBeforeClose()/OnRenderProcessTerminated()
  // -- if the router is removed while such a query is still outstanding (as
  // opposed to the browser actually closing, which OnBeforeClose() above
  // already handles), nothing else ever calls it, leaking the router (and
  // transitively the browser context it keeps alive) for the life of the
  // process. See issue #4/#23.
  router->CancelPending(nullptr, nullptr);

  // 1) Remove CefMessageRouterBrowserSide from the list.
  {
    base::AutoLock lock_scope(message_router_lock_);
    message_routers_.erase(router);
  }

  // 2) Update CefApp.
  BrowserProcessHandler::RemoveMessageRouterConfig(config);

  // 3) Update running render-processes.
  BrowserSet allBrowsers = GetAllBrowsers(env);
  if (allBrowsers.empty())
    return;

  CefRefPtr<CefProcessMessage> message =
      CefProcessMessage::Create("RemoveMessageRouter");
  CefRefPtr<CefListValue> args = message->GetArgumentList();
  args->SetString(0, config.js_query_function);
  args->SetString(1, config.js_cancel_function);

  BrowserSet::const_iterator it = allBrowsers.begin();
  for (; it != allBrowsers.end(); ++it) {
    (*it)->GetMainFrame()->SendProcessMessage(PID_RENDERER, message);
  }
}

void ClientHandler::OnAfterCreated() {
  JCEF_TRACE("ClientHandler::OnAfterCreated() ENTER");
}

void ClientHandler::OnBeforeClose(CefRefPtr<CefBrowser> browser) {
  JCEF_TRACE("ClientHandler::OnBeforeClose() ENTER");
  REQUIRE_UI_THREAD();

  base::AutoLock lock_scope(message_router_lock_);
  for (auto& router : message_routers_) {
    router->OnBeforeClose(browser);
  }
}

void ClientHandler::OnBeforeBrowse(CefRefPtr<CefBrowser> browser,
                                   CefRefPtr<CefFrame> frame) {
  JCEF_TRACE("ClientHandler::OnBeforeBrowse() ENTER");
  REQUIRE_UI_THREAD();

  base::AutoLock lock_scope(message_router_lock_);
  for (auto& router : message_routers_) {
    router->OnBeforeBrowse(browser, frame);
  }
}

void ClientHandler::OnRenderProcessTerminated(CefRefPtr<CefBrowser> browser) {
  JCEF_TRACE("ClientHandler::OnRenderProcessTerminated() ENTER");
  REQUIRE_UI_THREAD();

  base::AutoLock lock_scope(message_router_lock_);
  for (auto& router : message_routers_) {
    router->OnRenderProcessTerminated(browser);
  }
}

jobject ClientHandler::getBrowser(JNIEnv* env, CefRefPtr<CefBrowser> browser) {
  JCEF_TRACE("ClientHandler::getBrowser() ENTER");
  jobject jbrowser = nullptr;
  JNI_CALL_METHOD(env, handle_, "getBrowser", "(I)Lorg/cef/browser/CefBrowser;",
                  Object, jbrowser, browser->GetIdentifier());
  return jbrowser;
}

ClientHandler::BrowserSet ClientHandler::GetAllBrowsers(JNIEnv* env) {
  JCEF_TRACE("ClientHandler::GetAllBrowsers() ENTER");
  BrowserSet result;

  jobject jbrowsers = nullptr;
  JNI_CALL_METHOD(env, handle_, "getAllBrowser", "()[Ljava/lang/Object;",
                  Object, jbrowsers);
  if (!jbrowsers)
    return result;

  jobjectArray jbrowserArray = (jobjectArray)jbrowsers;
  jint length = env->GetArrayLength(jbrowserArray);
  for (int i = 0; i < length; ++i) {
    jobject jbrowser = env->GetObjectArrayElement(jbrowserArray, i);
    if (!jbrowser)
      continue;

    ScopedJNIBrowser sbrowser(env);
    sbrowser.SetHandle(jbrowser, true /* should_delete */);
    CefRefPtr<CefBrowser> browser = sbrowser.GetCefObject();
    if (!browser)
      continue;

    result.insert(browser);
  }
  env->DeleteLocalRef(jbrowserArray);

  return result;
}
