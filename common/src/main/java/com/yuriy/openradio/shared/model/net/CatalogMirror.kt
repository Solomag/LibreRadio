package com.yuriy.openradio.shared.model.net

import android.content.Context
import android.net.Uri
import com.yuriy.openradio.shared.utils.AppLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream

/**
 * A copy of the station catalog for when the catalog's own server does not answer or the network cuts it off.
 *
 * The copy is made once a day by .github/workflows/catalog-mirror.yml (tools/catalog_mirror.py) into the
 * "catalog" branch of the app's repository, and downloaded from GitHub or jsDelivr (other networks than the
 * catalog's server). It answers the same requests as the catalog, in the same JSON, so the rest of the app
 * does not know where the answer came from.
 *
 * The copy is kept on disk and read line by line for each request: held in memory it would take some 40 MB.
 */
object CatalogMirror {

    private const val TAG = "CMIR"
    private const val FILE_NAME = "catalog_mirror.tsv"
    private const val HEADER = "#libreradio-catalog 1 "
    private const val MIN_STATIONS = 10_000
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000

    /**
     * After the catalog failed and the copy answered, the copy answers first for this long, ms:
     * each try of a cut-off catalog costs the user several seconds.
     */
    private const val PREFER_MS = 15 * 60 * 1000L

    /**
     * A failed download is not repeated for this long, ms.
     */
    private const val RETRY_DOWNLOAD_MS = 5 * 60 * 1000L

    /**
     * A copy older than this is refreshed (in the background, the old one answers meanwhile), ms.
     */
    private const val STALE_MS = 3 * 24 * 60 * 60 * 1000L

    private val SOURCES = arrayOf(
        "https://raw.githubusercontent.com/Solomag/LibreRadio/catalog/catalog.tsv.gz",
        "https://gcore.jsdelivr.net/gh/Solomag/LibreRadio@catalog/catalog.tsv.gz",
        "https://fastly.jsdelivr.net/gh/Solomag/LibreRadio@catalog/catalog.tsv.gz",
        "https://cdn.jsdelivr.net/gh/Solomag/LibreRadio@catalog/catalog.tsv.gz"
    )

    /**
     * Field order of a line, as tools/catalog_mirror.py writes it.
     */
    private const val F_UUID = 0
    private const val F_NAME = 1
    private const val F_URL = 2
    private const val F_URL_RESOLVED = 3
    private const val F_HOMEPAGE = 4
    private const val F_FAVICON = 5
    private const val F_TAGS = 6
    private const val F_COUNTRY = 7
    private const val F_COUNTRY_CODE = 8
    private const val F_CODEC = 9
    private const val F_BITRATE = 10
    private const val F_LAST_CHECK_OK = 11
    private const val F_LAST_CHECK_OK_TIME = 12
    private const val F_LAST_CHANGE_TIME = 13
    private const val F_CLICK_COUNT = 14
    private const val FIELDS = 15

    private val mLock = Any()
    private val mDownloadLock = Any()

    @Volatile
    private var mPreferUntil = 0L

    @Volatile
    private var mNextDownloadAt = 0L

    @Volatile
    private var mLastAnswerAt = 0L

    @Volatile
    private var mRefreshing = false

    /**
     * Header of the copy on disk ("#libreradio-catalog 1 <time> <count>"), null before it is read.
     */
    private var mHeader: String? = null
    private var mTags: List<Pair<String, Int>>? = null
    private var mCountries: List<Triple<String, String, Int>>? = null

    /**
     * @return True for a request to the station catalog (see [UrlLayerRadioBrowserImpl]).
     */
    fun isCatalog(uri: Uri): Boolean {
        return uri.toString().startsWith(UrlLayerRadioBrowserImpl.BASE_URL_PREFIX)
    }

    /**
     * @return True when the copy should answer before the catalog is tried (it failed a moment ago).
     */
    fun isPreferred(): Boolean = CatalogStatus.now() < mPreferUntil

