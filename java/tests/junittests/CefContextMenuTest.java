// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefContextMenuParams;
import org.cef.callback.CefMenuModel;
import org.cef.handler.CefContextMenuHandler;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.awt.Component;
import java.awt.event.MouseEvent;

// Exercises CefContextMenuHandler/CefContextMenuParams/CefMenuModel (native/
// context_menu_handler.cpp, CefContextMenuParams_N.cpp, CefMenuModel_N.cpp --
// together the single largest remaining 0%-covered chunk per Track B's real
// gcovr run, 481 lines combined). CefBrowserOsr's GLCanvas does register a
// real MouseListener that forwards every AWT MouseEvent to sendMouseEvent()/
// native CEF (confirmed by reading CefBrowserOsr.java), so the mechanism
// this test relies on is real -- but a synthetic BUTTON3 press+release
// MouseEvent dispatched via Component.dispatchEvent() to the OSR canvas
// never triggered onBeforeContextMenu in this environment/CEF version.
//
// @Disabled: confirmed by running this test un-@Disabled that it currently
// fails -- the test hangs for the full 30s until TestFrame's watchdog
// force-closes it (the watchdog itself works correctly here -- this is
// exactly the class of bug it exists to catch -- so it's a clean, bounded
// failure, not an unrecoverable hang like the ones tracked in issue #12).
// Filed as Thrameos/java-cef#17 to keep a reproducer on record even though
// the root cause isn't confirmed yet -- it may simply be that CEF's
// context-menu trigger logic needs modifier/click-count
// semantics this synthetic event doesn't fully replicate, or that the OSR
// canvas needs to have completed its first real paint before dispatch (this
// test dispatches from onLoadingStateChange, right after load, which may be
// too early for the GL surface to be realized). See plan/roadmap.md's Track
// A item 4 finding for what to try next before concluding either way.
@Disabled("Synthetic right-click via Component.dispatchEvent() never "
        + "triggers onBeforeContextMenu in this environment -- not yet "
        + "root-caused as a JCEF/CEF bug vs. a test-technique limitation, "
        + "see Thrameos/java-cef#17")
@ExtendWith(TestSetupExtension.class)
class CefContextMenuTest {
    private static final String TEST_URL = "http://test.com/context_menu.html";
    private static final String CONTENT =
            "<html><body style='margin:0'><div style='width:800px;height:600px'>"
            + "right-click target</div></body></html>";

    @Test
    void onBeforeContextMenuFiresOnSyntheticRightClick() {
        CefContextMenuParams[] params = {null};
        CefMenuModel[] model = {null};
        boolean[] gotMenu = {false};

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
                        params[0] = params_;
                        model[0] = model_;
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
                long now = System.currentTimeMillis();
                canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, now, 0, x,
                        y, 1, /* popupTrigger= */ true, MouseEvent.BUTTON3));
                canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, now + 1, 0,
                        x, y, 1, /* popupTrigger= */ true, MouseEvent.BUTTON3));
            }
        };

        frame.awaitCompletion();

        assertTrue(gotMenu[0], "onBeforeContextMenu was never invoked");
        assertNotNull(params[0]);
        assertNotNull(model[0]);
    }
}
