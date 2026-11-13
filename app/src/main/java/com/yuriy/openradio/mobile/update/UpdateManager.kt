/*
 * Copyright 2026 Libre Radio contributors
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

package com.yuriy.openradio.mobile.update

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.shared.utils.AppLogger
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Checks GitHub Releases of this project for a newer version, downloads the APK of the matching
 * edition (modern or legacy), verifies its SHA-256 digest published by GitHub and hands it to the
 * system package installer. Android itself refuses the update unless it is signed with the same key.
 */
object UpdateManager {

    private const val TAG = "UPD"
    private const val REPO = "Solomag/LibreRadio"
    private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
    private const val PREFS = "libre_radio_updates"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_SKIPPED = "skipped_tag"
    private const val KEY_AUTO = "auto_check"
    private const val AUTO_INTERVAL_MS = 24L * 60 * 60 * 1000
    private const val TIMEOUT_MS = 15_000
    private const val LEGACY_SUFFIX = "-legacy"

    private val mBusy = AtomicBoolean(false)
    private val mMain = Handler(Looper.getMainLooper())

    /**
     * Downloaded APK waiting for the "install unknown apps" permission (Android 8+).
     */
    private var mPendingApk: File? = null

    private class Release(
        val tag: String,
        val version: List<Int>,
        val notes: String,
        val apkName: String,
        val apkUrl: String,
        val sha256: String,
        val size: Long
    )

