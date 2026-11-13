/*
 * Copyright 2024 The "Open Radio" Project. Author: Chernyshov Yuriy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yuriy.openradio.shared.model.storage

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommonUi
import com.yuriy.openradio.shared.dependencies.StorageManagerDependency
import com.yuriy.openradio.shared.utils.AppLogger
import com.yuriy.openradio.shared.utils.AppUtils
import com.yuriy.openradio.shared.utils.IntentUtils
import java.io.ByteArrayOutputStream
import java.io.File

class FileStoreManager : StorageManagerDependency {

    private lateinit var mStorageManagerLayer: StorageManagerLayer

    init {
        DependencyRegistryCommonUi.injectStorageManagerLayer(this)
    }

    override fun configureWith(storageManagerLayer: StorageManagerLayer) {
        mStorageManagerLayer = storageManagerLayer
    }

    /**
     * Without a document picker (many Android TV boxes) the backup goes to Downloads directly;
     * [onFallback] then gets a message for the user and whether the stations changed.
     */
    var onFallback: ((message: String, stationsChanged: Boolean) -> Unit)? = null

    private fun usePickerless(context: Context, intent: Intent): Boolean {
        val pm = context.packageManager
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) || intent.resolveActivity(pm) == null
    }

    private fun fallback(activity: Activity, export: Boolean): Boolean {
        val callback = onFallback ?: return false
        // Before Android 10 the public Downloads folder needs the storage permission.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_CODE_STORAGE
            )
            callback(activity.getString(com.yuriy.openradio.shared.R.string.backup_allow_storage), false)
            return true
        }
        val ok = if (export) exportToDownloads(activity) else importFromDownloads(activity)
        callback(fallbackMessage(activity, export, ok), ok && export.not())
        return true
    }

    fun upload(activity: AppCompatActivity, onFailure: () -> Unit) {
        if (usePickerless(activity, getUploadIntent()) && fallback(activity, true)) {
            return
        }
        // Even though it is deprecated, Android officially recommends this approach:
        // https://developer.android.com/training/data-storage/shared/documents-files
        // Was what recommended as alternative to deprecation require much more time to implement as it relies
        // on APIs available since Android 13.
        if (IntentUtils.startActivityForResultSafe(activity, getUploadIntent(), REQUEST_CODE_CREATE_FILE).not()) {
            onFailure()
        }
    }

    fun upload(dialogFragment: DialogFragment, onFailure: () -> Unit) {
        val ctx = dialogFragment.requireActivity()
        if (usePickerless(ctx, getUploadIntent()) && fallback(ctx, true)) {
            return
        }
        // Even though it is deprecated, Android officially recommends this approach:
        // https://developer.android.com/training/data-storage/shared/documents-files
        // Was what recommended as alternative to deprecation require much more time to implement as it relies
        // on APIs available since Android 13.
        if (IntentUtils.startActivityForResultSafe(dialogFragment, getUploadIntent(), REQUEST_CODE_CREATE_FILE).not()) {
            onFailure()
        }
    }

    fun download(activity: AppCompatActivity, onFailure: () -> Unit) {
        if (usePickerless(activity, getDownloadIntent()) && fallback(activity, false)) {
            return
        }
        // Even though it is deprecated, Android officially recommends this approach:
        // https://developer.android.com/training/data-storage/shared/documents-files
        // Was what recommended as alternative to deprecation require much more time to implement as it relies
        // on APIs available since Android 13.
        if (IntentUtils.startActivityForResultSafe(activity, getDownloadIntent(), REQUEST_CODE_OPEN_FILE).not()) {
            onFailure()
        }
    }

    fun download(dialogFragment: DialogFragment, onFailure: () -> Unit) {
        val ctx = dialogFragment.requireActivity()
        if (usePickerless(ctx, getDownloadIntent()) && fallback(ctx, false)) {
            return
        }
        // Even though it is deprecated, Android officially recommends this approach:
        // https://developer.android.com/training/data-storage/shared/documents-files
        // Was what recommended as alternative to deprecation require much more time to implement as it relies
        // on APIs available since Android 13.
        if (IntentUtils.startActivityForResultSafe(dialogFragment, getDownloadIntent(), REQUEST_CODE_OPEN_FILE).not()) {
            onFailure()
        }
    }

    fun onActivityResult(
        context: Context,
        requestCode: Int,
        resultCode: Int,
        intent: Intent?,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        if (resultCode != Activity.RESULT_OK) {
            AppLogger.e("$TAG activity result is not OK")
            onFailure()
            return
        }
        if (intent == null) {
            AppLogger.e("$TAG activity result has no intent")
            onFailure()
            return
        }
        when (requestCode) {
            REQUEST_CODE_CREATE_FILE -> {
                val uri: Uri? = intent.data
                AppLogger.d("Save data file to:$uri")
                if (uri != null) {
                    context.contentResolver?.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(encodeData())
                        onSuccess()
                    } ?: run {
                        AppLogger.e("Error opening output stream for uri: $uri")
                        onFailure()
                    }
                } else {
                    onFailure()
                }
            }

            REQUEST_CODE_OPEN_FILE -> {
                val uri: Uri? = intent.data
                uri?.let {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            val buffer = ByteArray(2046)
                            val byteArrayOutputStream = ByteArrayOutputStream()
                            var bytesRead: Int
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                byteArrayOutputStream.write(buffer, 0, bytesRead)
                            }
                            applyData(byteArrayOutputStream.toByteArray())
                            onSuccess()
                        }
                    } catch (e: Exception) {
                        AppLogger.e("Error reading file content", e)
                        onFailure()
                    }
                } ?: onFailure()
            }
        }
    }

    private fun encodeData(): ByteArray {
        val data = hashMapOf(
            KEY_FAV to mStorageManagerLayer.getAllFavoritesAsString(),
            KEY_LOC to mStorageManagerLayer.getAllDeviceLocalsAsString()
        )
        return AppUtils.encodeBase64(AppUtils.serializeMap(data))
    }

    private fun applyData(content: ByteArray) {
        val data = AppUtils.deserializeMap(AppUtils.decodeBase64(content))
        val fav: String? = data[KEY_FAV]
        if (!fav.isNullOrEmpty()) {
            mStorageManagerLayer.mergeFavorites(fav)
        }
        val loc: String? = data[KEY_LOC]
        if (!loc.isNullOrEmpty()) {
            mStorageManagerLayer.mergeDeviceLocals(loc)
        }
    }

    /**
     * Writes the backup to Downloads/[DOWNLOADS_FILE_NAME].
     */
    fun exportToDownloads(context: Context): Boolean {
        return try {
            val bytes = encodeData()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                findInDownloads(context)?.let { resolver.delete(it, null, null) }
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, DOWNLOADS_FILE_NAME)
                    put(MediaStore.Downloads.MIME_TYPE, FILE_TYPE)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return false
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                File(dir, DOWNLOADS_FILE_NAME).writeBytes(bytes)
            }
            true
        } catch (e: Exception) {
            AppLogger.e("$TAG export to Downloads", e)
            false
        }
    }

    /**
     * Merges the backup from Downloads/[DOWNLOADS_FILE_NAME] into Favorites and own stations.
     */
    fun importFromDownloads(context: Context): Boolean {
        return try {
            val bytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val uri = findInDownloads(context) ?: return false
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(dir, DOWNLOADS_FILE_NAME)
                if (file.exists().not()) {
                    return false
                }
                file.readBytes()
            }
            applyData(bytes)
            true
        } catch (e: Exception) {
            AppLogger.e("$TAG import from Downloads", e)
            false
        }
    }

    private fun findInDownloads(context: Context): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null
        }
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} = ?",
            arrayOf(DOWNLOADS_FILE_NAME),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(0))
            }
        }
        return null
    }

    private fun getUploadIntent(): Intent {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.setType(FILE_TYPE)
        intent.putExtra(Intent.EXTRA_TITLE, FILE_NAME)
        return intent
    }

    private fun getDownloadIntent(): Intent {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = FILE_TYPE
        return intent
    }

    companion object {

        private const val TAG = "FSM"
        private const val KEY_FAV = "favorites"
        private const val KEY_LOC = "locals"
        private const val FILE_NAME = "openradio_data"
        private const val FILE_TYPE = "text/plain"
        const val DOWNLOADS_FILE_NAME = "libreradio_favorites.txt"
        private const val REQUEST_CODE_STORAGE = 9012

        /**
         * Message for the result of a backup through Downloads.
         */
        fun fallbackMessage(context: Context, exported: Boolean, ok: Boolean): String {
            val res = when {
                exported && ok -> com.yuriy.openradio.shared.R.string.backup_saved_downloads
                ok -> com.yuriy.openradio.shared.R.string.backup_loaded_downloads
                exported -> com.yuriy.openradio.shared.R.string.backup_save_failed
                else -> com.yuriy.openradio.shared.R.string.backup_no_file
            }
            return context.getString(res, DOWNLOADS_FILE_NAME)
        }
        private const val REQUEST_CODE_CREATE_FILE = 1234
        private const val REQUEST_CODE_OPEN_FILE = 5678
    }
}
