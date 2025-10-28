package com.xihe.assistant.ui.permissions

import android.content.Context
import android.util.Log
import com.xihe.assistant.data.model.AITool
import java.util.concurrent.ConcurrentHashMap

/**
 * 工具权限系统
 * 管理工具的权限和安全性
 */
class ToolPermissionSystem private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ToolPermissionSystem"

        @Volatile private var INSTANCE: ToolPermissionSystem? = null

        fun getInstance(context: Context): ToolPermissionSystem {
            return INSTANCE
                ?: synchronized(this) {
                    INSTANCE ?: ToolPermissionSystem(context.applicationContext).also { INSTANCE = it }
                }
        }
    }

    // 危险操作检查器
    private val dangerousOperationCheckers = ConcurrentHashMap<String, (AITool) -> Boolean>()
    
    // 操作描述生成器
    private val operationDescriptionGenerators = ConcurrentHashMap<String, (AITool) -> String>()
    
    // 权限请求状态
    private var permissionRequestState = false

    /**
     * 初始化默认规则
     */
    fun initializeDefaultRules() {
        Log.d(TAG, "初始化工具权限系统默认规则")
    }

    /**
     * 注册危险操作检查器
     */
    fun registerDangerousOperation(toolName: String, checker: (AITool) -> Boolean) {
        dangerousOperationCheckers[toolName] = checker
        Log.d(TAG, "注册危险操作检查器: $toolName")
    }

    /**
     * 注册操作描述生成器
     */
    fun registerOperationDescription(toolName: String, generator: (AITool) -> String) {
        operationDescriptionGenerators[toolName] = generator
        Log.d(TAG, "注册操作描述生成器: $toolName")
    }

    /**
     * 检查操作是否危险
     */
    fun isDangerousOperation(tool: AITool): Boolean {
        val checker = dangerousOperationCheckers[tool.name]
        return checker?.invoke(tool) ?: false
    }

    /**
     * 获取操作描述
     */
    fun getOperationDescription(tool: AITool): String {
        val generator = operationDescriptionGenerators[tool.name]
        return generator?.invoke(tool) ?: "执行工具: ${tool.name}"
    }

    /**
     * 刷新权限请求状态
     */
    fun refreshPermissionRequestState(): Boolean {
        permissionRequestState = !permissionRequestState
        Log.d(TAG, "权限请求状态已刷新: $permissionRequestState")
        return permissionRequestState
    }
}