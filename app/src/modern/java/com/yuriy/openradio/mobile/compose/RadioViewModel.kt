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
        reordering = false
        page = 0
        endReached = folder.query != null || folder.id == MediaId.MEDIA_ID_ROOT ||
                folder.id == MediaId.MEDIA_ID_FAVORITES_LIST
        viewModelScope.launch {
            val result = try {
                if (folder.query != null) {
                    val query = SearchQuery(folder.query, folder.country).encode()
                    b.getSearchResult(query, 0, DependencyRegistryCommon.UI_PAGE_SIZE, null).await().value
                } else {
                    b.getChildren(folder.id, 0, DependencyRegistryCommon.UI_PAGE_SIZE, null).await().value
                }
            } catch (e: Exception) {
                AppLogger.e("RVM load ${folder.id}", e)
                null
            }
            // Ignore late results of a folder the user already left.
            if (current == folder) {
                items = result?.toList() ?: emptyList()
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
        nowPlaying = if (player.currentMediaItem != null) player.mediaMetadata else null
        currentMediaId = player.currentMediaItem?.mediaId
        isPlaying = player.isPlaying
        isBuffering = player.playbackState == Player.STATE_BUFFERING && player.playWhenReady
    }

    override fun onCleared() {
        browser?.let {
            it.removeListener(playerListener)
            it.release()
        }
        browser = null
        super.onCleared()
    }
}
