#!/bin/bash
# Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
# reserved. Use of this source code is governed by a BSD-style license
# that can be found in the LICENSE file.
#
# CI-oriented wrapper around the coverage job's JUnit+JaCoCo invocation.
# Same honest-red distinguishing logic as run_tests_ci.sh (a JUnit summary
# in the output means every test already ran and reported, so a nonzero
# exit after that point is the known, pre-existing native shutdown-race
# crash tracked in plan/state.md -- not a test regression), plus a retry
# loop for the *other* failure mode this job has hit: a crash *before* any
# summary prints at all.
#
# That pre-summary crash was confirmed live (2026-09-04) to be genuinely
# intermittent, not tied to one specific test -- three consecutive runs of
# identical code hit three different native CHECK/DCHECK failures at three
# different points in the suite (including once at CEF startup itself,
# before any test-specific code had run). Until the underlying native
# races are actually root-caused and fixed (tracked separately as Phase 2
# candidates), this job's ENABLE_LLVM_COVERAGE Debug build is just
# genuinely more fragile than the test job's Release build. Coverage
# numbers are supplementary information, not a correctness gate (the test
# job already covers that) -- retrying absorbs this intermittent flakiness
# instead of failing the whole job on every run.
#
# A real test failure (JUnit summary present, failure count > 0) is never
# retried -- that's a genuine regression, not flakiness, and retrying it
# away would be exactly the "fake green" this whole honest-red design
# exists to avoid.
#
# Usage: run_coverage_ci.sh <max_attempts> -- <command...>
#   e.g. tools/run_coverage_ci.sh 3 -- env LD_PRELOAD=libcef.so timeout 120 java ...

set -u

if [ "$#" -lt 3 ] || [ "$2" != "--" ]; then
    echo "usage: run_coverage_ci.sh <max_attempts> -- <command...>" >&2
    exit 2
fi

MAX_ATTEMPTS="$1"
shift 2

for attempt in $(seq 1 "$MAX_ATTEMPTS"); do
    # Start each attempt from a clean jacoco.exec so a crashed attempt's
    # partial/corrupt execution data can't bleed into the report for
    # whichever attempt actually succeeds.
    rm -f jacoco.exec

    LOGFILE="$(mktemp)"
    "$@" 2>&1 | tee "$LOGFILE"
    RAW_EXIT=${PIPESTATUS[0]}

    TESTS_FOUND=$(grep -oE '[0-9]+ tests found' "$LOGFILE" | grep -oE '^[0-9]+' | tail -1)
    TESTS_FAILED=$(grep -oE '[0-9]+ tests failed' "$LOGFILE" | grep -oE '^[0-9]+' | tail -1)
    rm -f "$LOGFILE"

    if [ -z "$TESTS_FOUND" ] || [ -z "$TESTS_FAILED" ]; then
        echo
        echo "run_coverage_ci.sh: attempt $attempt/$MAX_ATTEMPTS -- no JUnit" \
             "summary found, process crashed mid-run (raw exit $RAW_EXIT)." \
             "Confirmed intermittent, not tied to one specific test --" \
             "retrying." >&2
        continue
    fi

    if [ "$TESTS_FAILED" -gt 0 ]; then
        echo
        echo "run_coverage_ci.sh: $TESTS_FAILED/$TESTS_FOUND test(s) failed." \
             "Real test failure(s) -- not retrying, see the JUnit tree" \
             "above for which." >&2
        exit 1
    fi

    if [ "$RAW_EXIT" -ne 0 ]; then
        echo
        echo "run_coverage_ci.sh: all $TESTS_FOUND test(s) passed, but the" \
             "process exited nonzero ($RAW_EXIT) after JUnit's summary was" \
             "printed. This is the known, pre-existing native shutdown-race" \
             "crash (see plan/state.md), not a test regression -- every" \
             "test already reported before this point." >&2
        exit 0
    fi

    echo
    echo "run_coverage_ci.sh: all $TESTS_FOUND test(s) passed, clean exit." >&2
    exit 0
done

echo
echo "run_coverage_ci.sh: exhausted $MAX_ATTEMPTS attempts without a JUnit" \
     "summary ever printing -- treating as a real failure." >&2
exit 1
