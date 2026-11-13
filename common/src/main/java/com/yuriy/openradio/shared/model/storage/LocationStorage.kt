/*
 * Copyright 2017-2021 The "Open Radio" Project. Author: Chernyshov Yuriy
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
import com.yuriy.openradio.shared.service.location.Country
import com.yuriy.openradio.shared.service.location.LocationService
import java.lang.ref.WeakReference

/**
 * [LocationStorage] is a class that provides access to data associated with  Location.
 */
class LocationStorage(private val mContextRef: WeakReference<Context>) : AbstractStorage(mContextRef, FILE_NAME) {

    /**
     * Country of the "local stations" entry: the last detected one, otherwise the mobile network /
     * SIM / system locale country (previously this silently defaulted to Canada).
     */
    fun getCountryCode(): String {
        val stored = getStringValue(PREFS_KEY_LAST_COUNTRY_CODE, "").uppercase(java.util.Locale.ROOT)
        if (stored.length == 2) {
            return stored
        }
        val context = mContextRef.get() ?: return Country.COUNTRY_CODE_DEFAULT
        return LocationService.fallbackCountryCode(context)
    }

    fun setCountryCode(value: String) {
        if (value.length != 2) {
            return
        }
        putStringValue(PREFS_KEY_LAST_COUNTRY_CODE, value.uppercase(java.util.Locale.ROOT))
        putLongValue(PREFS_KEY_DETECTED_AT, System.currentTimeMillis())
    }

    /**
     * Whether the country should be detected again (never detected, or more than 12 hours ago).
     */
    fun isDetectionDue(): Boolean {
        return System.currentTimeMillis() - getLongValue(PREFS_KEY_DETECTED_AT, 0L) > DETECTION_INTERVAL_MS
    }

    /**
     * The location permission is requested only once; without it the network / SIM country is used.
     */
    fun isPermissionAsked(): Boolean {
        return getBooleanValue(PREFS_KEY_PERMISSION_ASKED, false)
    }

    fun setPermissionAsked() {
        putBooleanValue(PREFS_KEY_PERMISSION_ASKED, true)
    }

    companion object {
        /**
         * Name of the Preferences.
         */
        private const val FILE_NAME = "LocationPref"
        private const val PREFS_KEY_LAST_COUNTRY_CODE = "PREFS_KEY_LAST_COUNTRY_CODE"
        private const val PREFS_KEY_DETECTED_AT = "PREFS_KEY_DETECTED_AT"
        private const val PREFS_KEY_PERMISSION_ASKED = "PREFS_KEY_PERMISSION_ASKED"
        private const val DETECTION_INTERVAL_MS = 12L * 60 * 60 * 1000
    }
}
