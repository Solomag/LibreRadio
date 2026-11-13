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

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.yuriy.openradio.shared.service.OpenRadioService
import com.yuriy.openradio.shared.utils.AppLogger

/**
 * What plays now, for the home screen widget and the quick settings tile.
 * The service publishes it; both read it (also after the process was gone).
 */
object NowPlaying {

    private const val PREFS = "now_playing"
    private const val KEY_STATION = "station"
    private const val KEY_TRACK = "track"
    private const val KEY_PLAYING = "playing"

    data class State(val station: String, val track: String, val playing: Boolean)

    fun load(context: Context): State {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return State(
            p.getString(KEY_STATION, "") ?: "",
            p.getString(KEY_TRACK, "") ?: "",
            p.getBoolean(KEY_PLAYING, false)
        )
    }

    /**
     * Called by the service on every player change; refreshes the widget and the tile when needed.
     */
    fun publish(context: Context, station: String, track: String, playing: Boolean) {
        val app = context.applicationContext
        val old = load(app)
        val new = State(station, track, playing)
        if (old == new) {
            return
        }
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_STATION, station)
            .putString(KEY_TRACK, track)
            .putBoolean(KEY_PLAYING, playing)
            .apply()
        RadioWidgetProvider.refresh(app, new)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching {
                TileService.requestListeningState(app, ComponentName(app, RadioTileService::class.java))
            }
        }
    }

    /**
     * Play / pause the last station, like the play button of the app.
     */
    fun toggle(context: Context, done: () -> Unit = {}) {
        withController(context, done) {
            it.sendCustomCommand(SessionCommand(OpenRadioService.CMD_TOGGLE_LAST_PLAYED_ITEM, Bundle()), Bundle())
        }
    }

    fun next(context: Context, done: () -> Unit = {}) {
        withController(context, done) {
            if (it.playbackState == Player.STATE_IDLE) {
                it.prepare()
            }
            it.seekToNextMediaItem()
        }
    }

    /**
     * Connects to the service for one action. Binding lets Media3 start the service in the
     * foreground itself when playback starts, which a plain start from the background may not.
     */
    private fun withController(context: Context, done: () -> Unit, action: (MediaController) -> Unit) {
        val app = context.applicationContext
        val token = SessionToken(app, ComponentName(app, OpenRadioService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            try {
                val controller = future.get()
                action(controller)
                // Give the command a moment to reach the session before letting go.
                android.os.Handler(app.mainLooper).postDelayed({
                    controller.release()
                    done()
                }, 1500)
            } catch (e: Exception) {
                AppLogger.e("NowPlaying controller", e)
                done()
            }
        }, MoreExecutors.directExecutor())
    }
}
