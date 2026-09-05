// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.cef.handler.CefLoadHandler.ErrorCode;
import org.cef.network.CefResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashMap;
import java.util.Map;

@ExtendWith(TestSetupExtension.class)
class CefResponseTest {
    @Test
    void createDefault() {
        CefResponse response = CefResponse.create();
        assertNotNull(response);
        assertFalse(response.isReadOnly());
        assertEquals(ErrorCode.ERR_NONE, response.getError());
        response.dispose();
    }

    @Test
    void setAndGetError() {
        CefResponse response = CefResponse.create();
        response.setError(ErrorCode.ERR_FAILED);
        assertEquals(ErrorCode.ERR_FAILED, response.getError());
        response.dispose();
    }

    @Test
    void setAndGetStatus() {
        CefResponse response = CefResponse.create();
        response.setStatus(404);
        assertEquals(404, response.getStatus());
        response.dispose();
    }

    @Test
    void setAndGetStatusText() {
        CefResponse response = CefResponse.create();
        response.setStatusText("Not Found");
        assertEquals("Not Found", response.getStatusText());
        response.dispose();
    }

    @Test
    void setAndGetMimeType() {
        CefResponse response = CefResponse.create();
        response.setMimeType("text/html");
        assertEquals("text/html", response.getMimeType());
        response.dispose();
    }

    @Test
    void setAndGetHeaderByName() {
        CefResponse response = CefResponse.create();
        response.setHeaderByName("X-Test", "value1", true);
        assertEquals("value1", response.getHeaderByName("X-Test"));

        response.setHeaderByName("X-Test", "value2", false);
        assertEquals("value1", response.getHeaderByName("X-Test"));

        response.setHeaderByName("X-Test", "value2", true);
        assertEquals("value2", response.getHeaderByName("X-Test"));
        response.dispose();
    }

    @Test
    void setAndGetHeaderMap() {
        CefResponse response = CefResponse.create();
        Map<String, String> headers = new HashMap<>();
        headers.put("X-One", "1");
        headers.put("X-Two", "2");
        response.setHeaderMap(headers);

        Map<String, String> readBack = new HashMap<>();
        response.getHeaderMap(readBack);
        assertEquals("1", readBack.get("X-One"));
        assertEquals("2", readBack.get("X-Two"));
        response.dispose();
    }

    @Test
    void toStringDoesNotThrow() {
        CefResponse response = CefResponse.create();
        response.setStatus(200);
        assertNotNull(response.toString());
        response.dispose();
    }
}
