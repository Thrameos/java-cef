// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.
//
// Standalone LD_PRELOAD diagnostic, NOT part of the JCEF product build --
// see tools_native/leak_probe for the sibling "isolate CEF's own C++ side"
// diagnostic this was modeled after.
//
// Purpose: catch issue #4/#23's still-unexplained heap-corruption crash
// (SIGSEGV inside glibc malloc/free internals, see plan/roadmap.md and the
// issue_4_23_mental_model memory) by intercepting malloc()/free() and
// recording, per pointer, the immediate caller of the allocation and (if
// freed) the immediate caller of the free. A double-free -- free() called
// again on a pointer already marked freed, with no intervening malloc()
// returning that same address -- prints both callers' resolved symbols
// (via dladdr) and aborts on the spot, so the crash's actual cause shows up
// directly instead of as a downstream SIGSEGV deep in an unrelated
// allocation.
//
// Deliberately NOT a full ASan/valgrind-class tool: this repo's ASan setup
// is blocked under WSL2 ("Shadow memory range interleaves with an existing
// memory mapping"), and the bug is a confirmed Heisenbug that vanishes
// under both gdb and (per this session's testing) a JCEF_ENABLE_TRACE
// Debug-vs-Release build swap. A thin LD_PRELOAD shim recording a single
// __builtin_return_address(0) per call (no stack unwinding, no backtrace())
// is the cheapest instrumentation that can still answer "who freed this
// twice" -- the goal is to add as little timing perturbation as possible.
//
// Usage:
//   g++ -O2 -fPIC -shared -o malloc_trace.so malloc_trace.cc -ldl
//   LD_PRELOAD=./malloc_trace.so <target command>
// A double-free report goes to stderr and the process aborts (SIGABRT) at
// the exact moment of the second free(), so run under `ulimit -c unlimited`
// or with the target's own crash-log mechanism (e.g. HotSpot's
// hs_err_pid*.log) to keep the abort's own state around too.

#define _GNU_SOURCE
#include <atomic>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <dlfcn.h>
#include <unistd.h>

