package com.steleot.bluetoothplayground

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

fun Context.isFineLocationPermissionGranted() = ContextCompat.checkSelfPermission(
    this,
    Manifest.permission.ACCESS_FINE_LOCATION
) == PackageManager.PERMISSION_GRANTED

fun BluetoothDevice.toDebugString() = """
            Bluetooth Device :
            name : ${this.name},
            alias : ${ if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) this.alias else "not available"}
            address : ${this.address},
            bluetoothClass : ${this.bluetoothClass},
            bondState : ${this.bondState},
            type : ${this.type},
            uuids : ${this.uuids?.joinToString { it.toString() }}
            """.trimIndent()

fun Context.isBleSupported() = !packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

fun Fragment.isBleSupported() = requireContext().isBleSupported()