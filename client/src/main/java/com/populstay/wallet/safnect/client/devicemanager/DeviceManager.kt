package com.populstay.wallet.safnect.client.devicemanager

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.text.TextUtils
import android.util.Log
import androidx.annotation.RequiresApi
import com.populstay.common.bean.BTBean
import com.populstay.wallet.devicemanager.BTGattCallback
import com.populstay.wallet.devicemanager.DeviceConstant
import com.populstay.wallet.devicemanager.IDeviceManager
import com.populstay.wallet.devicemanager.IScanResult
import com.populstay.common.GlobalConstant
import com.populstay.wallet.safnect.client.R
import com.populstay.common.bean.CBleDevice
import com.populstay.common.repository.BlueToothBLEUtil
import pub.devrel.easypermissions.EasyPermissions

object DeviceManager : IDeviceManager {

    const val TAG = "DeviceManager-->"

    private val mBleDeviceList = mutableListOf<CBleDevice>()
    var mScanResult : IScanResult? = null

    var mBluetoothGattCallback : BTGattCallback? = null


    @RequiresApi(Build.VERSION_CODES.M)
    override fun startScanAndCheckBTStatus(callBack: IScanResult, activity : Activity) {
        Log.d(GlobalConstant.APP_TAG, "$TAG checkBTStatus")
        if (EasyPermissions.hasPermissions(activity, *(if (BlueToothBLEUtil.isAndroid12()) BlueToothBLEUtil.REQUIRED_BLEPERMISSIONS_12 else BlueToothBLEUtil.REQUIRED_BLEPERMISSIONS))) {
            if (!BlueToothBLEUtil.isEnabled()) {
                val builder: AlertDialog.Builder = AlertDialog.Builder(activity)
                builder.setTitle(R.string.hint)
                builder.setMessage(R.string.bluetooth_turned_on_hint)
                builder.setPositiveButton(
                    R.string.yes,
                    DialogInterface.OnClickListener { _, _ ->
                        if(!BlueToothBLEUtil.checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) return@OnClickListener

                        // 打开手机蓝牙开关
                        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        activity.startActivityForResult(enableBtIntent,
                            DeviceConstant.REQUEST_ENABLE_BT
                        )
                    })
                builder.setNegativeButton(R.string.no, DialogInterface.OnClickListener { _, _ ->
                    // 用户选择不打开蓝牙
                })
                builder.show()
            }else{
                startScan(callBack)
            }
        } else {
            // 如果没有上述权限 , 那么申请权限
            EasyPermissions.requestPermissions(
                activity,
                activity.resources.getString(R.string.rationale_hint),
                BlueToothBLEUtil.REQUEST_CODE_PERMISSIONS,
                *(if (BlueToothBLEUtil.isAndroid12()) BlueToothBLEUtil.REQUIRED_BLEPERMISSIONS_12 else BlueToothBLEUtil.REQUIRED_BLEPERMISSIONS)
            )
        }
    }

