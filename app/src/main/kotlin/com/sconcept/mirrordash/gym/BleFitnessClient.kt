package com.sconcept.mirrordash.gym

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Standard BLE fitness protocols only: HRM, CSC and Fitness Machine. No vendor-private GATT. */
class BleFitnessClient(private val context: Context) {
    data class DiscoveredDevice(val address: String, val name: String, val rssi: Int)
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = manager?.adapter
    private var gatt: BluetoothGatt? = null
    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices = _devices.asStateFlow()
    private val _telemetry = MutableStateFlow<FitnessTelemetry?>(null)
    val telemetry = _telemetry.asStateFlow()
    private val _connection = MutableStateFlow(FitnessConnectionState.DISCONNECTED)
    val connection = _connection.asStateFlow()

    fun canUseBluetooth(): Boolean = adapter?.isEnabled == true && hasBluetoothPermission()

    fun startScan() {
        if (!canUseBluetooth()) return
        _connection.value = FitnessConnectionState.SCANNING
        adapter?.bluetoothLeScanner?.startScan(scanCallback)
    }

    fun stopScan() { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }

    fun connect(address: String) {
        if (!canUseBluetooth()) return
        stopScan(); _connection.value = FitnessConnectionState.CONNECTING
        gatt?.close()
        gatt = adapter?.getRemoteDevice(address)?.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() { gatt?.disconnect(); gatt?.close(); gatt = null; _connection.value = FitnessConnectionState.DISCONNECTED }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val entry = DiscoveredDevice(result.device.address, result.device.name ?: "BLE fitness device", result.rssi)
            _devices.value = (_devices.value.filterNot { it.address == entry.address } + entry).sortedByDescending { it.rssi }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothGatt.STATE_CONNECTED) g.discoverServices()
            else _connection.value = FitnessConnectionState.DISCONNECTED
        }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) { _connection.value = FitnessConnectionState.ERROR; return }
            listOf(HEART_RATE_MEASUREMENT, CSC_MEASUREMENT, FTMS_INDOOR_BIKE_DATA, FTMS_TREADMILL_DATA, FTMS_ROWER_DATA).forEach { uuid ->
                g.services.flatMap { it.characteristics }.firstOrNull { it.uuid == uuid }?.let { enableNotifications(g, it) }
            }
            _connection.value = FitnessConnectionState.READY
        }
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            _telemetry.value = BleFitnessDecoder.decode(characteristic.uuid, characteristic.value, _telemetry.value)
            _connection.value = FitnessConnectionState.ACTIVE
        }
    }

    private fun enableNotifications(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
        g.setCharacteristicNotification(c, true)
        c.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)?.let { descriptor ->
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            g.writeDescriptor(descriptor)
        }
    }

    private fun hasBluetoothPermission(): Boolean = if (Build.VERSION.SDK_INT >= 31) context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED && context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED else context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    companion object {
        private fun uuid(short: String) = UUID.fromString("0000$short-0000-1000-8000-00805f9b34fb")
        private val HEART_RATE_MEASUREMENT = uuid("2a37")
        private val CSC_MEASUREMENT = uuid("2a5b")
        private val FTMS_INDOOR_BIKE_DATA = uuid("2ad2")
        private val FTMS_TREADMILL_DATA = uuid("2acd")
        private val FTMS_ROWER_DATA = uuid("2ad1")
        private val CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}

object BleFitnessDecoder {
    fun decode(characteristic: UUID, bytes: ByteArray, previous: FitnessTelemetry?): FitnessTelemetry? {
        if (bytes.isEmpty()) return previous
        val now = System.currentTimeMillis()
        return when (characteristic.toString().substring(4, 8)) {
            "2a37" -> FitnessTelemetry(timestampEpochMs = now, heartRate = if (bytes[0].toInt() and 1 == 0) bytes.getOrNull(1)?.toInt()?.and(0xff) else u16(bytes, 1))
            "2a5b" -> previous?.copy(timestampEpochMs = now) // CSC revolution counters require delta/time state; retained until paired-session accumulator lands.
            "2ad2" -> previous?.copy(timestampEpochMs = now, speedKph = u16(bytes, 2) / 100.0, cadenceRpm = u16(bytes, 4) / 2.0, powerWatts = u16(bytes, 6).toDouble())
            else -> previous?.copy(timestampEpochMs = now)
        }
    }
    private fun u16(bytes: ByteArray, offset: Int): Int = (bytes.getOrNull(offset)?.toInt()?.and(0xff) ?: 0) or ((bytes.getOrNull(offset + 1)?.toInt()?.and(0xff) ?: 0) shl 8)
}
