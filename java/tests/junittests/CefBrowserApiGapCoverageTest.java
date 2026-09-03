// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefBeforeDownloadCallback;
import org.cef.callback.CefDownloadItem;
import org.cef.callback.CefDownloadItemCallback;
import org.cef.handler.CefDownloadHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// Closes several previously-0%-covered gaps in native/CefBrowser_N.cpp found
// via a fresh jcef_build_llvmcov sweep this session (see
// plan/coverage-current-state.md's 2026-09-03 entry): CefBrowser.isLoading(),
// getFocusedFrame(), getFrameByName(), and startDownload() had no test
// exercising them at all. print()/getDevToolsClient()'s own native paths are
// deliberately left untouched here -- see Thrameos/java-cef#12, print() in
// particular is @Disabled elsewhere in this suite for a genuine
// unrecoverable hang risk and must not be called from a normal test.
@ExtendWith({TestSetupExtension.class, SharedBrowserExtension.class})
class CefBrowserApiGapCoverageTest {
    @Test
    void isLoadingReflectsCurrentLoadState() {
        SharedBrowserExtension.loadPage("<html><body>isLoading coverage</body></html>");
        // loadPage() only waits for the *main* frame's loading-state
        // transition; CEF's own IsLoading() can lag that by a beat (a
        // trailing subresource/frame settling) -- poll briefly rather than
        // asserting immediately. The "loading" branch is already exercised
        // implicitly by every onLoadingStateChange(isLoading=true) this
        // suite's tests already assert on; this call itself (the "not
        // loading" branch) is what native/CefBrowser_N.cpp's N_IsLoading
        // was missing (0% covered before this test).
        CefBrowser browser = SharedBrowserExtension.browser();
        boolean stillLoading = true;
        for (int i = 0; i < 50 && stillLoading; ++i) {
            stillLoading = browser.isLoading();
            if (stillLoading) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        assertFalse(stillLoading, "isLoading() never settled to false after loadPage() returned");
    }

    @Test
    void getFocusedFrameReturnsTheMainFrameByDefault() {
        SharedBrowserExtension.loadPage("<html><body>focused frame coverage</body></html>");
        CefFrame focused = SharedBrowserExtension.browser().getFocusedFrame();
        assertNotNull(focused, "getFocusedFrame() returned null for a freshly-loaded page");
        assertTrue(focused.isMain());
    }

    @Test
    void getFrameByNameFindsANamedIframeAndMissesAnUnknownOne() {
        SharedBrowserExtension.loadPage("<html><body>"
                + "<iframe name=\"named-child\" src=\"data:text/html,"
                + "<html><body>child</body></html>\"></iframe>"
                + "</body></html>");

        CefBrowser browser = SharedBrowserExtension.browser();
        // The named lookup can race the sub-frame's own creation slightly
        // after loadPage() returns (which only waits on the *main* frame's
        // loading-state transition) -- poll briefly rather than asserting
        // immediately.
        CefFrame named = null;
        for (int i = 0; i < 50 && named == null; ++i) {
            named = browser.getFrameByName("named-child");
            if (named == null) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        assertNotNull(named, "getFrameByName(\"named-child\") never found the iframe");
        assertFalse(named.isMain());

        assertEquals(null, browser.getFrameByName("no-such-frame-name"));
    }

    @Test
    void startDownloadTriggersOnBeforeDownloadForAnExplicitlyRequestedUrl() {
        String downloadUrl = "http://test.com/gap-coverage-download.bin";
        boolean[] gotBeforeDownload = {false};
        String[] seenUrl = {null};
        CountDownLatch done = new CountDownLatch(1);

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                addResource(downloadUrl, "download bytes", "application/octet-stream");
                client_.addDownloadHandler(new CefDownloadHandler() {
                    @Override
                    public boolean onBeforeDownload(CefBrowser browser, CefDownloadItem downloadItem,
                            String suggestedName, CefBeforeDownloadCallback callback) {
                        gotBeforeDownload[0] = true;
                        seenUrl[0] = downloadItem.getURL();
                        // Cancel rather than completing a real download --
                        // this test only cares that startDownload() reaches
                        // the handler, not about the download's own
                        // lifecycle (CefDownloadItemTest already covers
                        // that in depth).
                        done.countDown();
                        return false;
                    }

                    @Override
                    public void onDownloadUpdated(CefBrowser browser, CefDownloadItem downloadItem,
                            CefDownloadItemCallback callback) {}
                });

                addResource("http://test.com/gap-coverage-blank.html",
                        "<html><body>blank</body></html>", "text/html");
                createBrowser("http://test.com/gap-coverage-blank.html", true /* useOSR */);
                super.setupTest();
            }

            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                    boolean canGoBack, boolean canGoForward) {
                super.onLoadingStateChange(browser, isLoading, canGoBack, canGoForward);
                if (!isLoading) {
                    browser.startDownload(downloadUrl);
                }
            }
        };

        try {
            assertTrue(done.await(15, TimeUnit.SECONDS), "onBeforeDownload never fired");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertTrue(gotBeforeDownload[0]);
        assertEquals(downloadUrl, seenUrl[0]);

        frame.terminateTest();
        frame.awaitCompletion();
    }
}
