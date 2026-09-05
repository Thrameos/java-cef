// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.cef.browser.CefBrowser;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// Reproduces the modified-UTF-8 (JNI "CESU-8") handling bug in native/jni_util.cpp.
// GetJNIString()/NewJNIString() use env->GetStringUTFChars()/NewStringUTF(), which
// produce/consume JNI *modified* UTF-8 (supplementary-plane characters encoded as a
// CESU-8 surrogate pair instead of one real UTF-8 sequence), but the result is treated
// as standard UTF-8 on both sides of the JNI boundary. Uses OSR browsers -- see
// OsrSmokeTest -- to avoid the unrelated windowed-browser close hang (plan/findings.md,
// upstream java-cef#364).
@ExtendWith(TestSetupExtension.class)
class ModifiedUtf8Test {
    // U+1F600 GRINNING FACE: outside the BMP, requires a UTF-16 surrogate pair in
    // Java and a 4-byte sequence in real UTF-8 -- exactly the case modified UTF-8
    // encodes differently (as two 3-byte CESU-8 surrogate sequences).
    private static final String ASTRAL = new String(Character.toChars(0x1F600));

    // Direction: native (CEF/V8) generates the string -> NewJNIString() -> Java.
    // The astral character is produced entirely inside JS (String.fromCodePoint);
    // this Java test process never sends it to CEF, isolating NewJNIString().
    @Test
    void nativeToJavaAstralCharacterSurvivesTitleChange() {
        final String testUrl = "http://test.com/n2j.html";
        final String[] receivedTitle = {null};

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                client_.addDisplayHandler(new CefDisplayHandlerAdapter() {
                    @Override
                    public void onTitleChange(CefBrowser browser, String title) {
                        receivedTitle[0] = title;
                    }
                });

                addResource(testUrl,
                        "<html><head><script>"
                                + "document.title = String.fromCodePoint(0x1F600);"
                                + "</script></head><body></body></html>",
                        "text/html");
                createBrowser(testUrl, true /* useOSR */);

                super.setupTest();
            }

            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                    boolean canGoBack, boolean canGoForward) {
                if (!isLoading) terminateTest();
            }
        };

        frame.awaitCompletion();

        assertEquals(ASTRAL, receivedTitle[0]);
    }

    // Direction: Java string containing the astral character -> GetJNIString() ->
    // native (CEF/V8), verified entirely inside JS so the result never has to cross
    // back through NewJNIString() -- isolating GetJNIString().
    @Test
    void javaToNativeAstralCharacterSurvivesExecuteJavaScript() {
        final String testUrl = "http://test.com/j2n.html";
        final String[] receivedTitle = {null};
        final int expectedCodePoint = ASTRAL.codePointAt(0);

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                client_.addDisplayHandler(new CefDisplayHandlerAdapter() {
                    @Override
                    public void onTitleChange(CefBrowser browser, String title) {
                        receivedTitle[0] = title;
                        terminateTest();
                    }
                });

                addResource(testUrl, "<html><body></body></html>", "text/html");
                createBrowser(testUrl, true /* useOSR */);

                super.setupTest();
            }

            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                    boolean canGoBack, boolean canGoForward) {
                if (!isLoading) {
                    String code = "document.title = ('" + ASTRAL + "'.codePointAt(0) === "
                            + expectedCodePoint + ") ? 'PASS' : 'FAIL:' + '" + ASTRAL
                            + "'.codePointAt(0).toString(16);";
                    browser.executeJavaScript(code, testUrl, 0);
                }
            }
        };

        frame.awaitCompletion();

        assertEquals("PASS", receivedTitle[0]);
    }
}
