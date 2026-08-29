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

import java.util.Vector;
import java.util.concurrent.TimeUnit;

// Exercises CefDialogHandler.onFileDialog (native/dialog_handler.cpp,
// CefFileDialogCallback_N.cpp -- distinct from the Windows-only
// run_file_dialog_callback.cpp, which is CefBrowserHost.runFileDialog()
// triggered programmatically from Java, not by the page). Registering a
// CefDialogHandler and returning true from onFileDialog intercepts the
// request before any real native dialog is shown, so this should be safe
// even headless. Triggered via a page-side JS .click() on a real
// <input type=file> element.
//
// @Disabled: confirmed by running this test un-@Disabled that it currently
// fails cleanly (via TestFrame's 15s watchdog, not a hang) -- onFileDialog
// is never invoked. Consistent with the same script-doesn't-count-as-a-real-
// user-gesture pattern documented for onBeforePopup (issue #11) and the
// CefDownloadItem finding (see CefDownloadItemTest.java in this same file
// set) -- a JS-synthesized .click() on a file input likely doesn't carry
// the real user gesture CEF's file-dialog trigger requires. No fork issue
// filed separately since this is the same underlying limitation as #11,
// not a new distinct bug.
@Disabled("Script-synthesized .click() on a file input doesn't trigger "
        + "onFileDialog -- same user-gesture-requirement class as issue #11 "
        + "(onBeforePopup)")
@ExtendWith(TestSetupExtension.class)
class CefDialogHandlerTest {
    private static final String TEST_URL = "http://test.com/file_dialog.html";
    private static final String CONTENT = "<html><body>"
            + "<input type='file' id='f'>"
            + "<script>document.getElementById('f').click();</script>"
            + "</body></html>";

    @Test
    void onFileDialogFiresOnFileInputClick() {
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
        };

        frame.awaitCompletion(15, TimeUnit.SECONDS);

        assertTrue(gotFileDialog[0], "onFileDialog was never invoked");
    }
}
