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
