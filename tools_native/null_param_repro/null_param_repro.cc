// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.
//
// Standalone C++ diagnostic, NOT part of the JCEF product build -- modeled
// on tools_native/leak_probe. Minimum repro for issues #19/#20/#21's
// systemic class of bug: CEF's own ctocpp wrappers (request_ctocpp.cc,
// response_ctocpp.cc, post_data_element_ctocpp.cc) have a DCHECK(!x.empty())
// immediately before an `if (x.empty()) return;` safety net -- in a Debug/
// dcheck_always_on build, DCHECK aborts BEFORE that safety net ever runs.
// JCEF's own native/*_N.cpp marshals a null Java String to an empty
// CefString() with no guard at several call sites, reaching this DCHECK
// directly. Pure C++ against the same prebuilt libcef this repo downloads --
// no JNI, no JVM, no JUnit harness -- isolates CEF's own behavior from every
// other Debug-build crash this session found stacked on top of it in the
// real JUnit suite (see plan/roadmap.md).
//
// Pass one of the six operation names below as argv[1] to exercise exactly
// one call (this program crashes by design when it does, so each operation
// needs its own process): request-seturl, request-setmethod,
// request-setheader, request-set, response-setheader, postdata-settofile.
// With no argument, runs all six in-process, expecting each one to survive
// (i.e. this binary only makes sense to run per-operation via a wrapper
// script that captures each exit code separately).

#include <cstdlib>
#include <cstring>
#include <iostream>
#include <string>

#include "include/cef_app.h"
#include "include/cef_request.h"
#include "include/cef_response.h"

namespace {

void RunOp(const std::string& op) {
  // Unguarded variants: exactly what JCEF's native/*_N.cpp used to do
  // (marshal a null Java String straight to an empty CefString() with no
  // check) -- expected, by design, to crash with CEF's own Check failed:
  // !x.empty(), proving the underlying bug is real and precisely where
  // issues #19/#20/#21 said it was.
  if (op == "request-seturl") {
    CefRefPtr<CefRequest> request = CefRequest::Create();
    request->SetURL(CefString());
  } else if (op == "request-setmethod") {
    CefRefPtr<CefRequest> request = CefRequest::Create();
    request->SetMethod(CefString());
  } else if (op == "request-setheader") {
    CefRefPtr<CefRequest> request = CefRequest::Create();
    request->SetHeaderByName(CefString(), CefString("value"), true);
  } else if (op == "request-set") {
    CefRefPtr<CefRequest> request = CefRequest::Create();
    CefRequest::HeaderMap headerMap;
    request->Set(CefString(), CefString(), nullptr, headerMap);
  } else if (op == "response-setheader") {
    CefRefPtr<CefResponse> response = CefResponse::Create();
    response->SetHeaderByName(CefString(), CefString("value"), true);
  } else if (op == "postdata-settofile") {
    CefRefPtr<CefPostDataElement> element = CefPostDataElement::Create();
    element->SetToFile(CefString());

  // Guarded variants: the exact fix applied at each call site in
  // native/CefRequest_N.cpp, CefResponse_N.cpp, CefPostDataElement_N.cpp --
  // check .empty() first, skip the call entirely if so. Expected to reach
  // "SURVIVED" every time; if one of these ever crashes instead, the fix
  // itself (not just this repro) is wrong.
  } else if (op == "request-seturl-guarded") {
    CefRefPtr<CefRequest> request = CefRequest::Create();
    CefString url;
    if (!url.empty())
      request->SetURL(url);
  } else if (op == "request-setmethod-guarded") {
    CefRefPtr<CefRequest> request = CefRequest::Create();
    CefString method;
    if (!method.empty())
      request->SetMethod(method);
  } else if (op == "request-setheader-guarded") {
    CefRefPtr<CefRequest> request = CefRequest::Create();
    CefString name;
    if (!name.empty())
      request->SetHeaderByName(name, CefString("value"), true);
  } else if (op == "request-set-guarded") {
    CefRefPtr<CefRequest> request = CefRequest::Create();
    CefRequest::HeaderMap headerMap;
    CefString url, method;
    if (!(url.empty() || method.empty()))
      request->Set(url, method, nullptr, headerMap);
  } else if (op == "response-setheader-guarded") {
    CefRefPtr<CefResponse> response = CefResponse::Create();
    CefString name;
    if (!name.empty())
      response->SetHeaderByName(name, CefString("value"), true);
  } else if (op == "postdata-settofile-guarded") {
    CefRefPtr<CefPostDataElement> element = CefPostDataElement::Create();
    CefString fileName;
    if (!fileName.empty())
      element->SetToFile(fileName);
  } else {
    std::cerr << "Unknown op: " << op << "\n";
    exit(2);
  }
}

}  // namespace

int main(int argc, char* argv[]) {
  CefMainArgs main_args(argc, argv);

  int exit_code = CefExecuteProcess(main_args, nullptr, nullptr);
  if (exit_code >= 0) {
    return exit_code;
  }

  // NOT argv[1]: CEF's own command-line parsing (CefMainArgs/CommandLine::
  // Init, needed for the subprocess re-exec check above) mutates argv in
  // ways that make a custom trailing arg unreliable to read afterward --
  // confirmed directly (argv[1] came back as a mangled fragment of the
  // executable's own path). An env var sidesteps CEF's argv handling
  // entirely.
  const char* op_cstr = getenv("NULL_PARAM_REPRO_OP");
  if (!op_cstr) {
    std::cerr << "Usage: NULL_PARAM_REPRO_OP=<request-seturl|"
                 "request-setmethod|request-setheader|request-set|"
                 "response-setheader|postdata-settofile> " << argv[0] << "\n";
    return 2;
  }
  std::string op = op_cstr;

  CefSettings settings;
  settings.no_sandbox = true;
  settings.multi_threaded_message_loop = true;
  settings.windowless_rendering_enabled = false;

  if (!CefInitialize(main_args, settings, nullptr, nullptr)) {
    std::cerr << "CefInitialize failed, exit code " << CefGetExitCode()
              << "\n";
    return 1;
  }

  RunOp(op);

  std::cout << "SURVIVED: " << op << " did not crash\n";

  CefShutdown();
  return 0;
}
