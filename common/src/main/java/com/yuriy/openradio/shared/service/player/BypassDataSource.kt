package com.yuriy.openradio.shared.service.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.yuriy.openradio.shared.model.net.NoSniConnection
import com.yuriy.openradio.shared.model.net.StationCheck
import com.yuriy.openradio.shared.utils.AppLogger
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection

/**
 * Plays through [primary]; when the network breaks the secure connection to a station (a block by the
 * server name) and the bypass is on, opens the same stream again without the server name.
 */
@UnstableApi
class FallbackDataSource(
    private val context: Context,
    private val primary: DataSource,
    private val userAgent: String
) : DataSource {

    private val mListeners = ArrayList<TransferListener>()
    private var mCurrent: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        mListeners.add(transferListener)
        primary.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val host = dataSpec.uri.host
        if (StationCheck.needsBypass(host)) {
            return openBypass(dataSpec)
        }
        try {
            mCurrent = primary
            return primary.open(dataSpec)
        } catch (e: IOException) {
            if (StationCheck.isBypassEnabled(context).not() || NoSniConnection.isBlockedHandshake(e).not()) {
                throw e
            }
            AppLogger.i("FallbackDataSource secure connection to $host broken, retry without the server name")
            runCatching { primary.close() }
            val length = openBypass(dataSpec)
            StationCheck.rememberBypassHost(host)
            return length
        }
    }

    private fun openBypass(dataSpec: DataSpec): Long {
        val bypass = NoSniDataSource(userAgent)
        for (listener in mListeners) {
            bypass.addTransferListener(listener)
        }
        mCurrent = bypass
        return bypass.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return mCurrent?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
    }

    override fun getUri(): Uri? = mCurrent?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = mCurrent?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            mCurrent?.close()
        } finally {
            mCurrent = null
        }
    }

    class Factory(
        private val context: Context,
        private val primary: DataSource.Factory,
        private val userAgent: String
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return FallbackDataSource(context.applicationContext, primary.createDataSource(), userAgent)
        }
    }
}

/**
 * A plain HTTP data source over [NoSniConnection] with the bypass on (live streams: no ranges, no length).
 */
@UnstableApi
private class NoSniDataSource(private val userAgent: String) : BaseDataSource(true) {

    private var mConnection: HttpURLConnection? = null
    private var mInput: InputStream? = null
    private var mUri: Uri? = null
    private var mOpened = false

    override fun open(dataSpec: DataSpec): Long {
        mUri = dataSpec.uri
        transferInitializing(dataSpec)
        val headers = HashMap(dataSpec.httpRequestHeaders)
        headers["User-Agent"] = userAgent
        val connection = NoSniConnection.open(dataSpec.uri.toString(), headers, true)
        mConnection = connection
        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            throw IOException("HTTP $code from ${dataSpec.uri}")
        }
        mInput = connection.inputStream
        mOpened = true
        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) {
            return 0
        }
        val n = mInput?.read(buffer, offset, length) ?: return C.RESULT_END_OF_INPUT
        if (n < 0) {
            return C.RESULT_END_OF_INPUT
        }
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = mUri

    override fun getResponseHeaders(): Map<String, List<String>> {
        return mConnection?.headerFields?.filterKeys { it != null } ?: emptyMap()
    }

    override fun close() {
        try {
            mInput?.close()
        } catch (_: IOException) {
        } finally {
            mInput = null
            mConnection?.disconnect()
            mConnection = null
            if (mOpened) {
                mOpened = false
                transferEnded()
            }
        }
    }
}
