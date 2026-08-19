/*
 * JNI entry points exposed to com.sconcept.mirrordash.airplay.AirPlayNative.
 * This version starts the full RAOP/FairPlay receiver stack from the vendored
 * UxPlay sources, then forwards decrypted H.264 access units to Kotlin where
 * MediaCodec renders them onto an Android Surface.
 */

#include <android/log.h>
#include <jni.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "dnssd.h"
#include "jni_globals.h"
#include "logger.h"
#include "raop.h"

#define LOG_TAG "AirPlayNative"
#define CODEC_H264 1
#define CODEC_H265 2
/* AirPlay mirroring's audio compression type (raop_rtp.c's `ct`): always 8 (AAC-ELD 44.1kHz/2ch)
 * in practice - confirmed against a real iOS sender and matches upstream UxPlay's own
 * audio_renderer.c, which only ever wires up decoders for ct 8 (AAC-ELD) and ct 2 (ALAC, used for
 * non-mirroring "Audio Only" AirPlay, not mirroring). Kept as its own constant (rather than a
 * CODEC_* remap like video's) since it's already the stable wire value, not a uxplay-internal enum. */
#define AUDIO_CODEC_AAC_ELD 8

typedef struct receiver_state_s {
    dnssd_t *dnssd;
    raop_t *raop;
    char device_id[18];
    char password[128];
    unsigned short raop_port;
    unsigned short airplay_port;
    unsigned short display[5];
    unsigned short tcp[3];
    unsigned short udp[3];
    int auth_mode;
    int allow_hevc;
    int current_codec;
    int video_width;
    int video_height;
    int format_announced;
} receiver_state_t;

static receiver_state_t g_receiver = {0};

static void android_log_callback(void *cls, int level, const char *msg)
{
    (void) cls;
    int priority = ANDROID_LOG_INFO;
    if (level <= LOGGER_ERR) {
        priority = ANDROID_LOG_ERROR;
    } else if (level == LOGGER_WARNING) {
        priority = ANDROID_LOG_WARN;
    } else if (level >= LOGGER_DEBUG) {
        priority = ANDROID_LOG_DEBUG;
    }
    __android_log_print(priority, LOG_TAG, "%s", msg);
}

static jobject get_bridge(JNIEnv **env_out)
{
    JNIEnv *env = jni_globals_get_env();
    if (!env) {
        return NULL;
    }
    jobject bridge = jni_globals_get_bridge();
    if (!bridge) {
        jni_globals_release_env();
        return NULL;
    }
    if (env_out) {
        *env_out = env;
    }
    return bridge;
}

static jmethodID get_bridge_method(JNIEnv *env, jobject bridge, const char *name, const char *signature, jclass *cls_out)
{
    jclass cls = (*env)->GetObjectClass(env, bridge);
    if (!cls) {
        return NULL;
    }
    jmethodID method = (*env)->GetMethodID(env, cls, name, signature);
    if (!method) {
        (*env)->ExceptionClear(env);
    }
    if (cls_out) {
        *cls_out = cls;
    } else {
        (*env)->DeleteLocalRef(env, cls);
    }
    return method;
}

static void clear_pending_exception(JNIEnv *env)
{
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
    }
}

static void call_receiver_started(int port)
{
    JNIEnv *env = NULL;
    jobject bridge = get_bridge(&env);
    if (!bridge) {
        return;
    }

    jclass cls = NULL;
    jmethodID method = get_bridge_method(env, bridge, "onReceiverStarted", "(I)V", &cls);
    if (method) {
        (*env)->CallVoidMethod(env, bridge, method, (jint) port);
        clear_pending_exception(env);
    }
    if (cls) (*env)->DeleteLocalRef(env, cls);
    jni_globals_release_env();
}

