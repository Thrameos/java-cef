// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.browser.CefMessageRouter.CefMessageRouterConfig;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.CountDownLatch;

// Exercises CefMessageRouter -- JCEF's window.cefQuery <-> Java bridge -- both the
// no-browser-needed registration surface and, via a real OSR browser + page load, a
// full round trip through native/message_router_handler.cpp (0% covered per this
// session's baseline gcovr run; see plan/roadmap.md Phase 2).
//
// The two browser-based tests below are migrated to the shared-browser
// (Tier 1) harness -- see plan/roadmap.md's "two-tier test harness" entry;
// the two no-browser tests above don't touch a browser at all, so there's
// nothing to migrate for them. Both migrated tests poll for window.cefQuery
// to exist before calling it -- see UpstreamIssue398Test's class comment
// for why: adding a CefMessageRouter to an ALREADY-RUNNING shared browser
// immediately followed by a navigation is a real race against the async
// IPC that injects the JS binding into the new page's context.
@ExtendWith({TestSetupExtension.class, SharedBrowserExtension.class})
class CefMessageRouterTest {
    @Test
    void createAddRemoveHandlerWithoutABrowser() {
        CefMessageRouter router = CefMessageRouter.create();
        assertNotNull(router);

        CefMessageRouterHandlerAdapter handler = new CefMessageRouterHandlerAdapter() {};
        assertTrue(router.addHandler(handler, true));
        assertTrue(router.removeHandler(handler));

        router.dispose();
    }

    @Test
    void configDefaultsMatchDocumentedJsFunctionNames() {
        CefMessageRouterConfig config = new CefMessageRouterConfig();
        assertEquals("cefQuery", config.jsQueryFunction);
        assertEquals("cefQueryCancel", config.jsCancelFunction);

        CefMessageRouter router = CefMessageRouter.create(config);
        assertEquals(config, router.getMessageRouterConfig());
        router.dispose();
    }

    private static final String POLL_PREFIX = "function trySend() {"
            + "  if (typeof window.cefQuery !== 'function') {"
            + "    setTimeout(trySend, 10);"
            + "    return;"
            + "  }";

    @Test
    void handledQueryInvokesSuccessCallbackInJavaScript() {
        final String content = "<html><body><script>" + POLL_PREFIX
                + "  window.cefQuery({request: 'ping', persistent: false,"
                + "    onSuccess: function(response) { document.title = response; },"
                + "    onFailure: function(code, msg) { document.title = 'FAIL:' + code; }});"
                + "}"
                + "trySend();"
                + "</script></body></html>";

        boolean[] gotQuery = {false};
        String[] title = {null};
        CountDownLatch done = new CountDownLatch(1);

        CefMessageRouter router = CefMessageRouter.create();
        router.addHandler(new CefMessageRouterHandlerAdapter() {
            @Override
            public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId,
                    String request, boolean persistent, CefQueryCallback callback) {
                if ("ping".equals(request)) {
                    gotQuery[0] = true;
                    callback.success("pong");
                    return true;
                }
                return false;
            }
        }, true);
        SharedBrowserExtension.addMessageRouter(router);

        SharedBrowserExtension.addDisplayHandler(new org.cef.handler.CefDisplayHandlerAdapter() {
            @Override
            public void onTitleChange(CefBrowser browser, String newTitle) {
                // The browser fires an initial onTitleChange with the page URL
                // as a placeholder title (no <title> tag in this page) before
                // the query's JS callback runs and sets the real one -- only
                // react to the title this test actually cares about.
                if (title[0] != null || !"pong".equals(newTitle)) {
                    return;
                }
                title[0] = newTitle;
                done.countDown();
            }
        });

        SharedBrowserExtension.loadPage(content);
        if (title[0] == null) {
            SharedBrowserExtension.awaitLatch(done, 15);
        }

        assertTrue(gotQuery[0], "CefMessageRouterHandler.onQuery was never invoked");
        assertEquals("pong", title[0]);
    }

    @Test
    void unhandledQueryInvokesFailureCallbackInJavaScript() {
        final String content = "<html><body><script>" + POLL_PREFIX
                + "  window.cefQuery({request: 'unhandled', persistent: false,"
                + "    onSuccess: function(response) { document.title = 'SUCCESS'; },"
                + "    onFailure: function(code, msg) { document.title = 'FAIL:' + code; }});"
                + "}"
                + "trySend();"
                + "</script></body></html>";

        String[] title = {null};
        CountDownLatch done = new CountDownLatch(1);

        CefMessageRouter router = CefMessageRouter.create();
        // Handler that never handles anything -- every query is left to the
        // router's own "no handler accepted it" auto-cancel path.
        router.addHandler(new CefMessageRouterHandlerAdapter() {}, true);
        SharedBrowserExtension.addMessageRouter(router);

        SharedBrowserExtension.addDisplayHandler(new org.cef.handler.CefDisplayHandlerAdapter() {
            @Override
            public void onTitleChange(CefBrowser browser, String newTitle) {
                // See the comment in the success test above: ignore the
                // browser's initial URL-as-placeholder title.
                if (title[0] != null || !"FAIL:-1".equals(newTitle)) {
                    return;
                }
                title[0] = newTitle;
                done.countDown();
            }
        });

        SharedBrowserExtension.loadPage(content);
        if (title[0] == null) {
            SharedBrowserExtension.awaitLatch(done, 15);
        }

        // Unhandled queries are auto-canceled with error code -1 (documented on
        // CefMessageRouter's class-level Javadoc).
        assertEquals("FAIL:-1", title[0]);
    }
}
