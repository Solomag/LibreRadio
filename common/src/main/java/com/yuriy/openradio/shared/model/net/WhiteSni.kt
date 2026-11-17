package com.yuriy.openradio.shared.model.net

import android.content.Context
import android.os.Build
import com.yuriy.openradio.shared.utils.AppLogger
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * A white server name: secure connections show the network a name it lets through (a big local site)
 * instead of the station's own, for networks that block or cut connections by the name or let through
 * only listed ones.
 *
 * The certificate is still checked against the station's real name: most single servers present their
 * certificate whatever name they are asked for, so the connection stays as safe as a normal one. A server
 * that refuses the white name or presents another certificate (shared hosting, CDNs, the catalog) is
 * connected again at once with its own name and is not tried with the white one again until restart.
 *
 * Set as the default for all HttpsURLConnection: the player, the catalog, the station check, updates.
 * Not used through a proxy: the proxy carries the connection past the network anyway.
 */
object WhiteSni {

    private const val TAG = "WSNI"
    private const val PREFS = "white_sni"
    private const val KEY_ON = "on"
    private const val KEY_DOMAIN = "domain"
    const val DEFAULT_DOMAIN = "vk.com"
    private const val HANDSHAKE_TIMEOUT_MS = 15_000
    private const val CONNECT_TIMEOUT_MS = 15_000

    @Volatile
    private var mOn = false

    @Volatile
    private var mDomain = DEFAULT_DOMAIN

    private var mSystem: SSLSocketFactory? = null

    /**
     * Servers that did not take the white name.
     */
    private val mRefused: MutableSet<String> = Collections.synchronizedSet(HashSet())

    /**
     * Servers that took it (for the log: each once).
     */
    private val mAccepted: MutableSet<String> = Collections.synchronizedSet(HashSet())

    @Synchronized
    fun install(context: Context) {
        if (mSystem != null) {
            return
        }
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mOn = prefs.getBoolean(KEY_ON, false)
        mDomain = normalize(prefs.getString(KEY_DOMAIN, DEFAULT_DOMAIN) ?: DEFAULT_DOMAIN)
        mSystem = HttpsURLConnection.getDefaultSSLSocketFactory()
        HttpsURLConnection.setDefaultSSLSocketFactory(Factory)
        AppLogger.i("$TAG installed, on=$mOn domain=$mDomain")
    }

    fun isOn(): Boolean = mOn

    fun domain(): String = mDomain

    fun save(context: Context, on: Boolean, domain: String) {
        install(context)
        mOn = on
        mDomain = normalize(domain)
        mRefused.clear()
        mAccepted.clear()
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ON, on)
            .putString(KEY_DOMAIN, mDomain)
            .apply()
        AppLogger.i("$TAG saved, on=$on domain=$mDomain")
        // What plays depends on the route: check the stations again.
        StationCheck.clear(context)
    }

    private fun normalize(domain: String): String {
        val name = domain.trim().lowercase(Locale.ROOT)
            .removePrefix("https://").removePrefix("http://").substringBefore('/')
        return name.ifEmpty { DEFAULT_DOMAIN }
    }

    private fun isAddress(host: String): Boolean = host.contains(':') || host.all { it.isDigit() || it == '.' }

    private object Factory : SSLSocketFactory() {

        private val system: SSLSocketFactory get() = mSystem ?: HttpsURLConnection.getDefaultSSLSocketFactory()

        override fun getDefaultCipherSuites(): Array<String> = system.defaultCipherSuites

        override fun getSupportedCipherSuites(): Array<String> = system.supportedCipherSuites

        override fun createSocket(socket: Socket, host: String?, port: Int, autoClose: Boolean): Socket {
            val white = mDomain
            val name = host?.lowercase(Locale.ROOT)
            if (!mOn || name == null || isAddress(name) || name == white || mRefused.contains(name) ||
                NetProxy.isOn() || NetProxy.isLocal(name)
            ) {
                return system.createSocket(socket, host, port, autoClose)
            }
            val address = socket.inetAddress
            val timeout = socket.soTimeout
            try {
                socket.soTimeout = HANDSHAKE_TIMEOUT_MS
                // The white name goes into the handshake.
                val ssl = system.createSocket(socket, white, port, autoClose) as SSLSocket
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val parameters = ssl.sslParameters
                    parameters.serverNames = listOf(SNIHostName(white))
                    ssl.sslParameters = parameters
                }
                ssl.startHandshake()
                // The chain was checked in the handshake; the name is checked here, against the real one.
                if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(name, ssl.session)) {
                    ssl.close()
                    throw SSLPeerUnverifiedException("not a certificate of $name")
                }
                ssl.soTimeout = timeout
                if (mAccepted.add(name)) {
                    AppLogger.i("$TAG $name: connected with the white name $white")
                }
                return NoSniConnection.NoNameSocket(ssl, true)
            } catch (e: IOException) {
                mRefused.add(name)
                AppLogger.i("$TAG $name: not with the white name ($e), its own name from now on")
                runCatching { socket.close() }
                // The first connection is spent: a new one to the same server.
                val fresh = Socket()
                fresh.connect(InetSocketAddress(address, socket.port.takeIf { it > 0 } ?: port), CONNECT_TIMEOUT_MS)
                fresh.soTimeout = timeout
                return system.createSocket(fresh, host, port, true)
            }
        }

        override fun createSocket(host: String?, port: Int): Socket =
            createSocket(Socket(host, port), host, port, true)

        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
            createSocket(Socket(host, port, localHost, localPort), host, port, true)

        override fun createSocket(host: InetAddress?, port: Int): Socket =
            system.createSocket(host, port)

        override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
            system.createSocket(address, port, localAddress, localPort)
    }
}
