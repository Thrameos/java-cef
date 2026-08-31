// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.cef.network.CefCookieManager;
import org.cef.network.CefPostDataElement;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// More unhappy-path coverage: numeric/size-mismatch edge cases rather than
// null references (see NullParameterEdgeCaseTest for those). Per the
// 85%-coverage goal's explicit "happy AND unhappy paths" requirement.
@ExtendWith(TestSetupExtension.class)
class MalformedInputEdgeCaseTest {
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
    void postDataElementGetBytesWithZeroSizeBufferDoesNotThrow() {
        CefPostDataElement element = CefPostDataElement.create();
        byte[] data = {1, 2, 3};
        element.setToBytes(data.length, data);
        byte[] tooSmall = new byte[0];
        assertDoesNotThrow(() -> element.getBytes(0, tooSmall));
        element.dispose();
    }

    @Test
    void requestSetFlagsWithNegativeAndGarbageValuesDoesNotThrow() {
        CefRequest request = CefRequest.create();
        assertDoesNotThrow(() -> request.setFlags(-1));
        assertDoesNotThrow(() -> request.setFlags(Integer.MIN_VALUE));
        assertDoesNotThrow(() -> request.setFlags(Integer.MAX_VALUE));
        assertNotNull(request.toString());
        request.dispose();
    }

    @Test
    void requestSetURLWithNoSchemeStringDoesNotThrow() {
        CefRequest request = CefRequest.create();
        assertDoesNotThrow(() -> request.setURL("not a valid url at all"));
        request.dispose();
    }

    @Test
    void requestSetURLWithSchemeOnlyStringDoesNotThrow() {
        CefRequest request = CefRequest.create();
        assertDoesNotThrow(() -> request.setURL("http://"));
        request.dispose();
    }

    @Test
    void requestSetURLWithMissingSchemeColonStringDoesNotThrow() {
        CefRequest request = CefRequest.create();
        assertDoesNotThrow(() -> request.setURL("://missing-scheme"));
        request.dispose();
    }

    // Regression test for Thrameos/java-cef#21: CEF's own bundled binary
    // distribution has a CHECK(!url.empty()) in request_ctocpp.cc:75
    // (Debug/coverage-build-only -- silently permitted in Release) that
    // native/CefRequest_N.cpp's N_SetURL used to do nothing to guard
    // against. Fixed by rejecting an empty URL in the JNI shim.
    @Test
    void requestSetURLWithEmptyStringDoesNotThrow() {
        CefRequest request = CefRequest.create();
        assertDoesNotThrow(() -> request.setURL(""));
        assertNotNull(request.toString());
        request.dispose();
    }

    @Test
    void requestSetHeaderByNameWithEmptyValueDoesNotThrow() {
        CefRequest request = CefRequest.create();
        assertDoesNotThrow(() -> request.setHeaderByName("X-Empty-Value", "", true));
        assertNotNull(request.toString());
        request.dispose();
    }

    // Regression test for Thrameos/java-cef#20: CEF's own bundled binary
    // distribution has a CHECK(!name.empty()) in request_ctocpp.cc
    // (Debug/coverage-build-only -- silently permitted in Release) that
    // native/CefRequest_N.cpp's N_SetHeaderByName used to do nothing to
    // guard against. Fixed by rejecting an empty header name in the JNI
    // shim.
    @Test
    void requestSetHeaderByNameWithEmptyNameDoesNotThrow() {
        CefRequest request = CefRequest.create();
        assertDoesNotThrow(() -> request.setHeaderByName("", "some-value", true));
        assertNotNull(request.toString());
        request.dispose();
    }

    @Test
    void responseSetStatusWithOutOfRangeValuesDoesNotThrow() {
        CefResponse response = CefResponse.create();
        assertDoesNotThrow(() -> response.setStatus(-1));
        assertDoesNotThrow(() -> response.setStatus(0));
        assertDoesNotThrow(() -> response.setStatus(99999));
        assertDoesNotThrow(() -> response.setStatus(Integer.MIN_VALUE));
        assertDoesNotThrow(() -> response.setStatus(Integer.MAX_VALUE));
        assertNotNull(response.toString());
        response.dispose();
    }

    @Test
    void cookieManagerDeleteCookiesWithNullArgumentsDoesNotThrow() {
        CefCookieManager manager = CefCookieManager.getGlobalManager();
        assertDoesNotThrow(() -> manager.deleteCookies(null, null));
        assertDoesNotThrow(() -> manager.deleteCookies("http://test.invalid/", null));
        assertDoesNotThrow(() -> manager.deleteCookies(null, "some-cookie"));
    }
}
