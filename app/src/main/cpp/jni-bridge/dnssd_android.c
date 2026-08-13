/*
 * Android backend for UxPlay's lib/dnssd.h interface. UxPlay's own backends
 * (lib/dns_sd - Bonjour/Avahi, lib/mdnsd - its internal responder) aren't
 * portable to Android as-is; this implements the same four-function seam
 * (dnssd_private_init/destroy, dnssd_register_raop/airplay,
 * dnssd_unregister_raop/airplay) by calling into Kotlin's AirPlayNsdBridge,
 * which does the actual broadcast via android.net.nsd.NsdManager.
 *
 * The name-preparation logic below (dnssd_prepare_names/hwaddr formatting)
 * is adapted from lib/mdnsd/dnssd_mdnsd.c, which is pure string manipulation
 * with no mdnsd-specific calls - only the actual service (un)registration is
 * replaced. The RAOP/AirPlay TXT record *contents* are UxPlay's own (see
 * lib/dnssdint.h for the RAOP_ and AIRPLAY_ constants), just transmitted as
 * individual key/value pairs over JNI instead of packed into a raw DNS TXT
 * wire-format blob, since NsdManager's setAttribute() does that packing for
 * us on the Kotlin side.
 */

#include <assert.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "compat.h"
#include "dnssd.h"
#include "dnssdint.h"
#include "utils.h"

#include "jni_globals.h"

#define MAX_TXT_PAIRS 20

typedef struct dnssd_private_s {
    char raop_name[256];
    char airplay_name[256];
    unsigned short raop_port;
    unsigned short airplay_port;
    int raop_registered;
    int airplay_registered;
} dnssd_private_t;

static int dnssd_prepare_names(dnssd_private_t *dnssd, dnssd_t *dnssd_public)
{
    char hwaddr[2 * MAX_HWADDR_LEN + 1];

    if (utils_hwaddr_raop(hwaddr, sizeof(hwaddr), dnssd_public->hw_addr, dnssd_public->hw_addr_len) < 0) {
        return -1;
    }
    if (snprintf(dnssd->raop_name, sizeof(dnssd->raop_name), "%s@%s", hwaddr, dnssd_public->name)
        >= (int) sizeof(dnssd->raop_name)) {
        return -1;
    }
    if (snprintf(dnssd->airplay_name, sizeof(dnssd->airplay_name), "%s", dnssd_public->name)
        >= (int) sizeof(dnssd->airplay_name)) {
        return -1;
    }
    return 0;
}

void *dnssd_private_init(dnssd_t *dnssd_public, int *error)
{
    dnssd_private_t *dnssd = (dnssd_private_t *) calloc(1, sizeof(dnssd_private_t));
    if (!dnssd) {
        if (error) *error = DNSSD_ERROR_OUTOFMEM;
        return NULL;
    }
    if (dnssd_prepare_names(dnssd, dnssd_public)) {
        free(dnssd);
        if (error) *error = DNSSD_ERROR_HWADDRLEN;
        return NULL;
    }
    if (error) *error = DNSSD_ERROR_NOERROR;
    return dnssd;
}

void dnssd_private_destroy(void *private)
{
    free(private);
}

/* Calls AirPlayNsdBridge.registerService(String serviceType, String
 * serviceName, int port, String[] keys, String[] values). Returns 0 on
 * success reaching the call, -1 if the JVM/bridge/method isn't available. */
