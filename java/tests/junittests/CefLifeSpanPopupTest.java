// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

// Exercises native/life_span_handler.cpp's LifeSpanHandler::OnBeforePopup
// (0% covered -- no existing test triggers it), via a real window.open() call.
//
// @Disabled: window.open() never reaches OnBeforePopup in this harness --
// confirmed a genuine hang, not a test bug, via two ruled-out hypotheses:
// 1. Tried calling window.open() from a bare inline <script> at page load first
//    -- passed once, then hung waiting for onBeforePopup on a later run.
//    Consistent with Chromium's popup blocker requiring genuine user
//    activation (the same class of gate as beforeunload dialogs -- see
//    plan/roadmap.md's NEXT-UP PLAN).
// 2. Switched to triggering window.open() from a real synthetic mouse click
//    (same canvas.dispatchEvent(MOUSE_PRESSED/RELEASED/CLICKED) technique
//    CefContextMenuTest already uses successfully for its right-click) --
//    still hangs, every run. Added a diagnostic (document.title='clicked' in
//    the onclick handler, observed via CefDisplayHandler.onTitleChange): the
//    click *does* land and the onclick handler *does* run (title change
//    confirmed in the log) -- so this isn't a click-delivery problem. Yet
//    OnBeforePopup still never fires. Most likely explanation: Blink's
//    user-activation tracking isn't satisfied by this OSR synthetic-input
//    pipeline the way it is for other gestures (e.g. right-click already
//    reliably triggers OnBeforeContextMenu in CefContextMenuTest) -- not
//    confirmed further, left as an open native/CEF-behavior question rather
//    than guessed at.
//
// Left disabled per the standing "port tests, disable failures with a note,
// move on" strategy.
@ExtendWith(TestSetupExtension.class)
class CefLifeSpanPopupTest {
    private static final String TEST_URL = "http://test.com/life_span_popup.html";
    private static final String POPUP_URL = "http://test.com/popup_target.html";
    private static final String CONTENT = "<html><body>"
            + "<button id='opener' style='position:absolute; left:5px; top:5px; "
            + "width:100px; height:50px;' onclick=\"window.open('" + POPUP_URL
            + "', 'mypopup')\">open</button>"
            + "</body></html>";

    @Test
    @Disabled("window.open() never invokes OnBeforePopup in this harness even from a "
            + "real synthetic click that demonstrably reaches the onclick handler -- "
            + "see the class-level comment for the full investigation. Real, "
            + "reproducible finding, not a test bug.")
    void clickingOpenerInvokesOnBeforePopupAndIsCancelled() {
        boolean[] fired = {false};
        String[] receivedUrl = {null};
        String[] receivedFrameName = {null};

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                addResource(TEST_URL, CONTENT, "text/html");
                createBrowser(TEST_URL, true /* useOSR */);
                super.setupTest();
            }

            @Override
            public boolean onBeforePopup(
                    CefBrowser browser, CefFrame frame, String targetUrl, String targetFrameName) {
                fired[0] = true;
                receivedUrl[0] = targetUrl;
                receivedFrameName[0] = targetFrameName;
                terminateTest();
                return true; // Cancel -- no second browser is ever created.
            }

            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                    boolean canGoBack, boolean canGoForward) {
                if (isLoading) return;
                Component canvas = browser.getUIComponent();
                int x = 30;
                int y = 20;
                browser.setFocus(true);
                // Delay the synthetic click: the OSR GL surface may not be
                // realized/painted yet immediately after onLoadingStateChange
                // fires -- same rationale as CefContextMenuTest's click delay.
                new javax.swing.Timer(500, ev -> {
                    long now = System.currentTimeMillis();
                    canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, now,
                            InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1));
                    canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED,
                            now + 1, 0, x, y, 1, false, MouseEvent.BUTTON1));
                    canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED,
                            now + 1, 0, x, y, 1, false, MouseEvent.BUTTON1));
                }) {
                    { setRepeats(false); }
                }.start();
            }
        };

        frame.awaitCompletion();

        assertTrue(fired[0], "onBeforePopup never fired for the click on #opener");
        assertEquals(POPUP_URL, receivedUrl[0]);
        assertEquals("mypopup", receivedFrameName[0]);
    }
}
