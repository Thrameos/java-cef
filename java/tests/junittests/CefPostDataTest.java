// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.network.CefPostData;
import org.cef.network.CefPostDataElement;
import org.cef.network.CefPostDataElement.Type;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Vector;

@ExtendWith(TestSetupExtension.class)
class CefPostDataTest {
    @Test
    void createEmpty() {
        CefPostData postData = CefPostData.create();
        assertNotNull(postData);
        assertFalse(postData.isReadOnly());
        assertEquals(0, postData.getElementCount());
        postData.dispose();
    }

    @Test
    void elementDefaultsToEmpty() {
        CefPostDataElement element = CefPostDataElement.create();
        assertNotNull(element);
        assertFalse(element.isReadOnly());
        assertEquals(Type.PDE_TYPE_EMPTY, element.getType());
        element.dispose();
    }

    @Test
    void elementSetToBytes() {
        CefPostDataElement element = CefPostDataElement.create();
        byte[] data = {1, 2, 3, 4, 5};
        element.setToBytes(data.length, data);

        assertEquals(Type.PDE_TYPE_BYTES, element.getType());
        assertEquals(data.length, element.getBytesCount());

        byte[] readBack = new byte[data.length];
        int read = element.getBytes(readBack.length, readBack);
        assertEquals(data.length, read);
        assertEquals(1, readBack[0]);
        assertEquals(5, readBack[4]);
        element.dispose();
    }

    @Test
    void elementSetToZeroLengthBytes() {
        CefPostDataElement element = CefPostDataElement.create();
        // Exercises jni_util.cpp's GetJNIByteArray/element-bytes path with a
        // genuinely empty (not null) byte array, previously untested.
        element.setToBytes(0, new byte[0]);

        assertEquals(Type.PDE_TYPE_BYTES, element.getType());
        assertEquals(0, element.getBytesCount());

        byte[] readBack = new byte[0];
        assertEquals(0, element.getBytes(0, readBack));
        element.dispose();
    }

    // CORRECTION (2026-08-30, coverage-stabilization session): the claim below
    // ("no-ops... confirmed here") does not hold -- verified deterministically
    // that this actually hits a real, unconditional CEF CHECK() in the Debug/
    // coverage build: FATAL:.../post_data_element_ctocpp.cc:69] Check failed:
    // !fileName.empty(). Same bug class as the already-documented issues
    // #19/#20/#21 (CEF's own CHECK() firing on empty-string input instead of
    // gracefully no-op'ing). Disabled per this project's standing strategy
    // rather than trusted on unverified prior-session say-so -- see
    // [[coverage_strategy_port_ceftests]] user memory.
    @Disabled("Real CEF CHECK() failure on empty file name (post_data_element_"
            + "ctocpp.cc:69), not a graceful no-op as the old comment claimed -- "
            + "see method comment")
    @Test
    void elementSetToEmptyFilePathLeavesElementEmpty() {
        // CEF's own CefPostDataElement::SetToFile() no-ops on an empty file name
        // (confirmed here) rather than setting type PDE_TYPE_FILE with an empty
        // path -- a real, useful-to-document edge case in the underlying CEF
        // behavior, not a JCEF bug.
        CefPostDataElement element = CefPostDataElement.create();
        element.setToFile("");
        assertEquals(Type.PDE_TYPE_EMPTY, element.getType());
        element.dispose();
    }

    @Test
    void elementSetToFile() {
        CefPostDataElement element = CefPostDataElement.create();
        element.setToFile("/tmp/does-not-need-to-exist.txt");
        assertEquals(Type.PDE_TYPE_FILE, element.getType());
        assertEquals("/tmp/does-not-need-to-exist.txt", element.getFile());
        element.dispose();
    }

    @Test
    void elementSetToEmpty() {
        CefPostDataElement element = CefPostDataElement.create();
        byte[] data = {1, 2, 3};
        element.setToBytes(data.length, data);
        assertEquals(Type.PDE_TYPE_BYTES, element.getType());

        element.setToEmpty();
        assertEquals(Type.PDE_TYPE_EMPTY, element.getType());
        element.dispose();
    }

    @Test
    void addAndRemoveElement() {
        CefPostData postData = CefPostData.create();
        CefPostDataElement element = CefPostDataElement.create();
        byte[] data = {1, 2, 3};
        element.setToBytes(data.length, data);

        assertTrue(postData.addElement(element));
        assertEquals(1, postData.getElementCount());

        Vector<CefPostDataElement> elements = new Vector<>();
        postData.getElements(elements);
        assertEquals(1, elements.size());

        assertTrue(postData.removeElement(element));
        assertEquals(0, postData.getElementCount());

        postData.dispose();
    }

    @Test
    void removeAllElements() {
        CefPostData postData = CefPostData.create();
        CefPostDataElement element1 = CefPostDataElement.create();
        element1.setToBytes(1, new byte[] {1});
        CefPostDataElement element2 = CefPostDataElement.create();
        element2.setToBytes(1, new byte[] {2});

        postData.addElement(element1);
        postData.addElement(element2);
        assertEquals(2, postData.getElementCount());

        postData.removeElements();
        assertEquals(0, postData.getElementCount());

        postData.dispose();
    }

    @Test
    void toStringDoesNotThrow() {
        CefPostData postData = CefPostData.create();
        CefPostDataElement element = CefPostDataElement.create();
        byte[] data = {1, 2, 3};
        element.setToBytes(data.length, data);
        postData.addElement(element);

        assertNotNull(postData.toString());
        assertNotNull(postData.toString("text/plain"));

        postData.dispose();
    }
}
