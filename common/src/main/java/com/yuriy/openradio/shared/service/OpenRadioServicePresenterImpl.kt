package com.yuriy.openradio.shared.service

import android.content.Context
import com.yuriy.openradio.shared.model.ModelLayer
import com.yuriy.openradio.shared.model.cast.CastLayer
import com.yuriy.openradio.shared.model.eq.EqualizerLayer
import com.yuriy.openradio.shared.model.media.Category
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.media.RadioStationManagerLayerListener
import com.yuriy.openradio.shared.model.media.item.MediaItemAllCategories
import com.yuriy.openradio.shared.model.media.item.MediaItemBrowseCar
import com.yuriy.openradio.shared.model.media.item.MediaItemChildCategories
import com.yuriy.openradio.shared.model.media.item.MediaItemCommand
import com.yuriy.openradio.shared.model.media.item.MediaItemCountriesList
import com.yuriy.openradio.shared.model.media.item.MediaItemCountryStations
import com.yuriy.openradio.shared.model.media.item.MediaItemFavoritesList
import com.yuriy.openradio.shared.model.media.item.MediaItemLocalsList
import com.yuriy.openradio.shared.model.media.item.MediaItemNewStations
import com.yuriy.openradio.shared.model.media.item.MediaItemPopularStations
import com.yuriy.openradio.shared.model.media.item.MediaItemRoot
import com.yuriy.openradio.shared.model.media.item.MediaItemRootCar
import com.yuriy.openradio.shared.model.media.item.MediaItemSearchFromApp
import com.yuriy.openradio.shared.model.media.item.MediaItemSearchFromService
import com.yuriy.openradio.shared.model.net.NetworkLayer
import com.yuriy.openradio.shared.model.net.NetworkMonitorListener
import com.yuriy.openradio.shared.model.net.UrlLayer
import com.yuriy.openradio.shared.model.source.Source
import com.yuriy.openradio.shared.model.storage.DeviceLocalsStorage
import com.yuriy.openradio.shared.model.storage.FavoritesStorage
import com.yuriy.openradio.shared.model.storage.LatestRadioStationStorage
import com.yuriy.openradio.shared.model.storage.RecentStationsStorage
import com.yuriy.openradio.shared.model.media.item.MediaItemRecentList
import com.yuriy.openradio.shared.model.storage.LocationStorage
import com.yuriy.openradio.shared.model.storage.NetworkSettingsStorage
import com.yuriy.openradio.shared.model.storage.cache.api.ApiCache
import com.yuriy.openradio.shared.model.storage.images.ImagesPersistenceLayer
import com.yuriy.openradio.shared.model.timer.SleepTimerModel
import com.yuriy.openradio.shared.model.translation.MediaIdBuilder
import com.yuriy.openradio.shared.model.translation.MediaIdBuilderDefault
import com.yuriy.openradio.shared.service.location.Country
import com.yuriy.openradio.shared.model.media.getStreamUrlFixed
import com.yuriy.openradio.shared.utils.AppUtils
import com.yuriy.openradio.shared.utils.SortUtils
import java.util.TreeSet

