// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefRequestContext;
import org.cef.callback.CefCallback;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefRequestContextHandlerAdapter;
import org.cef.handler.CefResourceHandlerAdapter;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.misc.BoolRef;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.awt.BorderLayout;
import java.nio.ByteBuffer;

// Exercises native/request_context_handler.cpp (0% covered per this session's
// baseline gcovr run; see plan/roadmap.md Phase 2).
//
// CefRequestContextHandler.getResourceRequestHandler is only consulted when the
// browser's own CefRequestHandler.getResourceRequestHandler returns null for that
// request -- TestFrame always returns `this` (non-null), which would short-circuit
// this handler entirely. This test therefore builds its own minimal browser (via
// CefClient.createBrowser(url, useOSR, isTransparent, context), mirroring what
// TestFrame.createBrowser does internally) whose CefRequestHandler defers to the
// context handler instead of TestFrame's own resource map.
@ExtendWith(TestSetupExtension.class)
class CefRequestContextHandlerTest {
    private static final String TEST_URL = "http://test.com/request_context_handler.html";
    private static final String CONTENT =
            "<html><head><title>from-context-handler</title></head><body>Test!</body></html>";

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
    void handlerServesContentThroughARequestContext() {
        boolean[] gotHandlerCallback = {false};
        boolean[] gotTitle = {false};

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                CefRequestContext context =
                        CefRequestContext.createContext(new CefRequestContextHandlerAdapter() {
                            @Override
                            public CefResourceRequestHandler getResourceRequestHandler(
                                    CefBrowser browser, CefFrame frame, CefRequest request,
                                    boolean isNavigation, boolean isDownload,
                                    String requestInitiator, BoolRef disableDefaultHandling) {
                                gotHandlerCallback[0] = true;
                                return new CefResourceRequestHandler() {
                                    @Override
                                    public org.cef.handler.CefCookieAccessFilter
                                    getCookieAccessFilter(
                                            CefBrowser browser, CefFrame frame,
                                            CefRequest request) {
                                        return null;
                                    }
                                    @Override
                                    public boolean onBeforeResourceLoad(
                                            CefBrowser browser, CefFrame frame,
                                            CefRequest request) {
                                        return false;
                                    }
                                    @Override
                                    public org.cef.handler.CefResourceHandler getResourceHandler(
                                            CefBrowser browser, CefFrame frame,
                                            CefRequest request) {
                                        return new FixedContentHandler();
                                    }
                                    @Override
                                    public void onResourceRedirect(CefBrowser browser,
                                            CefFrame frame, CefRequest request,
                                            CefResponse response, StringRef new_url) {}
                                    @Override
                                    public boolean onResourceResponse(CefBrowser browser,
                                            CefFrame frame, CefRequest request,
                                            CefResponse response) {
                                        return false;
                                    }
                                    @Override
                                    public void onResourceLoadComplete(CefBrowser browser,
                                            CefFrame frame, CefRequest request,
                                            CefResponse response,
                                            org.cef.network.CefURLRequest.Status status,
                                            long receivedContentLength) {}
                                    @Override
                                    public void onProtocolExecution(CefBrowser browser,
                                            CefFrame frame, CefRequest request,
                                            BoolRef allowOsExecution) {}
                                };
                            }
                        });

                client_.addDisplayHandler(new CefDisplayHandlerAdapter() {
                    @Override
                    public void onTitleChange(CefBrowser browser, String title) {
                        if (gotTitle[0] || !"from-context-handler".equals(title)) return;
                        gotTitle[0] = true;
                        terminateTest();
                    }
                });

                // TestFrame's constructor unconditionally registers `this` as the
                // CefClient's CefRequestHandler, and getResourceRequestHandler
                // always returns non-null -- which would permanently shadow the
                // CefRequestContextHandler under test (CEF only consults it when
                // the browser-level handler returns null). Opt out of that so
                // this test's CefRequestContextHandler actually gets a chance,
                // instead of the request falling through to a real network call.
                delegateToRequestContextHandler_ = true;

                // Deliberately not calling super.setupTest()/createBrowser(String,
                // boolean) -- build the browser directly with a custom
                // CefRequestContext so this test's CefRequestContextHandler (not
                // TestFrame's own getResourceRequestHandler) serves the request.
                browser_ = client_.createBrowser(TEST_URL, true /* useOSR */,
                        false /* isTransparent */, context);
                getContentPane().add(browser_.getUIComponent(), BorderLayout.CENTER);
                pack();
                setSize(800, 600);
                setVisible(true);
            }
        };

        frame.awaitCompletion();

        assertTrue(gotHandlerCallback[0],
                "CefRequestContextHandler.getResourceRequestHandler was never invoked");
        assertTrue(gotTitle[0], "Page served via CefRequestContextHandler never loaded");
    }
}
