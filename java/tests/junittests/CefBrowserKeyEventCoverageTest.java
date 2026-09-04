// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import javax.swing.SwingUtilities;

// CefBrowser_N.cpp's N_SendKeyEvent() feeds every non-special key through
// KeyboardCodeFromXKeysym() (native/CefBrowser_N.cpp, ~lines 271-548), a
// ~180-case switch over X11 keysym values -- by far the single largest
// coverage gap in the codebase (see plan/roadmap.md's coverage-gap
// tracking): only 9 keys (BackSpace/Delete/Down/Enter/Escape/Left/Right/
// Tab/Up) are ever explicitly mapped by java keyCode
// (N_SendKeyEvent's VK_* checks); every other key falls through to using
// the raw Java keyChar value AS the X11 keysym directly (native/
// CefBrowser_N.cpp's `cef_event.native_key_code = key_char;` fallback) --
// valid because X11 keysyms for printable ASCII/Latin-1 characters and for
// the function/modifier-key range (0xFF00-0xFFFF, e.g. XK_F1, XK_Shift_L)
// both fit directly in a 16-bit Java char. So a switch case can be
// exercised from pure Java by constructing a KeyEvent whose keyChar
// numerically equals the target X11 keysym constant -- no native code
// changes or a real physical keyboard needed. KEYSYMS below is every
// case label the switch actually has (case XK_display; extracted from the
// switch source, cross-referenced against /usr/include/X11/keysymdef.h
// and XF86keysym.h for the real numeric values), so this one test method
// exercises essentially the entire switch.
@ExtendWith(TestSetupExtension.class)
class CefBrowserKeyEventCoverageTest {
    private static final String TEST_URL = "http://test.com/browser_key_event_coverage.html";
    private static final String CONTENT =
            "<html><body>key event coverage</body></html>";

    // Every case label in KeyboardCodeFromXKeysym(), as real X11 keysym
    // values (from keysymdef.h/XF86keysym.h) -- not guessed. All fit in 16
    // bits, so each can be used directly as a KeyEvent's keyChar.
    private static final int[] KEYSYMS = {0x0030, 0x0031, 0x0032, 0x0033, 0xfd05, 0x0034, 0x0035,
            0x0036, 0x0037, 0x0038, 0x0039, 0x0041, 0xffe9, 0xffea, 0x0042, 0xff08, 0x0043, 0xffe5,
            0xff0b, 0xffe3, 0xffe4, 0x0044, 0xffff, 0xff54, 0x0045, 0xff57, 0xff1b, 0xff62, 0x0046,
            0xffbe, 0x0047, 0x0048, 0xff31, 0xff34, 0xff6a, 0xff23, 0xff50, 0x0049, 0xfe34, 0xfe20,
            0xfe03, 0xfe11, 0xff63, 0x004a, 0x004b, 0xffb0, 0xffb1, 0xffb2, 0xffb3, 0xffb4, 0xffb5,
            0xffb6, 0xffb7, 0xffb8, 0xffb9, 0xffab, 0xff9d, 0xffae, 0xff9f, 0xffaf, 0xff99, 0xff9c,
            0xff8d, 0xffbd, 0xff95, 0xff9e, 0xff96, 0xffaa, 0xff9b, 0xff9a, 0xff98, 0xffac, 0xff80,
            0xffad, 0xff89, 0xff97, 0xff2d, 0xff2e, 0xff21, 0x004c, 0xff51, 0xff0a, 0x004d, 0xff67,
            0xffe7, 0xffe8, 0xff22, 0xff20, 0x004e, 0xff7f, 0x004f, 0x0050, 0xff56, 0xff55, 0xff13,
            0xff61, 0x0051, 0x0052, 0xff0d, 0xff53, 0x0053, 0xff14, 0xff60, 0xffe1, 0xffe2, 0xffeb,
            0xffec, 0x0054, 0xff09, 0x0055, 0xff52, 0x0056, 0x0057, 0x0058, 0x0059, 0x005a, 0xff2a,
            0x0061, 0x0026, 0x005e, 0x007e, 0x002a, 0x0040, 0x0062, 0x005c, 0x007c, 0x007b, 0x007d,
            0x005b, 0x005d, 0x0063, 0x003a, 0x002c, 0x0064, 0x0024, 0x0065, 0x003d, 0x0021, 0x0066,
            0x0067, 0x003e, 0x0068, 0x0069, 0x006a, 0x006b, 0x006c, 0x003c, 0x006d, 0x002d, 0x00d7,
            0x006e, 0x0023, 0x006f, 0x0070, 0x0028, 0x0029, 0x0025, 0x002e, 0x002b, 0x0071, 0x003f,
            0x0022, 0x0060, 0x0027, 0x0072, 0x0073, 0x003b, 0x002f, 0x0020, 0x0074, 0x0075, 0x005f,
            0x0076, 0x0077, 0x0078, 0x0079, 0x007a};

