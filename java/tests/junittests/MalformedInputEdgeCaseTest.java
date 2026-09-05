// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.cef.network.CefPostDataElement;
import org.cef.network.CefRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// Regression tests for Thrameos/java-cef#19, #20, and part of #21: several
// JNI shims forwarded malformed input (a negative/oversized byte-array size,
// an empty header name, an empty URL) straight through to CEF instead of
// validating it first. In a Debug/coverage CEF build this hits CEF's own
// CHECK()/DCHECK() and aborts the process; the #19 case is worse -- no
// diagnostic output at all, just a silent buffer over-read/segfault (a
// signed jint implicitly becoming a huge unsigned size_t).
@ExtendWith(TestSetupExtension.class)
class MalformedInputEdgeCaseTest {
    // Regression test for Thrameos/java-cef#19: native/CefPostDataElement_N.
    // cpp used to pass the jint |size| straight into CefPostDataElement::
    // SetToBytes(), whose C++ signature takes a size_t -- a negative jint
    // implicitly became a huge unsigned value, causing a silent segfault
    // (buffer over-read). Fixed by rejecting a negative (or too-large) size
    // in the JNI shim before it reaches CEF.
    @Test
    void postDataElementSetToBytesWithNegativeSizeDoesNotThrow() {
        CefPostDataElement element = CefPostDataElement.create();
        byte[] data = {1, 2, 3};
        assertDoesNotThrow(() -> element.setToBytes(-1, data));
        element.dispose();
    }

    @Test
    void postDataElementSetToBytesWithSizeLargerThanArrayDoesNotThrow() {
        CefPostDataElement element = CefPostDataElement.create();
        byte[] data = {1, 2, 3};
        // Claim a size larger than the actual array -- exercises
        // jni_util.cpp's byte-array-length handling with a mismatched
        // caller-provided size rather than the array's own real length.
        assertDoesNotThrow(() -> element.setToBytes(100, data));
        element.dispose();
    }

    // Regression test for Thrameos/java-cef#21: CEF's own bundled binary
    // distribution has a CHECK(!url.empty()) in request_ctocpp.cc (Debug/
    // coverage-build-only -- silently permitted in Release) that
    // native/CefRequest_N.cpp's N_SetURL used to do nothing to guard
    // against for a non-null but empty string (a null string was already
    // guarded separately). Fixed by rejecting an empty URL in the JNI shim.
    @Test
    void requestSetURLWithEmptyStringDoesNotThrow() {
        CefRequest request = CefRequest.create();
        assertDoesNotThrow(() -> request.setURL(""));
        request.dispose();
    }

    // Regression test for Thrameos/java-cef#20: CEF's own bundled binary
    // distribution has a CHECK(!name.empty()) in request_ctocpp.cc (Debug/
    // coverage-build-only -- silently permitted in Release) that
    // native/CefRequest_N.cpp's N_SetHeaderByName used to do nothing to
    // guard against for a non-null but empty name (a null name was already
    // guarded separately). Fixed by rejecting an empty header name in the
    // JNI shim.
    @Test
    void requestSetHeaderByNameWithEmptyNameDoesNotThrow() {
        CefRequest request = CefRequest.create();
        assertDoesNotThrow(() -> request.setHeaderByName("", "some-value", true));
        request.dispose();
    }
}
