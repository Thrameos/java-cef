// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.callback.CefBeforeDownloadCallback;
import org.cef.callback.CefDownloadItem;
import org.cef.callback.CefDownloadItemCallback;
import org.cef.handler.CefDownloadHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;

// Exercises CefDownloadHandler/CefDownloadItem/CefBeforeDownloadCallback/
// CefDownloadItemCallback (native/download_handler.cpp, CefDownloadItem_N.
// cpp, CefBeforeDownloadCallback_N.cpp, CefDownloadItemCallback_N.cpp --
// together a large chunk of the remaining 0%-covered surface per Track B's
// real gcovr run). Serves a resource via addResource() with a
// Content-Disposition: attachment header, registers a CefDownloadHandler,
// navigates the browser directly to the download URL as its *initial* load
// (avoids the user-gesture requirement a JS-synthesized click would need,
// see issue #11's finding).
//
// ROOT CAUSE of the original issue #18 finding (onBeforeDownload "never
// fires"): it does fire -- confirmed once the test actually reads the
// CefDownloadItem's state *inside* the callback. The original test held
// onto the CefDownloadItem object and called isValid()/getURL() etc. on it
// *after* awaitCompletion() returned (i.e. after terminateTest() had
// already begun tearing down the browser/window) -- CefDownloadItem is a
// scoped/temporary object (per its own javadoc: "Do not call any other
// methods if [isValid()] returns false"), same class of mistake this suite
// hit before with CefContextMenuParams/CefRequest params passed into other
// callbacks: capture primitives inside the callback, don't hold the object
// for later. Not a JCEF/CEF bug, not a root_cache_path issue -- the
// startup warning about root_cache_path is real but unrelated to this.
//
// This version goes further than just confirming the fix: actually calls
// CefBeforeDownloadCallback.Continue() with a real temp file path so the
// download completes for real, letting the test also exercise
// CefDownloadItemCallback's pause()/resume() and verify the downloaded
// file's real content on disk.
@ExtendWith(TestSetupExtension.class)
class CefDownloadItemTest {
    private static final String DOWNLOAD_URL = "http://test.com/download.bin";
    private static final String DOWNLOAD_CONTENT = "some download bytes";

    @Test
    void downloadCompletesAndWritesRealFile() throws Exception {
        boolean[] gotBeforeDownload = {false};
        boolean[] wasValid = {false};
        String[] url = {null};
        String[] suggestedName = {null};
        boolean[] triedPauseResume = {false};
        boolean[] gotComplete = {false};
        String[] finalFullPath = {null};
        long[] finalReceivedBytes = {-1};

        File targetFile = File.createTempFile("jcef-download-test-", ".bin");
        targetFile.delete();
        targetFile.deleteOnExit();

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                HashMap<String, String> headers = new HashMap<>();
                headers.put("Content-Disposition", "attachment; filename=\"test.bin\"");
                addResource(DOWNLOAD_URL, DOWNLOAD_CONTENT, "application/octet-stream", headers);

                client_.addDownloadHandler(new CefDownloadHandler() {
                    @Override
                    public boolean onBeforeDownload(CefBrowser browser,
                            CefDownloadItem downloadItem, String suggestedName_,
                            CefBeforeDownloadCallback callback) {
                        if (gotBeforeDownload[0]) return true;
                        gotBeforeDownload[0] = true;

                        wasValid[0] = downloadItem.isValid();
                        if (wasValid[0]) {
                            url[0] = downloadItem.getURL();
                            // Sweep the rest of CefDownloadItem's getters --
                            // just to exercise them, not asserting specific
                            // values for most (early-in-download state is
                            // inherently timing-dependent).
                            downloadItem.isInProgress();
                            downloadItem.isComplete();
                            downloadItem.isCanceled();
                            downloadItem.getCurrentSpeed();
                            downloadItem.getPercentComplete();
                            downloadItem.getTotalBytes();
                            downloadItem.getReceivedBytes();
                            downloadItem.getStartTime();
                            downloadItem.getEndTime();
                            downloadItem.getFullPath();
                            downloadItem.getId();
                            downloadItem.getSuggestedFileName();
                            downloadItem.getContentDisposition();
                            downloadItem.getMimeType();
                        }
                        suggestedName[0] = suggestedName_;

                        callback.Continue(targetFile.getAbsolutePath(), false /* showDialog */);
                        return true;
                    }

                    @Override
                    public void onDownloadUpdated(CefBrowser browser,
                            CefDownloadItem downloadItem, CefDownloadItemCallback callback) {
                        if (gotComplete[0] || !downloadItem.isValid()) return;

                        if (!downloadItem.isComplete() && !triedPauseResume[0]
                                && downloadItem.isInProgress()) {
                            triedPauseResume[0] = true;
                            callback.pause();
                            callback.resume();
                        }

                        if (downloadItem.isComplete()) {
                            gotComplete[0] = true;
                            finalFullPath[0] = downloadItem.getFullPath();
                            finalReceivedBytes[0] = downloadItem.getReceivedBytes();
                            terminateTest();
                        }
                    }
                });

                createBrowser(DOWNLOAD_URL, true /* useOSR */);
                super.setupTest();
            }
        };

        frame.awaitCompletion();

        assertTrue(gotBeforeDownload[0], "onBeforeDownload was never invoked");
        assertTrue(wasValid[0], "CefDownloadItem was not valid inside onBeforeDownload");
        assertEquals(DOWNLOAD_URL, url[0]);
        assertEquals("test.bin", suggestedName[0]);
        assertTrue(gotComplete[0], "Download never completed");
        assertEquals(targetFile.getAbsolutePath(), finalFullPath[0]);
        assertEquals(DOWNLOAD_CONTENT.length(), finalReceivedBytes[0]);
        assertTrue(targetFile.exists(), "Downloaded file does not exist on disk: " + targetFile);
        assertEquals(DOWNLOAD_CONTENT, new String(Files.readAllBytes(targetFile.toPath())));
    }
}
