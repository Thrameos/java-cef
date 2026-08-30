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

        if (globalInstance == null) {
            globalInstance = result;
        } else if (globalInstance.N_CefHandle == result.N_CefHandle) {
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
