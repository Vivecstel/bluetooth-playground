package com.steleot.bluetoothplayground.ui

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.steleot.bluetoothplayground.BuildConfig
import com.steleot.bluetoothplayground.databinding.FragmentMainBinding
import com.steleot.bluetoothplayground.isFineLocationPermissionGranted
import com.steleot.bluetoothplayground.toDebugString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*

private const val HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb"

class MainFragment : Fragment() {

    private val UUID_HEART_RATE_MEASUREMENT = UUID.fromString(HEART_RATE_MEASUREMENT)

    companion object {
        fun newInstance() = MainFragment()
    }

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private lateinit var binding: FragmentMainBinding
    private var isBleScanning = false
    private val bleDevices = mutableListOf<BluetoothDevice>()
    private var bluetoothGatt: BluetoothGatt? = null

    private val enableBluetooth = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            Timber.d("Bluetooth was enabled")
            renameBluetoothToggle(true)
        } else {
            Timber.d("Bluetooth was denied")
        }
    }

    private val requestLocationPermissionForDiscovery = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        requestLocationCommon(result) {
            handleDiscovery(bluetoothAdapter!!)
        }
    }

    private val requestLocationPermissionForBleScanning = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        requestLocationCommon(result) {
            handleDiscovery(bluetoothAdapter!!)
        }
    }

    private fun requestLocationCommon(
        result: Boolean,
        function: () -> Unit
    ) {
        if (result) {
            Timber.d("Permission granted")
            function()
        } else {
            Timber.d("Permission not granted")
            Toast.makeText(
                requireContext(),
                "Needs Location permission to start discovering",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val scanCallback = object : ScanCallback() {

        override fun onScanResult(
            callbackType: Int,
            result: ScanResult
        ) {
            val device = result.device
            if (!bleDevices.contains(device)) {
                bleDevices.add(device)
                Timber.d(device.toDebugString())
            }
        }

        override fun onBatchScanResults(
            results: MutableList<ScanResult>?
        ) {
            Timber.d("Batch results received")
        }

        override fun onScanFailed(
            errorCode: Int
        ) {
            Timber.d("Scan failed with code : $errorCode")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        if (bluetoothAdapter == null) {
            Timber.d("Bluetooth is not available on device")
            Toast.makeText(
                requireContext(), "This device doesn't support Bluetooth", Toast.LENGTH_LONG
            ).show()
            binding.bluetoothToggle.isEnabled = false
            binding.bluetoothStartDiscovery.isEnabled = false
        } else {
            initBluetoothToggle(bluetoothAdapter)
            initBluetoothStartDiscovery(bluetoothAdapter)
            initBluetoothBondedDevices(bluetoothAdapter)
            initBluetoothScanBleDevices(bluetoothAdapter)
            initBluetoothConnectToBleDevice()
        }
    }

    private fun initBluetoothToggle(
        bluetoothAdapter: BluetoothAdapter
    ) {
        renameBluetoothToggle(bluetoothAdapter.isEnabled)
        binding.bluetoothToggle.setOnClickListener {
            checkBluetooth(
                bluetoothAdapter,
                {
                    Timber.d("Disabling bluetooth")
                    val result = bluetoothAdapter.disable()
                    Timber.d("Result : $result")
                },
                {
                    Timber.d("Enabling bluetooth")
                    if (BuildConfig.USE_ALERT_DIALOG_BLUETOOTH) {
                        AlertDialog.Builder(requireContext())
                            .setTitle("Bluetooth Playground")
                            .setMessage("Bluetooth Playground is asking to turn on Bluetooth")
                            .setPositiveButton("Allow") { _, _ ->
                                Timber.d("Bluetooth was enabled")
                                bluetoothAdapter.enable()
                                renameBluetoothToggle(true)
                            }.setNegativeButton("Deny") { _, _ -> Timber.d("Bluetooth was denied") }
                            .show()
                    } else {
                        enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    }
                }
            )
        }
    }

    private fun checkBluetooth(
        bluetoothAdapter: BluetoothAdapter,
        enabledBlock: () -> Unit,
        disabledBlock: () -> Unit
    ) {
        if (bluetoothAdapter.isEnabled) {
            enabledBlock()
        } else {
            disabledBlock()
        }
    }

    private fun initBluetoothStartDiscovery(
        bluetoothAdapter: BluetoothAdapter
    ) {
        renameBluetoothStartDiscovery(bluetoothAdapter.isDiscovering)
        binding.bluetoothStartDiscovery.setOnClickListener {
            handleChecksBeforeScan(bluetoothAdapter, requestLocationPermissionForDiscovery) {
                handleDiscovery(bluetoothAdapter)
            }
        }
    }

    private fun handleChecksBeforeScan(
        bluetoothAdapter: BluetoothAdapter,
        launcher: ActivityResultLauncher<String>,
        function: () -> Unit
    ) {
        checkBluetooth(
            bluetoothAdapter,
            {
                if (requireContext().isFineLocationPermissionGranted()) {
                    function()
                } else {
                    launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            },
            {
                Timber.d("Bluetooth is not enabled to start discovery for devices")
                Toast.makeText(
                    requireContext(), "Enable Bluetooth first", Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    private fun handleDiscovery(
        bluetoothAdapter: BluetoothAdapter
    ) {
        if (bluetoothAdapter.isDiscovering) {
            Timber.d("Cancelling bluetooth Discovery")
            val result = bluetoothAdapter.cancelDiscovery()
            Timber.d("Result : $result")
        } else {
            Timber.d("Starting bluetooth Discovery")
            val boolean = bluetoothAdapter.startDiscovery()
            Timber.d("Result : $boolean")
        }
    }

    private fun initBluetoothBondedDevices(
        bluetoothAdapter: BluetoothAdapter
    ) {
        binding.bluetoothGetBondedDevices.setOnClickListener {
            checkBluetooth(
                bluetoothAdapter,
                {
                    bluetoothAdapter.bondedDevices.forEach {
                        Timber.d(it.toDebugString())
                    }
                },
                {
                    Timber.d("Bluetooth is not enabled in order to list bonded devices")
                    Toast.makeText(
                        requireContext(), "Enable Bluetooth first", Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    private fun initBluetoothScanBleDevices(
        bluetoothAdapter: BluetoothAdapter
    ) {
        binding.bluetoothScanBleDevices.setOnClickListener {
            handleChecksBeforeScan(bluetoothAdapter, requestLocationPermissionForBleScanning) {
                handleBleScanning(bluetoothAdapter)
            }
        }
    }

    private fun handleBleScanning(
        bluetoothAdapter: BluetoothAdapter
    ) {
        lifecycleScope.launch {
            val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
            if (isBleScanning) {
                stopScanning(bluetoothLeScanner)
            } else {
                launch {
                    Timber.d("Starting to scan for BLE devices")
                    isBleScanning = true
                    bleDevices.clear()
                    bluetoothLeScanner.startScan(scanCallback)
                    renameBluetoothScanBleDevices(true)
                }
                launch(Dispatchers.IO) {
                    delay(5_000L)
                    stopScanning(bluetoothLeScanner)
                }
            }
        }
    }

    private fun stopScanning(
        bluetoothLeScanner: BluetoothLeScanner
    ) {
        Timber.d("Stopping to scan for BLE devices")
        isBleScanning = false
        bluetoothLeScanner.stopScan(scanCallback)
        renameBluetoothScanBleDevices(false)
    }

    private fun renameBluetoothScanBleDevices(
        isEnabled: Boolean
    ) {
        binding.bluetoothScanBleDevices.text =
            if (isEnabled) "Stop Scanning BLE" else "Start Scanning BLE"
    }

    fun renameBluetoothStartDiscovery(
        isEnabled: Boolean
    ) {
        binding.bluetoothStartDiscovery.text =
            if (isEnabled) "Cancel Discovery" else "Start Discovery"
    }

    fun renameBluetoothToggle(
        isEnabled: Boolean
    ) {
        binding.bluetoothToggle.text = if (isEnabled) "Disable Bluetooth" else "Enable Bluetooth"
    }

    private fun initBluetoothConnectToBleDevice() {
        binding.bluetoothConnectToBleDevice.setOnClickListener {
            if (bleDevices.isEmpty()) {
                Toast.makeText(
                    requireContext(), "Try scanning for Ble devices first", Toast.LENGTH_LONG
                ).show()
            } else {
                Timber.d("Attempting to connect to a random ble device")
                connectToBle(bleDevices.random())
            }
        }
    }

    private fun connectToBle(
        device: BluetoothDevice
    ) {
        Timber.d("Ble Device name : ${device.name} and address : ${device.address}")
        bluetoothGatt = device.connectGatt(
            requireContext(),
            false,
            object : BluetoothGattCallback() {

                override fun onConnectionStateChange(
                    gatt: BluetoothGatt,
                    status: Int,
                    newState: Int
                ) {
                    Timber.d("status is : $status")
                    Timber.d("next state is : $newState")
                    if (newState == BluetoothGatt.STATE_CONNECTED) {
                        Timber.d("Device connected")
                        gatt.discoverServices()
                    } else {
                        Timber.d("Device not connected")
                    }
                }

                override fun onServicesDiscovered(
                    gatt: BluetoothGatt,
                    status: Int
                ) {
                    gatt.services?.forEach { service ->
                        Timber.d("Uuid of gatt service : ${service.uuid}")
                        service.characteristics.forEach { characteristic ->
                            Timber.d("Uuid of gatt characteristic : ${characteristic.uuid}")
                            characteristic.descriptors.forEach { descriptor ->
                                Timber.d("Uuid of descriptor : ${descriptor.uuid}")
                                gatt.setCharacteristicNotification(characteristic, true)
                                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                gatt.writeDescriptor(descriptor)
                            }
                        }
                    }
                }

                override fun onCharacteristicRead(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int
                ) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        handleCharacteristic(characteristic)
                    }
                }

                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic
                ) {
                    handleCharacteristic(characteristic)
                }

                private fun handleCharacteristic(
                    characteristic: BluetoothGattCharacteristic
                ) {
                    if (UUID_HEART_RATE_MEASUREMENT == characteristic.uuid) {
                        val flag = characteristic.properties
                        val format: Int
                        if (flag and 0x01 != 0) {
                            format = BluetoothGattCharacteristic.FORMAT_UINT16
                            Timber.d("Heart rate format UINT16.")
                        } else {
                            format = BluetoothGattCharacteristic.FORMAT_UINT8
                            Timber.d("Heart rate format UINT8.")
                        }
                        val heartRate = characteristic.getIntValue(format, 1)
                        Timber.d("Received heart rate : $heartRate")
                    } else {
                        val data = characteristic.value
                        if (data != null && data.isNotEmpty()) {
                            val stringBuilder = StringBuilder(data.size)
                            for (byteChar in data)
                                stringBuilder.append(String.format("%02X ", byteChar))
                            Timber.d("Value : $stringBuilder")
                        }
                    }
                }
            })
        lifecycleScope.launch {
            launch(Dispatchers.IO) {
                delay(10_000L)
                closeGattConnection()
            }
        }
    }

    private fun closeGattConnection() {
        bluetoothGatt?.let {
            it.disconnect()
            it.close()
        }
        bluetoothGatt = null
        Timber.d("Closed ble device connection")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScanning(bluetoothAdapter!!.bluetoothLeScanner)
        closeGattConnection()
    }
}