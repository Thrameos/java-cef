// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.network.CefPostData;
import org.cef.network.CefPostDataElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// Closes several 0%-covered gaps in native/CefPostDataElement_N.cpp and
// native/CefPostData_N.cpp found via the 2026-09-03 fresh coverage sweep
// (N_IsReadOnly, N_SetToEmpty, N_GetFile, N_IsReadOnly, N_GetElementCount,
// N_RemoveElement, N_AddElement, N_RemoveElements). NOT blocked by issue #9
// (the plain CefPostDataTest class is excluded from Debug/coverage-build
// runs for a real DCHECK crash, see state.md) -- unlike that class, this one
// never touches a real CefRequest/browser at all, only the standalone public
// factories CefPostData.create()/CefPostDataElement.create() (the same
// pattern CefPostDataElementFirstNativeObjectTest already uses safely), so
// it's unaffected by whatever triggers #9's crash.
@ExtendWith(TestSetupExtension.class)
class CefPostDataGapCoverageTest {
    @Test
    void postDataElementIsReadOnlySetToEmptyAndGetFile() {
        CefPostDataElement element = CefPostDataElement.create();
        try {
            assertFalse(element.isReadOnly());

            element.setToFile("/path/to/gap-coverage-file.bin");
            assertEquals(CefPostDataElement.Type.PDE_TYPE_FILE, element.getType());
            assertEquals("/path/to/gap-coverage-file.bin", element.getFile());

            element.setToEmpty();
            assertEquals(CefPostDataElement.Type.PDE_TYPE_EMPTY, element.getType());
        } finally {
            element.dispose();
        }
    }

    @Test
    void postDataIsReadOnlyElementCountAddRemoveElementsAndRemoveAll() {
        CefPostData postData = CefPostData.create();
        CefPostDataElement element1 = CefPostDataElement.create();
        CefPostDataElement element2 = CefPostDataElement.create();
        try {
            assertFalse(postData.isReadOnly());
            assertEquals(0, postData.getElementCount());

            element1.setToBytes(5, "hello".getBytes());
            element2.setToBytes(5, "world".getBytes());

            assertTrue(postData.addElement(element1));
            assertTrue(postData.addElement(element2));
            assertEquals(2, postData.getElementCount());

            assertTrue(postData.removeElement(element1));
            assertEquals(1, postData.getElementCount());

            postData.removeElements();
            assertEquals(0, postData.getElementCount());
        } finally {
            postData.dispose();
            // element1 was removed from postData above and never re-added;
            // element2 was removed from postData via removeElements(). Both
            // remain independently owned Java-side objects that still need
            // disposing (same convention as CefDragDataTest's "explicit cleanup
            // to avoid memory leaks" comments elsewhere in this suite).
            element1.dispose();
            element2.dispose();
        }
    }
}
