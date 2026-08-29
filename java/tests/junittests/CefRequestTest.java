// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.network.CefPostData;
import org.cef.network.CefRequest;
import org.cef.network.CefRequest.ReferrerPolicy;
import org.cef.network.CefRequest.ResourceType;
import org.cef.network.CefRequest.TransitionFlags;
import org.cef.network.CefRequest.TransitionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashMap;
import java.util.Map;

@ExtendWith(TestSetupExtension.class)
class CefRequestTest {
    @Test
    void createDefault() {
        CefRequest request = CefRequest.create();
        assertNotNull(request);
        assertFalse(request.isReadOnly());
        assertEquals("", request.getURL());
        assertEquals("GET", request.getMethod());
        assertEquals(0, request.getFlags());
        assertEquals(ResourceType.RT_SUB_RESOURCE, request.getResourceType());
        request.dispose();
    }

    @Test
    void setAndGetURL() {
        CefRequest request = CefRequest.create();
        final String url = "http://test.com/request.html";
        request.setURL(url);
        assertEquals(url, request.getURL());
        request.dispose();
    }

    @Test
    void setAndGetMethod() {
        CefRequest request = CefRequest.create();
        request.setMethod("POST");
        assertEquals("POST", request.getMethod());
        request.dispose();
    }

    @Test
    void setAndGetReferrer() {
        CefRequest request = CefRequest.create();
        final String referrer = "http://origin.com/page.html";
        request.setReferrer(referrer, ReferrerPolicy.REFERRER_POLICY_NEVER_CLEAR_REFERRER);
        assertEquals(referrer, request.getReferrerURL());
        assertEquals(
                ReferrerPolicy.REFERRER_POLICY_NEVER_CLEAR_REFERRER, request.getReferrerPolicy());
        request.dispose();
    }

    @Test
    void setAndGetHeaderByName() {
        CefRequest request = CefRequest.create();
        request.setHeaderByName("X-Test", "value1", true);
        assertEquals("value1", request.getHeaderByName("X-Test"));

        // overwrite=false must not replace an existing value.
        request.setHeaderByName("X-Test", "value2", false);
        assertEquals("value1", request.getHeaderByName("X-Test"));

        // overwrite=true must replace it.
        request.setHeaderByName("X-Test", "value2", true);
        assertEquals("value2", request.getHeaderByName("X-Test"));
        request.dispose();
    }

    @Test
    void setAndGetHeaderMap() {
        CefRequest request = CefRequest.create();
        Map<String, String> headers = new HashMap<>();
        headers.put("X-One", "1");
        headers.put("X-Two", "2");
        request.setHeaderMap(headers);

        Map<String, String> readBack = new HashMap<>();
        request.getHeaderMap(readBack);
        assertEquals("1", readBack.get("X-One"));
        assertEquals("2", readBack.get("X-Two"));
        request.dispose();
    }

    @Test
    void setAll() {
        CefRequest request = CefRequest.create();
        Map<String, String> headers = new HashMap<>();
        headers.put("X-One", "1");
        CefPostData postData = CefPostData.create();
        request.set("http://test.com/", "POST", postData, headers);

        assertEquals("http://test.com/", request.getURL());
        assertEquals("POST", request.getMethod());
        assertNotNull(request.getPostData());

        Map<String, String> readBack = new HashMap<>();
        request.getHeaderMap(readBack);
        assertEquals("1", readBack.get("X-One"));

        request.dispose();
    }

    @Test
    void setAndGetPostData() {
        CefRequest request = CefRequest.create();
        CefPostData postData = CefPostData.create();
        request.setPostData(postData);
        assertNotNull(request.getPostData());
        request.dispose();
    }

    @Test
    void setAndGetFlags() {
        CefRequest request = CefRequest.create();
        int flags = CefRequest.CefUrlRequestFlags.UR_FLAG_SKIP_CACHE
                | CefRequest.CefUrlRequestFlags.UR_FLAG_NO_RETRY_ON_5XX;
        request.setFlags(flags);
        assertEquals(flags, request.getFlags());
        request.dispose();
    }

    @Test
    void setAndGetFirstPartyForCookies() {
        CefRequest request = CefRequest.create();
        final String url = "http://firstparty.com/";
        request.setFirstPartyForCookies(url);
        assertEquals(url, request.getFirstPartyForCookies());
        request.dispose();
    }

    @Test
    void identifierDefaultsToZero() {
        CefRequest request = CefRequest.create();
        // A request that CEF hasn't dispatched itself has no assigned identifier.
        assertEquals(0, request.getIdentifier());
        request.dispose();
    }

    @Test
    void toStringDoesNotThrow() {
        CefRequest request = CefRequest.create();
        request.setURL("http://test.com/");
        assertNotNull(request.toString());
        request.dispose();
    }

    @Test
    void transitionTypeQualifiers() {
        TransitionType type = TransitionType.TT_LINK;
        assertEquals(0, type.getSource());
        assertFalse(type.isRedirect());

        type.addQualifier(TransitionFlags.TT_FORWARD_BACK_FLAG);
        assertTrue(type.isSet(TransitionFlags.TT_FORWARD_BACK_FLAG));

        type.removeQualifier(TransitionFlags.TT_FORWARD_BACK_FLAG);
        assertFalse(type.isSet(TransitionFlags.TT_FORWARD_BACK_FLAG));
    }

    @Test
    void transitionTypeRedirectFlags() {
        TransitionType type = TransitionType.TT_EXPLICIT;
        type.addQualifier(TransitionFlags.TT_SERVER_REDIRECT_FLAG);
        assertTrue(type.isRedirect());
    }
}
