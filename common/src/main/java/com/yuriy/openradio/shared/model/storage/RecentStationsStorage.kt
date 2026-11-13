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

package com.yuriy.openradio.shared.model.storage

import android.content.Context
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.media.isInvalid
import java.lang.ref.WeakReference

/**
 * The last played stations, newest first (the "Recent" folder).
 */
class RecentStationsStorage(contextRef: WeakReference<Context>) : AbstractRadioStationsStorage(contextRef, FILE_NAME) {

    /**
     * Id of the newest entry, to skip work on repeated playback state changes of the same station.
     */
    private var mTopId: String? = null

    /**
     * @return True if the list changed.
     */
    @Synchronized
    fun push(radioStation: RadioStation): Boolean {
        if (radioStation.isInvalid()) {
            return false
        }
        val copy = RadioStation.makeCopyInstance(radioStation)
        copy.id = MediaId.normalizeFromSearchId(copy.id)
        if (mTopId == copy.id) {
            return false
        }
        val list = getAll().filter { it.id != copy.id }.toMutableList()
        list.add(0, copy)
        clear()
        list.take(MAX).forEachIndexed { i, station ->
            station.sortId = i
            add(station, createKeyForRadioStation(station))
        }
        mTopId = copy.id
        return true
    }

    companion object {
        private const val FILE_NAME = "RecentRadioStations"
        private const val MAX = 15
    }
}
