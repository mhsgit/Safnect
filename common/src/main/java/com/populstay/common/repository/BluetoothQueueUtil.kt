package com.populstay.common.repository

import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue

object BluetoothQueueUtil {

    private var lastDataDealStatus = false

    // ConcurrentLinkedQueue 线程安全的队列
    private val queue: Queue<ByteArray> = ConcurrentLinkedQueue()

    fun enqueueData(data: ByteArray) {
        queue.offer(data)
    }

    fun dequeueData(): ByteArray? {
        return queue.poll()
    }

    fun isQueueEmpty(): Boolean {
        return queue.isEmpty()
    }

    fun clear(){
        unLockDealStatus()
        queue.clear()
    }

    fun isLock() : Boolean{
        return lastDataDealStatus
    }

    fun lockDealStatus(){
        lastDataDealStatus = true
    }

    fun unLockDealStatus(){
        lastDataDealStatus = false
    }
}
