// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package org.cef.handler;

import org.cef.browser.CefBrowser;

import java.awt.Rectangle;

/**
 * Implement this interface to handle events related to find results. The
 * methods of this class will be called on the UI thread.
 */
public interface CefFindHandler {
    /**
     * Called to report find results returned by CefBrowser.find().
     *
     * @param browser The corresponding browser.
     * @param identifier a unique incremental identifier for the currently
     * active search.
     * @param count the number of matches currently identified.
     * @param selectionRect the location of where the match was found (in
     * window coordinates).
     * @param activeMatchOrdinal the current position in the search results.
     * @param finalUpdate true if this is the last find notification.
     */
    public void onFindResult(CefBrowser browser, int identifier, int count,
            Rectangle selectionRect, int activeMatchOrdinal, boolean finalUpdate);
}
