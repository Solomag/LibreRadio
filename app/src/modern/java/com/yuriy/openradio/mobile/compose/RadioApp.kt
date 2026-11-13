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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
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
import com.yuriy.openradio.shared.utils.MediaItemHelper
import kotlinx.coroutines.launch

/**
 * Entries of the side menu; the activity maps them to the existing settings dialogs.
 */
enum class MenuAction { GENERAL, NETWORK, BUFFERING, SLEEP_TIMER, FILE_STORAGE, EQUALIZER, UPDATES, ABOUT, CLOSE }

@Composable
fun RadioApp(vm: RadioViewModel, onMenu: (MenuAction) -> Unit, onExit: () -> Unit) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var searching by rememberSaveable { mutableStateOf(false) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }

    // Back: close player / drawer / search, then go up one level; at the root close the app.
    BackHandler {
        when {
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
                onMenu(action)
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
                    placeholder = { Text(stringResource(R.string.compose_search_hint)) },
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
                vm.current.query?.let { "“$it”" } ?: vm.current.title,
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
    LazyColumn(state = state, contentPadding = PaddingValues(vertical = 8.dp), modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.mediaId }) { item ->
            if (item.mediaMetadata.isBrowsable == true) {
                FolderRow(item) { vm.open(item) }
            } else {
                StationRow(
                    item = item,
                    playing = item.mediaId == vm.currentMediaId,
                    favorite = vm.isFavorite(item),
                    onClick = { vm.open(item) },
                    onFavorite = { vm.toggleFavorite(item) }
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
    onFavorite: () -> Unit
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
            IconButton(onClick = onFavorite) {
                Icon(
                    if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                    tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        colors = colors,
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
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
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(scheme.primaryContainer, scheme.surface, scheme.surface)))
            .clickable(enabled = false) {}
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null) }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.compose_now_playing),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(48.dp))
            }
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
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
            Spacer(Modifier.weight(0.4f))
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
            Spacer(Modifier.height(40.dp))
        }
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
        DrawerItem(Icons.Rounded.Settings, stringResource(com.yuriy.openradio.shared.R.string.main_menu_general)) { onAction(MenuAction.GENERAL) }
        DrawerItem(Icons.Rounded.NetworkCheck, stringResource(com.yuriy.openradio.shared.R.string.main_menu_network)) { onAction(MenuAction.NETWORK) }
        DrawerItem(Icons.Rounded.Tune, stringResource(com.yuriy.openradio.shared.R.string.main_menu_buffering)) { onAction(MenuAction.BUFFERING) }
        DrawerItem(Icons.Rounded.Timer, stringResource(com.yuriy.openradio.shared.R.string.main_menu_sleep_timer)) { onAction(MenuAction.SLEEP_TIMER) }
        DrawerItem(Icons.Rounded.Equalizer, stringResource(R.string.eq_label)) { onAction(MenuAction.EQUALIZER) }
        DrawerItem(Icons.Rounded.SwapVert, stringResource(com.yuriy.openradio.shared.R.string.main_menu_file_storage)) { onAction(MenuAction.FILE_STORAGE) }
        DrawerItem(Icons.Rounded.SystemUpdate, stringResource(R.string.update_check_menu)) { onAction(MenuAction.UPDATES) }
        DrawerItem(Icons.Rounded.Info, stringResource(com.yuriy.openradio.shared.R.string.main_menu_about)) { onAction(MenuAction.ABOUT) }
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
    val uri = md.artworkUri
    var failed by remember(uri) { mutableStateOf(false) }
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (uri == null || failed) {
            Icon(
                Icons.Rounded.Radio,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(iconSize)
            )
        } else {
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
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
