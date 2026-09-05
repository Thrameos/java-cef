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
import java.util.concurrent.CountDownLatch;

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
// Migrated to the shared-browser (Tier 1) harness for the enabled test
// below -- see plan/roadmap.md's "two-tier test harness" entry. The
// @Disabled test further down stays on TestFrame unchanged (both can
// coexist in one class: TestFrame is just instantiated directly, not tied
// to the extension).
@ExtendWith({TestSetupExtension.class, SharedBrowserExtension.class})
class CefPrintHandlerTest {
    private static final String CONTENT = "<html><body>print handler test</body></html>";
    // Only used by the @Disabled test below (which stays on TestFrame).
    private static final String TEST_URL = "http://test.com/print_handler.html";

    @Test
    void printToPDFWritesARealFileAndInvokesCompletionCallback() throws IOException {
        boolean[] gotFinished = {false};
        boolean[] finishedOk = {false};
        CountDownLatch done = new CountDownLatch(1);

        Path outFile = Files.createTempFile("jcef-print-handler-test", ".pdf");
        Files.delete(outFile);

        SharedBrowserExtension.addPrintHandler(new CefPrintHandlerAdapter() {});
        SharedBrowserExtension.loadPage(CONTENT);

        CefBrowser browser = SharedBrowserExtension.browser();
        try {
            browser.printToPDF(outFile.toString(), new CefPdfPrintSettings(), (path, ok) -> {
                gotFinished[0] = true;
                finishedOk[0] = ok;
                done.countDown();
            });
            SharedBrowserExtension.awaitLatch(done, 15);

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
    // wrapped in a hard external `timeout -k 5 45` (SIGTERM then SIGKILL).
    // Filed as Thrameos/java-cef#12 (also covers the analogous DevTools
    // hang, see plan/roadmap.md).
    //
    // ROOT-CAUSE ATTEMPT (this session): the original v1 of this test called
    // terminateTest() (which begins browser/window teardown) *from inside*
    // onPrintDialog(), before returning. Per CEF's own source
    // (~/devel/cef/libcef/browser/printing/print_dialog_linux.cc's
    // ShowDialog(): "if (!handler_->OnPrintDialog(...)) { callback_impl->
    // Disconnect(); OnPrintCancel(); }"), the native print pipeline expects
    // to synchronously continue its own cleanup *after* this Java call
    // returns false -- tearing down the browser while still inside that
    // call stack looked like a plausible re-entrancy hazard, matching the
    // "wrong technique, not a real bug" pattern found for issues #17/#18.
    //
    // Tried deferring terminateTest() to onPrintReset() instead (called
    // from CefPrintDialogLinux's own destructor, after the print pipeline
    // has unwound). Result: genuinely **intermittent**, not a clean fix --
    // one isolated run recovered cleanly via the normal 30s watchdog (exit
    // 134, no SIGKILL needed); the very next isolated run of the identical
    // test needed a hard SIGKILL past a 45s external timeout with zero
    // diagnostic output (added System.out.println to each callback --
    // *none* fired that time, meaning it hangs somewhere before even
    // onPrintStart in some runs). So the re-entrancy fix is a real
    // improvement (it eliminates one guaranteed-hang code path) but does
    // NOT reliably fix the underlying issue -- there's a second,
    // independent, timing-dependent hang further upstream in CEF's own
    // print pipeline (a real CUPS daemon is running in this environment,
    // confirmed via `systemctl status cups`, but with zero printers
    // configured -- a plausible but unconfirmed trigger). Kept the
    // onPrintReset()-based fix since it's strictly safer than the
    // original, but @Disabled stays on until the remaining intermittent
    // hang is understood.
    @Disabled("Genuinely intermittent UNRECOVERABLE hang -- see "
            + "Thrameos/java-cef#12. Do not run without a hard external "
            + "timeout wrapper (e.g. `timeout -k 5 45`), and do not remove "
            + "@Disabled as part of a normal suite run.")
    @Test
    void browserPrintInvokesPrintStartSettingsAndDialog() {
        boolean[] gotPrintStart = {false};
        boolean[] gotPrintSettings = {false};
        boolean[] gotPrintDialog = {false};
        boolean[] gotPrintReset = {false};

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
                        // Cancel immediately -- no real dialog is shown. Do
                        // NOT tear the browser down from inside this call;
                        // see onPrintReset() below.
                        return false;
                    }

                    @Override
                    public void onPrintReset(CefBrowser browser) {
                        if (gotPrintReset[0]) return;
                        gotPrintReset[0] = true;
                        terminateTest();
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
        assertTrue(gotPrintReset[0], "onPrintReset was never invoked");
    }
}
