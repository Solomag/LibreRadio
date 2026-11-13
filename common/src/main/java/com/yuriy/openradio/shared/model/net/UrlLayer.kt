package com.yuriy.openradio.shared.model.net

import android.net.Uri
import androidx.core.util.Pair
import java.net.URL

interface UrlLayer {

    companion object {

        /**
         * Id of the first page of the Radio Stations List.
         */
        const val FIRST_PAGE_INDEX = 0
    }

    fun getConnectionUrl(uri: Uri, parameters: List<Pair<String, String>>): URL?

    /**
     * Outcome of a request to [url] got from [getConnectionUrl]: lets the layer avoid a server that fails.
     */
    fun reportResult(url: URL, success: Boolean) {}

    /**
     * Get Uri for the All Categories list.
     *
     * @return [Uri]
     */
    fun getAllCategoriesUrl(): Uri

    /**
     * Get Uri for the list of the Radio Stations in concrete Category.
     *
     * @param categoryId Id of the Category.
     * @return [Uri]
     */
    fun getStationsInCategory(categoryId: String, pageNumber: Int): Uri

    /**
     * Get Uri for the list of the Radio Stations in country.
     *
     * @param countryCode Country Code.
     * @return [Uri]
     */
    fun getStationsByCountry(countryCode: String, pageNumber: Int): Uri

    /**
     * Get Uri for the list of the popular Radio Stations.
     *
     * @return [Uri]
     */
    fun getPopularStations(): Uri

    /**
     * Get Uri for the list of the recently added Radio Stations.
     *
     * @return [Uri]
     */
    fun getNewStations(): Uri

    /**
     * Get Uri for the search.
     *
     * @return [Uri].
     */
    fun getSearchUrl(query: String): Uri

    /**
     * Get all Uris a search should query; results are merged.
     * By default only [getSearchUrl] is used.
     */
    fun getSearchUrls(query: String): List<Uri> {
        return listOf(getSearchUrl(query))
    }

    fun getAllCountries(): Uri
}
