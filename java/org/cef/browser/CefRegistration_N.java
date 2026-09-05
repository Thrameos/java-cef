// Copyright (c) 2024 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package org.cef.browser;

import org.cef.callback.CefNative;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class CefRegistration_N extends CefRegistration implements CefNative {
    // Used internally to store a pointer to the CEF object.
    private volatile long N_CefHandle = 0;
    private final Lock lock_ = new ReentrantLock();

    @Override
    public void setNativeRef(String identifier, long nativeRef) {
        N_CefHandle = nativeRef;
    }

    @Override
    public long getNativeRef(String identifier) {
        return N_CefHandle;
    }

    // See CefNativeAdapter's lockAndGetNativeRef()/unlock() for the full
    // rationale -- Thrameos/java-cef#22.
    long lockAndGetNativeRef(String identifier) {
        lock_.lock();
        return N_CefHandle;
    }

    void unlock(String identifier) {
        lock_.unlock();
    }

    @Override
    public void dispose() {
        try {
            N_Dispose(N_CefHandle);
        } catch (UnsatisfiedLinkError ule) {
            ule.printStackTrace();
        }
    }

    private final native void N_Dispose(long self);
}
