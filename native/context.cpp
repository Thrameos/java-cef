// Copyright (c) 2016 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

#include "context.h"

#include "include/cef_app.h"

#include "client_app.h"
#include "jcef_trace.h"
#include "jni_scoped_helpers.h"
#include "jni_util.h"

#if defined(OS_MACOSX)
#include "util_mac.h"
#endif

namespace {

Context* g_context = nullptr;

CefSettings GetJNISettings(JNIEnv* env, jobject obj) {
  CefString tmp;
  CefSettings settings;

#if defined(OS_POSIX) && !defined(OS_ANDROID)
  settings.disable_signal_handlers = true;
#endif

  if (!obj)
    return settings;

  ScopedJNIClass cls(env, "org/cef/CefSettings");
  if (!cls)
    return settings;

  if (GetJNIFieldString(env, cls, obj, "browser_subprocess_path", &tmp) &&
      !tmp.empty()) {
    CefString(&settings.browser_subprocess_path) = tmp;
    tmp.clear();
  }
  GetJNIFieldBoolean(env, cls, obj, "windowless_rendering_enabled",
                     &settings.windowless_rendering_enabled);
  GetJNIFieldBoolean(env, cls, obj, "command_line_args_disabled",
                     &settings.command_line_args_disabled);
  if (GetJNIFieldString(env, cls, obj, "cache_path", &tmp) && !tmp.empty()) {
    CefString(&settings.cache_path) = tmp;
    tmp.clear();
  }
  if (GetJNIFieldString(env, cls, obj, "root_cache_path", &tmp) &&
      !tmp.empty()) {
    CefString(&settings.root_cache_path) = tmp;
    tmp.clear();
  }
  GetJNIFieldBoolean(env, cls, obj, "persist_session_cookies",
                     &settings.persist_session_cookies);
  if (GetJNIFieldString(env, cls, obj, "user_agent", &tmp) && !tmp.empty()) {
    CefString(&settings.user_agent) = tmp;
    tmp.clear();
  }
  if (GetJNIFieldString(env, cls, obj, "user_agent_product", &tmp) &&
      !tmp.empty()) {
    CefString(&settings.user_agent_product) = tmp;
    tmp.clear();
  }
  if (GetJNIFieldString(env, cls, obj, "locale", &tmp) && !tmp.empty()) {
    CefString(&settings.locale) = tmp;
    tmp.clear();
  }
  if (GetJNIFieldString(env, cls, obj, "log_file", &tmp) && !tmp.empty()) {
    CefString(&settings.log_file) = tmp;
    tmp.clear();
  }
  jobject obj_sev = nullptr;
  if (GetJNIFieldObject(env, cls, obj, "log_severity", &obj_sev,
                        "Lorg/cef/CefSettings$LogSeverity;")) {
    ScopedJNIObjectLocal severity(env, obj_sev);
    if (IsJNIEnumValue(env, severity, "org/cef/CefSettings$LogSeverity",
                       "LOGSEVERITY_VERBOSE")) {
      settings.log_severity = LOGSEVERITY_VERBOSE;
    } else if (IsJNIEnumValue(env, severity, "org/cef/CefSettings$LogSeverity",
                              "LOGSEVERITY_INFO")) {
      settings.log_severity = LOGSEVERITY_INFO;
    } else if (IsJNIEnumValue(env, severity, "org/cef/CefSettings$LogSeverity",
                              "LOGSEVERITY_WARNING")) {
      settings.log_severity = LOGSEVERITY_WARNING;
    } else if (IsJNIEnumValue(env, severity, "org/cef/CefSettings$LogSeverity",
                              "LOGSEVERITY_ERROR")) {
      settings.log_severity = LOGSEVERITY_ERROR;
    } else if (IsJNIEnumValue(env, severity, "org/cef/CefSettings$LogSeverity",
                              "LOGSEVERITY_DISABLE")) {
      settings.log_severity = LOGSEVERITY_DISABLE;
    } else {
      settings.log_severity = LOGSEVERITY_DEFAULT;
    }
  }
  if (GetJNIFieldString(env, cls, obj, "javascript_flags", &tmp) &&
      !tmp.empty()) {
    CefString(&settings.javascript_flags) = tmp;
    tmp.clear();
  }
  if (GetJNIFieldString(env, cls, obj, "resources_dir_path", &tmp) &&
      !tmp.empty()) {
    CefString(&settings.resources_dir_path) = tmp;
    tmp.clear();
  }
  if (GetJNIFieldString(env, cls, obj, "locales_dir_path", &tmp) &&
      !tmp.empty()) {
    CefString(&settings.locales_dir_path) = tmp;
    tmp.clear();
  }
  GetJNIFieldInt(env, cls, obj, "remote_debugging_port",
                 &settings.remote_debugging_port);
  if (GetJNIFieldString(env, cls, obj, "chrome_policy_id", &tmp) &&
      !tmp.empty()) {
    CefString(&settings.chrome_policy_id) = tmp;
    tmp.clear();
  }
  GetJNIFieldInt(env, cls, obj, "uncaught_exception_stack_size",
                 &settings.uncaught_exception_stack_size);
  jobject obj_col = nullptr;
  if (GetJNIFieldObject(env, cls, obj, "background_color", &obj_col,
                        "Lorg/cef/CefSettings$ColorType;")) {
    ScopedJNIObjectLocal color_type(env, obj_col);
    jlong jcolor = 0;
    JNI_CALL_METHOD(env, color_type, "getColor", "()J", Long, jcolor);
    settings.background_color = (cef_color_t)jcolor;
  }
  if (GetJNIFieldString(env, cls, obj, "cookieable_schemes_list", &tmp) &&
      !tmp.empty()) {
    CefString(&settings.cookieable_schemes_list) = tmp;
    tmp.clear();
  }
  GetJNIFieldBoolean(env, cls, obj, "cookieable_schemes_exclude_defaults",
                     &settings.cookieable_schemes_exclude_defaults);

  return settings;
}

}  // namespace

