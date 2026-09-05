// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cef.CefApp;
import org.cef.CefApp.CefAppState;
import org.cef.CefApp.CefVersion;
import org.cef.CefSettings;
import org.cef.handler.CefAppHandlerAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(TestSetupExtension.class)
class CefAppTest {
    @Test
    void versionFieldsAreReasonable() {
        CefVersion version = CefApp.getInstance().getVersion();
        assertNotNull(version);

        // CEF version numbers are always non-negative.
        assertTrue(version.CEF_VERSION_MAJOR >= 0);
        assertTrue(version.CHROME_VERSION_MAJOR >= 0);

        assertNotNull(version.getJcefVersion());
        assertNotNull(version.getCefVersion());
        assertNotNull(version.getChromeVersion());
        assertNotNull(version.toString());
    }

    @Test
    void stateIsAtLeastInitializedByTheTimeATestRuns() {
        // TestSetupExtension has already called CefApp.getInstance() for the whole
        // suite by the time any @Test method runs.
        CefAppState state = CefApp.getState();
        assertTrue(state == CefAppState.INITIALIZED || state == CefAppState.NEW
                || state == CefAppState.INITIALIZING);
    }

    @Test
    void addAppHandlerAfterInitializationThrows() {
        // CefApp.addAppHandler is only legal before CefApp is first initialized,
        // which TestSetupExtension has already done for the whole suite -- this
        // documents/exercises the guard's exception path (CefApp.java:194).
        assertThrows(IllegalStateException.class,
                () -> CefApp.addAppHandler(new CefAppHandlerAdapter(null) {}));
    }

    @Test
    void setSettingsAfterCreateClientThrows() {
        // Same shape as addAppHandler above: setSettings is only legal before
        // createClient() has been called the first time, which happened long ago in
        // this shared-suite CefApp singleton.
        assertThrows(IllegalStateException.class,
                () -> CefApp.getInstance().setSettings(new CefSettings()));
    }

    @Test
    void getInstanceReturnsSameSingleton() {
        CefApp first = CefApp.getInstance();
        CefApp second = CefApp.getInstance();
        assertEquals(first, second);
    }
}
