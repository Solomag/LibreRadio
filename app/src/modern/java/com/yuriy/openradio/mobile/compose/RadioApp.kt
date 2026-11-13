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

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.yuriy.openradio.mobile.compose

import androidx.activity.compose.BackHandler
import java.util.Date
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.foundation.gestures.detectDragGestures
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import coil.compose.AsyncImage
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.shared.service.location.LocationService
import com.yuriy.openradio.shared.utils.MediaItemHelper
import kotlinx.coroutines.launch

/**
 * Entries of the side menu.
 */
enum class MenuAction { ADD_STATION, SETTINGS, UPDATES, ABOUT, CLOSE }

/**
 * Full-screen pages shown over the catalog.
 */
private enum class Page { NONE, ADD_STATION, SETTINGS, ABOUT }

@Composable
fun RadioApp(vm: RadioViewModel, host: SettingsHost, onMenu: (MenuAction) -> Unit, onExit: () -> Unit) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var searching by rememberSaveable { mutableStateOf(false) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf(Page.NONE) }

    // Back: close a page / player / drawer / search, then go up one level; at the root leave the screen.
    BackHandler {
        when {
            page != Page.NONE -> page = Page.NONE
            playerOpen -> playerOpen = false
            drawerState.isOpen -> scope.launch { drawerState.close() }
            searching -> searching = false
            vm.back().not() -> onExit()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent { action ->
                scope.launch { drawerState.close() }
                when (action) {
                    MenuAction.ADD_STATION -> page = Page.ADD_STATION
                    MenuAction.SETTINGS -> page = Page.SETTINGS
                    MenuAction.ABOUT -> page = Page.ABOUT
                    else -> onMenu(action)
                }
            }
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    RadioTopBar(
                        vm = vm,
                        searching = searching,
                        onSearchToggle = { searching = it },
                        onMenu = { scope.launch { drawerState.open() } }
                    )
                },
                bottomBar = {
                    if (vm.nowPlaying != null) {
                        MiniPlayer(vm) { playerOpen = true }
                    }
                }
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    Catalog(vm)
                    if (vm.loading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
            AnimatedVisibility(
                visible = playerOpen && vm.nowPlaying != null,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                FullPlayer(vm) { playerOpen = false }
            }
            AnimatedVisibility(
                visible = page != Page.NONE,
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it }
            ) {
                // Keep the last page while the exit animation runs.
                var shown by remember { mutableStateOf(page) }
                if (page != Page.NONE) {
                    shown = page
                }
                when (shown) {
                    Page.ABOUT -> AboutScreen { page = Page.NONE }
                    Page.ADD_STATION -> AddStationScreen(
                        onDone = {
                            page = Page.NONE
                            vm.stationsChanged()
                        },
                        onBack = { page = Page.NONE }
                    )
                    else -> SettingsScreen(vm, host) { page = Page.NONE }
                }
            }
        }
    }
}