static void call_receiver_stopped(void)
{
    JNIEnv *env = NULL;
    jobject bridge = get_bridge(&env);
    if (!bridge) {
        return;
    }

    jclass cls = NULL;
    jmethodID method = get_bridge_method(env, bridge, "onReceiverStopped", "()V", &cls);
    if (method) {
        (*env)->CallVoidMethod(env, bridge, method);
        clear_pending_exception(env);
    }
    if (cls) (*env)->DeleteLocalRef(env, cls);
    jni_globals_release_env();
}

static void call_client_changed(const char *device_id, const char *model, const char *name)
{
    JNIEnv *env = NULL;
    jobject bridge = get_bridge(&env);
    if (!bridge) {
        return;
    }

    jclass cls = NULL;
    jmethodID method = get_bridge_method(
        env,
        bridge,
        "onClientChanged",
        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
        &cls
    );
    if (method) {
        jstring jDeviceId = (*env)->NewStringUTF(env, device_id ? device_id : "");
        jstring jModel = (*env)->NewStringUTF(env, model ? model : "");
        jstring jName = (*env)->NewStringUTF(env, name ? name : "");
        (*env)->CallVoidMethod(env, bridge, method, jDeviceId, jModel, jName);
        clear_pending_exception(env);
        (*env)->DeleteLocalRef(env, jDeviceId);
        (*env)->DeleteLocalRef(env, jModel);
        (*env)->DeleteLocalRef(env, jName);
    }
    if (cls) (*env)->DeleteLocalRef(env, cls);
    jni_globals_release_env();
}

static void call_video_format_changed(int codec, int width, int height)
{
    JNIEnv *env = NULL;
    jobject bridge = get_bridge(&env);
    if (!bridge) {
        return;
    }

    jclass cls = NULL;
    jmethodID method = get_bridge_method(env, bridge, "onVideoFormatChanged", "(III)V", &cls);
    if (method) {
        (*env)->CallVoidMethod(env, bridge, method, (jint) codec, (jint) width, (jint) height);
        clear_pending_exception(env);
    }
    if (cls) (*env)->DeleteLocalRef(env, cls);
    jni_globals_release_env();
}

static void call_video_frame(const unsigned char *data, int len, uint64_t pts_us)
{
    JNIEnv *env = NULL;
    jobject bridge = get_bridge(&env);
    if (!bridge || !data || len <= 0) {
        return;
    }

    jclass cls = NULL;
    jmethodID method = get_bridge_method(env, bridge, "onVideoFrame", "([BJ)V", &cls);
    if (method) {
        jbyteArray frame = (*env)->NewByteArray(env, len);
        if (frame) {
            (*env)->SetByteArrayRegion(env, frame, 0, len, (const jbyte *) data);
            (*env)->CallVoidMethod(env, bridge, method, frame, (jlong) pts_us);
            clear_pending_exception(env);
            (*env)->DeleteLocalRef(env, frame);
        }
    }
    if (cls) (*env)->DeleteLocalRef(env, cls);
    jni_globals_release_env();
}

static void call_audio_frame(const unsigned char *data, int len, uint64_t pts_us)
{
    JNIEnv *env = NULL;
    jobject bridge = get_bridge(&env);
    if (!bridge || !data || len <= 0) {
        return;
    }

    jclass cls = NULL;
    jmethodID method = get_bridge_method(env, bridge, "onAudioFrame", "([BJ)V", &cls);
    if (method) {
        jbyteArray frame = (*env)->NewByteArray(env, len);
        if (frame) {
            (*env)->SetByteArrayRegion(env, frame, 0, len, (const jbyte *) data);
            (*env)->CallVoidMethod(env, bridge, method, frame, (jlong) pts_us);
            clear_pending_exception(env);
            (*env)->DeleteLocalRef(env, frame);
        }
    }
    if (cls) (*env)->DeleteLocalRef(env, cls);
    jni_globals_release_env();
}

