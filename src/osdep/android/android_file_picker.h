/*
 * Amiberry Android File Picker
 * 
 * JNI interface to Android's Storage Access Framework (SAF)
 * for secure folder and file selection on Android.
 */

#ifndef ANDROID_FILE_PICKER_H
#define ANDROID_FILE_PICKER_H

#ifdef __ANDROID__

#include <string>

// Launch Android's native folder picker dialog
// Returns the selected folder path, or empty string if cancelled
std::string android_select_folder(const std::string& title);

// Launch Android's native file picker dialog
// Returns the selected file path, or empty string if cancelled
// mimeTypes: comma-separated list like "application/octet-stream,application/zip"
std::string android_select_file(const std::string& title, const std::string& mimeTypes);

// Check if we have access to a given path
bool android_has_storage_access(const std::string& path);

// Request persistent storage access for a URI
void android_request_persistent_access(const std::string& uri);

// Get the internal storage path for the app
std::string android_get_internal_storage_path();

// Get the external storage path (if available)
std::string android_get_external_storage_path();

#endif // __ANDROID__

#endif // ANDROID_FILE_PICKER_H
