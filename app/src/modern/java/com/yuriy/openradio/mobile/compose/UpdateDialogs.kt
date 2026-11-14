package com.yuriy.openradio.mobile.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.mobile.update.UpdateManager

/**
 * What the update windows show: an offered version, and the download progress.
 */
class UpdateUiState {
    class Progress(val percent: Int?, val cancel: () -> Unit)

    var offer by mutableStateOf<UpdateManager.Offer?>(null)
    var progress by mutableStateOf<Progress?>(null)
}

/**
 * "New version" and "Downloading" windows in the app's own style.
 */
@Composable
fun UpdateDialogs(state: UpdateUiState) {
    state.offer?.let { offer ->
        OfferDialog(offer) { state.offer = null }
    }
    state.progress?.let { ProgressDialog(it) }
}

@Composable
private fun OfferDialog(offer: UpdateManager.Offer, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    stringResource(R.string.update_available_title, offer.version),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (offer.notes.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        for (line in offer.notes.lines().filter { it.isNotBlank() }) {
                            NoteLine(line)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        onClose()
                        offer.download()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.update_download)) }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = {
                        onClose()
                        offer.skip()
                    }) { Text(stringResource(R.string.update_skip)) }
                    TextButton(onClick = onClose) { Text(stringResource(R.string.update_later)) }
                }
            }
        }
    }
}

/**
 * A line of the changes: bullets get an amber dot and a hanging indent.
 */
@Composable
private fun NoteLine(line: String) {
    val bullet = line.startsWith("• ")
    Row(verticalAlignment = Alignment.Top) {
        if (bullet) {
            Text("•", color = Amber, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            if (bullet) line.removePrefix("• ") else line,
            style = MaterialTheme.typography.bodyMedium,
            color = if (bullet) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProgressDialog(progress: UpdateUiState.Progress) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    stringResource(R.string.update_downloading),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(20.dp))
                val percent = progress.percent
                if (percent == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp))
                } else {
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier.fillMaxWidth().height(4.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$percent%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = progress.cancel) { Text(stringResource(android.R.string.cancel)) }
                }
            }
        }
    }
}
