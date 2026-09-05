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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

// Exercises native/scheme_handler_factory.cpp (0% covered per this session's
// baseline gcovr run; see plan/roadmap.md Phase 2) -- a distinct code path from
// TestFrame's addResource()/CefResourceRequestHandler mechanism used by every other
// test in this package: CefApp.registerSchemeHandlerFactory() registers a factory
// for a (scheme, domain) pair at the CefApp level rather than per-request.
// factoryServesContentToARealBrowser() is migrated to the shared-browser
// (Tier 1) harness -- see plan/roadmap.md's "two-tier test harness" entry;
// registerAndClearReturnTrue() never touches a browser, so there's nothing
// to migrate for it.
@ExtendWith({TestSetupExtension.class, SharedBrowserExtension.class})
class CefSchemeHandlerFactoryTest {
    private static final String DOMAIN = "jcef-scheme-factory-test.invalid";
    private static final String TEST_URL = "http://" + DOMAIN + "/page.html";
    private static final String CONTENT =
            "<html><head><title>from-factory</title></head><body>Test!</body></html>";

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
    void registerAndClearReturnTrue() {
        CefSchemeHandlerFactory factory =
                (browser, frame, schemeName, request) -> new FixedContentHandler();

        assertTrue(CefApp.getInstance().registerSchemeHandlerFactory("http", DOMAIN, factory));
        assertTrue(CefApp.getInstance().clearSchemeHandlerFactories());
    }

    @Test
    void factoryServesContentToARealBrowser() {
        boolean[] gotTitle = {false};
        CountDownLatch done = new CountDownLatch(1);

        CefApp.getInstance().registerSchemeHandlerFactory(
                "http", DOMAIN, (browser, frame, schemeName, request) -> {
                    return new FixedContentHandler();
                });
        try {
            SharedBrowserExtension.addDisplayHandler(new CefDisplayHandlerAdapter() {
                @Override
                public void onTitleChange(CefBrowser browser, String title) {
                    if (gotTitle[0] || !"from-factory".equals(title)) return;
                    gotTitle[0] = true;
                    done.countDown();
                }
            });

            SharedBrowserExtension.navigateTo(TEST_URL);
            if (!gotTitle[0]) {
                SharedBrowserExtension.awaitLatch(done, 15);
            }
        } finally {
            CefApp.getInstance().clearSchemeHandlerFactories();
        }

        assertTrue(gotTitle[0], "Page served via CefSchemeHandlerFactory never loaded");
    }
}
