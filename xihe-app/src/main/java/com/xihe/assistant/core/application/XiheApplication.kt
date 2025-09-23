package com.xihe.assistant.core.application

import android.app.Application
import android.util.Log
import com.xihe.assistant.data.preferences.androidPermissionPreferences

class XiheApplication : Application() {
    private val TAG = "XiheApplication"
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "羲和应用启动")
        
        // 初始化权限偏好设置
        androidPermissionPreferences.initialize(this)
        
        // 初始化其他组件
        initializeComponents()
    }
    
    private fun initializeComponents() {
        Log.d(TAG, "初始化应用组件")
        // 这里可以初始化各种组件
    }
}