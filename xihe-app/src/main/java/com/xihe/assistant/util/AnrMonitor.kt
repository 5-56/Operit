package com.xihe.assistant.util

import android.app.Activity
import android.util.Log
import kotlinx.coroutines.*

class AnrMonitor(private val activity: Activity, private val scope: CoroutineScope) {
    private val TAG = "AnrMonitor"
    private var isMonitoring = false
    private var monitoringJob: Job? = null
    
    fun start() {
        if (isMonitoring) return
        
        isMonitoring = true
        monitoringJob = scope.launch {
            while (isMonitoring) {
                val startTime = System.currentTimeMillis()
                
                // 模拟一些工作
                delay(100)
                
                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime
                
                if (duration > 100) {
                    Log.w(TAG, "ANR检测: 主线程阻塞 ${duration}ms")
                }
                
                delay(1000) // 每秒检查一次
            }
        }
        
        Log.d(TAG, "ANR监控已启动")
    }
    
    fun stop() {
        isMonitoring = false
        monitoringJob?.cancel()
        monitoringJob = null
        Log.d(TAG, "ANR监控已停止")
    }
}