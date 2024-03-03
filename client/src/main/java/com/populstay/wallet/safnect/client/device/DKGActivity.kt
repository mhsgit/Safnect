package com.populstay.wallet.safnect.client.device

import android.bluetooth.BluetoothGattCharacteristic
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import com.populstay.common.GlobalConstant
import com.populstay.common.bean.BTBean
import com.populstay.common.bean.CBleDevice
import com.populstay.common.loader.LoaderStyle
import com.populstay.common.loader.PeachLoader
import com.populstay.common.log.PeachLogger
import com.populstay.common.repository.BlueToothBLEUtil
import com.populstay.wallet.devicemanager.DeviceConstant
import com.populstay.wallet.safnect.client.R
import com.populstay.wallet.safnect.client.databinding.ActivityDeviceListBinding
import com.populstay.wallet.safnect.client.device.adapter.DeviceAdapter
import kotlinx.android.synthetic.main.activity_device_list.spinner
import kotlinx.android.synthetic.main.common_title_layout.view.back
import kotlinx.coroutines.launch


open class DKGActivity : BTActivity() {

    private lateinit var binding: ActivityDeviceListBinding

    private val mDataList by lazy {
        mutableListOf<BTBean>()
    }

    private val mDeviceAdapter by lazy {
        DeviceAdapter(this@DKGActivity,mDataList)
    }

    protected fun showLoading() {
        PeachLoader.showLoading(this, LoaderStyle.BallSpinFadeLoaderIndicator.name)
    }

    protected fun stopLoading() {
        PeachLoader.stopLoading()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initTitleBar()
        initDeviceList()
        startScan()

        binding.pairMyWallet.setOnClickListener {
            showLoading()
            // 开始连接
            connect()
        }
    }

    fun showMsg(msg :String){
        binding.showMsgTv.text = msg
    }

    override fun onFoundService() {
        // 开始分片，刚连接上，要缓一会才能发起dkg
        binding.pairMyWallet.postDelayed({
            requestRunDKG()
        },500)
    }

    override fun onCharacteristicChanged(data: ByteArray) {
        stopLoading()
        showMsg("副端同意了DKG请求，可以开始DKG流程了：${String(data, Charsets.UTF_8)}")
        // todo 这里开始DKG流程了
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onConnectionFail() {

        Toast.makeText(this,resources.getString(R.string.connection_failed_scan_try),Toast.LENGTH_SHORT).show()

        BlueToothBLEUtil.clearBtData()

        stopScan()
        startScan()
    }

    override fun onScanStatus(state: Int) {
        if (DeviceConstant.SCAN_STATUS_START == state){
            setScanningData()
        }
    }
    override fun onScanResult(deviceList: List<CBleDevice>?) {
        setDeviceData(deviceList)
        updateConfirmBtnStatus()
    }

    fun setScanningData(){
        mDataList.clear()
        val scanBean = BTBean()
        scanBean.name = "正在扫描"
        scanBean.type = BTBean.TYPE_SCAN
        mDataList.add(scanBean)
        mDeviceAdapter.notifyDataSetChanged()
        // 如果只有一项数据，则禁用 Spinner 的点击选择功能
        if (mDeviceAdapter.count == 1) {
            spinner.isEnabled = false;
            spinner.isClickable = false;
        }
    }

    fun setDeviceData(deviceList : List<BTBean>?){
        mCurSelectDevice = deviceList?.get(0) as CBleDevice?
        mDataList.clear()
        deviceList?.let {
            mDataList.addAll(it)
        }
        mDeviceAdapter.notifyDataSetChanged()
        spinner.isEnabled = true
        spinner.isClickable = true
    }

    private fun initDeviceList() {

        binding.spinner.adapter = mDeviceAdapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val selectedItem = parent.getItemAtPosition(position)
                if (selectedItem is CBleDevice){
                    mCurSelectDevice = selectedItem
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // 未选择任何项时的处理
            }
        }
    }

    override fun initTitleBar() {
        binding.commonTitleLayout.back.setOnClickListener {
            finish()
        }
    }

    private fun updateConfirmBtnStatus(){
        binding.pairMyWallet.isEnabled = null != mCurSelectDevice
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onBTRequestOpen() {
        startScan()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onBTRequestPermissions() {
        startScan()
    }

    private fun requestRunDKG(){
        PeachLogger.d(GlobalConstant.APP_TAG, " requestRunDKG 发起创建账号申请")
        BlueToothBLEUtil.refreshDeviceCache()
        BlueToothBLEUtil.clearBtData()
        lifecycleScope.launch {


            val characteristicCommonRead = mBluetoothGattService?.getCharacteristic(
                BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_COMMON_READ))
            characteristicCommonRead?.let {
                // 通知的监听，这一步很关键，不然外围设备端发送的数据将无法接收到。
                if (characteristicCommonRead.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0){
                    BlueToothBLEUtil.setCharacteristicNotify(characteristicCommonRead , true)
                }
            }

            // 发送消息分发私钥
            val characteristic = mBluetoothGattService?.getCharacteristic(
                BlueToothBLEUtil.getUUID(
                    BlueToothBLEUtil.BLECHARACTERISTIC_COMMON_WRITE))
            characteristic?.let {
                // 通知的监听，这一步很关键，不然外围设备端发送的数据将无法接收到。
                if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0){
                    BlueToothBLEUtil.setCharacteristicNotify(characteristic , true)
                }

                launch {

                    // todo 据具体数据格式待定吧，先用最简单的方式调用通信
                    // 01 代表主端向副端设备发起DKG请求
                    BlueToothBLEUtil.writeCharacteristicSplit(characteristic,  "01".toByteArray())
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLoading()
    }
}