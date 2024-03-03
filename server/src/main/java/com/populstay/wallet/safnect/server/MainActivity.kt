package com.populstay.wallet.safnect.server

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseSettings
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.populstay.common.GlobalConstant
import com.populstay.common.bean.CPhone
import com.populstay.common.log.PeachLogger
import com.populstay.common.repository.BlueToothBLEUtil
import com.populstay.wallet.safnect.server.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import pub.devrel.easypermissions.EasyPermissions


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    //蓝牙广播回调类
    private lateinit var mAdvertiseCallback: advertiseCallback
    //GattServer回调
    private lateinit var mBluetoothGattServerCallback: BluetoothGattServerCallback
    private var mPhone = CPhone()
    private var mBluetoothDevice: BluetoothDevice? = null

    companion object{
        const val REQUEST_ENABLE_BT = 1
        const val TAG = "MainActivity"
    }

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        stopAdvertising()
    }

    private fun requestPermission() {
       startGetPermissions(this)
    }

    private fun startGetPermissions(activity : Activity) {
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
                        activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                    })
                builder.setNegativeButton(R.string.no, DialogInterface.OnClickListener { _, _ ->
                    // 用户选择不打开蓝牙
                })
                builder.show()
            }else{
              // ok
              // 接下来的逻辑写这里
                onReady()
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
               // OK
                requestPermission()

            } else {
                // 用户未能成功打开蓝牙
                Toast.makeText(this@MainActivity, resources.getString(R.string.refused_turn_on_bluetooth), Toast.LENGTH_SHORT).show()
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
            // ok
            requestPermission()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setStatusBarTextColor()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        requestPermission()
        binding.actionBtn.setOnClickListener {
            responseDkg()
        }
    }

    private fun showMsg(msg :String){
        binding.showMsgTv.text = msg
    }

    private fun responseDkg(){
        lifecycleScope.launch {

            // 发送消息分发私钥
            val characteristic = BlueToothBLEUtil.getBluetoothGattService()?.getCharacteristic(
                BlueToothBLEUtil.getUUID(
                    BlueToothBLEUtil.BLECHARACTERISTIC_COMMON_READ))

            val device = mBluetoothDevice
            device?.let { device ->
                characteristic?.let {
                    // 通知的监听，这一步很关键，不然外围设备端发送的数据将无法接收到。
                    if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0){
                        //BlueToothBLEUtil.setCharacteristicNotify(characteristic , true)
                    }

                    launch {
                        // 02 标识同意DKG请求
                        BlueToothBLEUtil.notifyCharacteristicChangedSplit(
                            device,
                            characteristic,
                            "02".toByteArray()
                        )
                    }
                }
            }
        }

    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setStatusBarTextColor()
    }

    private fun setStatusBarTextColor(){
        //设置字体白色
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    private fun onReady() {
        // 初始化蓝牙相关回调
        initBluetooth()
        // 添加服务
        addGattServer()
        // 开始广播
        startAdvertising()
    }

    private fun initBluetooth() {
        //初始化蓝牙回调包
        mAdvertiseCallback = advertiseCallback()
        //初始化GattServer回调
        mBluetoothGattServerCallback = bluetoothGattServerCallback()

        PeachLogger.d(GlobalConstant.APP_TAG, "$TAG ble mac address:${BlueToothBLEUtil.getAddress()},device = ${android.os.Build.DEVICE},model= ${android.os.Build.MODEL},manufacturer = ${android.os.Build.MANUFACTURER}")
    }

    private fun addGattServer(){
        try {
            BlueToothBLEUtil.addGattServer(mBluetoothGattServerCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startAdvertising(){
        try {
            if (!BlueToothBLEUtil.startAdvertising(
                    "${mPhone.manufacturer} ${mPhone.modelname}",
                    mAdvertiseCallback
                )
            ) {
                Toast.makeText(this,"该手机芯片不支持BLE广播",Toast.LENGTH_SHORT).show()
            }else{
                PeachLogger.d(GlobalConstant.APP_TAG, "$TAG 开始Ble广播")
            }
        } catch (e: Exception) {
           e.printStackTrace()
        }
    }

    private fun stopAdvertising(){
        try {
            if (BlueToothBLEUtil.stopAdvertising(mAdvertiseCallback)) {
                PeachLogger.d(GlobalConstant.APP_TAG, "$TAG 停止Ble广播")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 蓝牙广播回调类
     */
    private inner class advertiseCallback : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            val advertiseInfo = StringBuffer("启动BLE广播成功")
            // 连接性
            if (settingsInEffect.isConnectable) {
                advertiseInfo.append(", 可连接")
            } else {
                advertiseInfo.append(", 不可连接")
            }
            //广播时长
            if (settingsInEffect.timeout == 0) {
                advertiseInfo.append(", 持续广播")
            } else {
                advertiseInfo.append(", 广播时长 ${settingsInEffect.timeout} ms")
            }
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onStartSuccess $advertiseInfo")
        }

        //具体失败返回码可以到官网查看
        override fun onStartFailure(errorCode: Int) {
            var errInfo = if (errorCode == ADVERTISE_FAILED_DATA_TOO_LARGE) {
                "启动Ble广播失败 数据报文超出31字节"
            } else {
                "启动Ble广播失败"
            }
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onStartFailure BLE广播启动失败 errorCode = $errorCode, errInfo = $errInfo")
        }
    }

    /**
     * GattServer回调（蓝牙状态回调）
     */
    private inner class bluetoothGattServerCallback : BluetoothGattServerCallback() {

        //设备连接/断开连接回调
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onConnectionStateChange Server status = $status  newState = $newState")
            lifecycleScope.launch {
                var msg = ""
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    //连接成功
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        mBluetoothDevice = device
                        msg = "${device.address} 连接成功"
                        PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onConnectionStateChange 与${device.address} 连接成功")
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        msg = "${device.address} 断开连接"
                        PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onConnectionStateChange 与${device.address} 断开连接")
                    }

                } else {
                    msg = "onConnectionStateChange status = $status newState = $newState"
                    PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onConnectionStateChange 与${device.address} 连接失败")
                }
            }
        }

        override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
            super.onMtuChanged(device, mtu)
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onMtuChanged mtu = $mtu")
            lifecycleScope.launch {
                BlueToothBLEUtil.mtuSize = mtu
                val msg = "通讯的MTU值改为${BlueToothBLEUtil.mtuSize}"
            }
        }

        //添加本地服务回调
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            super.onServiceAdded(status, service)
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onServiceAdded status = $status,UUUID = ${service.uuid}")
            lifecycleScope.launch {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onServiceAdded 添加Gatt服务成功 UUUID = ${service.uuid}")

                } else {
                    PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onServiceAdded 添加Gatt服务失败 UUUID = ${service.uuid}")

                }
            }
        }

        //特征值读取回调
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onCharacteristicReadRequest requestId = $requestId")
            // 响应客户端
            if (!BlueToothBLEUtil.checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                return
            }
            BlueToothBLEUtil.sendResponse(
                device, requestId,
                offset, "我是服务端，你要提取数据给你了".toByteArray())

            lifecycleScope.launch {
                //回复客户端,让客户端读取该特征新赋予的值，获取由服务端发送的数据
                val readbytearray = "我是服务端，你要提取数据给你了".toByteArray()
                characteristic.value = readbytearray
                BlueToothBLEUtil.notifyCharacteristicChangedSplit(device,characteristic,readbytearray)
            }
        }

        //特征值写入回调（主端设备给副端设备传值，副端设备监听到接受数据）
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int,
            characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean,
            responseNeeded: Boolean, offset: Int, value: ByteArray
        ) {
            super.onCharacteristicWriteRequest(
                device,
                requestId,
                characteristic,
                preparedWrite,
                responseNeeded,
                offset,
                value
            )
            // 回调 onCharacteristicWriteRequest 函数时，需要调用下 mBtGattServer.sendResponse ，否则设备连接会莫名其妙断开。具体原因有待研究。
            BlueToothBLEUtil.sendResponse(device,requestId,offset,value)
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onCharacteristicWriteRequest 消息转发，副端接受到 requestId = $requestId,  value = ${value} ，len =${value.size},uuid= ${characteristic.uuid}")

            // 响应客户端
            if (!BlueToothBLEUtil.checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onCharacteristicWriteRequest not BLUETOOTH_CONNECT")
                return
            }
            dealCharacteristic(characteristic, device, value)
        }

        //描述读取回调
        override fun onDescriptorReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor)
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onDescriptorReadRequest requestId = $requestId")
            // 响应客户端
            if (!BlueToothBLEUtil.checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                return
            }
            BlueToothBLEUtil.sendResponse(
                device, requestId,
                offset, descriptor.value)

        }

        //描述写入回调
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean,
            offset: Int, value: ByteArray
        ) {
            super.onDescriptorWriteRequest(
                device,
                requestId,
                descriptor,
                preparedWrite,
                responseNeeded,
                offset,
                value
            )
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onDescriptorWriteRequest requestId = $requestId")
            //刷新描述值
            descriptor.value = value
            // 响应客户端
            if (!BlueToothBLEUtil.checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                return
            }
            BlueToothBLEUtil.sendResponse(
                device, requestId,
                offset, value)
        }

        override fun onNotificationSent(device: BluetoothDevice?, status: Int) {
            super.onNotificationSent(device, status)
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onNotificationSent status = $status")
        }
    }

    private fun dealCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        device: BluetoothDevice,
        value: ByteArray
    ) {

        // Common
        if (characteristic.uuid == BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_COMMON_WRITE)){
            val res = BlueToothBLEUtil.dealRecvByteArray(BlueToothBLEUtil.getDataTag(device.address, BlueToothBLEUtil.BLECHARACTERISTIC_COMMON_WRITE), value)
            if (res) {
                //获取接收完的数据
                val recvByteArray = BlueToothBLEUtil.getRecvByteArray(BlueToothBLEUtil.getDataTag(device.address, BlueToothBLEUtil.BLECHARACTERISTIC_COMMON_WRITE))
                val recvData = String(recvByteArray, Charsets.UTF_8)
                PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onCharacteristicWriteRequest 消息转发，副端接受到 recvData = $recvData,  value = ${value} ，len =${value.size},uuid= ${characteristic.uuid}")

                showMsg("主端发起了DKG请求：$recvData")
            }
        }
    }

}