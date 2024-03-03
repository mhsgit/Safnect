package com.populstay.wallet.safnect.server

import android.annotation.SuppressLint
import android.content.Context
import com.populstay.common.base.BaseApp
import com.populstay.common.repository.BlueToothBLEUtil

class ServerApp : BaseApp() {

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var mContext: Context

        @SuppressLint("StaticFieldLeak")
        lateinit var instance: ServerApp
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        mContext = this
        //初始化BlueToothBLEUtil
        BlueToothBLEUtil.init(this)
    }
}