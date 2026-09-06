// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package org.cef.handler;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;

/**
 * Implement this interface to handle events related to CefFrame life span.
 * The methods of this class will be called on the UI thread.
 */
public interface CefFrameHandler {
    /**
     * Called when a new frame is created. This will be the first
     * notification that references frame. Any commands that require
     * transport to the associated renderer process (loadRequest,
     * executeJavaScript, etc.) will be queued until onFrameAttached, or
     * discarded before onFrameDestroyed if the frame never attaches.
     */
    public void onFrameCreated(CefBrowser browser, CefFrame frame);

    /**
     * Called when an existing frame is destroyed. This will be the last
     * notification that references frame.
     */
    public void onFrameDestroyed(CefBrowser browser, CefFrame frame);

    /**
     * Called when a frame can begin routing commands to/from the associated
     * renderer process. reattached will be true if the frame was
     * re-attached after exiting the back/forward cache or after
     * encountering a recoverable connection error.
     */
    public void onFrameAttached(CefBrowser browser, CefFrame frame, boolean reattached);

    /**
     * Called when a frame loses its connection to the renderer process. This
     * may be followed by a (potentially async) call to onFrameDestroyed, or
     * by a later call to onFrameAttached if the frame recovers.
     */
    public void onFrameDetached(CefBrowser browser, CefFrame frame);

    /**
     * Called when the main frame changes due to initial browser creation,
     * final browser destruction, cross-origin navigation, or re-navigation
     * after renderer process termination. oldFrame will be null when a main
     * frame is assigned to browser for the first time. newFrame will be
     * null when a main frame is removed from browser for the last time.
     * Both will be non-null for cross-origin navigations or re-navigation
     * after renderer process termination.
     */
    public void onMainFrameChanged(CefBrowser browser, CefFrame oldFrame, CefFrame newFrame);
}
