// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package org.cef.handler;

import org.cef.browser.CefBrowser;
import org.cef.callback.CefBeforeDownloadCallback;
import org.cef.callback.CefDownloadItem;
import org.cef.callback.CefDownloadItemCallback;

/**
 * Implement this interface to handle file downloads. The methods of this class
 * will called on the browser process UI thread.
 */
public interface CefDownloadHandler {
    /**
     * Called before a download begins. Return true and execute |callback| either
     * asynchronously or in this method to continue the download. Return false
     * to cancel the download. Do not keep a reference to downloadItem outside
     * of this method.
     *
     * <p>Unlike CEF's own C++ {@code CefDownloadHandler::OnBeforeDownload},
     * returning false here always cancels safely, regardless of runtime
     * style. (CEF's own contract is runtime-dependent: cancel under Alloy
     * style, but Chrome-style default download-shelf handling otherwise --
     * this project's embedding never implemented that shelf UI, and letting
     * a false return reach it could crash the process with an internal
     * {@code CHECK} failure if the browser was torn down before CEF's
     * deferred shelf-handling task ran; see
     * plan/tasks/20260905-26-download-shelf-check-crash.md for the original
     * root-cause writeup.) The JNI binding now normalizes false to a safe
     * cancel (drop the callback un-run) before it ever reaches CEF, so both
     * true+drop-callback and a plain false return are equally safe ways to
     * reject a download.
     *
     * @param browser The desired browser.
     * @param downloadItem The item to be downloaded. Do not keep a reference to it outside this
     * method.
     * @param suggestedName is the suggested name for the download file.
     * @param callback start the download by calling the Continue method
     */
    public boolean onBeforeDownload(CefBrowser browser, CefDownloadItem downloadItem,
            String suggestedName, CefBeforeDownloadCallback callback);

    /**
     * Called when a download's status or progress information has been updated.
     * @param browser The desired browser.
     * @param downloadItem The downloading item.
     * @param callback Execute callback to cancel the download
     */
    public void onDownloadUpdated(
            CefBrowser browser, CefDownloadItem downloadItem, CefDownloadItemCallback callback);
}
