/*
 * Amiberry Android File Picker
 * 
 * JNI implementation for Android's Storage Access Framework (SAF)
 * Calls static methods in SDLActivity.java for file/folder selection
 */

#ifdef __ANDROID__

#include "android_file_picker.h"

#include <jni.h>
#include <android/log.h>
#include <string>
#include <SDL.h>

#define LOG_TAG "AmiberryFilePicker"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Cache for JNI class and method IDs
static jclass sSDLActivityClass = nullptr;
static jmethodID sShowFolderPickerMethod = nullptr;
static jmethodID sShowFilePickerMethod = nullptr;
static jmethodID sGetInternalStoragePathMethod = nullptr;
static jmethodID sGetExternalStoragePathMethod = nullptr;

// Initialize JNI references - called lazily on first use
static bool init_jni_refs(JNIEnv* env) {
    if (sSDLActivityClass != nullptr) {
        return true; // Already initialized
    }
    
    // Find SDLActivity class
    jclass localClass = env->FindClass("org/libsdl/app/SDLActivity");
    if (localClass == nullptr) {
        LOGE("Failed to find SDLActivity class");
        return false;
    }
    
    // Create global reference to class
    sSDLActivityClass = (jclass)env->NewGlobalRef(localClass);
    env->DeleteLocalRef(localClass);
    
    if (sSDLActivityClass == nullptr) {
        LOGE("Failed to create global ref for SDLActivity class");
        return false;
    }
    
    // Get method IDs
    sShowFolderPickerMethod = env->GetStaticMethodID(
        sSDLActivityClass, 
        "showFolderPicker", 
        "(Ljava/lang/String;)Ljava/lang/String;"
    );
    if (sShowFolderPickerMethod == nullptr) {
        LOGE("Failed to find showFolderPicker method");
        return false;
    }
    
    sShowFilePickerMethod = env->GetStaticMethodID(
        sSDLActivityClass, 
        "showFilePicker", 
        "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"
    );
    if (sShowFilePickerMethod == nullptr) {
        LOGE("Failed to find showFilePicker method");
        return false;
    }
    
    sGetInternalStoragePathMethod = env->GetStaticMethodID(
        sSDLActivityClass,
        "getInternalStoragePath",
        "()Ljava/lang/String;"
    );
    if (sGetInternalStoragePathMethod == nullptr) {
        LOGE("Failed to find getInternalStoragePath method");
        return false;
    }
    
    sGetExternalStoragePathMethod = env->GetStaticMethodID(
        sSDLActivityClass,
        "getExternalStoragePath",
        "()Ljava/lang/String;"
    );
    if (sGetExternalStoragePathMethod == nullptr) {
        LOGE("Failed to find getExternalStoragePath method");
        return false;
    }
    
    LOGI("JNI references initialized successfully");
    return true;
}

