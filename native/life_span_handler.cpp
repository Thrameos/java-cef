// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

#include "life_span_handler.h"

#include "client_handler.h"
#include "jcef_trace.h"
#include "jni_util.h"
#include "util.h"

LifeSpanHandler::LifeSpanHandler(JNIEnv* env, jobject handler)
    : handle_(env, handler) {}

// TODO(JCEF): Expose all parameters.
bool LifeSpanHandler::OnBeforePopup(CefRefPtr<CefBrowser> browser,
                                    CefRefPtr<CefFrame> frame,
                                    int popup_id,
                                    const CefString& target_url,
                                    const CefString& target_frame_name,
                                    WindowOpenDisposition target_disposition,
                                    bool user_gesture,
                                    const CefPopupFeatures& popupFeatures,
                                    CefWindowInfo& windowInfo,
                                    CefRefPtr<CefClient>& client,
                                    CefBrowserSettings& settings,
                                    CefRefPtr<CefDictionaryValue>& extra_info,
                                    bool* no_javascript_access) {
  JCEF_TRACE("LifeSpanHandler::OnBeforePopup() ENTER browser_id=%d osr=%d",
             browser->GetIdentifier(),
             browser->GetHost()->IsWindowRenderingDisabled());
  if (browser->GetHost()->IsWindowRenderingDisabled()) {
    // Cancel popups in off-screen rendering mode -- see CefLifeSpanPopupTest's
    // @Disabled note: this is why OnBeforePopup's JNI dispatch below is
    // unreachable for every OSR browser, unconditionally.
    JCEF_TRACE(
        "LifeSpanHandler::OnBeforePopup() EXIT early (OSR cancels "
        "popups, never reaching Java)");
    return true;
  }

  ScopedJNIEnv env;
  if (!env)
    return false;

  ScopedJNIBrowser jbrowser(env, browser);
  ScopedJNIFrame jframe(env, frame);
  jframe.SetTemporary();
  ScopedJNIString jtargetUrl(env, target_url);
  ScopedJNIString jtargetFrameName(env, target_frame_name);
  jboolean jreturn = JNI_FALSE;

  JNI_CALL_METHOD(env, handle_, "onBeforePopup",
                  "(Lorg/cef/browser/CefBrowser;Lorg/cef/browser/"
                  "CefFrame;Ljava/lang/String;Ljava/lang/String;)Z",
                  Boolean, jreturn, jbrowser.get(), jframe.get(),
                  jtargetUrl.get(), jtargetFrameName.get());

  return (jreturn != JNI_FALSE);
}

void LifeSpanHandler::OnAfterCreated(CefRefPtr<CefBrowser> browser) {
  JCEF_TRACE("LifeSpanHandler::OnAfterCreated() ENTER browser_id=%d",
             browser->GetIdentifier());
  ScopedJNIEnv env;
  if (!env || jbrowsers_.empty())
    return;

  util::AddCefBrowser(browser);

  jobject jbrowser = jbrowsers_.front();
  jbrowsers_.pop_front();

  CefRefPtr<ClientHandler> client =
      (ClientHandler*)browser->GetHost()->GetClient().get();
  client->OnAfterCreated();

  // Add a reference to |browser| that will be released in
  // LifeSpanHandler::OnBeforeClose.
  if (SetCefForJNIObject_sync(env, jbrowser, browser.get(), "CefBrowser")) {
    JNI_CALL_VOID_METHOD(env, handle_, "onAfterCreated",
                         "(Lorg/cef/browser/CefBrowser;)V", jbrowser);
  }

  // Release the global ref added in CefBrowser_N::create.
  env->DeleteGlobalRef(jbrowser);
}

bool LifeSpanHandler::DoClose(CefRefPtr<CefBrowser> browser) {
  JCEF_TRACE("LifeSpanHandler::DoClose() ENTER browser_id=%d",
             browser->GetIdentifier());
  ScopedJNIEnv env;
  if (!env)
    return false;

  ScopedJNIBrowser jbrowser(env, browser);
  jboolean jreturn = JNI_FALSE;

  JNI_CALL_METHOD(env, handle_, "doClose", "(Lorg/cef/browser/CefBrowser;)Z",
                  Boolean, jreturn, jbrowser.get());

  JCEF_TRACE("LifeSpanHandler::DoClose() EXIT browser_id=%d returning %d",
             browser->GetIdentifier(), jreturn != JNI_FALSE);
  return (jreturn != JNI_FALSE);
}

void LifeSpanHandler::OnBeforeClose(CefRefPtr<CefBrowser> browser) {
  JCEF_TRACE("LifeSpanHandler::OnBeforeClose() ENTER browser_id=%d",
             browser->GetIdentifier());
  REQUIRE_UI_THREAD();
  ScopedJNIEnv env;
  if (!env)
    return;

  ScopedJNIBrowser jbrowser(env, browser);

  JNI_CALL_VOID_METHOD(env, handle_, "onBeforeClose",
                       "(Lorg/cef/browser/CefBrowser;)V", jbrowser.get());

  // Clear the browser pointer member of the Java object. This will
  // release the browser reference that was added in
  // LifeSpanHandler::OnAfterCreated.
  SetCefForJNIObject_sync<CefBrowser>(env, jbrowser, nullptr, "CefBrowser");

  CefRefPtr<ClientHandler> client =
      (ClientHandler*)browser->GetHost()->GetClient().get();
  JCEF_TRACE(
      "LifeSpanHandler::OnBeforeClose() calling client->OnBeforeClose()");
  client->OnBeforeClose(browser);
  JCEF_TRACE("LifeSpanHandler::OnBeforeClose() EXIT browser_id=%d",
             browser->GetIdentifier());
}

void LifeSpanHandler::OnAfterParentChanged(CefRefPtr<CefBrowser> browser) {
  JCEF_TRACE("LifeSpanHandler::OnAfterParentChanged() ENTER browser_id=%d",
             browser->GetIdentifier());
  REQUIRE_UI_THREAD();
  ScopedJNIEnv env;
  if (!env)
    return;

  ScopedJNIBrowser jbrowser(env, browser);

  JNI_CALL_VOID_METHOD(env, handle_, "onAfterParentChanged",
                       "(Lorg/cef/browser/CefBrowser;)V", jbrowser.get());
}

void LifeSpanHandler::registerJBrowser(jobject browser) {
  jbrowsers_.push_back(browser);
}

void LifeSpanHandler::unregisterJBrowser(jobject browser) {
  jbrowsers_.remove(browser);
}
