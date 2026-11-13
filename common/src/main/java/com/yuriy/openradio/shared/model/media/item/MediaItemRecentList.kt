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

package com.yuriy.openradio.shared.model.media.item

import com.yuriy.openradio.shared.model.media.item.MediaItemCommand.IUpdatePlaybackState
import com.yuriy.openradio.shared.utils.MediaItemBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The "Recent" folder: the last played stations, newest first.
 */
class MediaItemRecentList : MediaItemCommand {

    private var mJob: Job? = null

    override fun execute(
        playbackStateListener: IUpdatePlaybackState,
        dependencies: MediaItemCommandDependencies
    ) {
        mJob?.cancel()
        mJob = dependencies.mScope.launch(Dispatchers.IO) {
            withTimeoutOrNull(MediaItemCommand.CMD_TIMEOUT_MS) {
                val list = dependencies.presenter.getRecentStations()
                for (radioStation in list) {
                    dependencies.addMediaItem(
                        MediaItemBuilder.buildPlayable(
                            radioStation,
                            dependencies.presenter.isRadioStationFavorite(radioStation)
                        )
                    )
                }
                dependencies.resultListener.onResult(dependencies.getMediaItems(), LinkedHashSet(list))
            } ?: dependencies.resultListener.onResult()
        }
    }
}
