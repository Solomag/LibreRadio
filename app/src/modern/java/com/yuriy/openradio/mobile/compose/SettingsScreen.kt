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

import androidx.compose.material.icons.rounded.Refresh
import com.yuriy.openradio.shared.model.net.StationCheck
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.RadioButton
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommonUi
import com.yuriy.openradio.shared.model.eq.EqualizerState
import com.yuriy.openradio.shared.model.storage.AppPreferencesManager
import com.yuriy.openradio.shared.model.timer.SleepTimerListener
import com.yuriy.openradio.shared.model.timer.SleepTimerModel
import com.yuriy.openradio.shared.service.OpenRadioService
import com.yuriy.openradio.shared.service.OpenRadioStore
import com.yuriy.openradio.shared.service.location.LocationService
import com.yuriy.openradio.shared.utils.AppUtils
import java.util.Calendar
import java.util.Date

/**
 * Actions of the settings screen that need the activity.
 */
interface SettingsHost {
    fun exportFavorites()
    fun importFavorites()
    fun checkUpdates()
    fun countryCode(): String

    /**
     * @param code Country code, or null to detect it from the location again.
     */
    fun setCountryCode(code: String?)
}

/**
 * All settings of the app on one screen (replaces the separate dialogs of the classic UI).
 */
@Composable
fun SettingsScreen(vm: RadioViewModel, host: SettingsHost, onBack: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {
                SleepTimerSection()
                PlaybackSection(vm)
                EqualizerSection()
                NetworkSection(vm)
                StationsSection(vm)
                CatalogSection(vm, host)
                BufferingSection()
                BackupSection(host)
                AdvancedSection()
                Header(stringResource(R.string.settings_section_app))
                ClickRow(Icons.Rounded.SystemUpdate, stringResource(R.string.update_check_menu)) { host.checkUpdates() }
            }
        }
    }
}

// region Sleep timer

@Composable
private fun SleepTimerSection() {
    val context = LocalContext.current
    var end by rememberSleepTimerEnd()
    Header(stringResource(R.string.settings_section_timer))
    val endValue = end
    SwitchRow(
        icon = Icons.Rounded.Bedtime,
        title = stringResource(R.string.settings_timer_switch),
        desc = if (endValue == null) {
            stringResource(R.string.settings_timer_off)
        } else {
            stringResource(R.string.settings_timer_at, DateFormat.getTimeFormat(context).format(Date(endValue)))
        },
        checked = endValue != null
    ) { on ->
        end = if (on) {
            SleepTimer.start(SleepTimer.inMinutes(30))
        } else {
            SleepTimer.stop()
            null
        }
    }
    if (endValue != null) {
        Column(Modifier.padding(start = 56.dp, end = 16.dp)) {
            SleepTimerChips(endValue, { end = it }, showOff = false)
        }
    }
}

// endregion

@Composable
private fun PlaybackSection(vm: RadioViewModel) {
    val context = LocalContext.current
    Header(stringResource(R.string.settings_section_playback))

    var lastStation by remember { mutableStateOf(AppPreferencesManager.lastKnownRadioStationEnabled(context)) }
    SwitchRow(title = stringResource(R.string.settings_last_station), checked = lastStation) {
        lastStation = it
        AppPreferencesManager.lastKnownRadioStationEnabled(context, it)
    }

    var btResume by remember { mutableStateOf(AppPreferencesManager.isBtAutoPlay(context)) }
    val btPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            btResume = true
            AppPreferencesManager.setBtAutoPlay(context, true)
        }
    }
    SwitchRow(title = stringResource(R.string.settings_bt_resume), checked = btResume) { on ->
        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            btPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            btResume = on
            AppPreferencesManager.setBtAutoPlay(context, on)
        }
    }
}

