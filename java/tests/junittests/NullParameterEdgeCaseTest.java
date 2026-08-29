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
        assertDoesNotThrow(() -> request.setURL(null));
        assertDoesNotThrow(() -> request.setMethod(null));
        assertDoesNotThrow(() -> request.setHeaderByName(null, "value", true));
        assertDoesNotThrow(() -> request.setHeaderByName("X-Test", null, true));
        assertDoesNotThrow(() -> request.setFirstPartyForCookies(null));
        // Object should remain usable afterward -- not left in a corrupted
        // state by any of the null calls above.
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
        assertDoesNotThrow(() -> response.setHeaderByName(null, "value", true));
        assertDoesNotThrow(() -> response.setHeaderByName("X-Test", null, true));
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

    @Test
    void requestSetAcceptsAllNullArguments() {
        CefRequest request = CefRequest.create();
        assertDoesNotThrow(() -> request.set(null, null, null, null));
        assertNotNull(request.toString());
        request.dispose();
    }

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
