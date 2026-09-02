// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package org.cef.handler;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;

/**
 * An abstract adapter class for receiving frame life span events.
 * The methods in this class are empty.
 * This class exists as convenience for creating handler objects.
 */
public abstract class CefFrameHandlerAdapter implements CefFrameHandler {
    @Override
    public void onFrameCreated(CefBrowser browser, CefFrame frame) {}

    @Override
    public void onFrameDestroyed(CefBrowser browser, CefFrame frame) {}

    @Override
    public void onFrameAttached(CefBrowser browser, CefFrame frame, boolean reattached) {}

    @Override
    public void onFrameDetached(CefBrowser browser, CefFrame frame) {}

    @Override
    public void onMainFrameChanged(CefBrowser browser, CefFrame oldFrame, CefFrame newFrame) {}
}
