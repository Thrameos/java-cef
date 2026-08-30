// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefContextMenuParams;
import org.cef.callback.CefMenuModel;
import org.cef.handler.CefContextMenuHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

// Exercises CefContextMenuHandler/CefContextMenuParams/CefMenuModel (native/
// context_menu_handler.cpp, CefContextMenuParams_N.cpp, CefMenuModel_N.cpp --
// together the single largest remaining 0%-covered chunk per Track B's real
// gcovr run, 481 lines combined).
//
// ROOT CAUSE of the original issue #17 hang (now fixed here, not a JCEF/CEF
// bug -- two separate test-technique bugs, both confirmed via standalone
// repro/reference-source reading rather than guessing):
//
// 1. The synthetic MouseEvent's |modifiers| constructor argument was passed
//    as 0. native/CefBrowser_N.cpp's GetCefModifiers() reads
//    getModifiersEx() (the *extended* modifier mask), not the deprecated
//    getModifiers() -- and MouseEvent's legacy constructor does NOT
//    auto-populate getModifiersEx() from the |button| argument alone.
//    Confirmed with a standalone repro: modifiers=0 leaves getModifiersEx()
//    at 0 even though paramString()/getButton() both still correctly report
//    BUTTON3. Fix: pass InputEvent.BUTTON3_DOWN_MASK as |modifiers|.
//
// 2. Real interactive use gives the OSR canvas AWT input focus before any
//    click reaches it -- CefBrowserOsr registers a FocusListener that calls
//    browser.setFocus(true) (-> CEF's own internal WebContents focus) on
//    FOCUS_GAINED. Confirmed by reading CEF's own reference OSR
//    implementation (~/devel/cef/tests/cefclient/browser/
//    browser_window_osr_gtk.cc's ClickEvent()), which calls
//    gtk_widget_grab_focus() on every mouse-down before forwarding the
//    click. Component.dispatchEvent() bypasses AWT's normal focus-transfer
//    machinery entirely (no FOCUS_GAINED event is synthesized), so
//    browser.setFocus(true) must be called directly to match what a real
//    click would produce.
//
// With both fixed, onBeforeContextMenu does fire -- but then CEF creates a
// REAL native (GTK) context-menu popup, which never gets dismissed in this
// headless Xvfb environment (no window manager / user to click it away),
// blocking teardown indefinitely -- the same "modal native UI blocks
// teardown" hang class as issue #12. CEF's documented idiom for suppressing
// the actual popup while still receiving the callback is clearing the menu
// model (an empty model has nothing to show), so onBeforeContextMenu calls
// model_.clear() before returning -- this is done unconditionally, not
// gated on assertion results, specifically to guarantee the popup never
// gets a chance to appear even if something above it throws first. All
// assertions happen on primitives captured from callback-thread values, per
// this suite's established JNI-callback-thread-exception-safety pattern
// (asserting directly inside a native callback can corrupt later browser
// teardown).
@ExtendWith(TestSetupExtension.class)
class CefContextMenuTest {
    private static final String TEST_URL = "http://test.com/context_menu.html";
    private static final String CONTENT =
            "<html><body style='margin:0'><div style='width:800px;height:600px'>"
            + "right-click target</div></body></html>";

    @Test
    void onBeforeContextMenuFiresOnSyntheticRightClick() {
        boolean[] gotMenu = {false};
        int[] xCoord = {-1};
        int[] yCoord = {-1};
        int[] typeFlags = {-1};
        boolean[] modelClearedOk = {false};
        int[] countAfterClear = {-1};

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                addResource(TEST_URL, CONTENT, "text/html");

                client_.addContextMenuHandler(new CefContextMenuHandler() {
                    @Override
                    public void onBeforeContextMenu(CefBrowser browser, CefFrame frame,
                            CefContextMenuParams params_, CefMenuModel model_) {
                        if (gotMenu[0]) return;
                        gotMenu[0] = true;

                        xCoord[0] = params_.getXCoord();
                        yCoord[0] = params_.getYCoord();
                        typeFlags[0] = params_.getTypeFlags();

                        // Suppress the real native popup unconditionally --
                        // see the class-level comment above.
                        modelClearedOk[0] = model_.clear();
                        countAfterClear[0] = model_.getCount();

                        terminateTest();
                    }

                    @Override
                    public boolean onContextMenuCommand(CefBrowser browser, CefFrame frame,
                            CefContextMenuParams params, int commandId, int eventFlags) {
                        return false;
                    }

                    @Override
                    public void onContextMenuDismissed(CefBrowser browser, CefFrame frame) {}
                });

                createBrowser(TEST_URL, true /* useOSR */);
                super.setupTest();
            }

            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                    boolean canGoBack, boolean canGoForward) {
                if (isLoading || gotMenu[0]) return;
                Component canvas = browser.getUIComponent();
                int x = 50;
                int y = 50;
                browser.setFocus(true);
                // Delay the synthetic click: the OSR GL surface may not be
                // realized/painted yet immediately after onLoadingStateChange
                // fires (this callback runs right as the load completes, not
                // after a real paint cycle) -- give it a moment before
                // dispatching, matching how a real user's click would always
                // land well after the surface is already visible.
                new javax.swing.Timer(500, ev -> {
                    long now = System.currentTimeMillis();
                    canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, now,
                            InputEvent.BUTTON3_DOWN_MASK, x, y, 1, /* popupTrigger= */ true,
                            MouseEvent.BUTTON3));
                    canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED,
                            now + 1, 0, x, y, 1, /* popupTrigger= */ true, MouseEvent.BUTTON3));
                }) {
                    { setRepeats(false); }
                }.start();
            }
        };

        frame.awaitCompletion();

        assertTrue(gotMenu[0], "onBeforeContextMenu was never invoked");
        assertTrue(xCoord[0] >= 0, "Unexpected xCoord: " + xCoord[0]);
        assertTrue(yCoord[0] >= 0, "Unexpected yCoord: " + yCoord[0]);
        assertTrue(typeFlags[0] >= 0, "Unexpected typeFlags: " + typeFlags[0]);
        assertTrue(modelClearedOk[0], "CefMenuModel.clear() reported failure");
        assertTrue(countAfterClear[0] == 0, "Model should be empty after clear(): "
                + countAfterClear[0]);
    }
}
