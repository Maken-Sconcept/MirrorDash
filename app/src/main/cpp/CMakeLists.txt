cmake_minimum_required(VERSION 3.22.1)
project(berthieroptions_airplay)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 11)

# --- Prebuilt OpenSSL (cross-compiled per-ABI outside Gradle - see
#     app/src/main/cpp/openssl-android-build/<abi>/, produced from the
#     vendored openssl/ submodule via its own Configure+make) ---
set(OPENSSL_ANDROID_DIR ${CMAKE_CURRENT_SOURCE_DIR}/openssl-android-build/${ANDROID_ABI})

add_library(ssl_prebuilt STATIC IMPORTED)
set_target_properties(ssl_prebuilt PROPERTIES
    IMPORTED_LOCATION ${OPENSSL_ANDROID_DIR}/lib/libssl.a
    INTERFACE_INCLUDE_DIRECTORIES ${OPENSSL_ANDROID_DIR}/include
)

add_library(crypto_prebuilt STATIC IMPORTED)
set_target_properties(crypto_prebuilt PROPERTIES
    IMPORTED_LOCATION ${OPENSSL_ANDROID_DIR}/lib/libcrypto.a
    INTERFACE_INCLUDE_DIRECTORIES ${OPENSSL_ANDROID_DIR}/include
)

# --- libplist (built directly from vendored source, see libplist-android/) ---
add_subdirectory(libplist-android)

# --- UxPlay vendored pieces that are already fully self-contained/portable ---
set(UXPLAY_DIR ${CMAKE_CURRENT_SOURCE_DIR}/uxplay)

add_library(llhttp STATIC
    ${UXPLAY_DIR}/lib/llhttp/api.c
    ${UXPLAY_DIR}/lib/llhttp/http.c
    ${UXPLAY_DIR}/lib/llhttp/llhttp.c
)
target_include_directories(llhttp PUBLIC ${UXPLAY_DIR}/lib/llhttp)

add_library(playfair STATIC
    ${UXPLAY_DIR}/lib/playfair/hand_garble.c
    ${UXPLAY_DIR}/lib/playfair/modified_md5.c
    ${UXPLAY_DIR}/lib/playfair/omg_hax.c
    ${UXPLAY_DIR}/lib/playfair/playfair.c
    ${UXPLAY_DIR}/lib/playfair/sap_hash.c
)
target_include_directories(playfair PUBLIC ${UXPLAY_DIR}/lib/playfair)

# --- UxPlay's core protocol/crypto/buffer layer (platform-portable: POSIX
#     sockets + pthreads only, verified clean of X11/DBus/Win32 assumptions).
#     Excludes: renderers/ (desktop GStreamer output - needs a MediaCodec/
#     AudioTrack replacement, not a port) and the mDNS *backend* half of
#     dnssd (dnssd.c's TXT-record building is portable and included below;
#     its dnssd_private_init/register/unregister calls will be implemented
#     against Android's NsdManager via JNI in a later pass - until then this
#     compiles fine as a static lib, it just can't be linked into a final
#     executable yet). ---
add_library(airplay STATIC
    ${UXPLAY_DIR}/lib/airplay_video.c
    ${UXPLAY_DIR}/lib/byteutils.c
    ${UXPLAY_DIR}/lib/compat.c
    ${UXPLAY_DIR}/lib/crypto.c
    ${UXPLAY_DIR}/lib/dnssd.c
    ${UXPLAY_DIR}/lib/fairplay_playfair.c
    ${UXPLAY_DIR}/lib/http_request.c
    ${UXPLAY_DIR}/lib/http_response.c
    ${UXPLAY_DIR}/lib/httpd.c
    ${UXPLAY_DIR}/lib/logger.c
    ${UXPLAY_DIR}/lib/mirror_buffer.c
    ${UXPLAY_DIR}/lib/netutils.c
    ${UXPLAY_DIR}/lib/pairing.c
    ${UXPLAY_DIR}/lib/raop.c
    ${UXPLAY_DIR}/lib/raop_buffer.c
    ${UXPLAY_DIR}/lib/raop_ntp.c
    ${UXPLAY_DIR}/lib/raop_rtp.c
    ${UXPLAY_DIR}/lib/raop_rtp_mirror.c
    ${UXPLAY_DIR}/lib/srp.c
    ${UXPLAY_DIR}/lib/utils.c
)
target_include_directories(airplay PUBLIC
    ${UXPLAY_DIR}/lib
    ${UXPLAY_DIR}/lib/playfair
    ${UXPLAY_DIR}/lib/llhttp
)
target_link_libraries(airplay PUBLIC
    playfair
    llhttp
    plist
    ssl_prebuilt
    crypto_prebuilt
)
target_compile_definitions(airplay PRIVATE
    NOHOLD
    __STDC_CONSTANT_MACROS
    __STDC_LIMIT_MACROS
    TARGET_POSIX
    _REENTRANT
    _LARGEFILE64_SOURCE
    _FILE_OFFSET_BITS=64
    # selects the modern libplist API shape (>=2.1.0 / >=2.3.0) in UxPlay's
    # own version-conditional code - the vendored libplist is 2.7.0
    PLIST_210
    PLIST_230
)

# --- Android glue: the dnssd backend (NsdManager via JNI) that dnssd.c
#     above needs, plus the JNI entry points themselves. This is the only
#     genuinely-new native code in this module - everything above is
#     compiled-as-is vendored C. Built as the actual loadable .so. ---
add_library(airplay_jni SHARED
    jni-bridge/jni_globals.c
    jni-bridge/dnssd_android.c
    jni-bridge/airplay_jni.c
)
target_include_directories(airplay_jni PRIVATE
    jni-bridge
    ${UXPLAY_DIR}/lib
)
target_link_libraries(airplay_jni PRIVATE
    airplay
    log
)
