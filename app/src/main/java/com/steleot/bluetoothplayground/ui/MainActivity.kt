package com.steleot.bluetoothplayground.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.steleot.bluetoothplayground.R
import com.steleot.bluetoothplayground.receiver.BluetoothReceiver
import com.steleot.bluetoothplayground.receiver.BluetoothReceiverCallbacks
import timber.log.Timber

private const val FRAGMENT_TAG = "MAIN_FRAGMENT"

class MainActivity : AppCompatActivity(), BluetoothReceiverCallbacks {

    private val bluetoothReceiver = BluetoothReceiver(this)
    private lateinit var mainFragment: MainFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        printAllAvailableSensors()
        if (savedInstanceState == null) {
            mainFragment = MainFragment.newInstance()
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.container, mainFragment, FRAGMENT_TAG)
                .commitNow()
        } else {
            mainFragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) as MainFragment
        }
    }

    private fun printAllAvailableSensors() {
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sensorManager?.let {
            val sensorList = it.getSensorList(Sensor.TYPE_ALL)
            sensorList.forEach { sensor ->
                Timber.d(sensor.name)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(bluetoothReceiver, IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_FOUND)
        })
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(bluetoothReceiver)
    }

    override fun bluetoothStatus(
        isEnabled: Boolean
    ) {
        mainFragment.renameBluetoothToggle(isEnabled)
    }

    override fun bluetoothDiscoveryStatus(
        isEnabled: Boolean
    ) {
        mainFragment.renameBluetoothStartDiscovery(isEnabled)
    }
}
