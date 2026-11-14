package com.yuriy.openradio.shared.model.net

import android.os.Build
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.URL
import java.security.cert.CertificateException
import javax.net.ssl.HandshakeCompletedListener
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Connections to a station that pass a provider's block by the server name.
 *
 * Some providers cut a secure connection when they see a blocked name in its first packet (SNI).
 * The station's server usually answers without a name too, so the bypass connects without one.
 * The certificate chain is still checked by the system; only the check that it is issued for this very
 * name is skipped (without a name the server may present a certificate of a sibling address).
 */
object NoSniConnection {

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val MAX_REDIRECTS = 5

    /**
     * A failed secure connection that the bypass may help with: the handshake was broken, not refused
     * because of the station's certificate.
     */
    fun isBlockedHandshake(error: Throwable): Boolean {
        var cause: Throwable? = error
        var ssl = false
        while (cause != null) {
            if (cause is CertificateException) {
                return false
            }
            if (cause is SSLException) {
                ssl = true
            }
            cause = cause.cause
        }
        return ssl
    }

    /**
     * Opens [url] (following redirects, also between http and https) and returns the connection with the
     * response received. With [bypass], secure connections are made without the server name.
     */
    @Throws(IOException::class)
    fun open(url: String, headers: Map<String, String>, bypass: Boolean): HttpURLConnection {
        var current = URL(url)
        repeat(MAX_REDIRECTS + 1) {
            val connection = current.openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            for ((key, value) in headers) {
                connection.setRequestProperty(key, value)
            }
            if (bypass && connection is HttpsURLConnection) {
                connection.sslSocketFactory = Factory
                connection.hostnameVerifier = AnyNameVerifier
            }
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrEmpty()) {
                    throw IOException("Redirect without a location from $current")
                }
                current = URL(current, location)
                return@repeat
            }
            return connection
        }
        throw IOException("Too many redirects for $url")
    }

    /**
     * The chain was already checked by the system trust manager during the handshake.
     */
    private object AnyNameVerifier : HostnameVerifier {
        override fun verify(hostname: String?, session: SSLSession?): Boolean = session != null
    }

    /**
     * Makes TLS sockets that do not send the server name. The socket is wrapped, so the HTTP stack cannot
     * switch the name back on through the platform socket's own methods.
     */
    private object Factory : SSLSocketFactory() {

        private val system: SSLSocketFactory get() = HttpsURLConnection.getDefaultSSLSocketFactory()

        override fun getDefaultCipherSuites(): Array<String> = system.defaultCipherSuites

        override fun getSupportedCipherSuites(): Array<String> = system.supportedCipherSuites

        override fun createSocket(socket: Socket, host: String?, port: Int, autoClose: Boolean): Socket {
            // An IP address instead of the name: no name goes into the handshake.
            val address = socket.inetAddress?.hostAddress ?: host
            val ssl = system.createSocket(socket, address, port, autoClose) as SSLSocket
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val parameters = ssl.sslParameters
                parameters.serverNames = emptyList()
                ssl.sslParameters = parameters
            }
            return NoNameSocket(ssl)
        }

        override fun createSocket(host: String?, port: Int): Socket =
            createSocket(Socket(host, port), host, port, true)

        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
            createSocket(Socket(host, port, localHost, localPort), host, port, true)

        override fun createSocket(host: InetAddress?, port: Int): Socket =
            createSocket(Socket(host, port), host?.hostAddress, port, true)

        override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
            createSocket(Socket(address, port, localAddress, localPort), address?.hostAddress, port, true)
    }

    /**
     * An [SSLSocket] that forwards everything to [s], except that the server name cannot be set again.
     */
    private class NoNameSocket(private val s: SSLSocket) : SSLSocket() {
        override fun getSupportedCipherSuites(): Array<String> = s.supportedCipherSuites
        override fun getEnabledCipherSuites(): Array<String> = s.enabledCipherSuites
        override fun setEnabledCipherSuites(suites: Array<out String>?) {
            s.enabledCipherSuites = suites
        }

        override fun getSupportedProtocols(): Array<String> = s.supportedProtocols
        override fun getEnabledProtocols(): Array<String> = s.enabledProtocols
        override fun setEnabledProtocols(protocols: Array<out String>?) {
            s.enabledProtocols = protocols
        }

        override fun getSession(): SSLSession = s.session
        override fun addHandshakeCompletedListener(listener: HandshakeCompletedListener?) =
            s.addHandshakeCompletedListener(listener)

        override fun removeHandshakeCompletedListener(listener: HandshakeCompletedListener?) =
            s.removeHandshakeCompletedListener(listener)

        override fun startHandshake() = s.startHandshake()
        override fun setUseClientMode(mode: Boolean) {
            s.useClientMode = mode
        }

        override fun getUseClientMode(): Boolean = s.useClientMode
        override fun setNeedClientAuth(need: Boolean) {
            s.needClientAuth = need
        }

        override fun getNeedClientAuth(): Boolean = s.needClientAuth
        override fun setWantClientAuth(want: Boolean) {
            s.wantClientAuth = want
        }

        override fun getWantClientAuth(): Boolean = s.wantClientAuth
        override fun setEnableSessionCreation(flag: Boolean) {
            s.enableSessionCreation = flag
        }

        override fun getEnableSessionCreation(): Boolean = s.enableSessionCreation
        override fun getSSLParameters(): SSLParameters = s.sslParameters
        override fun setSSLParameters(params: SSLParameters?) {
            if (params != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                params.serverNames = emptyList()
            }
            s.sslParameters = params
        }

        override fun getInputStream(): InputStream = s.inputStream
        override fun getOutputStream(): OutputStream = s.outputStream
        override fun close() = s.close()
        override fun isClosed(): Boolean = s.isClosed
        override fun isConnected(): Boolean = s.isConnected
        override fun isBound(): Boolean = s.isBound
        override fun isInputShutdown(): Boolean = s.isInputShutdown
        override fun isOutputShutdown(): Boolean = s.isOutputShutdown
        override fun shutdownInput() = s.shutdownInput()
        override fun shutdownOutput() = s.shutdownOutput()
        override fun getInetAddress(): InetAddress? = s.inetAddress
        override fun getLocalAddress(): InetAddress? = s.localAddress
        override fun getPort(): Int = s.port
        override fun getLocalPort(): Int = s.localPort
        override fun getRemoteSocketAddress(): SocketAddress? = s.remoteSocketAddress
        override fun getLocalSocketAddress(): SocketAddress? = s.localSocketAddress
        override fun setSoTimeout(timeout: Int) {
            s.soTimeout = timeout
        }

        override fun getSoTimeout(): Int = s.soTimeout
        override fun setTcpNoDelay(on: Boolean) {
            s.tcpNoDelay = on
        }

        override fun getTcpNoDelay(): Boolean = s.tcpNoDelay
        override fun setKeepAlive(on: Boolean) {
            s.keepAlive = on
        }

        override fun getKeepAlive(): Boolean = s.keepAlive
        override fun setSoLinger(on: Boolean, linger: Int) = s.setSoLinger(on, linger)
        override fun getSoLinger(): Int = s.soLinger
        override fun setReceiveBufferSize(size: Int) {
            s.receiveBufferSize = size
        }

        override fun getReceiveBufferSize(): Int = s.receiveBufferSize
        override fun setSendBufferSize(size: Int) {
            s.sendBufferSize = size
        }

        override fun getSendBufferSize(): Int = s.sendBufferSize
        override fun toString(): String = "NoNameSocket($s)"
    }
}
