// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package org.cef.callback;

/**
 * Callback interface used for asynchronous continuation of media access
 * permission requests.
 */
public interface CefMediaAccessCallback {
    /**
     * Call to allow or deny media access. If this callback was initiated in
     * response to a getUserMedia request then allowedPermissions must match
     * the requestedPermissions passed to
     * CefPermissionHandler.onRequestMediaAccessPermission. See
     * CefPermissionHandler.MediaAccessPermissionType for the supported bit
     * flags.
     *
     * @param allowedPermissions the permissions to allow.
     */
    public void Continue(int allowedPermissions);

    /**
     * Cancel the media access request.
     */
    public void Cancel();
}