static int call_register_service(
    const char *service_type,
    const char *service_name,
    unsigned short port,
    const char *keys[],
    const char *values[],
    int pair_count
) {
    JNIEnv *env = jni_globals_get_env();
    if (!env) return -1;
    jobject bridge = jni_globals_get_bridge();
    if (!bridge) {
        jni_globals_release_env();
        return -1;
    }

    jclass cls = (*env)->GetObjectClass(env, bridge);
    jmethodID mid = (*env)->GetMethodID(
        env, cls, "registerService",
        "(Ljava/lang/String;Ljava/lang/String;I[Ljava/lang/String;[Ljava/lang/String;)V"
    );
    if (!mid) {
        (*env)->ExceptionClear(env);
        jni_globals_release_env();
        return -1;
    }

    jclass stringClass = (*env)->FindClass(env, "java/lang/String");
    jobjectArray jKeys = (*env)->NewObjectArray(env, pair_count, stringClass, NULL);
    jobjectArray jValues = (*env)->NewObjectArray(env, pair_count, stringClass, NULL);
    for (int i = 0; i < pair_count; i++) {
        jstring k = (*env)->NewStringUTF(env, keys[i]);
        jstring v = (*env)->NewStringUTF(env, values[i] ? values[i] : "");
        (*env)->SetObjectArrayElement(env, jKeys, i, k);
        (*env)->SetObjectArrayElement(env, jValues, i, v);
        (*env)->DeleteLocalRef(env, k);
        (*env)->DeleteLocalRef(env, v);
    }

    jstring jServiceType = (*env)->NewStringUTF(env, service_type);
    jstring jServiceName = (*env)->NewStringUTF(env, service_name);

    (*env)->CallVoidMethod(env, bridge, mid, jServiceType, jServiceName, (jint) port, jKeys, jValues);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
    }

    (*env)->DeleteLocalRef(env, jServiceType);
    (*env)->DeleteLocalRef(env, jServiceName);
    (*env)->DeleteLocalRef(env, jKeys);
    (*env)->DeleteLocalRef(env, jValues);
    (*env)->DeleteLocalRef(env, stringClass);
    jni_globals_release_env();
    return 0;
}

static int call_unregister_service(const char *service_name)
{
    JNIEnv *env = jni_globals_get_env();
    if (!env) return -1;
    jobject bridge = jni_globals_get_bridge();
    if (!bridge) {
        jni_globals_release_env();
        return -1;
    }

    jclass cls = (*env)->GetObjectClass(env, bridge);
    jmethodID mid = (*env)->GetMethodID(env, cls, "unregisterService", "(Ljava/lang/String;)V");
    if (!mid) {
        (*env)->ExceptionClear(env);
        jni_globals_release_env();
        return -1;
    }

    jstring jServiceName = (*env)->NewStringUTF(env, service_name);
    (*env)->CallVoidMethod(env, bridge, mid, jServiceName);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
    }
    (*env)->DeleteLocalRef(env, jServiceName);
    jni_globals_release_env();
    return 0;
}

int dnssd_register_raop(dnssd_t *dnssd_public, unsigned short port)
{
    assert(dnssd_public);
    assert(dnssd_public->dnssd_private);
    dnssd_private_t *dnssd = (dnssd_private_t *) dnssd_public->dnssd_private;

    char features[22] = {0};
    const char *pw = "false";
    const char *sf = RAOP_SF;
    switch (dnssd_public->pin_pw) {
        case 1: pw = "true"; sf = "0x8c"; break;
        case 2:
        case 3: pw = "true"; sf = "0x84"; break;
        default: break;
    }
    snprintf(features, sizeof(features), "0x%X,0x%X", dnssd_public->features1, dnssd_public->features2);

    const char *keys[MAX_TXT_PAIRS];
    const char *values[MAX_TXT_PAIRS];
    int n = 0;
    keys[n] = "ch";      values[n++] = RAOP_CH;
    keys[n] = "cn";      values[n++] = RAOP_CN;
    keys[n] = "da";      values[n++] = RAOP_DA;
    keys[n] = "et";      values[n++] = RAOP_ET;
    keys[n] = "vv";      values[n++] = RAOP_VV;
    keys[n] = "ft";      values[n++] = features;
    keys[n] = "am";      values[n++] = GLOBAL_MODEL;
    keys[n] = "md";      values[n++] = RAOP_MD;
    keys[n] = "rhd";     values[n++] = RAOP_RHD;
    keys[n] = "pw";      values[n++] = pw;
    keys[n] = "sr";      values[n++] = RAOP_SR;
    keys[n] = "ss";      values[n++] = RAOP_SS;
    keys[n] = "sv";      values[n++] = RAOP_SV;
    keys[n] = "tp";      values[n++] = RAOP_TP;
    keys[n] = "txtvers"; values[n++] = RAOP_TXTVERS;
    keys[n] = "sf";      values[n++] = sf;
    keys[n] = "vs";      values[n++] = RAOP_VS;
    keys[n] = "vn";      values[n++] = RAOP_VN;
    keys[n] = "pk";      values[n++] = dnssd_public->pk;

    dnssd->raop_port = port;
    int ret = call_register_service("_raop._tcp", dnssd->raop_name, port, keys, values, n);
    dnssd->raop_registered = (ret == 0);
    return ret;
}

