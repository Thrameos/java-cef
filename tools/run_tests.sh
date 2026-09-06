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

    # Staleness check: a source edit (native or Java) with no rebuild before
    # this run is indistinguishable, from the test output alone, from a real
    # regression -- and has repeatedly cost real debugging time chasing a
    # bug that was actually just an old binary. Fail loudly and specifically
    # instead. Grep for "^STALE_BUILD_ERROR:" to find this check's output.
    check_stale_build() {
      local artifact="$1" src_root="$2" rebuild_hint="$3"
      if [ ! -e "$artifact" ]; then
        echo "STALE_BUILD_ERROR: build artifact '$artifact' does not exist -- $rebuild_hint" >&2
        return 1
      fi
      local newer
      newer=$(find "$src_root" -type f \
        \( -name '*.java' -o -name '*.cpp' -o -name '*.cc' -o -name '*.h' \
           -o -name '*.mm' -o -name 'CMakeLists.txt' \) \
        -newer "$artifact" 2>/dev/null | head -1)
      if [ -n "$newer" ]; then
        echo "STALE_BUILD_ERROR: '$newer' is newer than '$artifact' -- $rebuild_hint" >&2
        return 1
      fi
      return 0
    }
    STALE=0
    check_stale_build "$LIB_PATH/libjcef.so" "${DIR}/native" \
      "rebuild native first (ninja -C jcef_build jcef)" || STALE=1
    check_stale_build "$LIB_PATH/libjcef.so" "${DIR}/CMakeLists.txt" \
      "rebuild native first (ninja -C jcef_build jcef)" || STALE=1
    check_stale_build "$OUT_PATH/org/cef/CefApp.class" "${DIR}/java" \
      "recompile Java first (tools/compile.sh $1)" || STALE=1
    if [ "$STALE" -ne 0 ]; then
      echo "STALE_BUILD_ERROR: refusing to run tests against a stale build (set JCEF_SKIP_STALE_CHECK=1 to override)" >&2
      if [ -z "${JCEF_SKIP_STALE_CHECK:-}" ]; then
        exit 1
      fi
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

    LD_PRELOAD=libcef.so java -Djava.library.path="$LIB_PATH" -jar "${DIR}"/third_party/junit/junit-platform-console-standalone-*.jar -cp "$CLS_PATH" --select-package tests.junittests "$@"
  fi
fi

