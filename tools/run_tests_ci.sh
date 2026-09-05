#!/bin/bash
# Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
# reserved. Use of this source code is governed by a BSD-style license
# that can be found in the LICENSE file.
#
# CI-oriented wrapper around run_tests.sh. Exists because of a known,
# pre-existing native race (see plan/state.md's "Known non-test-scoped
# issues" entry) that reliably aborts (SIGABRT) during CefApp.dispose()
# shutdown -- strictly *after* every real test has already run and
# reported its result. run_tests.sh's own raw exit code conflates that
# harness-level crash with an actual test failure, which would either
# make every CI run permanently red for a reason nobody can see in the
# JUnit summary, or tempt someone into silently swallowing the exit code
# (the "fake green" this wrapper deliberately does not do).
#
# This script runs run_tests.sh as a plain child process (no isolation
# machinery needed here -- the crash happens after JUnit's own summary
# is already flushed to stdout, so waiting on this one child is enough
# to always complete, never hang) and parses the JUnit console summary
# out of its captured output, so CI can see, distinctly:
#   - real test failures (still fails CI, obviously), vs.
#   - zero test failures but a nonzero process exit (still fails CI --
#     honest red, not swallowed -- but reported as exactly what it is:
#     the known harness-shutdown crash, not a test regression).
#
# Usage: identical to run_tests.sh, e.g.:
#   tools/run_tests_ci.sh linux64 Release --exclude-tag leak-sweep --exclude-tag process-isolated

set -u

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOGFILE="$(mktemp)"
trap 'rm -f "$LOGFILE"' EXIT

"${SCRIPT_DIR}/run_tests.sh" "$@" > "$LOGFILE" 2>&1
RAW_EXIT=$?

cat "$LOGFILE"

# JUnit console-launcher summary lines look like:
#   [         9 tests found           ]
#   [         0 tests failed          ]
#   [         9 tests successful      ]
TESTS_FOUND=$(grep -oE '[0-9]+ tests found' "$LOGFILE" | grep -oE '^[0-9]+' | tail -1)
TESTS_FAILED=$(grep -oE '[0-9]+ tests failed' "$LOGFILE" | grep -oE '^[0-9]+' | tail -1)

if [ -z "$TESTS_FOUND" ] || [ -z "$TESTS_FAILED" ]; then
    echo
    echo "run_tests_ci.sh: no JUnit summary found in output -- the process" \
         "likely crashed or hung before any test could report a result." \
         "Treating as a real failure (raw exit code $RAW_EXIT)."
    exit 1
fi

if [ "$TESTS_FAILED" -gt 0 ]; then
    echo
    echo "run_tests_ci.sh: $TESTS_FAILED/$TESTS_FOUND test(s) failed. Real" \
         "test failure(s) -- see the JUnit tree above for which."
    exit 1
fi

if [ "$RAW_EXIT" -ne 0 ]; then
    echo
    echo "run_tests_ci.sh: all $TESTS_FOUND test(s) passed, but the process" \
         "exited nonzero ($RAW_EXIT) after JUnit's summary was printed." \
         "This is the known, pre-existing native shutdown-race crash" \
         "tracked in plan/state.md, not a test regression -- still" \
         "reported as a failing CI run (honest red), not swallowed."
    exit 1
fi

echo
echo "run_tests_ci.sh: all $TESTS_FOUND test(s) passed, clean exit."
exit 0
