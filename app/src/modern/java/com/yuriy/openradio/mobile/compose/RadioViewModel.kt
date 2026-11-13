/*
 * Copyright 2026 Libre Radio contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yuriy.openradio.mobile.compose

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.yuriy.openradio.R
import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommon
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.net.SearchQuery
import com.yuriy.openradio.shared.model.storage.AppPreferencesManager
import com.yuriy.openradio.shared.model.storage.LocationStorage
import com.yuriy.openradio.shared.service.OpenRadioService
import com.yuriy.openradio.shared.service.OpenRadioStore
import com.yuriy.openradio.shared.utils.AppLogger
import com.yuriy.openradio.shared.utils.MediaItemHelper
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * State holder of the Compose UI. Talks to [OpenRadioService] through a Media3 [MediaBrowser]:
 * browsing the catalog, search, playback and favorites.
 */
class RadioViewModel(app: Application) : AndroidViewModel(app) {

    /**
     * A level of the navigation stack: a catalog folder or a search.
     */
    data class Folder(val id: String, val title: String, val query: String? = null, val country: String? = null)

    val folders = mutableStateListOf<Folder>()
    var items by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set

    /**
     * When the current load started ([android.os.SystemClock.elapsedRealtime]), to show how long it takes.
     */
    var loadingSince by mutableStateOf(0L)
        private set

    /**
     * The catalog did not answer for the current folder (shown with a Retry button instead of "empty").
     */
    var loadFailed by mutableStateOf(false)
        private set

    /**
     * Genres sorted by name instead of by number of stations.
     */
    var genresByName by mutableStateOf(false)
        private set

    /**
     * The current station has played since it was started: later buffering is a reload, not connecting.
     */
    var hasPlayedCurrent by mutableStateOf(false)
        private set

    /**
     * Media volume of the phone, 0..1.
     */
    var volume by mutableStateOf(0f)
        private set
    var connected by mutableStateOf(false)
        private set

    var nowPlaying by mutableStateOf<MediaMetadata?>(null)
        private set
    var currentMediaId by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(false)
        private set

    /**
     * Favorites / own stations are shown with up and down buttons to change their order.
     */
    var reordering by mutableStateOf(false)

    /**
     * A track heard on the radio.
     */
    data class TrackEntry(val time: Long, val station: String, val track: String)

    private var stationSubtitle: String? = null

    /**
     * Tracks heard, newest first (kept between launches).
     */
    val history = mutableStateListOf<TrackEntry>()

    private val prefs = app.getSharedPreferences("compose_track_history", android.content.Context.MODE_PRIVATE)

    /**
     * Texts the service puts in place of a track: "Live stream", "Buffering…", errors.
     */
    private val errorTexts: Set<String> = setOf(
        app.getString(com.yuriy.openradio.R.string.media_stream_error),
        app.getString(com.yuriy.openradio.R.string.media_stream_network_failed),
        app.getString(com.yuriy.openradio.R.string.media_stream_http_403),
        app.getString(com.yuriy.openradio.R.string.media_stream_http_404),
        app.getString(com.yuriy.openradio.R.string.media_stream_no_internet),
        app.getString(com.yuriy.openradio.R.string.media_stream_no_answer),
        app.getString(com.yuriy.openradio.R.string.media_stream_dns),
        app.getString(com.yuriy.openradio.R.string.media_stream_tls),
        app.getString(com.yuriy.openradio.R.string.media_stream_cert_expired),
        app.getString(com.yuriy.openradio.R.string.media_stream_cert_untrusted),
        app.getString(com.yuriy.openradio.R.string.media_stream_connection_lost),
        app.getString(com.yuriy.openradio.R.string.media_stream_format),
    )

    private val notTracks: Set<String> = setOf(
        app.getString(com.yuriy.openradio.R.string.media_description_default),
        app.getString(com.yuriy.openradio.R.string.buffering_infinite),
    ) + errorTexts

    /**
     * "Station server error (HTTP %d)" up to the number, to recognise it whatever the code.
     */
    private val httpErrorPrefix =
        app.getString(com.yuriy.openradio.R.string.media_stream_http_code, 0).substringBefore("0")