static void call_video_session_ended(void)
{
    JNIEnv *env = NULL;
    jobject bridge = get_bridge(&env);
    if (!bridge) {
        return;
    }

    jclass cls = NULL;
    jmethodID method = get_bridge_method(env, bridge, "onVideoSessionEnded", "()V", &cls);
    if (method) {
        (*env)->CallVoidMethod(env, bridge, method);
        clear_pending_exception(env);
    }
    if (cls) (*env)->DeleteLocalRef(env, cls);
    jni_globals_release_env();
}

static void call_pin_requested(const char *pin)
{
    JNIEnv *env = NULL;
    jobject bridge = get_bridge(&env);
    if (!bridge) {
        return;
    }

    jclass cls = NULL;
    jmethodID method = get_bridge_method(env, bridge, "onPinRequested", "(Ljava/lang/String;)V", &cls);
    if (method) {
        jstring jPin = (*env)->NewStringUTF(env, pin ? pin : "");
        (*env)->CallVoidMethod(env, bridge, method, jPin);
        clear_pending_exception(env);
        (*env)->DeleteLocalRef(env, jPin);
    }
    if (cls) (*env)->DeleteLocalRef(env, cls);
    jni_globals_release_env();
}

static void maybe_announce_video_format(void)
{
    if (g_receiver.format_announced) {
        return;
    }
    if (g_receiver.current_codec != CODEC_H264 && g_receiver.current_codec != CODEC_H265) {
        return;
    }
    if (g_receiver.video_width <= 0 || g_receiver.video_height <= 0) {
        return;
    }
    call_video_format_changed(g_receiver.current_codec, g_receiver.video_width, g_receiver.video_height);
    g_receiver.format_announced = 1;
}

static void reset_video_session_state(void)
{
    g_receiver.current_codec = 0;
    g_receiver.video_width = 0;
    g_receiver.video_height = 0;
    g_receiver.format_announced = 0;
}

static void cleanup_receiver(int notify)
{
    reset_video_session_state();

    if (g_receiver.dnssd) {
        dnssd_unregister_raop(g_receiver.dnssd);
        dnssd_unregister_airplay(g_receiver.dnssd);
    }
    if (g_receiver.raop) {
        raop_destroy(g_receiver.raop);
        g_receiver.raop = NULL;
    }
    if (g_receiver.dnssd) {
        dnssd_destroy(g_receiver.dnssd);
        g_receiver.dnssd = NULL;
    }

    g_receiver.raop_port = 0;
    g_receiver.airplay_port = 0;
    memset(g_receiver.display, 0, sizeof(g_receiver.display));
    memset(g_receiver.tcp, 0, sizeof(g_receiver.tcp));
    memset(g_receiver.udp, 0, sizeof(g_receiver.udp));
    memset(g_receiver.device_id, 0, sizeof(g_receiver.device_id));

    if (notify) {
        call_receiver_stopped();
    }
}

static void set_device_id_string(const unsigned char *hw_addr, int hw_len, char output[18])
{
    if (hw_len < 6) {
        strncpy(output, "00:00:00:00:00:00", 18);
        return;
    }
    snprintf(
        output,
        18,
        "%02X:%02X:%02X:%02X:%02X:%02X",
        hw_addr[0],
        hw_addr[1],
        hw_addr[2],
        hw_addr[3],
        hw_addr[4],
        hw_addr[5]
    );
}

