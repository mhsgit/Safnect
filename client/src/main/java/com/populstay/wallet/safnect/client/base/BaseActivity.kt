package com.populstay.wallet.safnect.client.base

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStatusBarTextColor()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setStatusBarTextColor()
    }

    fun setStatusBarTextColor(){
        //设置字体黑色
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    }

    abstract fun initTitleBar()
}
