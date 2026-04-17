package com.aliothmoon.maameow.overlay.screensaver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.aliothmoon.maameow.manager.RemoteServiceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

class HardwareScreenOffManager(private val context: Context) {

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private var receiver: BroadcastReceiver? = null

    fun activate() {
        if (_active.value) return
        runCatching {
            RemoteServiceManager.getInstanceOrNull()?.setDisplayPower(false)
        }.onFailure {
            Timber.e(it, "Hardware screen off failed")
            return
        }
        _active.value = true
        registerScreenOnReceiver()
    }

    fun deactivate() {
        if (!_active.value) return
        unregisterReceiver()
        _active.value = false
        runCatching {
            RemoteServiceManager.getInstanceOrNull()?.setDisplayPower(true)
        }.onFailure {
            Timber.e(it, "Hardware screen restore failed")
        }
    }

    private fun registerScreenOnReceiver() {
        if (receiver != null) return
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_ON && _active.value) {
                    Timber.i("Screen restored by user, deactivating hardware screen off")
                    deactivate()
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_ON))
    }

    private fun unregisterReceiver() {
        receiver?.let {
            runCatching { context.unregisterReceiver(it) }
            receiver = null
        }
    }
}
