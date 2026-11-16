package com.yuriy.openradio.mobile.compose

import android.content.Context
import coil.Coil
import coil.ImageLoader
import com.yuriy.openradio.shared.model.net.NoSniConnection
import com.yuriy.openradio.shared.model.net.StationCheck
import com.yuriy.openradio.shared.utils.AppUtils
import com.yuriy.openradio.shared.model.net.NetProxy
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

/**
 * Station logos load like the streams: when the network breaks the secure connection to a server
 * (a block by its name) and the bypass is on, the logo is downloaded without the server name.
 */
object BypassImages {

    private var installed = false

    @Synchronized
    fun install(context: Context) {
        if (installed) {
            return
        }
        installed = true
        val app = context.applicationContext
        Coil.setImageLoader(
            ImageLoader.Builder(app)
                .okHttpClient {
                    OkHttpClient.Builder()
                        .addInterceptor(Retry(app))
                        // The proxy itself comes from the process-wide ProxySelector (NetProxy).
                        .proxyAuthenticator { _, response ->
                            val login = NetProxy.httpProxyLogin()
                            if (login == null || response.request.header("Proxy-Authorization") != null) {
                                null
                            } else {
                                response.request.newBuilder()
                                    .header("Proxy-Authorization", Credentials.basic(login.first, login.second))
                                    .build()
                            }
                        }
                        .build()
                }
                .build()
        )
    }

    private class Retry(private val context: Context) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            try {
                return chain.proceed(request)
            } catch (e: IOException) {
                if (StationCheck.isBypassEnabled(context).not() || NoSniConnection.isBlockedHandshake(e).not()) {
                    throw e
                }
                val connection = NoSniConnection.open(
                    request.url.toString(), mapOf("User-Agent" to AppUtils.getUserAgent(context)), true
                )
                try {
                    val code = connection.responseCode
                    val bytes = if (code in 200..299) connection.inputStream.use { it.readBytes() } else ByteArray(0)
                    return Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message(connection.responseMessage ?: "")
                        .body(bytes.toResponseBody(connection.contentType?.toMediaTypeOrNull()))
                        .build()
                } finally {
                    connection.disconnect()
                }
            }
        }
    }
}