// static
void Context::Create() {
  JCEF_TRACE("Context::Create() static");
  new Context();
}

// static
void Context::Destroy() {
  JCEF_TRACE("Context::Destroy() static ENTER");
  DCHECK(g_context);
  if (g_context)
    delete g_context;
  JCEF_TRACE("Context::Destroy() static EXIT");
}

// static
Context* Context::GetInstance() {
  return g_context;
}

bool Context::PreInitialize(JNIEnv* env, jobject c) {
  JCEF_TRACE("PreInitialize() ENTER");
  DCHECK(thread_checker_.CalledOnValidThread());

  JavaVM* jvm;
  jint rs = env->GetJavaVM(&jvm);
  DCHECK_EQ(rs, JNI_OK);
  if (rs != JNI_OK)
    return JNI_FALSE;
  SetJVM(jvm);

  ScopedJNIClass javaClass(env, env->GetObjectClass(c));
  ScopedJNIObjectResult javaClassLoader(env);
  JNI_CALL_METHOD(env, javaClass, "getClassLoader", "()Ljava/lang/ClassLoader;",
                  Object, javaClassLoader);
  ASSERT(javaClassLoader);
  if (!javaClassLoader)
    return false;
  SetJavaClassLoader(env, javaClassLoader);

  JCEF_TRACE("PreInitialize() EXIT ok");
  return true;
}

bool Context::Initialize(JNIEnv* env,
                         jobject c,
                         jobject appHandler,
                         jobject jsettings) {
  JCEF_TRACE("Initialize() ENTER");
  DCHECK(thread_checker_.CalledOnValidThread());

#if defined(OS_WIN)
  CefMainArgs main_args(::GetModuleHandle(nullptr));
#else
  CefMainArgs main_args(0, nullptr);
#endif

  CefSettings settings = GetJNISettings(env, jsettings);

  // Sandbox is not supported because:
  // - Use of a separate sub-process executable on Windows.
  // - Use of a temporary file to communicate custom schemes to the
  //   renderer process.
  settings.no_sandbox = true;

#if defined(OS_WIN) || defined(OS_LINUX)
  // Use external message pump with OSR.
  external_message_pump_ = !!settings.windowless_rendering_enabled;
  if (!external_message_pump_) {
    // Windowed rendering on Windows requires multi-threaded message loop,
    // otherwise something eats the messages required by Java and the Java
    // window becomes unresponsive.
    //
    // Actually the same appears to be true for Linux, which is why we also
    // need multithreaded message loops there. Note that however, on Linux
    // it is more difficult to get this to work: it is necessary for the first
    // call to Xlib to be a call to XInitThreads! Since Java itself calls
    // Xlib when it initializes the first window, an application must make
    // sure to invoke this method before any other Xlib functions are called
    // - including by the Java runtime itself, which makes this feat a little
    // tricky. The CefApp class exposes a static method for this purpose,
    // initXlibForMultithreading(), but the host application must load the
    // jcef native lib by itself in order to use it, and it must invoke it
    // VERY early, ideally at the beginning of the main method.
    // Another neat trick to get this done is to create a special native lib
    // just for this purpose like described in this StackOverflow thread:
    // https://stackoverflow.com/questions/24559368
    settings.multi_threaded_message_loop = true;
  }
#endif

  // Use CefAppHandler.onScheduleMessagePumpWork to schedule calls to
  // DoMessageLoopWork.
  settings.external_message_pump = external_message_pump_;

  JCEF_TRACE("Initialize() calling CefInitialize(), external_message_pump_=%d",
            external_message_pump_ ? 1 : 0);
  CefRefPtr<ClientApp> client_app(
      new ClientApp(CefString(&settings.cache_path), env, appHandler));
  bool res = false;

#if defined(OS_MACOSX)
  res = util_mac::CefInitializeOnMainThread(main_args, settings,
                                            client_app.get());
#else
  res = CefInitialize(main_args, settings, client_app.get(), nullptr);
#endif

  JCEF_TRACE("Initialize() EXIT res=%d", res ? 1 : 0);
  return res;
}