@Composable
private fun RadioTopBar(
    vm: RadioViewModel,
    searching: Boolean,
    onSearchToggle: (Boolean) -> Unit,
    onMenu: () -> Unit
) {
    if (searching) {
        var text by rememberSaveable { mutableStateOf(vm.current.query ?: "") }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = { onSearchToggle(false) }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                }
            },
            title = {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = {
                        val country = vm.searchCountry
                        Text(
                            if (country == null) {
                                stringResource(R.string.compose_search_hint)
                            } else {
                                stringResource(R.string.compose_search_in, LocationService.displayCountryName(country))
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        vm.search(text)
                        onSearchToggle(false)
                    }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        )
        return
    }
    TopAppBar(
        title = {
            Text(
                vm.current.query?.let { q ->
                    val country = vm.current.country
                    if (country == null) "“$q”" else "“$q” · ${LocationService.displayCountryName(country)}"
                } ?: vm.current.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold
            )
        },
        navigationIcon = {
            if (vm.isRoot) {
                IconButton(onClick = onMenu) { Icon(Icons.Rounded.Menu, contentDescription = null) }
            } else {
                IconButton(onClick = { vm.back() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                }
            }
        },
        actions = {
            if (vm.canReorder && vm.items.size > 1) {
                IconButton(onClick = { vm.reordering = vm.reordering.not() }) {
                    Icon(
                        if (vm.reordering) Icons.Rounded.Check else Icons.Rounded.SwapVert,
                        contentDescription = stringResource(R.string.compose_reorder)
                    )
                }
            }
            IconButton(onClick = { onSearchToggle(true) }) {
                Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.compose_search_hint))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@Composable
private fun Catalog(vm: RadioViewModel) {
    val items = vm.items
    if (items.isEmpty()) {
        if (!vm.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.compose_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }
    // Folders (categories, countries, the root) as a grid of cards; stations as a list.
    if (items.all { it.mediaMetadata.isBrowsable == true }) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items, key = { it.mediaId }) { item -> FolderCard(item) { vm.open(item) } }
        }
        return
    }
    val state = rememberLazyListState()
    val atEnd by remember {
        derivedStateOf {
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.layoutInfo.totalItemsCount - 5
        }
    }
    LaunchedEffect(atEnd) {
        if (atEnd) {
            vm.loadMore()
        }
    }
    var toDelete by remember { mutableStateOf<MediaItem?>(null) }
    toDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            text = {
                Text(
                    stringResource(
                        com.yuriy.openradio.shared.R.string.remove_station_dialog_main_text,
                        item.mediaMetadata.title?.toString() ?: ""
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteStation(item)
                    toDelete = null
                }) { Text(stringResource(com.yuriy.openradio.shared.R.string.remove_station_dialog_button_label)) }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.settings_cancel)) }
            }
        )
    }
    // Drag state of the reorder mode: the dragged item and its offset from its slot.
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    LazyColumn(state = state, contentPadding = PaddingValues(vertical = 8.dp), modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.mediaId }) { item ->
            if (item.mediaMetadata.isBrowsable == true) {
                FolderRow(item) { vm.open(item) }
            } else {
                val dragging = draggedId == item.mediaId
                StationRow(
                    item = item,
                    playing = item.mediaId == vm.currentMediaId,
                    favorite = vm.isFavorite(item),
                    onClick = { vm.open(item) },
                    onFavorite = { vm.toggleFavorite(item) },
                    dragHandle = if (vm.reordering) {
                        Modifier.pointerInput(item.mediaId) {
                            detectDragGestures(
                                onDragStart = {
                                    draggedId = item.mediaId
                                    dragOffset = 0f
                                },
                                onDragEnd = {
                                    draggedId = null
                                    dragOffset = 0f
                                    vm.saveOrder()
                                },
                                onDragCancel = {
                                    draggedId = null
                                    dragOffset = 0f
                                    vm.saveOrder()
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    // Swap with the neighbour once the row passes half of its height.
                                    val info = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == item.mediaId }
                                    val size = (info?.size ?: 200).toFloat()
                                    if (dragOffset > size / 2 && vm.moveLocal(item.mediaId, 1)) {
                                        dragOffset -= size
                                    } else if (dragOffset < -size / 2 && vm.moveLocal(item.mediaId, -1)) {
                                        dragOffset += size
                                    }
                                }
                            )
                        }
                    } else {
                        null
                    },
                    onLongClick = if (vm.canDelete) ({ toDelete = item }) else null,
                    modifier = if (dragging) {
                        Modifier
                            .zIndex(1f)
                            .graphicsLayer { translationY = dragOffset }
                    } else {
                        Modifier.animateItem()
                    }
                )
            }
        }
    }
}

