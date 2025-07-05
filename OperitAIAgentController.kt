package com.ai.assistance.operit.core.agent

import android.content.Context
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.util.LogUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Operit AI Agent 核心控制器
 * 
 * 实现完整的AI驱动自动化流程：
 * 1. 接收用户意图
 * 2. 感知屏幕信息  
 * 3. 与AI大脑通信
 * 4. 执行AI指令
 * 5. 模拟用户操作
 * 6. 实时反馈循环
 * 7. 展示AI思考过程
 */
class OperitAIAgentController private constructor(
    private val context: Context,
    private val toolHandler: AIToolHandler
) {
    
    companion object {
        private const val TAG = "OperitAIAgent"
        private const val MAX_TASK_EXECUTION_TIME = 300000L // 5分钟超时
        private const val FEEDBACK_LOOP_INTERVAL = 1000L // 1秒反馈间隔
        
        @Volatile
        private var INSTANCE: OperitAIAgentController? = null
        
        fun getInstance(context: Context): OperitAIAgentController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OperitAIAgentController(
                    context.applicationContext,
                    AIToolHandler.getInstance(context)
                ).also { INSTANCE = it }
            }
        }
    }
    
    // 核心组件
    private val screenPerception = EnhancedScreenPerception(context)
    private val actionExecutor = IntelligentActionExecutor(context, toolHandler)
    private val aiCommunicator = AIBrainCommunicator()
    private val taskStateManager = TaskStateManager()
    private val securityController = SecurityController(context)
    
    // 协程作用域
    private val agentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Agent状态流
    private val _agentState = MutableStateFlow(AgentState.Idle)
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()
    
    // 任务执行状态流
    private val _taskProgress = MutableSharedFlow<TaskProgress>(replay = 1)
    val taskProgress: SharedFlow<TaskProgress> = _taskProgress.asSharedFlow()
    
    // AI思考过程展示流
    private val _aiThinking = MutableSharedFlow<AIThinkingProcess>(replay = 1)
    val aiThinking: SharedFlow<AIThinkingProcess> = _aiThinking.asSharedFlow()
    
    /**
     * Agent状态
     */
    sealed class AgentState {
        object Idle : AgentState()
        object PerceivingScreen : AgentState()
        object CommunicatingWithAI : AgentState()
        object ExecutingInstructions : AgentState()
        object WaitingForFeedback : AgentState()
        data class TaskCompleted(val result: TaskResult) : AgentState()
        data class Error(val error: String) : AgentState()
    }
    
    /**
     * 任务进度
     */
    data class TaskProgress(
        val taskId: String,
        val currentStep: String,
        val progress: Float, // 0.0 - 1.0
        val totalSteps: Int,
        val completedSteps: Int,
        val estimatedTimeRemaining: Long? = null
    )
    
    /**
     * AI思考过程
     */
    data class AIThinkingProcess(
        val step: String,
        val reasoning: String,
        val confidence: Float, // 0.0 - 1.0
        val nextAction: String?,
        val alternatives: List<String> = emptyList()
    )
    
    /**
     * 用户意图
     */
    data class UserIntent(
        val description: String,
        val priority: Priority = Priority.NORMAL,
        val context: Map<String, Any> = emptyMap(),
        val constraints: List<String> = emptyList()
    ) {
        enum class Priority { LOW, NORMAL, HIGH, URGENT }
    }
    
    /**
     * 任务结果
     */
    data class TaskResult(
        val taskId: String,
        val success: Boolean,
        val result: String,
        val executedSteps: List<ExecutedStep>,
        val duration: Long,
        val error: String? = null
    )
    
    /**
     * 执行步骤
     */
    data class ExecutedStep(
        val stepId: String,
        val action: String,
        val parameters: Map<String, Any>,
        val result: String,
        val timestamp: Long,
        val success: Boolean
    )
    
    /**
     * 执行用户意图 - 核心入口方法
     */
    suspend fun executeUserIntent(intent: UserIntent): TaskResult = withContext(Dispatchers.IO) {
        val taskId = generateTaskId()
        LogUtils.i(TAG, "开始执行用户意图: ${intent.description}")
        
        try {
            // 1. 初始化任务
            _agentState.value = AgentState.PerceivingScreen
            taskStateManager.startTask(taskId, intent)
            
            // 2. 执行主要任务循环
            val result = executeTaskLoop(taskId, intent)
            
            // 3. 更新最终状态
            _agentState.value = AgentState.TaskCompleted(result)
            taskStateManager.completeTask(taskId, result)
            
            LogUtils.i(TAG, "用户意图执行完成: ${result.success}")
            return@withContext result
            
        } catch (e: Exception) {
            LogUtils.e(TAG, "执行用户意图失败", e)
            
            val errorResult = TaskResult(
                taskId = taskId,
                success = false,
                result = "",
                executedSteps = taskStateManager.getExecutedSteps(taskId),
                duration = System.currentTimeMillis() - taskStateManager.getTaskStartTime(taskId),
                error = e.message
            )
            
            _agentState.value = AgentState.Error(e.message ?: "Unknown error")
            taskStateManager.failTask(taskId, e.message ?: "Unknown error")
            
            return@withContext errorResult
        }
    }
    
    /**
     * 核心任务执行循环
     */
    private suspend fun executeTaskLoop(taskId: String, intent: UserIntent): TaskResult = withContext(Dispatchers.IO) {
        var currentStep = 1
        val maxSteps = 50 // 最大步骤数防止无限循环
        val executedSteps = mutableListOf<ExecutedStep>()
        val startTime = System.currentTimeMillis()
        
        // 初始屏幕感知
        var currentScreenData = captureAndAnalyzeScreen()
        
        while (currentStep <= maxSteps) {
            LogUtils.d(TAG, "执行任务循环 - 步骤 $currentStep")
            
            // 更新进度
            updateTaskProgress(taskId, currentStep, maxSteps, "分析当前状态")
            
            // 1. 与AI大脑通信，获取下一步指令
            _agentState.value = AgentState.CommunicatingWithAI
            val aiResponse = communicateWithAIBrain(intent, currentScreenData, executedSteps)
            
            // 2. 展示AI思考过程
            showAIThinkingProcess(aiResponse.thinkingProcess)
            
            // 3. 检查任务是否完成
            if (aiResponse.taskCompleted) {
                LogUtils.i(TAG, "AI判断任务已完成")
                break
            }
            
            // 4. 安全检查AI指令
            val securityCheckResult = securityController.checkInstructions(aiResponse.instructions)
            if (!securityCheckResult.approved) {
                throw SecurityException("AI指令被安全系统拦截: ${securityCheckResult.reason}")
            }
            
            // 5. 执行AI指令
            _agentState.value = AgentState.ExecutingInstructions
            val instructionResults = executeInstructions(aiResponse.instructions)
            
            // 6. 记录执行步骤
            instructionResults.forEach { result ->
                executedSteps.add(
                    ExecutedStep(
                        stepId = "step_${currentStep}_${executedSteps.size}",
                        action = result.instruction.type,
                        parameters = result.instruction.parameters,
                        result = result.result?.result?.toString() ?: "",
                        timestamp = System.currentTimeMillis(),
                        success = result.result?.success ?: false
                    )
                )
            }
            
            // 7. 等待操作完成并获取新的屏幕状态
            _agentState.value = AgentState.WaitingForFeedback
            delay(FEEDBACK_LOOP_INTERVAL)
            
            // 8. 重新感知屏幕状态
            currentScreenData = captureAndAnalyzeScreen()
            
            currentStep++
            
            // 检查超时
            if (System.currentTimeMillis() - startTime > MAX_TASK_EXECUTION_TIME) {
                throw TimeoutException("任务执行超时")
            }
        }
        
        // 构造最终结果
        return@withContext TaskResult(
            taskId = taskId,
            success = true,
            result = "任务执行完成，共执行 ${executedSteps.size} 个步骤",
            executedSteps = executedSteps,
            duration = System.currentTimeMillis() - startTime
        )
    }
    
    /**
     * 捕获和分析屏幕状态
     */
    private suspend fun captureAndAnalyzeScreen(): EnhancedScreenPerception.ScreenPerceptionData? {
        LogUtils.d(TAG, "捕获和分析屏幕状态")
        
        return try {
            screenPerception.getEnhancedScreenData(
                includeScreenshot = true,
                optimizeForAI = true
            )
        } catch (e: Exception) {
            LogUtils.e(TAG, "屏幕感知失败", e)
            null
        }
    }
    
    /**
     * 与AI大脑通信
     */
    private suspend fun communicateWithAIBrain(
        intent: UserIntent,
        screenData: EnhancedScreenPerception.ScreenPerceptionData?,
        executedSteps: List<ExecutedStep>
    ): AIResponse {
        LogUtils.d(TAG, "与AI大脑通信")
        
        val requestData = JSONObject().apply {
            put("user_intent", intent.description)
            put("priority", intent.priority.name)
            put("constraints", JSONArray(intent.constraints))
            
            // 屏幕数据
            screenData?.let { data ->
                put("screen_data", screenPerception.toAIFormat(data))
            }
            
            // 执行历史
            put("executed_steps", JSONArray().apply {
                executedSteps.takeLast(5).forEach { step -> // 只发送最近5步
                    put(JSONObject().apply {
                        put("action", step.action)
                        put("parameters", JSONObject(step.parameters))
                        put("result", step.result)
                        put("success", step.success)
                    })
                }
            })
        }
        
        return aiCommunicator.sendRequest(requestData)
    }
    
    /**
     * 执行AI指令序列
     */
    private suspend fun executeInstructions(
        instructions: List<AIInstruction>
    ): List<InstructionExecutionResult> {
        LogUtils.d(TAG, "执行 ${instructions.size} 条AI指令")
        
        val results = mutableListOf<InstructionExecutionResult>()
        
        for ((index, instruction) in instructions.withIndex()) {
            try {
                updateTaskProgress(
                    getCurrentTaskId(),
                    index + 1,
                    instructions.size,
                    "执行指令: ${instruction.type}"
                )
                
                val result = actionExecutor.executeAIInstruction(instruction)
                results.add(InstructionExecutionResult(instruction, result.result))
                
                // 指令间延迟
                if (index < instructions.size - 1) {
                    delay(500)
                }
                
            } catch (e: Exception) {
                LogUtils.e(TAG, "执行指令失败: ${instruction.type}", e)
                results.add(InstructionExecutionResult(instruction, null))
            }
        }
        
        return results
    }
    
    /**
     * 展示AI思考过程
     */
    private suspend fun showAIThinkingProcess(thinkingProcess: AIThinkingProcess) {
        _aiThinking.emit(thinkingProcess)
        LogUtils.d(TAG, "AI思考: ${thinkingProcess.reasoning}")
    }
    
    /**
     * 更新任务进度
     */
    private suspend fun updateTaskProgress(
        taskId: String,
        currentStep: Int,
        totalSteps: Int,
        stepDescription: String
    ) {
        val progress = currentStep.toFloat() / totalSteps
        val progressData = TaskProgress(
            taskId = taskId,
            currentStep = stepDescription,
            progress = progress,
            totalSteps = totalSteps,
            completedSteps = currentStep - 1
        )
        
        _taskProgress.emit(progressData)
    }
    
    /**
     * 暂停Agent执行
     */
    fun pauseAgent() {
        LogUtils.i(TAG, "暂停Agent执行")
        // TODO: 实现暂停逻辑
    }
    
    /**
     * 恢复Agent执行
     */
    fun resumeAgent() {
        LogUtils.i(TAG, "恢复Agent执行")
        // TODO: 实现恢复逻辑
    }
    
    /**
     * 停止Agent执行
     */
    fun stopAgent() {
        LogUtils.i(TAG, "停止Agent执行")
        agentScope.cancel()
        actionExecutor.release()
        _agentState.value = AgentState.Idle
    }
    
    /**
     * 获取Agent当前状态
     */
    fun getCurrentState(): AgentState = _agentState.value
    
    /**
     * 检查Agent是否忙碌
     */
    fun isBusy(): Boolean = _agentState.value != AgentState.Idle
    
    // 辅助方法
    private fun generateTaskId(): String = "task_${System.currentTimeMillis()}_${(0..999).random()}"
    private fun getCurrentTaskId(): String = taskStateManager.getCurrentTaskId() ?: "unknown"
    
    /**
     * 指令执行结果
     */
    private data class InstructionExecutionResult(
        val instruction: AIInstruction,
        val result: com.ai.assistance.operit.data.model.ToolResult?
    )
}

