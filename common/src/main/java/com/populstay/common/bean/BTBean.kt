package com.populstay.common.bean

open class BTBean{

    companion object{
        const val TYPE_SCAN = 1
        const val TYPE_DEVICE = 0
    }

    var name :String? = null
    var type : Int = TYPE_DEVICE
}
