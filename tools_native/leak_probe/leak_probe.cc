// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

// Standalone C++ diagnostic, NOT part of the JCEF product build -- see
// plan/LeakCheckerPort.md's 2026-08-30 status. Purpose: bifurcate the
// leak-sweep findings in java/tests/junittests/LeakTargets.java between (1)
// a genuine leak in CEF's own C++ implementation (in which case JCEF's C++
// wrapper code, or upstream CEF itself, needs an RAII fix) and (2) a
// JNI-boundary problem specific to JCEF's dispose() plumbing (in which case
// a JPype-JPJavaFrame-style RAII fix on the Java/JNI side is the right
// shape). Runs the identical create()/dispose() churn as the JCEF
// LeakTargets, but in pure C++ against the same prebuilt libcef this repo
// already downloads -- no JNI, no Java, no JVM heap -- so any RSS growth
// here can only be CEF's own C++ side.
//
// Modeled on tests/cefsimple/cefsimple_linux.cc for CEF init/shutdown, and
// on tests/ceftests/request_unittest.cc + response_unittest.cc for calling
// convention -- those call CefRequest::Create()/CefResponse::Create()
// directly with no CEF_REQUIRE_UI_THREAD wrapping, confirming these really
// are plain non-thread-affine value objects by CEF's own design, not
// something this probe needs special threading ceremony for.
//
// Algorithm matches java/tests/junittests/LeakChecker.java's memTest()
// exactly (itself a port of jpype's test_leak.py LeakChecker.memTest):
// fixed NUM_BATCHES batches of BATCH_SIZE calls each, RSS read via
// /proc/self/status between batches, "leaky" if fewer than
// CLEAN_BATCHES_REQUIRED batches show growth under GROWTH_THRESHOLD_BYTES.
// No time budget, same reasoning as that class's own 2026-08-30
// course-correction comment: bounded, deterministic total call volume
// regardless of throughput.

#include <fstream>
#include <functional>
#include <iostream>
#include <string>
#include <vector>

#include "include/cef_app.h"
#include "include/cef_browser.h"
#include "include/cef_command_line.h"
#include "include/cef_drag_data.h"
#include "include/cef_parser.h"
#include "include/cef_print_settings.h"
#include "include/cef_request.h"
#include "include/cef_request_context.h"
#include "include/cef_response.h"

namespace {

// Same tolerance shape as LeakChecker.java -- see that file's own comment
// for the rationale (a real leak grows in nearly every batch; GC/allocator
// timing noise produces occasional bad batches but never a persistent
// majority).
constexpr int kNumBatches = 10;
constexpr int kBatchSize = 200;
constexpr int kCleanBatchesRequired = 4;
constexpr double kGrowthThresholdBytesPerCall = 64.0;

// Linux-only, matching LeakChecker.java's readRssBytes() -- same reasoning
// (this repo's CLAUDE.md: Linux-first dev environment; Windows/macOS
// equivalents are follow-up work, not needed for this diagnostic).
long ReadRssBytes() {
  std::ifstream f("/proc/self/status");
  std::string line;
  while (std::getline(f, line)) {
    if (line.rfind("VmRSS:", 0) == 0) {
      // Format: "VmRSS:\t   12345 kB"
      size_t start = line.find_first_of("0123456789");
      if (start == std::string::npos) {
        return -1;
      }
      return std::stol(line.substr(start)) * 1024L;
    }
  }
  return -1;
}

// Returns true if leaky (fewer than kCleanBatchesRequired clean batches
// seen within kNumBatches). Prints per-batch growth either way, matching
// LeakChecker.printGrowth()'s always-visible diagnostic role for this
// standalone tool (there's no separate "known open finding" concept here
// -- this is a one-shot diagnostic run, not a regression-guarding test).
bool MemTest(const std::string& name, const std::function<void()>& action) {
  std::cout << "=== " << name << " (batchSize=" << kBatchSize << ") ===\n";

  long start_rss = ReadRssBytes();
  long prev_rss = start_rss;
  int clean_batches = 0;
  int batches_run = 0;
  bool leaky = true;

  for (int batch = 1; batch <= kNumBatches; ++batch) {
    for (int i = 0; i < kBatchSize; ++i) {
      action();
    }
    batches_run = batch;

    long cur_rss = ReadRssBytes();
    double growth = (cur_rss - prev_rss) / static_cast<double>(kBatchSize);
    prev_rss = cur_rss;

    std::cout << "  Batch" << (batch - 1) << ": rss=" << growth
              << " B/call  (cumulative=" << (cur_rss - start_rss)
              << " bytes over " << (batch * kBatchSize) << " calls)\n";

    // A negative reading is not evidence of a leak, same as
    // LeakChecker.java's `if (rssGrowth < 0) continue`.
    if (growth < 0) {
      continue;
    }

    if (growth < kGrowthThresholdBytesPerCall) {
      clean_batches++;
    }
    if (clean_batches > kCleanBatchesRequired - 1) {
      leaky = false;
      break;
    }
  }

  std::cout << name << ": " << (leaky ? "LEAK" : "PASS") << " (" << batches_run
            << " batches, cumulative=" << (prev_rss - start_rss)
            << " bytes over " << (batches_run * kBatchSize) << " calls)\n\n";
  return leaky;
}

}  // namespace

