#ifndef BERTHIEROPTIONS_JNI_GLOBALS_H
#define BERTHIEROPTIONS_JNI_GLOBALS_H

#include <jni.h>

/* Shared JavaVM + callback-target state, set once from JNI_OnLoad /
 * nativeSetCallback, and used by native code that runs on UxPlay's own
 * pthreads (not JVM-created threads) to call back into Kotlin - e.g. the
 * dnssd Android backend registering services via NsdManager. */

void jni_globals_set_vm(JavaVM *vm);

/* Stores a global reference to the Kotlin AirPlayNsdBridge singleton. */
void jni_globals_set_bridge(JNIEnv *env, jobject bridge);

/* Attaches the calling thread to the JVM if needed and returns a valid
 * JNIEnv*, or NULL if no JavaVM has been set yet. Pair with
 * jni_globals_release_env() once done on this thread. */
JNIEnv *jni_globals_get_env(void);
void jni_globals_release_env(void);

/* Returns the global ref set by jni_globals_set_bridge(), or NULL. */
jobject jni_globals_get_bridge(void);

#endif
