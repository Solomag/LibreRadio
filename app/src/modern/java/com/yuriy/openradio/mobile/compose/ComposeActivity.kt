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

package com.yuriy.openradio.mobile.compose

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.yuriy.openradio.mobile.update.UpdateManager
import com.yuriy.openradio.shared.model.storage.LocationStorage
import com.yuriy.openradio.shared.service.location.LocationService
import android.content.Intent
import android.widget.Toast
import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommonUi
import java.lang.ref.WeakReference

/**
 * Main screen of the modern edition, written in Jetpack Compose.
 * Settings and "About" are Compose pages too (see [SettingsScreen], [AboutScreen]).
 */
class ComposeActivity : AppCompatActivity(), SettingsHost {

    private val mViewModel: RadioViewModel by viewModels()

    private val mLocationStorage by lazy { LocationStorage(WeakReference(applicationContext)) }

    /**
     * Receives the detected country from [LocationService].
     */
    private val mLocationMessenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what == LocationService.MSG_ID_ON_LOCATION) {
                val previous = mLocationStorage.getCountryCode()
                mLocationStorage.setCountryCode(LocationService.getCountryCode(msg))
                if (previous != mLocationStorage.getCountryCode() && mViewModel.isRoot) {
                    mViewModel.refresh()
                }
            }
        }
    })

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LibreRadioTheme {
                RadioApp(
                    vm = mViewModel,
                    host = this,
                    onMenu = ::onMenuAction,
                    // Back at the root only leaves the screen; playback goes on in the background.
                    onExit = ::finish
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        UpdateManager.onResume(this)
        if (mLocationStorage.isDetectionDue()) {
            val asked = LocationService.checkCountry(
                this, window.decorView, mLocationMessenger, mLocationStorage.isPermissionAsked().not()
            )
            if (asked) {
                mLocationStorage.setPermissionAsked()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        for (i in permissions.indices) {
            if (permissions[i] == Manifest.permission.ACCESS_COARSE_LOCATION &&
                grantResults.getOrNull(i) == PackageManager.PERMISSION_GRANTED
            ) {
                LocationService.doEnqueueWork(applicationContext, mLocationMessenger)
            }
        }
    }

    private fun onMenuAction(action: MenuAction) {
        when (action) {
            MenuAction.UPDATES -> UpdateManager.check(this, manual = true)
            MenuAction.CLOSE -> closeApp()
            // Settings and About are pages of RadioApp.
            else -> Unit
        }
    }

    override fun exportFavorites() {
        DependencyRegistryCommonUi.fileStoreManager().upload(this) { toast(com.yuriy.openradio.shared.R.string.failure) }
    }

    override fun importFavorites() {
        DependencyRegistryCommonUi.fileStoreManager().download(this) { toast(com.yuriy.openradio.shared.R.string.failure) }
    }

    override fun checkUpdates() {
        UpdateManager.check(this, manual = true)
    }

    override fun countryCode(): String = mLocationStorage.getCountryCode()

    override fun setCountryCode(code: String?) {
        if (code == null) {
            // "Use location": the fallback country now, the GPS one when it arrives.
            LocationService.checkCountry(this, window.decorView, mLocationMessenger, true)
            mLocationStorage.setCountryCode(LocationService.fallbackCountryCode(this))
        } else {
            mLocationStorage.setCountryCode(code)
        }
        mViewModel.refreshRoot()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        DependencyRegistryCommonUi.fileStoreManager().onActivityResult(
            applicationContext, requestCode, resultCode, data,
            {
                runOnUiThread {
                    toast(com.yuriy.openradio.shared.R.string.success)
                    mViewModel.refreshRoot()
                }
            },
            { runOnUiThread { toast(com.yuriy.openradio.shared.R.string.failure) } }
        )
    }

    private fun toast(resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    }

    /**
     * "Close": stop playback and the service, then leave.
     */
    private fun closeApp() {
        mViewModel.stopService()
        finish()
    }
}
