package com.ai.assistance.operit.ui.features.agent.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.core.agent.AgentCore
import com.ai.assistance.operit.core.agent.AgentPlan
import com.ai.assistance.operit.core.agent.AgentPlanStatus
import com.ai.assistance.operit.core.agent.AgentStep
import com.ai.assistance.operit.core.agent.AgentStepStatus
import com.ai.assistance.operit.core.agent.AgentStepType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Agent消息类型
 */
enum class AgentMessageType {
    USER,      // 用户消息
    AGENT,     // Agent响应
    SYSTEM,    // 系统消息
    ERROR      // 错误消息
}

/**
 * Agent消息
 */
data class AgentMessage(
    val id: String = UUID.randomUUID().toString(),
    val type: AgentMessageType,
    val content: String,
    val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
    val step: AgentStepInfo? = null
)

/**
 * Agent步骤信息（用于UI显示）
 */
data class AgentStepInfo(
    val id: String,
    val type: AgentStepType,
    val description: String,
    val status: AgentStepStatus,
    val hasScript: Boolean
)

/**
 * Agent计划信息（用于UI显示）
 */
data class AgentPlanInfo(
    val id: String,
    val title: String,
    val description: String,
    val status: AgentPlanStatus,
    val steps: List<AgentStepInfo>
)

/**
 * Agent UI状态
 */
data class AgentUiState(
    val messages: List<AgentMessage> = emptyList(),
    val currentPlan: AgentPlanInfo? = null,
    val isExecuting: Boolean = false,
    val error: String? = null
)

/**
 * Agent ViewModel - 管理Agent界面的状态和业务逻辑
 */
class AgentViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "AgentViewModel"
    }
    
    private val agentCore = AgentCore.getInstance(application)
    
    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()
    
    init {
        // 添加欢迎消息
        addMessage(
            AgentMessage(
                type = AgentMessageType.SYSTEM,
                content = "欢迎使用智能Agent助手！\n\n我可以帮您：\n• 理解和分析复杂任务\n• 自动生成执行计划\n• 编写和执行脚本代码\n• 根据结果进行优化改进\n\n请告诉我您需要完成什么任务。"
            )
        )
    }
    
    /**
     * 执行Agent任务
     */
    fun executeAgentTask(userRequest: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "开始执行Agent任务: $userRequest")
                
                // 添加用户消息
                addMessage(
                    AgentMessage(
                        type = AgentMessageType.USER,
                        content = userRequest
                    )
                )
                
                // 设置执行状态
                _uiState.value = _uiState.value.copy(
                    isExecuting = true,
                    error = null
                )
                
                // 执行Agent任务
                agentCore.processUserRequest(userRequest).collect { result ->
                    Log.d(TAG, "收到Agent结果: ${result.message}")
                    
                    // 添加Agent响应消息
                    val messageType = if (result.success) AgentMessageType.AGENT else AgentMessageType.ERROR
                    val stepInfo = result.currentStep?.let { step ->
                        AgentStepInfo(
                            id = step.id,
                            type = step.type,
                            description = step.description,
                            status = step.status,
                            hasScript = !step.script.isNullOrEmpty()
                        )
                    }
                    
                    addMessage(
                        AgentMessage(
                            type = messageType,
                            content = result.message,
                            step = stepInfo
                        )
                    )
                    
                    // 更新当前计划
                    val planInfo = result.plan?.let { plan ->
                        AgentPlanInfo(
                            id = plan.id,
                            title = plan.title,
                            description = plan.description,
                            status = plan.status,
                            steps = plan.steps.map { step ->
                                AgentStepInfo(
                                    id = step.id,
                                    type = step.type,
                                    description = step.description,
                                    status = step.status,
                                    hasScript = !step.script.isNullOrEmpty()
                                )
                            }
                        )
                    }
                    
                    _uiState.value = _uiState.value.copy(
                        currentPlan = planInfo,
                        isExecuting = result.plan?.status == AgentPlanStatus.EXECUTING
                    )
                }
                
                // 任务完成，清除执行状态
                _uiState.value = _uiState.value.copy(isExecuting = false)
                
            } catch (e: Exception) {
                Log.e(TAG, "执行Agent任务失败", e)
                
                addMessage(
                    AgentMessage(
                        type = AgentMessageType.ERROR,
                        content = "执行任务时发生错误: ${e.message}"
                    )
                )
                
                _uiState.value = _uiState.value.copy(
                    isExecuting = false,
                    error = e.message
                )
            }
        }
    }
    
    /**
     * 暂停当前计划
     */
    fun pauseCurrentPlan() {
        try {
            agentCore.pauseCurrentPlan()
            addMessage(
                AgentMessage(
                    type = AgentMessageType.SYSTEM,
                    content = "已暂停当前Agent计划"
                )
            )
            
            // 更新计划状态
            _uiState.value.currentPlan?.let { plan ->
                _uiState.value = _uiState.value.copy(
                    currentPlan = plan.copy(status = AgentPlanStatus.PAUSED),
                    isExecuting = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "暂停计划失败", e)
            addMessage(
                AgentMessage(
                    type = AgentMessageType.ERROR,
                    content = "暂停计划失败: ${e.message}"
                )
            )
        }
    }
    
    /**
     * 恢复当前计划
     */
    fun resumeCurrentPlan() {
        try {
            agentCore.resumeCurrentPlan()
            addMessage(
                AgentMessage(
                    type = AgentMessageType.SYSTEM,
                    content = "已恢复当前Agent计划"
                )
            )
            
            // 更新计划状态
            _uiState.value.currentPlan?.let { plan ->
                _uiState.value = _uiState.value.copy(
                    currentPlan = plan.copy(status = AgentPlanStatus.EXECUTING),
                    isExecuting = true
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "恢复计划失败", e)
            addMessage(
                AgentMessage(
                    type = AgentMessageType.ERROR,
                    content = "恢复计划失败: ${e.message}"
                )
            )
        }
    }
    
    /**
     * 取消当前计划
     */
    fun cancelCurrentPlan() {
        try {
            agentCore.cancelCurrentPlan()
            addMessage(
                AgentMessage(
                    type = AgentMessageType.SYSTEM,
                    content = "已取消当前Agent计划"
                )
            )
            
            _uiState.value = _uiState.value.copy(
                currentPlan = null,
                isExecuting = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "取消计划失败", e)
            addMessage(
                AgentMessage(
                    type = AgentMessageType.ERROR,
                    content = "取消计划失败: ${e.message}"
                )
            )
        }
    }
    
    /**
     * 清除所有消息
     */
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            messages = emptyList(),
            error = null
        )
        
        // 重新添加欢迎消息
        addMessage(
            AgentMessage(
                type = AgentMessageType.SYSTEM,
                content = "消息已清除。请告诉我您需要完成什么任务。"
            )
        )
    }
    
    /**
     * 获取执行历史
     */
    fun getExecutionHistory() {
        viewModelScope.launch {
            try {
                val history = agentCore.getExecutionHistory()
                val historyText = if (history.isEmpty()) {
                    "暂无执行历史"
                } else {
                    "最近的执行历史:\n\n" + history.takeLast(5).joinToString("\n\n") { plan ->
                        "• ${plan.title}\n  状态: ${plan.status}\n  步骤: ${plan.steps.size}个"
                    }
                }
                
                addMessage(
                    AgentMessage(
                        type = AgentMessageType.SYSTEM,
                        content = historyText
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "获取执行历史失败", e)
                addMessage(
                    AgentMessage(
                        type = AgentMessageType.ERROR,
                        content = "获取执行历史失败: ${e.message}"
                    )
                )
            }
        }
    }
    
    /**
     * 添加消息到列表
     */
    private fun addMessage(message: AgentMessage) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + message
        )
    }
}