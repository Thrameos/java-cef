// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// Exercises CefSchemeRegistrar (no public Java factory -- only obtainable via
// CefAppHandler.onRegisterCustomSchemes(), which fires once, synchronously,
// during CefApp startup, before any @Test runs -- same harness-capture shape
// TestSetupExtension already uses for CefCommandLine). TestSetupExtension
// registers the same scheme name twice back to back in that callback and
// stashes both boolean results via TestSetupContext, so this test can assert
// on both the happy path (first registration succeeds) and the unhappy path
// (duplicate registration fails), per addCustomScheme()'s own Javadoc. See
// plan/roadmap.md Track A item 6.
@ExtendWith(TestSetupExtension.class)
class CefSchemeRegistrarTest {
    @Test
    void capturedResultsAreNotNull() {
        assertNotNull(TestSetupContext.getCapturedSchemeRegistrationResults(),
                "TestSetupExtension.onRegisterCustomSchemes never fired");
    }

    @Test
    void firstRegistrationOfANewSchemeSucceeds() {
        TestSetupContext.SchemeRegistrationResults results =
                TestSetupContext.getCapturedSchemeRegistrationResults();
        assertNotNull(results);
        assertTrue(results.firstResult, "First registration of a new scheme should succeed");
    }

    @Test
    void duplicateRegistrationOfTheSameSchemeFails() {
        TestSetupContext.SchemeRegistrationResults results =
                TestSetupContext.getCapturedSchemeRegistrationResults();
        assertNotNull(results);
        assertFalse(results.secondResult,
                "Re-registering the same scheme name should fail per addCustomScheme()'s "
                        + "documented contract");
    }
}
