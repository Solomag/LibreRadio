/*
 * Copyright 2023 The "Open Radio" Project. Author: Chernyshov Yuriy
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

import android.app.Activity

/**
 * Cloud storage used the original author's Firebase project, which was shut down in January 2024.
 * This is a no-op replacement that keeps the existing UI code compiling: no user is ever signed in
 * and every remote operation fails. Use file export / import of Favorites instead.
 */
class CloudStoreManager {

    fun isUserExist(): Boolean = false

    fun getUserEmail(): String = "no email found"

    fun signOut() {}

    fun deleteAccount(
        onSuccess: () -> Unit,
        onFailure: () -> Unit,
        onInternalError: () -> Unit,
        onRecentLoginRequired: () -> Unit
    ) {
        onFailure()
    }

    fun sendPasswordReset(
        email: String,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        onFailure()
    }

    fun getToken(
        onSuccess: (token: String) -> Unit,
        onFailure: (msg: String) -> Unit
    ) {
        onFailure(UNAVAILABLE)
    }

    fun signIn(
        activity: Activity,
        email: String,
        password: String,
        onSuccess: (token: String) -> Unit,
        onFailure: (msg: String) -> Unit
    ) {
        onFailure(UNAVAILABLE)
    }

    fun download(
        token: String,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        onFailure()
    }

    companion object {
        private const val UNAVAILABLE = "Cloud storage is no longer available. Use file export / import."
    }
}