/**
 * AI响应数据
 */
data class AIResponse(
    val taskCompleted: Boolean,
    val instructions: List<AIInstruction>,
    val thinkingProcess: AIThinkingProcess,
    val confidence: Float,
    val nextExpectedState: String? = null
)

/**
 * AI大脑通信器
 */
class AIBrainCommunicator {
    
    companion object {
        private const val TAG = "AIBrainCommunicator"
    }
    
    suspend fun sendRequest(requestData: JSONObject): AIResponse {
        // TODO: 实现实际的AI大脑通信逻辑
        LogUtils.d(TAG, "发送请求到AI大脑")
        
        // 模拟AI响应
        delay(1000)
        
        return AIResponse(
            taskCompleted = false,
            instructions = listOf(
                AIInstruction(
                    type = "tap",
                    parameters = mapOf("x" to "500", "y" to "800"),
                    description = "点击屏幕中心"
                )
            ),
            thinkingProcess = AIThinkingProcess(
                step = "分析屏幕状态",
                reasoning = "检测到用户想要执行点击操作，目标坐标已确定",
                confidence = 0.85f,
                nextAction = "执行点击操作"
            ),
            confidence = 0.85f
        )
    }
}

/**
 * 任务状态管理器
 */
class TaskStateManager {
    
    private val tasks = mutableMapOf<String, TaskInfo>()
    
