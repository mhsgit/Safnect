package com.populstay.common.repository

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.populstay.common.GlobalConstant
import com.populstay.common.log.PeachLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.lang.reflect.Method
import java.util.Hashtable
import java.util.UUID

object BlueToothBLEUtil {
    //服务 UUID
    const val BLESERVER = "2603"
    // 特征值的UUID
    //特征-消息 UUID 主端传消息给副端(通用简单消息)
    const val BLECHARACTERISTIC_COMMON_WRITE = "ca06"
    //特征-消息 UUID 副端传消息给主端(通用简单消息)
    const val BLECHARACTERISTIC_COMMON_READ = "ca07"
    //描述-消息 UUID 主端传消息给副端(通用简单消息)
    const val BLEDESCRIPTOR_COMMON_WRITE = "da06"
    //描述-消息 UUID 副端传消息给主端(通用简单消息)
    const val BLEDESCRIPTOR_COMMON_READ = "da07"

    //蓝牙相关权限
    const val REQUEST_CODE_PERMISSIONS = 10
    @RequiresApi(Build.VERSION_CODES.S)
    val REQUIRED_BLEPERMISSIONS_12 = arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN,
                                            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    val REQUIRED_BLEPERMISSIONS = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)

    //服务端接收的HashTable
    private var mHtRecvByteArray: Hashtable<String, Array<ByteArray?>> = Hashtable()

    private var mBluetoothManager: BluetoothManager? = null
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mBluetoothGattService: BluetoothGattService? = null
    private var mBluetoothGattServer: BluetoothGattServer? = null
    private var mBluetoothDevice: BluetoothDevice? = null
    private var mBluetoothGatt: BluetoothGatt? = null

    //BLE广播操作类
    private var mBluetoothLeAdvertiser: BluetoothLeAdvertiser? = null

    //是否初始化
    var hasInit = false

    lateinit var mApplication: Application

    //MTU传输值
    var mtuSize = 20


    //检测蓝牙权限
    fun checkBlueToothPermission(permissions: String = ""): Boolean {
        if (!hasInit) throw IOException("未初始化蓝牙BlueTooth！")
        // 非Android 12 不需要BLUETOOTH_CONNECT，只需要静态注册和申请定位权限
        if (!isAndroid12()){
            return true
        }
        if (permissions == "") return true
        return ActivityCompat.checkSelfPermission(
            mApplication.applicationContext,
            permissions
        ) == PackageManager.PERMISSION_GRANTED
    }


    fun init(application: Application): Boolean {
        if (hasInit) return true
        mApplication = application

        //初始化ble设配器
        mBluetoothManager =
            mApplication.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        //初始化适配器
        mBluetoothAdapter = mBluetoothManager?.adapter

        hasInit = true
        return hasInit
    }

    fun destory() {
        mBluetoothGatt = null
        mBluetoothDevice = null
        mBluetoothGattService = null
        mBluetoothAdapter = null
        hasInit = false
    }

    //获取UUID
    /*
    蓝牙技术联盟SIG定义UUID共用了一个基本的UUID：0x0000xxxx-0000-1000-8000-00805F9B34FB。
    总共128位，为了进一步简化基本UUID，每一个蓝牙技术联盟定义的属性有一个唯一的16位UUID，
    以代替上面的基本UUID的‘x’部分。使用16位的UUID便于记忆和操作
     */
    fun getUUID(baseuuid: String): UUID {
        return UUID.fromString("0000${baseuuid}-0000-1000-8000-00805f9b34fb")
    }

    fun getDataTag(address :String ,baseuuid: String) : String{
        return "${address}_${baseuuid}"
    }
    fun getAddress(): String?{
        return mBluetoothAdapter?.address
    }

    //广播时间(设置为0则持续广播)
    val Time = 0

    //是否在扫描中
    private var mScanning: Boolean = false

    //获取BluetoothManager
    fun getBluetoothManager(): BluetoothManager? {
        return if (checkBlueToothPermission()) {
            mBluetoothManager
        } else {
            null
        }
    }

    //获取BluetoothAdapter
    fun getBluetoothAdapter(): BluetoothAdapter? {
        return if (checkBlueToothPermission()) {
            mBluetoothAdapter
        } else {
            null
        }
    }
    //region 服务端外围设备相关函数
    /**
     * 添加Gatt 服务和特征
     * 广播是广播，只有添加Gatt服务和特征后，连接才有服务和特征用于数据交换
     */
    //获取Gatt服务
    fun getGattService(): BluetoothGattService {
        //初始化Service
        //创建服务，并初始化服务的UUID和服务类型。
        //BluetoothGattService.SERVICE_TYPE_PRIMARY 为主要服务类型
        val mGattService = BluetoothGattService(
            getUUID(BLESERVER),
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        // 初始化特征
        // 消息转发-写，主端到副端
        val mGattCharacteristicCommonWrite = BluetoothGattCharacteristic(
            getUUID(BLECHARACTERISTIC_COMMON_WRITE),
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            (BluetoothGattCharacteristic.PERMISSION_WRITE)
        )
        val mGattDescriptorCommonWrite = BluetoothGattDescriptor(
            getUUID(BLEDESCRIPTOR_COMMON_WRITE),
            BluetoothGattDescriptor.PERMISSION_WRITE
        )
        mGattCharacteristicCommonWrite.addDescriptor(mGattDescriptorCommonWrite)
        mGattService.addCharacteristic(mGattCharacteristicCommonWrite)

        // 消息转发-读，副端到主端
        val mGattCharacteristicCommonRead = BluetoothGattCharacteristic(
            getUUID(BLECHARACTERISTIC_COMMON_READ),
            BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            (BluetoothGattCharacteristic.PERMISSION_READ)
        )
        val mGattDescriptorCommonRead = BluetoothGattDescriptor(
            getUUID(BLEDESCRIPTOR_COMMON_READ),
            BluetoothGattDescriptor.PERMISSION_READ
        )
        mGattCharacteristicCommonRead.addDescriptor(mGattDescriptorCommonRead)
        mGattService.addCharacteristic(mGattCharacteristicCommonRead)

        return mGattService
    }

    //添加服务
    fun addGattServer(mGattServerCallback: BluetoothGattServerCallback) {
        if (checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            mBluetoothGattService = getGattService()
            mBluetoothGattServer =
                mBluetoothManager!!.openGattServer(
                    mApplication.applicationContext, mGattServerCallback
                )

            mBluetoothGattServer!!.addService(mBluetoothGattService)
        }
    }

    fun isAndroid12(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    //开启广播
    //官网建议获取mBluetoothLeAdvertiser时，先做mBluetoothAdapter.isMultipleAdvertisementSupported判断，
    // 但部分华为手机支持Ble广播却还是返回false,所以最后以mBluetoothLeAdvertiser是否不为空且蓝牙打开为准
    fun startAdvertising(phonename: String, mAdvertiseCallback: AdvertiseCallback): Boolean {
        if (checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            mBluetoothAdapter!!.name = phonename
            mBluetoothLeAdvertiser = mBluetoothAdapter!!.bluetoothLeAdvertiser
            //蓝牙关闭或者不支持
            return if (mBluetoothLeAdvertiser != null && mBluetoothAdapter!!.isEnabled) {
                //开始广播（不附带扫描响应报文）
                mBluetoothLeAdvertiser?.startAdvertising(
                    getAdvertiseSettings(),
                    getAdvertiseData(), mAdvertiseCallback
                )
                true
            } else {
                false
            }
        } else {
            return false
        }
    }

    //关闭蓝牙广播
    fun stopAdvertising(mAdvertiseCallback: AdvertiseCallback): Boolean {
        if (checkBlueToothPermission(Manifest.permission.BLUETOOTH_ADVERTISE)) {
            mBluetoothLeAdvertiser?.let { advertiser ->
                advertiser.stopAdvertising(mAdvertiseCallback)
            }
            return true
        } else {
            return false
        }
    }
    //endregion

    fun isEnabled() : Boolean{
        //return true
        // TODo 测试代码，有些机型无法通过该方法检测蓝牙开关
      return mBluetoothAdapter?.isEnabled == true
    }


    @RequiresApi(Build.VERSION_CODES.M)
    fun scanBlueToothDevice(scancallback: ScanCallback) {
        if (mScanning) return
        if (checkBlueToothPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            //扫描设置
            /**
             * 三种模式
             * - SCAN_MODE_LOW_POWER : 低功耗模式，默认此模式，如果应用不在前台，则强制此模式
             * - SCAN_MODE_BALANCED ： 平衡模式，一定频率下返回结果
             * - SCAN_MODE_LOW_LATENCY 高功耗模式，建议应用在前台才使用此模式
             */
            val builder = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)

            /**
             * 三种回调模式
             * - CALLBACK_TYPE_ALL_MATCHED : 寻找符合过滤条件的广播，如果没有，则返回全部广播
             * - CALLBACK_TYPE_FIRST_MATCH : 仅筛选匹配第一个广播包出发结果回调的
             * - CALLBACK_TYPE_MATCH_LOST : 这个看英文文档吧，不满足第一个条件的时候，不好解释
             */
            builder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)

            //判断手机蓝牙芯片是否支持皮批处理扫描
            if (mBluetoothAdapter!!.isOffloadedFilteringSupported) {
                builder.setReportDelay(0L)
            }
            mScanning = true
            //3秒后关闭
            CoroutineScope(Dispatchers.IO).launch {
                delay(15000)
                stopScanBlueToothDevice(scancallback)
                Log.i("bluetooth", "关闭扫描")
            }

            //过滤掉不是自己程序发送的广播
            val filter = getScanFilter()
            mBluetoothAdapter?.bluetoothLeScanner?.startScan(filter, builder.build(), scancallback)
            //过滤特定的 UUID 设备
            //bluetoothAdapter?.bluetoothLeScanner?.startScan()
        }
    }

    fun stopScanBlueToothDevice(scancallback: ScanCallback) {
        //连接时要先关闭扫描
        if (mScanning) {
            if (checkBlueToothPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                mBluetoothAdapter!!.bluetoothLeScanner?.stopScan(scancallback)
                mScanning = false
            }
        }
    }

    //初始化广播设置
    fun getAdvertiseSettings(): AdvertiseSettings {
        //初始化广播设置
        return AdvertiseSettings.Builder()
            //设置广播模式，以控制广播的功率和延迟。 ADVERTISE_MODE_LOW_LATENCY为高功率，低延迟
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            //设置蓝牙广播发射功率级别
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
            //广播时限。最多180000毫秒。值为0将禁用时间限制。（不设置则为无限广播时长）
            .setTimeout(Time)
            //设置广告类型是可连接还是不可连接。
            .setConnectable(true)
            .build()
    }

    //设置广播报文
    fun getAdvertiseData(): AdvertiseData {
        return AdvertiseData.Builder()
            //设置广播包中是否包含设备名称。
            .setIncludeDeviceName(true)
            //设置广播包中是否包含发射功率
            .setIncludeTxPowerLevel(true)
            //设置UUID
            .addServiceUuid(ParcelUuid(getUUID(BLESERVER)))
            .build()
    }


    //设置扫描过滤
    fun getScanFilter(): ArrayList<ScanFilter> {
        val scanFilterList = ArrayList<ScanFilter>()
        val builder = ScanFilter.Builder()
        builder.setServiceUuid(ParcelUuid(getUUID(BLESERVER)))
        scanFilterList.add(builder.build())
        return scanFilterList
    }

    fun  getBluetoothGatt() : BluetoothGatt?{
        return mBluetoothGatt
    }


    //获取原生蓝牙对象
    fun getBlueToothDevice(macAddress: String): BluetoothDevice? {
        return if (checkBlueToothPermission()) {
            mBluetoothDevice = mBluetoothAdapter!!.getRemoteDevice(macAddress)
            if (mBluetoothDevice == null) throw IOException("获取不到BluetoothDevice")
            mBluetoothDevice!!
        } else {
            null
        }
    }

    //申请通讯字节长度
    fun requestMTU(size: Int = 512): Boolean {
        return if (checkBlueToothPermission()) {
            mBluetoothGatt?.let {
                if (it.requestMtu(size)) {
                    mtuSize = size
                    true
                } else {
                    false
                }
            } ?: false
        } else {
            false
        }
    }

    //连接蓝牙Gatt
    fun connect(macAddress: String, callback: BluetoothGattCallback): BluetoothGatt? {
        return if (checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            if (mBluetoothDevice == null || macAddress != mBluetoothDevice?.address)
                getBlueToothDevice(macAddress)


            // TODO 这么写需要评估一下
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mBluetoothGatt =
                    mBluetoothDevice?.connectGatt(mApplication.applicationContext, false, callback,BluetoothDevice.TRANSPORT_LE/*, BluetoothDevice.PHY_LE_2M*/)
            }else{
                mBluetoothGatt =
                    mBluetoothDevice?.connectGatt(mApplication.applicationContext, false, callback)
            }
            mBluetoothGatt
        } else {
            null
        }
    }

    //发现服务
    fun discoverServices(): Boolean {
        return if (checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            mBluetoothGatt?.let {
                return it.discoverServices()
            } ?: false
        } else false
    }

    //断开蓝牙Gatt
    fun disConnect() {
        if (checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            mBluetoothGatt?.let {
                it.disconnect()
                //调用close()后，连接时传入callback会被置空，无法得到断开连接时onConnectionStateChange（）回调
                it.close()
            }
        }
    }

    //获取蓝牙GattService
    fun getBlueToothGattService(gatt: BluetoothGatt): List<BluetoothGattService> {
        return gatt.services
    }

    //发送Characteristic
    fun writeCharacteristic(
        srvuuid: String,
        charuuid: String,
        byteArray: ByteArray
    ) {
        if (checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            mBluetoothGatt?.let {
                val characteristic =
                    it.getService(getUUID(srvuuid)).getCharacteristic(getUUID(charuuid))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    it.writeCharacteristic(
                        characteristic,
                        byteArray,
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    )
                } else {
                    characteristic.setValue(byteArray)
                    it.writeCharacteristic(characteristic)
                }
            } ?: {
                throw IOException("mBluetoothGatt为空")
            }
        }
    }

    //分包发送数据
    fun writeCharacteristicByGroup(
        characteristic: BluetoothGattCharacteristic,
        byteArray: ByteArray
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            if (checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                //组装发送的分包数据
                val sendbytes = BLEByteArrayUtil.calcSendbyteArray(byteArray)
                mBluetoothGatt?.let {
                    for (curbytes in sendbytes) {
                        curbytes?.let { byte ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                it.writeCharacteristic(
                                    characteristic,
                                    byte,
                                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                                )
                            } else {
                                characteristic.setValue(byte)
                                it.writeCharacteristic(characteristic)
                            }
                        }
                        delay(30)
                    }
                } ?: {
                    throw IOException("mBluetoothGatt为空")
                }
            }
        }
    }

    //分包发送Characteristic
    suspend fun writeCharacteristicSplit(
        characteristic: BluetoothGattCharacteristic,
        byteArray: ByteArray,
        callBack : IBlueTooth.IMsgSplitStatus? = null
    ) {
        if (checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            mBluetoothGatt?.let {
                //获取到拆分后的数据包数组
                val sendarray = BLEByteArrayUtil.calcSendbyteArray(byteArray)
                for (cursend in sendarray) {
                    cursend?.let { cur ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            it.writeCharacteristic(
                                characteristic,
                                cur,
                                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                            )
                        } else {
                            characteristic.setValue(cur)
                            it.writeCharacteristic(characteristic)
                        }
                        delay(350)
                    }
                }

                // 消息分包完成
                callBack?.onIMsgSplitStatus()
            } ?: {
                throw IOException("mBluetoothGatt为空")
            }
        }
    }


    //发送Characteristic
    fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        byteArray: ByteArray
    ) {
        if (checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {

            mBluetoothGatt?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    it.writeCharacteristic(
                        characteristic,
                        byteArray,
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    )
                } else {
                    characteristic.setValue(byteArray)
                    it.writeCharacteristic(characteristic)
                }
            } ?: {
                throw IOException("mBluetoothGatt为空")
            }
        }
    }

    fun readCharacteristic(characteristic: BluetoothGattCharacteristic): ByteArray? {
        return if (checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            var byteArray: ByteArray? = null
            mBluetoothGatt?.let {
                it.readCharacteristic(characteristic)
                byteArray = characteristic.value
            }
            byteArray
        } else {
            null
        }
    }

    //发送返回值sendResponse
    fun sendResponse(
        device: BluetoothDevice, requestId: Int, offset: Int, value: ByteArray
    ) {
        if (checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            mBluetoothGattServer!!.sendResponse(
                device, requestId, BluetoothGatt.GATT_SUCCESS,
                offset, value
            )

        }
    }

    //处理服务端接收HashTable,返回值为True则全部接收完成
    fun dealRecvByteArray(deviceAdr: String, value: ByteArray): Boolean {



        //获取总包数
        val totalpkgs = BLEByteArrayUtil.getTotalpkgs(value)
        //获取当前包数
        val curpkgs = BLEByteArrayUtil.getCurpkgs(value)
        //获取实际数据
        val curdatabytes = BLEByteArrayUtil.getByteArray(value)


        if (mHtRecvByteArray.contains(deviceAdr)){

            val arraybytes = mHtRecvByteArray[deviceAdr]

            arraybytes?.let {
                it[curpkgs]?.let {
                    return false
                }
            }
        }

        //获取HashTable中的数据包
        var arraybytes = arrayOfNulls<ByteArray>(totalpkgs)
        //如果HashTable中存在，直接赋值
        if (mHtRecvByteArray.containsKey(deviceAdr)) {
            arraybytes = mHtRecvByteArray[deviceAdr]!!
        }
        //设置当前包的数据
        arraybytes[curpkgs] = curdatabytes
        //重新写回Hashtable中
        mHtRecvByteArray.put(deviceAdr, arraybytes)

        //当前包和总包数相同，说明全部完成
        return totalpkgs == curpkgs + 1
    }

    //获取接收数据,获取完整数据后，将Hashtable数据去掉
    fun getRecvByteArray(deviceAdr: String): ByteArray {
        val arrbytes = mHtRecvByteArray[deviceAdr]
        var result = byteArrayOf()
        arrbytes?.let {
            for (cur in it) {
                cur?.let { p ->
                    result += cur
                }
            }
        }
        //组装完后清除
        mHtRecvByteArray.remove(deviceAdr)
        return result
    }

    fun clearBtData(){
        mHtRecvByteArray.clear()
    }

    /**
     * Clears the internal cache and forces a refresh of the services from the
     * remote device.
     */
    fun refreshDeviceCache(): Boolean {
        if (mBluetoothGatt != null) {
            try {
                val localBluetoothGatt: BluetoothGatt? = mBluetoothGatt
                val localMethod: Method? = localBluetoothGatt?.javaClass?.getMethod(
                    "refresh", *arrayOfNulls(0)
                )
                if (localMethod != null) {

                    val invoke = localMethod.invoke(
                        localBluetoothGatt, arrayOfNulls<Any>(0))
                    return invoke as Boolean

                }
            } catch (localException: Exception) {
                Log.i("TAG", "An exception occured while refreshing device")
            }
        }
        return false
    }


    fun setCharacteristicNotify(characteristic: BluetoothGattCharacteristic, bool: Boolean) {
        if (checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            mBluetoothGatt?.let {
                val enable = it.setCharacteristicNotification(characteristic, bool)
            }
        }
    }

    fun notifyCharacteristicChanged(
        device: BluetoothDevice,
        characteristic: BluetoothGattCharacteristic,
        byteArray: ByteArray
    ) {
        if (checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            //回复客户端,让客户端读取该特征新赋予的值，获取由服务端发送的数据
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mBluetoothGattServer!!.notifyCharacteristicChanged(
                    device,
                    characteristic,
                    false,
                    byteArray
                )
            } else {
                characteristic.value = byteArray
                mBluetoothGattServer!!.notifyCharacteristicChanged(device, characteristic, false)
            }
        }
    }


    suspend fun notifyCharacteristicChangedSplit(
        device: BluetoothDevice,
        characteristic: BluetoothGattCharacteristic,
        byteArray: ByteArray
    ) {
        if (checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            //获取到拆分后的数据包数组
            val sendarray = BLEByteArrayUtil.calcSendbyteArray(byteArray)
            for (cursend in sendarray) {
                //回复客户端,让客户端读取该特征新赋予的值，获取由服务端发送的数据
                cursend?.let { cur ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        mBluetoothGattServer!!.notifyCharacteristicChanged(
                            device,
                            characteristic,
                            false,
                            cur
                        )
                    } else {
                        characteristic.value = cur
                        mBluetoothGattServer!!.notifyCharacteristicChanged(device, characteristic, false)
                    }
                    delay(50)
                }
            }
        }
    }


    // 副端移植过来
    fun getBluetoothGattService() : BluetoothGattService?{
        return mBluetoothGattService
    }

    suspend fun notifyCharacteristicChangedSplit(
        device: BluetoothDevice,
        characteristic: BluetoothGattCharacteristic,
        byteArray: ByteArray,
        callBack : IBlueTooth.IMsgSplitStatus? = null
    ) {
        if (checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            //获取到拆分后的数据包数组
            val sendarray = BLEByteArrayUtil.calcSendbyteArray(byteArray)
            for (cursend in sendarray) {
                //回复客户端,让客户端读取该特征新赋予的值，获取由服务端发送的数据
                cursend?.let { cur ->
                    PeachLogger.d(GlobalConstant.APP_TAG, "数据通信-->副端发出-->cur=${cur}，size=${cur.size}")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        mBluetoothGattServer!!.notifyCharacteristicChanged(
                            device,
                            characteristic,
                            false,
                            cur
                        )
                    } else {
                        characteristic.value = cur
                        mBluetoothGattServer!!.notifyCharacteristicChanged(device, characteristic, false)
                    }
                    delay(300)
                }
            }

            // 消息分包完成
            callBack?.onIMsgSplitStatus()
        }
    }

}