@Composable
private fun EqualizerSection() {
    val presenter = remember { DependencyRegistryCommonUi.equalizerPresenter() }
    Header(stringResource(R.string.settings_section_eq))
    Text(
        stringResource(R.string.settings_eq_desc),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
    var available by remember { mutableStateOf(presenter.hasEqualizerState()) }
    LaunchedEffect(available) {
        while (available.not()) {
            delay(1000)
            available = presenter.hasEqualizerState()
        }
    }
    if (available.not()) {
        Text(
            stringResource(R.string.settings_eq_waiting),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        return
    }
    var state by remember { mutableStateOf(presenter.loadEqualizerState()) }
    var preset by remember { mutableStateOf(state.currentPreset.toInt()) }
    var expanded by remember { mutableStateOf(false) }
    val presets = state.presets
    val custom = stringResource(R.string.settings_eq_custom)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        OutlinedTextField(
            value = if (preset < 0) custom else presets.getOrElse(preset) { "" },
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_eq_preset)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            presets.forEachIndexed { i, name ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    expanded = false
                    preset = i
                    presenter.selectEqualizerState(state, i) { state = it }
                })
            }
        }
    }
    Text(
        stringResource(R.string.settings_eq_bands_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
    EqualizerBands(state) { band, level ->
        preset = EqualizerState.CUSTOM_PRESET.toInt()
        state = presenter.setBandLevel(band, level)
    }
}

/**
 * Bands side by side as vertical sliders, 0 dB in the middle (like a hardware equalizer);
 * moving one switches to the custom preset.
 */
@Composable
private fun EqualizerBands(state: EqualizerState, onLevel: (Int, Short) -> Unit) {
    val range = state.bandLevelRange
    if (range.size < 2 || range[1] <= range[0]) {
        return
    }
    val low = range[0].toFloat()
    val high = range[1].toFloat()
    val freqs = state.centerFrequencies
    val levels = state.bandLevels
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        for (i in 0 until minOf(freqs.size, levels.size)) {
            val hz = freqs[i] / 1000
            val label = if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"
            var value by remember(state, i) { mutableFloatStateOf(levels[i].toFloat()) }
            VerticalBand(
                label = label,
                value = value,
                low = low,
                high = high,
                onChange = { value = it },
                onDone = { onLevel(i, value.toInt().toShort()) }
            )
        }
    }
}

/**
 * One band: dB on top, a vertical track filled from the middle (0 dB) to the level, frequency below.
 * Drag or tap to set; levels snap to whole dB.
 */
