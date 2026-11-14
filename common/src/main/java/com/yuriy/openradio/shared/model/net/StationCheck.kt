package com.yuriy.openradio.shared.model.net

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import com.yuriy.openradio.shared.utils.AppLogger
import com.yuriy.openradio.shared.utils.AppUtils
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Which stations play on this phone, with its network and the current settings.
 *
 * A station is checked by connecting to its stream and reading its first bytes (a short "dry run"); the
 * player also reports the stations it played or failed to play. Results are kept for a while and dropped
 * when the bypass setting changes, since it changes what can play.
 */
object StationCheck {

    enum class Status { OK, FAILED }

    private const val TAG = "SCHK"
    private const val PREFS = "station_check"
    private const val PREFS_SETTINGS = "station_check_settings"
    private const val KEY_HIDE = "hide_failed"
    private const val KEY_BYPASS = "bypass_blocks"
    private const val KEY_BYPASS_HOSTS = "bypass_hosts"
    private const val OK_TTL_MS = 3L * 24 * 60 * 60 * 1000
    private const val FAILED_TTL_MS = 24L * 60 * 60 * 1000
    private const val PROBE_BYTES = 4096
    private const val RETRY_DELAY_MS = 1500L

    private class Entry(val status: Status, val time: Long, val bypass: Boolean)

    private val mEntries = ConcurrentHashMap<String, Entry>()
    private val mBypassHosts = ConcurrentHashMap.newKeySet<String>()
    private var mPrefs: SharedPreferences? = null
    private var mSettings: SharedPreferences? = null

    @Volatile
    private var mBypass = true

    @Volatile
    private var mHide = true

    /**
     * Grows on every change, so the UI knows to look again.
     */
    val version = AtomicInteger(0)

