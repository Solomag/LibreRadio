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

@file:OptIn(ExperimentalMaterial3Api::class)

package com.yuriy.openradio.mobile.compose

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommonUi
import com.yuriy.openradio.shared.model.media.RadioStationToAdd
import com.yuriy.openradio.shared.service.location.LocationService
import com.yuriy.openradio.shared.utils.AppUtils

/**
 * Form to add an own station (stream URL). The user's own country is preselected.
 */
@Composable
fun AddStationScreen(onDone: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val presenter = remember { DependencyRegistryCommonUi.addEditStationPresenter() }
    val genres = remember { AppUtils.predefinedCategories().toList() }
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var homePage by rememberSaveable { mutableStateOf("") }
    var genre by rememberSaveable { mutableStateOf(genres.firstOrNull() ?: "") }
    var country by rememberSaveable { mutableStateOf(LocationService.ownCountryCodes(context).firstOrNull() ?: "") }
    var favorite by rememberSaveable { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var choosingCountry by remember { mutableStateOf(false) }
    var genreOpen by remember { mutableStateOf(false) }

    fun save() {
        if (name.isBlank() || url.isBlank() || saving) {
            return
        }
        saving = true
        var finished = false
        val station = RadioStationToAdd(
            name.trim(), url.trim(), "", homePage.trim(), genre,
            LocationService.COUNTRY_CODE_TO_NAME[country] ?: "", favorite
        )
        presenter.addRadioStation(
            station,
            {
                if (finished.not()) {
                    finished = true
                    saving = false
                    Toast.makeText(context, R.string.add_station_done, Toast.LENGTH_SHORT).show()
                    onDone()
                }
            },
            { msg ->
                // A bad home page is only a warning; the station is still added.
                if (finished.not() && msg.contains("home page", ignoreCase = true).not()) {
                    saving = false
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(com.yuriy.openradio.shared.R.string.add_station_dialog_title)) },
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
                    .padding(16.dp)
            ) {
                if (saving) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(com.yuriy.openradio.shared.R.string.add_station_name_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(com.yuriy.openradio.shared.R.string.add_station_url_hint)) },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
                OutlinedTextField(
                    value = homePage,
                    onValueChange = { homePage = it },
                    label = { Text(stringResource(com.yuriy.openradio.shared.R.string.add_station_home_page_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
                ExposedDropdownMenuBox(
                    expanded = genreOpen,
                    onExpandedChange = { genreOpen = it },
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    OutlinedTextField(
                        value = genre,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(com.yuriy.openradio.shared.R.string.add_station_genre_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(genreOpen) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = genreOpen, onDismissRequest = { genreOpen = false }) {
                        genres.forEach { g ->
                            DropdownMenuItem(text = { Text(g) }, onClick = {
                                genre = g
                                genreOpen = false
                            })
                        }
                    }
                }
                ListItem(
                    headlineContent = { Text(stringResource(com.yuriy.openradio.shared.R.string.add_station_country_label)) },
                    supportingContent = { Text(if (country.isEmpty()) "—" else LocationService.displayCountryName(country)) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.clickable { choosingCountry = true }
                )
                ListItem(
                    headlineContent = { Text(stringResource(com.yuriy.openradio.shared.R.string.add_to_fav_string)) },
                    trailingContent = { Switch(checked = favorite, onCheckedChange = { favorite = it }) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.clickable { favorite = favorite.not() }
                )
                Button(
                    onClick = { save() },
                    enabled = name.isNotBlank() && url.isNotBlank() && saving.not(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) { Text(stringResource(com.yuriy.openradio.shared.R.string.add_station_dialog_button_label)) }
            }
        }
    }
    if (choosingCountry) {
        CountryDialog(
            ownFirst = true,
            onDismiss = { choosingCountry = false },
            onPick = { picked ->
                choosingCountry = false
                if (picked != null) {
                    country = picked
                }
            }
        )
    }
}
