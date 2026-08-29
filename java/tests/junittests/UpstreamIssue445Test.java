// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.CefApp;
import org.cef.browser.CefBrowser;
import org.cef.callback.CefCallback;
import org.cef.callback.CefSchemeHandlerFactory;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefResourceHandlerAdapter;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

// Regression test for upstream chromiumembedded/java-cef#445: custom scheme
// registration never reaches the renderer subprocess, so navigating to a
// genuinely custom (non-http) registered scheme fails. Root-caused upstream
// to native/util_posix.cpp's GetTempFileName():
//   tmpName << tmpPath.ToString().c_str();
//   tmpName << "jcef-p" << (useParentId ? util::GetParentPid() : util::GetPid());
// -- no separator between the temp directory path and the filename, so if
// CefGetPath(PK_DIR_TEMP) returns a path with no trailing slash (which is
// exactly what Chromium's base::GetTempDir() does on Linux), the resulting
// path is malformed (e.g. "/tmpjcef-p1234.tmp") and the subprocess helper
// can never find the custom scheme registration file. The original upstream
// report was filed from WSL2 Ubuntu -- the same environment this fork's
// entire test suite runs in -- with $TMPDIR unset, which is exactly the
// condition that triggers the no-trailing-slash fallback path.
//
// Uses TestSetupExtension's "jceftestscheme" custom scheme (registered once
// at CefApp startup for CefSchemeRegistrarTest) with a real
// CefSchemeHandlerFactory attached and a real navigation -- unlike
// UpstreamIssue365Test, which deliberately never attaches a factory.
//
// @Disabled until the bug is fixed -- confirmed by running this test
// un-@Disabled that it currently fails (times out waiting for the page to
// load, cleanly caught by TestFrame's watchdog, not a real hang).
@Disabled("Known bug: custom (non-http) scheme navigation never completes -- "
        + "see Thrameos/java-cef#15 (upstream chromiumembedded/java-cef#445)")
@ExtendWith(TestSetupExtension.class)
class UpstreamIssue445Test {
    private static final String TEST_URL = "jceftestscheme://test/custom-scheme-page";
    private static final String CONTENT =
            "<html><head><title>custom-scheme-loaded</title></head><body>Test!</body></html>";

    private static class FixedContentHandler extends CefResourceHandlerAdapter {
        private int offset_ = 0;

        @Override
        public boolean processRequest(CefRequest request, CefCallback callback) {
            callback.Continue();
            return true;
        }

        @Override
        public void getResponseHeaders(
                CefResponse response, IntRef response_length, StringRef redirectUrl) {
            response_length.set(CONTENT.length());
            response.setMimeType("text/html");
            response.setStatus(200);
        }

        @Override
        public boolean readResponse(
                byte[] data_out, int bytes_to_read, IntRef bytes_read, CefCallback callback) {
            int length = CONTENT.length();
            if (offset_ >= length) return false;

            int endPos = Math.min(offset_ + bytes_to_read, length);
            String chunk = CONTENT.substring(offset_, endPos);
            ByteBuffer.wrap(data_out).put(chunk.getBytes());
            bytes_read.set(chunk.length());
            offset_ = endPos;
            return true;
        }
    }

    @Test
    void navigatingToARealCustomSchemeLoadsContent() {
        boolean[] gotTitle = {false};

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                CefApp.getInstance().registerSchemeHandlerFactory("jceftestscheme", "test",
                        (browser, frame, schemeName, request) -> new FixedContentHandler());

                client_.addDisplayHandler(new CefDisplayHandlerAdapter() {
                    @Override
                    public void onTitleChange(CefBrowser browser, String title) {
                        if (gotTitle[0] || !"custom-scheme-loaded".equals(title)) return;
                        gotTitle[0] = true;
                        terminateTest();
                    }
                });

                createBrowser(TEST_URL, true /* useOSR */);
                super.setupTest();
            }

            @Override
            protected void cleanupTest() {
                CefApp.getInstance().clearSchemeHandlerFactories();
                super.cleanupTest();
            }
        };

        frame.awaitCompletion(15, TimeUnit.SECONDS);

        assertTrue(gotTitle[0], "Page served via a real custom (non-http) scheme never loaded");
    }
}
