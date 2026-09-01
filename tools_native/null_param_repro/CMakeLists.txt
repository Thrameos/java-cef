# Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
# reserved. Use of this source code is governed by a BSD-style license that
# can be found in the LICENSE file.

# Standalone diagnostic executable, NOT part of the JCEF product build --
# see null_param_repro.cc's own comment and tools_native/leak_probe's
# sibling CMakeLists.txt (this file mirrors it exactly).

set(NULL_PARAM_REPRO_TARGET "null_param_repro")

add_executable(${NULL_PARAM_REPRO_TARGET} null_param_repro.cc)
SET_EXECUTABLE_TARGET_PROPERTIES(${NULL_PARAM_REPRO_TARGET})
add_dependencies(${NULL_PARAM_REPRO_TARGET} libcef_dll_wrapper)
target_link_libraries(${NULL_PARAM_REPRO_TARGET} libcef_lib libcef_dll_wrapper ${CEF_STANDARD_LIBS})

set_target_properties(${NULL_PARAM_REPRO_TARGET} PROPERTIES INSTALL_RPATH "$ORIGIN")
set_target_properties(${NULL_PARAM_REPRO_TARGET} PROPERTIES BUILD_WITH_INSTALL_RPATH TRUE)
