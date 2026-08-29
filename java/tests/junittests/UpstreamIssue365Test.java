// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandler.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.TimeUnit;

// Regression-guard test for upstream chromiumembedded/java-cef#365:
// navigating directly to a registered-but-unhandled custom (non-standard)
// scheme -- one added via CefSchemeRegistrar but never given a
// CefSchemeHandlerFactory -- should fail gracefully with
// CefLoadHandler.onLoadError(), same as any other unhandled/failed
// navigation (see LoadErrorTest for the analogous http:// case). Multiple
// upstream reporters, across several CEF versions (80.x through 109.x),
// found onLoadError was never called for this specific case, with a raw
// Mojo network-service validation error logged instead.
//
// CONFIRMED FIXED as of this fork's current CEF version (146.0.10) -- this
// test passes. No fork issue filed (nothing currently broken to track); kept
// in the suite as a regression guard in case this ever breaks again, and as
// a record of having checked. See plan/roadmap.md's upstream-issue-harvesting
// section.
//
// Uses TestSetupExtension's "jceftestscheme" custom scheme (registered once
// at CefApp startup for CefSchemeRegistrarTest, see TestSetupExtension.java)
// -- it is a real, registered, non-standard scheme with no factory ever
// attached to it, exactly matching the upstream repro shape.
@ExtendWith(TestSetupExtension.class)
class UpstreamIssue365Test {
    private static final String TEST_URL = "jceftestscheme://test/missing-page";

    @Test
    void unhandledCustomSchemeNavigationInvokesOnLoadError() {
        boolean[] gotError = {false};
        ErrorCode[] errorCode = {null};

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                // Deliberately do NOT register a CefSchemeHandlerFactory for
                // "jceftestscheme" -- the scheme is registered (see
                // TestSetupExtension's onRegisterCustomSchemes) but nothing
                // serves it, matching the upstream repro shape exactly.
                createBrowser(TEST_URL, true /* useOSR */);
                super.setupTest();
            }

            @Override
            public void onLoadError(CefBrowser browser, CefFrame frame, ErrorCode errorCode_,
                    String errorText, String failedUrl) {
                if (gotError[0]) return;
                gotError[0] = true;
                errorCode[0] = errorCode_;
                terminateTest();
            }
        };

        // Shorter than the 30s default -- if the bug reproduces, onLoadError
        // never arrives and there's no point waiting the full default.
        frame.awaitCompletion(10, TimeUnit.SECONDS);

        assertTrue(gotError[0], "onLoadError was never invoked for an unhandled custom "
                + "scheme navigation");
    }
}
