#include "jni_globals.h"
#include <stddef.h>

static JavaVM *g_vm = NULL;
static jobject g_bridge = NULL;

/* per-thread: did jni_globals_get_env() itself attach this thread? if so,
 * jni_globals_release_env() must detach it; if the thread was already a
 * JVM thread (e.g. called from a JNI entry point directly), leave it alone */
static _Thread_local int t_did_attach = 0;

void jni_globals_set_vm(JavaVM *vm) {
    g_vm = vm;
}

void jni_globals_set_bridge(JNIEnv *env, jobject bridge) {
    if (g_bridge) {
        (*env)->DeleteGlobalRef(env, g_bridge);
        g_bridge = NULL;
    }
    if (bridge) {
        g_bridge = (*env)->NewGlobalRef(env, bridge);
    }
}

JNIEnv *jni_globals_get_env(void) {
    if (!g_vm) return NULL;

    JNIEnv *env = NULL;
    jint result = (*g_vm)->GetEnv(g_vm, (void **) &env, JNI_VERSION_1_6);
    if (result == JNI_OK) {
        t_did_attach = 0;
        return env;
    }
    if (result == JNI_EDETACHED) {
        if ((*g_vm)->AttachCurrentThread(g_vm, &env, NULL) == 0) {
            t_did_attach = 1;
            return env;
        }
    }
    return NULL;
}

void jni_globals_release_env(void) {
    if (g_vm && t_did_attach) {
        (*g_vm)->DetachCurrentThread(g_vm);
        t_did_attach = 0;
    }
}

jobject jni_globals_get_bridge(void) {
    return g_bridge;
}
