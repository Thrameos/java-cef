// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.callback.CefFileDialogCallback;
import org.cef.handler.CefDialogHandler;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Vector;

// Exercises CefDialogHandler.onFileDialog (native/dialog_handler.cpp,
// CefFileDialogCallback_N.cpp -- distinct from the Windows-only
// run_file_dialog_callback.cpp, which is CefBrowserHost.runFileDialog()
// triggered programmatically from Java, not by the page). Registering a
// CefDialogHandler and returning true from onFileDialog intercepts the
// request before any real native dialog is shown, so this is safe even
// headless.
//
// ROOT CAUSE of the original finding (a JS-synthesized .click() on the
// <input type=file> never triggered onFileDialog): not a JCEF/CEF bug, and
// not fundamentally the same "no real user gesture" limitation as issue #11
// -- it's specifically that Chromium deliberately blocks *script*-triggered
// clicks from opening a file picker (an anti-abuse security policy, by
// design). The fix used for issue #17's synthetic right-click (a real AWT
// MouseEvent dispatched to the OSR canvas, not element.click()) applies
// here too: dispatch a real synthetic click at the file input's on-screen
// position instead of calling .click() from JS.
//
// @Disabled: tried it. Same technique (real MouseEvent, correct
// InputEvent.BUTTON1_DOWN_MASK modifiers, browser.setFocus(true), a 500ms
// delay for the OSR surface to paint) that fully fixed issue #17 does NOT
// fix this one -- confirmed via an isolated run that needed a hard SIGKILL
// past a 45s external timeout (no clean 30s-watchdog recovery this time,
// unlike the JS-.click() version's original clean failure). So this isn't
// simply "wrong technique" the way #17/#18 were -- either a real native
// file-picker widget is genuinely opening and blocking (GTK file chooser
// needs a real desktop/window-manager interaction this headless Xvfb
// environment can't provide, even with onFileDialog returning true +
// callback.Cancel() -- unlike context menu's model.clear(), there may be no
// way to suppress the dialog before it's shown), or file-picker triggering
// has additional requirements beyond correct mouse-event mechanics. Not
// investigated further -- same dead-end class as #11, but now confirmed to
// risk a genuine unrecoverable hang (worse than previously known) rather
// than a clean 15s-watchdog failure.
@Disabled("Real synthetic click (same technique that fixed #17) still "
        + "risks an unrecoverable hang here -- see Thrameos/java-cef#11")
@ExtendWith(TestSetupExtension.class)
class CefDialogHandlerTest {
    private static final String TEST_URL = "http://test.com/file_dialog.html";
    private static final String CONTENT = "<html><body style='margin:0'>"
            + "<input type='file' id='f' style='position:absolute;left:10px;top:10px;"
            + "width:200px;height:40px;'>"
            + "</body></html>";

    @Test
    void onFileDialogFiresOnRealSyntheticClick() {
        boolean[] gotFileDialog = {false};

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                addResource(TEST_URL, CONTENT, "text/html");

                client_.addDialogHandler(new CefDialogHandler() {
                    @Override
                    public boolean onFileDialog(CefBrowser browser, FileDialogMode mode,
                            String title, String defaultFilePath, Vector<String> acceptFilters,
                            Vector<String> acceptExtensions, Vector<String> acceptDescriptions,
                            CefFileDialogCallback callback) {
                        if (gotFileDialog[0]) return true;
                        gotFileDialog[0] = true;
                        callback.Cancel();
                        terminateTest();
                        return true;
                    }
                });

                createBrowser(TEST_URL, true /* useOSR */);
                super.setupTest();
            }

            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                    boolean canGoBack, boolean canGoForward) {
                if (isLoading || gotFileDialog[0]) return;
                Component canvas = browser.getUIComponent();
                int x = 50;
                int y = 25;
                browser.setFocus(true);
                // Same technique as issue #17's context-menu fix: a short
                // delay for the OSR surface to realize/paint before
                // dispatching, and InputEvent.BUTTON1_DOWN_MASK (not 0) so
                // getModifiersEx() reports the button correctly.
                new javax.swing.Timer(500, ev -> {
                    long now = System.currentTimeMillis();
                    canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, now,
                            InputEvent.BUTTON1_DOWN_MASK, x, y, 1, /* popupTrigger= */ false,
                            MouseEvent.BUTTON1));
                    canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED,
                            now + 1, 0, x, y, 1, /* popupTrigger= */ false, MouseEvent.BUTTON1));
                    canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED,
                            now + 2, 0, x, y, 1, /* popupTrigger= */ false, MouseEvent.BUTTON1));
                }) {
                    { setRepeats(false); }
                }.start();
            }
        };

        frame.awaitCompletion();

        assertTrue(gotFileDialog[0], "onFileDialog was never invoked");
    }
}
