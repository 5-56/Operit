package com.ai.assistance.operit.ui.permissions

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.model.AITool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

// Define DataStore
private val Context.toolPermissionsDataStore: DataStore<Preferences> by preferencesDataStore(name = "tool_permissions")

/**
 * Permission levels for tool operations
 */
enum class PermissionLevel {
    ALLOW,      // Allow automatically without asking
    CAUTION,    // Ask for dangerous operations, allow others
    ASK,        // Always ask
    FORBID;     // Never allow

    companion object {
        fun fromString(value: String?): PermissionLevel {
            return when (value) {
                "ALLOW" -> ALLOW
                "CAUTION" -> CAUTION
                "ASK" -> ASK
                "FORBID" -> FORBID
                else -> ALLOW  // 改为默认ALLOW而不是ASK
            }
        }
    }
}

/**
 * Tool categories with different security implications
 */
enum class ToolCategory {
    SYSTEM_OPERATION,    // System operations (settings modifications)
    NETWORK,             // Network operations (HTTP requests)
    UI_AUTOMATION,       // UI automation (clicks, touches)
    FILE_READ,           // File reading operations
    FILE_WRITE;          // File writing/deletion operations

    companion object {
        fun getDefaultPermissionLevel(category: ToolCategory): PermissionLevel {
            return when (category) {
                SYSTEM_OPERATION -> PermissionLevel.ALLOW // 改为ALLOW
                NETWORK -> PermissionLevel.ALLOW
                UI_AUTOMATION -> PermissionLevel.ALLOW // 改为ALLOW
                FILE_READ -> PermissionLevel.ALLOW
                FILE_WRITE -> PermissionLevel.ALLOW // 改为ALLOW
            }
        }
    }
}

/**
 * Centralized tool permission system that manages both permission storage and checking
 */
