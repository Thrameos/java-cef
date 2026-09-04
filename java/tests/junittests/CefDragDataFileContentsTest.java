// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.cef.callback.CefDragData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.ByteArrayOutputStream;

// Exercises native/write_handler.cpp (0% covered per this session's baseline gcovr
// run; see plan/roadmap.md Phase 2), specifically the WriteHandler constructor/
// destructor path in CefDragData_N::N_GetFileContents.
//
// CefDragData.getFileContents()/getFileName() are for a file being dragged *out of*
// the web view (populated internally by CEF's renderer from a real in-page drag
// gesture, e.g. dragging an <img> element) -- a different feature from
// addFile()/getFileNames(), which is for files being dragged *into* the browser
// window. They're unrelated: addFile() has no effect on what getFileContents()
// returns. A CefDragData built via the public create()/addFile() API therefore
// always has zero file content to write, and there is no public API to construct
// one that does -- only a real user-driven drag-out gesture from a live page can,
// which is out of scope here (same class of risk as CefRunFileDialogCallback; see
// plan/windows-todo.md).
//
// What IS still real, testable coverage: passing a real (non-null) OutputStream
// causes native/CefDragData_N.cpp to construct a WriteHandler unconditionally
// before checking whether there's any content -- unlike a null stream, which
// short-circuits before WriteHandler is ever touched (see CefDragDataTest.
// createEmpty(), which only exercises the null case).
@ExtendWith(TestSetupExtension.class)
class CefDragDataFileContentsTest {
    @Test
    void getFileContentsWithRealStreamConstructsWriteHandlerButWritesNothing() {
        CefDragData dragData = CefDragData.create();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int written = dragData.getFileContents(out);

        assertEquals(0, written);
        assertEquals(0, out.size());

        dragData.dispose();
    }
}