int main(int argc, char* argv[]) {
  CefMainArgs main_args(argc, argv);

  // CEF applications have multiple sub-processes (render, GPU, etc) that
  // share the same executable. This probe never creates a browser, so in
  // practice only the browser-process path below actually runs real work,
  // but subprocess re-exec must still be handled correctly, same as
  // cefsimple.
  int exit_code = CefExecuteProcess(main_args, nullptr, nullptr);
  if (exit_code >= 0) {
    return exit_code;
  }

  CefSettings settings;
  settings.no_sandbox = true;
  // Multi-threaded message loop: CEF drives its own UI thread
  // automatically, so this probe's main thread never needs to pump
  // anything (unlike JCEF's external_message_pump mode -- see
  // native/context.cpp). This deliberately avoids reproducing JCEF's own
  // pump-driving mechanism, since the whole point is to test CEF's C++
  // side in isolation from JCEF's JNI/dispose() plumbing.
  settings.multi_threaded_message_loop = true;
  settings.windowless_rendering_enabled = false;

  if (!CefInitialize(main_args, settings, nullptr, nullptr)) {
    std::cerr << "CefInitialize failed, exit code " << CefGetExitCode()
              << "\n";
    return 1;
  }

  bool any_leaky = false;

  any_leaky |= MemTest("CefResponse.Create/Release", [] {
    CefRefPtr<CefResponse> response = CefResponse::Create();
  });

  any_leaky |= MemTest("CefPostData.Create/Release", [] {
    CefRefPtr<CefPostData> data = CefPostData::Create();
  });

  any_leaky |= MemTest("CefPostDataElement.Create/Release", [] {
    CefRefPtr<CefPostDataElement> element = CefPostDataElement::Create();
  });

  any_leaky |= MemTest("CefDragData.Create/Release", [] {
    CefRefPtr<CefDragData> data = CefDragData::Create();
  });

  any_leaky |= MemTest("CefPrintSettings.Create/Release", [] {
    CefRefPtr<CefPrintSettings> settings = CefPrintSettings::Create();
  });

  any_leaky |= MemTest("CefRequest.Create/Release", [] {
    CefRefPtr<CefRequest> request = CefRequest::Create();
  });

  any_leaky |= MemTest("CefRequestContext.CreateContext/Release", [] {
    CefRequestContextSettings context_settings;
    CefRefPtr<CefRequestContext> context =
        CefRequestContext::CreateContext(context_settings, nullptr);
  });

  CefShutdown();

  std::cout << (any_leaky ? "RESULT: at least one target leaked in pure C++\n"
                          : "RESULT: all targets clean in pure C++\n");

  return any_leaky ? 1 : 0;
}
