#!/bin/bash
# Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
# reserved. Use of this source code is governed by a BSD-style license
# that can be found in the LICENSE file.
#
# Per-target process-isolated leak sweep driver. See
# plan/LeakCheckerPort.md's 2026-08-30 pooling-design status for the full
# rationale: java/tests/junittests/LeakSweepTest.java's normal
# sweepAllTargetsForLeaks() runs every LeakTarget serially in one
# long-lived CefApp session, which was proven to produce real
# carryover-contamination between targets. This script instead launches
# one fresh JVM+CEF subprocess per target (-Dleak.isolated=true
# -Dleak.target.filter=<exact name>), up to POOL_SIZE at a time. Each
# subprocess hard-exits via Runtime.halt() as soon as its target's result
# is known, deliberately skipping CefApp's own shutdown path (see the
# isolated-mode comment in LeakSweepTest.java for why: that path is a
# documented, known-crashing one -- issue java-cef#4 / #22/#23 -- and a
# disposable subprocess never needs a clean shutdown, only to report its
# result before it dies).
#
# Usage:
#   tools/run_leak_sweep_isolated.sh <platform> <Debug|Release> [poolSize] [-Dleak.foo=bar ...]
#
# <platform>/<Debug|Release> match tools/run_tests.sh exactly. poolSize
# defaults to 1 (fully serial, one subprocess at a time) -- deliberately
# conservative: proving correctness (no more carryover contamination)
# matters more than wall-clock time until this driver itself has been
# exercised for real. Raise it once that's established -- the whole point
# of the pool design is that isolation doesn't have to mean serial.
# Any trailing -Dxxx=yyy args are forwarded to every per-target JVM
# (e.g. -Dleak.numBatches=50 for a deeper sweep).

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

POOL_SIZE=1
if [ -n "${1:-}" ] && [[ "$1" =~ ^[0-9]+$ ]]; then
  POOL_SIZE="$1"
  shift
fi

# A -Dleak.target.filter=<substring> among the trailing args narrows which
# *discovered* targets this driver actually runs (e.g. for a quick smoke
# test of the driver itself) -- pulled out here rather than forwarded
# straight through, since each subprocess already gets its own
# -Dleak.target.filter=<exact name> below and a second, conflicting
# -D of the same property on the same command line would just clobber it
# (last one wins), silently making every subprocess run the same target.
DRIVER_FILTER=""
EXTRA_JVM_ARGS=()
for arg in "$@"; do
  if [[ "$arg" == -Dleak.target.filter=* ]]; then
    DRIVER_FILTER="${arg#-Dleak.target.filter=}"
  else
    EXTRA_JVM_ARGS+=("$arg")
  fi
done

DIR="$( cd "$( dirname "$0" )" && cd .. && pwd )"
OUT_PATH="${DIR}/out/${PLATFORM}"
LIB_PATH="${DIR}/jcef_build/native/${BUILD_TYPE}"
if [ ! -d "$LIB_PATH" ]; then
  echo "ERROR: Native build output path does not exist: $LIB_PATH"
  exit 1
fi