static void configure_feature_flags(dnssd_t *dnssd)
{
    dnssd_set_airplay_features(dnssd, 0, 0);
    dnssd_set_airplay_features(dnssd, 1, 1);
    dnssd_set_airplay_features(dnssd, 2, 1);
    dnssd_set_airplay_features(dnssd, 3, 0);
    dnssd_set_airplay_features(dnssd, 4, 0);
    dnssd_set_airplay_features(dnssd, 5, 1);
    dnssd_set_airplay_features(dnssd, 6, 1);
    dnssd_set_airplay_features(dnssd, 7, 1);
    dnssd_set_airplay_features(dnssd, 8, 0);
    dnssd_set_airplay_features(dnssd, 9, 1);
    dnssd_set_airplay_features(dnssd, 10, 1);
    dnssd_set_airplay_features(dnssd, 11, 1);
    dnssd_set_airplay_features(dnssd, 12, 1);
    dnssd_set_airplay_features(dnssd, 13, 1);
    dnssd_set_airplay_features(dnssd, 14, 1);
    dnssd_set_airplay_features(dnssd, 15, 1);
    dnssd_set_airplay_features(dnssd, 16, 1);
    dnssd_set_airplay_features(dnssd, 17, 1);
    dnssd_set_airplay_features(dnssd, 18, 1);
    dnssd_set_airplay_features(dnssd, 19, 1);
    dnssd_set_airplay_features(dnssd, 20, 1);
    dnssd_set_airplay_features(dnssd, 21, 1);
    dnssd_set_airplay_features(dnssd, 22, 1);
    dnssd_set_airplay_features(dnssd, 23, 0);
    dnssd_set_airplay_features(dnssd, 24, 0);
    dnssd_set_airplay_features(dnssd, 25, 1);
    dnssd_set_airplay_features(dnssd, 26, 0);
    dnssd_set_airplay_features(dnssd, 27, 0);
    dnssd_set_airplay_features(dnssd, 28, 1);
    dnssd_set_airplay_features(dnssd, 29, 0);
    dnssd_set_airplay_features(dnssd, 30, 1);
    dnssd_set_airplay_features(dnssd, 31, 0);
    dnssd_set_airplay_features(dnssd, 42, g_receiver.allow_hevc ? 1 : 0);
}

static void conn_init(void *cls)
{
    (void) cls;
}

static void conn_destroy(void *cls)
{
    (void) cls;
    reset_video_session_state();
    call_video_session_ended();
}

static void conn_feedback(void *cls)
{
    (void) cls;
}

static void conn_reset(void *cls, int reason)
{
    (void) cls;
    (void) reason;
    reset_video_session_state();
    call_video_session_ended();
}

static void audio_process(void *cls, raop_ntp_t *ntp, audio_decode_struct *data)
{
    (void) cls;
    (void) ntp;
    if (!data || !data->data || data->data_len <= 0) {
        return;
    }
    if (data->ct != AUDIO_CODEC_AAC_ELD) {
        /* ALAC ("Audio Only" AirPlay, ct=2) and anything else isn't decoded on the Android side
         * yet - screen mirroring (this app's use case) always negotiates AAC-ELD in practice. */
        static int warned_ct = -1;
        if (data->ct != warned_ct) {
            __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "Dropping unsupported audio compression type ct=%d", data->ct);
            warned_ct = data->ct;
        }
        return;
    }
    call_audio_frame(data->data, data->data_len, data->ntp_time_remote / 1000ULL);
}

static void video_process(void *cls, raop_ntp_t *ntp, video_decode_struct *data)
{
    (void) cls;
    (void) ntp;
    if (!data || !data->data || data->data_len <= 0) {
        return;
    }
    if (data->data[0] != 0x00) {
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "Dropping undecipherable video packet");
        return;
    }
    maybe_announce_video_format();
    call_video_frame(data->data, data->data_len, data->ntp_time_remote / 1000ULL);
}

static void video_pause(void *cls)
{
    (void) cls;
}

static void video_resume(void *cls)
{
    (void) cls;
}

static void audio_flush(void *cls)
{
    (void) cls;
}

static void video_flush(void *cls)
{
    (void) cls;
}

static void audio_set_volume(void *cls, float volume)
{
    (void) cls;
    (void) volume;
}

static double audio_set_client_volume(void *cls)
{
    (void) cls;
    return 0.0;
}

static void audio_set_metadata(void *cls, const void *buffer, int buflen)
{
    (void) cls;
    (void) buffer;
    (void) buflen;
}

