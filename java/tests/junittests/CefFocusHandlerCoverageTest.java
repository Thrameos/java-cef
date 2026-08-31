// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.handler.CefFocusHandlerAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

// Exercises native/focus_handler.cpp's FocusHandler::OnTakeFocus (previously 0%
// covered -- no existing test triggered it). Technique mirrors
// ~/devel/cef/tests/ceftests/os_rendering_unittest.cc's OSR_TEST_TAKE_FOCUS: give
// keyboard focus to the last (only, here) focusable element on the page, then send
// a real Tab key event. With nowhere left to tab *to* inside the page, Chromium
// calls CefFocusHandler::OnTakeFocus to let the embedder take focus back --
// exactly the "TAB key on the last HTML element" scenario the interface's own doc
// comment describes.
@ExtendWith(TestSetupExtension.class)
class CefFocusHandlerCoverageTest {
    private static final String TEST_URL = "http://test.com/focus_handler_coverage.html";
    private static final String CONTENT = "<html><body>"
            + "<input id='only' autofocus>"
            + "</body></html>";

    @Test
    void tabPastLastElementInvokesOnTakeFocus() throws InterruptedException {
        boolean[] fired = {false};
        boolean[] receivedNext = {false};

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                client_.addFocusHandler(new CefFocusHandlerAdapter() {
                    @Override
                    public void onTakeFocus(CefBrowser browser, boolean next) {
                        fired[0] = true;
                        receivedNext[0] = next;
                        terminateTest();
                    }
                });
                addResource(TEST_URL, CONTENT, "text/html");
                createBrowser(TEST_URL, true /* useOSR */);
                super.setupTest();
            }

            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                    boolean canGoBack, boolean canGoForward) {
                if (isLoading) return;
                browser.setFocus(true);

                // Give the page a moment to actually apply the `autofocus`
                // attribute and for the OSR surface/focus state to settle before
                // sending the Tab key -- same rationale/timing as
                // CefContextMenuTest's synthetic click delay.
                // javax.swing.Timer callbacks already run on the EDT (same thread
                // real AWT key events are delivered on), so dispatch directly here
                // rather than through SwingUtilities.invokeAndWait/Later.
                new javax.swing.Timer(500, ev -> {
                    ((javax.swing.Timer) ev.getSource()).stop();
                    Component canvas = browser.getUIComponent();
                    KeyListener[] listeners = canvas.getKeyListeners();
                    assertDoesNotThrow(() -> {
                        KeyEvent tabDown = new KeyEvent(canvas, KeyEvent.KEY_PRESSED,
                                System.currentTimeMillis(), 0, KeyEvent.VK_TAB,
                                KeyEvent.CHAR_UNDEFINED);
                        for (KeyListener listener : listeners) {
                            listener.keyPressed(tabDown);
                        }
                    });
                }).start();
            }
        };

        frame.awaitCompletion();

        assertTrue(fired[0], "onTakeFocus never fired after Tab past the last element");
        assertTrue(receivedNext[0], "Expected next=true (tabbing forward)");
    }
}