    private val uiPrefs = app.getSharedPreferences("compose_ui", android.content.Context.MODE_PRIVATE)

    private val audioManager =
        app.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager

    /**
     * Follows volume changes made with the phone's buttons.
     */
    private val volumeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            readVolume()
        }
    }

    /**
     * An error the player shows in place of a track (why the station does not play).
     */
    fun isErrorText(text: String?): Boolean {
        text ?: return false
        return text in errorTexts || (httpErrorPrefix.isNotEmpty() && text.startsWith(httpErrorPrefix))
    }

    /**
     * Favorite state changed in this session, by media id (the catalog is not re-fetched on every toggle).
     */
    private val favoriteOverrides = mutableStateMapOf<String, Boolean>()

    private var browser: MediaBrowser? = null
    private var page = 0
    private var endReached = false

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncPlayer(player)
        }
    }

    init {
        loadHistory()
        genresByName = uiPrefs.getBoolean(KEY_GENRES_BY_NAME, false)
        readVolume()
        androidx.core.content.ContextCompat.registerReceiver(
            app, volumeReceiver, android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )
        val title = app.getString(R.string.app_name)
        folders.add(Folder(MediaId.MEDIA_ID_ROOT, title))
        viewModelScope.launch {
            try {
                val token = SessionToken(app, ComponentName(app, OpenRadioService::class.java))
                val b = MediaBrowser.Builder(app, token).buildAsync().await()
                browser = b
                b.addListener(playerListener)
                syncPlayer(b)
                b.getLibraryRoot(null).await()
                connected = true
                resetPlayerVolume()
                load()
            } catch (e: Exception) {
                AppLogger.e("RVM connect", e)
                loading = false
            }
        }
    }

    val current: Folder get() = folders.last()

    val isRoot: Boolean get() = folders.size == 1

    fun isFavorite(item: MediaItem): Boolean {
        return favoriteOverrides[item.mediaId] ?: MediaItemHelper.isFavoriteField(item.mediaMetadata)
    }

    fun isNowPlayingFavorite(): Boolean {
        val id = currentMediaId ?: return false
        return favoriteOverrides[id] ?: MediaItemHelper.isFavoriteField(nowPlaying)
    }

    fun open(item: MediaItem) {
        val md = item.mediaMetadata
        when {
            md.isBrowsable == true -> {
                folders.add(Folder(item.mediaId, md.title?.toString() ?: ""))
                load()
            }

            md.isPlayable == true -> play(item)
        }
    }

    /**
     * Country whose stations a search started here is limited to, or null for a search everywhere.
     */
    val searchCountry: String?
        get() {
            val folder = current
            if (folder.query != null) {
                return folder.country
            }
            val id = folder.id
            return when {
                id == MediaId.MEDIA_ID_COUNTRY_STATIONS ->
                    LocationStorage(WeakReference(getApplication<Application>())).getCountryCode()
                id.startsWith(MediaId.MEDIA_ID_COUNTRIES_LIST) && id != MediaId.MEDIA_ID_COUNTRIES_LIST ->
                    id.removePrefix(MediaId.MEDIA_ID_COUNTRIES_LIST)
                else -> null
            }
        }

    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) {
            return
        }
        val country = searchCountry
        if (current.query != null) {
            folders.removeAt(folders.lastIndex)
        }
        folders.add(Folder(MediaId.MEDIA_ID_SEARCH_FROM_APP, q, q, country))
        load()
    }

    /**
     * Own stations can be deleted (long press).
     */
    val canDelete: Boolean
        get() = current.id == MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST

    fun deleteStation(item: MediaItem) {
        com.yuriy.openradio.shared.dependencies.DependencyRegistryCommonUi.removeStationPresenter()
            .removeRadioStation(item.mediaId)
        // A deleted own station should not stay behind in Favorites.
        if (isFavorite(item)) {
            setFavorite(item.mediaId, false)
        }
        items = items.filter { it.mediaId != item.mediaId }
        stationsChanged()
    }

    /**
     * Own stations or Favorites changed outside the catalog: drop the service's cached folders,
     * then reload the root if it is shown.
     */
    fun stationsChanged() {
        val b = browser ?: return
        val stale = listOf(
            MediaId.MEDIA_ID_ROOT,
            MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST,
            MediaId.MEDIA_ID_FAVORITES_LIST
        )
        viewModelScope.launch {
            runCatching {
                b.sendCustomCommand(
                    SessionCommand(OpenRadioService.CMD_UPDATE_TREE, Bundle()),
                    OpenRadioStore.makeStaleIdsBundle(stale)
                ).await()
            }
            refreshRoot()
        }
    }

    val canReorder: Boolean
        get() = current.id == MediaId.MEDIA_ID_FAVORITES_LIST ||
                current.id == MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST

    /**
     * Moves a station one place while it is dragged; [saveOrder] stores the order afterwards.
     *
     * @return False at the ends of the list.
     */
    fun moveLocal(mediaId: String, delta: Int): Boolean {
        val list = items.toMutableList()
        val from = list.indexOfFirst { it.mediaId == mediaId }
        val to = from + delta
        if (from < 0 || to !in list.indices) {
            return false
        }
        java.util.Collections.swap(list, from, to)
        items = list
        return true
    }

    fun saveOrder() {
        if (canReorder.not()) {
            return
        }
        sendCommand(
            OpenRadioService.CMD_SET_ORDER,
            OpenRadioStore.makeSetOrderBundle(items.map { it.mediaId }, current.id)
        )
    }

    /**
     * Moves a station of Favorites / own stations up (-1) or down (+1) and stores the new order.
     */
    fun move(item: MediaItem, delta: Int) {
        val list = items.toMutableList()
        val from = list.indexOfFirst { it.mediaId == item.mediaId }
        val to = from + delta
        if (from < 0 || to !in list.indices) {
            return
        }
        java.util.Collections.swap(list, from, to)
        items = list
        sendCommand(
            OpenRadioService.CMD_SET_ORDER,
            OpenRadioStore.makeSetOrderBundle(list.map { it.mediaId }, current.id)
        )
    }

    /**
     * @return false when already at the root (the caller then closes the app).
     */
    fun back(): Boolean {
        if (isRoot) {
            return false
        }
        folders.removeAt(folders.lastIndex)
        load()
        return true
    }

    fun goHome() {
        while (folders.size > 1) {
            folders.removeAt(folders.lastIndex)
        }
        load()
    }

    fun refresh() {
        load()
    }

    /**
     * Reloads the root (local country, favorites) if it is the current folder.
     */
    fun refreshRoot() {
        if (isRoot) {
            load()
        }
    }

    private fun load() {
        val b = browser ?: return
        val folder = current
        loading = true
        loadFailed = false
        loadingSince = android.os.SystemClock.elapsedRealtime()
        reordering = false
        page = 0
        endReached = folder.query != null || folder.id == MediaId.MEDIA_ID_ROOT ||
                folder.id == MediaId.MEDIA_ID_FAVORITES_LIST || folder.id == MediaId.MEDIA_ID_ALL_CATEGORIES
        viewModelScope.launch {
            val result = try {
                if (folder.query != null) {
                    val query = SearchQuery(folder.query, folder.country).encode()
                    b.getSearchResult(query, 0, DependencyRegistryCommon.UI_PAGE_SIZE, null).await()
                } else {
                    b.getChildren(folder.id, 0, DependencyRegistryCommon.UI_PAGE_SIZE, null).await()
                }
            } catch (e: Exception) {
                AppLogger.e("RVM load ${folder.id}", e)
                null
            }
            // Ignore late results of a folder the user already left.
            if (current == folder) {
                val list = result?.value?.toList() ?: emptyList()
                genresInCountOrder = if (folder.id == MediaId.MEDIA_ID_ALL_CATEGORIES) list else emptyList()
                items = if (genresInCountOrder.isNotEmpty() && genresByName) sortByName(list) else list
                loadFailed = list.isEmpty() &&
                        (result == null || result.resultCode != androidx.media3.session.LibraryResult.RESULT_SUCCESS)
                loading = false
            }
        }
    }

    /**
     * Loads the next page of a long catalog folder when the list is scrolled to its end.
     */
    fun loadMore() {
        val b = browser ?: return
        if (loading || endReached || items.size < DependencyRegistryCommon.PAGE_SIZE) {
            return
        }
        val folder = current
        loading = true
        viewModelScope.launch {
            val next = try {
                b.getChildren(folder.id, page + 1, DependencyRegistryCommon.UI_PAGE_SIZE, null).await().value
            } catch (e: Exception) {
                AppLogger.e("RVM more ${folder.id}", e)
                null
            }
            if (current == folder) {
                val known = items.mapTo(HashSet()) { it.mediaId }
                val fresh = next?.filter { known.contains(it.mediaId).not() } ?: emptyList()
                if (fresh.isEmpty()) {
                    endReached = true
                } else {
                    page++
                    items = items + fresh
                }
                loading = false
            }
        }
    }

    /**
     * Genres as the catalog gave them (most stations first), to switch back from sorting by name.
     */
    private var genresInCountOrder: List<MediaItem> = emptyList()

    val isGenres: Boolean
        get() = current.id == MediaId.MEDIA_ID_ALL_CATEGORIES && current.query == null

    fun setGenresSortByName(byName: Boolean) {
        genresByName = byName
        uiPrefs.edit().putBoolean(KEY_GENRES_BY_NAME, byName).apply()
        if (isGenres && genresInCountOrder.isNotEmpty()) {
            items = if (byName) sortByName(genresInCountOrder) else genresInCountOrder
        }
    }

    private fun sortByName(list: List<MediaItem>): List<MediaItem> {
        val collator = java.text.Collator.getInstance()
        return list.sortedWith { a, b ->
            collator.compare(a.mediaMetadata.title?.toString() ?: "", b.mediaMetadata.title?.toString() ?: "")
        }
    }

    private fun readVolume() {
        val max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        volume = if (max <= 0) {
            0f
        } else {
            audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() / max
        }
    }

    /**
     * Sets the phone's media volume (the same one as its volume buttons).
     */
    fun changeVolume(value: Float) {
        val v = value.coerceIn(0f, 1f)
        val max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        runCatching {
            audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, Math.round(v * max), 0)
        }
        volume = v
    }

    /**
     * The app's own player volume used to be a setting; it is gone, so a lowered one is put back to full.
     */
    private fun resetPlayerVolume() {
        val app = getApplication<Application>()
        val full = OpenRadioService.MASTER_VOLUME_DEFAULT
        if (AppPreferencesManager.getMasterVolume(app, full) >= full) {
            return
        }
        AppPreferencesManager.setMasterVolume(app, full)
        sendCommand(OpenRadioService.CMD_MASTER_VOLUME_CHANGED, OpenRadioStore.makeMasterVolumeChangedBundle(full))
    }

    fun play(item: MediaItem) {
        val b = browser ?: return
        if (item.mediaId == currentMediaId && b.playbackState != Player.STATE_IDLE) {
            togglePlay()
            return
        }
        val playlist = items.filter { it.mediaMetadata.isPlayable == true }.ifEmpty { listOf(item) }
        val index = playlist.indexOfFirst { it.mediaId == item.mediaId }.coerceAtLeast(0)
        b.setMediaItems(playlist, index, C.TIME_UNSET)
        b.prepare()
        b.play()
    }

    fun togglePlay() {
        val b = browser ?: return
        if (b.isPlaying) {
            b.pause()
        } else {
            if (b.playbackState == Player.STATE_IDLE) {
                b.prepare()
            }
            b.play()
        }
    }

    fun next() {
        browser?.seekToNextMediaItem()
    }

    fun previous() {
        browser?.seekToPreviousMediaItem()
    }

    fun toggleFavorite(item: MediaItem) {
        setFavorite(item.mediaId, isFavorite(item).not())
    }

    fun toggleNowPlayingFavorite() {
        val id = currentMediaId ?: return
        setFavorite(id, isNowPlayingFavorite().not())
    }

    private fun setFavorite(mediaId: String, favorite: Boolean) {
        val b = browser ?: return
        favoriteOverrides[mediaId] = favorite
        val args = OpenRadioStore.makeUpdateIsFavoriteBundle(mediaId)
        // The service names the command after the current state: "FAVORITE_OFF" turns it on.
        val command = if (favorite) OpenRadioService.CMD_FAVORITE_OFF else OpenRadioService.CMD_FAVORITE_ON
        viewModelScope.launch {
            try {
                b.sendCustomCommand(SessionCommand(command, Bundle()), args).await()
                if (current.id == MediaId.MEDIA_ID_FAVORITES_LIST || isRoot) {
                    load()
                }
            } catch (e: Exception) {
                AppLogger.e("RVM favorite", e)
            }
        }
    }

    /**
     * Stops playback and the service (the app's "close").
     */
    fun stopService() {
        sendCommand(OpenRadioService.CMD_STOP_SERVICE)
    }

    /**
     * Sends a custom command of [OpenRadioService] (settings changes, cache, stop).
     */
    fun sendCommand(command: String, args: Bundle = Bundle()) {
        val b = browser ?: return
        viewModelScope.launch {
            runCatching {
                b.sendCustomCommand(SessionCommand(command, Bundle()), args).await()
            }.onFailure { AppLogger.e("RVM command $command", it) }
        }
    }

    private fun syncPlayer(player: Player) {
        val previousId = currentMediaId
        // The station's own subtitle (its country) is shown until the stream names a track; the player
        // later writes the track into the item's subtitle, so remember it only when the station changes.
        if (player.currentMediaItem?.mediaId != previousId) {
            stationSubtitle = player.currentMediaItem?.mediaMetadata?.subtitle?.toString()?.trim()
            hasPlayedCurrent = false
        }
        if (player.isPlaying) {
            hasPlayedCurrent = true
        }
        nowPlaying = if (player.currentMediaItem != null) player.mediaMetadata else null
        currentMediaId = player.currentMediaItem?.mediaId
        nowPlaying?.let { recordTrack(it) }
        if (previousId == null && currentMediaId != null) {
            // The first station played may add "Recent" to the root.
            refreshRoot()
        }
        isPlaying = player.isPlaying
        isBuffering = player.playbackState == Player.STATE_BUFFERING && player.playWhenReady
    }

    /**
     * The current track ("Artist - Title"), or null for station texts like "Live stream".
     */
    fun trackOf(md: MediaMetadata?): String? {
        md ?: return null
        val text = (md.subtitle ?: md.artist)?.toString()?.trim()
        if (text.isNullOrEmpty() || text == md.title?.toString()?.trim() || text in notTracks || isErrorText(text) ||
            text == stationSubtitle
        ) {
            return null
        }
        return text
    }

    private fun recordTrack(md: MediaMetadata) {
        val track = trackOf(md) ?: return
        if (history.firstOrNull()?.track == track) {
            return
        }
        history.add(0, TrackEntry(System.currentTimeMillis(), md.title?.toString()?.trim() ?: "", track))
        while (history.size > MAX_HISTORY) {
            history.removeAt(history.lastIndex)
        }
        saveHistory()
    }

    fun clearHistory() {
        history.clear()
        saveHistory()
    }

    private fun saveHistory() {
        val array = org.json.JSONArray()
        history.forEach {
            array.put(org.json.JSONObject().put("t", it.time).put("s", it.station).put("k", it.track))
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    private fun loadHistory() {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return
        runCatching {
            val array = org.json.JSONArray(raw)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                history.add(TrackEntry(o.getLong("t"), o.getString("s"), o.getString("k")))
            }
        }
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(volumeReceiver) }
        browser?.let {
            it.removeListener(playerListener)
            it.release()
        }
        browser = null
        super.onCleared()
    }

    companion object {
        private const val MAX_HISTORY = 100
        private const val KEY_HISTORY = "history"
        private const val KEY_GENRES_BY_NAME = "genres_by_name"
    }
}