static void audio_set_coverart(void *cls, const void *buffer, int buflen)
{
    (void) cls;
    (void) buffer;
    (void) buflen;
}

static void audio_stop_coverart_rendering(void *cls)
{
    (void) cls;
}

static void audio_set_progress(void *cls, uint32_t *start, uint32_t *curr, uint32_t *end)
{
    (void) cls;
    (void) start;
    (void) curr;
    (void) end;
}

static void audio_get_format(
    void *cls,
    unsigned char *ct,
    unsigned short *spf,
    bool *using_screen,
    bool *is_media,
    uint64_t *audio_format
) {
    (void) cls;
    __android_log_print(
        ANDROID_LOG_INFO, LOG_TAG,
        "audio_get_format: ct=%d spf=%d usingScreen=%d isMedia=%d audioFormat=0x%llx",
        ct ? *ct : -1, spf ? *spf : -1, using_screen ? *using_screen : -1,
        is_media ? *is_media : -1, audio_format ? (unsigned long long) *audio_format : 0
    );
}

static void video_report_size(void *cls, float *width_source, float *height_source, float *width, float *height)
{
    (void) cls;
    int reported_width = (width && *width > 0.0f) ? (int) (*width) : 0;
    int reported_height = (height && *height > 0.0f) ? (int) (*height) : 0;
    if (reported_width <= 0 && width_source) {
        reported_width = (int) (*width_source);
    }
    if (reported_height <= 0 && height_source) {
        reported_height = (int) (*height_source);
    }
    g_receiver.video_width = reported_width;
    g_receiver.video_height = reported_height;
    maybe_announce_video_format();
}

static void mirror_video_running(void *cls, bool is_running)
{
    (void) cls;
    if (!is_running) {
        reset_video_session_state();
        call_video_session_ended();
    }
}

static void report_client_request(void *cls, char *deviceid, char *model, char *name, bool *admit)
{
    (void) cls;
    if (admit) {
        *admit = true;
    }
    call_client_changed(deviceid, model, name);
}

static void display_pin(void *cls, char *pin)
{
    (void) cls;
    call_pin_requested(pin);
}

static void register_client(void *cls, const char *device_id, const char *pk_str, const char *name)
{
    (void) cls;
    (void) device_id;
    (void) pk_str;
    (void) name;
}

static bool check_register(void *cls, const char *pk_str)
{
    (void) cls;
    (void) pk_str;
    return false;
}

static const char *passwd(void *cls, int *len)
{
    (void) cls;
    if (!len) {
        return NULL;
    }
    if (g_receiver.auth_mode == 2 && g_receiver.password[0] != '\0') {
        *len = (int) strlen(g_receiver.password);
        return g_receiver.password;
    }
    if (g_receiver.auth_mode == 1) {
        *len = -1;
        return NULL;
    }
    *len = 0;
    return NULL;
}

static void export_dacp(void *cls, const char *active_remote, const char *dacp_id)
{
    (void) cls;
    (void) active_remote;
    (void) dacp_id;
}

static int video_set_codec(void *cls, video_codec_t codec)
{
    (void) cls;
    if (codec == VIDEO_CODEC_H265 && !g_receiver.allow_hevc) {
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "Rejecting H.265 AirPlay stream on Android receiver");
        return -1;
    }
    g_receiver.current_codec = codec == VIDEO_CODEC_H265 ? CODEC_H265 : CODEC_H264;
    g_receiver.format_announced = 0;
    maybe_announce_video_format();
    return 0;
}

static void video_reset(void *cls, reset_type_t reset_type)
{
    (void) cls;
    (void) reset_type;
    reset_video_session_state();
    call_video_session_ended();
}

static void on_video_play(void *cls, const char *location, const float start_position)
{
    (void) cls;
    (void) location;
    (void) start_position;
}

static void on_video_scrub(void *cls, const float position)
{
    (void) cls;
    (void) position;
}

