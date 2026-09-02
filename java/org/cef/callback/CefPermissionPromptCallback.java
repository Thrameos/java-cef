// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package org.cef.callback;

/**
 * Callback interface used for asynchronous continuation of permission
 * prompts.
 */
public interface CefPermissionPromptCallback {
    /**
     * Complete the permissions request with the specified result. See
     * CefPermissionHandler.PermissionRequestResult for the supported
     * values.
     *
     * @param result the result to complete the request with.
     */
    public void Continue(int result);
}