    private val mListeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        mListeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        mListeners.remove(listener)
    }

    private fun changed() {
        version.incrementAndGet()
        for (listener in mListeners) {
            listener()
        }
    }

    @Synchronized
    fun init(context: Context) {
        if (mPrefs != null) {
            return
        }
        val app = context.applicationContext
        val settings = app.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        mSettings = settings
        mBypass = settings.getBoolean(KEY_BYPASS, true)
        mHide = settings.getBoolean(KEY_HIDE, true)
        mBypassHosts.addAll(settings.getStringSet(KEY_BYPASS_HOSTS, emptySet()) ?: emptySet())
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mPrefs = prefs
        for ((id, value) in prefs.all) {
            val parts = (value as? String)?.split('|') ?: continue
            if (parts.size < 3) {
                continue
            }
            val status = runCatching { Status.valueOf(parts[0]) }.getOrNull() ?: continue
            mEntries[id] = Entry(status, parts[1].toLongOrNull() ?: 0L, parts[2] == "1")
        }
    }

    // region Settings

    /**
     * Retry a secure connection without the server name when the network breaks it.
     */
    fun isBypassEnabled(context: Context): Boolean {
        init(context)
        return mBypass
    }

    fun setBypassEnabled(context: Context, enabled: Boolean) {
        init(context)
        mBypass = enabled
        mSettings?.edit()?.putBoolean(KEY_BYPASS, enabled)?.apply()
        // What plays depends on it: check again.
        changed()
    }

    /**
     * Hide stations that do not play from the catalog lists (search still shows them, marked).
     */
    fun isHideEnabled(context: Context): Boolean {
        init(context)
        return mHide
    }

    fun setHideEnabled(context: Context, enabled: Boolean) {
        init(context)
        mHide = enabled
        mSettings?.edit()?.putBoolean(KEY_HIDE, enabled)?.apply()
        changed()
    }

    fun clear(context: Context) {
        init(context)
        mEntries.clear()
        mPrefs?.edit()?.clear()?.apply()
        changed()
    }

    // endregion

    /**
     * Known result for the station, or null when unknown, old, or made with other settings.
     */
    fun status(id: String): Status? {
        val entry = mEntries[id] ?: return null
        if (entry.bypass != mBypass) {
            return null
        }
        val ttl = if (entry.status == Status.OK) OK_TTL_MS else FAILED_TTL_MS
        if (System.currentTimeMillis() - entry.time > ttl) {
            return null
        }
        return entry.status
    }

    fun record(context: Context, id: String, status: Status) {
        if (id.isEmpty()) {
            return
        }
        init(context)
        val previous = status(id)
        val entry = Entry(status, System.currentTimeMillis(), mBypass)
        mEntries[id] = entry
        mPrefs?.edit()?.putString(id, "${status.name}|${entry.time}|${if (entry.bypass) 1 else 0}")?.apply()
        if (previous != status) {
            changed()
        }
    }

    /**
     * The server [host] answers only without its name: the player goes straight to the bypass for it.
     */
    fun needsBypass(host: String?): Boolean {
        return mBypass && host != null && mBypassHosts.contains(host)
    }

    fun rememberBypassHost(host: String?) {
        host ?: return
        if (mBypassHosts.add(host)) {
            mSettings?.edit()?.putStringSet(KEY_BYPASS_HOSTS, HashSet(mBypassHosts))?.apply()
        }
    }

    /**
     * Checks one station now (blocking; call off the main thread). Records and returns the result,
     * or null when it says nothing about the station (no internet, a server that is busy right now).
     */
    fun probe(context: Context, id: String, url: String): Status? {
        init(context)
        if (url.isEmpty() || isOnline(context).not()) {
            return null
        }
        val first = probeOnce(context, url)
        // A station is hidden only when it fails twice: networks and servers hiccup.
        val result = if (first == Status.FAILED) {
            Thread.sleep(RETRY_DELAY_MS)
            probeOnce(context, url)
        } else {
            first
        }
        if (result != null && isOnline(context)) {
            record(context, id, result)
        }
        return result
    }

    private fun probeOnce(context: Context, url: String): Status? {
        val headers = mapOf("User-Agent" to AppUtils.getUserAgent(context), "Icy-MetaData" to "1")
        val host = runCatching { java.net.URL(url).host }.getOrNull()
        return try {
            read(NoSniConnection.open(url, headers, needsBypass(host)))
        } catch (e: IOException) {
            if (mBypass && NoSniConnection.isBlockedHandshake(e)) {
                try {
                    val status = read(NoSniConnection.open(url, headers, true))
                    if (status == Status.OK) {
                        rememberBypassHost(host)
                    }
                    status
                } catch (e2: IOException) {
                    AppLogger.d("$TAG $url failed with bypass: $e2")
                    Status.FAILED
                }
            } else {
                AppLogger.d("$TAG $url failed: $e")
                Status.FAILED
            }
        } catch (e: Exception) {
            AppLogger.d("$TAG $url failed: $e")
            Status.FAILED
        }
    }

    /**
     * A station plays when it answers with something that is not a web page and sends data.
     */
    private fun read(connection: HttpURLConnection): Status? {
        try {
            val code = connection.responseCode
            if (code == 429 || code == 502 || code == 503 || code == 504) {
                // Busy right now: says nothing about the station.
                return null
            }
            if (code !in 200..299) {
                return Status.FAILED
            }
            val type = connection.contentType?.lowercase() ?: ""
            if (type.startsWith("text/html")) {
                // A provider's block page or a site instead of a stream.
                return Status.FAILED
            }
            val buffer = ByteArray(PROBE_BYTES)
            var total = 0
            connection.inputStream.use { input ->
                while (total < PROBE_BYTES) {
                    val n = input.read(buffer, total, PROBE_BYTES - total)
                    if (n < 0) {
                        break
                    }
                    total += n
                }
            }
            // Playlists (m3u, pls, HLS) are short; streams send data at once.
            return if (total > 0) Status.OK else Status.FAILED
        } finally {
            connection.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        return manager.activeNetworkInfo?.isConnected ?: false
    }
}
