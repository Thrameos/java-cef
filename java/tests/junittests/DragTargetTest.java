// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.cef.browser.CefBrowser;
import org.cef.callback.CefDragData;
import org.cef.handler.CefDragHandler.DragOperationMask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.awt.Point;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;

// Exercises native/drag_handler.cpp's DragHandler::OnDragEnter (previously 0%
// covered -- see plan/coverage-native-llvm-report.txt; no existing test triggered
// it). CefDragHandler.onDragEnter() fires in response to an *external* drag
// entering the browser window; normally that's driven by a real OS drag-and-drop
// gesture landing on the AWT java.awt.dnd.DropTarget that CefDropTargetListener
// wires up, which forwards into CefBrowser_N.dragTargetDragEnter(). A real OS-level
// drag can't be synthesized in a headless test run, so this calls
// dragTargetDragEnter() directly instead -- exactly the technique CEF's own C++
// test suite uses for this (~/devel/cef/tests/ceftests/os_rendering_unittest.cc
// calls CefBrowserHost::DragTargetDragEnter() directly rather than driving a real
// OS drag), just via the JNI-facing Java entry point. That method is `protected`
// on the package-private CefBrowser_N (org.cef.browser, not accessible from this
// package at compile time), so it's invoked via reflection -- walking up from the
// browser's runtime class since CefBrowserOsr doesn't declare it itself.
//
// Migrated to the shared-browser (Tier 1) harness -- see plan/roadmap.md's
// "two-tier test harness" entry.
@ExtendWith({TestSetupExtension.class, SharedBrowserExtension.class})
class DragTargetTest {
    private static final String DRAGGED_LINK_URL = "http://example.com/dragged-link";

    @Test
    void dragTargetDragEnterInvokesOnDragEnter() {
        boolean[] fired = {false};
        int[] receivedMask = {-1};
        String[] receivedLinkUrl = {null};
        CountDownLatch done = new CountDownLatch(1);

        SharedBrowserExtension.addDragHandler((browser, dragData, mask) -> {
            fired[0] = true;
            receivedMask[0] = mask;
            receivedLinkUrl[0] = dragData.getLinkURL();
            done.countDown();
            return false;
        });

        SharedBrowserExtension.loadPage("<html><body>drag target</body></html>");

        CefBrowser browser = SharedBrowserExtension.browser();
        try (CefDragData dragData = CefDragData.create()) {
            dragData.setLinkURL(DRAGGED_LINK_URL);
            invokeDragTargetDragEnter(browser, dragData, DragOperationMask.DRAG_OPERATION_COPY);
        }

        SharedBrowserExtension.awaitLatch(done, 15);

        assertTrue(fired[0], "onDragEnter should have fired after dragTargetDragEnter()");
        assertEquals(DragOperationMask.DRAG_OPERATION_COPY, receivedMask[0]);
        assertEquals(DRAGGED_LINK_URL, receivedLinkUrl[0]);
    }

    private static void invokeDragTargetDragEnter(
            CefBrowser browser, CefDragData dragData, int allowedOps) {
        invoke(browser, "dragTargetDragEnter",
                new Class<?>[] {CefDragData.class, Point.class, int.class, int.class},
                dragData, new Point(10, 10), /* modifiers */ 0, allowedOps);
    }

    // Completes the rest of the drag lifecycle
    // (dragTargetDragEnter -> dragTargetDragOver -> dragTargetDrop, and
    // separately dragTargetDragLeave, dragSourceEndedAt,
    // dragSourceSystemDragEnded) -- all previously 0% covered per
    // plan/coverage-current-state.md's 2026-09-03 llvm-cov sweep. None of
    // these fire a CefDragHandler callback of their own (they're one-way
    // notifications straight through to CefBrowserHost, see
    // native/CefBrowser_N.cpp) -- this is structural coverage: they must
    // not throw, matching the same real-drag-can't-be-synthesized rationale
    // as dragTargetDragEnterInvokesOnDragEnter() above.
    @Test
    void restOfDragTargetAndDragSourceLifecycleDoesNotThrow() {
        SharedBrowserExtension.loadPage("<html><body>drag target 2</body></html>");
        CefBrowser browser = SharedBrowserExtension.browser();

        try (CefDragData dragData = CefDragData.create()) {
            dragData.setLinkURL("http://example.com/dragged-link-2");
            invokeDragTargetDragEnter(browser, dragData, DragOperationMask.DRAG_OPERATION_COPY);
            invoke(browser, "dragTargetDragOver",
                    new Class<?>[] {Point.class, int.class, int.class}, new Point(20, 20),
                    /* modifiers */ 0, DragOperationMask.DRAG_OPERATION_COPY);
            invoke(browser, "dragTargetDrop", new Class<?>[] {Point.class, int.class},
                    new Point(20, 20), /* modifiers */ 0);
        }

        try (CefDragData dragData = CefDragData.create()) {
            dragData.setLinkURL("http://example.com/dragged-link-3");
            invokeDragTargetDragEnter(browser, dragData, DragOperationMask.DRAG_OPERATION_COPY);
            invoke(browser, "dragTargetDragLeave", new Class<?>[0]);
        }

        invoke(browser, "dragSourceEndedAt", new Class<?>[] {Point.class, int.class},
                new Point(30, 30), DragOperationMask.DRAG_OPERATION_COPY);
        invoke(browser, "dragSourceSystemDragEnded", new Class<?>[0]);
    }

    // Invokes a `protected final` CefBrowser_N drag method via reflection --
    // same technique/rationale as invokeDragTargetDragEnter() above, just
    // generalized to any of the DragTarget*/DragSource* methods.
    private static void invoke(
            CefBrowser browser, String methodName, Class<?>[] argTypes, Object... args) {
        Class<?> cls = browser.getClass();
        while (cls != null) {
            try {
                Method m = cls.getDeclaredMethod(methodName, argTypes);
                m.setAccessible(true);
                m.invoke(browser, args);
                return;
            } catch (NoSuchMethodException e) {
                cls = cls.getSuperclass();
            } catch (ReflectiveOperationException e) {
                fail("Failed to invoke " + methodName + " via reflection: " + e);
            }
        }
        fail(methodName + "() not found on " + browser.getClass());
    }
}
