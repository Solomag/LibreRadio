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

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.yuriy.openradio.mobile.compose

import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommonUi
import com.yuriy.openradio.shared.model.timer.SleepTimerListener
import com.yuriy.openradio.shared.model.timer.SleepTimerModel
import java.util.Calendar
import java.util.Date

/**
 * Sleep timer operations shared by the settings screen and the player.
 */
object SleepTimer {

    val model: SleepTimerModel get() = DependencyRegistryCommonUi.sleepTimerModel()

    /**
     * @return End of the running timer, or null when it is off.
     */
    fun end(): Long? {
        val m = model
        if (m.isEnabled().not()) {
            return null
        }
        m.updateTime(true)
        val ts = m.getTimestamp()
        return if (m.isTimestampNotValid(ts)) null else ts
    }

    fun start(at: Long): Long {
        val m = model
        val c = Calendar.getInstance().apply { timeInMillis = at }
        m.setDate(c[Calendar.YEAR], c[Calendar.MONTH], c[Calendar.DAY_OF_MONTH])
        m.setTime(c[Calendar.HOUR_OF_DAY], c[Calendar.MINUTE])
        m.setEnabled(true)
        m.updateTimer(m.getTimestamp(), true)
        return m.getTimestamp()
    }

    fun stop() {
        val m = model
        m.setEnabled(false)
        m.updateTimer(m.getTimestamp(), false)
    }

    fun inMinutes(min: Int) = System.currentTimeMillis() + min * 60_000L

    /**
     * Today at [hour]:[minute], or tomorrow if that time has passed.
     */
    fun nextAt(hour: Int, minute: Int): Long {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        return c.timeInMillis
    }
}

/**
 * State of the sleep timer that follows its completion.
 */
@Composable
fun rememberSleepTimerEnd(): androidx.compose.runtime.MutableState<Long?> {
    val state = remember { mutableStateOf(SleepTimer.end()) }
    DisposableEffect(Unit) {
        val listener = object : SleepTimerListener {
            override fun onComplete() {
                state.value = null
            }
        }
        SleepTimer.model.addSleepTimerListener(listener)
        onDispose { SleepTimer.model.removeSleepTimerListener(listener) }
    }
    return state
}

/**
 * Quick presets of the sleep timer and an exact time; used in the player and in settings.
 */
@Composable
fun SleepTimerChips(end: Long?, onEnd: (Long?) -> Unit, showOff: Boolean) {
    var picking by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(15, 30, 60, 120).forEach { min ->
            val label = if (min < 60) {
                stringResource(R.string.settings_timer_in_min, min)
            } else {
                stringResource(R.string.settings_timer_in_hours, min / 60)
            }
            AssistChip(onClick = { onEnd(SleepTimer.start(SleepTimer.inMinutes(min))) }, label = { Text("+$label") })
        }
        AssistChip(
            onClick = { picking = true },
            label = { Text(stringResource(R.string.settings_timer_pick)) },
            leadingIcon = { Icon(Icons.Rounded.Schedule, contentDescription = null) }
        )
        if (showOff && end != null) {
            AssistChip(
                onClick = {
                    SleepTimer.stop()
                    onEnd(null)
                },
                label = { Text(stringResource(R.string.settings_timer_off)) }
            )
        }
    }
    if (picking) {
        val context = LocalContext.current
        val start = Calendar.getInstance().apply { timeInMillis = end ?: SleepTimer.inMinutes(30) }
        val state = rememberTimePickerState(
            initialHour = start[Calendar.HOUR_OF_DAY],
            initialMinute = start[Calendar.MINUTE],
            is24Hour = DateFormat.is24HourFormat(context)
        )
        AlertDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    onEnd(SleepTimer.start(SleepTimer.nextAt(state.hour, state.minute)))
                    picking = false
                }) { Text(stringResource(R.string.settings_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) { Text(stringResource(R.string.settings_cancel)) }
            },
            text = { TimePicker(state) }
        )
    }
}

/**
 * Sleep timer from the player.
 */
@Composable
fun SleepTimerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var end by rememberSleepTimerEnd()
    val endValue = end
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_section_timer)) },
        text = {
            Column {
                Text(
                    if (endValue == null) {
                        stringResource(R.string.settings_timer_off)
                    } else {
                        stringResource(R.string.settings_timer_at, DateFormat.getTimeFormat(context).format(Date(endValue)))
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                SleepTimerChips(endValue, { end = it }, showOff = true)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_ok)) } }
    )
}

/**
 * Tracks heard on the radio, newest first.
 */
@Composable
fun TrackHistoryDialog(vm: RadioViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_title)) },
        text = {
            if (vm.history.isEmpty()) {
                Text(stringResource(R.string.history_empty))
            } else {
                LazyColumn(Modifier.fillMaxHeight(0.7f)) {
                    items(vm.history) { entry ->
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            ListItem(
                                headlineContent = { Text(entry.track) },
                                supportingContent = {
                                    Text("${DateFormat.getTimeFormat(context).format(Date(entry.time))} · ${entry.station}")
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                modifier = Modifier.clickable { menu = true }
                            )
                            FindSongMenu(entry.track, menu) { menu = false }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_ok)) } },
        dismissButton = if (vm.history.isEmpty()) null else ({
            TextButton(onClick = { vm.clearHistory() }) { Text(stringResource(R.string.history_clear)) }
        })
    )
}

/**
 * "Find song": web search, a music app, or copy.
 */
@Composable
fun FindSongMenu(track: String, expanded: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.song_search_web)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            onClick = {
                onDismiss()
                startSafe(context, Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, track))
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.song_search_music_app)) },
            leadingIcon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null) },
            onClick = {
                onDismiss()
                val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
                    .putExtra(SearchManager.QUERY, track)
                    .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                startSafe(context, Intent.createChooser(intent, null))
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.song_copy)) },
            leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
            onClick = {
                onDismiss()
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("track", track))
                Toast.makeText(context, R.string.song_copied, Toast.LENGTH_SHORT).show()
            }
        )
    }
}

private fun startSafe(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, com.yuriy.openradio.shared.R.string.failure, Toast.LENGTH_SHORT).show()
    }
}