CLS_PATH="$OUT_PATH"
for jar in "${DIR}"/third_party/jogamp/jar/*.jar; do
  CLS_PATH="${jar}:${CLS_PATH}"
done

if [ -n "${LD_LIBRARY_PATH:-}" ]; then
  export LD_LIBRARY_PATH="$LIB_PATH:${LD_LIBRARY_PATH}"
else
  export LD_LIBRARY_PATH="$LIB_PATH"
fi

JUNIT_JAR=$(ls "${DIR}"/third_party/junit/junit-platform-console-standalone-*.jar 2>/dev/null | head -1)
if [ -z "$JUNIT_JAR" ]; then
  echo "ERROR: JUnit console launcher jar not found under third_party/junit/"
  exit 1
fi

WORKDIR=$(mktemp -d /tmp/jcef_leak_sweep.XXXXXX)
echo "Per-target logs: ${WORKDIR}"

run_one_target() {
  local name="$1"
  local safe_name
  safe_name=$(echo "$name" | tr -c 'A-Za-z0-9._-' '_')
  local log="${WORKDIR}/${safe_name}.log"
  # Own cache dir per subprocess -- see TestSetupExtension.java's
  # leak.cachePath comment: sharing the default profile dir across
  # hard-exiting subprocesses causes the next one to trip CEF's
  # singleton-collision fatal path on startup.
  local cache_dir="${WORKDIR}/cache_${safe_name}"
  mkdir -p "$cache_dir"
  LD_PRELOAD=libcef.so java -Djava.library.path="$LIB_PATH" \
      -Dleak.isolated=true -Dleak.target.filter="$name" -Dleak.cachePath="$cache_dir" \
      "${EXTRA_JVM_ARGS[@]}" \
      -jar "${JUNIT_JAR}" -cp "$CLS_PATH" --select-class tests.junittests.LeakSweepTest \
      >"$log" 2>&1
  local exit_code=$?
  echo "$exit_code" >"${log}.exitcode"
}

# Step 1: enumerate targets via -Dleak.listTargets=true (still pays CEF
# startup cost once, but only once -- see LeakSweepTest.java's comment on
# why a bare name-per-line format wasn't reliable here).
LIST_LOG="${WORKDIR}/_list.log"
LD_PRELOAD=libcef.so java -Djava.library.path="$LIB_PATH" -Dleak.listTargets=true \
    -jar "${JUNIT_JAR}" -cp "$CLS_PATH" --select-class tests.junittests.LeakSweepTest \
    >"$LIST_LOG" 2>&1
mapfile -t TARGETS < <(grep -o 'LEAK_SWEEP_TARGET: .*' "$LIST_LOG" | sed 's/^LEAK_SWEEP_TARGET: //')

if [ "${#TARGETS[@]}" -eq 0 ]; then
  echo "ERROR: no targets discovered -- see $LIST_LOG"
  exit 1
fi

if [ -n "$DRIVER_FILTER" ]; then
  FILTERED=()
  for name in "${TARGETS[@]}"; do
    [[ "$name" == *"$DRIVER_FILTER"* ]] && FILTERED+=("$name")
  done
  TARGETS=("${FILTERED[@]}")
  if [ "${#TARGETS[@]}" -eq 0 ]; then
    echo "ERROR: leak.target.filter='$DRIVER_FILTER' matched no discovered targets"
    exit 1
  fi
fi

echo "Discovered ${#TARGETS[@]} target(s) to run, pool size ${POOL_SIZE}."

# Step 2: run each target in its own subprocess, up to POOL_SIZE at a time.
declare -a PIDS=()
RUNNING=0
for name in "${TARGETS[@]}"; do
  # Safety net for LeakSweepTest.java's substring-based leak.target.filter:
  # refuse to proceed if this name isn't unique against the full list (see
  # the isolated-mode comment there for why exact-match uniqueness matters).
  matches=0
  for other in "${TARGETS[@]}"; do
    [[ "$other" == *"$name"* ]] && matches=$((matches + 1))
  done
  if [ "$matches" -ne 1 ]; then
    echo "ERROR: target name '$name' is not a unique substring match against the full" \
         "target list (leak.target.filter is substring-based) -- refusing to run it" \
         "in isolation, its subprocess could silently pick up the wrong target."
    exit 1
  fi

  run_one_target "$name" &
  PIDS+=("$!")
  RUNNING=$((RUNNING + 1))
  if [ "$RUNNING" -ge "$POOL_SIZE" ]; then
    wait -n
    RUNNING=$((RUNNING - 1))
  fi
done
wait

# Step 3: summarize.
echo
echo "=== Isolated leak-sweep summary ==="
FAIL_COUNT=0
for name in "${TARGETS[@]}"; do
  safe_name=$(echo "$name" | tr -c 'A-Za-z0-9._-' '_')
  log="${WORKDIR}/${safe_name}.log"
  code_file="${log}.exitcode"
  code=$(cat "$code_file" 2>/dev/null || echo "?")
  result_line=$(grep -o 'LEAK_SWEEP_RESULT: .*' "$log" 2>/dev/null | tail -1)
  case "$code" in
    0) verdict="PASS" ;;
    1) verdict="LEAK" ;;
    *) verdict="ERROR(exit=$code)"; FAIL_COUNT=$((FAIL_COUNT + 1)) ;;
  esac
  echo "$verdict  $name  ${result_line}"
  [ -z "$result_line" ] && echo "    (no result line -- see $log)"
done
echo
echo "Per-target logs kept at: ${WORKDIR}"

exit 0
