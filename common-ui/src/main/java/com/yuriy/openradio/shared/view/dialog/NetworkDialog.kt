/*
 * Copyright 2021 The "Open Radio" Project. Author: Chernyshov Yuriy
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

package com.yuriy.openradio.shared.view.dialog

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.yuriy.openradio.shared.R
import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommonUi
import com.yuriy.openradio.shared.dependencies.ServiceCommanderDependency
import com.yuriy.openradio.shared.model.ServiceCommander
import com.yuriy.openradio.shared.model.net.ByeDpi
import com.yuriy.openradio.shared.model.net.NetProxy
import com.yuriy.openradio.shared.model.net.WhiteSni
import com.yuriy.openradio.shared.model.storage.NetworkSettingsStorage
import com.yuriy.openradio.shared.service.OpenRadioService
import com.yuriy.openradio.shared.utils.findCheckBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Network settings of the classic and TV UIs: mobile data, a proxy for all connections (SOCKS5, HTTP or the
 * built-in ByeDPI, see [NetProxy]) and the white server name (see [WhiteSni]).
 *
 * Created by Yuriy Chernyshov
 * At Android Studio
 * On 12/20/14
 * E-Mail: chernyshov.yuriy@gmail.com
 */
class NetworkDialog : BaseDialogFragment(), ServiceCommanderDependency {

    private lateinit var mNetworkSettingsStorage: NetworkSettingsStorage
    private lateinit var mServiceCommander: ServiceCommander
    private val mScope = MainScope()

    /** ByeDPI: the way being tried while picking (0: not picking). */
    private var mPicking = 0
    private var mTesting = false

    override fun configureWith(serviceCommander: ServiceCommander) {
        mServiceCommander = serviceCommander
    }

    fun configureWith(locationStorage: NetworkSettingsStorage) {
        mNetworkSettingsStorage = locationStorage
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        DependencyRegistryCommonUi.inject(this)
        DependencyRegistryCommonUi.injectServiceCommander(this)
        val view = inflater.inflate(
            R.layout.dialog_network,
            requireActivity().findViewById(R.id.dialog_network_root)
        )
        setWindowDimensions(view, 0.8f, 0.8f)
        val useMobileCheckBox = view.findCheckBox(R.id.use_mobile_network_check_box)
        useMobileCheckBox.isChecked = mNetworkSettingsStorage.getUseMobile()
        useMobileCheckBox.setOnCheckedChangeListener { _, isChecked ->
            mNetworkSettingsStorage.setUseMobile(isChecked)
            sendNetChanged()
        }
        setUpProxy(view)
        return createAlertDialog(view)
    }

    override fun onDestroy() {
        mScope.cancel()
        super.onDestroy()
    }

    private fun sendNetChanged() {
        CoroutineScope(Dispatchers.Main).launch {
            mServiceCommander.sendCommand(OpenRadioService.CMD_NET_CHANGED)
        }
    }

