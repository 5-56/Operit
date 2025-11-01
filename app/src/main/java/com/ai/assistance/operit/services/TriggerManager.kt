package com.ai.assistance.operit.services

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.ai.assistance.operit.data.preferences.TriggerPreferences
import com.ai.assistance.operit.data.repository.TriggerRuleRepository

class TriggerManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "TriggerManager"
        
        @Volatile
        private var INSTANCE: TriggerManager? = null
        
        fun getInstance(context: Context): TriggerManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TriggerManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
    
    private val triggerPreferences = TriggerPreferences.getInstance(context)
    private val triggerRuleRepository = TriggerRuleRepository.getInstance(context)
    
    /**
     * 检查通知访问权限是否已授予
     */
    fun hasNotificationAccess(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        val packageName = context.packageName
        return enabledListeners != null && enabledListeners.contains(packageName)
    }
    
    /**
     * 检查位置权限是否已授予
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 打开通知访问设置页面
     */
    fun openNotificationAccessSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
    
    /**
     * 启动通知监听服务
     */
    suspend fun startNotificationMonitor(): Boolean {
        return try {
            if (!hasNotificationAccess()) {
                Log.w(TAG, "Notification access not granted")
                return false
            }
            
            if (!triggerPreferences.isNotificationTriggersEnabled()) {
                Log.w(TAG, "Notification triggers not enabled")
                return false
            }
            
            // Enable the service
            val componentName = ComponentName(context, NotificationMonitor::class.java)
            context.packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            
            // Request rebind if supported
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                NotificationMonitor.requestRebind(componentName)
            }
            
            Log.i(TAG, "Notification monitor started")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start notification monitor", e)
            false
        }
    }
    
    /**
     * 停止通知监听服务
     */
    fun stopNotificationMonitor() {
        try {
            val componentName = ComponentName(context, NotificationMonitor::class.java)
            context.packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.i(TAG, "Notification monitor stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop notification monitor", e)
        }
    }
    
    /**
     * 启动位置上下文服务
     */
    suspend fun startLocationContextService(): Boolean {
        return try {
            if (!hasLocationPermission()) {
                Log.w(TAG, "Location permission not granted")
                return false
            }
            
            if (!triggerPreferences.isGeofenceTriggersEnabled()) {
                Log.w(TAG, "Geofence triggers not enabled")
                return false
            }
            
            val intent = Intent(context, LocationContextService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            
            Log.i(TAG, "Location context service started")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start location context service", e)
            false
        }
    }
    
    /**
     * 停止位置上下文服务
     */
    fun stopLocationContextService() {
        try {
            val intent = Intent(context, LocationContextService::class.java)
            context.stopService(intent)
            Log.i(TAG, "Location context service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop location context service", e)
        }
    }
    
    /**
     * 启用所有触发器（基于配置和权限）
     */
    suspend fun enableAllTriggers(): Boolean {
        var success = true
        
        // Start notification monitor if enabled
        if (triggerPreferences.isNotificationTriggersEnabled()) {
            if (!startNotificationMonitor()) {
                success = false
            }
        }
        
        // Start location service if enabled
        if (triggerPreferences.isGeofenceTriggersEnabled()) {
            if (!startLocationContextService()) {
                success = false
            }
        }
        
        return success
    }
    
    /**
     * 禁用所有触发器
     */
    fun disableAllTriggers() {
        stopNotificationMonitor()
        stopLocationContextService()
    }
    
    /**
     * 完全清理 - 停止服务并清除所有规则
     */
    suspend fun clearAll() {
        disableAllTriggers()
        triggerRuleRepository.deleteAllRules()
        triggerPreferences.optOutAndDisableAll()
        Log.i(TAG, "All triggers cleared")
    }
}
