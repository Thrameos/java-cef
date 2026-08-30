package org.cef.callback;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CefNativeAdapter implements CefNative {
    // Used internally to store a pointer to the CEF object. volatile because
    // native callback threads read this concurrently with setNativeRef()
    // being called from a different thread during dispose/teardown -- a
    // plain (non-volatile) 64-bit field read/write is not guaranteed atomic
    // per JLS 17.7, so a concurrent reader could otherwise observe a torn
    // (half-old/half-new) value: a garbage pointer that is neither the old
    // nor the new native object. See Thrameos/java-cef#22 (a SIGSEGV
    // dereferencing pointer 0x21 -- consistent with exactly this kind of
    // torn read) and #24 for the fuller synchronized-access fix this is
    // paired with.
    private volatile long N_CefHandle = 0;
    private final Lock lock_ = new ReentrantLock();

    @Override
    public void setNativeRef(String identifer, long nativeRef) {
        N_CefHandle = nativeRef;
    }

    @Override
    public long getNativeRef(String identifer) {
        return N_CefHandle;
    }

    /**
     * Called by native code to atomically lock and read the native
     * reference. The caller MUST call unlock() (matched 1:1) after it has
     * safely retained its own reference to the returned pointer (e.g. via
     * CefRefPtr's AddRef), so that a concurrent setNativeRef()/dispose()
     * cannot release the object out from under the caller between reading
     * the pointer and retaining it. See jni_scoped_helpers.h's
     * SetCefForJNIObject_sync()/GetCefFromJNIObject_sync().
     *
     * @param identifer The name of the interface class (e.g. CefFocusHandler).
     * @return The stored reference value of the native code.
     */
    long lockAndGetNativeRef(String identifer) {
        lock_.lock();
        return N_CefHandle;
    }

    /**
     * Releases the lock acquired by lockAndGetNativeRef().
     *
     * @param identifer The name of the interface class (e.g. CefFocusHandler).
     */
    void unlock(String identifer) {
        lock_.unlock();
    }
}
