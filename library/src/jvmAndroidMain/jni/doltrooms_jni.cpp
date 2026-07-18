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

jint NativeReset(JNIEnv*, jobject, jlong stmt_pointer) {
    return sqlite3_reset(reinterpret_cast<sqlite3_stmt*>(stmt_pointer));
}

jint NativeClearBindings(JNIEnv*, jobject, jlong stmt_pointer) {
    return sqlite3_clear_bindings(reinterpret_cast<sqlite3_stmt*>(stmt_pointer));
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

jint NativeBindLong(JNIEnv*, jobject, jlong stmt_pointer, jint index, jlong value) {
    return sqlite3_bind_int64(reinterpret_cast<sqlite3_stmt*>(stmt_pointer), index, value);
}

jint NativeBindDouble(JNIEnv*, jobject, jlong stmt_pointer, jint index, jdouble value) {
    return sqlite3_bind_double(reinterpret_cast<sqlite3_stmt*>(stmt_pointer), index, value);
}

jint NativeBindText(JNIEnv* env, jobject, jlong stmt_pointer, jint index, jstring value) {
    const jchar* chars = env->GetStringChars(value, nullptr);
    const jsize bytes = env->GetStringLength(value) * 2;
    // SQLITE_TRANSIENT: SQLite copies before returning, so releasing the
    // JNI chars right after is safe (https://www.sqlite.org/c3ref/bind_blob.html).
    jint rc = sqlite3_bind_text16(reinterpret_cast<sqlite3_stmt*>(stmt_pointer), index, chars,
                                  bytes, SQLITE_TRANSIENT);
    env->ReleaseStringChars(value, chars);
    return rc;
}

jint NativeBindBlob(JNIEnv* env, jobject, jlong stmt_pointer, jint index, jbyteArray value) {
    const jsize length = env->GetArrayLength(value);
    jbyte* bytes = env->GetByteArrayElements(value, nullptr);
    // A NULL data pointer would bind SQL NULL, so an empty array binds a
    // zero-length blob explicitly.
    jint rc;
    if (bytes == nullptr || length == 0) {
        rc = sqlite3_bind_zeroblob(reinterpret_cast<sqlite3_stmt*>(stmt_pointer), index, 0);
    } else {
        rc = sqlite3_bind_blob(reinterpret_cast<sqlite3_stmt*>(stmt_pointer), index, bytes,
                               length, SQLITE_TRANSIENT);
    }
    if (bytes != nullptr) {
        env->ReleaseByteArrayElements(value, bytes, JNI_ABORT);
    }
    return rc;
}

jint NativeBindNull(JNIEnv*, jobject, jlong stmt_pointer, jint index) {
    return sqlite3_bind_null(reinterpret_cast<sqlite3_stmt*>(stmt_pointer), index);
}

jdouble NativeColumnDouble(JNIEnv*, jobject, jlong stmt_pointer, jint index) {
    return sqlite3_column_double(reinterpret_cast<sqlite3_stmt*>(stmt_pointer), index);
}

jbyteArray NativeColumnBlob(JNIEnv* env, jobject, jlong stmt_pointer, jint index) {
    sqlite3_stmt* stmt = reinterpret_cast<sqlite3_stmt*>(stmt_pointer);
    // Call column_blob before column_bytes (https://www.sqlite.org/c3ref/column_blob.html).
    // NULL values and zero-length blobs both yield a NULL pointer; the
    // Kotlin side distinguishes them via getColumnType.
    const void* blob = sqlite3_column_blob(stmt, index);
    const jsize length = blob == nullptr ? 0 : sqlite3_column_bytes(stmt, index);
    jbyteArray result = env->NewByteArray(length);
    if (result != nullptr && length > 0) {
        env->SetByteArrayRegion(result, 0, length, static_cast<const jbyte*>(blob));
    }
    return result;
}

jint NativeColumnType(JNIEnv*, jobject, jlong stmt_pointer, jint index) {
    return sqlite3_column_type(reinterpret_cast<sqlite3_stmt*>(stmt_pointer), index);
}

jint NativeColumnCount(JNIEnv*, jobject, jlong stmt_pointer) {
    return sqlite3_column_count(reinterpret_cast<sqlite3_stmt*>(stmt_pointer));
}

jstring NativeColumnName(JNIEnv* env, jobject, jlong stmt_pointer, jint index) {
    // NewStringUTF copies immediately — the sqlite3 pointer is invalidated
    // by the next column_name call (https://www.sqlite.org/c3ref/column_name.html).
    const char* name = sqlite3_column_name(reinterpret_cast<sqlite3_stmt*>(stmt_pointer), index);
    return name == nullptr ? nullptr : env->NewStringUTF(name);
}

jlong NativeColumnLong(JNIEnv*, jobject, jlong stmt_pointer, jint index) {
    return sqlite3_column_int64(reinterpret_cast<sqlite3_stmt*>(stmt_pointer), index);
}

jstring NativeColumnText(JNIEnv* env, jobject, jlong stmt_pointer, jint index) {
    sqlite3_stmt* stmt = reinterpret_cast<sqlite3_stmt*>(stmt_pointer);
    // Call column_text16 before column_bytes16 (https://www.sqlite.org/c3ref/column_blob.html).
    const jchar* text = static_cast<const jchar*>(sqlite3_column_text16(stmt, index));
    if (text == nullptr) {
        return nullptr;
    }
    const jsize length = sqlite3_column_bytes16(stmt, index) / 2;
    return env->NewString(text, length);
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
    {const_cast<char*>("nativeReset"), const_cast<char*>("(J)I"),
     reinterpret_cast<void*>(NativeReset)},
    {const_cast<char*>("nativeClearBindings"), const_cast<char*>("(J)I"),
     reinterpret_cast<void*>(NativeClearBindings)},
    {const_cast<char*>("nativeErrmsg"), const_cast<char*>("(J)Ljava/lang/String;"),
     reinterpret_cast<void*>(NativeErrmsg)},
    {const_cast<char*>("nativeBindLong"), const_cast<char*>("(JIJ)I"),
     reinterpret_cast<void*>(NativeBindLong)},
    {const_cast<char*>("nativeBindDouble"), const_cast<char*>("(JID)I"),
     reinterpret_cast<void*>(NativeBindDouble)},
    {const_cast<char*>("nativeBindText"), const_cast<char*>("(JILjava/lang/String;)I"),
     reinterpret_cast<void*>(NativeBindText)},
    {const_cast<char*>("nativeBindBlob"), const_cast<char*>("(JI[B)I"),
     reinterpret_cast<void*>(NativeBindBlob)},
    {const_cast<char*>("nativeBindNull"), const_cast<char*>("(JI)I"),
     reinterpret_cast<void*>(NativeBindNull)},
    {const_cast<char*>("nativeColumnDouble"), const_cast<char*>("(JI)D"),
     reinterpret_cast<void*>(NativeColumnDouble)},
    {const_cast<char*>("nativeColumnBlob"), const_cast<char*>("(JI)[B"),
     reinterpret_cast<void*>(NativeColumnBlob)},
    {const_cast<char*>("nativeColumnType"), const_cast<char*>("(JI)I"),
     reinterpret_cast<void*>(NativeColumnType)},
    {const_cast<char*>("nativeColumnCount"), const_cast<char*>("(J)I"),
     reinterpret_cast<void*>(NativeColumnCount)},
    {const_cast<char*>("nativeColumnName"), const_cast<char*>("(JI)Ljava/lang/String;"),
     reinterpret_cast<void*>(NativeColumnName)},
    {const_cast<char*>("nativeColumnLong"), const_cast<char*>("(JI)J"),
     reinterpret_cast<void*>(NativeColumnLong)},
    {const_cast<char*>("nativeColumnText"), const_cast<char*>("(JI)Ljava/lang/String;"),
     reinterpret_cast<void*>(NativeColumnText)},
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