class ToolPermissionSystem private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ToolPermissionSystem"
        private const val PERMISSION_REQUEST_TIMEOUT_MS = 60000L // 60 seconds timeout
        
        // DataStore keys
        private val MASTER_SWITCH = stringPreferencesKey("master_switch")
        private val SYSTEM_OPERATION_PERMISSION = stringPreferencesKey("system_operation_permission")
        private val NETWORK_PERMISSION = stringPreferencesKey("network_permission")
        private val UI_AUTOMATION_PERMISSION = stringPreferencesKey("ui_automation_permission")
        private val FILE_READ_PERMISSION = stringPreferencesKey("file_read_permission")
        private val FILE_WRITE_PERMISSION = stringPreferencesKey("file_write_permission")
        
        // Default permission setting
        private val DEFAULT_MASTER_SWITCH = PermissionLevel.ALLOW.name // 改为ALLOW
        
        @Volatile
        private var INSTANCE: ToolPermissionSystem? = null
        
        fun getInstance(context: Context): ToolPermissionSystem {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ToolPermissionSystem(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // Permission request management
    private val mainHandler = Handler(Looper.getMainLooper())
    private val permissionRequestOverlay = PermissionRequestOverlay(context)
    private var currentPermissionCallback: ((PermissionRequestResult) -> Unit)? = null
    private var permissionRequestInfo: Pair<AITool, String>? = null
    
    // Permission request state flow
    private val _permissionRequestState = MutableStateFlow<Pair<AITool, String>?>(null)
    val permissionRequestState = _permissionRequestState.asStateFlow()
    
    // Permission level flows
    val masterSwitchFlow: Flow<PermissionLevel> = context.toolPermissionsDataStore.data.map { preferences ->
        PermissionLevel.fromString(preferences[MASTER_SWITCH] ?: DEFAULT_MASTER_SWITCH)
    }
    
    val systemOperationPermissionFlow: Flow<PermissionLevel> = context.toolPermissionsDataStore.data.map { preferences ->
        PermissionLevel.fromString(
            preferences[SYSTEM_OPERATION_PERMISSION]
                ?: ToolCategory.getDefaultPermissionLevel(ToolCategory.SYSTEM_OPERATION).name
        )
    }
    
    val networkPermissionFlow: Flow<PermissionLevel> = context.toolPermissionsDataStore.data.map { preferences ->
        PermissionLevel.fromString(
            preferences[NETWORK_PERMISSION]
                ?: ToolCategory.getDefaultPermissionLevel(ToolCategory.NETWORK).name
        )
    }
    
    val uiAutomationPermissionFlow: Flow<PermissionLevel> = context.toolPermissionsDataStore.data.map { preferences ->
        PermissionLevel.fromString(
            preferences[UI_AUTOMATION_PERMISSION]
                ?: ToolCategory.getDefaultPermissionLevel(ToolCategory.UI_AUTOMATION).name
        )
    }
    
    val fileReadPermissionFlow: Flow<PermissionLevel> = context.toolPermissionsDataStore.data.map { preferences ->
        PermissionLevel.fromString(
            preferences[FILE_READ_PERMISSION]
                ?: ToolCategory.getDefaultPermissionLevel(ToolCategory.FILE_READ).name
        )
    }
    
    val fileWritePermissionFlow: Flow<PermissionLevel> = context.toolPermissionsDataStore.data.map { preferences ->
        PermissionLevel.fromString(
            preferences[FILE_WRITE_PERMISSION]
                ?: ToolCategory.getDefaultPermissionLevel(ToolCategory.FILE_WRITE).name
        )
    }
    
    // Registry of dangerous operations by tool name
    private val dangerousOperationsRegistry = mutableMapOf<String, (AITool) -> Boolean>()
    
    // Registry of operation descriptions by tool name
    private val operationDescriptionRegistry = mutableMapOf<String, (AITool) -> String>()
    
    /**
     * Register a tool as potentially dangerous with custom danger check logic
     */
    fun registerDangerousOperation(toolName: String, dangerCheck: (AITool) -> Boolean) {
        dangerousOperationsRegistry[toolName] = dangerCheck
    }
    
    /**
     * Register a description generator for a tool
     */
    fun registerOperationDescription(toolName: String, descriptionGenerator: (AITool) -> String) {
        operationDescriptionRegistry[toolName] = descriptionGenerator
    }
    
    /**
     * Initialize default dangerous operations and descriptions
     */
    fun initializeDefaultRules() {
        // 不需要在这里预先注册工具的危险操作检查和描述生成器
        // 所有工具相关的信息都应该在AIToolHandler中通过统一的registerTool方法完成
        // 这个方法保留为空，以便在必要时进行一些全局初始化操作
        Log.d(TAG, "工具权限系统已初始化 - 所有工具定义现在都在AIToolHandler中")
    }
    
    /**
     * Save permission level settings
     */
    suspend fun saveMasterSwitch(level: PermissionLevel) {
        context.toolPermissionsDataStore.edit { preferences ->
            preferences[MASTER_SWITCH] = level.name
        }
    }
    
    suspend fun saveSystemOperationPermission(level: PermissionLevel) {
        context.toolPermissionsDataStore.edit { preferences ->
            preferences[SYSTEM_OPERATION_PERMISSION] = level.name
        }
    }
    
    suspend fun saveNetworkPermission(level: PermissionLevel) {
        context.toolPermissionsDataStore.edit { preferences ->
            preferences[NETWORK_PERMISSION] = level.name
        }
    }
    
    suspend fun saveUIAutomationPermission(level: PermissionLevel) {
        context.toolPermissionsDataStore.edit { preferences ->
            preferences[UI_AUTOMATION_PERMISSION] = level.name
        }
    }
    
    suspend fun saveFileReadPermission(level: PermissionLevel) {
        context.toolPermissionsDataStore.edit { preferences ->
            preferences[FILE_READ_PERMISSION] = level.name
        }
    }
    
    suspend fun saveFileWritePermission(level: PermissionLevel) {
        context.toolPermissionsDataStore.edit { preferences ->
            preferences[FILE_WRITE_PERMISSION] = level.name
        }
    }
    
    suspend fun saveAllPermissions(
        masterSwitch: PermissionLevel,
        systemOperation: PermissionLevel,
        network: PermissionLevel,
        uiAutomation: PermissionLevel,
        fileRead: PermissionLevel,
        fileWrite: PermissionLevel
    ) {
        context.toolPermissionsDataStore.edit { preferences ->
            preferences[MASTER_SWITCH] = masterSwitch.name
            preferences[SYSTEM_OPERATION_PERMISSION] = systemOperation.name
            preferences[NETWORK_PERMISSION] = network.name
            preferences[UI_AUTOMATION_PERMISSION] = uiAutomation.name
            preferences[FILE_READ_PERMISSION] = fileRead.name
            preferences[FILE_WRITE_PERMISSION] = fileWrite.name
        }
    }
    
    /**
     * Check if a tool operation is dangerous
     */
    fun isDangerousOperation(tool: AITool): Boolean {
        // 检查是否是支付或密码相关操作
        val toolName = tool.name.lowercase()
        val parameters = tool.parameters.map { it.name.lowercase() to it.value.toString().lowercase() }
        
        // 只有涉及支付密码、银行密码、登录密码等才认为是危险操作
        val isPaymentPassword = toolName.contains("pay") || toolName.contains("payment") || 
                                toolName.contains("bank") || toolName.contains("financial") ||
                                parameters.any { (name, value) -> 
                                    (name.contains("password") || name.contains("pin") || name.contains("密码")) &&
                                    (value.contains("pay") || value.contains("bank") || value.contains("支付") || 
                                     value.contains("银行") || value.contains("financial"))
                                }
        
        return isPaymentPassword
    }
    
    /**
     * Get human-readable description of an operation
     */
    fun getOperationDescription(tool: AITool): String {
        return operationDescriptionRegistry[tool.name]?.invoke(tool) ?: "${tool.name} 操作"
    }
    
    /**
     * Check if a tool is allowed to execute
     */
    suspend fun checkToolPermission(tool: AITool): Boolean {
        Log.d(TAG, "Starting permission check: ${tool.name}")
        
        // 检查是否是支付密码相关操作
        if (isDangerousOperation(tool)) {
            // 只有支付密码相关操作才需要用户确认
            return requestPermission(tool)
        }
        
        // 其他所有操作都直接允许
        return true
    }
    
    /**
     * Request permission from the user to execute a tool
     */
    private suspend fun requestPermission(tool: AITool): Boolean {
        // 只有涉及支付密码的操作才会进入这里
        val operationDescription = getOperationDescription(tool)
        
        Log.d(TAG, "Requesting permission for payment/password operation: ${tool.name}")
        
        // 清除现有请求
        currentPermissionCallback = null
        permissionRequestInfo = null
        _permissionRequestState.value = null
        
        // 设置新请求
        val requestInfo = Pair(tool, operationDescription)
        permissionRequestInfo = requestInfo
        _permissionRequestState.value = requestInfo
        
        Log.d(TAG, "Permission request state updated: ${tool.name}")
        
        return withTimeoutOrNull(PERMISSION_REQUEST_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                // 设置回调
                currentPermissionCallback = { result ->
                    Log.d(TAG, "Permission result received: $result for ${tool.name}")
                    // 清理状态
                    currentPermissionCallback = null
                    permissionRequestInfo = null
                    _permissionRequestState.value = null
                    
                    // 处理结果
                    when (result) {
                        PermissionRequestResult.ALLOW -> continuation.resume(true)
                        PermissionRequestResult.DENY -> continuation.resume(false)
                        PermissionRequestResult.DISCONNECT -> {
                            if (continuation.isActive) continuation.cancel()
                            continuation.resume(false)
                        }
                    }
                }
                
                // 在主线程开始权限请求
                mainHandler.post {
                    // 使用覆盖层显示权限请求
                    if (!permissionRequestOverlay.hasOverlayPermission()) {
                        Log.w(TAG, "No overlay permission, requesting...")
                        permissionRequestOverlay.requestOverlayPermission()
                        currentPermissionCallback?.invoke(PermissionRequestResult.DENY)
                    } else {
                        permissionRequestOverlay.show(tool, operationDescription) { result ->
                            handlePermissionResult(result)
                        }
                    }
                }
            }
        } ?: run {
            // 超时处理
            Log.d(TAG, "Permission request timed out: ${tool.name}")
            currentPermissionCallback = null
            permissionRequestInfo = null
            _permissionRequestState.value = null
            false
        }
    }
    
    /**
     * Handle permission request result
     */
    fun handlePermissionResult(result: PermissionRequestResult) {
        currentPermissionCallback?.invoke(result)
    }
    
    /**
     * Get current permission request info
     */
    fun getCurrentPermissionRequest(): Pair<AITool, String>? {
        return permissionRequestInfo
    }
    
    /**
     * Check if there is an active permission request
     */
    fun hasActivePermissionRequest(): Boolean {
        return permissionRequestInfo != null && currentPermissionCallback != null
    }
    
    /**
     * Refresh permission request state
     */
    fun refreshPermissionRequestState(): Boolean {
        return hasActivePermissionRequest()
    }
} 