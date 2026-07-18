// JNI glue between dev.seri.doltrooms.driver.DoltLiteNative and the
// DoltLite amalgamation (sqlite3_* symbols preserved; see the
// androidx-sqlite skill's bundled-driver-internals for the template).
#include <jni.h>

#include "doltlite.h"

namespace {

jstring NativeLibVersion(JNIEnv* env, jobject) {
    return env->NewStringUTF(sqlite3_libversion());
}

jlong NativeOpen(JNIEnv* env, jobject, jstring file_name, jint flags, jintArray rc_out) {
    const char* path = env->GetStringUTFChars(file_name, nullptr);
    sqlite3* db = nullptr;
    jint rc = sqlite3_open_v2(path, &db, flags, nullptr);
    env->ReleaseStringUTFChars(file_name, path);
    if (rc == SQLITE_OK) {
        sqlite3_extended_result_codes(db, 1);
    }
    env->SetIntArrayRegion(rc_out, 0, 1, &rc);
    return reinterpret_cast<jlong>(db);
}

void NativeClose(JNIEnv*, jobject, jlong db_pointer) {
    sqlite3_close_v2(reinterpret_cast<sqlite3*>(db_pointer));
}

jint NativePrepare(JNIEnv* env, jobject, jlong db_pointer, jstring sql, jlongArray stmt_out) {
    sqlite3* db = reinterpret_cast<sqlite3*>(db_pointer);
    const jchar* sql_chars = env->GetStringChars(sql, nullptr);
    const jsize sql_bytes = env->GetStringLength(sql) * 2;
    sqlite3_stmt* stmt = nullptr;
    jint rc = sqlite3_prepare16_v2(db, sql_chars, sql_bytes, &stmt, nullptr);
    env->ReleaseStringChars(sql, sql_chars);
    jlong stmt_handle = reinterpret_cast<jlong>(stmt);
    env->SetLongArrayRegion(stmt_out, 0, 1, &stmt_handle);
    return rc;
}

jint NativeStep(JNIEnv*, jobject, jlong stmt_pointer) {
    return sqlite3_step(reinterpret_cast<sqlite3_stmt*>(stmt_pointer));
}

void NativeFinalize(JNIEnv*, jobject, jlong stmt_pointer) {
    sqlite3_finalize(reinterpret_cast<sqlite3_stmt*>(stmt_pointer));
}

jstring NativeErrmsg(JNIEnv* env, jobject, jlong db_pointer) {
    // errmsg16: UTF-16 in native byte order, NUL-terminated.
    const jchar* msg =
        static_cast<const jchar*>(sqlite3_errmsg16(reinterpret_cast<sqlite3*>(db_pointer)));
    if (msg == nullptr) {
        return nullptr;
    }
    jsize length = 0;
    while (msg[length] != 0) {
        length++;
    }
    return env->NewString(msg, length);
}

// JNINativeMethod's name/signature fields are non-const char* in jni.h.
const JNINativeMethod kMethods[] = {
    {const_cast<char*>("nativeLibVersion"), const_cast<char*>("()Ljava/lang/String;"),
     reinterpret_cast<void*>(NativeLibVersion)},
    {const_cast<char*>("nativeOpen"), const_cast<char*>("(Ljava/lang/String;I[I)J"),
     reinterpret_cast<void*>(NativeOpen)},
    {const_cast<char*>("nativeClose"), const_cast<char*>("(J)V"),
     reinterpret_cast<void*>(NativeClose)},
    {const_cast<char*>("nativePrepare"), const_cast<char*>("(JLjava/lang/String;[J)I"),
     reinterpret_cast<void*>(NativePrepare)},
    {const_cast<char*>("nativeStep"), const_cast<char*>("(J)I"),
     reinterpret_cast<void*>(NativeStep)},
    {const_cast<char*>("nativeFinalize"), const_cast<char*>("(J)V"),
     reinterpret_cast<void*>(NativeFinalize)},
    {const_cast<char*>("nativeErrmsg"), const_cast<char*>("(J)Ljava/lang/String;"),
     reinterpret_cast<void*>(NativeErrmsg)},
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
