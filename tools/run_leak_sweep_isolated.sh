#!/bin/bash
# Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
# reserved. Use of this source code is governed by a BSD-style license
# that can be found in the LICENSE file.
#
# Per-target process-isolated leak sweep driver -- thin wrapper preserving
# this script's original CLI contract. The actual pooling/dispatch logic
# used to live here in bash; it's been migrated to
# java/tests/junittests/LeakSweepIsolatedTest.java (built on the general
# IsolatedTask/IsolatedRunner process-isolation harness, see
# IsolatedRunner.java's class comment) so this project has only one
# isolation mechanism to understand/maintain, not a bash-side one and a
# Java-side one. See plan/study/leak-checker-port.md's 2026-08-30
# pooling-design status for the original rationale (still accurate): one
# fresh JVM+CEF subprocess per target, up to poolSize at a time, because
# running every target serially in one long-lived CefApp session was
# proven to produce real carryover-contamination between targets.
#
# Usage (unchanged):
#   tools/run_leak_sweep_isolated.sh <platform> <Debug|Release> [poolSize] [-Dleak.foo=bar ...]
#
# tools/run_tests.sh has no route for a `-D` JVM flag (it forwards trailing
# args straight to the JUnit console launcher jar, which doesn't understand
# them -- see IsolatedRunner.java's class comment for the full story), so
# any trailing -Dkey=value args here are translated into identically-named
# environment variables for LeakSweepIsolatedTest to read instead (it
# checks both, see that class's propertyOrEnv()).

set -u

if [ -z "${1:-}" ]; then
  echo "ERROR: Please specify a target platform: linux32 or linux64"
  exit 1
fi
if [ -z "${2:-}" ]; then
  echo "ERROR: Please specify a build type: Debug or Release"
  exit 1
fi

PLATFORM="$1"
BUILD_TYPE="$2"
shift 2

# No default here -- LeakSweepIsolatedTest.DEFAULT_POOL_SIZE
# (Runtime.availableProcessors()) applies if poolSize is omitted; only set
# the env var at all when the caller passed one explicitly.
ENV_ARGS=()
if [ -n "${1:-}" ] && [[ "$1" =~ ^[0-9]+$ ]]; then
  ENV_ARGS+=("leak.poolSize=$1")
  shift
fi

DIR="$( cd "$( dirname "$0" )" && cd .. && pwd )"

for arg in "$@"; do
  if [[ "$arg" == -D*=* ]]; then
    ENV_ARGS+=("${arg#-D}")
  else
    echo "ERROR: Unrecognized trailing argument '$arg' -- expected -Dkey=value" >&2
    exit 1
  fi
done

env "${ENV_ARGS[@]}" "${DIR}/tools/run_tests.sh" "$PLATFORM" "$BUILD_TYPE" \
    --select-class tests.junittests.LeakSweepIsolatedTest