    /**
     * The catalog answered: it is tried first again.
     */
    fun onCatalogAnswered() {
        mPreferUntil = 0L
    }

    /**
     * @return True when the copy answered a request at or after [since].
     */
    fun answeredSince(since: Long): Boolean {
        val at = mLastAnswerAt
        return at > 0 && at >= since
    }

    /**
     * @return When the copy was made, in milliseconds since 1970 (UTC), or 0 when there is no copy.
     */
    fun madeAt(context: Context): Long {
        val header = header(context) ?: return 0L
        return parseTime(header.removePrefix(HEADER).substringBefore(' '))
    }

    /**
     * Answers a catalog request from the copy, downloading the copy first when there is none.
     *
     * @param failed True when the catalog itself failed just now (then the copy answers first for a while).
     * @return The answer in the catalog's JSON, or null when there is no copy or the request is not known.
     */
    fun answer(context: Context, uri: Uri, failed: Boolean): ByteArray? {
        val file = file(context)
        if (!file.exists() && !download(context)) {
            return null
        }
        refreshIfStale(context)
        val started = CatalogStatus.now()
        val json = try {
            synchronized(mLock) { query(file, uri) }
        } catch (e: Exception) {
            AppLogger.e("$TAG answer $uri", e)
            null
        } ?: return null
        if (failed) {
            mPreferUntil = CatalogStatus.now() + PREFER_MS
        }
        mLastAnswerAt = CatalogStatus.now()
        CatalogStatus.onSuccess()
        AppLogger.i("$TAG answered ${json.length} chars in ${CatalogStatus.now() - started} ms for $uri")
        return json.toByteArray(Charsets.UTF_8)
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    private fun header(context: Context): String? {
        synchronized(mLock) {
            mHeader?.let { return it }
            val file = file(context)
            if (!file.exists()) {
                return null
            }
            mHeader = try {
                BufferedReader(InputStreamReader(FileInputStream(file), Charsets.UTF_8)).use { it.readLine() }
            } catch (e: IOException) {
                null
            }
            return mHeader
        }
    }

    private fun parseTime(text: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
            format.timeZone = TimeZone.getTimeZone("UTC")
            format.parse(text)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun refreshIfStale(context: Context) {
        val made = madeAt(context)
        if (made != 0L && System.currentTimeMillis() - made < STALE_MS) {
            return
        }
        if (mRefreshing || CatalogStatus.now() < mNextDownloadAt) {
            return
        }
        mRefreshing = true
        Thread {
            try {
                download(context)
            } finally {
                mRefreshing = false
            }
        }.start()
    }

    /**
     * Downloads the copy from the first source that gives a whole one.
     *
     * @return True when a new copy is on disk.
     */
    private fun download(context: Context): Boolean {
        synchronized(mDownloadLock) {
            if (CatalogStatus.now() < mNextDownloadAt) {
                return file(context).exists()
            }
            for (source in SOURCES) {
                if (downloadFrom(context, source)) {
                    return true
                }
            }
            mNextDownloadAt = CatalogStatus.now() + RETRY_DOWNLOAD_MS
            return false
        }
    }

    private fun downloadFrom(context: Context, source: String): Boolean {
        val tmp = File(context.filesDir, "$FILE_NAME.tmp")
        val started = CatalogStatus.now()
        var connection: HttpURLConnection? = null
        try {
            connection = URL(source).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            if (connection.responseCode !in 200..299) {
                AppLogger.w("$TAG $source answered ${connection.responseCode}")
                return false
            }
            var lines = 0
            var header: String? = null
            unpack(BufferedInputStream(connection.inputStream, 64 * 1024)).use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8), 64 * 1024).use { reader ->
                    FileOutputStream(tmp).bufferedWriter(Charsets.UTF_8).use { writer ->
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (header == null) {
                                header = line
                                if (!line.startsWith(HEADER)) {
                                    AppLogger.w("$TAG $source: not a catalog copy")
                                    return false
                                }
                            } else {
                                lines++
                            }
                            writer.write(line)
                            writer.write("\n")
                        }
                    }
                }
            }
            if (lines < MIN_STATIONS) {
                AppLogger.w("$TAG $source: only $lines stations")
                return false
            }
            synchronized(mLock) {
                val file = file(context)
                if (!tmp.renameTo(file)) {
                    file.delete()
                    if (!tmp.renameTo(file)) {
                        return false
                    }
                }
                mHeader = header
                mTags = null
                mCountries = null
            }
            AppLogger.i("$TAG got $lines stations from $source in ${CatalogStatus.now() - started} ms: $header")
            return true
        } catch (e: Exception) {
            AppLogger.e("$TAG download $source", e)
            return false
        } finally {
            connection?.disconnect()
            tmp.delete()
        }
    }

    /**
     * The copy is gzip; a server may also unpack it on the way (then it comes plain).
     */
    private fun unpack(input: BufferedInputStream): InputStream {
        input.mark(2)
        val first = input.read()
        val second = input.read()
        input.reset()
        return if (first == 0x1f && second == 0x8b) GZIPInputStream(input, 64 * 1024) else input
    }

    private inline fun forEachStation(file: File, block: (Array<String>) -> Unit) {
        BufferedReader(InputStreamReader(FileInputStream(file), Charsets.UTF_8), 64 * 1024).use { reader ->
            reader.readLine()
            while (true) {
                val line = reader.readLine() ?: break
                val fields = line.split('\t')
                if (fields.size < FIELDS) {
                    continue
                }
                block(fields.toTypedArray())
            }
        }
    }

    private fun query(file: File, uri: Uri): String? {
        val segments = uri.pathSegments
        val json = segments.indexOf("json")
        if (json < 0 || json + 1 >= segments.size) {
            return null
        }
        val path = segments.subList(json + 1, segments.size)
        val offset = uri.getQueryParameter("offset")?.toIntOrNull() ?: 0
        val limit = uri.getQueryParameter("limit")?.toIntOrNull() ?: Int.MAX_VALUE
        return when {
            path[0] == "tags" -> tags(file, limit)
            path[0] == "countries" -> countries(file)
            path[0] != "stations" || path.size < 2 -> null
            path[1] == "bytag" && path.size > 2 -> {
                val tag = path[2].lowercase(Locale.ROOT)
                stations(file, offset, limit, byName) { hasTag(it[F_TAGS], tag) }
            }
            path[1] == "bycountrycodeexact" && path.size > 2 -> {
                val code = path[2].uppercase(Locale.ROOT)
                stations(file, offset, limit, byName) { it[F_COUNTRY_CODE].equals(code, true) }
            }
            path[1] == "topclick" -> stations(file, 0, path.getOrNull(2)?.toIntOrNull() ?: 50, byClicks) { true }
            path[1] == "lastchange" -> stations(file, 0, path.getOrNull(2)?.toIntOrNull() ?: 50, byChange) { true }
            path[1] == "search" -> {
                val name = uri.getQueryParameter("name")?.trim()?.lowercase(Locale.ROOT)
                val tag = uri.getQueryParameter("tag")?.trim()?.lowercase(Locale.ROOT)
                val code = uri.getQueryParameter("countrycode")?.trim()
                stations(file, offset, limit, byClicks) {
                    (name.isNullOrEmpty() || it[F_NAME].lowercase(Locale.ROOT).contains(name)) &&
                            (tag.isNullOrEmpty() || it[F_TAGS].lowercase(Locale.ROOT).contains(tag)) &&
                            (code.isNullOrEmpty() || it[F_COUNTRY_CODE].equals(code, true))
                }
            }
            else -> null
        }
    }

    /**
     * As the catalog's "bytag": a tag of the station contains the asked one.
     */
    private fun hasTag(tags: String, tag: String): Boolean {
        if (tags.isEmpty()) {
            return false
        }
        return tags.lowercase(Locale.ROOT).split(',').any { it.trim().contains(tag) }
    }

    private val byName = Comparator<Array<String>> { a, b -> a[F_NAME].compareTo(b[F_NAME], true) }
    private val byClicks = Comparator<Array<String>> { a, b -> clicks(b).compareTo(clicks(a)) }
    private val byChange = Comparator<Array<String>> { a, b -> b[F_LAST_CHANGE_TIME].compareTo(a[F_LAST_CHANGE_TIME]) }

    private fun clicks(fields: Array<String>) = fields[F_CLICK_COUNT].toIntOrNull() ?: 0

    private inline fun stations(
        file: File, offset: Int, limit: Int, order: Comparator<Array<String>>, match: (Array<String>) -> Boolean
    ): String {
        // Only the best offset + limit stations are kept: all 50 000 at once would take tens of MB.
        val keep = (offset.toLong() + limit).coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1)
        val worstFirst = java.util.PriorityQueue<Array<String>>(minOf(keep, 1024)) { a, b -> order.compare(b, a) }
        forEachStation(file) {
            if (match(it)) {
                worstFirst.add(it)
                if (worstFirst.size > keep) {
                    worstFirst.poll()
                }
            }
        }
        val found = ArrayList<Array<String>>(worstFirst)
        found.sortWith(order)
        val array = JSONArray()
        val end = minOf(found.size.toLong(), offset.toLong() + limit).toInt()
        for (i in offset until end) {
            array.put(toJson(found[i]))
        }
        return array.toString()
    }

    private fun toJson(f: Array<String>): JSONObject {
        val station = JSONObject()
        station.put("stationuuid", f[F_UUID])
        station.put("name", f[F_NAME])
        station.put("url", f[F_URL])
        station.put("url_resolved", f[F_URL_RESOLVED])
        station.put("homepage", f[F_HOMEPAGE])
        station.put("favicon", f[F_FAVICON])
        station.put("tags", f[F_TAGS])
        station.put("country", f[F_COUNTRY])
        station.put("countrycode", f[F_COUNTRY_CODE])
        station.put("codec", f[F_CODEC])
        station.put("bitrate", f[F_BITRATE].toIntOrNull() ?: 0)
        station.put("lastcheckok", f[F_LAST_CHECK_OK].toIntOrNull() ?: 1)
        station.put("lastcheckoktime", f[F_LAST_CHECK_OK_TIME])
        station.put("lastchangetime", f[F_LAST_CHANGE_TIME])
        station.put("clickcount", clicks(f))
        return station
    }

    private fun tags(file: File, limit: Int): String {
        val tags = mTags ?: run {
            val counts = HashMap<String, Int>()
            forEachStation(file) { fields ->
                for (raw in fields[F_TAGS].split(',')) {
                    val tag = raw.trim().lowercase(Locale.ROOT)
                    if (tag.isNotEmpty()) {
                        counts[tag] = (counts[tag] ?: 0) + 1
                    }
                }
            }
            counts.entries.sortedByDescending { it.value }.take(1000).map { it.key to it.value }
        }
        mTags = tags
        val array = JSONArray()
        for ((name, count) in tags.take(limit)) {
            array.put(JSONObject().put("name", name).put("stationcount", count))
        }
        return array.toString()
    }

    private fun countries(file: File): String {
        val countries = mCountries ?: run {
            val counts = HashMap<String, Int>()
            val names = HashMap<String, String>()
            forEachStation(file) { fields ->
                val code = fields[F_COUNTRY_CODE].uppercase(Locale.ROOT)
                if (code.length == 2) {
                    counts[code] = (counts[code] ?: 0) + 1
                    if (!names.containsKey(code) && fields[F_COUNTRY].isNotEmpty()) {
                        names[code] = fields[F_COUNTRY]
                    }
                }
            }
            counts.entries.sortedBy { it.key }.map { Triple(it.key, names[it.key] ?: it.key, it.value) }
        }
        mCountries = countries
        val array = JSONArray()
        for ((code, name, count) in countries) {
            array.put(JSONObject().put("name", name).put("iso_3166_1", code).put("stationcount", count))
        }
        return array.toString()
    }
}