    fun isAutoCheckEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_AUTO, true)
    }

    fun setAutoCheckEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO, enabled).apply()
    }

    /**
     * Called from the main activity's onResume: installs a pending APK once the permission is
     * granted and runs the automatic check at most once a day.
     */
    fun onResume(activity: Activity) {
        mPendingApk?.let {
            if (canInstall(activity)) {
                mPendingApk = null
                install(activity, it)
                return
            }
        }
        if (!isAutoCheckEnabled(activity)) {
            return
        }
        val last = prefs(activity).getLong(KEY_LAST_CHECK, 0L)
        if (System.currentTimeMillis() - last < AUTO_INTERVAL_MS) {
            return
        }
        check(activity, manual = false)
    }

    /**
     * Checks for an update. A manual check also reports "up to date" and errors, and ignores
     * a previously skipped version.
     */
    fun check(activity: Activity, manual: Boolean) {
        if (!mBusy.compareAndSet(false, true)) {
            return
        }
        if (manual) {
            toast(activity, activity.getString(R.string.update_checking))
        }
        val appContext = activity.applicationContext
        Thread {
            val result = runCatching { fetchLatest(appContext) }
            prefs(appContext).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            mMain.post {
                mBusy.set(false)
                if (activity.isFinishing) {
                    return@post
                }
                val release = result.getOrElse {
                    AppLogger.e("$TAG check failed", it)
                    if (manual) {
                        toast(activity, activity.getString(R.string.update_check_failed))
                    }
                    return@post
                }
                val current = parseVersion(currentVersionName(activity))
                if (release == null || compare(release.version, current) <= 0) {
                    if (manual) {
                        toast(activity, activity.getString(R.string.update_up_to_date))
                    }
                    return@post
                }
                if (!manual && release.tag == prefs(activity).getString(KEY_SKIPPED, null)) {
                    return@post
                }
                showUpdateDialog(activity, release)
            }
        }.start()
    }

    private fun fetchLatest(context: Context): Release? {
        val json = JSONObject(httpGetString(LATEST_URL))
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) {
            return null
        }
        val tag = json.getString("tag_name")
        val legacy = isLegacy(context)
        val assets = json.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.getString("name")
            if (!name.endsWith(".apk")) {
                continue
            }
            // "LibreRadio-15.4.0.apk" is the modern phone build, "-legacy" the legacy one;
            // "-tv" and "-automotive" builds are for other devices.
            val edition = name.removeSuffix(".apk").substringAfterLast('-')
            val matches = if (legacy) "-$edition" == LEGACY_SUFFIX else edition.firstOrNull()?.isDigit() == true
            if (!matches) {
                continue
            }
            val digest = asset.optString("digest", "")
            return Release(
                tag = tag,
                version = parseVersion(tag),
                notes = json.optString("body", "").trim(),
                apkName = name,
                apkUrl = asset.getString("browser_download_url"),
                sha256 = if (digest.startsWith("sha256:")) digest.substring(7).lowercase(Locale.ROOT) else "",
                size = asset.optLong("size", -1L)
            )
        }
        AppLogger.w("$TAG no APK for this edition in $tag")
        return null
    }

    private fun showUpdateDialog(activity: Activity, release: Release) {
        val notes = changesFromNotes(release.notes)
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.update_available_title, release.tag.removePrefix("v")))
            .setMessage(notes.ifEmpty { release.apkName })
            .setPositiveButton(R.string.update_download) { _, _ -> download(activity, release) }
            .setNegativeButton(R.string.update_later, null)
            .setNeutralButton(R.string.update_skip) { _, _ ->
                prefs(activity).edit().putString(KEY_SKIPPED, release.tag).apply()
            }
            .show()
    }

    /**
     * Release notes are Markdown; show only the "Changes" list as plain text so the dialog stays short.
     */
    private fun changesFromNotes(markdown: String): String {
        val lines = markdown.replace("\r", "").lines()
        val start = lines.indexOfFirst { it.trim().startsWith("#") && it.contains("Changes", ignoreCase = true) }
        val section = if (start >= 0) {
            lines.drop(start + 1).takeWhile { !it.trim().startsWith("#") }
        } else {
            lines
        }
        val link = Regex("""\[([^\]]+)]\([^)]*\)""")
        val italic = Regex("""(^|\s)\*([^*]+)\*""")
        val bullet = Regex("""^\s*[-*]\s+""")
        val text = section
            .map { it.trimEnd() }
            .filter { it.isNotEmpty() && !it.trimStart().startsWith("|") }
            .joinToString("\n") { line ->
                var out = line.replace(link, "$1").replace("**", "").replace("`", "")
                out = out.replace(italic, "$1$2")
                out.replace(bullet, "• ")
            }
            .trim()
        return if (text.length > 900) text.substring(0, 900) + "…" else text
    }

    private fun download(activity: Activity, release: Release) {
        val progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            isIndeterminate = release.size <= 0
        }
        val label = TextView(activity).apply { text = release.apkName }
        val pad = (16 * activity.resources.displayMetrics.density).toInt()
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(label)
            addView(progress)
        }
        val cancelled = AtomicBoolean(false)
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.update_downloading)
            .setView(layout)
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel) { _, _ -> cancelled.set(true) }
            .show()
        val target = apkFile(activity, release.apkName)
        Thread {
            val result = runCatching {
                downloadTo(release, target, cancelled) { percent ->
                    mMain.post { progress.progress = percent }
                }
            }
            mMain.post {
                if (dialog.isShowing) {
                    dialog.dismiss()
                }
                if (cancelled.get()) {
                    target.delete()
                    return@post
                }
                result.onFailure {
                    AppLogger.e("$TAG download failed", it)
                    target.delete()
                    toast(activity, activity.getString(R.string.update_download_failed))
                }.onSuccess {
                    install(activity, target)
                }
            }
        }.start()
    }

    private fun downloadTo(release: Release, target: File, cancelled: AtomicBoolean, onProgress: (Int) -> Unit) {
        target.parentFile?.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256")
        val connection = open(release.apkUrl)
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }
            val total = if (release.size > 0) release.size else connection.contentLength.toLong()
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPercent = -1
                    while (true) {
                        if (cancelled.get()) {
                            return
                        }
                        val read = input.read(buffer)
                        if (read < 0) {
                            break
                        }
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (release.sha256.isNotEmpty()) {
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != release.sha256) {
                throw SecurityException("SHA-256 mismatch: expected ${release.sha256}, got $actual")
            }
        }
    }

    private fun install(activity: Activity, apk: File) {
        if (!canInstall(activity)) {
            // Android 8+: the user has to allow this app to install updates once.
            mPendingApk = apk
            toast(activity, activity.getString(R.string.update_allow_install))
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + activity.packageName)
            )
            runCatching { activity.startActivity(intent) }
            return
        }
        val intent = Intent(Intent.ACTION_VIEW)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val uri = FileProvider.getUriForFile(activity, activity.packageName + ".provider", apk)
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            intent.setDataAndType(Uri.fromFile(apk), "application/vnd.android.package-archive")
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { activity.startActivity(intent) }.onFailure {
            AppLogger.e("$TAG install intent", it)
            toast(activity, activity.getString(R.string.update_download_failed))
        }
    }

    private fun canInstall(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                context.packageManager.canRequestPackageInstalls()
    }

    /**
     * Android 7+ installs from a FileProvider URI (internal storage, covered by provider_paths);
     * older versions need a file the installer can read, so the app's external files dir is used.
     */
    private fun apkFile(context: Context, name: String): File {
        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            File(context.filesDir, "updates")
        } else {
            File(context.getExternalFilesDir(null) ?: context.filesDir, "updates")
        }
        dir.mkdirs()
        dir.listFiles()?.forEach { if (it.name != name) it.delete() }
        return File(dir, name)
    }

    private fun httpGetString(url: String): String {
        val connection = open(url)
        try {
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "LibreRadio-Updater")
        return connection
    }

    private fun currentVersionName(context: Context): String {
        return runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0"
    }

    private fun isLegacy(context: Context): Boolean {
        return currentVersionName(context).endsWith(LEGACY_SUFFIX)
    }

    /**
     * "v15.3.0", "15.3.0-legacy" -> [15, 3, 0].
     */
    private fun parseVersion(value: String): List<Int> {
        return value.removePrefix("v").substringBefore('-')
            .split('.')
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
    }

    private fun compare(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (diff != 0) {
                return diff
            }
        }
        return 0
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun toast(context: Context, text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}
