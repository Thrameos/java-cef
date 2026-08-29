// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// Exercises native/string_visitor.cpp (0% covered per this session's baseline
// gcovr run; see plan/roadmap.md Phase 2) via CefBrowser.getSource()/getText(),
// both of which deliver their (async) result through a CefStringVisitor.
@ExtendWith(TestSetupExtension.class)
class CefStringVisitorTest {
    private static final String TEST_URL = "http://test.com/string_visitor.html";
    private static final String CONTENT =
            "<html><body><p>Hello Visitor</p></body></html>";

    @Test
    void getTextReturnsRenderedPlainText() {
        String[] result = {null};

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                addResource(TEST_URL, CONTENT, "text/html");
                createBrowser(TEST_URL, true /* useOSR */);
                super.setupTest();
            }

            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                    boolean canGoBack, boolean canGoForward) {
                if (isLoading) return;
                browser.getText(text -> {
                    result[0] = text;
                    terminateTest();
                });
            }
        };

        frame.awaitCompletion();

        assertTrue(result[0] != null && result[0].contains("Hello Visitor"),
                "Unexpected getText() result: " + result[0]);
    }

    @Test
    void getSourceReturnsRawHtml() {
        String[] result = {null};

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                addResource(TEST_URL, CONTENT, "text/html");
                createBrowser(TEST_URL, true /* useOSR */);
                super.setupTest();
            }

            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                    boolean canGoBack, boolean canGoForward) {
                if (isLoading) return;
                browser.getSource(source -> {
                    result[0] = source;
                    terminateTest();
                });
            }
        };

        frame.awaitCompletion();

        assertTrue(result[0] != null && result[0].contains("Hello Visitor"),
                "Unexpected getSource() result: " + result[0]);
    }
}
