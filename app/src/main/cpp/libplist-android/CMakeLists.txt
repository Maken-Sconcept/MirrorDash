# Builds libplist's plain-C API (libplist-2.0) directly from the vendored
# submodule sources, bypassing its autotools build entirely. libplist has no
# external dependencies beyond a handful of POSIX headers/functions that
# Bionic already provides (see config.h in this directory). libplist's core
# C implementation still relies on libcnary's node helpers internally, so we
# build those sources into the same static library.

set(LIBPLIST_SRC ${CMAKE_CURRENT_SOURCE_DIR}/../libplist/src)
set(LIBCNARY_SRC ${CMAKE_CURRENT_SOURCE_DIR}/../libplist/libcnary)

add_library(plist STATIC
    ${LIBPLIST_SRC}/base64.c
    ${LIBPLIST_SRC}/bytearray.c
    ${LIBPLIST_SRC}/hashtable.c
    ${LIBPLIST_SRC}/ptrarray.c
    ${LIBPLIST_SRC}/time64.c
    ${LIBPLIST_SRC}/common.c
    ${LIBPLIST_SRC}/xplist.c
    ${LIBPLIST_SRC}/bplist.c
    ${LIBPLIST_SRC}/jsmn.c
    ${LIBPLIST_SRC}/jplist.c
    ${LIBPLIST_SRC}/oplist.c
    ${LIBPLIST_SRC}/out-default.c
    ${LIBPLIST_SRC}/out-plutil.c
    ${LIBPLIST_SRC}/out-limd.c
    ${LIBPLIST_SRC}/plist.c
    ${LIBCNARY_SRC}/node.c
    ${LIBCNARY_SRC}/node_list.c
)

target_include_directories(plist
    PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/../libplist/include
    PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${LIBPLIST_SRC}
        ${CMAKE_CURRENT_SOURCE_DIR}/../libplist/libcnary/include
)

target_compile_definitions(plist PRIVATE HAVE_CONFIG_H)
