// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.TimeUnit;

// Regression test for upstream chromiumembedded/java-cef#398 (and this fork's
// Thrameos/java-cef#13): the CEF message router's "subscription" style query
// workflow (JS sets persistent:true) is documented to allow
// CefQueryCallback.success() to be called repeatedly, with each call invoking
// the JS onSuccess handler again. native/CefQueryCallback_N.cpp's N_Success
// used to unconditionally call ClearSelf() after the first call regardless
// of |persistent|, so the native reference was torn down and a second
// success() call silently did nothing. Fixed by threading |persistent|
// through to N_Success (set on the Java-side callback object by
// MessageRouterHandler::OnQuery() before onQuery() is invoked) and only
// clearing the native ref when the query is not persistent.
@ExtendWith(TestSetupExtension.class)
class UpstreamIssue398Test {
    private static final String TEST_URL = "http://test.com/upstream_issue_398.html";
    private static final String CONTENT = "<html><body><script>"
            + "window.responseCount = 0;"
            + "window.cefQuery({request: 'subscribe', persistent: true,"
            + " onSuccess: function(response) {"
            + "   window.responseCount++;"
            + "   document.title = 'response' + window.responseCount;"
            + " },"
            + " onFailure: function(code, msg) { document.title = 'FAIL:' + code; }});"
            + "</script></body></html>";

    @Test
    void persistentQuerySuccessCanBeCalledMoreThanOnce() {
        CefQueryCallback[] savedCallback = {null};
        boolean[] gotFirstResponse = {false};
        String[] finalTitle = {null};
        boolean[] wasPersistent = {false};

        TestFrame frame = new TestFrame() {
            CefMessageRouter router;

            @Override
            protected void setupTest() {
                router = CefMessageRouter.create();
                router.addHandler(new CefMessageRouterHandlerAdapter() {
                    @Override
                    public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId,
                            String request, boolean persistent, CefQueryCallback callback) {
                        if ("subscribe".equals(request)) {
                            wasPersistent[0] = persistent;
                            savedCallback[0] = callback;
                            callback.success("first");
                            return true;
                        }
                        return false;
                    }
                }, true);
                client_.addMessageRouter(router);

                client_.addDisplayHandler(new CefDisplayHandlerAdapter() {
                    @Override
                    public void onTitleChange(CefBrowser browser, String newTitle) {
                        if ("response1".equals(newTitle) && !gotFirstResponse[0]) {
                            gotFirstResponse[0] = true;
                            // Per the documented "subscription" workflow, this
                            // second call should invoke the JS onSuccess
                            // handler again.
                            savedCallback[0].success("second");
                        } else if ("response2".equals(newTitle)) {
                            finalTitle[0] = newTitle;
                            terminateTest();
                        }
                    }
                });

                addResource(TEST_URL, CONTENT, "text/html");
                createBrowser(TEST_URL, true /* useOSR */);
                super.setupTest();
            }

            @Override
            protected void cleanupTest() {
                router.dispose();
                super.cleanupTest();
            }
        };

        // Shorter than the 30s default -- if the bug reproduces, "response2"
        // never arrives and there's no point waiting the full default.
        frame.awaitCompletion(10, TimeUnit.SECONDS);

        assertTrue(wasPersistent[0], "Query should have been sent with persistent:true");
        assertTrue(gotFirstResponse[0], "First success() call's response never arrived");
        assertEquals("response2", finalTitle[0],
                "Second success() call on a persistent query's callback never invoked the "
                        + "JS onSuccess handler again");
    }
}