@Composable
private fun VerticalBand(
    label: String,
    value: Float,
    low: Float,
    high: Float,
    onChange: (Float) -> Unit,
    onDone: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val change by rememberUpdatedState(onChange)
    val done by rememberUpdatedState(onDone)
    fun levelAt(y: Float, height: Float): Float {
        val fraction = 1f - (y / height).coerceIn(0f, 1f)
        val raw = low + fraction * (high - low)
        // Whole dB (level is in millibels).
        return (Math.round(raw / 100f) * 100f).coerceIn(low, high)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
        Text(
            "%+d".format(Math.round(value / 100f)),
            style = MaterialTheme.typography.labelMedium,
            color = if (Math.round(value / 100f) == 0) scheme.onSurfaceVariant else scheme.primary
        )
        Spacer(Modifier.height(6.dp))
        Canvas(
            modifier = Modifier
                .height(180.dp)
                .width(44.dp)
                .semantics {
                    contentDescription = label
                    stateDescription = "%+d dB".format(Math.round(value / 100f))
                    progressBarRangeInfo = ProgressBarRangeInfo(value, low..high)
                    setProgress { target ->
                        change(target.coerceIn(low, high))
                        done()
                        true
                    }
                }
                .pointerInput(low, high) {
                    detectTapGestures { offset ->
                        change(levelAt(offset.y, size.height.toFloat()))
                        done()
                    }
                }
                .pointerInput(low, high) {
                    detectVerticalDragGestures(
                        onDragEnd = { done() },
                        onDragCancel = { done() }
                    ) { pointer, _ ->
                        pointer.consume()
                        change(levelAt(pointer.position.y, size.height.toFloat()))
                    }
                }
        ) {
            val cx = size.width / 2
            val track = 6.dp.toPx()
            val mid = size.height / 2
            val y = size.height * (1f - (value - low) / (high - low))
            // Track, 0 dB mark, fill from 0 dB to the level, thumb.
            drawLine(scheme.surfaceVariant, Offset(cx, 0f), Offset(cx, size.height), track, StrokeCap.Round)
            drawLine(scheme.outline, Offset(cx - 14.dp.toPx(), mid), Offset(cx + 14.dp.toPx(), mid), 2.dp.toPx())
            drawLine(scheme.primary, Offset(cx, mid), Offset(cx, y), track, StrokeCap.Round)
            drawCircle(scheme.primary, 10.dp.toPx(), Offset(cx, y))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun NetworkSection(vm: RadioViewModel) {
    val storage = remember { DependencyRegistryCommonUi.networkSettingsStorage() }
    var mobile by remember { mutableStateOf(storage.getUseMobile()) }
    Header(stringResource(R.string.settings_section_network))
    SwitchRow(
        title = stringResource(R.string.settings_mobile_data),
        desc = stringResource(R.string.settings_mobile_data_desc),
        checked = mobile
    ) {
        mobile = it
        storage.setUseMobile(it)
        vm.sendCommand(OpenRadioService.CMD_NET_CHANGED)
    }
}

/**
 * Station check on this phone and the bypass of blocks by the server name.
 */
@Composable
private fun StationsSection(vm: RadioViewModel) {
    val context = LocalContext.current
    var hide by remember { mutableStateOf(StationCheck.isHideEnabled(context)) }
    var bypass by remember { mutableStateOf(StationCheck.isBypassEnabled(context)) }
    Header(stringResource(R.string.settings_section_stations))
    SwitchRow(
        title = stringResource(R.string.settings_hide_failed),
        desc = stringResource(R.string.settings_hide_failed_desc),
        checked = hide
    ) {
        hide = it
        StationCheck.setHideEnabled(context, it)
    }
    SwitchRow(
        title = stringResource(R.string.settings_bypass),
        desc = stringResource(R.string.settings_bypass_desc),
        checked = bypass
    ) {
        bypass = it
        StationCheck.setBypassEnabled(context, it)
        vm.recheckStations()
    }
    ClickRow(
        Icons.Rounded.Refresh,
        stringResource(R.string.settings_recheck),
        stringResource(R.string.settings_recheck_desc)
    ) {
        StationCheck.clear(context)
        vm.recheckStations()
        Toast.makeText(context, R.string.settings_recheck_started, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun CatalogSection(vm: RadioViewModel, host: SettingsHost) {
    val context = LocalContext.current
    var code by remember { mutableStateOf(host.countryCode()) }
    var choosing by remember { mutableStateOf(false) }
    Header(stringResource(R.string.settings_section_catalog))
    ClickRow(Icons.Rounded.Public, stringResource(R.string.settings_country), countryName(code)) { choosing = true }
    ClickRow(
        Icons.Rounded.CleaningServices,
        stringResource(R.string.settings_clear_cache),
        stringResource(R.string.settings_clear_cache_desc)
    ) {
        vm.sendCommand(OpenRadioService.CMD_CLEAR_CACHE)
        Toast.makeText(context, R.string.settings_cache_cleared, Toast.LENGTH_SHORT).show()
    }
    if (choosing) {
        CountryDialog(
            onDismiss = { choosing = false },
            onPick = { picked ->
                choosing = false
                host.setCountryCode(picked)
                code = host.countryCode()
            }
        )
    }
}

private fun countryName(code: String): String {
    return LocationService.displayCountryName(code)
}

/**
 * Searchable list of countries. By default the first entry detects the country from the location
 * (null is picked); with [ownFirst] the user's own countries come first instead.
 */
@Composable
internal fun CountryDialog(ownFirst: Boolean = false, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    val context = LocalContext.current
    val all = remember {
        val list = LocationService.getCountriesWithLocation(context).toList()
        if (ownFirst.not()) {
            list
        } else {
            val own = LocationService.ownCountryCodes(context)
            val rest = list.drop(1)
            // A placeholder head keeps "drop(1)" below working; it is replaced by the own countries.
            listOf(list.first()) + own.mapNotNull { c -> rest.firstOrNull { it.code == c } } +
                    rest.filter { it.code !in own }
        }
    }
    var query by remember { mutableStateOf("") }
    val shown = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) all else all.drop(1).filter {
            it.name.contains(q, ignoreCase = true) || it.code.equals(q, ignoreCase = true)
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxHeight(0.8f)
        ) {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.settings_country_search)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
                LazyColumn {
                    items(if (ownFirst) shown.filter { it !== all.first() } else shown, key = { it.code }) { country ->
                        val isLocation = ownFirst.not() && country === all.first()
                        val support: (@Composable () -> Unit)? =
                            if (isLocation) null else slot { Text(country.code) }
                        val leading: (@Composable () -> Unit)? =
                            if (isLocation) slot { Icon(Icons.Rounded.Public, contentDescription = null) } else null
                        ListItem(
                            headlineContent = { Text(country.name) },
                            supportingContent = support,
                            leadingContent = leading,
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            modifier = Modifier.clickable { onPick(if (isLocation) null else country.code) }
                        )
                    }
                }
            }
        }
    }
}

// region Buffering

private const val DEFAULT_MIN_BUFFER = 50_000
private const val DEFAULT_MAX_BUFFER = 50_000
private const val DEFAULT_PLAY_BUFFER = 2_500
private const val DEFAULT_REBUFFER = 5_000

/**
 * Buffer presets: (minimum, maximum, to start, to resume after a stall), ms.
 */
private enum class BufferPreset(val title: Int, val desc: Int, val values: List<Int>) {
    FAST(R.string.settings_buffer_fast, R.string.settings_buffer_fast_desc, listOf(15_000, 30_000, 1_000, 2_500)),
    NORMAL(
        R.string.settings_buffer_normal, R.string.settings_buffer_normal_desc,
        listOf(DEFAULT_MIN_BUFFER, DEFAULT_MAX_BUFFER, DEFAULT_PLAY_BUFFER, DEFAULT_REBUFFER)
    ),
    RELIABLE(R.string.settings_buffer_reliable, R.string.settings_buffer_reliable_desc, listOf(60_000, 120_000, 5_000, 10_000))
}

@Composable
private fun BufferingSection() {
    val context = LocalContext.current
    val minVal = context.resources.getInteger(com.yuriy.openradio.R.integer.min_buffer_val)
    val maxVal = context.resources.getInteger(com.yuriy.openradio.R.integer.max_buffer_val)
    var minBuf by remember { mutableStateOf(AppPreferencesManager.getMinBuffer(context).toString()) }
    var maxBuf by remember { mutableStateOf(AppPreferencesManager.getMaxBuffer(context).toString()) }
    var playBuf by remember { mutableStateOf(AppPreferencesManager.getPlayBuffer(context).toString()) }
    var reBuf by remember { mutableStateOf(AppPreferencesManager.getPlayBufferRebuffer(context).toString()) }
    val current = listOf(minBuf, maxBuf, playBuf, reBuf).map { it.toIntOrNull() }
    var custom by remember { mutableStateOf(BufferPreset.entries.none { it.values == current }) }

    Header(stringResource(R.string.settings_section_buffering))
    Text(
        stringResource(R.string.settings_buffering_explained),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
    for (preset in BufferPreset.entries) {
        val selected = custom.not() && preset.values == current
        ListItem(
            headlineContent = { Text(stringResource(preset.title)) },
            supportingContent = { Text(stringResource(preset.desc)) },
            leadingContent = { RadioButton(selected = selected, onClick = null) },
            colors = rowColors(),
            modifier = Modifier.clickable {
                custom = false
                val (a, b, c, d) = preset.values.map { it.toString() }
                minBuf = a
                maxBuf = b
                playBuf = c
                reBuf = d
                saveBuffers(context, a, b, c, d, minVal, maxVal)
            }
        )
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_buffer_custom)) },
        supportingContent = { Text(stringResource(R.string.settings_buffering_desc, minVal, maxVal)) },
        leadingContent = { RadioButton(selected = custom, onClick = null) },
        colors = rowColors(),
        modifier = Modifier.clickable { custom = true }
    )
    if (custom.not()) {
        return
    }
    BufferField(stringResource(R.string.settings_buffer_min), minBuf) { minBuf = it }
    BufferField(stringResource(R.string.settings_buffer_max), maxBuf) { maxBuf = it }
    BufferField(stringResource(R.string.settings_buffer_start), playBuf) { playBuf = it }
    BufferField(stringResource(R.string.settings_buffer_rebuffer), reBuf) { reBuf = it }
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        FilledTonalButton(onClick = {
            saveBuffers(context, minBuf, maxBuf, playBuf, reBuf, minVal, maxVal)
        }) { Text(stringResource(R.string.settings_apply)) }
    }
}

