package com.yuriy.openradio.shared.model.net

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.yuriy.openradio.shared.utils.AppLogger
import java.io.IOException
import java.net.Authenticator
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.URL

/**
 * A proxy for all of the app's connections: the catalog, the streams, logos, the station check, updates.
 *
 * It is set as the process-wide [ProxySelector], which both HttpURLConnection (the player, the catalog)
 * and OkHttp (logos) use. Local addresses (the phone itself, the home network, Tailscale) go direct.
 * A login, if any, is given only to the proxy itself, never to stations.
 */
object NetProxy {

    /**
     * [BYEDPI]: no outside proxy, ByeDPI on the phone itself (see [ByeDpi]).
     */
    enum class Type { OFF, SOCKS, HTTP, BYEDPI }

    class Settings(
        val type: Type, val host: String, val port: Int, val user: String, val password: String,
        /** ByeDPI's options, for [Type.BYEDPI]. */
        val dpiArgs: String = ""
    ) {
        val isOn: Boolean
            get() = when (type) {
                Type.OFF -> false
                Type.BYEDPI -> ByeDpi.isAvailable
                else -> host.isNotBlank() && port in 1..65535
            }

        /** ByeDPI's options in use: the first way when none was chosen. */
        val dpiArgsOrDefault: String get() = dpiArgs.ifBlank { ByeDpi.PRESETS[0] }
    }

    /**
     * What a test of the proxy found.
     */
    sealed class TestResult {
        /** Reached the catalog through the proxy and read [bytes] in [millis]. */
        class Ok(val millis: Long, val bytes: Int) : TestResult()

        /** The proxy did not answer or refused (wrong address, port, type or login). */
        class ProxyFailed(val reason: String) : TestResult()

        /** The connection stopped after the first kilobytes: the network cuts it here too. */
        class CutOff(val bytes: Int) : TestResult()
    }

    private const val TAG = "NPRX"
    private const val PREFS = "net_proxy"
    private const val KEY_TYPE = "type"
    private const val KEY_HOST = "host"
    private const val KEY_PORT = "port"
    private const val KEY_USER = "user"
    private const val KEY_PASSWORD = "password"
    private const val KEY_DPI_ARGS = "dpi_args"

    /**
     * A catalog page of a few hundred kilobytes: enough to pass the point where cut connections stop.
     */
    private const val TEST_URL = "https://de1.api.radio-browser.info/json/tags?limit=3000"

    @Volatile
    private var mSettings = Settings(Type.OFF, "", 0, "", "")
    private var mPrefs: SharedPreferences? = null
    private var mDefaultSelector: ProxySelector? = null

    /**
     * Grows on every change, so results that depend on the network (the station check) can be dropped.
     */
    @Volatile
    var version = 0
        private set

    @Synchronized
    fun install(context: Context) {
        if (mPrefs != null) {
            return
        }
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mPrefs = prefs
        mSettings = Settings(
            runCatching { Type.valueOf(prefs.getString(KEY_TYPE, Type.OFF.name)!!) }.getOrDefault(Type.OFF),
            prefs.getString(KEY_HOST, "") ?: "",
            prefs.getInt(KEY_PORT, 0),
            prefs.getString(KEY_USER, "") ?: "",
            prefs.getString(KEY_PASSWORD, "") ?: "",
            prefs.getString(KEY_DPI_ARGS, "") ?: ""
        )
        mDefaultSelector = ProxySelector.getDefault()
        ProxySelector.setDefault(Selector)
        Authenticator.setDefault(Login)
        AppLogger.i("$TAG installed, on=${mSettings.isOn} type=${mSettings.type}")
        startDpiInBackground()
    }

    /**
     * ByeDPI starts on the first connection anyway; starting it now saves that connection a moment.
     */
    private fun startDpiInBackground() {
        val settings = mSettings
        if (settings.type != Type.BYEDPI || !settings.isOn) {
            Thread { ByeDpi.stop() }.start()
            return
        }
        Thread { ByeDpi.start(settings.dpiArgsOrDefault) }.start()
    }

    fun settings(context: Context): Settings {
        install(context)
        return mSettings
    }

    fun isOn(): Boolean = mSettings.isOn

    /**
     * The login for an HTTP proxy, for clients that ask for it themselves (OkHttp), or null.
     */
    fun httpProxyLogin(): Pair<String, String>? {
        val settings = mSettings
        if (settings.isOn.not() || settings.type != Type.HTTP || settings.user.isEmpty()) {
            return null
        }
        return settings.user to settings.password
    }

    fun save(context: Context, settings: Settings) {
        install(context)
        mSettings = settings
        mPrefs?.edit()
            ?.putString(KEY_TYPE, settings.type.name)
            ?.putString(KEY_HOST, settings.host.trim())
            ?.putInt(KEY_PORT, settings.port)
            ?.putString(KEY_USER, settings.user)
            ?.putString(KEY_PASSWORD, settings.password)
            ?.putString(KEY_DPI_ARGS, settings.dpiArgs)
            ?.apply()
        startDpiInBackground()
        version++
        AppLogger.i("$TAG saved, on=${settings.isOn} type=${settings.type}")
        // What plays depends on the route: check the stations again.
        StationCheck.clear(context)
    }

