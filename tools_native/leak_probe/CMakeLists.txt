# Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
# reserved. Use of this source code is governed by a BSD-style license that
# can be found in the LICENSE file.

# Standalone diagnostic executable, NOT part of the JCEF product build -- see
# leak_probe.cc's own comment and plan/LeakCheckerPort.md's 2026-08-30
# status. Lives under tools_native/ (sibling to tools/ for shell scripts and
# native/ for shipped product source), not inside native/, so it's never
# mistaken for or accidentally linked into shipped product code -- see that
# status section's own note on this repo not previously having a "native
# tooling, not shipped" location the way tools/ already serves for scripts.
# Reuses this build's already-configured libcef_lib/libcef_dll_wrapper
# targets (see native/CMakeLists.txt, which add_subdirectory()s this
# directory) rather than setting up a separate CEF distribution checkout.

set(LEAK_PROBE_TARGET "leak_probe")

add_executable(${LEAK_PROBE_TARGET} leak_probe.cc)
SET_EXECUTABLE_TARGET_PROPERTIES(${LEAK_PROBE_TARGET})
add_dependencies(${LEAK_PROBE_TARGET} libcef_dll_wrapper)
target_link_libraries(${LEAK_PROBE_TARGET} libcef_lib libcef_dll_wrapper ${CEF_STANDARD_LIBS})

set_target_properties(${LEAK_PROBE_TARGET} PROPERTIES INSTALL_RPATH "$ORIGIN")
set_target_properties(${LEAK_PROBE_TARGET} PROPERTIES BUILD_WITH_INSTALL_RPATH TRUE)

# CMAKE_RUNTIME_OUTPUT_DIRECTORY is already set (by native/CMakeLists.txt's
# SET_CEF_TARGET_OUT_DIR() call, inherited into this directory's scope via
# its add_subdirectory() call below) to the same directory as
# jcef_helper/libjcef.so/*.pak/locales -- no separate copy step needed,
# unlike a resource file that isn't a build output.
