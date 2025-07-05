package com.ai.assistance.operit.auraflow.permission

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

/**
 * 权限类型枚举
 */
enum class PermissionType(
    val displayName: String,
    val description: String,
    val isRequired: Boolean = true
) {
    OVERLAY("悬浮窗权限", "显示浮动控制窗口", true),
    ACCESSIBILITY("无障碍服务", "执行自动化操作", true),
    MEDIA_PROJECTION("屏幕录制权限", "捕获屏幕内容", true),
    NOTIFICATION("通知权限", "显示运行状态通知", false),
    STORAGE("存储权限", "保存截图和日志文件", false),
    INTERNET("网络权限", "与AI大脑通信", true)
}

/**
 * 权限状态
 */
enum class PermissionStatus {
    GRANTED,        // 已授权
    DENIED,         // 被拒绝
    NOT_REQUESTED,  // 未请求
    CHECKING        // 检查中
}

/**
 * 权限检查结果
 */
data class PermissionState(
    val type: PermissionType,
    val status: PermissionStatus,
    val canRequest: Boolean = true,
    val errorMessage: String? = null
)

/**
 * 统一权限管理器
 */
class PermissionManager private constructor() {
    
    companion object {
        private const val TAG = "PermissionManager"
        private const val ACCESSIBILITY_SERVICE_NAME = "com.ai.assistance.operit.auraflow/.service.AuraFlowAccessibilityService"
        
        @Volatile
        private var INSTANCE: PermissionManager? = null
        
        fun getInstance(): PermissionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PermissionManager().also { INSTANCE = it }
            }
        }
    }
    
    // 权限状态流
    private val _permissionStates = MutableStateFlow<Map<PermissionType, PermissionState>>(
        PermissionType.values().associateWith { type ->
            PermissionState(type, PermissionStatus.NOT_REQUESTED)
        }
    )
    val permissionStates: StateFlow<Map<PermissionType, PermissionState>> = _permissionStates.asStateFlow()
    
    // 媒体投影结果通道
    private val mediaProjectionResultChannel = Channel<ActivityResult>(Channel.UNLIMITED)
    
    // Activity Result Launchers（需要在Activity中初始化）
    private var overlayPermissionLauncher: ActivityResultLauncher<Intent>? = null
    private var accessibilitySettingsLauncher: ActivityResultLauncher<Intent>? = null
    private var mediaProjectionLauncher: ActivityResultLauncher<Intent>? = null
    private var notificationPermissionLauncher: ActivityResultLauncher<String>? = null
    private var storagePermissionLauncher: ActivityResultLauncher<Array<String>>? = null
    
    /**
     * 在Activity中初始化权限启动器
     */
    fun initializeInActivity(activity: ComponentActivity) {
        Log.d(TAG, "在Activity中初始化权限启动器")
        
        // 悬浮窗权限启动器
        overlayPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            checkOverlayPermission(activity)
        }
        
        // 无障碍服务设置启动器
        accessibilitySettingsLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            checkAccessibilityPermission(activity)
        }
        
        // 媒体投影权限启动器
        mediaProjectionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            mediaProjectionResultChannel.trySend(result)
            if (result.resultCode == Activity.RESULT_OK) {
                updatePermissionState(PermissionType.MEDIA_PROJECTION, PermissionStatus.GRANTED)
            } else {
                updatePermissionState(PermissionType.MEDIA_PROJECTION, PermissionStatus.DENIED)
            }
        }
        
        // 通知权限启动器（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher = activity.registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                val status = if (granted) PermissionStatus.GRANTED else PermissionStatus.DENIED
                updatePermissionState(PermissionType.NOTIFICATION, status)
            }
        }
        
        // 存储权限启动器
        storagePermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.values.all { it }
            val status = if (allGranted) PermissionStatus.GRANTED else PermissionStatus.DENIED
            updatePermissionState(PermissionType.STORAGE, status)
        }
    }
    
    /**
     * 检查所有权限状态
     */
    suspend fun checkAllPermissions(context: Context) {
        Log.d(TAG, "检查所有权限状态")
        
        checkOverlayPermission(context)
        checkAccessibilityPermission(context)
        checkMediaProjectionPermission(context)
        checkNotificationPermission(context)
        checkStoragePermission(context)
        checkInternetPermission(context)
    }
    
    /**
     * 检查悬浮窗权限
     */
    private fun checkOverlayPermission(context: Context) {
        updatePermissionState(PermissionType.OVERLAY, PermissionStatus.CHECKING)
        
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true // Android 6.0以下默认有权限
        }
        
        val status = if (hasPermission) PermissionStatus.GRANTED else PermissionStatus.DENIED
        updatePermissionState(PermissionType.OVERLAY, status)
        
        Log.d(TAG, "悬浮窗权限检查结果: $status")
    }
    
    /**
     * 检查无障碍服务权限
     */
    private fun checkAccessibilityPermission(context: Context) {
        updatePermissionState(PermissionType.ACCESSIBILITY, PermissionStatus.CHECKING)
        
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        
        val isEnabled = enabledServices.any { serviceInfo ->
            serviceInfo.id.contains(context.packageName)
        }
        
        val status = if (isEnabled) PermissionStatus.GRANTED else PermissionStatus.DENIED
        updatePermissionState(PermissionType.ACCESSIBILITY, status)
        
        Log.d(TAG, "无障碍服务权限检查结果: $status")
    }
    
    /**
     * 检查媒体投影权限
     */
    private fun checkMediaProjectionPermission(context: Context) {
        updatePermissionState(PermissionType.MEDIA_PROJECTION, PermissionStatus.CHECKING)
        
        // 媒体投影权限需要在运行时申请，这里只是标记为未请求
        updatePermissionState(PermissionType.MEDIA_PROJECTION, PermissionStatus.NOT_REQUESTED)
        
        Log.d(TAG, "媒体投影权限需要在使用时申请")
    }
    
    /**
     * 检查通知权限
     */
    private fun checkNotificationPermission(context: Context) {
        updatePermissionState(PermissionType.NOTIFICATION, PermissionStatus.CHECKING)
        
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 13以下默认有权限
        }
        
        val status = if (hasPermission) PermissionStatus.GRANTED else PermissionStatus.DENIED
        updatePermissionState(PermissionType.NOTIFICATION, status)
        
        Log.d(TAG, "通知权限检查结果: $status")
    }
    
    /**
     * 检查存储权限
     */
    private fun checkStoragePermission(context: Context) {
        updatePermissionState(PermissionType.STORAGE, PermissionStatus.CHECKING)
        
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
        
        val hasPermission = permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        
        val status = if (hasPermission) PermissionStatus.GRANTED else PermissionStatus.DENIED
        updatePermissionState(PermissionType.STORAGE, status)
        
        Log.d(TAG, "存储权限检查结果: $status")
    }
    
    /**
     * 检查网络权限
     */
    private fun checkInternetPermission(context: Context) {
        updatePermissionState(PermissionType.INTERNET, PermissionStatus.CHECKING)
        
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.INTERNET
        ) == PackageManager.PERMISSION_GRANTED
        
        val status = if (hasPermission) PermissionStatus.GRANTED else PermissionStatus.DENIED
        updatePermissionState(PermissionType.INTERNET, status)
        
        Log.d(TAG, "网络权限检查结果: $status")
    }
    
    /**
     * 请求悬浮窗权限
     */
    fun requestOverlayPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(context)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                
                overlayPermissionLauncher?.launch(intent) ?: run {
                    // 如果启动器未初始化，直接启动Intent
                    context.startActivity(intent)
                }
                return false
            }
        }
        return true
    }
    
    /**
     * 请求无障碍服务权限
     */
    fun requestAccessibilityPermission(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        
        accessibilitySettingsLauncher?.launch(intent) ?: run {
            context.startActivity(intent)
        }
        return false
    }
    
    /**
     * 请求媒体投影权限
     */
    fun requestMediaProjectionPermission(context: Context): Boolean {
        val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        
        mediaProjectionLauncher?.launch(intent) ?: run {
            updatePermissionState(
                PermissionType.MEDIA_PROJECTION, 
                PermissionStatus.DENIED,
                errorMessage = "无法请求媒体投影权限：Activity启动器未初始化"
            )
            return false
        }
        return false
    }
    
    /**
     * 请求通知权限
     */
    fun requestNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher?.launch(android.Manifest.permission.POST_NOTIFICATIONS) ?: run {
                updatePermissionState(
                    PermissionType.NOTIFICATION,
                    PermissionStatus.DENIED,
                    errorMessage = "无法请求通知权限：Activity启动器未初始化"
                )
                return false
            }
            return false
        }
        return true
    }
    
    /**
     * 请求存储权限
     */
    fun requestStoragePermission(context: Context): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
        
        storagePermissionLauncher?.launch(permissions) ?: run {
            updatePermissionState(
                PermissionType.STORAGE,
                PermissionStatus.DENIED,
                errorMessage = "无法请求存储权限：Activity启动器未初始化"
            )
            return false
        }
        return false
    }
    
    /**
     * 获取媒体投影结果
     */
    suspend fun getMediaProjectionResult(): ActivityResult? {
        return try {
            mediaProjectionResultChannel.receive()
        } catch (e: Exception) {
            Log.e(TAG, "获取媒体投影结果失败", e)
            null
        }
    }
    
    /**
     * 更新权限状态
     */
    private fun updatePermissionState(
        type: PermissionType, 
        status: PermissionStatus,
        canRequest: Boolean = true,
        errorMessage: String? = null
    ) {
        val currentStates = _permissionStates.value.toMutableMap()
        currentStates[type] = PermissionState(type, status, canRequest, errorMessage)
        _permissionStates.value = currentStates
        
        Log.d(TAG, "权限状态更新: ${type.displayName} -> $status")
    }
    
    /**
     * 获取指定权限状态
     */
    fun getPermissionStatus(type: PermissionType): PermissionStatus {
        return _permissionStates.value[type]?.status ?: PermissionStatus.NOT_REQUESTED
    }
    
    /**
     * 检查是否所有必需权限都已授权
     */
    fun areAllRequiredPermissionsGranted(): Boolean {
        return PermissionType.values()
            .filter { it.isRequired }
            .all { type ->
                getPermissionStatus(type) == PermissionStatus.GRANTED
            }
    }
    
    /**
     * 获取未授权的必需权限
     */
    fun getMissingRequiredPermissions(): List<PermissionType> {
        return PermissionType.values()
            .filter { it.isRequired }
            .filter { type ->
                getPermissionStatus(type) != PermissionStatus.GRANTED
            }
    }
    
    /**
     * 创建权限状态监听流
     */
    fun observePermissionChanges(context: Context): Flow<Map<PermissionType, PermissionState>> {
        return callbackFlow {
            // 发送当前状态
            trySend(_permissionStates.value)
            
            // 监听权限变化
            val job = kotlinx.coroutines.GlobalScope.launch {
                _permissionStates.collect { states ->
                    trySend(states)
                }
            }
            
            // 定期检查权限状态（每5秒）
            val checkJob = kotlinx.coroutines.GlobalScope.launch {
                while (true) {
                    kotlinx.coroutines.delay(5000)
                    checkAllPermissions(context)
                }
            }
            
            awaitClose {
                job.cancel()
                checkJob.cancel()
            }
        }
    }
    
    /**
     * 重置所有权限状态
     */
    fun resetAllPermissions() {
        _permissionStates.value = PermissionType.values().associateWith { type ->
            PermissionState(type, PermissionStatus.NOT_REQUESTED)
        }
        Log.d(TAG, "所有权限状态已重置")
    }
    
    /**
     * 获取权限请求说明文本
     */
    fun getPermissionRationale(type: PermissionType): String {
        return when (type) {
            PermissionType.OVERLAY -> 
                "AuraFlow Agent 需要悬浮窗权限来显示浮动控制面板，方便您随时控制自动化任务的执行。"
            
            PermissionType.ACCESSIBILITY -> 
                "无障碍服务权限是 AuraFlow Agent 的核心功能，用于模拟点击、滑动等操作来执行自动化任务。"
            
            PermissionType.MEDIA_PROJECTION -> 
                "屏幕录制权限用于捕获当前屏幕内容，让 AI 大脑能够 \"看到\" 屏幕状态并做出相应的操作决策。"
            
            PermissionType.NOTIFICATION -> 
                "通知权限用于显示任务运行状态和重要提醒信息，帮助您及时了解自动化任务的执行情况。"
            
            PermissionType.STORAGE -> 
                "存储权限用于保存任务执行过程中的截图、日志等文件，便于您查看和分析任务执行记录。"
            
            PermissionType.INTERNET -> 
                "网络权限是 AuraFlow Agent 与 AI 大脑服务通信的基础，用于发送屏幕信息和接收操作指令。"
        }
    }
}