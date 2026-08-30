// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.cef.browser.CefMessageRouter;
import org.cef.browser.CefMessageRouter.CefMessageRouterConfig;
import org.cef.network.CefCookie;
import org.cef.network.CefCookieManager;
import org.cef.network.CefPostDataElement;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Date;

// Unhappy-path coverage for jni_util.cpp's null-guarded JNI marshaling
// helpers (GetJNIString: "if (!jstr) return CefString();",
// GetJNIStringMap/GetJNIStringMultiMap: "if (!jmap)"/"if (!jheaderMap)",
// GetJNIStringVector: "if (!jvector)") -- all previously untested, since
// every other test in this suite only ever passes real, non-null values.
// None of the Java-side *_N.java setter wrappers null-check their arguments
// before calling into native, so a null argument here reaches these native
// guards directly. Per the 85%-coverage goal's explicit "happy AND unhappy
// paths" requirement -- this is exactly that.
@ExtendWith(TestSetupExtension.class)
class NullParameterEdgeCaseTest {
    @Test
    void requestSettersAcceptNullStringsWithoutThrowing() {
        CefRequest request = CefRequest.create();
        // setURL(null) is deliberately NOT exercised here -- see
        // requestSetURLWithNullDoesNotThrow() below, @Disabled: it hits the
        // exact same CEF-internal CHECK(!url.empty()) as issue #21, because
        // native/CefRequest_N.cpp's N_SetURL marshals a null jstring to an
        // empty CefString() with no guard, same as an explicit "".
        // setMethod(null) is also deliberately NOT exercised here -- see
        // requestSetMethodWithNullDoesNotThrow() below, @Disabled: same
        // unguarded-null-to-empty-CefString() pattern, different CEF-side
        // CHECK (CHECK(!method.empty()) in request_ctocpp.cc).
        // setHeaderByName(null, ...) is also deliberately NOT exercised here
        // -- see requestSetHeaderByNameWithNullNameDoesNotThrow() below,
        // @Disabled: same pattern as issue #20 (CHECK(!name.empty())),
        // reached via null instead of "".
        assertDoesNotThrow(() -> request.setHeaderByName("X-Test", null, true));
        assertDoesNotThrow(() -> request.setFirstPartyForCookies(null));
        // Object should remain usable afterward -- not left in a corrupted
        // state by any of the null calls above.
        assertNotNull(request.toString());
        request.dispose();
    }

    // @Disabled -- IMPORTANT: same class of bug as issue #20
    // (CHECK(!name.empty()) in request_ctocpp.cc via CefRequest::
    // SetHeaderByName()), reached here via a null |name| instead of "".
    // Debug/coverage-build-only. Do not remove @Disabled without a fix.
    @Disabled("Same class of bug as Thrameos/java-cef#20 (CEF's own "
            + "CHECK(!name.empty())), reached via a null header name -- "
            + "Debug/coverage-build-only")
    @Test
    void requestSetHeaderByNameWithNullNameDoesNotThrow() {
        CefRequest request = CefRequest.create();
        assertDoesNotThrow(() -> request.setHeaderByName(null, "value", true));
        assertNotNull(request.toString());
        request.dispose();
    }

    // @Disabled -- IMPORTANT: same class of bug as Thrameos/java-cef#21
    // (unguarded null-to-empty-CefString() marshaling hitting a CEF-internal
    // CHECK), this time CHECK(!method.empty()) in request_ctocpp.cc via
    // CefRequest::SetMethod(). Debug/coverage-build-only. Do not remove
    // @Disabled without a fix.
    @Disabled("Same class of bug as Thrameos/java-cef#21 (CEF's own "
            + "CHECK(!method.empty())), reached via CefRequest.setMethod"
            + "(null) -- Debug/coverage-build-only")
    @Test
    void requestSetMethodWithNullDoesNotThrow() {
        CefRequest request = CefRequest.create();
        assertDoesNotThrow(() -> request.setMethod(null));
        assertNotNull(request.toString());
        request.dispose();
    }

    // @Disabled -- IMPORTANT: same root cause as Thrameos/java-cef#21
    // (CEF's own CHECK(!url.empty()) in request_ctocpp.cc), reached here via
    // a null jstring rather than an explicit empty string -- both marshal to
    // the same empty CefString() with no guard on the JCEF side. Confirmed
    // Debug/coverage-build-only, same as #21. Do not remove @Disabled
    // without a fix; run only under a hard external timeout wrapper.
    @Disabled("Same root cause as Thrameos/java-cef#21 (CEF's own "
            + "CHECK(!url.empty())), reached via null instead of \"\" -- "
            + "Debug/coverage-build-only")
    @Test
    void requestSetURLWithNullDoesNotThrow() {
        CefRequest request = CefRequest.create();
        assertDoesNotThrow(() -> request.setURL(null));
        assertNotNull(request.toString());
        request.dispose();
    }

