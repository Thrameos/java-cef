// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.awt.Component;
import java.awt.event.MouseWheelEvent;
import java.util.concurrent.CountDownLatch;

// Regression test for upstream chromiumembedded/java-cef#26 / this repo's
// #14: mouse wheel direction was inverted in OSR mode.
// native/CefBrowser_N.cpp's N_SendMouseWheelEvent used to pass the raw AWT
// MouseWheelEvent.getWheelRotation() value straight through as CEF's deltaY
// with no sign adjustment. AWT's convention (positive wheelRotation = wheel
// rotated away from the user, the typical "scroll down" gesture) is the
// opposite sign of what Chromium's CefMouseEvent deltaY convention expects
// for the same gesture. Fixed by negating delta before assigning it to
// deltaX/deltaY.
//
// Uses the same synthetic-AWT-event-dispatch technique already proven to
// work in CefKeyboardHandlerTest (unlike the mouse *click*/context-menu
// path, which did not route through to CEF the same way -- see
// plan/roadmap.md's Track A item 4 finding). Scrolls a tall page down first
// via JS, then dispatches a synthetic wheel event with a positive
// getWheelRotation() (AWT's "scroll down" convention) and confirms
// window.scrollY increased rather than decreased.
//
// Migrated to the shared-browser (Tier 1) harness -- see plan/roadmap.md's
// "two-tier test harness" entry. The wheel dispatch happens from inside
// onTitleChange() itself (a real EDT callback, not code the test runs after
// loadPage() returns), so no explicit invokeAndWait() is needed here -- see
// SharedBrowserExtension's own class comment on the EDT-threading
// distinction between the two shapes.
@ExtendWith({TestSetupExtension.class, SharedBrowserExtension.class})
class UpstreamIssue26Test {
    // window.scrollY on an OSR browser is a non-integer float (device-pixel-
    // ratio rounding), so titles carry it as a double, not an int -- an
    // earlier version of this test used Integer.parseInt() here and threw an
    // uncaught NumberFormatException from inside the onTitleChange() native
    // callback thread, which (matching the caution already documented in
    // UpstreamIssue398Test/DisplayHandlerTest) caused a genuine unrecoverable
    // hang rather than a clean test failure.
    private static final String CONTENT = "<html><body style='margin:0'>"
            + "<div style='height:5000px'>tall content</div>"
            + "<script>"
            + "window.scrollTo(0, 500);"
            // Wait a couple of animation frames so the programmatic
            // scrollTo()'s own asynchronous 'scroll' event has already fired
            // and settled before the 'ready' signal -- otherwise that
            // trailing event races the later wheel-dispatch-triggered one
            // and gets caught by the {once: true} listener below instead,
            // making it look like the wheel event did nothing.
            + "requestAnimationFrame(function() {"
            + "  requestAnimationFrame(function() {"
            + "    document.title = 'ready:' + window.scrollY;"
            + "    window.addEventListener('scroll', function() {"
            + "      document.title = 'scrolled:' + window.scrollY;"
            + "    }, {once: true});"
            + "  });"
            + "});"
            + "</script></body></html>";

    @Test
    void positiveWheelRotationScrollsDown() {
        double[] initialScrollY = {-1};
        double[] finalScrollY = {-1};
        boolean[] dispatchedWheel = {false};
        boolean[] gotScrollEvent = {false};
        CountDownLatch done = new CountDownLatch(1);

        SharedBrowserExtension.addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override
            public void onTitleChange(CefBrowser browser, String title) {
                if (title.startsWith("ready:") && !dispatchedWheel[0]) {
                    dispatchedWheel[0] = true;
                    initialScrollY[0] = Double.parseDouble(title.substring("ready:".length()));

                    Component canvas = browser.getUIComponent();
                    canvas.requestFocusInWindow();
                    long now = System.currentTimeMillis();
                    // getWheelRotation() > 0: AWT's "wheel rotated away from
                    // the user" convention, i.e. a normal "scroll down"
                    // gesture in every native application/browser.
                    for (int i = 0; i < 5; i++) {
                        canvas.dispatchEvent(new MouseWheelEvent(canvas,
                                MouseWheelEvent.MOUSE_WHEEL, now + i, 0, 50, 50, 0, false,
                                MouseWheelEvent.WHEEL_UNIT_SCROLL, 10, 10));
                    }
                } else if (title.startsWith("scrolled:") && !gotScrollEvent[0]) {
                    gotScrollEvent[0] = true;
                    finalScrollY[0] = Double.parseDouble(title.substring("scrolled:".length()));
                    done.countDown();
                }
            }
        });

        SharedBrowserExtension.loadPage(CONTENT);
        SharedBrowserExtension.awaitLatch(done, 15);

        assertTrue(dispatchedWheel[0],
                "Never reached the ready state to dispatch the wheel "
                        + "event");
        assertTrue(gotScrollEvent[0],
                "Page never reported a scroll event after the "
                        + "synthetic wheel event");
        assertTrue(finalScrollY[0] > initialScrollY[0],
                "A positive (AWT 'scroll down') wheel rotation should increase window.scrollY "
                        + "-- got initial=" + initialScrollY[0] + " final=" + finalScrollY[0]);
    }
}