namespace {

// Power-of-two slot count -- big enough to hold every live+recently-freed
// allocation JCEF/CEF/the JVM make during the ~10-15s window before the
// target crash without wrapping around and losing history, small enough
// (32 bytes/slot) to comfortably fit this sandbox's ~7.7GB RAM.
constexpr size_t kTableBits = 22;  // 4M slots * 32B = 128MB
constexpr size_t kTableSize = size_t(1) << kTableBits;
constexpr size_t kTableMask = kTableSize - 1;

struct Slot {
  std::atomic<void*> ptr{nullptr};
  void* alloc_caller = nullptr;
  void* free_caller = nullptr;
  std::atomic<bool> freed{false};
};

// Zero-initialized by the loader (.bss) -- no constructor runs before
// malloc/free can be called by very early libc/ld.so startup code, and no
// heap allocation of the table itself is needed.
Slot g_table[kTableSize];
std::atomic<bool> g_table_full_warned{false};

// --- Bootstrap arena -------------------------------------------------
// dlsym(RTLD_NEXT, "malloc") can itself call calloc() internally (a
// long-documented glibc quirk) before we've resolved the real malloc/
// calloc, i.e. before g_real_malloc/g_real_calloc are non-null. Serve any
// allocation requested while resolution is in flight from a static arena
// instead of recursing. These allocations are never individually freed
// (free() on a bootstrap-arena pointer is just a silent no-op below) --
// fine, it's used for a handful of small one-time dlsym bookkeeping
// allocations only, not the target program's real allocations.
constexpr size_t kBootstrapSize = 1 << 20;  // 1MB
alignas(16) char g_bootstrap[kBootstrapSize];
std::atomic<size_t> g_bootstrap_off{0};

bool InBootstrap(const void* p) {
  return p >= static_cast<const void*>(g_bootstrap) &&
         p < static_cast<const void*>(g_bootstrap + kBootstrapSize);
}

void* BootstrapAlloc(size_t size) {
  size = (size + 15) & ~size_t(15);
  size_t off = g_bootstrap_off.fetch_add(size, std::memory_order_relaxed);
  if (off + size > kBootstrapSize) {
    const char msg[] = "malloc_trace: bootstrap arena exhausted\n";
    write(2, msg, sizeof(msg) - 1);
    _exit(97);
  }
  return g_bootstrap + off;
}

// --- Real allocator resolution -----------------------------------------
using MallocFn = void* (*)(size_t);
using FreeFn = void (*)(void*);
using CallocFn = void* (*)(size_t, size_t);
using ReallocFn = void* (*)(void*, size_t);

std::atomic<MallocFn> g_real_malloc{nullptr};
std::atomic<FreeFn> g_real_free{nullptr};
std::atomic<CallocFn> g_real_calloc{nullptr};
std::atomic<ReallocFn> g_real_realloc{nullptr};

// Re-entrancy guard: dlsym() itself, or our own diagnostic printing, must
// never recurse back into our hooks in a way that deadlocks or infinitely
// recurses. Per-thread, since multiple CEF/JVM threads call malloc/free
// concurrently and each needs its own "am I already inside the hook" state.
thread_local bool g_in_hook = false;

FreeFn RealFree() {
  FreeFn f = g_real_free.load(std::memory_order_acquire);
  if (f) return f;
  g_in_hook = true;
  f = reinterpret_cast<FreeFn>(dlsym(RTLD_NEXT, "free"));
  g_in_hook = false;
  g_real_free.store(f, std::memory_order_release);
  return f;
}

MallocFn RealMalloc() {
  MallocFn f = g_real_malloc.load(std::memory_order_acquire);
  if (f) return f;
  g_in_hook = true;
  f = reinterpret_cast<MallocFn>(dlsym(RTLD_NEXT, "malloc"));
  g_in_hook = false;
  g_real_malloc.store(f, std::memory_order_release);
  return f;
}

CallocFn RealCalloc() {
  CallocFn f = g_real_calloc.load(std::memory_order_acquire);
  if (f) return f;
  g_in_hook = true;
  f = reinterpret_cast<CallocFn>(dlsym(RTLD_NEXT, "calloc"));
  g_in_hook = false;
  g_real_calloc.store(f, std::memory_order_release);
  return f;
}

ReallocFn RealRealloc() {
  ReallocFn f = g_real_realloc.load(std::memory_order_acquire);
  if (f) return f;
  g_in_hook = true;
  f = reinterpret_cast<ReallocFn>(dlsym(RTLD_NEXT, "realloc"));
  g_in_hook = false;
  g_real_realloc.store(f, std::memory_order_release);
  return f;
}

size_t HashPtr(void* p) {
  uint64_t v = reinterpret_cast<uintptr_t>(p);
  v ^= v >> 33;
  v *= 0xff51afd7ed558ccdULL;
  v ^= v >> 33;
  return static_cast<size_t>(v) & kTableMask;
}

// Out-of-scope allocators to ignore entirely (never tracked, so their own
// internal double-frees -- confirmed to be real but irrelevant here, see
// this tool's own investigation notes -- never trigger a report). WSL2's
// D3D12/GPU translation shim (libnvwgf2umx.so, libd3d12core.so under
// /usr/lib/wsl/) has at least one genuine double-free of its own, unrelated
// to JCEF/CEF/the JVM; this is the only allowlist-vs-denylist decision this
// tool makes.
// Allowlist, not a denylist: this environment turned out to have several
// real-but-irrelevant double-frees of its own (WSL2's D3D12/GPU driver
// shim, and something in libjvm.so's internals) that would otherwise
// swamp the one thing this tool exists to find -- a corruption reachable
// from JCEF's or CEF's own code, matching issue #4/#23's documented crash
// signature (SIGSEGV inside libc.so.6's malloc/free machinery, reached
// from a JCEF/CEF call chain). Only track an allocation if the code that
// requested it lives in one of these two shared libraries.
// Absolute path prefix of this project's own build output -- deliberately
// a path-prefix match, not a loose basename substring match. A first
// attempt at this using strstr(fname, "libEGL.so") wrongly matched the
// *system's* /lib/x86_64-linux-gnu/libEGL.so.1 (unrelated, another one of
// this sandbox's own graphics-driver-adjacent double-frees) because
// "libEGL.so" is a substring of "libEGL.so.1" too -- only this exact
// directory (jcef_build/native/<config>/) holds the libraries actually
// built or shipped by this project.
constexpr const char* kInScopeDirs[] = {
    "/home/kenelson/devel/java-cef/jcef_build/native/",
    "/home/kenelson/devel/java-cef/jcef_build_trace/native/",
};

bool IsInScopeCaller(void* caller) {
  if (!caller) return false;
  Dl_info info;
  if (!dladdr(caller, &info) || !info.dli_fname) return false;
  for (const char* dir : kInScopeDirs) {
    if (strncmp(info.dli_fname, dir, strlen(dir)) == 0) return true;
  }
  return false;
}

void PrintCaller(const char* label, void* caller) {
  Dl_info info;
  if (caller && dladdr(caller, &info) && info.dli_sname) {
    fprintf(stderr, "  %s: %p  %s+0x%lx  (%s)\n", label, caller,
            info.dli_sname,
            reinterpret_cast<uintptr_t>(caller) -
                reinterpret_cast<uintptr_t>(info.dli_saddr),
            info.dli_fname ? info.dli_fname : "?");
  } else if (caller && dladdr(caller, &info) && info.dli_fname) {
    fprintf(stderr, "  %s: %p  (%s+0x%lx)\n", label, caller,
            info.dli_fname,
            reinterpret_cast<uintptr_t>(caller) -
                reinterpret_cast<uintptr_t>(info.dli_fbase));
  } else {
    fprintf(stderr, "  %s: %p  (unresolved)\n", label, caller);
  }
}

// Records a successful allocation. Reuses a dead (freed) slot for the same
// pointer if this address is being handed out again (legitimate allocator
// reuse), otherwise claims the first empty slot found by linear probing.
// Diagnostic-only: confirms whether any traffic at all is reaching this
// tool's allowlist, since CEF/Chromium typically route most of their own
// C++ allocations through PartitionAlloc rather than glibc malloc/free
// directly -- if this stays nearly zero across a real run, that's strong
// evidence the actual corruption isn't reachable via a raw malloc/free
// interposer at all, and a different technique is needed.
std::atomic<uint64_t> g_in_scope_alloc_count{0};

void RecordAlloc(void* p, void* caller) {
  if (!p) return;
  if (!IsInScopeCaller(caller)) return;
  uint64_t n = g_in_scope_alloc_count.fetch_add(1, std::memory_order_relaxed);
  if ((n & 0xFFFF) == 0) {
    fprintf(stderr, "[malloc_trace] in-scope allocations so far: %llu\n",
            static_cast<unsigned long long>(n));
  }
  size_t h = HashPtr(p);
  for (size_t i = 0; i < kTableSize; ++i) {
    size_t idx = (h + i) & kTableMask;
    void* existing = g_table[idx].ptr.load(std::memory_order_acquire);
    if (existing == p) {
      g_table[idx].alloc_caller = caller;
      g_table[idx].freed.store(false, std::memory_order_release);
      return;
    }
    if (existing == nullptr) {
      void* expected = nullptr;
      if (g_table[idx].ptr.compare_exchange_strong(
              expected, p, std::memory_order_acq_rel)) {
        g_table[idx].alloc_caller = caller;
        g_table[idx].freed.store(false, std::memory_order_release);
        return;
      }
      // Lost the race for this slot -- keep probing; the winner already
      // recorded a valid entry (possibly for a different pointer).
      if (expected == p) {
        g_table[idx].alloc_caller = caller;
        g_table[idx].freed.store(false, std::memory_order_release);
        return;
      }
    }
  }
  if (!g_table_full_warned.exchange(true)) {
    const char msg[] =
        "malloc_trace: tracking table full -- further allocations are "
        "unrecorded (double-free detection for them is disabled)\n";
    write(2, msg, sizeof(msg) - 1);
  }
}

// Checks a free() against the tracked table. Returns true if this is a
// genuine double-free (already-freed slot for this exact pointer), in
// which case it has already printed the report -- caller should abort.
// Note: no in-scope filter here (unlike RecordAlloc) -- a double-free is
// still worth reporting even if the second free() call happens to be made
// by generic code (e.g. glibc's own internals unwinding a call chain that
// started in libjcef.so/libcef.so), as long as the ORIGINAL allocation
// (already filtered by RecordAlloc) came from the code this tool cares
// about. Only pointers RecordAlloc actually tracked ever reach the
// existing==p branch below, so this stays correctly scoped.
bool CheckAndRecordFree(void* p, void* caller) {
  if (!p) return false;
  size_t h = HashPtr(p);
  for (size_t i = 0; i < kTableSize; ++i) {
    size_t idx = (h + i) & kTableMask;
    void* existing = g_table[idx].ptr.load(std::memory_order_acquire);
    if (existing == nullptr) {
      // Never seen this pointer allocated through us -- either allocated
      // before the hook was fully installed, or the table wrapped/missed
      // it. Nothing to check; let the real free() run.
      return false;
    }
    if (existing == p) {
      if (g_table[idx].freed.load(std::memory_order_acquire)) {
        fprintf(stderr,
                "\n=== malloc_trace: DOUBLE FREE detected, ptr=%p ===\n", p);
        PrintCaller("original alloc caller", g_table[idx].alloc_caller);
        PrintCaller("first free() caller  ", g_table[idx].free_caller);
        PrintCaller("second free() caller ", caller);
        fprintf(stderr, "=== end double-free report -- aborting now ===\n\n");
        fflush(stderr);
        return true;
      }
      g_table[idx].free_caller = caller;
      g_table[idx].freed.store(true, std::memory_order_release);
      return false;
    }
  }
  return false;
}

}  // namespace