    /**
     * Tries [settings] without saving them: downloads a catalog page through the proxy (blocking).
     */
    fun test(settings: Settings): TestResult {
        val proxy = toProxy(settings) ?: return TestResult.ProxyFailed("off")
        val started = SystemClock.elapsedRealtime()
        var total = 0
        val previous = mTesting
        // Set first: other connections meanwhile keep ByeDPI on the options under test.
        mTesting = settings
        try {
            if (settings.type == Type.BYEDPI && !ByeDpi.start(settings.dpiArgsOrDefault)) {
                return TestResult.ProxyFailed("ByeDPI")
            }
            val connection = URL(TEST_URL).openConnection(proxy) as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 10_000
            try {
                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_PROXY_AUTH) {
                    return TestResult.ProxyFailed("407")
                }
                if (code !in 200..299) {
                    return TestResult.ProxyFailed("HTTP $code")
                }
                val buffer = ByteArray(8192)
                try {
                    connection.inputStream.use { input ->
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) {
                                break
                            }
                            total += n
                        }
                    }
                } catch (e: IOException) {
                    if (total > 0) {
                        return TestResult.CutOff(total)
                    }
                    throw e
                }
                return TestResult.Ok(SystemClock.elapsedRealtime() - started, total)
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            AppLogger.d("$TAG test failed: $e")
            return TestResult.ProxyFailed(e.message ?: e.javaClass.simpleName)
        } finally {
            mTesting = previous
        }
    }

    /**
     * Tries ByeDPI's ways in turn (blocking) and returns the first that gets the catalog through whole,
     * or null when none does. [onTry] gets the number of the way being tried, from 1.
     */
    fun pickDpi(onTry: (Int) -> Unit): String? {
        for ((i, args) in ByeDpi.PRESETS.withIndex()) {
            onTry(i + 1)
            val result = test(Settings(Type.BYEDPI, "", 0, "", "", args))
            AppLogger.i("$TAG ByeDPI way ${i + 1} '$args': $result")
            if (result is TestResult.Ok) {
                return args
            }
        }
        return null
    }

    /**
     * Settings being tested: their login answers the proxy's question during the test,
     * and ByeDPI runs with their options meanwhile.
     */
    @Volatile
    private var mTesting: Settings? = null

    private fun toProxy(settings: Settings): Proxy? {
        if (settings.isOn.not()) {
            return null
        }
        if (settings.type == Type.BYEDPI) {
            return Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("127.0.0.1", ByeDpi.port))
        }
        val type = if (settings.type == Type.SOCKS) Proxy.Type.SOCKS else Proxy.Type.HTTP
        return Proxy(type, InetSocketAddress.createUnresolved(settings.host.trim(), settings.port))
    }

    /**
     * The phone itself, the home network and Tailscale are reached directly.
     */
    internal fun isLocal(host: String?): Boolean {
        val h = host?.lowercase()?.trim('[', ']') ?: return true
        if (h == "localhost" || h.endsWith(".local") || h.endsWith(".lan") || h.endsWith(".ts.net")) {
            return true
        }
        if (h == "::1" || h.startsWith("fe80:") || h.startsWith("fd") && h.contains(':')) {
            return true
        }
        val parts = h.split('.')
        if (parts.size != 4) {
            return false
        }
        val b = parts.map { it.toIntOrNull() ?: return false }
        return b[0] == 127 || b[0] == 10 || (b[0] == 192 && b[1] == 168) || (b[0] == 172 && b[1] in 16..31) ||
                (b[0] == 169 && b[1] == 254) || (b[0] == 100 && b[1] in 64..127)
    }

    private object Selector : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> {
            val settings = mTesting?.takeIf { it.type == Type.BYEDPI } ?: mSettings
            if (settings.isOn && !isLocal(uri?.host) && settings.type == Type.BYEDPI) {
                // Started (or restarted with other options) here, on the connection's own thread.
                ByeDpi.start(settings.dpiArgsOrDefault)
            }
            val proxy = toProxy(settings)
            if (proxy == null || isLocal(uri?.host) ||
                (settings.type != Type.BYEDPI && uri?.host.equals(settings.host.trim(), ignoreCase = true))
            ) {
                return mDefaultSelector?.select(uri) ?: listOf(Proxy.NO_PROXY)
            }
            return listOf(proxy)
        }

        override fun connectFailed(uri: URI?, address: SocketAddress?, error: IOException?) {
            AppLogger.w("$TAG proxy $address failed for $uri: $error")
        }
    }

    /**
     * Answers only the proxy's own login question.
     */
    private object Login : Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication? {
            val settings = mTesting ?: mSettings
            if (settings.isOn.not() || settings.user.isEmpty() || settings.type == Type.BYEDPI) {
                return null
            }
            val fromProxy = requestorType == RequestorType.PROXY ||
                    requestingProtocol?.startsWith("SOCKS", ignoreCase = true) == true
            if (fromProxy.not() || requestingHost?.equals(settings.host.trim(), ignoreCase = true) != true) {
                return null
            }
            return PasswordAuthentication(settings.user, settings.password.toCharArray())
        }
    }
}
