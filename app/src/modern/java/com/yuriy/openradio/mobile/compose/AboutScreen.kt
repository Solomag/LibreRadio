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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaLibraryInfo
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.shared.utils.AppUtils
import com.yuriy.openradio.shared.utils.IntentUtils

private const val ORIGINAL_PROJECT_URL =
    "https://archive.softwareheritage.org/browse/origin/directory/?origin_url=https://github.com/ChernyshovYuriy/OpenRadio"
private const val AUTHOR_PROFILE_URL = "https://www.linkedin.com/in/yurii-chernyshov"
private const val SUPPORT_URL = "https://ko-fi.com/I2I1LGFPH"
private const val PROJECT_URL = "https://github.com/Solomag/LibreRadio"
private const val DEVELOPER_URL = "https://github.com/Solomag"
private const val ISSUES_URL = "https://github.com/Solomag/LibreRadio/issues"
private const val LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0"
private const val RADIO_BROWSER_URL = "https://www.radio-browser.info"
private const val WEB_RADIO_URL = "https://jcorporation.github.io/webradiodb"
private const val MEDIA3_URL = "https://github.com/androidx/media"
private const val PLAYLIST_PARSER_URL = "https://github.com/wseemann/JavaPlaylistParser"
private const val COMPOSE_URL = "https://developer.android.com/compose"
private const val COIL_URL = "https://github.com/coil-kt/coil"

/**
 * "About": the app, its origin (Open Radio by Yurii Chernyshov) and the projects it relies on.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val open: (String) -> Unit = { url ->
        IntentUtils.startActivitySafe(context, IntentUtils.makeUrlBrowsableIntent(url))
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.about_title)) },
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
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {
                // Header: icon, name, version, tagline.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(
                            Icons.Rounded.Radio,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(com.yuriy.openradio.R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(
                            R.string.about_version,
                            "${AppUtils.getApplicationVersion(context)} (${AppUtils.getApplicationVersionCode(context)})"
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.about_tagline),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }

                AboutHeader(stringResource(R.string.about_section_origin))
                LinkRow(
                    stringResource(com.yuriy.openradio.shared.R.string.about_based_on),
                    stringResource(com.yuriy.openradio.shared.R.string.about_original_name)
                ) { open(ORIGINAL_PROJECT_URL) }
                LinkRow(
                    stringResource(com.yuriy.openradio.shared.R.string.about_author),
                    stringResource(com.yuriy.openradio.shared.R.string.about_author_name)
                ) { open(AUTHOR_PROFILE_URL) }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        stringResource(com.yuriy.openradio.shared.R.string.about_support_original_author),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = { open(SUPPORT_URL) }) {
                        Icon(Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Ko-fi")
                    }
                }

                AboutHeader(stringResource(R.string.about_section_project))
                LinkRow(stringResource(R.string.about_developer), "Solomag") { open(DEVELOPER_URL) }
                LinkRow(stringResource(R.string.about_source_code), "github.com/Solomag/LibreRadio") { open(PROJECT_URL) }
                LinkRow(stringResource(com.yuriy.openradio.shared.R.string.about_report_issue), null) { open(ISSUES_URL) }
                LinkRow(stringResource(R.string.about_license), null) { open(LICENSE_URL) }

                AboutHeader(stringResource(R.string.about_section_sources))
                LinkRow("Radio Browser", "radio-browser.info") { open(RADIO_BROWSER_URL) }
                LinkRow("Web Radio DB", "jcorporation.github.io/webradiodb") { open(WEB_RADIO_URL) }

                AboutHeader(stringResource(R.string.about_section_libraries))
                LinkRow("AndroidX Media3", MediaLibraryInfo.VERSION) { open(MEDIA3_URL) }
                LinkRow("Jetpack Compose", null) { open(COMPOSE_URL) }
                LinkRow("Coil", null) { open(COIL_URL) }
                LinkRow(stringResource(com.yuriy.openradio.shared.R.string.about_playlist_url), null) {
                    open(PLAYLIST_PARSER_URL)
                }
            }
        }
    }
}

@Composable
private fun AboutHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun LinkRow(title: String, subtitle: String?, onClick: () -> Unit) {
    val support: (@Composable () -> Unit)? = if (subtitle == null) null else slot { Text(subtitle) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = support,
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable(onClick = onClick)
    )
}