extern "C" {

void* malloc(size_t size) {
  if (g_in_hook) {
    // Reentrant call (from within dlsym() resolving a symbol, or from our
    // own bookkeeping) -- serve from the bootstrap arena, never recurse.
    return BootstrapAlloc(size);
  }
  MallocFn real = RealMalloc();
  void* p = real(size);
  if (!g_in_hook) {
    void* caller = __builtin_return_address(0);
    g_in_hook = true;
    RecordAlloc(p, caller);
    g_in_hook = false;
  }
  return p;
}

void free(void* p) {
  if (!p) return;
  if (InBootstrap(p)) {
    // Allocated from the bootstrap arena -- never individually freed (see
    // BootstrapAlloc's own comment). Silently ignore, matching the arena's
    // "one-time, never freed" design.
    return;
  }
  FreeFn real = RealFree();
  if (!g_in_hook) {
    void* caller = __builtin_return_address(0);
    g_in_hook = true;
    bool double_free = CheckAndRecordFree(p, caller);
    g_in_hook = false;
    if (double_free) {
      abort();
    }
  }
  real(p);
}

void* calloc(size_t nmemb, size_t size) {
  if (g_in_hook) {
    void* p = BootstrapAlloc(nmemb * size);
    memset(p, 0, nmemb * size);
    return p;
  }
  CallocFn real = RealCalloc();
  if (!real) {
    // First-ever calloc call, before dlsym has resolved calloc itself --
    // dlsym's own internals may call calloc reentrantly (see RealCalloc()).
    // Serve this one from the bootstrap arena too.
    void* p = BootstrapAlloc(nmemb * size);
    memset(p, 0, nmemb * size);
    return p;
  }
  void* p = real(nmemb, size);
  if (!g_in_hook) {
    void* caller = __builtin_return_address(0);
    g_in_hook = true;
    RecordAlloc(p, caller);
    g_in_hook = false;
  }
  return p;
}

void* realloc(void* p, size_t size) {
  if (p && InBootstrap(p)) {
    // Bootstrap-arena pointer growing past its original size -- hand out a
    // fresh real allocation and copy; never realloc the arena itself.
    ReallocFn real = RealRealloc();
    void* np = real ? real(nullptr, size) : BootstrapAlloc(size);
    memcpy(np, p, size);
    return np;
  }
  ReallocFn real = RealRealloc();
  void* caller = __builtin_return_address(0);
  if (!g_in_hook && p) {
    // realloc(p, ...) both frees p (logically) and returns a new pointer --
    // treat the old pointer's disposal the same as free() for double-free
    // detection (e.g. realloc() called on a pointer already freed
    // elsewhere is exactly as much of a bug as a plain double free).
    g_in_hook = true;
    bool double_free = CheckAndRecordFree(p, caller);
    g_in_hook = false;
    if (double_free) {
      abort();
    }
  }
  void* np = real(p, size);
  if (!g_in_hook) {
    g_in_hook = true;
    RecordAlloc(np, caller);
    g_in_hook = false;
  }
  return np;
}

}  // extern "C"
