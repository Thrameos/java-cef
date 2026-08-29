// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefRequestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

// CefRequestContextTest.preferenceAccessorsOffUiThreadDoNotThrow() exercises the
// documented off-UI-thread no-op path. This exercises the real, on-UI-thread path
// (via CefLifeSpanHandler.onAfterCreated(), which the class's own Javadoc names as
// the correct place to do this), which is what actually marshals values through
// native/jni_util.cpp's GetCefValueFromJNI*/NewJNIObjectFromCefValue (previously
// untested -- see plan/roadmap.md Phase 3).
@ExtendWith(TestSetupExtension.class)
class CefRequestContextPreferencesTest {
    private static final String TEST_URL = "http://test.com/request_context_prefs.html";
    private static final String CONTENT = "<html><body>prefs test</body></html>";

    @Test
    void getAllPreferencesOnUiThreadReturnsPopulatedMap() {
        Map<String, Object>[] result = new Map[1];

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                addResource(TEST_URL, CONTENT, "text/html");
                createBrowser(TEST_URL, true /* useOSR */);
                super.setupTest();
            }

            @Override
            public void onAfterCreated(CefBrowser browser) {
                super.onAfterCreated(browser);
                result[0] = CefRequestContext.getGlobalContext().getAllPreferences(true);
                terminateTest();
            }
        };

        frame.awaitCompletion();

        assertNotNull(result[0]);
        assertFalse(result[0].isEmpty());
    }

    @Test
    void setPreferenceWithUnknownNameOnUiThreadReturnsErrorString() {
        String[] error = {null};
        boolean[] settable = {true};

        TestFrame frame = new TestFrame() {
            @Override
            protected void setupTest() {
                addResource(TEST_URL, CONTENT, "text/html");
                createBrowser(TEST_URL, true /* useOSR */);
                super.setupTest();
            }

            @Override
            public void onAfterCreated(CefBrowser browser) {
                super.onAfterCreated(browser);
                CefRequestContext context = CefRequestContext.getGlobalContext();
                settable[0] = context.canSetPreference("this.preference.does.not.exist");
                error[0] = context.setPreference("this.preference.does.not.exist", "value");
                terminateTest();
            }
        };

        frame.awaitCompletion();

        assertFalse(settable[0]);
        assertNotNull(error[0]);
        assertTrue(error[0].length() > 0);
    }
}