    private fun setUpProxy(view: View) {
        val context = requireContext()
        val initial = NetProxy.settings(context)
        val state = view.findViewById<TextView>(R.id.proxy_state_view)
        val use = view.findViewById<CheckBox>(R.id.proxy_use_check_box)
        val fields = view.findViewById<View>(R.id.proxy_fields)
        val group = view.findViewById<RadioGroup>(R.id.proxy_type_group)
        val dpiRadio = view.findViewById<RadioButton>(R.id.proxy_type_dpi)
        val serverFields = view.findViewById<View>(R.id.proxy_server_fields)
        val dpiFields = view.findViewById<View>(R.id.proxy_dpi_fields)
        val host = view.findViewById<EditText>(R.id.proxy_host_edit)
        val port = view.findViewById<EditText>(R.id.proxy_port_edit)
        val user = view.findViewById<EditText>(R.id.proxy_user_edit)
        val password = view.findViewById<EditText>(R.id.proxy_password_edit)
        val pick = view.findViewById<Button>(R.id.proxy_dpi_pick_button)
        val pickStatus = view.findViewById<TextView>(R.id.proxy_dpi_status_view)
        val dpiArgs = view.findViewById<EditText>(R.id.proxy_dpi_args_edit)
        val test = view.findViewById<Button>(R.id.proxy_test_button)
        val result = view.findViewById<TextView>(R.id.proxy_test_result_view)
        val sni = view.findViewById<CheckBox>(R.id.white_sni_check_box)
        val sniFields = view.findViewById<View>(R.id.white_sni_fields)
        val sniDomain = view.findViewById<EditText>(R.id.white_sni_domain_edit)
        val save = view.findViewById<Button>(R.id.network_save_button)
        val plainColor = state.textColors

        state.text = when {
            initial.isOn && initial.type == NetProxy.Type.BYEDPI -> getString(R.string.proxy_state_dpi)
            initial.isOn -> getString(
                R.string.proxy_state_on,
                if (initial.type == NetProxy.Type.SOCKS) "SOCKS5" else "HTTP", initial.host, initial.port
            )
            else -> getString(R.string.proxy_state_off)
        }

        fun type(): NetProxy.Type = when (group.checkedRadioButtonId) {
            R.id.proxy_type_http -> NetProxy.Type.HTTP
            R.id.proxy_type_dpi -> NetProxy.Type.BYEDPI
            else -> NetProxy.Type.SOCKS
        }

        fun current() = NetProxy.Settings(
            if (use.isChecked) type() else NetProxy.Type.OFF,
            host.text.toString().trim(), port.text.toString().trim().toIntOrNull() ?: 0,
            user.text.toString(), password.text.toString(), dpiArgs.text.toString().trim()
        )

        fun showResult(text: String?, good: Boolean) {
            result.visibility = if (text == null) View.GONE else View.VISIBLE
            result.text = text
            result.setTextColor(if (good) GOOD else BAD)
        }

        fun refresh() {
            fields.visibility = if (use.isChecked) View.VISIBLE else View.GONE
            val dpi = type() == NetProxy.Type.BYEDPI
            serverFields.visibility = if (dpi) View.GONE else View.VISIBLE
            dpiFields.visibility = if (dpi) View.VISIBLE else View.GONE
            pick.isEnabled = mPicking == 0 && !mTesting
            dpiArgs.isEnabled = mPicking == 0
            test.isEnabled = mPicking == 0 && !mTesting && current().isOn
            sniFields.visibility = if (sni.isChecked) View.VISIBLE else View.GONE
        }

        use.isChecked = initial.type != NetProxy.Type.OFF
        group.check(
            when (initial.type) {
                NetProxy.Type.HTTP -> R.id.proxy_type_http
                NetProxy.Type.BYEDPI -> R.id.proxy_type_dpi
                else -> R.id.proxy_type_socks
            }
        )
        if (!ByeDpi.isAvailable) {
            // Android 4: no ByeDPI.
            dpiRadio.visibility = View.GONE
            if (initial.type == NetProxy.Type.BYEDPI) {
                group.check(R.id.proxy_type_socks)
            }
        }
        host.setText(initial.host)
        port.setText(if (initial.port > 0) initial.port.toString() else "")
        user.setText(initial.user)
        password.setText(initial.password)
        dpiArgs.setText(initial.dpiArgsOrDefault)
        sni.isChecked = WhiteSni.isOn()
        sniDomain.setText(WhiteSni.domain())

        val changed = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                showResult(null, true)
                refresh()
            }
        }
        listOf(host, port, user, password, dpiArgs).forEach { it.addTextChangedListener(changed) }
        // A TV remote: up and down leave a field instead of getting stuck in it.
        listOf(host, port, user, password, dpiArgs, sniDomain).forEach { field ->
            field.setOnKeyListener { v, keyCode, event ->
                val direction = when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
                    KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
                    else -> return@setOnKeyListener false
                }
                if (event.action == KeyEvent.ACTION_DOWN) {
                    v.focusSearch(direction)?.requestFocus()
                }
                true
            }
        }
        use.setOnCheckedChangeListener { _, _ ->
            showResult(null, true)
            refresh()
        }
        group.setOnCheckedChangeListener { _, _ ->
            showResult(null, true)
            refresh()
        }
        sni.setOnCheckedChangeListener { _, _ -> refresh() }

        pick.setOnClickListener {
            showResult(null, true)
            mPicking = 1
            pickStatus.visibility = View.VISIBLE
            pickStatus.setTextColor(plainColor)
            pickStatus.text = getString(R.string.proxy_dpi_trying, 1, ByeDpi.PRESETS.size)
            refresh()
            mScope.launch {
                val found = withContext(Dispatchers.IO) {
                    NetProxy.pickDpi { n ->
                        mScope.launch {
                            pickStatus.text = getString(R.string.proxy_dpi_trying, n, ByeDpi.PRESETS.size)
                        }
                    }
                }
                mPicking = 0
                if (found != null) {
                    dpiArgs.setText(found)
                    pickStatus.text = getString(R.string.proxy_dpi_found, ByeDpi.PRESETS.indexOf(found) + 1)
                    pickStatus.setTextColor(GOOD)
                } else {
                    pickStatus.text = getString(R.string.proxy_dpi_none)
                    pickStatus.setTextColor(BAD)
                }
                refresh()
                // The button was off while picking: give the remote its place back.
                pick.requestFocus()
            }
        }

        test.setOnClickListener {
            mTesting = true
            showResult(null, true)
            refresh()
            val tried = current()
            mScope.launch {
                val outcome = withContext(Dispatchers.IO) { NetProxy.test(tried) }
                mTesting = false
                when (outcome) {
                    is NetProxy.TestResult.Ok -> showResult(
                        getString(R.string.proxy_test_ok, outcome.bytes / 1024, outcome.millis / 1000.0), true
                    )
                    is NetProxy.TestResult.CutOff ->
                        showResult(getString(R.string.proxy_test_cut, outcome.bytes / 1024), false)
                    is NetProxy.TestResult.ProxyFailed ->
                        showResult(getString(R.string.proxy_test_failed, outcome.reason), false)
                }
                refresh()
                test.requestFocus()
            }
        }

        save.setOnClickListener {
            NetProxy.save(context, current())
            WhiteSni.save(context, sni.isChecked, sniDomain.text.toString())
            sendNetChanged()
            dismissAllowingStateLoss()
        }

        refresh()
    }

    companion object {
        /**
         * Tag string to use in logging message.
         */
        private val CLASS_NAME = NetworkDialog::class.java.simpleName

        /**
         * Tag string to use in dialog transactions.
         */
        val DIALOG_TAG = CLASS_NAME + "_DIALOG_TAG"

        private val GOOD = Color.rgb(0x2E, 0x9D, 0x4F)
        private val BAD = Color.rgb(0xD3, 0x2F, 0x2F)
    }
}
