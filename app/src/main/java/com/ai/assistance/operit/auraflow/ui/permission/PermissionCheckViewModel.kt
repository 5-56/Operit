package com.ai.assistance.operit.auraflow.ui.permission

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.auraflow.permission.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * 权限检查UI状态
 */
data class PermissionCheckUiState(
    val showWelcome: Boolean = true,
    val isLoading: Boolean = false,
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
    val errorMessage: String? = null
)

/**
 * 权限检查ViewModel
 */
class PermissionCheckViewModel : ViewModel() {
    
    companion object {
        private const val TAG = "PermissionCheckViewModel"
    }
    
    private val permissionManager = PermissionManager.getInstance()
    
    // UI状态
    private val _uiState = MutableStateFlow(PermissionCheckUiState())
    val uiState: StateFlow<PermissionCheckUiState> = _uiState.asStateFlow()
    
    // 权限状态
    val permissionStates: StateFlow<Map<PermissionType, PermissionState>> = 
        permissionManager.permissionStates
    
    init {
        // 初始化时检查权限状态
        viewModelScope.launch {
            Log.d(TAG, "PermissionCheckViewModel 初始化")
        }
    }
    
    /**
     * 开始权限设置流程
     */
    suspend fun startPermissionSetup(context: Context) {
        Log.d(TAG, "开始权限设置流程")
        
        _uiState.update { 
            it.copy(
                showWelcome = false,
                isLoading = true
            ) 
        }
        
        // 检查所有权限状态
        permissionManager.checkAllPermissions(context)
        
        delay(1000) // 等待检查完成
        
        _uiState.update { 
            it.copy(isLoading = false) 
        }
    }
    
    /**
     * 请求单个权限
     */
    suspend fun requestPermission(context: Context, permissionType: PermissionType) {
        Log.d(TAG, "请求权限: ${permissionType.displayName}")
        
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        
        try {
            val success = when (permissionType) {
                PermissionType.OVERLAY -> {
                    permissionManager.requestOverlayPermission(context)
                }
                PermissionType.ACCESSIBILITY -> {
                    permissionManager.requestAccessibilityPermission(context)
                }
                PermissionType.MEDIA_PROJECTION -> {
                    permissionManager.requestMediaProjectionPermission(context)
                }
                PermissionType.NOTIFICATION -> {
                    permissionManager.requestNotificationPermission(context)
                }
                PermissionType.STORAGE -> {
                    permissionManager.requestStoragePermission(context)
                }
                PermissionType.INTERNET -> {
                    // 网络权限在Manifest中声明，不需要运行时申请
                    true
                }
            }
            
            if (!success) {
                // 权限申请启动，等待用户操作
                delay(1000)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "请求权限失败: ${permissionType.displayName}", e)
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    errorMessage = "请求${permissionType.displayName}失败: ${e.message}"
                ) 
            }
        } finally {
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    /**
     * 一键请求所有缺失的必需权限
     */
    suspend fun requestAllMissingPermissions(context: Context) {
        Log.d(TAG, "一键请求所有缺失权限")
        
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        
        try {
            val missingPermissions = permissionManager.getMissingRequiredPermissions()
            
            for ((index, permissionType) in missingPermissions.withIndex()) {
                _uiState.update { 
                    it.copy(
                        currentStep = index + 1,
                        totalSteps = missingPermissions.size
                    ) 
                }
                
                Log.d(TAG, "请求权限 ${index + 1}/${missingPermissions.size}: ${permissionType.displayName}")
                
                // 请求权限
                requestPermission(context, permissionType)
                
                // 等待一段时间让用户处理
                delay(2000)
                
                // 检查权限状态
                permissionManager.checkAllPermissions(context)
                delay(500)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "批量请求权限失败", e)
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    errorMessage = "批量权限申请失败: ${e.message}"
                ) 
            }
        } finally {
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    currentStep = 0,
                    totalSteps = 0
                ) 
            }
        }
    }
    
    /**
     * 检查是否所有必需权限都已授权
     */
    fun areAllRequiredPermissionsGranted(): Boolean {
        return permissionManager.areAllRequiredPermissionsGranted()
    }
    
    /**
     * 获取权限申请进度
     */
    fun getPermissionProgress(): Pair<Int, Int> {
        val requiredPermissions = PermissionType.values().filter { it.isRequired }
        val grantedCount = requiredPermissions.count { type ->
            permissionManager.getPermissionStatus(type) == PermissionStatus.GRANTED
        }
        return Pair(grantedCount, requiredPermissions.size)
    }
    
    /**
     * 重新检查所有权限
     */
    fun recheckAllPermissions(context: Context) {
        viewModelScope.launch {
            Log.d(TAG, "重新检查所有权限")
            _uiState.update { it.copy(isLoading = true) }
            
            permissionManager.checkAllPermissions(context)
            delay(1000)
            
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    /**
     * 获取权限详细说明
     */
    fun getPermissionRationale(permissionType: PermissionType): String {
        return permissionManager.getPermissionRationale(permissionType)
    }
    
    /**
     * 重置权限设置流程
     */
    fun resetSetupFlow() {
        _uiState.update { 
            PermissionCheckUiState(showWelcome = true) 
        }
        Log.d(TAG, "权限设置流程已重置")
    }
    
    /**
     * 跳过权限设置
     */
    fun skipPermissionSetup() {
        Log.d(TAG, "用户选择跳过权限设置")
        // 可以在这里记录用户跳过的行为，用于后续提醒
    }
    
    /**
     * 获取权限设置建议
     */
    fun getPermissionSetupSuggestion(): String {
        val missingPermissions = permissionManager.getMissingRequiredPermissions()
        
        return when {
            missingPermissions.isEmpty() -> 
                "🎉 恭喜！所有必需权限都已设置完成，您可以开始使用 AuraFlow Agent 了！"
            
            missingPermissions.size == 1 -> 
                "还差一步！请授权${missingPermissions.first().displayName}以完成设置。"
            
            missingPermissions.size <= 3 -> 
                "即将完成！还需要授权 ${missingPermissions.joinToString("、") { it.displayName }} 权限。"
            
            else -> 
                "为了获得最佳体验，建议您授权所有必需权限。这些权限对 AuraFlow Agent 的正常运行非常重要。"
        }
    }
    
    /**
     * 处理权限申请结果
     */
    fun handlePermissionResult(permissionType: PermissionType, granted: Boolean) {
        Log.d(TAG, "权限申请结果: ${permissionType.displayName} -> $granted")
        
        if (granted) {
            // 权限授权成功，可以显示成功提示
            _uiState.update { 
                it.copy(errorMessage = null) 
            }
        } else {
            // 权限被拒绝，显示建议
            _uiState.update { 
                it.copy(
                    errorMessage = "${permissionType.displayName}被拒绝，这可能会影响应用功能。您可以稍后在设置中手动开启。"
                ) 
            }
        }
    }
    
    /**
     * 清除错误信息
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
    
    /**
     * 开始权限状态监控
     */
    fun startPermissionMonitoring(context: Context) {
        viewModelScope.launch {
            permissionManager.observePermissionChanges(context).collect { states ->
                Log.d(TAG, "权限状态更新: ${states.size} 个权限")
                // 权限状态已通过permissionStates流自动更新
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "PermissionCheckViewModel 清理")
    }
}