package com.ai.assistance.operit.core.ai

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.ai.assistance.operit.core.assistant.IntelligentAssistantService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 智能助手管理器
 * 负责启动、管理和控制智能助手服务的生命周期
 */
class IntelligentAssistantManager(private val context: Context) {
    
    companion object {
        private const val TAG = "IntelligentAssistantManager"
        
        @Volatile
        private var INSTANCE: IntelligentAssistantManager? = null
        
        fun getInstance(context: Context): IntelligentAssistantManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: IntelligentAssistantManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
    
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val isServiceBound = AtomicBoolean(false)
    private val isServiceStarted = AtomicBoolean(false)
    
    private var assistantService: IntelligentAssistantService? = null
    private var bindingJob: Job? = null
    
    // 服务连接回调
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "智能助手服务已连接")
            
            val binder = service as? IntelligentAssistantService.LocalBinder
            assistantService = binder?.getService()
            isServiceBound.set(true)
            
            // 通知服务连接成功
            onServiceConnected()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "智能助手服务连接断开")
            assistantService = null
            isServiceBound.set(false)
            
            // 尝试重新连接
            if (isServiceStarted.get()) {
                managerScope.launch {
                    reconnectService()
                }
            }
        }
    }
    
    /**
     * 启动智能助手服务
     */
    fun startIntelligentAssistant(): Boolean {
        return try {
            if (isServiceStarted.get()) {
                Log.d(TAG, "智能助手服务已经启动")
                return true
            }
            
            val serviceIntent = Intent(context, IntelligentAssistantService::class.java)
            
            // 启动前台服务
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            if (result != null) {
                isServiceStarted.set(true)
                
                // 绑定服务
                bindToService()
                
                Log.d(TAG, "智能助手服务启动成功")
                true
            } else {
                Log.e(TAG, "智能助手服务启动失败")
                false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "启动智能助手服务异常", e)
            false
        }
    }
    
    /**
     * 停止智能助手服务
     */
    fun stopIntelligentAssistant() {
        try {
            // 解绑服务
            unbindFromService()
            
            // 停止服务
            val serviceIntent = Intent(context, IntelligentAssistantService::class.java)
            context.stopService(serviceIntent)
            
            isServiceStarted.set(false)
            assistantService = null
            
            Log.d(TAG, "智能助手服务已停止")
            
        } catch (e: Exception) {
            Log.e(TAG, "停止智能助手服务异常", e)
        }
    }
    
    /**
     * 绑定到服务
     */
    private fun bindToService() {
        if (isServiceBound.get()) {
            return
        }
        
        bindingJob = managerScope.launch {
            try {
                val serviceIntent = Intent(context, IntelligentAssistantService::class.java)
                val bindResult = context.bindService(
                    serviceIntent,
                    serviceConnection,
                    Context.BIND_AUTO_CREATE
                )
                
                if (!bindResult) {
                    Log.e(TAG, "绑定智能助手服务失败")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "绑定服务异常", e)
            }
        }
    }
    
    /**
     * 解绑服务
     */
    private fun unbindFromService() {
        if (isServiceBound.get()) {
            try {
                context.unbindService(serviceConnection)
                isServiceBound.set(false)
                bindingJob?.cancel()
            } catch (e: Exception) {
                Log.e(TAG, "解绑服务异常", e)
            }
        }
    }
    
    /**
     * 重新连接服务
     */
    private suspend fun reconnectService() {
        Log.d(TAG, "尝试重新连接智能助手服务")
        
        // 等待一段时间后重试
        kotlinx.coroutines.delay(3000)
        
        if (isServiceStarted.get() && !isServiceBound.get()) {
            bindToService()
        }
    }
    
    /**
     * 服务连接成功的回调
     */
    private fun onServiceConnected() {
        Log.d(TAG, "智能助手服务连接成功，开始初始化")
        
        // 可以在这里进行服务连接后的初始化工作
        managerScope.launch {
            try {
                val status = assistantService?.getServiceStatus()
                Log.d(TAG, "服务状态: $status")
                
            } catch (e: Exception) {
                Log.e(TAG, "获取服务状态失败", e)
            }
        }
    }
    
    /**
     * 手动触发对话
     */
    fun triggerConversation(text: String) {
        if (isServiceBound.get()) {
            assistantService?.triggerConversation(text)
        } else {
            Log.w(TAG, "服务未连接，无法触发对话")
        }
    }
    
    /**
     * 获取服务状态
     */
    fun getServiceStatus(): Map<String, Any>? {
        return if (isServiceBound.get()) {
            assistantService?.getServiceStatus()
        } else {
            null
        }
    }
    
    /**
     * 检查服务是否运行
     */
    fun isServiceRunning(): Boolean {
        return isServiceStarted.get() && IntelligentAssistantService.isRunning()
    }
    
    /**
     * 检查服务是否已绑定
     */
    fun isServiceBound(): Boolean {
        return isServiceBound.get()
    }
    
    /**
     * 重启智能助手服务
     */
    fun restartIntelligentAssistant() {
        managerScope.launch {
            Log.d(TAG, "重启智能助手服务")
            
            stopIntelligentAssistant()
            kotlinx.coroutines.delay(1000)
            startIntelligentAssistant()
        }
    }
    
    /**
     * 释放管理器资源
     */
    fun release() {
        try {
            stopIntelligentAssistant()
            managerScope.cancel()
            Log.d(TAG, "智能助手管理器资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放管理器资源失败", e)
        }
    }
}