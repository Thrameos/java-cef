# Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
# reserved. Use of this source code is governed by a BSD-style license that
# can be found in the LICENSE file.

# Standalone diagnostic shared library, NOT part of the JCEF product build
# and NOT linked into libjcef.so -- see JniNoOpProbe.cpp's own comment and
# java/tests/junittests/JniNoOpProbe.java's. Deliberately its own tiny .so
# (loaded via System.loadLibrary("jni_noop_probe"), not bundled into the
# product library) so this diagnostic-only code never ships as part of
# libjcef.so -- the mistake this was moved out of native/ to fix, see
# plan/LeakCheckerPort.md's 2026-08-30 status. Needs only JNI headers, no
# CEF/libcef linking at all -- the whole point is testing the JNI-crossing
# mechanism with zero CEF involvement.

set(JNI_NOOP_PROBE_TARGET "jni_noop_probe")

add_library(${JNI_NOOP_PROBE_TARGET} SHARED JniNoOpProbe.cpp)
SET_LIBRARY_TARGET_PROPERTIES(${JNI_NOOP_PROBE_TARGET})
target_include_directories(${JNI_NOOP_PROBE_TARGET} PUBLIC ${JNI_INCLUDE_DIRS})

# CMAKE_LIBRARY_OUTPUT_DIRECTORY is already set (by native/CMakeLists.txt's
# SET_CEF_TARGET_OUT_DIR() call, inherited via the add_subdirectory() call
# below) to the same directory as libjcef.so -- java.library.path already
# covers it for System.loadLibrary(), no separate copy step needed.
