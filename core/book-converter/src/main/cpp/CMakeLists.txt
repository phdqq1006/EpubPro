cmake_minimum_required(VERSION 3.22.1)
project(epubpro_book_converter C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(USE_ENCRYPTION OFF CACHE BOOL "" FORCE)
set(USE_XMLWRITER ON CACHE BOOL "" FORCE)
set(USE_LIBXML2 OFF CACHE BOOL "" FORCE)
set(USE_ZLIB OFF CACHE BOOL "" FORCE)
set(USE_MINIZ ON CACHE BOOL "" FORCE)
set(PACKAGE_VERSION "0.12.0")
set(PACKAGE_VERSION_MAJOR 0)

add_compile_definitions(
    USE_XMLWRITER
    USE_MINIZ
    HAVE_UNISTD_H
    HAVE_STRDUP
    PACKAGE_VERSION="0.12.0"
)

add_subdirectory(libmobi/src libmobi-build)

# libmobi được liên kết vào thư viện JNI nên luôn cần mã position-independent.
set_target_properties(mobi PROPERTIES POSITION_INDEPENDENT_CODE ON)
if(TARGET miniz)
    set_target_properties(miniz PROPERTIES POSITION_INDEPENDENT_CODE ON)
endif()

add_library(book_converter SHARED mobi_jni.c)
target_include_directories(book_converter PRIVATE libmobi/src)
target_link_libraries(book_converter PRIVATE mobi log)
target_link_options(book_converter PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
