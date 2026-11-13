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

/**
 * @return End of the running sleep timer, or null when it is off.
 */
private fun timerEnd(model: SleepTimerModel): Long? {
    if (model.isEnabled().not()) {
        return null
    }
    model.updateTime(true)
    val ts = model.getTimestamp()
    return if (model.isTimestampNotValid(ts)) null else ts
}

@Composable
private fun SleepTimerSection() {
    val context = LocalContext.current
    val model = remember { DependencyRegistryCommonUi.sleepTimerModel() }
    var end by remember { mutableStateOf(timerEnd(model)) }
    var picking by remember { mutableStateOf(false) }

    DisposableEffect(model) {
        val listener = object : SleepTimerListener {
            override fun onComplete() {
                end = null
            }
        }
        model.addSleepTimerListener(listener)
        onDispose { model.removeSleepTimerListener(listener) }
    }

    fun start(at: Long) {
        val c = Calendar.getInstance().apply { timeInMillis = at }
        model.setDate(c[Calendar.YEAR], c[Calendar.MONTH], c[Calendar.DAY_OF_MONTH])
        model.setTime(c[Calendar.HOUR_OF_DAY], c[Calendar.MINUTE])
        model.setEnabled(true)
        model.updateTimer(model.getTimestamp(), true)
        end = model.getTimestamp()
    }

    fun stop() {
        model.setEnabled(false)
        model.updateTimer(model.getTimestamp(), false)
        end = null
    }

    fun inMinutes(min: Int) = System.currentTimeMillis() + min * 60_000L

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
    ) { on -> if (on) start(inMinutes(30)) else stop() }
    if (endValue != null) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 56.dp, end = 16.dp)
        ) {
            listOf(15, 30, 60, 120).forEach { min ->
                val label = if (min < 60) {
                    stringResource(R.string.settings_timer_in_min, min)
                } else {
                    stringResource(R.string.settings_timer_in_hours, min / 60)
                }
                AssistChip(onClick = { start(inMinutes(min)) }, label = { Text("+$label") })
            }
            AssistChip(
                onClick = { picking = true },
                label = { Text(stringResource(R.string.settings_timer_pick)) },
                leadingIcon = { Icon(Icons.Rounded.Schedule, contentDescription = null) }
            )
        }
    }
    if (picking) {
        val now = Calendar.getInstance().apply { timeInMillis = end ?: inMinutes(30) }
        val state = rememberTimePickerState(
            initialHour = now[Calendar.HOUR_OF_DAY],
            initialMinute = now[Calendar.MINUTE],
            is24Hour = DateFormat.is24HourFormat(context)
        )
        AlertDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    val c = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, state.hour)
                        set(Calendar.MINUTE, state.minute)
                        set(Calendar.SECOND, 0)
                        if (timeInMillis <= System.currentTimeMillis()) {
                            add(Calendar.DAY_OF_MONTH, 1)
                        }
                    }
                    start(c.timeInMillis)
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

    var volume by remember {
        mutableFloatStateOf(
            AppPreferencesManager.getMasterVolume(context, OpenRadioService.MASTER_VOLUME_DEFAULT).toFloat()
        )
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_volume)) },
        supportingContent = {
            Slider(
                value = volume,
                onValueChange = { volume = it },
                valueRange = 0f..100f,
                onValueChangeFinished = {
                    val v = volume.toInt()
                    AppPreferencesManager.setMasterVolume(context, v)
                    vm.sendCommand(
                        OpenRadioService.CMD_MASTER_VOLUME_CHANGED,
                        OpenRadioStore.makeMasterVolumeChangedBundle(v)
                    )
                }
            )
        },
        trailingContent = { Text("${volume.toInt()}%") },
        colors = rowColors()
    )
}

@Composable
private fun EqualizerSection() {
    val presenter = remember { DependencyRegistryCommonUi.equalizerPresenter() }
    Header(stringResource(R.string.settings_section_eq))
    if (presenter.hasEqualizerState().not()) {
        Text(
            stringResource(com.yuriy.openradio.shared.R.string.eq_not_available),
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
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        OutlinedTextField(
            value = presets.getOrElse(preset) { "" },
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
    EqualizerBands(state)
}

/**
 * Band levels of the current preset (presets only; bands are not editable one by one).
 */
@Composable
private fun EqualizerBands(state: EqualizerState) {
    val range = state.bandLevelRange
    if (range.size < 2 || range[1] <= range[0]) {
        return
    }
    val low = range[0].toFloat()
    val high = range[1].toFloat()
    val freqs = state.centerFrequencies
    val levels = state.bandLevels
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        for (i in 0 until minOf(freqs.size, levels.size)) {
            val hz = freqs[i] / 1000
            val label = if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(64.dp))
                LinearProgressIndicator(
                    progress = { ((levels[i] - low) / (high - low)).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                )
                Text(
                    "%+d dB".format(levels[i] / 100),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .width(56.dp)
                        .padding(start = 8.dp)
                )
            }
        }
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
    return LocationService.COUNTRY_CODE_TO_NAME[code.uppercase()] ?: code
}

/**
 * Searchable list of countries; the first entry detects the country from the location.
 */
@Composable
private fun CountryDialog(onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    val context = LocalContext.current
    val all = remember { LocationService.getCountriesWithLocation(context).toList() }
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
                    items(shown, key = { it.code }) { country ->
                        val isLocation = country === all.first()
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

@Composable
private fun BufferingSection() {
    val context = LocalContext.current
    val minVal = context.resources.getInteger(com.yuriy.openradio.R.integer.min_buffer_val)
    val maxVal = context.resources.getInteger(com.yuriy.openradio.R.integer.max_buffer_val)
    var minBuf by remember { mutableStateOf(AppPreferencesManager.getMinBuffer(context).toString()) }
    var maxBuf by remember { mutableStateOf(AppPreferencesManager.getMaxBuffer(context).toString()) }
    var playBuf by remember { mutableStateOf(AppPreferencesManager.getPlayBuffer(context).toString()) }
    var reBuf by remember { mutableStateOf(AppPreferencesManager.getPlayBufferRebuffer(context).toString()) }

    Header(stringResource(R.string.settings_section_buffering))
    Text(
        stringResource(R.string.settings_buffering_desc, minVal, maxVal),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
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
        OutlinedButton(onClick = {
            minBuf = DEFAULT_MIN_BUFFER.toString()
            maxBuf = DEFAULT_MAX_BUFFER.toString()
            playBuf = DEFAULT_PLAY_BUFFER.toString()
            reBuf = DEFAULT_REBUFFER.toString()
            saveBuffers(context, minBuf, maxBuf, playBuf, reBuf, minVal, maxVal)
        }) { Text(stringResource(R.string.settings_defaults)) }
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
