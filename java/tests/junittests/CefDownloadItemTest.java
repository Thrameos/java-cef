// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.callback.CefBeforeDownloadCallback;
import org.cef.callback.CefDownloadItem;
import org.cef.callback.CefDownloadItemCallback;
import org.cef.handler.CefDownloadHandler;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

// Exercises CefDownloadHandler/CefDownloadItem (native/download_handler.cpp,
// CefDownloadItem_N.cpp, CefBeforeDownloadCallback_N.cpp,
// CefDownloadItemCallback_N.cpp -- together a large chunk of the remaining
// 0%-covered surface per Track B's real gcovr run). Serves a resource via
// addResource() with a Content-Disposition: attachment header, registers a
// CefDownloadHandler, navigates the browser directly to the download URL as
// its *initial* load (avoids the user-gesture requirement a JS-synthesized
// click would need, see the deleted v1 of this test / issue #11's finding).
// Never calls CefBeforeDownloadCallback.Continue(), so no file is ever
// actually written to disk.
//
// @Disabled: confirmed via debug instrumentation that Chromium DOES detect
// this as a download and aborts the navigation (onLoadError fires with
// ERR_ABORTED -- the normal, expected signal for a download-triggered
// navigation) -- but onBeforeDownload still never fires, and the test hangs
// for the full 30s watchdog timeout (a clean, bounded failure, not an
// unrecoverable hang). Most likely cause: TestSetupExtension's CefSettings
// never sets root_cache_path (there's a startup warning about exactly this
// -- "Please customize CefSettings.root_cache_path..."), so the download
// manager may not be fully initialized against the ephemeral/in-memory
// profile this suite runs with. root_cache_path is only settable globally
// at CefApp startup (same limitation documented for
// CefSettings.background_color, upstream issue #362) -- this whole suite
// shares one CefApp singleton via TestSetupExtension, so there's no way to
// vary it per-test without a separate JVM process per test. Filed as
// Thrameos/java-cef#18 to keep a reproducer on record even though the
// root_cache_path theory above isn't confirmed as the actual fix.
@Disabled("onBeforeDownload never fires without a real CefSettings."
        + "root_cache_path configured, which this suite's shared CefApp "
        + "singleton can't vary per-test -- see Thrameos/java-cef#18")
@ExtendWith(TestSetupExtension.class)
class CefDownloadItemTest {
    private static final String DOWNLOAD_URL = "http://test.com/download.bin";
    private static final String DOWNLOAD_CONTENT = "some download bytes";

    @Test
    void onBeforeDownloadFiresWithRealDownloadItem() {
        CefDownloadItem[] item = {null};
        String[] suggestedName = {null};
        boolean[] gotCallback = {false};

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
                        if (gotCallback[0]) return true;
                        gotCallback[0] = true;
                        item[0] = downloadItem;
                        suggestedName[0] = suggestedName_;
                        terminateTest();
                        return true;
                    }

                    @Override
                    public void onDownloadUpdated(CefBrowser browser,
                            CefDownloadItem downloadItem, CefDownloadItemCallback callback) {}
                });

                createBrowser(DOWNLOAD_URL, true /* useOSR */);
                super.setupTest();
            }
        };

        frame.awaitCompletion(30, TimeUnit.SECONDS);

        assertTrue(gotCallback[0], "onBeforeDownload was never invoked");
        assertNotNull(item[0]);
        assertTrue(item[0].isValid());
        assertEquals(DOWNLOAD_URL, item[0].getURL());
        assertEquals("test.bin", suggestedName[0]);
        assertFalse(item[0].isComplete());
    }
}
