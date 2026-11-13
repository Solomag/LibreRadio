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

package com.yuriy.openradio.shared.model.net

import java.util.Locale

/**
 * Search text with an optional country scope ("search among the stations of this country").
 * The scope travels inside the plain query string, the only search parameter of the media session.
 */
data class SearchQuery(val text: String, val countryCode: String?) {

    fun encode(): String {
        val code = countryCode ?: return text
        return "$PREFIX${code.uppercase(Locale.ROOT)}$SEPARATOR$text"
    }

    companion object {

        private const val PREFIX = "\u0001country:"
        private const val SEPARATOR = "\u0001"

        fun decode(value: String): SearchQuery {
            if (value.startsWith(PREFIX).not()) {
                return SearchQuery(value, null)
            }
            val rest = value.substring(PREFIX.length)
            val end = rest.indexOf(SEPARATOR)
            if (end <= 0) {
                return SearchQuery(value, null)
            }
            return SearchQuery(rest.substring(end + SEPARATOR.length), rest.substring(0, end))
        }
    }
}