    data class TaskInfo(
        val taskId: String,
        val intent: UserIntent,
        val startTime: Long,
        val executedSteps: MutableList<ExecutedStep> = mutableListOf(),
        var status: TaskStatus = TaskStatus.RUNNING
    )
    
    enum class TaskStatus { RUNNING, COMPLETED, FAILED, CANCELLED }
    
    fun startTask(taskId: String, intent: UserIntent) {
        tasks[taskId] = TaskInfo(taskId, intent, System.currentTimeMillis())
    }
    
    fun completeTask(taskId: String, result: TaskResult) {
        tasks[taskId]?.status = TaskStatus.COMPLETED
    }
    
    fun failTask(taskId: String, error: String) {
        tasks[taskId]?.status = TaskStatus.FAILED
    }
    
    fun getExecutedSteps(taskId: String): List<ExecutedStep> {
        return tasks[taskId]?.executedSteps ?: emptyList()
    }
    
    fun getTaskStartTime(taskId: String): Long {
        return tasks[taskId]?.startTime ?: System.currentTimeMillis()
    }
    
    fun getCurrentTaskId(): String? {
        return tasks.values.find { it.status == TaskStatus.RUNNING }?.taskId
    }
}

/**
 * 安全控制中心
 */
class SecurityController(private val context: Context) {
    
    companion object {
        private const val TAG = "SecurityController"
    }
    
    data class SecurityCheckResult(
        val approved: Boolean,
        val reason: String? = null
    )
    
    fun checkInstructions(instructions: List<AIInstruction>): SecurityCheckResult {
        // TODO: 实现安全检查逻辑
        LogUtils.d(TAG, "安全检查 ${instructions.size} 条指令")
        
        // 简单的安全检查示例
        val dangerousKeywords = listOf("delete", "uninstall", "format", "reset")
        
        instructions.forEach { instruction ->
            instruction.parameters.values.forEach { value ->
                dangerousKeywords.forEach { keyword ->
                    if (value.contains(keyword, ignoreCase = true)) {
                        return SecurityCheckResult(
                            approved = false,
                            reason = "检测到危险操作关键词: $keyword"
                        )
                    }
                }
            }
        }
        
        return SecurityCheckResult(approved = true)
    }
}