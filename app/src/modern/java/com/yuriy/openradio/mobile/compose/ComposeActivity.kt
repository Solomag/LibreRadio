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
import com.yuriy.openradio.shared.view.dialog.AboutDialog
import com.yuriy.openradio.shared.view.dialog.BaseDialogFragment
import com.yuriy.openradio.shared.view.dialog.EqualizerDialog
import com.yuriy.openradio.shared.view.dialog.FileStorageDialog
import com.yuriy.openradio.shared.view.dialog.GeneralSettingsDialog
import com.yuriy.openradio.shared.view.dialog.NetworkDialog
import com.yuriy.openradio.shared.view.dialog.SleepTimerDialog
import com.yuriy.openradio.shared.view.dialog.StreamBufferingDialog
import java.lang.ref.WeakReference

/**
 * Main screen of the modern edition, written in Jetpack Compose.
 * Settings still use the shared dialogs of the classic UI.
 */
class ComposeActivity : AppCompatActivity() {

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
                    onMenu = ::onMenuAction,
                    onExit = ::closeApp
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
            MenuAction.GENERAL -> showDialog(GeneralSettingsDialog::class.java.name, GeneralSettingsDialog.DIALOG_TAG)
            MenuAction.NETWORK -> showDialog(NetworkDialog::class.java.name, NetworkDialog.DIALOG_TAG)
            MenuAction.BUFFERING -> showDialog(StreamBufferingDialog::class.java.name, StreamBufferingDialog.DIALOG_TAG)
            MenuAction.SLEEP_TIMER -> showDialog(SleepTimerDialog::class.java.name, SleepTimerDialog.DIALOG_TAG)
            MenuAction.FILE_STORAGE -> showDialog(FileStorageDialog::class.java.name, FileStorageDialog.DIALOG_TAG)
            MenuAction.EQUALIZER -> showDialog(EqualizerDialog::class.java.name, EqualizerDialog.DIALOG_TAG)
            MenuAction.ABOUT -> showDialog(AboutDialog::class.java.name, AboutDialog.DIALOG_TAG)
            MenuAction.UPDATES -> UpdateManager.check(this, manual = true)
            MenuAction.CLOSE -> closeApp()
        }
    }

    private fun showDialog(className: String, tag: String) {
        if (supportFragmentManager.isStateSaved) {
            return
        }
        supportFragmentManager.findFragmentByTag(tag)?.let {
            supportFragmentManager.beginTransaction().remove(it).commitNow()
        }
        BaseDialogFragment.newInstance(className).show(supportFragmentManager, tag)
    }

    /**
     * "Close": stop playback and the service, then leave.
     */
    private fun closeApp() {
        mViewModel.stopService()
        finish()
    }
}
