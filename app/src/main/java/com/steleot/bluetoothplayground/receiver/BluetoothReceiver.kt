package com.steleot.bluetoothplayground.receiver

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.steleot.bluetoothplayground.toDebugString
import timber.log.Timber

class BluetoothReceiver(
    private val callbacks: BluetoothReceiverCallbacks
) : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        when (intent.action) {
            BluetoothAdapter.ACTION_STATE_CHANGED -> bluetoothStateChanged(intent)
            BluetoothAdapter.ACTION_DISCOVERY_STARTED -> bluetoothDiscoveryStarted(intent)
            BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> bluetoothDiscoveryFinished(intent)
            BluetoothDevice.ACTION_FOUND -> bluetoothDeviceFound(intent)
        }
    }

    private fun bluetoothStateChanged(
        intent: Intent
    ) {
        val previousState = intent.getIntExtra(
            BluetoothAdapter.EXTRA_PREVIOUS_STATE,
            BluetoothAdapter.ERROR
        )
        val nextState = intent.getIntExtra(
            BluetoothAdapter.EXTRA_STATE,
            BluetoothAdapter.ERROR
        )
        Timber.d("previous state is : $previousState")
        Timber.d("next state is : $nextState")

        if (nextState == BluetoothAdapter.STATE_ON
            || nextState == BluetoothAdapter.STATE_TURNING_ON
        ) {
            Timber.d("Bluetooth enabled")
            callbacks.bluetoothStatus(true)
        } else if (nextState == BluetoothAdapter.STATE_OFF
            || nextState == BluetoothAdapter.STATE_TURNING_OFF
        ) {
            Timber.d("Bluetooth disabled")
            callbacks.bluetoothStatus(false)
        }
    }

    private fun bluetoothDiscoveryStarted(
        @Suppress("UNUSED_PARAMETER") intent: Intent
    ) {
        Timber.d("Bluetooth Discovery started")
        callbacks.bluetoothDiscoveryStatus(true)
    }

    private fun bluetoothDiscoveryFinished(
        @Suppress("UNUSED_PARAMETER") intent: Intent
    ) {
        Timber.d("Bluetooth Discovery finished")
        callbacks.bluetoothDiscoveryStatus(false)
    }

    private fun bluetoothDeviceFound(
        intent: Intent
    ) {
        val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as? BluetoothDevice
        device?.let {
            Timber.d(it.toDebugString())
        }
    }
}

interface BluetoothReceiverCallbacks {

    fun bluetoothStatus(isEnabled: Boolean)
    fun bluetoothDiscoveryStatus(isEnabled: Boolean)
}