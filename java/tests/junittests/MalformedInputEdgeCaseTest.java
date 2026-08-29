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
import org.junit.jupiter.api.Disabled;
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

    // @Disabled -- IMPORTANT: this is a real, silent (no FATAL/DCHECK output
    // at all, unlike every other crash found this session) segfault, root-
    // caused to a signed/unsigned integer bug: native/CefPostDataElement_N.
    // cpp passes the jint |size| straight into CefPostDataElement::
    // SetToBytes(), whose C++ signature takes a size_t -- a negative jint
    // implicitly becomes a huge unsigned value, causing a massive buffer
    // over-read. Filed as Thrameos/java-cef#19. Do not remove @Disabled
    // without a fix; run only under a hard external timeout wrapper if
    // investigating further, since this crash gives no diagnostic to work
    // from otherwise.
    @Disabled("Real silent segfault (signed/unsigned integer bug in "
            + "CefPostDataElement.setToBytes()) -- see Thrameos/java-cef#19")
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

    // @Disabled -- IMPORTANT: a real crash, but Debug/coverage-build-only
    // (same class of issue as #20 -- confirmed does NOT reproduce in
    // Release): CEF's own bundled binary distribution has a
    // CHECK(!url.empty()) in request_ctocpp.cc:75 that
    // native/CefRequest_N.cpp's N_SetURL does nothing to guard against.
    // Filed as Thrameos/java-cef#21. Also crashed early enough to prevent
    // CoverageTestHelper.flush() from running, losing an entire coverage-
    // measurement run's .gcda data -- same lesson as #20.
    @Disabled("Real crash (CEF's own CHECK(!url.empty())), Debug/coverage-"
            + "build-only -- see Thrameos/java-cef#21")
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

    // @Disabled -- IMPORTANT: a real crash, but Debug/coverage-build-only
    // (confirmed does NOT reproduce in Release): CEF's own bundled binary
    // distribution has a CHECK(!name.empty()) in request_ctocpp.cc that
    // native/CefRequest_N.cpp's N_SetHeaderByName does nothing to guard
    // against on the JCEF side. Filed as Thrameos/java-cef#20. This one also
    // crashed early enough to prevent CoverageTestHelper.flush() from
    // running, losing an entire coverage-measurement run's .gcda data --
    // exclude this class by name from any future ENABLE_COVERAGE run until
    // fixed, the same way CefPrintSettingsTest/CefRequestContextTest/
    // CefPostDataTest/CefBrowserApiTest already are (see plan/roadmap.md).
    @Disabled("Real crash (CEF's own CHECK(!name.empty())), Debug/coverage-"
            + "build-only -- see Thrameos/java-cef#20")
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
