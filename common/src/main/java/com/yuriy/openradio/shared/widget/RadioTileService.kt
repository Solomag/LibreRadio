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

package com.yuriy.openradio.shared.widget

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.yuriy.openradio.R

/**
 * Quick settings tile: play / pause the last station; shows its name.
 */
@RequiresApi(Build.VERSION_CODES.N)
class RadioTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        update()
    }

    override fun onClick() {
        super.onClick()
        // Show the expected state at once; the service confirms it through NowPlaying.publish.
        qsTile?.let {
            it.state = if (it.state == Tile.STATE_ACTIVE) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            it.updateTile()
        }
        NowPlaying.toggle(this)
    }

    private fun update() {
        val tile = qsTile ?: return
        val state = NowPlaying.load(this)
        tile.state = if (state.playing) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = state.station.ifEmpty { getString(R.string.app_name) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = state.track.ifEmpty { null }
        }
        tile.updateTile()
    }
}
