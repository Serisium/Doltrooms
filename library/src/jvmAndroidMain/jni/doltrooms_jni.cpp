// JNI glue between dev.seri.doltrooms.driver.DoltLiteNative and the
// DoltLite amalgamation (sqlite3_* symbols preserved; see the
// androidx-sqlite skill's bundled-driver-internals for the template).
#include <jni.h>

#include "doltlite.h"

namespace {

jstring NativeLibVersion(JNIEnv* env, jobject) {
    return env->NewStringUTF(sqlite3_libversion());
}

// JNINativeMethod's name/signature fields are non-const char* in jni.h.
const JNINativeMethod kMethods[] = {
    {const_cast<char*>("nativeLibVersion"), const_cast<char*>("()Ljava/lang/String;"),
     reinterpret_cast<void*>(NativeLibVersion)},
};

}  // namespace

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    jclass cls = env->FindClass("dev/seri/doltrooms/driver/DoltLiteNative");
    if (cls == nullptr) {
        return JNI_ERR;
    }
    if (env->RegisterNatives(cls, kMethods, sizeof(kMethods) / sizeof(kMethods[0])) != JNI_OK) {
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}
