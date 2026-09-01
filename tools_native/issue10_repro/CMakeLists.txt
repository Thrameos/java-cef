# Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
# reserved. Use of this source code is governed by a BSD-style license that
# can be found in the LICENSE file.

# Standalone diagnostic executable, NOT part of the JCEF product build --
# see issue10_repro.cc's own comment and tools_native/leak_probe's sibling
# CMakeLists.txt (this file mirrors it exactly).

set(ISSUE10_REPRO_TARGET "issue10_repro")

add_executable(${ISSUE10_REPRO_TARGET} issue10_repro.cc)
SET_EXECUTABLE_TARGET_PROPERTIES(${ISSUE10_REPRO_TARGET})
add_dependencies(${ISSUE10_REPRO_TARGET} libcef_dll_wrapper)
target_link_libraries(${ISSUE10_REPRO_TARGET} libcef_lib libcef_dll_wrapper ${CEF_STANDARD_LIBS})

set_target_properties(${ISSUE10_REPRO_TARGET} PROPERTIES INSTALL_RPATH "$ORIGIN")
set_target_properties(${ISSUE10_REPRO_TARGET} PROPERTIES BUILD_WITH_INSTALL_RPATH TRUE)
