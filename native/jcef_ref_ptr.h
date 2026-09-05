// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

// A CefRefPtr<T> drop-in replacement that traces every AddRef()/Release()
// it performs. CefRefPtr<T> itself is just `using CefRefPtr = scoped_refptr;`
// (include/internal/cef_ptr.h) -- a type alias into CEF's own vendored,
// unmodifiable headers, so there's no way to add tracing to CefRefPtr<T>
// itself. JCefRefPtr<T> is a separate type with the same semantics (copy,
// move, reset, swap, implicit bool/pointer-like access) plus implicit
// conversion to/from CefRefPtr<T>, so it can be swapped in at a specific
// member field or local variable's declared type without having to touch
// the CEF API signatures it interacts with elsewhere.
//
// This exists because native/jni_scoped_helpers.h's CEF_ADDREF/CEF_RELEASE
// trace only covers SetCefForJNIObjectHelper's specific AddRef/Release
// choke point (the JNI-wrapper-association mechanism), not ordinary
// CefRefPtr<T> copy/assign/destroy used throughout the rest of native/
// *.cpp -- see issue_4_23_mental_model's "Scope correction" entry. Use
// JCefRefPtr<T> instead of CefRefPtr<T> for the specific member field(s) or
// local(s) under investigation for a leak/double-release, not as a blanket
// mechanical replacement of every CefRefPtr<T> in the codebase (too large a
// diff to land and verify in one pass -- see native/jcef_trace.h's "Finish
// tracer sweep" precedent for the same judgment call applied to JCEF_TRACE
// itself).
//
// Trace kind: REF kind=CEF_REFPTR_ADDREF / CEF_REFPTR_RELEASE ptr=<p> --
// registered as a pair in tools/analyze_jcef_trace.py's PAIRS list.

#ifndef JCEF_NATIVE_JCEF_REF_PTR_H_
#define JCEF_NATIVE_JCEF_REF_PTR_H_
#pragma once

#include <utility>

#include "include/cef_base.h"
#include "jcef_trace.h"

template <class T>
class JCefRefPtr {
 public:
  constexpr JCefRefPtr() = default;
  constexpr JCefRefPtr(std::nullptr_t) {}

  JCefRefPtr(T* p) : ptr_(p) { AddRefTraced(); }

  JCefRefPtr(const JCefRefPtr<T>& r) : ptr_(r.ptr_) { AddRefTraced(); }
  template <typename U>
  JCefRefPtr(const JCefRefPtr<U>& r) : ptr_(r.get()) {
    AddRefTraced();
  }

  // Implicit -- lets a JCefRefPtr<T> be constructed directly from a plain
  // CefRefPtr<T> (e.g. a CEF callback parameter) without an explicit cast.
  JCefRefPtr(const CefRefPtr<T>& r) : ptr_(r.get()) { AddRefTraced(); }

  JCefRefPtr(JCefRefPtr<T>&& r) noexcept : ptr_(r.ptr_) { r.ptr_ = nullptr; }
  template <typename U>
  JCefRefPtr(JCefRefPtr<U>&& r) noexcept : ptr_(r.ptr_) {
    r.ptr_ = nullptr;
  }

  ~JCefRefPtr() { ReleaseTraced(); }

  T* get() const { return ptr_; }
  T& operator*() const {
    DCHECK(ptr_);
    return *ptr_;
  }
  T* operator->() const {
    DCHECK(ptr_);
    return ptr_;
  }
  explicit operator bool() const { return ptr_ != nullptr; }

  // Implicit -- lets a JCefRefPtr<T> be passed anywhere a plain CefRefPtr<T>
  // is expected (the vast majority of the CEF API surface).
  operator CefRefPtr<T>() const { return CefRefPtr<T>(ptr_); }

  JCefRefPtr& operator=(std::nullptr_t) {
    reset();
    return *this;
  }
  JCefRefPtr& operator=(T* p) { return *this = JCefRefPtr(p); }
  JCefRefPtr& operator=(JCefRefPtr r) noexcept {
    swap(r);
    return *this;
  }
  JCefRefPtr& operator=(const CefRefPtr<T>& r) { return *this = JCefRefPtr(r); }

  void reset() { JCefRefPtr().swap(*this); }
  void swap(JCefRefPtr& r) noexcept { std::swap(ptr_, r.ptr_); }

  friend bool operator==(const JCefRefPtr<T>& lhs, const JCefRefPtr<T>& rhs) {
    return lhs.ptr_ == rhs.ptr_;
  }
  friend bool operator==(const JCefRefPtr<T>& lhs, std::nullptr_t) {
    return lhs.ptr_ == nullptr;
  }
  friend bool operator!=(const JCefRefPtr<T>& lhs, const JCefRefPtr<T>& rhs) {
    return !(lhs == rhs);
  }
  friend bool operator!=(const JCefRefPtr<T>& lhs, std::nullptr_t) {
    return lhs.ptr_ != nullptr;
  }
  // Pointer-identity ordering only (matches scoped_refptr's own <=>) --
  // needed so JCefRefPtr<T> can be used as a std::set/std::map key, same as
  // CefRefPtr<T> already is in a few places (e.g. ClientHandler's
  // MessageRouterSet).
  friend bool operator<(const JCefRefPtr<T>& lhs, const JCefRefPtr<T>& rhs) {
    return lhs.ptr_ < rhs.ptr_;
  }

 private:
  void AddRefTraced() {
    if (ptr_) {
      JCEF_TRACE("REF kind=CEF_REFPTR_ADDREF ptr=%p", (void*)ptr_);
      ptr_->AddRef();
    }
  }
  void ReleaseTraced() {
    if (ptr_) {
      JCEF_TRACE("REF kind=CEF_REFPTR_RELEASE ptr=%p", (void*)ptr_);
      ptr_->Release();
    }
  }

  T* ptr_ = nullptr;

  template <typename U>
  friend class JCefRefPtr;
};

#endif  // JCEF_NATIVE_JCEF_REF_PTR_H_