    @Test
    void requestSetHeaderMapAcceptsNullMap() {
        CefRequest request = CefRequest.create();
        request.setHeaderByName("X-Before", "value", true);
        assertDoesNotThrow(() -> request.setHeaderMap(null));
        // Behavior (whether existing headers survive a null map) isn't
        // asserted here -- just that the native call (jmap == nullptr in
        // jni_util.cpp's GetJNIStringMultiMap) doesn't crash.
        assertNotNull(request.toString());
        request.dispose();
    }

    @Test
    void responseSettersAcceptNullStringsWithoutThrowing() {
        CefResponse response = CefResponse.create();
        assertDoesNotThrow(() -> response.setMimeType(null));
        assertDoesNotThrow(() -> response.setStatusText(null));
        // setHeaderByName(null, ...) is deliberately NOT exercised here --
        // see responseSetHeaderByNameWithNullNameDoesNotThrow() below,
        // @Disabled: same systemic null-to-empty-CefString() pattern as
        // issues #19/#20/#21, this time in CefResponse_N.cpp/
        // response_ctocpp.cc's CHECK(!name.empty()).
        assertDoesNotThrow(() -> response.setHeaderByName("X-Test", null, true));
        assertNotNull(response.toString());
        response.dispose();
    }

    // @Disabled -- IMPORTANT: same systemic class of bug as issues
    // #19/#20/#21 (see NullParameterEdgeCaseTest's CefRequest-side
    // reproductions above), this time CHECK(!name.empty()) in
    // response_ctocpp.cc via CefResponse::SetHeaderByName(). Debug/
    // coverage-build-only. Do not remove @Disabled without a fix.
    @Disabled("Same systemic class of bug as Thrameos/java-cef#19/#20/#21 "
            + "(CEF's own CHECK(!name.empty())), reached via "
            + "CefResponse.setHeaderByName(null, ...) -- Debug/coverage-"
            + "build-only")
    @Test
    void responseSetHeaderByNameWithNullNameDoesNotThrow() {
        CefResponse response = CefResponse.create();
        assertDoesNotThrow(() -> response.setHeaderByName(null, "value", true));
        assertNotNull(response.toString());
        response.dispose();
    }

    @Test
    void responseSetHeaderMapAcceptsNullMap() {
        CefResponse response = CefResponse.create();
        response.setHeaderByName("X-Before", "value", true);
        assertDoesNotThrow(() -> response.setHeaderMap(null));
        assertNotNull(response.toString());
        response.dispose();
    }

    // @Disabled -- IMPORTANT: same root cause as Thrameos/java-cef#21 (see
    // requestSetURLWithNullDoesNotThrow() above): the null |url| argument
    // marshals to an empty CefString(), hitting CEF's own
    // CHECK(!url.empty()) inside CefRequest::Set(). Debug/coverage-build-
    // only. Do not remove @Disabled without a fix.
    @Disabled("Same root cause as Thrameos/java-cef#21 (CEF's own "
            + "CHECK(!url.empty())), reached via a null url argument to "
            + "CefRequest.set() -- Debug/coverage-build-only")
    @Test
    void requestSetAcceptsAllNullArguments() {
        CefRequest request = CefRequest.create();
        assertDoesNotThrow(() -> request.set(null, null, null, null));
        assertNotNull(request.toString());
        request.dispose();
    }

    // @Disabled -- IMPORTANT: same class of bug as issues #20/#21 (see
    // requestSetURLWithNullDoesNotThrow() above): CHECK(!fileName.empty())
    // in post_data_element_ctocpp.cc via CefPostDataElement::SetToFile(),
    // reached because native/CefPostDataElement_N.cpp marshals a null
    // jstring to an empty CefString() with no guard. Debug/coverage-build-
    // only. Do not remove @Disabled without a fix.
    @Disabled("Same class of bug as Thrameos/java-cef#20/#21 (CEF's own "
            + "CHECK(!fileName.empty())), reached via "
            + "CefPostDataElement.setToFile(null) -- Debug/coverage-"
            + "build-only")
    @Test
    void postDataElementSetToFileAcceptsNull() {
        CefPostDataElement element = CefPostDataElement.create();
        assertDoesNotThrow(() -> element.setToFile(null));
        element.dispose();
    }

    @Test
    void cookieManagerSetCookieWithNullFieldsDoesNotThrow() {
        CefCookieManager manager = CefCookieManager.getGlobalManager();
        CefCookie cookie = new CefCookie(
                null, null, null, null, false, false, new Date(), new Date(), false, null);
        // Not asserting the return value here (a malformed cookie may
        // legitimately be rejected) -- only that the native call itself
        // (which marshals every one of these null String fields through
        // jni_util.cpp's GetJNIString) doesn't crash.
        assertDoesNotThrow(() -> manager.setCookie("http://null-cookie-test.invalid/", cookie));
    }

    @Test
    void messageRouterConfigWithNullFunctionNamesDoesNotThrowOnCreate() {
        CefMessageRouterConfig config = new CefMessageRouterConfig();
        config.jsQueryFunction = null;
        config.jsCancelFunction = null;
        CefMessageRouter[] router = {null};
        assertDoesNotThrow(() -> router[0] = CefMessageRouter.create(config));
        if (router[0] != null) router[0].dispose();
    }
}
