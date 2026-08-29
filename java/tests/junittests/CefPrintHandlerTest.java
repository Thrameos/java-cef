// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.handler.CefPrintHandlerAdapter;
import org.cef.misc.CefPdfPrintSettings;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

// Exercises native/pdf_print_callback.cpp (0% covered per this session's baseline
// gcovr run; see plan/roadmap.md Phase 2) via CefBrowser.printToPDF().
//
// A CefPrintHandler is still registered here so native/print_handler.cpp's JNI
// bridge is constructed and wired up (ClientHandler::GetPrintHandler() /
// PrintHandler ctor), but empirically (confirmed by instrumenting this test)
// GetPdfPaperSize is never actually invoked by this CEF version's printToPDF
// pipeline for an OSR browser -- onPdfPrintFinished fires with ok=true and a real
// PDF is written regardless. print_handler.cpp's other CefPrintHandler methods
// (OnPrintStart/OnPrintSettings/OnPrintDialog/OnPrintJob) are for the
// window.print()/print-dialog path, not printToPDF -- see
// browserPrintInvokesPrintStartSettingsAndDialog() below, @Disabled, for
// that path.
@ExtendWith(TestSetupExtension.class)
class CefPrintHandlerTest {
    private static final String TEST_URL = "http://test.com/print_handler.html";
    private static final String CONTENT = "<html><body>print handler test</body></html>";

    @Test
    void printToPDFWritesARealFileAndInvokesCompletionCallback() throws IOException {
        boolean[] gotFinished = {false};
        boolean[] finishedOk = {false};

        Path outFile = Files.createTempFile("jcef-print-handler-test", ".pdf");
        Files.delete(outFile);

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                client_.addPrintHandler(new CefPrintHandlerAdapter() {});

                addResource(TEST_URL, CONTENT, "text/html");
                createBrowser(TEST_URL, true /* useOSR */);
                super.setupTest();
            }

            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                    boolean canGoBack, boolean canGoForward) {
                if (isLoading) return;
                browser.printToPDF(outFile.toString(), new CefPdfPrintSettings(),
                        (path, ok) -> {
                            gotFinished[0] = true;
                            finishedOk[0] = ok;
                            terminateTest();
                        });
            }
        };

        try {
            frame.awaitCompletion();

            assertTrue(gotFinished[0], "onPdfPrintFinished was never invoked");
            assertTrue(finishedOk[0], "PDF printing did not complete successfully");
            assertTrue(Files.exists(outFile) && Files.size(outFile) > 0,
                    "Expected a non-empty PDF file at " + outFile);
        } finally {
            Files.deleteIfExists(outFile);
        }
    }

    // @Disabled -- IMPORTANT, do not remove without extreme caution: this
    // test causes a genuine UNRECOVERABLE hang, confirmed via an isolated run
    // wrapped in a hard external `timeout -k 5 45` (SIGTERM then SIGKILL) --
    // even SIGKILL fallback was needed. TestFrame's own 30s watchdog never
    // got a chance to run. Even with onPrintDialog returning false ("cancel
    // the printing immediately" per its own Javadoc), browser.print() itself
    // never returns. Likely CEF's print pipeline blocks at the native level
    // without a real printer backend in this headless environment -- same
    // class of risk as CefRunFileDialogCallback (see plan/windows-todo.md).
    // Filed as Thrameos/java-cef#12 (also covers the analogous DevTools hang,
    // see CefDevToolsClientTest.java).
    @Disabled("UNRECOVERABLE HANG, not just a slow/bounded failure -- see "
            + "Thrameos/java-cef#12. Do not run without a hard external "
            + "timeout wrapper (e.g. `timeout -k 5 45`), and do not remove "
            + "@Disabled as part of a normal suite run.")
    @Test
    void browserPrintInvokesPrintStartSettingsAndDialog() {
        boolean[] gotPrintStart = {false};
        boolean[] gotPrintSettings = {false};
        boolean[] gotPrintDialog = {false};

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                client_.addPrintHandler(new CefPrintHandlerAdapter() {
                    @Override
                    public void onPrintStart(CefBrowser browser) {
                        gotPrintStart[0] = true;
                    }

                    @Override
                    public void onPrintSettings(CefBrowser browser,
                            org.cef.misc.CefPrintSettings settings, boolean getDefaults) {
                        gotPrintSettings[0] = true;
                    }

                    @Override
                    public boolean onPrintDialog(CefBrowser browser, boolean hasSelection,
                            org.cef.callback.CefPrintDialogCallback callback) {
                        gotPrintDialog[0] = true;
                        terminateTest();
                        // Cancel immediately -- no real dialog is shown.
                        return false;
                    }
                });

                addResource(TEST_URL, CONTENT, "text/html");
                createBrowser(TEST_URL, true /* useOSR */);
                super.setupTest();
            }

            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                    boolean canGoBack, boolean canGoForward) {
                if (!isLoading) browser.print();
            }
        };

        frame.awaitCompletion();

        assertTrue(gotPrintStart[0], "onPrintStart was never invoked");
        assertTrue(gotPrintSettings[0], "onPrintSettings was never invoked");
        assertTrue(gotPrintDialog[0], "onPrintDialog was never invoked");
    }
}
