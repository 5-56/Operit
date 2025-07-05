package com.ai.assistance.operit.core.agent

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.util.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import kotlin.coroutines.CoroutineContext

/**
 * Operit AI Agent 控制器
 * 
 * 这是AI Agent的核心控制组件，负责：
 * 1. 接收用户意图
 * 2. 感知屏幕信息
 * 3. 与AI大脑通信
 * 4. 执行AI指令
 * 5. 模拟用户操作
 * 6. 实时反馈与循环
 * 7. 展示AI思考过程
 */
class OperitAIAgentController private constructor(
    private val context: Context
) : CoroutineScope {
    
    companion object {
        private const val TAG = "OperitAIAgentController"
        
        @Volatile
        private var INSTANCE: OperitAIAgentController? = null
        
        fun getInstance(context: Context): OperitAIAgentController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OperitAIAgentController(context.applicationContext).also { 
                    INSTANCE = it 
                }
            }
        }
    }
    
    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Main
    
    // 核心组件
    private lateinit var screenPerception: EnhancedScreenPerception
    private lateinit var actionExecutor: IntelligentActionExecutor
    private lateinit var toolHandler: AIToolHandler
    
    // 状态管理
    private val _currentState = MutableStateFlow<AgentState>(AgentState.Idle)
    val currentState: StateFlow<AgentState> = _currentState.asStateFlow()
    
    private val _taskProgress = MutableStateFlow<TaskProgress?>(null)
    val taskProgress: StateFlow<TaskProgress?> = _taskProgress.asStateFlow()
    
    private val _aiThinking = MutableStateFlow<AIThinkingProcess?>(null)
    val aiThinking: StateFlow<AIThinkingProcess?> = _aiThinking.asStateFlow()
    
    // 初始化状态
    private var isInitialized = false
    
    /**
     * AI Agent状态定义
     */
    sealed class AgentState {
        object Idle : AgentState()
        object PerceivingScreen : AgentState()
        data class CommunicatingWithAI(val prompt: String) : AgentState()
        data class ExecutingInstructions(val instructions: List<AIInstruction>) : AgentState()
        object WaitingForFeedback : AgentState()
        data class TaskCompleted(val result: String) : AgentState()
        data class Error(val error: String) : AgentState()
    }
    
    /**
     * 用户意图数据类
     */
    data class UserIntent(
        val description: String,
        val priority: Priority = Priority.NORMAL,
        val constraints: List<String> = emptyList(),
        val expectedOutcome: String? = null,
        val timeout: Long = 300000L // 5分钟默认超时
    ) {
        enum class Priority {
            LOW, NORMAL, HIGH, URGENT
        }
    }
    
    /**
     * 任务进度数据类
     */
    data class TaskProgress(
        val taskId: String,
        val currentStep: Int,
        val totalSteps: Int,
        val currentOperation: String,
        val startTime: Long,
        val estimatedTimeRemaining: Long? = null
    )
    
    /**
     * AI思考过程数据类
     */
    data class AIThinkingProcess(
        val step: String,
        val reasoning: String,
        val confidence: Float,
        val nextAction: String,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * 任务执行结果
     */
    data class TaskResult(
        val success: Boolean,
        val result: String? = null,
        val error: String? = null,
        val executedSteps: List<AIInstruction> = emptyList(),
        val duration: Long = 0L,
        val screenChanges: Int = 0
    )
    
    init {
        initializeAgent()
    }
    
    /**
     * 初始化AI Agent
     */
    private fun initializeAgent() {
        if (isInitialized) return
        
        try {
            LogUtils.i(TAG, "开始初始化AI Agent控制器...")
            
            // 初始化核心组件
            toolHandler = AIToolHandler.getInstance(context)
            screenPerception = EnhancedScreenPerception(context)
            actionExecutor = IntelligentActionExecutor(context, toolHandler)
            
            isInitialized = true
            LogUtils.i(TAG, "✅ AI Agent控制器初始化完成")
            
        } catch (e: Exception) {
            LogUtils.e(TAG, "AI Agent初始化失败", e)
            _currentState.value = AgentState.Error("初始化失败: ${e.message}")
        }
    }
    
    /**
     * 执行用户意图
     */
    suspend fun executeUserIntent(userIntent: UserIntent): TaskResult {
        if (!isInitialized) {
            return TaskResult(false, error = "AI Agent未初始化")
        }
        
        val startTime = System.currentTimeMillis()
        val taskId = "task_${System.currentTimeMillis()}"
        var executedSteps = mutableListOf<AIInstruction>()
        
        try {
            LogUtils.i(TAG, "🚀 开始执行用户意图: ${userIntent.description}")
            
            // 设置超时
            return withTimeout(userIntent.timeout) {
                // 1. 感知屏幕信息
                _currentState.value = AgentState.PerceivingScreen
                updateProgress(taskId, 1, 6, "正在感知屏幕信息...")
                
                val screenData = screenPerception.getEnhancedScreenData(
                    includeScreenshot = true,
                    optimizeForAI = true
                )
                
                if (screenData == null) {
                    return@withTimeout TaskResult(false, error = "无法获取屏幕信息，请检查无障碍服务权限")
                }
                
                LogUtils.d(TAG, "✅ 屏幕信息感知完成")
                
                // 2. 与AI大脑通信
                val prompt = buildAIPrompt(userIntent, screenData)
                _currentState.value = AgentState.CommunicatingWithAI(prompt)
                updateProgress(taskId, 2, 6, "正在与AI大脑通信...")
                
                // 模拟AI思考过程
                updateAIThinking("分析用户意图", "理解用户想要执行的操作：${userIntent.description}", 0.9f, "分析当前屏幕状态")
                delay(1000)
                
                updateAIThinking("制定执行计划", "基于当前屏幕状态制定操作步骤", 0.85f, "生成具体操作指令")
                delay(1000)
                
                // 3. 生成AI指令（模拟）
                val instructions = generateAIInstructions(userIntent, screenData)
                updateProgress(taskId, 3, 6, "正在生成操作指令...")
                
                // 4. 执行指令
                _currentState.value = AgentState.ExecutingInstructions(instructions)
                updateProgress(taskId, 4, 6, "正在执行操作指令...")
                
                var successCount = 0
                for ((index, instruction) in instructions.withIndex()) {
                    updateProgress(taskId, 4, 6, "执行第${index + 1}/${instructions.size}个指令: ${instruction.description}")
                    
                    val success = actionExecutor.executeInstruction(instruction)
                    if (success) {
                        successCount++
                        executedSteps.add(instruction)
                        LogUtils.d(TAG, "✅ 指令执行成功: ${instruction.description}")
                    } else {
                        LogUtils.w(TAG, "⚠️ 指令执行失败: ${instruction.description}")
                    }
                    
                    delay(500) // 指令间短暂等待
                }
                
                // 5. 等待反馈和验证
                _currentState.value = AgentState.WaitingForFeedback
                updateProgress(taskId, 5, 6, "等待操作反馈...")
                delay(1000)
                
                // 6. 完成任务
                val duration = System.currentTimeMillis() - startTime
                val result = "✅ 任务执行完成！\n共执行 ${successCount}/${instructions.size} 个指令\n耗时 ${duration / 1000.0} 秒"
                
                _currentState.value = AgentState.TaskCompleted(result)
                updateProgress(taskId, 6, 6, "任务执行完成")
                
                LogUtils.i(TAG, "🎉 用户意图执行完成: $result")
                
                TaskResult(
                    success = successCount > 0,
                    result = result,
                    executedSteps = executedSteps,
                    duration = duration,
                    screenChanges = successCount
                )
            }
            
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            val error = "任务执行失败: ${e.message}"
            
            LogUtils.e(TAG, error, e)
            _currentState.value = AgentState.Error(error)
            
            return TaskResult(
                success = false,
                error = error,
                executedSteps = executedSteps,
                duration = duration
            )
        }
    }
    
    /**
     * 构建AI提示词
     */
    private fun buildAIPrompt(userIntent: UserIntent, screenData: EnhancedScreenPerception.ScreenPerceptionData): String {
        return buildString {
            appendLine("用户意图: ${userIntent.description}")
            appendLine("当前应用: ${screenData.contextInfo.currentApp}")
            appendLine("屏幕元素数量: ${screenData.uiStructure.elements.size}")
            appendLine("优先级: ${userIntent.priority}")
            
            if (userIntent.constraints.isNotEmpty()) {
                appendLine("约束条件: ${userIntent.constraints.joinToString(", ")}")
            }
            
            if (userIntent.expectedOutcome != null) {
                appendLine("期望结果: ${userIntent.expectedOutcome}")
            }
        }
    }
    
    /**
     * 生成AI指令（模拟实现）
     */
    private fun generateAIInstructions(
        userIntent: UserIntent, 
        screenData: EnhancedScreenPerception.ScreenPerceptionData
    ): List<AIInstruction> {
        // 这里是模拟的AI指令生成逻辑
        // 在实际实现中，这里应该调用真正的AI服务
        
        return when {
            userIntent.description.contains("等待", ignoreCase = true) -> {
                listOf(
                    AIInstruction(
                        type = "wait",
                        parameters = mapOf("duration" to "2000"),
                        description = "等待2秒"
                    )
                )
            }
            
            userIntent.description.contains("点击", ignoreCase = true) -> {
                // 寻找可点击的元素
                val clickableElements = screenData.uiStructure.elements.filter { it.isClickable }
                if (clickableElements.isNotEmpty()) {
                    val element = clickableElements.first()
                    listOf(
                        AIInstruction(
                            type = "tap",
                            parameters = mapOf(
                                "x" to element.bounds.centerX().toString(),
                                "y" to element.bounds.centerY().toString()
                            ),
                            description = "点击${element.text ?: "元素"}"
                        )
                    )
                } else {
                    listOf(
                        AIInstruction(
                            type = "wait",
                            parameters = mapOf("duration" to "1000"),
                            description = "未找到可点击元素，等待1秒"
                        )
                    )
                }
            }
            
            else -> {
                // 默认简单指令
                listOf(
                    AIInstruction(
                        type = "wait",
                        parameters = mapOf("duration" to "1000"),
                        description = "执行默认等待操作"
                    )
                )
            }
        }
    }
    
    /**
     * 更新任务进度
     */
    private fun updateProgress(taskId: String, current: Int, total: Int, operation: String) {
        _taskProgress.value = TaskProgress(
            taskId = taskId,
            currentStep = current,
            totalSteps = total,
            currentOperation = operation,
            startTime = System.currentTimeMillis()
        )
    }
    
    /**
     * 更新AI思考过程
     */
    private fun updateAIThinking(step: String, reasoning: String, confidence: Float, nextAction: String) {
        _aiThinking.value = AIThinkingProcess(
            step = step,
            reasoning = reasoning,
            confidence = confidence,
            nextAction = nextAction
        )
    }
    
    /**
     * 停止AI Agent
     */
    fun stopAgent() {
        LogUtils.i(TAG, "🛑 停止AI Agent")
        _currentState.value = AgentState.Idle
        _taskProgress.value = null
        _aiThinking.value = null
    }
    
    /**
     * 检查AI Agent是否忙碌
     */
    fun isBusy(): Boolean {
        return _currentState.value !is AgentState.Idle && _currentState.value !is AgentState.Error
    }
    
    /**
     * 获取当前状态
     */
    fun getCurrentState(): AgentState {
        return _currentState.value
    }
    
    /**
     * 重置AI Agent到空闲状态
     */
    fun reset() {
        LogUtils.i(TAG, "🔄 重置AI Agent到空闲状态")
        stopAgent()
    }
    
    /**
     * 获取AI Agent状态报告
     */
    fun getStatusReport(): String {
        val state = getCurrentState()
        val isInitialized = this.isInitialized
        
        return buildString {
            appendLine("🤖 AI Agent 状态报告")
            appendLine("=" * 30)
            appendLine("初始化状态: ${if (isInitialized) "✅ 已初始化" else "❌ 未初始化"}")
            appendLine("当前状态: ${getStateDescription(state)}")
            appendLine("忙碌状态: ${if (isBusy()) "🔄 执行中" else "💤 空闲"}")
            
            _taskProgress.value?.let { progress ->
                appendLine("")
                appendLine("📊 任务进度:")
                appendLine("  当前步骤: ${progress.currentStep}/${progress.totalSteps}")
                appendLine("  当前操作: ${progress.currentOperation}")
            }
            
            _aiThinking.value?.let { thinking ->
                appendLine("")
                appendLine("🧠 AI思考:")
                appendLine("  步骤: ${thinking.step}")
                appendLine("  推理: ${thinking.reasoning}")
                appendLine("  置信度: ${(thinking.confidence * 100).toInt()}%")
            }
        }
    }
    
    /**
     * 获取状态描述
     */
    private fun getStateDescription(state: AgentState): String {
        return when (state) {
            is AgentState.Idle -> "💤 空闲中"
            is AgentState.PerceivingScreen -> "👀 感知屏幕中"
            is AgentState.CommunicatingWithAI -> "🧠 与AI通信中"
            is AgentState.ExecutingInstructions -> "⚡ 执行指令中 (${state.instructions.size}个指令)"
            is AgentState.WaitingForFeedback -> "⏳ 等待反馈中"
            is AgentState.TaskCompleted -> "🎉 任务已完成"
            is AgentState.Error -> "❌ 错误: ${state.error}"
        }
    }
}