@Composable
private fun BufferField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(6)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

/**
 * Validates and stores the buffer values (the same rules as the classic dialog).
 */
private fun saveBuffers(
    context: Context,
    minStr: String,
    maxStr: String,
    playStr: String,
    reStr: String,
    minVal: Int,
    maxVal: Int
) {
    val values = listOf(minStr, maxStr, playStr, reStr).map { it.toIntOrNull() }
    values.forEachIndexed { i, v ->
        if (v == null || v < minVal || v > maxVal) {
            val raw = listOf(minStr, maxStr, playStr, reStr)[i]
            toast(context, context.getString(com.yuriy.openradio.shared.R.string.invalid_buffer_desc) + raw)
            return
        }
    }
    val (min, max, play, re) = values.map { it!! }
    val error = when {
        max < min -> com.yuriy.openradio.shared.R.string.min_buffer_greater_max_buffer
        min < play -> com.yuriy.openradio.shared.R.string.play_buffer_greater_min_buffer
        min < re -> com.yuriy.openradio.shared.R.string.play_re_buffer_greater_min_buffer
        else -> 0
    }
    if (error != 0) {
        toast(context, context.getString(error))
        return
    }
    AppPreferencesManager.setMinBuffer(context, min)
    AppPreferencesManager.setMaxBuffer(context, max)
    AppPreferencesManager.setPlayBuffer(context, play)
    AppPreferencesManager.setPlayBufferRebuffer(context, re)
    toast(context, context.getString(R.string.settings_saved))
}

