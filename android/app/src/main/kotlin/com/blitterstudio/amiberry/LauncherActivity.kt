package com.blitterstudio.amiberry

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import java.io.File

/**
 * LauncherActivity - Handles first-run folder selection before launching SDL
 *
 * This activity checks if a data folder has been configured. If not, it shows
 * a welcome dialog and prompts the user to select one. Once configured, it
 * launches AmiberryActivity which handles the actual SDL/emulator.
 *
 * This separation is necessary because SDLActivity.onCreate() immediately starts
 * the SDL thread, and we need to have the data path configured before that happens.
 */
class LauncherActivity : Activity() {

    companion object {
        private const val TAG = "LauncherActivity"
        const val PREFS_NAME = "AmiberryPrefs"
        const val KEY_USER_DATA_PATH = "user_data_path"
        const val KEY_USER_DATA_URI = "user_data_uri"
        private const val REQUEST_CODE_SELECT_DATA_FOLDER = 43001
        private const val REQUEST_CODE_MANAGE_STORAGE = 43002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.v(TAG, "onCreate()")

        // On Android 11+, check for MANAGE_EXTERNAL_STORAGE permission first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Log.v(TAG, "MANAGE_EXTERNAL_STORAGE not granted, requesting")
                requestManageStoragePermission()
                return
            }
            Log.v(TAG, "MANAGE_EXTERNAL_STORAGE already granted")
        }

        // Check if we already have a valid user data path
        if (hasValidUserDataPath()) {
            Log.v(TAG, "Valid user data path exists, launching Amiberry")
            launchAmiberry()
            return
        }

        // First run - need to select a folder
        Log.v(TAG, "No valid user data path, showing welcome dialog")
        showWelcomeDialog()
    }

    /**
     * Request MANAGE_EXTERNAL_STORAGE permission on Android 11+
     * This is required to access files outside the app's private directories
     */
    private fun requestManageStoragePermission() {
        AlertDialog.Builder(this)
            .setTitle("Storage Permission Required")
            .setMessage(
                "Amiberry needs full storage access to read ROM files and disk images.\n\n" +
                "On the next screen, please enable \"Allow access to manage all files\" for Amiberry."
            )
            .setPositiveButton("Grant Permission") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open permission settings: ${e.message}")
                    // Fallback to general settings
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE)
                }
            }
            .setNegativeButton("Exit") { _, _ ->
                Toast.makeText(this, "Storage permission is required to run Amiberry", Toast.LENGTH_LONG).show()
                finish()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Check if we have a valid user data path saved
     */
    private fun hasValidUserDataPath(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val path = prefs.getString(KEY_USER_DATA_PATH, null)
        
        if (path.isNullOrEmpty()) {
            Log.v(TAG, "No user data path saved")
            return false
        }
        
        val file = File(path)
        val exists = file.exists() && file.isDirectory
        Log.v(TAG, "User data path '$path' exists: $exists")
        
        if (exists) {
            // Ensure the config file exists for native code
            ensureNativeConfig(path)
        }
        
        return exists
    }

    /**
     * Ensure the native config file exists
     */
    private fun ensureNativeConfig(path: String) {
        val configFile = File(filesDir, "user_data_path.txt")
        if (!configFile.exists() || configFile.readText() != path) {
            configFile.writeText(path)
            Log.v(TAG, "Wrote native config: $path")
        }
    }

    /**
     * Show welcome dialog explaining the need for folder selection
     */
    private fun showWelcomeDialog() {
        AlertDialog.Builder(this)
            .setTitle("Welcome to Amiberry!")
            .setMessage(
                "Please select a folder where Amiberry will store:\n\n" +
                "\u2022 Kickstart ROMs\n" +
                "\u2022 Floppy disk images (.adf)\n" +
                "\u2022 Hard drive images (.hdf)\n" +
                "\u2022 Save states\n" +
                "\u2022 Configuration files\n\n" +
                "Choose a location accessible via your file manager " +
                "(e.g., in Downloads or a custom folder) for easy file transfers."
            )
            .setPositiveButton("Select Folder") { _, _ ->
                launchFolderPicker()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Launch Android's native folder picker using Storage Access Framework
     */
    private fun launchFolderPicker() {
        Log.v(TAG, "Launching folder picker")
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            startActivityForResult(intent, REQUEST_CODE_SELECT_DATA_FOLDER)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch folder picker: ${e.message}")
            Toast.makeText(this, "Could not open folder picker", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.v(TAG, "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")
        
        if (requestCode == REQUEST_CODE_MANAGE_STORAGE) {
            // Check if permission was granted
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                Log.v(TAG, "MANAGE_EXTERNAL_STORAGE permission granted")
                // Now check for user data path
                if (hasValidUserDataPath()) {
                    launchAmiberry()
                } else {
                    showWelcomeDialog()
                }
            } else {
                Log.v(TAG, "MANAGE_EXTERNAL_STORAGE permission denied")
                Toast.makeText(this, "Storage permission is required to run Amiberry", Toast.LENGTH_LONG).show()
                finish()
            }
            return
        }
        
        if (requestCode == REQUEST_CODE_SELECT_DATA_FOLDER) {
            if (resultCode == RESULT_OK && data?.data != null) {
                handleFolderSelection(data.data!!)
            } else {
                // User cancelled - exit app as per requirements
                Log.v(TAG, "User cancelled folder selection, exiting")
                Toast.makeText(this, "Folder selection is required to run Amiberry", Toast.LENGTH_LONG).show()
                finish()
            }
            return
        }
        
        super.onActivityResult(requestCode, resultCode, data)
    }

    /**
     * Handle the folder selection result
     */
    private fun handleFolderSelection(uri: Uri) {
        Log.v(TAG, "Folder selected: $uri")
        
        // Take persistable permission so it survives app restarts
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            Log.v(TAG, "Persistable permission granted")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to take persistable permission: ${e.message}")
        }
        
        // Convert URI to filesystem path
        val path = getPathFromTreeUri(uri)
        
        if (path != null && File(path).exists()) {
            Log.v(TAG, "Resolved path: $path")
            
            // Save to SharedPreferences
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_USER_DATA_PATH, path)
                .putString(KEY_USER_DATA_URI, uri.toString())
                .apply()
            
            // Write config file for native code
            ensureNativeConfig(path)
            
            // Create subdirectories
            createDataSubdirectories(path)
            
            Toast.makeText(this, "Data folder set to:\n$path", Toast.LENGTH_LONG).show()
            
            // Launch the main Amiberry activity
            launchAmiberry()
        } else {
            Log.e(TAG, "Could not resolve URI to filesystem path")
            Toast.makeText(
                this, 
                "Could not access the selected folder.\nPlease try a different location.", 
                Toast.LENGTH_LONG
            ).show()
            // Show dialog again
            showWelcomeDialog()
        }
    }

    /**
     * Convert a tree (folder) URI to a filesystem path
     */
    private fun getPathFromTreeUri(treeUri: Uri): String? {
        try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            Log.v(TAG, "Tree document ID: $docId")
            
            // Handle primary storage (internal)
            if (docId.startsWith("primary:")) {
                val relativePath = docId.substring(8) // Remove "primary:"
                val basePath = Environment.getExternalStorageDirectory().absolutePath
                return if (relativePath.isEmpty()) {
                    basePath
                } else {
                    "$basePath/$relativePath"
                }
            }
            
            // Handle external SD card or other storage
            // Format is usually "XXXX-XXXX:path" where XXXX-XXXX is the volume ID
            val parts = docId.split(":")
            if (parts.size >= 2) {
                val volumeId = parts[0]
                val relativePath = parts[1]
                
                // Try to find the storage path for this volume
                val externalDirs = getExternalFilesDirs(null)
                for (dir in externalDirs) {
                    if (dir != null) {
                        val dirPath = dir.absolutePath
                        // External storage paths contain the volume ID
                        if (dirPath.contains(volumeId)) {
                            // Get the root of this storage volume
                            val androidIndex = dirPath.indexOf("/Android/")
                            if (androidIndex > 0) {
                                val storagePath = dirPath.substring(0, androidIndex)
                                return if (relativePath.isEmpty()) {
                                    storagePath
                                } else {
                                    "$storagePath/$relativePath"
                                }
                            }
                        }
                    }
                }
                
                // Fallback: try common paths
                val possiblePaths = arrayOf(
                    "/storage/$volumeId/$relativePath",
                    "/mnt/media_rw/$volumeId/$relativePath"
                )
                for (path in possiblePaths) {
                    if (File(path).exists()) {
                        return path
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing tree URI: ${e.message}")
        }
        
        return null
    }

    /**
     * Create the expected subdirectories in the user data folder
     */
    private fun createDataSubdirectories(basePath: String) {
        val subdirs = listOf(
            "roms",
            "floppies",
            "harddrives",
            "cdroms",
            "savestates",
            "screenshots",
            "nvram",
            "conf",
            "lha",
            "rp9",
            "inputrecordings",
            "whdboot"
        )
        
        for (subdir in subdirs) {
            val dir = File(basePath, subdir)
            if (!dir.exists()) {
                if (dir.mkdirs()) {
                    Log.v(TAG, "Created directory: ${dir.absolutePath}")
                } else {
                    Log.w(TAG, "Failed to create directory: ${dir.absolutePath}")
                }
            }
        }
    }

    /**
     * Launch the main Amiberry SDL activity
     */
    private fun launchAmiberry() {
        Log.v(TAG, "Starting AmiberryActivity")
        val intent = Intent(this, AmiberryActivity::class.java)
        startActivity(intent)
        finish() // Close the launcher activity
    }
}
