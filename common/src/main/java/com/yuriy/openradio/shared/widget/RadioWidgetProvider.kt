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

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.yuriy.openradio.R

/**
 * Home screen widget: station, track, play / pause and next.
 */
class RadioWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = build(context, NowPlaying.load(context))
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE -> {
                val pending = goAsync()
                NowPlaying.toggle(context) { pending.finish() }
            }

            ACTION_NEXT -> {
                val pending = goAsync()
                NowPlaying.next(context) { pending.finish() }
            }

            else -> super.onReceive(context, intent)
        }
    }

    companion object {

        private const val ACTION_TOGGLE = "com.yuriy.openradio.widget.TOGGLE"
        private const val ACTION_NEXT = "com.yuriy.openradio.widget.NEXT"

        fun refresh(context: Context, state: NowPlaying.State) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, RadioWidgetProvider::class.java))
            if (ids.isEmpty()) {
                return
            }
            val views = build(context, state)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun build(context: Context, state: NowPlaying.State): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_radio)
            views.setTextViewText(
                R.id.widget_station,
                state.station.ifEmpty { context.getString(R.string.app_name) }
            )
            views.setTextViewText(R.id.widget_track, state.track)
            views.setViewVisibility(R.id.widget_track, if (state.track.isEmpty()) View.GONE else View.VISIBLE)
            views.setImageViewResource(
                R.id.widget_play,
                if (state.playing) R.drawable.ic_pause else R.drawable.ic_play_arrow
            )
            views.setOnClickPendingIntent(R.id.widget_play, broadcast(context, ACTION_TOGGLE, 1))
            views.setOnClickPendingIntent(R.id.widget_next, broadcast(context, ACTION_NEXT, 2))
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
                views.setOnClickPendingIntent(
                    R.id.widget_texts,
                    PendingIntent.getActivity(context, 3, launch, flags())
                )
            }
            return views
        }

        private fun broadcast(context: Context, action: String, code: Int): PendingIntent {
            val intent = Intent(context, RadioWidgetProvider::class.java).setAction(action)
            return PendingIntent.getBroadcast(context, code, intent, flags())
        }

        private fun flags(): Int {
            return PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        }
    }
}
