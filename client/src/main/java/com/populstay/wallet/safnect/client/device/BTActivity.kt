package com.populstay.wallet.safnect.client.device

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import com.populstay.common.GlobalConstant
import com.populstay.common.bean.CBleDevice
import com.populstay.common.log.PeachLogger
import com.populstay.common.repository.BlueToothBLEUtil
import com.populstay.wallet.devicemanager.BTGattCallback
import com.populstay.wallet.devicemanager.DeviceConstant
import com.populstay.wallet.devicemanager.IBTListener
import com.populstay.wallet.devicemanager.IScanResult
import com.populstay.wallet.safnect.client.R
import com.populstay.wallet.safnect.client.base.BaseActivity
import com.populstay.wallet.safnect.client.devicemanager.DeviceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

abstract class BTActivity : BaseActivity(),IScanResult, BTGattCallback , IBTListener , CoroutineScope by MainScope() {
    
    companion object{
        const val TAG = "BTActivity-->"
    }
    protected var mCurSelectDevice : CBleDevice? = null
    protected var mBluetoothGattService : BluetoothGattService? = null

    protected fun connect(){
        mCurSelectDevice?.device?.address?.let { address->
            // todo 特别的大坑，重复连接，导致蓝牙数据回传方法多次回调onCharacteristicChanged
            // todo  要先断开连接，才能二次连接，各种怀疑哎，kotlin协程问题、蓝牙传输问题。。。。。。
            DeviceManager.disconnect()
            DeviceManager.connect(address,this)
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    protected fun startScan(){
        DeviceManager.startScanAndCheckBTStatus(this,this)
    }

    protected fun stopScan(){
        DeviceManager.stopScan()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    var onConnectionStateChangeJob: Job? = null
    // 设备连接状态
    override fun onConnectionStateChange(
        gatt: BluetoothGatt?,
        status: Int,
        newState: Int
    ) {
        super.onConnectionStateChange(gatt, status, newState)
        val address = gatt?.device?.address
        PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onConnectionStateChange Client status = $status  newState = $newState address= $address")
        onConnectionStateChangeJob?.cancel()
        onConnectionStateChangeJob?.cancelChildren()
        onConnectionStateChangeJob = lifecycleScope.launch {
            if (!BlueToothBLEUtil.checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                return@launch
            }
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    BlueToothBLEUtil.refreshDeviceCache()
                    //连接状态
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            // 连接成功
                            gatt?.let {
                                PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onConnectionStateChange Client 连接ok,开始发现服务 address= $address")
                                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED)
                                // 去发现服务
                                DeviceManager.discoverServices()
                            }
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            // 连接断开
                            gatt?.let {
                                // 重连
                                gatt.connect()
                                PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onConnectionStateChange Client 开始重连 address= $address")
                            }
                        }
                        else -> {
                            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onConnectionStateChange Client 断开了 address= $address")
                            // 断开了
                            gatt?.disconnect()
                        }
                    }
                }
                else ->{
                    PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onConnectionStateChange Client 连接失败了 address= $address")
                    onConnectionFail()
                }
            }
        }
    }

    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
        super.onMtuChanged(gatt, mtu, status)
        PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onMtuChanged Client mtu = $mtu")
    }

    var onServicesDiscoveredJob: Job? = null
    // 发现服务
    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        super.onServicesDiscovered(gatt, status)
        onServicesDiscoveredJob?.cancel()
        onServicesDiscoveredJob?.cancelChildren()
        onServicesDiscoveredJob = lifecycleScope.launch {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    gatt?.let { gatt ->
                        PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onServicesDiscovered Client status = $status 发现服务")
                        gatt.getService(BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLESERVER))?.let { service ->
                            mBluetoothGattService = service
                            val characteristicCommonRead = mBluetoothGattService?.getCharacteristic(
                                BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_COMMON_READ))
                            characteristicCommonRead?.let {
                                // 通知的监听，这一步很关键，不然外围设备端发送的数据将无法接收到。
                                if (characteristicCommonRead.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0){
                                    BlueToothBLEUtil.setCharacteristicNotify(characteristicCommonRead , true)
                                }
                            }
                        }
                    }

                    launch {
                        //连接成功后设置MTU通讯
                        BlueToothBLEUtil.requestMTU(500)
                    }

                    // 回调给子类处理
                    onFoundService()
                }
                else -> {
                    PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onServicesDiscovered Client 发现服务失败")
                }
            }
        }
    }

    // 特征值变化，副端向主端传值，主端监听到数据
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?
    ) {
        super.onCharacteristicChanged(gatt, characteristic)
        lifecycleScope.launch {

            characteristic?.let {
                characteristic?.value?.let {
                    if (characteristic.uuid == BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_COMMON_READ)){
                        val res = gatt?.device?.address?.let { address ->
                            BlueToothBLEUtil.getDataTag(address, BlueToothBLEUtil.BLECHARACTERISTIC_COMMON_READ)
                        }?.let { tag -> BlueToothBLEUtil.dealRecvByteArray(tag, it) }
                        try {
                            //接收完毕后进行数据处理
                            if(res == true) {
                                //获取接收完的数据
                                val recvByteArray = BlueToothBLEUtil.getRecvByteArray( BlueToothBLEUtil.getDataTag(gatt.device.address,
                                    BlueToothBLEUtil.BLECHARACTERISTIC_COMMON_READ))
                                PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onCharacteristicWriteRequest recvByteArray= ${recvByteArray.toString()}")
                                onCharacteristicChanged(recvByteArray)
                            }
                        }catch (e : Exception){
                            e.printStackTrace()
                            Log.e(GlobalConstant.APP_TAG, "$TAG onCharacteristicWriteRequest common msg= e = ${e.message}")
                        }
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DeviceConstant.REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                onBTRequestOpen()
            } else {
                // 用户未能成功打开蓝牙
                Toast.makeText(this@BTActivity, resources.getString(R.string.refused_turn_on_bluetooth), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (BlueToothBLEUtil.REQUEST_CODE_PERMISSIONS == requestCode) {
            for (x in grantResults) {
                if (x == PackageManager.PERMISSION_DENIED) {
                    //权限拒绝了
                    return
                }
            }
            onBTRequestPermissions()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        stopScan()
        DeviceManager.clearBleDeviceList()
    }
}