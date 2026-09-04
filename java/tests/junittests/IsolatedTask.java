// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import java.util.Map;

// One unit of work dispatched into a disposable, process-isolated JVM+CefApp
// by IsolatedRunner -- see IsolatedRunner's class comment for the full
// design and why this exists (JVM-global CEF state, like the real
// browser-process CefCommandLine, that can't safely be touched inside the
// shared long-lived suite process).
//
// Implementations run inside the CHILD process (IsolatedTaskRunner
// instantiates them there via reflection, so a public no-arg constructor is
// required) and must return only simple string-keyed/string-valued data --
// see IsolatedRunner's comment for why (java.util.Properties-based
// marshaling over the child's stdout, not real object serialization).
// Implementations may also read state set into TestSetupContext by
// TestSetupExtension's CefApp-startup callbacks (e.g. captured command-line
// snapshots) -- that machinery already runs once per child process, before
// this task's run() is invoked.
interface IsolatedTask {
    Map<String, String> run() throws Exception;
}
