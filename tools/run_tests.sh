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

    LD_PRELOAD=libcef.so java -Djava.library.path="$LIB_PATH" -jar "${DIR}"/third_party/junit/junit-platform-console-standalone-*.jar -cp "$CLS_PATH" --select-package tests.junittests "$@"
  fi
fi

