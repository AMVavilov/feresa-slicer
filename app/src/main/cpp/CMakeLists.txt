cmake_minimum_required(VERSION 3.22.1)
project(feresa_slicer LANGUAGES CXX)

# Keep the JNI bridge as an explicit Android shared-library target so Gradle
# packages libferesa_slicer.so into every APK ABI selected by the application.

add_library(slicer_engine STATIC
    slicer_core.cpp
)

target_include_directories(slicer_engine PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_features(slicer_engine PUBLIC cxx_std_17)

if(ANDROID)
    add_library(feresa_slicer SHARED
        native_bridge.cpp
    )

    target_link_libraries(feresa_slicer
        PRIVATE
        slicer_engine
        log
    )
endif()

option(FERESA_SLICER_BUILD_TESTS "Build native host tests" OFF)
if(FERESA_SLICER_BUILD_TESTS AND NOT ANDROID)
    enable_testing()
    add_executable(slicer_engine_test tests/slicer_engine_test.cpp)
    target_link_libraries(slicer_engine_test PRIVATE slicer_engine)
    add_test(NAME slicer_engine_test COMMAND slicer_engine_test)
endif()