int dnssd_register_airplay(dnssd_t *dnssd_public, unsigned short port)
{
    assert(dnssd_public);
    assert(dnssd_public->dnssd_private);
    dnssd_private_t *dnssd = (dnssd_private_t *) dnssd_public->dnssd_private;

    char device_id[3 * MAX_HWADDR_LEN];
    char features[22] = {0};
    if (utils_hwaddr_airplay(device_id, sizeof(device_id), dnssd_public->hw_addr, dnssd_public->hw_addr_len) < 0) {
        return -1;
    }
    snprintf(features, sizeof(features), "0x%X,0x%X", dnssd_public->features1, dnssd_public->features2);

    const char *keys[MAX_TXT_PAIRS];
    const char *values[MAX_TXT_PAIRS];
    int n = 0;
    keys[n] = "deviceid"; values[n++] = device_id;
    keys[n] = "features"; values[n++] = features;
    keys[n] = "pw";       values[n++] = dnssd_public->pin_pw ? "true" : "false";
    keys[n] = "flags";    values[n++] = "0x4";
    keys[n] = "model";    values[n++] = GLOBAL_MODEL;
    keys[n] = "pk";       values[n++] = dnssd_public->pk;
    keys[n] = "pi";       values[n++] = AIRPLAY_PI;
    keys[n] = "srcvers";  values[n++] = AIRPLAY_SRCVERS;
    keys[n] = "vv";       values[n++] = AIRPLAY_VV;

    dnssd->airplay_port = port;
    int ret = call_register_service("_airplay._tcp", dnssd->airplay_name, port, keys, values, n);
    dnssd->airplay_registered = (ret == 0);
    return ret;
}

const char *dnssd_get_raop_txt(dnssd_t *dnssd_public, int *length)
{
    /* not used by any protocol logic (only lib/mdnsd's own backend consumes
     * these internally) - the Android backend builds/sends TXT attributes
     * directly to NsdManager in call_register_service() instead */
    if (length) *length = 0;
    return NULL;
}

const char *dnssd_get_airplay_txt(dnssd_t *dnssd_public, int *length)
{
    if (length) *length = 0;
    return NULL;
}

void dnssd_unregister_raop(dnssd_t *dnssd_public)
{
    assert(dnssd_public);
    assert(dnssd_public->dnssd_private);
    dnssd_private_t *dnssd = (dnssd_private_t *) dnssd_public->dnssd_private;
    if (dnssd->raop_registered) {
        call_unregister_service(dnssd->raop_name);
        dnssd->raop_registered = 0;
    }
}

void dnssd_unregister_airplay(dnssd_t *dnssd_public)
{
    assert(dnssd_public);
    assert(dnssd_public->dnssd_private);
    dnssd_private_t *dnssd = (dnssd_private_t *) dnssd_public->dnssd_private;
    if (dnssd->airplay_registered) {
        call_unregister_service(dnssd->airplay_name);
        dnssd->airplay_registered = 0;
    }
}

void dnssd_error_text(int *dnssd_error, const char *appname)
{
    (void) appname;
    fprintf(stderr, "dnssd_android: registration failed (error=%d)\n", dnssd_error ? *dnssd_error : 0);
}
