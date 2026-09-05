// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package org.cef.callback;

import org.cef.handler.CefPermissionHandler;

class CefPermissionPromptCallback_N
        extends CefNativeAdapter implements CefPermissionPromptCallback {
    CefPermissionPromptCallback_N() {}

    @Override
    protected void finalize() throws Throwable {
        Continue(CefPermissionHandler.PermissionRequestResult.IGNORE);
        super.finalize();
    }

    @Override
    public void Continue(int result) {
        try {
            N_Continue(getNativeRef(null), result);
        } catch (UnsatisfiedLinkError ule) {
            ule.printStackTrace();
        }
    }

    private final native void N_Continue(long self, int result);
}