class OpenRadioServicePresenterImpl(
    isCar: Boolean,
    source: Source,
    private val mUrlLayer: UrlLayer,
    private val mNetworkLayer: NetworkLayer,
    private val mModelLayer: ModelLayer,
    private var mFavoritesStorage: FavoritesStorage,
    private val mDeviceLocalsStorage: DeviceLocalsStorage,
    private val mLatestRadioStationStorage: LatestRadioStationStorage,
    private val mNetworkSettingsStorage: NetworkSettingsStorage,
    private val mLocationStorage: LocationStorage,
    private var mImagesPersistenceLayer: ImagesPersistenceLayer,
    private val mEqualizerLayer: EqualizerLayer,
    private val mApiCachePersistent: ApiCache,
    private val mApiCacheInMemory: ApiCache,
    private val mSleepTimerModel: SleepTimerModel,
    private val mCountriesCache:TreeSet<Country>,
    private val mListener: RadioStationManagerLayerListener,
    private val mCastLayer: CastLayer,
    /**
     * Additional catalogs merged into the primary one (e.g. Web Radio next to Radio Browser).
     * Each catalog returns all its matches at once, so they are only queried for the first page.
     */
    /**
     * The last played stations ("Recent" folder).
     */
    private val mRecentStorage: RecentStationsStorage,
    private val mExtraCatalogs: List<kotlin.Pair<UrlLayer, ModelLayer>> = emptyList()
) : OpenRadioServicePresenter {

    /**
     * Map of the Media Item commands that responsible for the Media Items List creation.
     */
    private val mMediaItemCommands = HashMap<String, MediaItemCommand>()

    init {
        if (isCar) {
            mMediaItemCommands[MediaId.MEDIA_ID_ROOT] = MediaItemRootCar(source)
            mMediaItemCommands[MediaId.MEDIA_ID_BROWSE_CAR] = MediaItemBrowseCar(source)
        } else {
            mMediaItemCommands[MediaId.MEDIA_ID_ROOT] = MediaItemRoot(source)
        }
        mMediaItemCommands[MediaId.MEDIA_ID_ALL_CATEGORIES] = MediaItemAllCategories()
        mMediaItemCommands[MediaId.MEDIA_ID_COUNTRIES_LIST] = MediaItemCountriesList()
        mMediaItemCommands[MediaId.MEDIA_ID_COUNTRY_STATIONS] = MediaItemCountryStations()
        mMediaItemCommands[MediaId.MEDIA_ID_CHILD_CATEGORIES] = MediaItemChildCategories()
        mMediaItemCommands[MediaId.MEDIA_ID_FAVORITES_LIST] = MediaItemFavoritesList()
        mMediaItemCommands[MediaId.MEDIA_ID_RECENT_LIST] = MediaItemRecentList()
        mMediaItemCommands[MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST] = MediaItemLocalsList()
        mMediaItemCommands[MediaId.MEDIA_ID_SEARCH_FROM_APP] = MediaItemSearchFromApp()
        mMediaItemCommands[MediaId.MEDIA_ID_SEARCH_FROM_SERVICE] = MediaItemSearchFromService()
        mMediaItemCommands[MediaId.MEDIA_ID_POPULAR_STATIONS] = MediaItemPopularStations()
        mMediaItemCommands[MediaId.MEDIA_ID_NEW_STATIONS] = MediaItemNewStations()
    }

    override fun getMediaItemCommand(commandId: String): MediaItemCommand? {
        return mMediaItemCommands[commandId]
    }

    override fun getCastLayer(): CastLayer {
        return mCastLayer
    }

    override fun startNetworkMonitor(context: Context, listener: NetworkMonitorListener) {
        mNetworkLayer.startMonitor(context, listener)
    }

    override fun stopNetworkMonitor(context: Context) {
        mNetworkLayer.stopMonitor(context)
    }

    override fun isMobileNetwork(): Boolean {
        return mNetworkLayer.isMobileNetwork()
    }

    override fun getUseMobile(): Boolean {
        return mNetworkSettingsStorage.getUseMobile()
    }

    override fun getStationsInCategory(categoryId: String, pageNumber: Int): Set<RadioStation> {
        val primary = mModelLayer.getStations(
            mUrlLayer.getStationsInCategory(categoryId, pageNumber),
            MediaIdBuilderDefault()
        )
        if (pageNumber != 0) {
            return mergeStations(primary, emptyList())
        }
        return mergeStations(primary, mExtraCatalogs.map { (url, model) ->
            model.getStations(url.getStationsInCategory(categoryId, pageNumber), MediaIdBuilderDefault())
        })
    }

    override fun getStationsByCountry(countryCode: String, pageNumber: Int): Set<RadioStation> {
        val primary = mModelLayer.getStations(
            mUrlLayer.getStationsByCountry(countryCode, pageNumber),
            MediaIdBuilderDefault()
        )
        if (pageNumber != 0) {
            return mergeStations(primary, emptyList())
        }
        return mergeStations(primary, mExtraCatalogs.map { (url, model) ->
            model.getStations(url.getStationsByCountry(countryCode, pageNumber), MediaIdBuilderDefault())
        })
    }

    /**
     * Appends stations from [others] to [primary], skipping duplicates: the same id, the same stream
     * (ignoring the scheme and "?ref=..." style parameters), or the same name in the same country.
     * Catalogs list many stations twice (and the two catalogs share many), so lists are always passed here.
     */
    private fun mergeStations(primary: Set<RadioStation>, others: List<Set<RadioStation>>): Set<RadioStation> {
        val result = LinkedHashSet<RadioStation>()
        val ids = HashSet<String>()
        val urls = HashSet<String>()
        // Name -> countries it was seen in ("" when a catalog does not say).
        val names = HashMap<String, MutableSet<String>>()
        for (station in primary.asSequence() + others.asSequence().flatten()) {
            val url = try {
                normalizeUrl(station.getStreamUrlFixed())
            } catch (e: Exception) {
                AppUtils.EMPTY_STRING
            }
            val name = normalizeName(station.name)
            val country = station.countryCode.lowercase()
            if (ids.add(station.id).not()) {
                continue
            }
            if (url.isNotEmpty() && urls.contains(url)) {
                continue
            }
            val seen = if (name.isEmpty()) null else names[name]
            if (seen != null && (country.isEmpty() || seen.contains("") || seen.contains(country))) {
                continue
            }
            if (url.isNotEmpty()) {
                urls.add(url)
            }
            if (name.isNotEmpty()) {
                names.getOrPut(name) { HashSet() }.add(country)
            }
            result.add(station)
        }
        return result
    }

    override fun getNewStations(): Set<RadioStation> {
        return mergeStations(mModelLayer.getStations(mUrlLayer.getNewStations(), MediaIdBuilderDefault()), emptyList())
    }

    override fun getPopularStations(): Set<RadioStation> {
        return mergeStations(mModelLayer.getStations(mUrlLayer.getPopularStations(), MediaIdBuilderDefault()), emptyList())
    }

    override fun getSearchStations(query: String, mediaIdBuilder: MediaIdBuilder): Set<RadioStation> {
        // Merge results of all search requests (by name and by tag, in every catalog).
        val catalogs = listOf(kotlin.Pair(mUrlLayer, mModelLayer)) + mExtraCatalogs
        val sets = catalogs.flatMap { (url, model) ->
            url.getSearchUrls(query).map { model.getStations(it, mediaIdBuilder) }
        }
        val merged = mergeStations(sets.firstOrNull() ?: emptySet(), sets.drop(1))
        // Search within one country: catalogs that can't filter by country are filtered here.
        val country = com.yuriy.openradio.shared.model.net.SearchQuery.decode(query).countryCode ?: return merged
        val countryName = com.yuriy.openradio.shared.service.location.LocationService.COUNTRY_CODE_TO_NAME[country]
        return merged.filterTo(LinkedHashSet()) {
            it.countryCode.equals(country, ignoreCase = true) ||
                    (countryName != null && it.country.equals(countryName, ignoreCase = true))
        }
    }

    override fun getAllCategories(): Set<Category> {
        val primary = mModelLayer.getAllCategories(mUrlLayer.getAllCategoriesUrl())
        if (mExtraCatalogs.isEmpty()) {
            return primary
        }
        // Categories with the same name (case-insensitive) are shown once; opening one queries every catalog.
        val result = LinkedHashSet<Category>(primary)
        val names = primary.mapTo(HashSet()) { it.id.lowercase() }
        for ((url, model) in mExtraCatalogs) {
            for (category in model.getAllCategories(url.getAllCategoriesUrl())) {
                if (names.add(category.id.lowercase())) {
                    result.add(category)
                }
            }
        }
        return result
    }

    @Synchronized
    override fun getAllCountries(): Set<Country> {
        if (mCountriesCache.isEmpty().not()) {
            return mCountriesCache
        }
        mCountriesCache.addAll(mModelLayer.getAllCountries(mUrlLayer.getAllCountries()))
        for ((url, model) in mExtraCatalogs) {
            val known = mCountriesCache.mapTo(HashSet()) { it.code }
            mCountriesCache.addAll(model.getAllCountries(url.getAllCountries()).filter { known.contains(it.code).not() })
        }
        return mCountriesCache
    }

    override fun getAllFavorites(): Set<RadioStation> {
        return mFavoritesStorage.getAll()
    }

    override fun getAllDeviceLocal(): Set<RadioStation> {
        return mDeviceLocalsStorage.getAll()
    }

    override fun getRecentStations(): List<RadioStation> {
        return mRecentStorage.getAll().toList()
    }

    override fun pushRecent(radioStation: RadioStation): Boolean {
        return mRecentStorage.push(radioStation)
    }

    override fun getLastRadioStation(): RadioStation {
        return mLatestRadioStationStorage.get()
    }

    override fun setLastRadioStation(radioStation: RadioStation) {
        mLatestRadioStationStorage.add(radioStation)
    }

    override fun getCountryCode(): String {
        return mLocationStorage.getCountryCode()
    }

    override fun isRadioStationFavorite(radioStation: RadioStation): Boolean {
        return mFavoritesStorage.isFavorite(radioStation)
    }

    override fun updateRadioStationFavorite(radioStation: RadioStation) {
        updateRadioStationFavorite(radioStation, isRadioStationFavorite(radioStation).not())
    }

    override fun updateRadioStationFavorite(radioStation: RadioStation, isFavorite: Boolean) {
        if (isFavorite) {
            mFavoritesStorage.add(radioStation)
        } else {
            mFavoritesStorage.remove(radioStation)
        }
    }

    override fun updateSortIds(
        mediaId: String,
        sortId: Int,
        categoryMediaId: String
    ) {
        SortUtils.updateSortIds(
            mediaId, sortId, categoryMediaId,
            mFavoritesStorage, mDeviceLocalsStorage
        )
    }

    override fun setOrder(ids: List<String>, categoryMediaId: String) {
        SortUtils.setOrder(ids, categoryMediaId, mFavoritesStorage, mDeviceLocalsStorage)
    }

    override fun getEqualizerLayer(): EqualizerLayer {
        return mEqualizerLayer
    }

    override fun clear() {
        mApiCachePersistent.clear()
        mApiCacheInMemory.clear()
        mImagesPersistenceLayer.deleteAll()
        mLatestRadioStationStorage.clear()
    }

    override fun close() {
        mApiCacheInMemory.clear()
    }

    override fun getSleepTimerModel(): SleepTimerModel {
        return mSleepTimerModel
    }
}

/**
 * Stream address without the scheme, a trailing slash and the query ("?ref=radiobrowser" and the like).
 */
internal fun normalizeUrl(url: String): String {
    return url.trim().lowercase().substringBefore('?').substringBefore('#')
        .removePrefix("https://").removePrefix("http://").trimEnd('/', ';')
}

/**
 * Station name for comparing: letters and digits only ("- 0 N - Rock on Radio" = "0N Rock on Radio").
 */
internal fun normalizeName(name: String): String {
    return name.lowercase().filter { it.isLetterOrDigit() }
}