// endregion

@Composable
private fun BackupSection(host: SettingsHost) {
    Header(stringResource(R.string.settings_section_backup))
    ClickRow(Icons.Rounded.Upload, stringResource(R.string.settings_export), stringResource(R.string.settings_export_desc)) {
        host.exportFavorites()
    }
    ClickRow(Icons.Rounded.Download, stringResource(R.string.settings_import), stringResource(R.string.settings_import_desc)) {
        host.importFavorites()
    }
}

@Composable
private fun AdvancedSection() {
    val context = LocalContext.current
    var custom by remember { mutableStateOf(AppPreferencesManager.isCustomUserAgent(context)) }
    var agent by remember { mutableStateOf(AppUtils.getUserAgent(context)) }
    Header(stringResource(R.string.settings_section_advanced))
    SwitchRow(title = stringResource(R.string.settings_user_agent), checked = custom) {
        custom = it
        AppPreferencesManager.isCustomUserAgent(context, it)
        agent = AppUtils.getUserAgent(context)
    }
    OutlinedTextField(
        value = agent,
        onValueChange = {
            agent = it
            if (it.isNotBlank()) {
                AppPreferencesManager.setCustomUserAgent(context, it.trim())
            }
        },
        enabled = custom,
        textStyle = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

// region Building blocks

/**
 * Typed composable slot, for optional slots built with if/else.
 */
internal fun slot(content: @Composable () -> Unit): @Composable () -> Unit = content

private fun toast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}

@Composable
private fun rowColors() = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)

@Composable
private fun Header(text: String) {
    Column {
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    icon: ImageVector? = null,
    desc: String? = null,
    onChange: (Boolean) -> Unit
) {
    val support: (@Composable () -> Unit)? = if (desc == null) null else slot { Text(desc) }
    val leading: (@Composable () -> Unit)? =
        if (icon == null) null else slot { Icon(icon, contentDescription = null) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = support,
        leadingContent = leading,
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = rowColors(),
        modifier = Modifier.clickable { onChange(checked.not()) }
    )
}

@Composable
private fun ClickRow(icon: ImageVector, title: String, desc: String? = null, onClick: () -> Unit) {
    val support: (@Composable () -> Unit)? =
        if (desc == null) null else slot { Text(desc, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = support,
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = rowColors(),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

// endregion
