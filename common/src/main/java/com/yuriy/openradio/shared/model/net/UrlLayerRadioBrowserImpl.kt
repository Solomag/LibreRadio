package com.yuriy.openradio.shared.model.net

import android.net.Uri
import androidx.core.util.Pair
import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommon
import com.yuriy.openradio.shared.utils.AppLogger
import com.yuriy.openradio.shared.utils.NetUtils
import java.net.InetAddress
import java.net.MalformedURLException
import java.net.URL
import java.net.UnknownHostException
import java.util.Random

class UrlLayerRadioBrowserImpl : UrlLayer {

    private val mLock = Any()
    private val mRandom = Random()

    /**
     * Known API servers ("https://host"): from the DNS look up plus [RESERVED_URLS].
     */
    private val mServers = LinkedHashSet<String>()

    /**
     * Servers that failed lately, with the time until which they are skipped.
     */
    private val mBadUntil = HashMap<String, Long>()

    /**
     * Server in use: one server for all requests keeps paging consistent; changed only when it fails.
     */
    private var mCurrent: String? = null
    private var mLookupAt = 0L

    /**
     * Picks an API server for requests that use the look up prefix; other URLs are returned as they are.
     */
    override fun getConnectionUrl(
        uri: Uri,
        parameters: List<Pair<String, String>>
    ): URL? {
        val uriStr = uri.toString()
        if (!uriStr.startsWith(BASE_URL_PREFIX)) {
            return getUrl(uriStr, parameters)
        }
        return getUrlModified(uriStr, pickServer(), parameters)
    }

    override fun reportResult(url: URL, success: Boolean) {
        val server = "https://" + url.host
        synchronized(mLock) {
            if (mServers.contains(server).not()) {
                return
            }
            if (success) {
                mBadUntil.remove(server)
            } else {
                AppLogger.w("$TAG server failed: $server")
                mBadUntil[server] = CatalogStatus.now() + BAD_SERVER_MS
                if (mCurrent == server) {
                    mCurrent = null
                }
            }
            Unit
        }
        if (success) {
            CatalogStatus.onSuccess()
        } else {
            CatalogStatus.onFailure()
        }
    }

    private fun pickServer(): String {
        lookUpServers()
        synchronized(mLock) {
            val now = CatalogStatus.now()
            mCurrent?.let {
                if ((mBadUntil[it] ?: 0L) <= now) {
                    return it
                }
            }
            val healthy = mServers.filter { (mBadUntil[it] ?: 0L) <= now }
            // All failed lately: try the one that failed longest ago.
            val pick = if (healthy.isNotEmpty()) {
                healthy[mRandom.nextInt(healthy.size)]
            } else {
                mServers.minByOrNull { mBadUntil[it] ?: 0L } ?: RESERVED_URLS[0]
            }
            mCurrent = pick
            return pick
        }
    }

    /**
     * Finds the API servers by DNS (at most once per [LOOK_UP_INTERVAL_MS]; once is enough after a success).
     */
    private fun lookUpServers() {
        synchronized(mLock) {
            val now = CatalogStatus.now()
            if (mLookupAt != 0L && (mServers.size > RESERVED_URLS.size || now - mLookupAt < LOOK_UP_INTERVAL_MS)) {
                return
            }
            mLookupAt = now
            if (mServers.isEmpty()) {
                mServers.addAll(RESERVED_URLS)
            }
        }
        val found = try {
            InetAddress.getAllByName(LOOK_UP_DNS).map { "https://" + it.canonicalHostName }
        } catch (exception: UnknownHostException) {
            AppLogger.e("$TAG look up $LOOK_UP_DNS", exception)
            emptyList()
        }
        synchronized(mLock) {
            for (server in found) {
                // A host without a reverse DNS name comes back as a bare IP: TLS would fail on it.
                val host = server.removePrefix("https://")
                if (host.contains(':') || host.none { it.isLetter() }) {
                    continue
                }
                if (mServers.add(server)) {
                    AppLogger.i("$TAG look up host:$server")
                }
            }
        }
    }

    override fun getAllCategoriesUrl(): Uri {
        return Uri.parse(BASE_URL + "tags?order=stationcount&reverse=true&hidebroken=true&limit=" + CATEGORIES_LIMIT)
    }

