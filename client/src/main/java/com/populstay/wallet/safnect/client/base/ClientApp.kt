package com.populstay.wallet.safnect.client.base

import android.annotation.SuppressLint
import android.content.Context
import com.populstay.common.base.BaseApp
import com.populstay.common.repository.BlueToothBLEUtil

class ClientApp : BaseApp() {

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var mContext: Context

        @SuppressLint("StaticFieldLeak")
        lateinit var instance: ClientApp
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        mContext = this
        //初始化BlueToothBLEUtil
        BlueToothBLEUtil.init(this)
    }
}