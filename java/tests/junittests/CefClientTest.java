// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.handler.CefContextMenuHandlerAdapter;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefDownloadHandlerAdapter;
import org.cef.handler.CefFocusHandlerAdapter;
import org.cef.handler.CefJSDialogHandlerAdapter;
import org.cef.handler.CefKeyboardHandlerAdapter;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.handler.CefPrintHandlerAdapter;
import org.cef.handler.CefRequestHandlerAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// Exercises CefClient's handler registration surface -- each addXxxHandler/
// removeXxxHandler pair only needs a CefApp singleton (via TestSetupExtension), not
// a live browser, so this belongs in Phase 1 alongside the other value/registration
// tests rather than Phase 2's browser-lifecycle tests.
@ExtendWith(TestSetupExtension.class)
class CefClientTest {
    @Test
    void createClientReturnsNonNull() {
        CefClient client = CefApp.getInstance().createClient();
        assertNotNull(client);
        client.dispose();
    }

    @Test
    void addHandlerReturnsSameClientForChaining() {
        CefClient client = CefApp.getInstance().createClient();
        // Every addXxxHandler method is documented/typed to return `this` so calls
        // can be chained.
        assertSame(client, client.addContextMenuHandler(new CefContextMenuHandlerAdapter() {}));
        client.dispose();
    }

    @Test
    void addAndRemoveEveryHandlerType() {
        CefClient client = CefApp.getInstance().createClient();

        client.addContextMenuHandler(new CefContextMenuHandlerAdapter() {});
        client.removeContextMenuHandler();

        client.addDialogHandler((browser, mode, title, defaultFilePath, acceptFilters,
                                         acceptExtensions, acceptDescriptions, callback) -> false);
        client.removeDialogHandler();

        client.addDisplayHandler(new CefDisplayHandlerAdapter() {});
        client.removeDisplayHandler();

        client.addDownloadHandler(new CefDownloadHandlerAdapter() {});
        client.removeDownloadHandler();

        client.addDragHandler((browser, dragData, mask) -> false);
        client.removeDragHandler();

        client.addFocusHandler(new CefFocusHandlerAdapter() {});
        client.removeFocusHandler();

        client.addJSDialogHandler(new CefJSDialogHandlerAdapter() {});
        client.removeJSDialogHandler();

        client.addKeyboardHandler(new CefKeyboardHandlerAdapter() {});
        client.removeKeyboardHandler();

        client.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {});
        client.removeLifeSpanHandler();

        client.addLoadHandler(new CefLoadHandlerAdapter() {});
        client.removeLoadHandler();

        client.addPrintHandler(new CefPrintHandlerAdapter() {});
        client.removePrintHandler();

        client.addRequestHandler(new CefRequestHandlerAdapter() {});
        client.removeRequestHandler();

        client.dispose();
    }

    @Test
    void disposeIsIdempotent() {
        CefClient client = CefApp.getInstance().createClient();
        client.dispose();
        // A second dispose() must not throw.
        client.dispose();
    }
}
