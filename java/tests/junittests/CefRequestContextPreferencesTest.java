// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefRequestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    // Finds a real, currently-settable preference (via canSetPreference()) rather
    // than guessing a Chromium preference name, then pushes a new value of the
    // same type back in through setPreference() and confirms it round-trips. This
    // is what actually exercises GetCefValueFromJNIBoolean/Integer/Double/String
    // in native/jni_util.cpp on the "Java value going in" direction -- everything
    // else in this class only exercises the "native value coming out" direction.
    @Test
    void setPreferenceWithSettablePreferenceRoundTripsNewValue() {
        String[] settableKey = {null};
        Object[] originalValue = {null};
        Object[] newValue = {null};
        String[] setError = {"not run"};
        Object[] readBackValue = {null};

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
                Map<String, Object> all = context.getAllPreferences(true);
                for (Map.Entry<String, Object> entry : all.entrySet()) {
                    if (context.canSetPreference(entry.getKey())) {
                        settableKey[0] = entry.getKey();
                        originalValue[0] = entry.getValue();
                        break;
                    }
                }

                if (settableKey[0] != null) {
                    if (TestSetupContext.debugPrint()) {
                        System.out.println("setPreferenceWithSettablePreferenceRoundTripsNewValue: "
                                + "using settable preference '" + settableKey[0] + "' (was "
                                + originalValue[0] + ")");
                    }

                    if (originalValue[0] instanceof Boolean) {
                        newValue[0] = !((Boolean) originalValue[0]);
                    } else if (originalValue[0] instanceof Integer) {
                        newValue[0] = ((Integer) originalValue[0]) + 1;
                    } else if (originalValue[0] instanceof Double) {
                        newValue[0] = ((Double) originalValue[0]) + 1.0;
                    } else if (originalValue[0] instanceof String) {
                        newValue[0] = originalValue[0] + "_jcef_test_suffix";
                    } else {
                        // Map/List/null or some other type we don't want to
                        // construct a synthetic replacement for -- skip the
                        // set/read-back below, but still record what we found.
                        settableKey[0] = null;
                    }
                }

                if (settableKey[0] != null) {
                    setError[0] = context.setPreference(settableKey[0], newValue[0]);
                    readBackValue[0] = context.getPreference(settableKey[0]);
                }

                terminateTest();
            }
        };

        frame.awaitCompletion();

        if (settableKey[0] == null) {
            // Legitimate outcome per plan/roadmap.md Track A item 1: this CEF
            // version/build may not expose any settable scalar preference. Not a
            // failure -- just nothing further to assert.
            if (TestSetupContext.debugPrint()) {
                System.out.println("setPreferenceWithSettablePreferenceRoundTripsNewValue: "
                        + "no settable scalar preference found, nothing to round-trip");
            }
            return;
        }

        assertEquals(null, setError[0], "setPreference reported an error: " + setError[0]);
        assertEquals(newValue[0], readBackValue[0]);

        // Restore the original value so this test doesn't leak state into any
        // other test that happens to read the same global preference.
        TestFrame restoreFrame = new TestFrame() {
            @Override
            protected void setupTest() {
                addResource(TEST_URL, CONTENT, "text/html");
                createBrowser(TEST_URL, true /* useOSR */);
                super.setupTest();
            }

            @Override
            public void onAfterCreated(CefBrowser browser) {
                super.onAfterCreated(browser);
                CefRequestContext.getGlobalContext().setPreference(
                        settableKey[0], originalValue[0]);
                terminateTest();
            }
        };
        restoreFrame.awaitCompletion();
    }

    // Targeted unhappy-path coverage for native/jni_util.cpp's
    // GetCefValueFromJNI{Integer,Double,Map,List,ByteBuffer} -- none of
    // this CEF build's currently-settable preferences happen to be of these
    // types (setPreferenceWithSettablePreferenceRoundTripsNewValue() above
    // only ever finds Boolean/Integer/Double/String, and per its own
    // comment deliberately skips Map/List), so the only way to reach these
    // conversion functions at all is to call setPreference() with a
    // synthetic value of each type. Uses a name that's guaranteed not to be
    // a real settable preference (canSetPreference() asserted false first)
    // so CEF's own SetPreference() call is expected to reject it -- but per
    // native/CefRequestContext_N.cpp's N_SetPreference, the Java->CefValue
    // marshaling always runs first, unconditionally, before that rejection,
    // so this still exercises every conversion function for real.
    //
    // Found and fixed a real bug while tracing this path (see
    // native/jni_util.cpp's GetCefValueFromJNIMap): it built a populated
    // CefDictionaryValue but returned a brand-new, empty CefValue instead of
    // attaching the dictionary to it via SetDictionary() (the List sibling
    // function does this correctly via SetList()) -- every Map ever passed
    // to setPreference() silently lost all its data. Can't assert on the
    // fix's effect directly (no settable dict/list preference exists to
    // round-trip through), but the fixed code is what this test now runs.
    @Test
    void setPreferenceWithEachUnmappedJavaTypeDoesNotThrow() {
        String bogusKey = "this.preference.does.not.exist";
        String[] intError = {"not run"};
        String[] doubleError = {"not run"};
        String[] mapError = {"not run"};
        String[] listError = {"not run"};
        String[] byteBufferError = {"not run"};
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
                settable[0] = context.canSetPreference(bogusKey);

                intError[0] = context.setPreference(bogusKey, Integer.valueOf(42));
                doubleError[0] = context.setPreference(bogusKey, Double.valueOf(4.2));

                Map<String, Object> nested = new HashMap<>();
                nested.put("aString", "value");
                nested.put("aBool", Boolean.TRUE);
                Map<String, Object> map = new HashMap<>();
                map.put("anInt", Integer.valueOf(7));
                map.put("nested", nested);
                mapError[0] = context.setPreference(bogusKey, map);

                List<Object> list = new ArrayList<>();
                list.add("first");
                list.add(Integer.valueOf(2));
                list.add(Boolean.FALSE);
                listError[0] = context.setPreference(bogusKey, list);

                ByteBuffer buf = ByteBuffer.allocateDirect(4);
                buf.put(new byte[] {1, 2, 3, 4});
                buf.flip();
                byteBufferError[0] = context.setPreference(bogusKey, buf);

                terminateTest();
            }
        };

        frame.awaitCompletion();

        assertFalse(settable[0]);
        assertNotNull(intError[0], "Integer setPreference should have reported an error");
        assertNotNull(doubleError[0], "Double setPreference should have reported an error");
        assertNotNull(mapError[0], "Map setPreference should have reported an error");
        assertNotNull(listError[0], "List setPreference should have reported an error");
        assertNotNull(
                byteBufferError[0], "ByteBuffer setPreference should have reported an error");
    }
}
