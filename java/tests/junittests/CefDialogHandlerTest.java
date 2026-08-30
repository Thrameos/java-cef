// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.callback.CefFileDialogCallback;
import org.cef.callback.CefRunFileDialogCallback;
import org.cef.handler.CefDialogHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Vector;

// Exercises CefDialogHandler.onFileDialog (native/dialog_handler.cpp,
// CefFileDialogCallback_N.cpp) and CefBrowser.runFileDialog() (native/
// run_file_dialog_callback.cpp).
//
// ROOT CAUSE of the original findings (a JS-synthesized .click() never
// triggered onFileDialog at all; a real synthetic AWT MouseEvent triggered
// it but then needed a hard SIGKILL): both were the wrong trigger
// mechanism. Confirmed by reading CEF's own internal test suite
// (~/devel/cef/tests/ceftests/dialog_unittest.cc's DialogTestHandler,
// exercised by TEST(DialogTest, FileOpen) et al., which run on Linux too):
// CEF's own maintainers don't simulate a click on a page-side <input
// type=file> at all -- they call CefBrowserHost::RunFileDialog() (the C++
// equivalent of CefBrowser.runFileDialog()) directly, which routes through
// CefDialogHandler::OnFileDialog the same way a real click would, without
// any of the input-simulation fragility. Registering a CefDialogHandler
// and returning true + calling callback.Cancel() from onFileDialog
// intercepts the request before any real native dialog is shown (exactly
// CEF's own test's pattern too), so this is safe even headless -- no
// SIGKILL risk this way, confirmed via isolated runs.
@ExtendWith(TestSetupExtension.class)
class CefDialogHandlerTest {
    private static final String TEST_URL = "http://test.com/file_dialog.html";
    private static final String CONTENT = "<html><body>file dialog test</body></html>";

    @Test
    void onFileDialogFiresForRunFileDialog() {
        boolean[] gotFileDialog = {false};
        boolean[] gotDismissed = {false};

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
                browser.runFileDialog(CefDialogHandler.FileDialogMode.FILE_DIALOG_OPEN,
                        "Test Title", "", new Vector<>(), 0, filePaths -> {
                            if (gotDismissed[0]) return;
                            gotDismissed[0] = true;
                            terminateTest();
                        });
            }
        };

        frame.awaitCompletion();

        assertTrue(gotFileDialog[0], "onFileDialog was never invoked");
        assertTrue(gotDismissed[0], "onFileDialogDismissed (CefRunFileDialogCallback) was "
                + "never invoked");
    }
}