    override fun clearBleDeviceList() {
        mBleDeviceList.clear()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun startScan(callBack: IScanResult) {
        Log.d(GlobalConstant.APP_TAG, "$TAG startScan")
        // 重新扫描，要把上一次的扫描设备清理掉
        mBleDeviceList.clear()
        mScanResult = callBack
        mScanResult?.onScanStatus(DeviceConstant.SCAN_STATUS_START)
        BlueToothBLEUtil.scanBlueToothDevice(innerScanListener)
    }

    override fun stopScan() {
        Log.d(GlobalConstant.APP_TAG, "$TAG stopScan")
        BlueToothBLEUtil.stopScanBlueToothDevice(innerScanListener)
    }

    override fun connect(macAddress: String, callback: BTGattCallback) {
        Log.d(GlobalConstant.APP_TAG, "$TAG connect macAddress = $macAddress")
        stopScan()
        mBluetoothGattCallback = callback
        BlueToothBLEUtil.connect(macAddress, innerBluetoothGattCallback)
    }

    override fun disconnect() {
        Log.d(GlobalConstant.APP_TAG, "$TAG disconnect")
        BlueToothBLEUtil.disConnect()
    }

    override fun discoverServices() {
        BlueToothBLEUtil.discoverServices()
    }

    // 扫描回调
    private val innerScanListener = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            try {
                result?.let {
                    if(!BlueToothBLEUtil.checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) return@let
                    // 封装一下蓝牙设备数据
                    val item = CBleDevice()
                    item.device = result.device
                    item.rssi = result.rssi
                    item.scanRecordBytes = result.scanRecord?.bytes
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        item.isConnectable = result.isConnectable
                    }
                    item.scanRecord = result.scanRecord
                    item.type = BTBean.TYPE_DEVICE
                    item.name = result.device.name
                    if (TextUtils.isEmpty(result.device.name) || TextUtils.isEmpty(result.device.name.trim())){
                        item.name = "Wallet Device"
                    }

                    Log.d(GlobalConstant.APP_TAG, "$TAG onScanResult name = ${result.device.name},address = ${result.device.address}")

                    val idx = mBleDeviceList.indexOfFirst { idx ->
                        item.device?.address == idx.device?.address
                    }
                    if (idx < 0) {
                        mBleDeviceList.add(item)
                    }
                    mScanResult?.onScanStatus(DeviceConstant.SCAN_STATUS_END)
                    mScanResult?.onScanResult(mBleDeviceList)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.d(GlobalConstant.APP_TAG, "$TAG onScanResult e = ${e.message}")
            }
        }
    }

    // 连接、通信回调
    private val innerBluetoothGattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            mBluetoothGattCallback?.onConnectionStateChange(gatt, status, newState)
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            mBluetoothGattCallback?.onMtuChanged(gatt, mtu, status)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            mBluetoothGattCallback?.onServicesDiscovered(gatt, status)
        }

        @RequiresApi(Build.VERSION_CODES.S)
        override fun onServiceChanged(gatt: BluetoothGatt) {
            super.onServiceChanged(gatt)
            mBluetoothGattCallback?.onServiceChanged(gatt)
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)
            mBluetoothGattCallback?.onCharacteristicChanged(gatt, characteristic, value)
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, value, status)
            mBluetoothGattCallback?.onCharacteristicRead(gatt, characteristic, value, status)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            mBluetoothGattCallback?.onCharacteristicWrite(gatt, characteristic, status)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
           mBluetoothGattCallback?.onCharacteristicChanged(gatt, characteristic)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            mBluetoothGattCallback?.onCharacteristicRead(gatt, characteristic, status)
        }

        override fun onDescriptorRead(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorRead(gatt, descriptor, status)
            mBluetoothGattCallback?.onDescriptorRead(gatt, descriptor, status)
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        override fun onDescriptorRead(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
            value: ByteArray
        ) {
            super.onDescriptorRead(gatt, descriptor, status, value)
            mBluetoothGattCallback?.onDescriptorRead(gatt, descriptor, status, value)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
            mBluetoothGattCallback?.onDescriptorWrite(gatt, descriptor, status)
        }

        override fun onReliableWriteCompleted(gatt: BluetoothGatt?, status: Int) {
            super.onReliableWriteCompleted(gatt, status)
            mBluetoothGattCallback?.onReliableWriteCompleted(gatt, status)
        }

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onPhyRead(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
            super.onPhyRead(gatt, txPhy, rxPhy, status)
            mBluetoothGattCallback?.onPhyRead(gatt, txPhy, rxPhy, status)
        }

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onPhyUpdate(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
            super.onPhyUpdate(gatt, txPhy, rxPhy, status)
            mBluetoothGattCallback?.onPhyUpdate(gatt, txPhy, rxPhy, status)
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
            super.onReadRemoteRssi(gatt, rssi, status)
            mBluetoothGattCallback?.onReadRemoteRssi(gatt, rssi, status)
        }
    }
}