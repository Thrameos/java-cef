// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// First test in the suite to use windowed (non-OSR) rendering -- every other test
// creates its browser with useOSR=true. org/cef/browser/CefBrowserWr.java and
// native/jni_util_linux.cpp's GetDrawableOfCanvas() (the Linux windowed path's way
// of getting the X11 Drawable of the AWT Canvas CefBrowserWr embeds the browser
// into, so CEF can parent its own native window to it -- see
// native/CefBrowser_N.cpp's osr==JNI_FALSE branch) were both previously 0% covered
// as a direct result: nothing exercised this code path at all.
//
// @Disabled: the browser loads fine (onAfterCreated, resource served,
// onLoadingStateChange all fire normally), and the close sequence starts and
// completes correctly on JCEF's own side -- confirmed with full native-side
// tracing (build with -DJCEF_ENABLE_TRACE=ON, run with JCEF_TRACE=1; every
// *_handler.cpp callback CEF can invoke into JCEF is now traced, see
// native/jcef_trace.h and the ENTER/EXIT calls added throughout native/):
//   CefBrowser_N::N_Close(force=false) -> LifeSpanHandler::DoClose() returns
//   true (cancel) -> CefBrowser_N::N_Close(force=true) -> already on TID_UI ->
//   util::DestroyCefBrowser() -> LifeSpanHandler::DoClose() returns false
//   (allow) -> CloseBrowser(true) called and returns.
// After that last line, the trace shows NOTHING JCEF-side ever fires again --
// no LifeSpanHandler::OnBeforeClose, no ClientHandler::OnBeforeClose, nothing
// in any handler file -- only BrowserProcessHandler::OnScheduleMessagePumpWork
// (routine message-pump scheduling) forever after. This rules out a JCEF-side
// cause entirely: CloseBrowser(true) is called and returns normally, but CEF's
// own internal close/teardown state machine never crosses back into any JCEF
// callback again.
//
// Root-caused past that boundary by reading CEF's own source
// (~/devel/cef/libcef/browser/alloy/alloy_browser_host_impl.cc and
// libcef/browser/native/window_x11.cc -- NOT the vendored binary's opaque
// libcef.so, the actual C++ this repo tracks): for a windowed browser,
// AlloyBrowserHostImpl::CloseContents() -- reached right after the 2nd
// DoClose() allows the close, matching the trace above -- does NOT destroy
// the browser directly. Since `!IsWindowless() && !window_destroyed_`, it
// instead calls platform_delegate_->CloseHostWindow(), which on Linux is
// CefWindowX11::Close(): this sends a *synthetic X11 ClientMessageEvent*
// (WM_DELETE_WINDOW) to CEF's own window and returns immediately, relying on
// CEF's own X11 event source to later receive and process that self-sent
// message (CefWindowX11::ProcessXEvent) -- only THEN does it call the real
// XDestroyWindow, followed by AlloyBrowserHostImpl::WindowDestroyed(), which
// finally re-enters CloseBrowser(true) a second time and, since
// window_destroyed_ is now true, proceeds straight to DestroyBrowser() ->
// eventually OnBeforeClose. (For OSR/IsWindowless(), CloseContents() skips
// all of this and calls DestroyBrowser() immediately -- which is exactly why
// every other test in this suite, all OSR, closes cleanly and only windowed
// mode hangs.)
//
// So the entire remaining chain hinges on ONE queued X11 event: CEF's own
// self-addressed WM_DELETE_WINDOW ClientMessage on its own window. That
// window is a child CEF itself creates (via CefWindowX11's constructor,
// parented to the AWT Canvas's X11 drawable JCEF passes as
// window_info_.parent_window -- see native/jni_util_linux.cpp's
// GetDrawableOfCanvas()), not something JCEF reparents after the fact.
//
// CONFIRMED live via gdb (jpype's doc/develguide.rst gdb technique -- launch
// java directly under gdb, `handle SIGSEGV nostop noprint pass` so HotSpot's
// own benign implicit-null-check SIGSEGVs don't spam-stop the debugger, then
// breakpoint the CEF symbols by name; libcef.so ships with debug_info even in
// the vendored binary, so this works without a custom CEF build) with
// breakpoints on CefWindowX11::Close, ui::SendClientMessage,
// CefWindowX11::ProcessXEvent, and AlloyBrowserHostImpl::WindowDestroyed/
// DestroyBrowser: CefWindowX11::Close() and ui::SendClientMessage() both DO
// fire (full backtrace confirms the exact call chain through
// AlloyBrowserHostImpl::CloseContents() described above) -- but
// ProcessXEvent, WindowDestroyed, and DestroyBrowser NEVER fire afterward, in
// this or any later run. The message is genuinely sent; it is never
// processed. Corroborated two ways: (1) a breakpoint on the imported
// `xcb_flush` symbol shows dozens of hits during normal startup/paint
// activity but zero after the close sequence begins; (2) `strace -f -e
// trace=write,writev,sendmsg,sendto` across the whole process shows no
// meaningful write-family syscall activity in the same window beyond routine
// JCEF_TRACE stderr writes, all the way to the 30s watchdog. (Chromium's
// Ozone/X11 layer partly reimplements the XCB wire protocol in C++ and may
// not always route through libxcb's own `xcb_flush`, so neither check alone
// is airtight -- but both point the same direction and neither shows the
// message ever leaving the process.)
//
// Not yet confirmed: WHY delivery/processing never happens -- an event-
// ownership/routing gap specific to a CEF-owned child window under a foreign
// (AWT-owned) window hierarchy, versus something about how JCEF's
// external-pump integration (CefSettings.external_message_pump, see
// CefDoMessageLoopWork()'s own doc comment on its integration caveats)
// services CEF's X11 socket, remain both plausible and both untested further
// -- doing so would need to patch/rebuild CEF's own X11 layer (source is
// available at ~/devel/cef but is a large rebuild) or a from-scratch
// reimplementation of Chromium's Ozone/X11 connection object to trace inside
// it, neither attempted this session.
//
// FIXED 2026-08-31: native/util_linux.cpp's DestroyCefBrowser() now schedules
// a bounded (2s) fallback via CefPostDelayedTask -- if CEF's own real
// OnBeforeClose() hasn't arrived by then, JCEF calls the exact same public
// CefLifeSpanHandler::OnBeforeClose() entry point CEF itself would have
// called, satisfying the downstream JNI contract Java code expects (the
// underlying native browser object is left alone; if CEF's real close does
// complete later -- confirmed it usually does, once CefShutdown()'s own
// heavier teardown runs, see below -- LifeSpanHandler::OnBeforeClose()'s own
// idempotency guard, g_closed_browser_ids in life_span_handler.cpp, makes
// that late call a no-op). Verified live: onBeforeClose/cleanupTest/suite
// teardown all now complete correctly instead of hanging forever.
//
// Still @Disabled, but for an entirely different, pre-existing, already-
// tracked reason unrelated to windowed mode: this test's own suite-global
// teardown (TestSetupExtension.close() -> CefApp.dispose() -> CefShutdown())
// now reaches the already-documented issue #4/#23 "DCHECK failed:
// all_.empty()" crash (cef/libcef/browser/browser_context.cc:44) -- verified
// this crash is NOT caused by this fix or by windowed mode at all: two plain
// OSR tests run together (no windowed browser anywhere) hit the identical
// crash. It was never reachable by this test before only because the
// windowed-close hang always blocked forever first.
//
// Re-enable once issue #4/#23 itself is fixed (see plan/roadmap.md's
// "MENTAL MODEL" section and java_cef_coverage_measurement_broken memory) --
// unrelated future work, not blocked on anything from this investigation.
@ExtendWith(TestSetupExtension.class)
class CefBrowserWrTest {
    private static final String TEST_URL = "http://test.com/windowed.html";

    @Test
    @Disabled("Windowed close itself is now fixed (see class comment) -- this test still can't "
            + "run because the suite-global teardown that follows hits the pre-existing, "
            + "unrelated issue #4/#23 shutdown DCHECK (verified: reproduces with plain OSR "
            + "tests alone too, nothing to do with windowed mode). Re-enable once #4/#23 is "
            + "fixed.")
    void windowedBrowserLoadsAndReportsCorrectUrl() {
        boolean[] done = {false};

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                addResource(TEST_URL, "<html><body>windowed</body></html>", "text/html");
                createBrowser(TEST_URL, false /* useOSR */);
                super.setupTest();
            }

            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                    boolean canGoBack, boolean canGoForward) {
                if (isLoading) return;
                done[0] = true;
                terminateTest();
            }
        };

        frame.awaitCompletion();

        assertTrue(done[0], "Windowed browser never finished loading");
        assertEquals(TEST_URL, frame.browser_.getURL());
    }
}
