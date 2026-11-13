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

package com.yuriy.openradio.shared.service.player

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/**
 * Live radio: ride out short network drops and stalls instead of stopping (4PDA report:
 * "often stalls and buffers regardless of the connection").
 * A dead station (403 / 404 / 410) still fails at once.
 */
@UnstableApi
class StreamLoadErrorPolicy : DefaultLoadErrorHandlingPolicy() {

    override fun getMinimumLoadableRetryCount(dataType: Int): Int = RETRIES

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val error = loadErrorInfo.exception
        if (error is HttpDataSource.InvalidResponseCodeException &&
            (error.responseCode == 403 || error.responseCode == 404 || error.responseCode == 410)
        ) {
            return C.TIME_UNSET
        }
        return minOf(loadErrorInfo.errorCount * 1000L, MAX_DELAY_MS)
    }

    companion object {
        private const val RETRIES = 12
        private const val MAX_DELAY_MS = 5_000L
    }
}
