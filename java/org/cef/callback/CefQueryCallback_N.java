// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package org.cef.callback;

class CefQueryCallback_N extends CefNativeAdapter implements CefQueryCallback {
    // Set once via setPersistent() by MessageRouterHandler::OnQuery() (native
    // side), before onQuery() is invoked on the Java handler. See
    // Thrameos/java-cef#13: success() must not release the native callback
    // after the first call when the originating query was persistent, since
    // CEF's message router allows a persistent query's callback to be used
    // repeatedly.
    private boolean persistent_ = false;

    CefQueryCallback_N() {}

    void setPersistent(boolean persistent) {
        persistent_ = persistent;
    }

    @Override
    protected void finalize() throws Throwable {
        failure(-1, "Unexpected call to CefQueryCallback_N::finalize()");
        super.finalize();
    }

    @Override
    public void success(String response) {
        try {
            N_Success(getNativeRef(null), response, persistent_);
        } catch (UnsatisfiedLinkError ule) {
            ule.printStackTrace();
        }
    }

    @Override
    public void failure(int error_code, String error_message) {
        try {
            N_Failure(getNativeRef(null), error_code, error_message);
        } catch (UnsatisfiedLinkError ule) {
            ule.printStackTrace();
        }
    }

    private final native void N_Success(long self, String response, boolean persistent);
    private final native void N_Failure(long self, int error_code, String error_message);
}
