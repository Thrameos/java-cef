// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.CountDownLatch;

// Exercises native/string_visitor.cpp (0% covered per this session's baseline
// gcovr run; see plan/roadmap.md Phase 2) via CefBrowser.getSource()/getText(),
// both of which deliver their (async) result through a CefStringVisitor.
//
// Migrated to the shared-browser (Tier 1) harness -- see plan/roadmap.md's
// "two-tier test harness" entry.
@ExtendWith({TestSetupExtension.class, SharedBrowserExtension.class})
class CefStringVisitorTest {
    private static final String CONTENT = "<html><body><p>Hello Visitor</p></body></html>";

    @Test
    void getTextReturnsRenderedPlainText() {
        String[] result = {null};
        CountDownLatch done = new CountDownLatch(1);

        SharedBrowserExtension.loadPage(CONTENT);
        CefBrowser browser = SharedBrowserExtension.browser();
        browser.getText(text -> {
            result[0] = text;
            done.countDown();
        });
        SharedBrowserExtension.awaitLatch(done, 15);

        assertTrue(result[0] != null && result[0].contains("Hello Visitor"),
                "Unexpected getText() result: " + result[0]);
    }

    @Test
    void getSourceReturnsRawHtml() {
        String[] result = {null};
        CountDownLatch done = new CountDownLatch(1);

        SharedBrowserExtension.loadPage(CONTENT);
        CefBrowser browser = SharedBrowserExtension.browser();
        browser.getSource(source -> {
            result[0] = source;
            done.countDown();
        });
        SharedBrowserExtension.awaitLatch(done, 15);

        assertTrue(result[0] != null && result[0].contains("Hello Visitor"),
                "Unexpected getSource() result: " + result[0]);
    }
}