@Composable
private fun FolderCard(item: MediaItem, onClick: () -> Unit) {
    val md = item.mediaMetadata
    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            ItemIcon(md, Modifier.size(36.dp))
            Column {
                Text(
                    md.title?.toString() ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                md.subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderRow(item: MediaItem, onClick: () -> Unit) {
    val md = item.mediaMetadata
    ListItem(
        headlineContent = { Text(md.title?.toString() ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = md.subtitle?.takeIf { it.isNotBlank() }?.let { { Text(it.toString(), maxLines = 1) } },
        leadingContent = { ItemIcon(md, Modifier.size(40.dp)) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun StationRow(
    item: MediaItem,
    playing: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    dragHandle: Modifier? = null,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val md = item.mediaMetadata
    val colors = if (playing) {
        ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    } else {
        ListItemDefaults.colors()
    }
    ListItem(
        headlineContent = {
            Text(
                md.title?.toString()?.trim() ?: "",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal
            )
        },
        supportingContent = md.subtitle?.takeIf { it.isNotBlank() }?.let {
            { Text(it.toString(), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        leadingContent = {
            Box {
                Artwork(md, Modifier.size(56.dp), RoundedCornerShape(14.dp))
                if (playing) {
                    Icon(
                        Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(2.dp)
                    )
                }
            }
        },
        trailingContent = {
            if (dragHandle != null) {
                Icon(
                    Icons.Rounded.DragHandle,
                    contentDescription = stringResource(R.string.compose_drag),
                    modifier = dragHandle
                        .size(48.dp)
                        .padding(12.dp)
                )
                return@ListItem
            }
            IconButton(onClick = onFavorite) {
                Icon(
                    if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                    tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        colors = colors,
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    )
}

@Composable
private fun MiniPlayer(vm: RadioViewModel, onExpand: () -> Unit) {
    val md = vm.nowPlaying ?: return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 3.dp,
        modifier = Modifier.clickable(onClick = onExpand)
    ) {
        Column(Modifier.navigationBarsPadding()) {
            if (vm.isBuffering) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Artwork(md, Modifier.size(52.dp), RoundedCornerShape(14.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        md.title?.toString()?.trim() ?: "",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    trackText(md)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    }
                }
                IconButton(onClick = { vm.toggleNowPlayingFavorite() }) {
                    Icon(
                        if (vm.isNowPlayingFavorite()) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = null
                    )
                }
                FilledIconButton(onClick = { vm.togglePlay() }, modifier = Modifier.size(48.dp)) {
                    Icon(if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun FullPlayer(vm: RadioViewModel, onClose: () -> Unit) {
    val md = vm.nowPlaying ?: return
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.surface, contentColor = scheme.onSurface, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(scheme.primaryContainer, scheme.surface, scheme.surface)))
                .clickable(enabled = false) {}
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            val landscape = maxWidth > maxHeight
            Column(Modifier.fillMaxSize()) {
                PlayerHeader(vm, onClose)
                if (landscape) {
                    // Artwork on the left, title and controls on the right.
                    Row(
                        Modifier
                            .fillMaxSize()
                            .padding(start = 28.dp, end = 28.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Artwork(
                            md,
                            Modifier
                                .fillMaxHeight()
                                .aspectRatio(1f),
                            RoundedCornerShape(28.dp),
                            iconSize = 96.dp
                        )
                        Spacer(Modifier.width(32.dp))
                        Column(Modifier.weight(1f)) {
                            PlayerTexts(vm, md)
                            Spacer(Modifier.height(24.dp))
                            PlayerControls(vm)
                        }
                    }
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.weight(0.6f))
                        Artwork(
                            md,
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            RoundedCornerShape(32.dp),
                            iconSize = 120.dp
                        )
                        Spacer(Modifier.height(32.dp))
                        PlayerTexts(vm, md)
                        Spacer(Modifier.weight(0.4f))
                        PlayerControls(vm)
                        Spacer(Modifier.height(40.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerHeader(vm: RadioViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val timerEnd by rememberSleepTimerEnd()
    var timerOpen by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null) }
        Spacer(Modifier.size(48.dp))
        Spacer(Modifier.weight(1f))
        val end = timerEnd
        Text(
            if (end == null) {
                stringResource(R.string.compose_now_playing)
            } else {
                stringResource(R.string.player_timer_active, DateFormat.getTimeFormat(context).format(Date(end)))
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (end == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { timerOpen = true }) {
            Icon(
                Icons.Rounded.Bedtime,
                contentDescription = stringResource(R.string.settings_section_timer),
                tint = if (end == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
            )
        }
        IconButton(onClick = { historyOpen = true }) {
            Icon(Icons.Rounded.History, contentDescription = stringResource(R.string.history_title))
        }
    }
    if (timerOpen) {
        SleepTimerDialog { timerOpen = false }
    }
    if (historyOpen) {
        TrackHistoryDialog(vm) { historyOpen = false }
    }
}

@Composable
private fun PlayerTexts(vm: RadioViewModel, md: MediaMetadata) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            md.title?.toString()?.trim() ?: "",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))
        Text(
            trackText(md) ?: " ",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.basicMarquee()
        )
        val track = vm.trackOf(md)
        var menu by remember { mutableStateOf(false) }
        Box(Modifier.height(48.dp)) {
            if (track != null) {
                TextButton(onClick = { menu = true }, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.song_find))
                }
                FindSongMenu(track, menu) { menu = false }
            }
        }
    }
}

@Composable
private fun PlayerControls(vm: RadioViewModel) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth()
    ) {
        IconButton(onClick = { vm.toggleNowPlayingFavorite() }) {
            Icon(
                if (vm.isNowPlayingFavorite()) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = null,
                tint = if (vm.isNowPlayingFavorite()) scheme.primary else scheme.onSurface
            )
        }
        IconButton(onClick = { vm.previous() }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = null, modifier = Modifier.size(36.dp))
        }
        FilledIconButton(
            onClick = { vm.togglePlay() },
            modifier = Modifier.size(84.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = scheme.primary)
        ) {
            if (vm.isBuffering) {
                CircularProgressIndicator(color = scheme.onPrimary, modifier = Modifier.size(36.dp))
            } else {
                Icon(
                    if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
        IconButton(onClick = { vm.next() }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(36.dp))
        }
        IconButton(onClick = { vm.stopService() }) {
            Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null)
        }
    }
}

@Composable
private fun DrawerContent(onAction: (MenuAction) -> Unit) {
    ModalDrawerSheet {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 24.dp)
        ) {
            Icon(
                Icons.Rounded.Radio,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(com.yuriy.openradio.R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
        HorizontalDivider(Modifier.padding(horizontal = 28.dp))
        Spacer(Modifier.height(8.dp))
        DrawerItem(Icons.Rounded.Add, stringResource(com.yuriy.openradio.shared.R.string.add_station_dialog_title)) {
            onAction(MenuAction.ADD_STATION)
        }
        DrawerItem(Icons.Rounded.Settings, stringResource(R.string.settings_title)) { onAction(MenuAction.SETTINGS) }
        DrawerItem(Icons.Rounded.SystemUpdate, stringResource(R.string.update_check_menu)) { onAction(MenuAction.UPDATES) }
        DrawerItem(Icons.Rounded.Info, stringResource(R.string.about_title)) { onAction(MenuAction.ABOUT) }
        Spacer(Modifier.weight(1f))
        DrawerItem(Icons.Rounded.Close, stringResource(R.string.compose_close_app)) { onAction(MenuAction.CLOSE) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp)
    )
}

/**
 * Category / country icon from the item extras, or a generic radio icon.
 */
@Composable
private fun ItemIcon(md: MediaMetadata, modifier: Modifier) {
    val id = MediaItemHelper.getDrawableId(md.extras)
    if (id != 0) {
        Image(painterResource(id), contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
    } else {
        Icon(Icons.Rounded.Radio, contentDescription = null, modifier = modifier, tint = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Station logo (artwork URI) with a radio icon on a tinted tile as fallback.
 */
@Composable
private fun Artwork(
    md: MediaMetadata,
    modifier: Modifier,
    shape: RoundedCornerShape,
    iconSize: androidx.compose.ui.unit.Dp = 28.dp
) {
    // The catalog logo, then the station site's own icons (no third-party icon service).
    val candidates = remember(md.artworkUri, md.extras) { artworkCandidates(md) }
    var index by remember(candidates) { mutableIntStateOf(0) }
    val uri = candidates.getOrNull(index)
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (uri == null) {
            Icon(
                Icons.Rounded.Radio,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(iconSize)
            )
        } else {
            // A site icon is small: show it centred instead of stretched over the artwork.
            val siteIcon = index > 0 || (md.artworkUri == null && candidates.isNotEmpty())
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = if (siteIcon) ContentScale.Fit else ContentScale.Crop,
                onError = { index++ },
                modifier = if (siteIcon) Modifier.fillMaxSize(0.5f) else Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Logo sources of a station: the catalog image, then the touch icon and favicon of its web site.
 */
private fun artworkCandidates(md: MediaMetadata): List<Any> {
    val list = ArrayList<Any>()
    md.artworkUri?.let { list.add(it) }
    val site = MediaItemHelper.getHomePage(md.extras)
    val host = runCatching { android.net.Uri.parse(site).host }.getOrNull()
    if (!host.isNullOrEmpty() && (site.startsWith("http://") || site.startsWith("https://"))) {
        list.add("https://$host/apple-touch-icon.png")
        list.add("https://$host/favicon.ico")
    }
    return list
}

/**
 * Current track ("Artist - Title") from the stream, if any.
 */
private fun trackText(md: MediaMetadata): String? {
    val text = (md.subtitle ?: md.artist)?.toString()?.trim()
    if (text.isNullOrEmpty() || text == md.title?.toString()?.trim()) {
        return null
    }
    return text
}
