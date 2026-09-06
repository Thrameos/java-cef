// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package org.cef.handler;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefMediaAccessCallback;
import org.cef.callback.CefPermissionPromptCallback;

/**
 * Implement this interface to handle events related to permission requests.
 * The methods of this class will be called on the browser process UI thread.
 */
public interface CefPermissionHandler {
    /**
     * Media access permissions used by onRequestMediaAccessPermission.
     */
    public static final class MediaAccessPermissionType {
        public final static int PERMISSION_NONE = 0;
        public final static int PERMISSION_DEVICE_AUDIO_CAPTURE = 1 << 0;
        public final static int PERMISSION_DEVICE_VIDEO_CAPTURE = 1 << 1;
        public final static int PERMISSION_DESKTOP_AUDIO_CAPTURE = 1 << 2;
        public final static int PERMISSION_DESKTOP_VIDEO_CAPTURE = 1 << 3;
    }

    /**
     * Permission request results used by onDismissPermissionPrompt and
     * CefPermissionPromptCallback.Continue.
     */
    public static final class PermissionRequestResult {
        /** Accept the permission request as an explicit user action. */
        public final static int ACCEPT = 0;
        /** Deny the permission request as an explicit user action. */
        public final static int DENY = 1;
        /** Dismiss the permission request as an explicit user action. */
        public final static int DISMISS = 2;
        /** Ignore the permission request. */
        public final static int IGNORE = 3;
    }

    /**
     * Called when a page requests permission to access media.
     *
     * @param browser The corresponding browser.
     * @param frame The corresponding frame.
     * @param requestingOrigin the URL origin requesting permission.
     * @param requestedPermissions a combination of values from
     * MediaAccessPermissionType that represent the requested permissions.
     * @param callback execute callback methods either in this method or at
     * a later time to continue or cancel the request.
     * @return true to handle the request, or false to proceed with default
     * handling (deny the request).
     */
    public boolean onRequestMediaAccessPermission(CefBrowser browser, CefFrame frame,
            String requestingOrigin, int requestedPermissions,
            CefMediaAccessCallback callback);

    /**
     * Called when a page should show a permission prompt.
     *
     * @param browser The corresponding browser.
     * @param promptId uniquely identifies the prompt.
     * @param requestingOrigin the URL origin requesting permission.
     * @param requestedPermissions a combination of CEF_PERMISSION_TYPE_*
     * values (see cef_permission_request_types_t in CEF's cef_types.h) that
     * represent the requested permissions.
     * @param callback call Continue either in this method or at a later
     * time to continue or cancel the request.
     * @return true to handle the request, or false to proceed with default
     * handling (CEF_PERMISSION_RESULT_IGNORE with Alloy style).
     */
    public boolean onShowPermissionPrompt(CefBrowser browser, long promptId,
            String requestingOrigin, int requestedPermissions,
            CefPermissionPromptCallback callback);

    /**
     * Called when a permission prompt handled via onShowPermissionPrompt is
     * dismissed. promptId will match the value that was passed to
     * onShowPermissionPrompt. This method will not be called if
     * onShowPermissionPrompt returned false for promptId.
     *
     * @param browser The corresponding browser.
     * @param promptId uniquely identifies the prompt.
     * @param result one of the PermissionRequestResult values.
     */
    public void onDismissPermissionPrompt(CefBrowser browser, long promptId, int result);
}