    // The 9 keys N_SendKeyEvent() maps explicitly by java.awt.event.KeyEvent
    // keyCode (not keyChar) before ever reaching KeyboardCodeFromXKeysym --
    // a separate code path (the VK_BACK_SPACE/.../VK_UP if/else chain) from
    // the keysym-fallback path KEYSYMS above exercises, so needs its own
    // coverage.
    private static final int[] EXPLICIT_VK_CODES = {KeyEvent.VK_BACK_SPACE, KeyEvent.VK_DELETE,
            KeyEvent.VK_DOWN, KeyEvent.VK_ENTER, KeyEvent.VK_ESCAPE, KeyEvent.VK_LEFT,
            KeyEvent.VK_RIGHT, KeyEvent.VK_TAB, KeyEvent.VK_UP};

    @Test
    void sendKeyEventCoversKeyboardCodeFromXKeysymSwitch() {
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
                terminateTest();
            }
        };

        frame.awaitCompletion();

        CefBrowser browser = frame.browser_;
        Component canvas = browser.getUIComponent();
        KeyListener[] listeners = canvas.getKeyListeners();
        assertTrue(listeners.length > 0,
                "canvas.getKeyListeners() returned none -- sendKeyEvent() path unreachable");

        // Dispatch on the EDT, like every real key event -- CefApp's own
        // internal message-pump Timer (doMessageLoopWork()'s self-
        // perpetuating javax.swing.Timer chain, see CefApp.java) also runs
        // on the EDT, calling into the same native CEF entry points this
        // test exercises. Calling KeyListener.keyPressed() directly from
        // this test method's own thread (JUnit's main thread, not the EDT)
        // would run those two native call streams concurrently from two
        // different OS threads -- a real thread-safety violation no actual
        // caller ever produces (AWT always delivers KeyListener callbacks
        // on the EDT). Kept as the correct, realistic way to drive this
        // test regardless: while developing it, an intermittent SIGSEGV
        // inside Context::DoMessageLoopWork() showed up in this
        // Debug/coverage build (bisected down to a pre-existing, timing-
        // dependent race unrelated to this test's own dispatch thread or
        // event volume -- reproduces even with zero events sent, and not
        // on every run of the exact same test), see
        // plan/roadmap.md/plan/findings.md for that investigation; harmless
        // to this test's own correctness either way since the crash-
        // resilient gcov flush (native/CoverageTestHelper.cpp) preserves
        // this test's coverage contribution even on the runs that do hit
        // it.
        assertDoesNotThrow(() -> {
            for (KeyListener listener : listeners) {
                for (int keysym : KEYSYMS) {
                    // keyCode intentionally not one of the 9 explicit
                    // VK_* values below, so N_SendKeyEvent's fallback path
                    // (native_key_code = key_char) is what's exercised.
                    KeyEvent pressed = new KeyEvent(canvas, KeyEvent.KEY_PRESSED,
                            System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, (char) keysym);
                    SwingUtilities.invokeAndWait(() -> listener.keyPressed(pressed));
                }
                for (int vkCode : EXPLICIT_VK_CODES) {
                    KeyEvent pressed = new KeyEvent(canvas, KeyEvent.KEY_PRESSED,
                            System.currentTimeMillis(), 0, vkCode, KeyEvent.CHAR_UNDEFINED);
                    SwingUtilities.invokeAndWait(() -> listener.keyPressed(pressed));
                }
            }
        }, "sendKeyEvent() should not throw for any keysym in KEYSYMS/EXPLICIT_VK_CODES");
    }
}