void Context::OnContextInitialized() {
  JCEF_TRACE("OnContextInitialized() ENTER");
  REQUIRE_UI_THREAD();
  temp_window_.reset(new TempWindow());
  JCEF_TRACE("OnContextInitialized() EXIT");
}

void Context::DoMessageLoopWork() {
  DCHECK(thread_checker_.CalledOnValidThread());

  static long long call_count = 0;
  ++call_count;
  if (call_count <= 5 || call_count % 100 == 0) {
    JCEF_TRACE("DoMessageLoopWork() call #%lld", call_count);
  }

#if defined(OS_MACOSX)
  util_mac::CefDoMessageLoopWorkOnMainThread();
#else
  CefDoMessageLoopWork();
#endif
}

void Context::Shutdown() {
  JCEF_TRACE("Shutdown() ENTER");
  DCHECK(thread_checker_.CalledOnValidThread());

  // Make Shutdown() idempotent: guard against being invoked more than once
  // (e.g. a race between two paths that each believe they're the last
  // client/browser being disposed -- see CefApp.java's shutdown(), which is
  // scheduled via SwingUtilities.invokeLater() and so is not atomic with
  // the state check that triggers it). A second CefShutdown() call is not
  // safe to make. See Thrameos/java-cef#22/#23.
  static bool shutdown_called = false;
  if (shutdown_called) {
    JCEF_TRACE("Shutdown() EXIT early -- already called once");
    return;
  }
  shutdown_called = true;

  // Clear scheme handler factories on shutdown to avoid refcount DCHECK.
  CefClearSchemeHandlerFactories();

  ClientApp::eraseTempFiles();
#if defined(OS_MACOSX)
  util_mac::CefShutdownOnMainThread();
#else
  // Pump CefDoMessageLoopWork a few times before shutting down.
  //
  // RULED OUT (2026-08-30, root-causing issue #4/#23's all_.empty() DCHECK):
  // hypothesized this fixed, undelayed 10-iteration burst might not give
  // async browser-context teardown enough real wall-clock time to finish
  // before CefShutdown() tears down process-wide statics (ImplManager's all_
  // vector). Tested directly with JCEF_TRACE instrumentation: a 200-iteration,
  // 5ms-delayed pump (~1s total, 100x the original budget) ran to full,
  // logged completion, and CefShutdown() still crashed on the very next line
  // with the identical DCHECK. Pump budget is not the cause -- reverted to
  // the original 10x. The crash happens inside CefShutdown() itself; the
  // leading suspect now is a JVM-vs-CEF child-process-reaping race (a
  // `waitpid(PID): No child processes` ECHILD line from CEF's own
  // base/process/kill_posix.cc immediately precedes this specific DCHECK in
  // every captured repro, but not the other unrelated crash signatures seen
  // this session) -- not yet confirmed. See plan/findings.md.
  // GH #10 derisking probe (2026-09-03, not yet acted on): log JCEF's own
  // live-CEF-object count at both ends of this pump -- see the "GH #10:
  // phased/quiescent shutdown" plan and SetCefForJNIObjectHelper's own
  // comment. Purely observational; no behavior change here.
  JCEF_TRACE("Shutdown() live_object_count=%d before pump",
            SetCefForJNIObjectHelper::GetLiveObjectCount());
  if (external_message_pump_) {
    for (int i = 0; i < 10; ++i)
      CefDoMessageLoopWork();
  }

  temp_window_.reset(nullptr);

  JCEF_TRACE("Shutdown() live_object_count=%d before CefShutdown()",
            SetCefForJNIObjectHelper::GetLiveObjectCount());
  JCEF_TRACE("Shutdown() calling CefShutdown()...");
  CefShutdown();
  JCEF_TRACE("Shutdown() CefShutdown() returned");
#endif
}

Context::Context() : external_message_pump_(true) {
  JCEF_TRACE("Context::Context() (constructor)");
  DCHECK(!g_context);
  g_context = this;

#if defined(OS_MACOSX)
  // On macOS we create this object very early to allow LibraryLoader
  // assignment. However, we still want the PreInitialize() call to determine
  // thread ownership.
  thread_checker_.DetachFromThread();
#endif
}

Context::~Context() {
  JCEF_TRACE("Context::~Context() (destructor) ENTER");
  DCHECK(thread_checker_.CalledOnValidThread());
  g_context = nullptr;

#if defined(OS_MACOSX)
  cef_unload_library();
#endif
  JCEF_TRACE("Context::~Context() (destructor) EXIT");
}
