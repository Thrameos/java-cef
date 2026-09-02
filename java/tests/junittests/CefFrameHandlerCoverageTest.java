// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefFrameHandlerAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// Coverage for the CefFrameHandler binding (GH follow-up to #32): a
// data: URL iframe is enough to create a real sub-frame without needing a
// second addResource()'d URL, so onFrameCreated/onFrameAttached are
// asserted directly. onMainFrameChanged(old!=null, new!=null) only fires
// for a cross-origin main-frame navigation, which loadPage() (always
// http://test.com/shared/...) never exercises, so it's intentionally not
// asserted here.
@ExtendWith({TestSetupExtension.class, SharedBrowserExtension.class})
class CefFrameHandlerCoverageTest {
    private static final String CONTENT = "<html><body><iframe src=\"data:text/html,"
            + "<html><body>child</body></html>\"></iframe></body></html>";

    @Test
    void subFrameLifecycleCallbacksFire() {
        boolean[] sawCreated = {false};
        // onFrameAttached always follows onFrameCreated for a given frame,
        // so waiting on this latch alone is enough to know both fired.
        CountDownLatch subFrameAttached = new CountDownLatch(1);

        SharedBrowserExtension.addFrameHandler(new CefFrameHandlerAdapter() {
            @Override
            public void onFrameCreated(CefBrowser browser, CefFrame frame) {
                if (!frame.isMain()) sawCreated[0] = true;
            }

            @Override
            public void onFrameAttached(CefBrowser browser, CefFrame frame, boolean reattached) {
                if (!frame.isMain()) subFrameAttached.countDown();
            }
        });

        SharedBrowserExtension.loadPage(CONTENT);

        SharedBrowserExtension.awaitLatch(subFrameAttached, 10);
        assertTrue(sawCreated[0], "onFrameCreated never fired for the iframe's sub-frame");
    }
}
