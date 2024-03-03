package com.populstay.wallet.devicemanager

import android.app.Activity
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.populstay.common.bean.CBleDevice

interface IDeviceManager {

    fun startScanAndCheckBTStatus(callBack: IScanResult, activity : Activity)
    fun startScan(callBack : IScanResult)
    fun stopScan()
    fun connect(macAddress: String, callback: BTGattCallback)
    fun disconnect()
    fun discoverServices()

    fun clearBleDeviceList()
}

// 蓝牙开关和权限相关
interface IBTListener{
    // 蓝牙请求打开ok
    fun onBTRequestOpen()
    // 蓝牙相关权限请求ok
    fun onBTRequestPermissions()
    // onServicesDiscovered
    fun onFoundService()
    fun onConnectionFail()
    fun onCharacteristicChanged(data : ByteArray)

}

// 包装ScanCallback
interface IScanResult{
    fun onScanStatus(state :Int)
    fun onScanResult(deviceList : List<CBleDevice>?)
}

// 包装BluetoothGattCallback
interface BTGattCallback{
    fun onPhyUpdate(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
    }

    fun onPhyRead(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
    }

    fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
    }

    fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
    }


    @Deprecated("")
    fun onCharacteristicRead(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
    }

    fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ) {
    }

    fun onCharacteristicWrite(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
    }


    @Deprecated("")
    fun onCharacteristicChanged(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?
    ) {
    }

    fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
    }


    @Deprecated("")
    fun onDescriptorRead(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
    }

    fun onDescriptorRead(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int,
        value: ByteArray
    ) {
    }

    fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
    }

    fun onReliableWriteCompleted(gatt: BluetoothGatt?, status: Int) {
    }

    fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
    }

    fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
    }

    fun onServiceChanged(gatt: BluetoothGatt) {
    }
}