    override fun getStationsInCategory(categoryId: String, pageNumber: Int): Uri {
        return Uri.parse(
            BASE_URL + "stations/bytag/" + encodeValue(categoryId) + "?hidebroken=true&order=name"
                    + "&offset=" + (pageNumber * (DependencyRegistryCommon.PAGE_SIZE + 1))
                    + "&limit=" + DependencyRegistryCommon.PAGE_SIZE
        )
    }

    override fun getStationsByCountry(countryCode: String, pageNumber: Int): Uri {
        return Uri.parse(
            BASE_URL + "stations/bycountrycodeexact/" + countryCode
                    + "?offset=" + (pageNumber * (DependencyRegistryCommon.PAGE_SIZE + 1))
                    + "&limit=" + DependencyRegistryCommon.PAGE_SIZE
        )
    }

    override fun getPopularStations(): Uri {
        return Uri.parse(BASE_URL + "stations/topclick/" + DependencyRegistryCommon.PAGE_SIZE)
    }

    override fun getNewStations(): Uri {
        return Uri.parse(BASE_URL + "stations/lastchange/" + DependencyRegistryCommon.PAGE_SIZE)
    }

    override fun getSearchUrl(query: String): Uri {
        return makeSearchUrl("name", query)
    }

    /**
     * Search by station name and by tag (genre), most popular first.
     * Radio Browser treats several filters in one request as AND, so these are separate requests.
     */
    override fun getSearchUrls(query: String): List<Uri> {
        val scoped = SearchQuery.decode(query)
        return listOf(
            makeSearchUrl("name", scoped.text, scoped.countryCode),
            makeSearchUrl("tag", scoped.text, scoped.countryCode)
        )
    }

    private fun makeSearchUrl(field: String, query: String, countryCode: String? = null): Uri {
        val country = if (countryCode.isNullOrEmpty()) "" else "&countrycode=" + Uri.encode(countryCode)
        return Uri.parse(
            BASE_URL + "stations/search?" + field + "=" + Uri.encode(query.trim()) + country
                    + "&hidebroken=true&order=clickcount&reverse=true"
                    + "&offset=" + 0
                    + "&limit=" + DependencyRegistryCommon.PAGE_SIZE
        )
    }

    override fun getAllCountries(): Uri {
        return Uri.parse(BASE_URL + "countries")
    }

    private fun getUrlModified(
        uriOrigin: String,
        uri: String?, parameters: List<Pair<String, String>>
    ): URL? {
        val uriModified = uriOrigin.replaceFirst(BASE_URL_PREFIX.toRegex(), uri!!)
        return getUrl(uriModified, parameters)
    }

    private fun getUrl(uri: String, parameters: List<Pair<String, String>>): URL? {
        return try {
            URL(uri)
        } catch (exception: MalformedURLException) {
            AppLogger.e(
                "$TAG getUrl ${
                    NetUtils.createExceptionMessage(
                        uri,
                        parameters
                    )
                }", exception
            )
            null
        }
    }

    /**
     * Method to encode a string value using UTF-8 encoding scheme.
     *
     * @param value
     * @return
     */
    private fun encodeValue(value: String): String {
        return value.replace(" ".toRegex(), "%20")
    }

    companion object {

        private const val TAG = "ULRBI"
        private const val LOOK_UP_DNS = "all.api.radio-browser.info"
        private const val BASE_URL_PREFIX = "https://do-look-up-dns-first"

        /**
         * Base URL for the API requests.
         */
        private const val BASE_URL = "${BASE_URL_PREFIX}/json/"
        /**
         * Servers known to work; the catalog lists its servers at all.api.radio-browser.info/json/servers.
         */
        private val RESERVED_URLS = arrayOf(
            "https://de1.api.radio-browser.info",
            "https://de2.api.radio-browser.info",
            "https://all.api.radio-browser.info"
        )

        /**
         * A server that failed is not used for this long, ms.
         */
        private const val BAD_SERVER_MS = 2 * 60 * 1000L

        private const val LOOK_UP_INTERVAL_MS = 60 * 1000L

        /**
         * Genres: the ones with most stations (the catalog has thousands of tags, most with a station or two).
         */
        private const val CATEGORIES_LIMIT = 300
    }
}
