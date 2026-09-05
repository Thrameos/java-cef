#!/bin/bash
# Copyright (c) 2019 The Chromium Embedded Framework Authors. All rights
# reserved. Use of this source code is governed by a BSD-style license
# that can be found in the LICENSE file.

if [ -z "$1" ]; then
  echo "ERROR: Please specify a target platform: linux32 or linux64"
else
  if [ -z "$2" ]; then
    echo "ERROR: Please specify a build type: Debug or Release"
  else
    DIR="$( cd "$( dirname "$0" )" && cd .. && pwd )"
    OUT_PATH="${DIR}/out/$1"

    LIB_PATH="${DIR}/jcef_build/native/$2"
    if [ ! -d "$LIB_PATH" ]; then
      echo "ERROR: Native build output path does not exist"
      exit 1
    fi

    # Note: a trailing "/*" is only expanded into a jar list by the real `java`
    # launcher's own -cp/-classpath handling, which we can't use together with
    # -jar below. The JUnit console launcher's own -cp option does not expand
    # it, so the jogamp jars must be listed explicitly here.
    CLS_PATH="$OUT_PATH"
    for jar in "${DIR}"/third_party/jogamp/jar/*.jar; do
      CLS_PATH="${jar}:${CLS_PATH}"
    done

    # Necessary for jcef_helper to find libcef.so.
    if [ -n "$LD_LIBRARY_PATH" ]; then
      LD_LIBRARY_PATH="$LIB_PATH:${LD_LIBRARY_PATH}"
    else
      LD_LIBRARY_PATH="$LIB_PATH"
    fi
    export LD_LIBRARY_PATH

    # Remove the first two params ($1 and $2) and pass the rest to java.
    shift
    shift

    # Default to the whole tests.junittests package, but only if the caller
    # didn't pass their own JUnit console launcher selector (--select-class,
    # --select-package, etc). JUnit ORs multiple selectors together rather
    # than intersecting them, so appending our default alongside a caller's
    # --select-class silently widens the run back out to the whole package --
    # a "run just this one class" invocation was actually running the whole
    # suite. See commit 7678597 on coverage/phase1-value-objects-phase2-handlers.
    SELECT_ARGS=(--select-package tests.junittests)
    for arg in "$@"; do
      case "$arg" in
        --select-*|-s)
          SELECT_ARGS=()
          break
          ;;
      esac
    done

    # leak-sweep/process-isolated tests spawn a pool of isolated CEF
    # subprocesses (each its own browser+renderer+GPU+zygote process tree),
    # fanning out to dozens of real processes/windows. That must never be
    # something this general-purpose script can trigger as a side effect of
    # an otherwise-unrelated invocation (e.g. one scoped to a single
    # --select-class for a quick local repro) -- it's a real resource-
    # exhaustion risk, not just noise. Always excluded here, unconditionally
    # (no flag on this script re-enables it); run leak-sweep coverage only
    # via the dedicated tools/run_leak_sweep_isolated.sh.
    TAG_ARGS=(--exclude-tag leak-sweep --exclude-tag process-isolated)

    LD_PRELOAD=libcef.so java -Djava.library.path="$LIB_PATH" -jar "${DIR}"/third_party/junit/junit-platform-console-standalone-*.jar -cp "$CLS_PATH" "${SELECT_ARGS[@]}" "${TAG_ARGS[@]}" "$@"
  fi
fi

