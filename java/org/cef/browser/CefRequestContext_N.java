// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package org.cef.browser;

import org.cef.callback.CefNative;
import org.cef.handler.CefRequestContextHandler;
import org.cef.misc.StringRef;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class CefRequestContext_N extends CefRequestContext implements CefNative {
    // Used internally to store a pointer to the CEF object. volatile + the
    // lock below for the same reason as CefNativeAdapter.N_CefHandle -- see
    // Thrameos/java-cef#22/#23.
    private volatile long N_CefHandle = 0;
    private final Lock lock_ = new ReentrantLock();
    private static CefRequestContext_N globalInstance = null;
    private CefRequestContextHandler handler = null;

    @Override
    public void setNativeRef(String identifer, long nativeRef) {
        N_CefHandle = nativeRef;
    }

    @Override
    public long getNativeRef(String identifer) {
        return N_CefHandle;
    }

    // See CefNativeAdapter's lockAndGetNativeRef()/unlock() for the full
    // rationale -- Thrameos/java-cef#22/#23.
    long lockAndGetNativeRef(String identifer) {
        lock_.lock();
        return N_CefHandle;
    }

    void unlock(String identifer) {
        lock_.unlock();
    }

    CefRequestContext_N() {
        super();
    }

    static final CefRequestContext_N getGlobalContextNative() {
        CefRequestContext_N result = null;
        try {
            result = CefRequestContext_N.N_GetGlobalContext();
        } catch (UnsatisfiedLinkError ule) {
            ule.printStackTrace();
        }

        // N_GetGlobalContext() always creates a brand-new native wrapper with
        // its own AddRef (see CefRequestContext_N.cpp's N_GetGlobalContext()),
        // even though it conceptually represents the same global CEF
        // CefRequestContext every time. This method exists to memoize that
        // into a single long-lived Java wrapper (globalInstance) instead of
        // leaking a fresh AddRef'd reference on every call.
        //
        // CORRECTION (2026-08-30, found via native/jcef_trace.h's ref/unref
        // tracing + tools/analyze_jcef_trace.py -- see plan/findings.md):
        // the previous version of this method only released the redundant
        // fresh `result` when its native pointer happened to equal the
        // already-cached globalInstance's pointer ("else if" with no final
        // "else"). CEF does not guarantee GetGlobalContext() returns the
        // identical wrapper pointer on every call -- confirmed directly via
        // the trace: two sequential browsers in one process produced two
        // DIFFERENT native pointers for what's supposed to be the same
        // global context, so the "else if" branch's equality check silently
        // failed and `result`'s AddRef'd reference was dropped with no
        // release at all -- a real, deterministic leak of a
        // CefRequestContext (and therefore its underlying CefBrowserContext)
        // reference on every browser after the first, still outstanding at
        // CefShutdown() time. This is a strong, well-evidenced contributing
        // cause of Thrameos/java-cef#4/#23's `all_.empty()` DCHECK (a
        // CefBrowserContext with an outstanding reference never getting
        // fully torn down before shutdown), though not yet confirmed as the
        // *complete* explanation. Fixed by always disposing the redundant
        // fresh wrapper once a globalInstance is already cached, regardless
        // of whether the native pointers happen to match.
        if (globalInstance == null) {
            globalInstance = result;
        } else {
            result.N_CefRequestContext_DTOR();
        }
        return globalInstance;
    }

    // Releases the single persistent native reference this class caches in
    // globalInstance (every CefBrowser without an explicit CefRequestContext
    // falls back to CefRequestContext.getGlobalContext(), so this is almost
    // always populated). Without this, that AddRef'd reference to the native
    // global CefRequestContext/CefBrowserContext survives indefinitely (a
    // static field, never otherwise cleared), keeping it registered in CEF's
    // internal ImplManager past CefShutdown() -- see Thrameos/java-cef#23's
    // "DCHECK failed: all_.empty()" during final process teardown. Must be
    // called before CefApp.shutdown() calls N_Shutdown().
    static final void disposeGlobalContextNative() {
        if (globalInstance != null) {
            globalInstance.dispose();
            globalInstance = null;
        }
    }

    static final CefRequestContext_N createNative(CefRequestContextHandler handler) {
        CefRequestContext_N result = null;
        try {
            result = CefRequestContext_N.N_CreateContext(handler);
        } catch (UnsatisfiedLinkError ule) {
            ule.printStackTrace();
        }
        if (result != null) result.handler = handler;
        return result;
    }

    @Override
    public void dispose() {
        try {
            N_CefRequestContext_DTOR();
        } catch (UnsatisfiedLinkError ule) {
            ule.printStackTrace();
        }
    }

    @Override
    public boolean isGlobal() {
        try {
            return N_IsGlobal();
        } catch (UnsatisfiedLinkError ule) {
            ule.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean hasPreference(String name) {
        try {
            return N_HasPreference(name);
        } catch (UnsatisfiedLinkError ule) {
            ule.printStackTrace();
        }
        return false;
    }

    @Override
    public Object getPreference(String name) {
        try {
            return N_GetPreference(name);
        } catch (UnsatisfiedLinkError ule) {
            ule.printStackTrace();
        }
        return null;
    }

    @Override
    public Map<String, Object> getAllPreferences(boolean includeDefaults) {
        try {
            return N_GetAllPreferences(includeDefaults);
        } catch (UnsatisfiedLinkError ule) {
            ule.printStackTrace();
        }
        return new HashMap<String, Object>();
    }

    @Override
    public boolean canSetPreference(String name) {
        try {
            return N_CanSetPreference(name);
        } catch (UnsatisfiedLinkError ule) {
            ule.printStackTrace();
        }
        return false;
    }

    @Override
    public String setPreference(String name, Object value) {
        try {
            return N_SetPreference(name, value);
        } catch (UnsatisfiedLinkError ule) {
            ule.printStackTrace();
            return ule.getMessage();
        }
    }

    @Override
    public CefRequestContextHandler getHandler() {
        return handler;
    }

    private final static native CefRequestContext_N N_GetGlobalContext();
    private final static native CefRequestContext_N N_CreateContext(
            CefRequestContextHandler handler);
    private final native boolean N_IsGlobal();
    private final native boolean N_HasPreference(String name);
    private final native Object N_GetPreference(String name);
    private final native Map<String, Object> N_GetAllPreferences(boolean includeDefaults);
    private final native boolean N_CanSetPreference(String name);
    private final native String N_SetPreference(String name, Object value);
    private final native void N_CefRequestContext_DTOR();
}