// Helper to convert jstring to std::string
static std::string jstring_to_string(JNIEnv* env, jstring jstr) {
    if (jstr == nullptr) {
        return "";
    }
    
    const char* chars = env->GetStringUTFChars(jstr, nullptr);
    if (chars == nullptr) {
        return "";
    }
    
    std::string result(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return result;
}

std::string android_select_folder(const std::string& title) {
    LOGI("android_select_folder called with title: %s", title.c_str());
    
    JNIEnv* env = (JNIEnv*)SDL_AndroidGetJNIEnv();
    if (env == nullptr) {
        LOGE("Failed to get JNI environment");
        return "";
    }
    
    if (!init_jni_refs(env)) {
        LOGE("Failed to initialize JNI references");
        return "";
    }
    
    // Create Java string for title
    jstring jTitle = env->NewStringUTF(title.c_str());
    if (jTitle == nullptr) {
        LOGE("Failed to create Java string for title");
        return "";
    }
    
    // Call Java method
    jstring jResult = (jstring)env->CallStaticObjectMethod(
        sSDLActivityClass,
        sShowFolderPickerMethod,
        jTitle
    );
    
    env->DeleteLocalRef(jTitle);
    
    // Check for exceptions
    if (env->ExceptionCheck()) {
        LOGE("Exception occurred in showFolderPicker");
        env->ExceptionDescribe();
        env->ExceptionClear();
        return "";
    }
    
    std::string result = jstring_to_string(env, jResult);
    if (jResult != nullptr) {
        env->DeleteLocalRef(jResult);
    }
    
    LOGI("android_select_folder returning: %s", result.c_str());
    return result;
}

std::string android_select_file(const std::string& title, const std::string& mimeTypes) {
    LOGI("android_select_file called with title: %s, mimeTypes: %s", 
         title.c_str(), mimeTypes.c_str());
    
    JNIEnv* env = (JNIEnv*)SDL_AndroidGetJNIEnv();
    if (env == nullptr) {
        LOGE("Failed to get JNI environment");
        return "";
    }
    
    if (!init_jni_refs(env)) {
        LOGE("Failed to initialize JNI references");
        return "";
    }
    
    // Create Java strings
    jstring jTitle = env->NewStringUTF(title.c_str());
    jstring jMimeTypes = env->NewStringUTF(mimeTypes.c_str());
    
    if (jTitle == nullptr || jMimeTypes == nullptr) {
        LOGE("Failed to create Java strings");
        if (jTitle != nullptr) env->DeleteLocalRef(jTitle);
        if (jMimeTypes != nullptr) env->DeleteLocalRef(jMimeTypes);
        return "";
    }
    
    // Call Java method
    jstring jResult = (jstring)env->CallStaticObjectMethod(
        sSDLActivityClass,
        sShowFilePickerMethod,
        jTitle,
        jMimeTypes
    );
    
    env->DeleteLocalRef(jTitle);
    env->DeleteLocalRef(jMimeTypes);
    
    // Check for exceptions
    if (env->ExceptionCheck()) {
        LOGE("Exception occurred in showFilePicker");
        env->ExceptionDescribe();
        env->ExceptionClear();
        return "";
    }
    
    std::string result = jstring_to_string(env, jResult);
    if (jResult != nullptr) {
        env->DeleteLocalRef(jResult);
    }
    
    LOGI("android_select_file returning: %s", result.c_str());
    return result;
}

bool android_has_storage_access(const std::string& path) {
    // For now, just check if the path is readable
    // This could be enhanced to check SAF permissions
    FILE* f = fopen(path.c_str(), "r");
    if (f != nullptr) {
        fclose(f);
        return true;
    }
    return false;
}

void android_request_persistent_access(const std::string& uri) {
    // Persistent access is automatically granted when using ACTION_OPEN_DOCUMENT_TREE
    // with FLAG_GRANT_PERSISTABLE_URI_PERMISSION
    LOGI("android_request_persistent_access called for: %s", uri.c_str());
}

std::string android_get_internal_storage_path() {
    JNIEnv* env = (JNIEnv*)SDL_AndroidGetJNIEnv();
    if (env == nullptr) {
        LOGE("Failed to get JNI environment");
        return "";
    }
    
    if (!init_jni_refs(env)) {
        LOGE("Failed to initialize JNI references");
        return "";
    }
    
    jstring jResult = (jstring)env->CallStaticObjectMethod(
        sSDLActivityClass,
        sGetInternalStoragePathMethod
    );
    
    if (env->ExceptionCheck()) {
        LOGE("Exception occurred in getInternalStoragePath");
        env->ExceptionDescribe();
        env->ExceptionClear();
        return "";
    }
    
    std::string result = jstring_to_string(env, jResult);
    if (jResult != nullptr) {
        env->DeleteLocalRef(jResult);
    }
    
    return result;
}

std::string android_get_external_storage_path() {
    JNIEnv* env = (JNIEnv*)SDL_AndroidGetJNIEnv();
    if (env == nullptr) {
        LOGE("Failed to get JNI environment");
        return "";
    }
    
    if (!init_jni_refs(env)) {
        LOGE("Failed to initialize JNI references");
        return "";
    }
    
    jstring jResult = (jstring)env->CallStaticObjectMethod(
        sSDLActivityClass,
        sGetExternalStoragePathMethod
    );
    
    if (env->ExceptionCheck()) {
        LOGE("Exception occurred in getExternalStoragePath");
        env->ExceptionDescribe();
        env->ExceptionClear();
        return "";
    }
    
    std::string result = jstring_to_string(env, jResult);
    if (jResult != nullptr) {
        env->DeleteLocalRef(jResult);
    }
    
    return result;
}

#endif // __ANDROID__