static void on_video_rate(void *cls, const float rate)
{
    (void) cls;
    (void) rate;
}

static void on_video_stop(void *cls)
{
    (void) cls;
    reset_video_session_state();
    call_video_session_ended();
}

static void on_video_acquire_playback_info(void *cls, playback_info_t *playback_video)
{
    (void) cls;
    if (!playback_video) {
        return;
    }
    memset(playback_video, 0, sizeof(playback_info_t));
}

static float on_video_playlist_remove(void *cls)
{
    (void) cls;
    return 0.0f;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved)
{
    (void) reserved;
    jni_globals_set_vm(vm);
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL
Java_com_sconcept_mirrordash_airplay_AirPlayNative_nativeSetCallback(
    JNIEnv *env, jclass clazz, jobject callback
) {
    (void) clazz;
    jni_globals_set_bridge(env, callback);
}

JNIEXPORT jint JNICALL
Java_com_sconcept_mirrordash_airplay_AirPlayNative_nativeStartReceiver(
    JNIEnv *env,
    jclass clazz,
    jstring name,
    jbyteArray hwAddr,
    jint displayWidth,
    jint displayHeight,
    jint authMode,
    jstring password,
    jboolean allowHevc
) {
    (void) clazz;
    if (g_receiver.raop || g_receiver.dnssd) {
        return -1;
    }

    const char *name_chars = (*env)->GetStringUTFChars(env, name, NULL);
    const char *password_chars = password ? (*env)->GetStringUTFChars(env, password, NULL) : NULL;
    jsize hw_len = (*env)->GetArrayLength(env, hwAddr);
    jbyte *hw_bytes = (*env)->GetByteArrayElements(env, hwAddr, NULL);

    int result = 0;
    int dnssd_error = DNSSD_ERROR_NOERROR;
    set_device_id_string((const unsigned char *) hw_bytes, (int) hw_len, g_receiver.device_id);
    g_receiver.auth_mode = authMode;
    g_receiver.allow_hevc = allowHevc ? 1 : 0;
    memset(g_receiver.password, 0, sizeof(g_receiver.password));
    if (password_chars) {
        snprintf(g_receiver.password, sizeof(g_receiver.password), "%s", password_chars);
    }

    g_receiver.display[0] = (unsigned short) (displayWidth > 0 ? displayWidth : 1280);
    g_receiver.display[1] = (unsigned short) (displayHeight > 0 ? displayHeight : 720);
    g_receiver.display[2] = 60;
    g_receiver.display[3] = 30;
    g_receiver.display[4] = 0;

    g_receiver.dnssd = dnssd_init(
        name_chars,
        (int) strlen(name_chars),
        (const char *) hw_bytes,
        (int) hw_len,
        0,
        &dnssd_error
    );
    if (!g_receiver.dnssd) {
        result = dnssd_error ? dnssd_error : -2;
        goto cleanup;
    }
    configure_feature_flags(g_receiver.dnssd);

    raop_callbacks_t callbacks;
    memset(&callbacks, 0, sizeof(callbacks));
    callbacks.cls = &g_receiver;
    callbacks.conn_init = conn_init;
    callbacks.conn_destroy = conn_destroy;
    callbacks.conn_feedback = conn_feedback;
    callbacks.conn_reset = conn_reset;
    callbacks.audio_process = audio_process;
    callbacks.video_process = video_process;
    callbacks.video_pause = video_pause;
    callbacks.video_resume = video_resume;
    callbacks.audio_flush = audio_flush;
    callbacks.video_flush = video_flush;
    callbacks.audio_set_client_volume = audio_set_client_volume;
    callbacks.audio_set_volume = audio_set_volume;
    callbacks.audio_set_metadata = audio_set_metadata;
    callbacks.audio_set_coverart = audio_set_coverart;
    callbacks.audio_stop_coverart_rendering = audio_stop_coverart_rendering;
    callbacks.audio_set_progress = audio_set_progress;
    callbacks.audio_get_format = audio_get_format;
    callbacks.video_report_size = video_report_size;
    callbacks.mirror_video_running = mirror_video_running;
    callbacks.report_client_request = report_client_request;
    callbacks.display_pin = display_pin;
    callbacks.register_client = register_client;
    callbacks.check_register = check_register;
    callbacks.passwd = passwd;
    callbacks.export_dacp = export_dacp;
    callbacks.video_set_codec = video_set_codec;
    callbacks.video_reset = video_reset;
    callbacks.on_video_play = on_video_play;
    callbacks.on_video_scrub = on_video_scrub;
    callbacks.on_video_rate = on_video_rate;
    callbacks.on_video_stop = on_video_stop;
    callbacks.on_video_acquire_playback_info = on_video_acquire_playback_info;
    callbacks.on_video_playlist_remove = on_video_playlist_remove;

    g_receiver.raop = raop_init(&callbacks);
    if (!g_receiver.raop) {
        result = -3;
        goto cleanup;
    }
    raop_set_log_callback(g_receiver.raop, android_log_callback, NULL);
    raop_set_log_level(g_receiver.raop, LOGGER_INFO);

    if (raop_init2(g_receiver.raop, 0, g_receiver.device_id, "")) {
        result = -4;
        goto cleanup;
    }

    raop_set_plist(g_receiver.raop, "width", (int) g_receiver.display[0]);
    raop_set_plist(g_receiver.raop, "height", (int) g_receiver.display[1]);
    raop_set_plist(g_receiver.raop, "refreshRate", (int) g_receiver.display[2]);
    raop_set_plist(g_receiver.raop, "maxFPS", (int) g_receiver.display[3]);
    raop_set_plist(g_receiver.raop, "overscanned", (int) g_receiver.display[4]);

    raop_set_tcp_ports(g_receiver.raop, g_receiver.tcp);
    raop_set_udp_ports(g_receiver.raop, g_receiver.udp);

    g_receiver.raop_port = raop_get_port(g_receiver.raop);
    /* UxPlay's httpd_start() returns:
     *   1  -> started successfully
     *   0  -> already running
     *  <0  -> actual failure
     */
    if (raop_start_httpd(g_receiver.raop, &g_receiver.raop_port) < 0) {
        result = -5;
        goto cleanup;
    }
    raop_set_port(g_receiver.raop, g_receiver.raop_port);
    g_receiver.airplay_port = g_receiver.raop_port;
    raop_set_dnssd(g_receiver.raop, g_receiver.dnssd);

    if (dnssd_register_raop(g_receiver.dnssd, g_receiver.raop_port) != 0) {
        result = -6;
        goto cleanup;
    }
    if (dnssd_register_airplay(g_receiver.dnssd, g_receiver.airplay_port) != 0) {
        result = -7;
        goto cleanup;
    }

cleanup:
    (*env)->ReleaseByteArrayElements(env, hwAddr, hw_bytes, JNI_ABORT);
    (*env)->ReleaseStringUTFChars(env, name, name_chars);
    if (password_chars) {
        (*env)->ReleaseStringUTFChars(env, password, password_chars);
    }

    if (result != 0) {
        cleanup_receiver(0);
        return result;
    }

    call_receiver_started((int) g_receiver.raop_port);
    return 0;
}

JNIEXPORT void JNICALL
Java_com_sconcept_mirrordash_airplay_AirPlayNative_nativeStopReceiver(
    JNIEnv *env, jclass clazz
) {
    (void) env;
    (void) clazz;
    cleanup_receiver(1);
}

JNIEXPORT jboolean JNICALL
Java_com_sconcept_mirrordash_airplay_AirPlayNative_nativeIsRunning(
    JNIEnv *env, jclass clazz
) {
    (void) env;
    (void) clazz;
    return g_receiver.raop != NULL ? JNI_TRUE : JNI_FALSE;
}
