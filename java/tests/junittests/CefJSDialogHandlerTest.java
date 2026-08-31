// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefJSDialogHandlerAdapter;
import org.cef.handler.CefJSDialogHandler.JSDialogType;
import org.cef.misc.BoolRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.CountDownLatch;

// Exercises native/jsdialog_handler.cpp (0% covered per this session's baseline
// gcovr run; see plan/roadmap.md Phase 2) via a real alert() call from JavaScript.
//
// Migrated to the shared-browser (Tier 1) harness -- see plan/roadmap.md's
// "two-tier test harness" entry.
@ExtendWith({TestSetupExtension.class, SharedBrowserExtension.class})
class CefJSDialogHandlerTest {
    private static final String CONTENT = "<html><body><script>"
            + "alert('hello-dialog');"
            + "document.title = 'after-alert';"
            + "</script></body></html>";

    @Test
    void alertInvokesOnJSDialogAndResumesAfterContinue() {
        JSDialogType[] dialogType = {null};
        String[] messageText = {null};
        boolean[] gotTitle = {false};
        CountDownLatch done = new CountDownLatch(1);

        SharedBrowserExtension.addJSDialogHandler(new CefJSDialogHandlerAdapter() {
            @Override
            public boolean onJSDialog(CefBrowser browser, String origin_url,
                    JSDialogType dialog_type, String message_text, String default_prompt_text,
                    org.cef.callback.CefJSDialogCallback callback, BoolRef suppress_message) {
                dialogType[0] = dialog_type;
                messageText[0] = message_text;
                callback.Continue(true, null);
                return true;
            }
        });

        SharedBrowserExtension.addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override
            public void onTitleChange(CefBrowser browser, String title) {
                if (gotTitle[0] || !"after-alert".equals(title)) return;
                gotTitle[0] = true;
                done.countDown();
            }
        });

        SharedBrowserExtension.loadPage(CONTENT);
        if (!gotTitle[0]) {
            SharedBrowserExtension.awaitLatch(done, 15);
        }

        assertEquals(JSDialogType.JSDIALOGTYPE_ALERT, dialogType[0]);
        assertEquals("hello-dialog", messageText[0]);
        assertTrue(gotTitle[0], "Page never resumed after callback.Continue()");
    }
}
