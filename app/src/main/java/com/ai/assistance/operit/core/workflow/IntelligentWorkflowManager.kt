package com.ai.assistance.operit.core.workflow

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

/**
 * 智能工作流管理器
 * 
 * 整合所有5个核心模块，实现完整的AI助手工作流系统：
 * 1. 智能指令理解系统 (IntelligentCommandProcessor)
 * 2. 任务分解与规划引擎 (TaskPlanningEngine)
 * 3. 代码生成与沙箱执行系统 (CodeExecutionEngine)
 * 4. 结果分析与智能反馈系统 (ResultAnalysisEngine)
 * 5. 自适应学习与优化系统 (AdaptiveLearningEngine)
 */
class IntelligentWorkflowManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "IntelligentWorkflowManager"
        
        @Volatile
        private var INSTANCE: IntelligentWorkflowManager? = null
        
        fun getInstance(context: Context): IntelligentWorkflowManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: IntelligentWorkflowManager(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        enum class WorkflowState {
            IDLE,
            UNDERSTANDING,
            PLANNING,
            GENERATING,
            EXECUTING,
            ANALYZING,
            LEARNING,
            COMPLETED,
            FAILED
        }
    }
    
    // 所有5个核心模块
    private val commandProcessor = IntelligentCommandProcessor.getInstance(context)
    private val planningEngine = TaskPlanningEngine.getInstance(context)
    private val codeExecutionEngine = CodeExecutionEngine.getInstance(context)
    private val resultAnalysisEngine = ResultAnalysisEngine.getInstance(context)
    private val learningEngine = AdaptiveLearningEngine.getInstance(context)
    
    // 工作流状态
    private val _workflowState = MutableStateFlow<WorkflowState>(WorkflowState.IDLE)
    val workflowState: StateFlow<WorkflowState> = _workflowState.asStateFlow()
    
    // 当前工作流会话
    private val _currentSession = MutableStateFlow<WorkflowSession?>(null)
    val currentSession: StateFlow<WorkflowSession?> = _currentSession.asStateFlow()
    
    // 进度更新
    private val _progressUpdate = MutableStateFlow<ProgressUpdate?>(null)
    val progressUpdate: StateFlow<ProgressUpdate?> = _progressUpdate.asStateFlow()
    
    /**
     * 工作流会话
     */
    data class WorkflowSession(
        val sessionId: String,
        val userInput: String,
        val taskDescription: IntelligentCommandProcessor.TaskDescription? = null,
        val executionPlan: TaskPlanningEngine.ExecutionPlan? = null,
        val generatedCode: CodeExecutionEngine.GeneratedCode? = null,
        val executionResult: CodeExecutionEngine.ExecutionResult? = null,
        val analysisReport: ResultAnalysisEngine.AnalysisReport? = null,
        val createdAt: Long = System.currentTimeMillis(),
        val completedAt: Long? = null,
        val success: Boolean = false
    )
    
    /**
     * 进度更新
     */
    data class ProgressUpdate(
        val stage: String,
        val message: String,
        val progress: Float, // 0.0 - 1.0
        val details: Map<String, Any> = emptyMap()
    )
    
    /**
     * 工作流完成回调
     */
    data class WorkflowResult(
        val session: WorkflowSession,
        val success: Boolean,
        val output: String,
        val analysisReport: ResultAnalysisEngine.AnalysisReport?,
        val recommendations: List<String> = emptyList(),
        val metadata: Map<String, Any> = emptyMap()
    )
    
    /**
     * 执行完整的智能工作流
     */
    suspend fun executeWorkflow(
        userInput: String,
        onProgress: ((ProgressUpdate) -> Unit)? = null,
        onOutput: ((String) -> Unit)? = null
    ): WorkflowResult {
        return withContext(Dispatchers.Main) {
            val sessionId = UUID.randomUUID().toString()
            var session = WorkflowSession(sessionId = sessionId, userInput = userInput)
            _currentSession.value = session
            
            try {
                Log.d(TAG, "开始执行智能工作流: $sessionId")
                
                // 阶段1: 智能指令理解
                _workflowState.value = WorkflowState.UNDERSTANDING
                updateProgress("理解指令", "正在分析用户输入...", 0.1f, onProgress)
                
                val taskDescription = commandProcessor.processUserInput(userInput)
                session = session.copy(taskDescription = taskDescription)
                _currentSession.value = session
                
                Log.d(TAG, "指令理解完成: ${taskDescription.intentType}, 置信度: ${taskDescription.confidence}")
                
                // 阶段2: 任务分解与规划
                _workflowState.value = WorkflowState.PLANNING
                updateProgress("制定计划", "正在分解任务并制定执行计划...", 0.2f, onProgress)
                
                val executionPlan = planningEngine.createExecutionPlan(taskDescription)
                session = session.copy(executionPlan = executionPlan)
                _currentSession.value = session
                
                Log.d(TAG, "任务规划完成: ${executionPlan.subTasks.size} 个子任务")
                
                // 阶段3: 代码生成
                _workflowState.value = WorkflowState.GENERATING
                updateProgress("生成代码", "正在根据计划生成执行代码...", 0.4f, onProgress)
                
                // 应用学习引擎的优化
                val optimizations = learningEngine.optimizeCodeGeneration(userInput, taskDescription.intentType)
                
                val codeRequest = CodeExecutionEngine.CodeGenerationRequest(
                    taskDescription = userInput,
                    codeType = CodeExecutionEngine.CodeType.PYTHON,
                    requiredLibraries = optimizations["recommended_libraries"] as? List<String> ?: 
                                      getDefaultLibraries(taskDescription.intentType),
                    inputData = taskDescription.extractedParameters,
                    constraints = taskDescription.constraints,
                    securityLevel = getSecurityLevel(taskDescription.intentType),
                    timeout = executionPlan.estimatedTotalTime.coerceAtLeast(10000L)
                )
                
                val generatedCode = codeExecutionEngine.generateCode(codeRequest)
                session = session.copy(generatedCode = generatedCode)
                _currentSession.value = session
                
                Log.d(TAG, "代码生成完成: ${generatedCode.id}")
                
                // 阶段4: 代码执行
                _workflowState.value = WorkflowState.EXECUTING
                updateProgress("执行代码", "正在安全沙箱中执行代码...", 0.6f, onProgress)
                
                val executionResult = codeExecutionEngine.executeCode(
                    generatedCode = generatedCode,
                    onOutput = onOutput
                )
                session = session.copy(executionResult = executionResult)
                _currentSession.value = session
                
                Log.d(TAG, "代码执行完成: 成功=${executionResult.success}")
                
                // 阶段5: 结果分析
                _workflowState.value = WorkflowState.ANALYZING
                updateProgress("分析结果", "正在分析执行结果并生成报告...", 0.8f, onProgress)
                
                val analysisReport = resultAnalysisEngine.analyzeResult(executionResult)
                session = session.copy(
                    analysisReport = analysisReport,
                    success = executionResult.success,
                    completedAt = System.currentTimeMillis()
                )
                _currentSession.value = session
                
                Log.d(TAG, "结果分析完成: 质量=${analysisReport.overallQuality}")
                
                // 阶段6: 学习与优化
                _workflowState.value = WorkflowState.LEARNING
                updateProgress("学习优化", "正在记录执行结果并更新学习模型...", 0.9f, onProgress)
                
                learningEngine.recordExecution(
                    taskDescription = userInput,
                    intentType = taskDescription.intentType,
                    complexityLevel = taskDescription.complexityLevel,
                    executionResult = executionResult,
                    generatedCode = generatedCode.sourceCode
                )
                
                // 完成
                _workflowState.value = WorkflowState.COMPLETED
                updateProgress("完成", "工作流执行完成", 1.0f, onProgress)
                
                val recommendations = generateRecommendations(session)
                
                Log.d(TAG, "智能工作流执行完成: $sessionId")
                
                WorkflowResult(
                    session = session,
                    success = executionResult.success,
                    output = formatOutput(session),
                    analysisReport = analysisReport,
                    recommendations = recommendations,
                    metadata = mapOf(
                        "total_time" to (System.currentTimeMillis() - session.createdAt),
                        "intent_confidence" to taskDescription.confidence,
                        "result_quality" to analysisReport.overallQuality.name
                    )
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "工作流执行失败", e)
                _workflowState.value = WorkflowState.FAILED
                
                // 即使失败也要记录学习数据
                session.taskDescription?.let { taskDesc ->
                    session.generatedCode?.let { code ->
                        val failureResult = CodeExecutionEngine.ExecutionResult(
                            codeId = code.id,
                            success = false,
                            output = "",
                            errorOutput = e.message ?: "未知错误",
                            exitCode = -1,
                            executionTime = 0L,
                            resourceUsage = emptyMap(),
                            exception = e.message
                        )
                        
                        runBlocking {
                            learningEngine.recordExecution(
                                taskDescription = userInput,
                                intentType = taskDesc.intentType,
                                complexityLevel = taskDesc.complexityLevel,
                                executionResult = failureResult,
                                generatedCode = code.sourceCode
                            )
                        }
                    }
                }
                
                WorkflowResult(
                    session = session.copy(success = false, completedAt = System.currentTimeMillis()),
                    success = false,
                    output = "执行失败: ${e.message}",
                    analysisReport = null,
                    recommendations = listOf("请检查输入并重试", "如果问题持续，请联系技术支持"),
                    metadata = mapOf("error" to (e.message ?: "未知错误"))
                )
            }
        }
    }
    
    /**
     * 获取学习统计信息
     */
    fun getLearningStatistics(): AdaptiveLearningEngine.LearningStatistics {
        return learningEngine.getLearningStatistics()
    }
    
    /**
     * 获取优化建议
     */
    fun getOptimizationSuggestions(): List<AdaptiveLearningEngine.OptimizationSuggestion> {
        return learningEngine.getOptimizationSuggestions()
    }
    
    /**
     * 提交用户反馈
     */
    suspend fun submitUserFeedback(
        sessionId: String,
        feedback: AdaptiveLearningEngine.UserFeedback
    ) {
        // 这里可以根据sessionId找到对应的执行记录并更新反馈
        Log.d(TAG, "收到用户反馈: $sessionId, 满意度: ${feedback.satisfaction}")
    }
    
    /**
     * 取消当前工作流
     */
    fun cancelWorkflow() {
        codeExecutionEngine.interruptExecution()
        _workflowState.value = WorkflowState.IDLE
        _currentSession.value = null
        Log.d(TAG, "工作流已取消")
    }
    
    private fun updateProgress(
        stage: String,
        message: String,
        progress: Float,
        onProgress: ((ProgressUpdate) -> Unit)?
    ) {
        val update = ProgressUpdate(stage, message, progress)
        _progressUpdate.value = update
        onProgress?.invoke(update)
    }
    
    private fun getDefaultLibraries(intentType: IntelligentCommandProcessor.Companion.IntentType): List<String> {
        return when (intentType) {
            IntelligentCommandProcessor.Companion.IntentType.DATA_ANALYSIS -> listOf("pandas", "numpy")
            IntelligentCommandProcessor.Companion.IntentType.PROGRAMMING_TASK -> listOf("json")
            IntelligentCommandProcessor.Companion.IntentType.WEB_SEARCH -> listOf("requests")
            IntelligentCommandProcessor.Companion.IntentType.CALCULATION -> listOf("math")
            else -> emptyList()
        }
    }
    
    private fun getSecurityLevel(intentType: IntelligentCommandProcessor.Companion.IntentType): CodeExecutionEngine.SecurityLevel {
        return when (intentType) {
            IntelligentCommandProcessor.Companion.IntentType.SYSTEM_QUERY -> CodeExecutionEngine.SecurityLevel.ELEVATED
            IntelligentCommandProcessor.Companion.IntentType.FILE_OPERATION -> CodeExecutionEngine.SecurityLevel.MODERATE
            IntelligentCommandProcessor.Companion.IntentType.WEB_SEARCH -> CodeExecutionEngine.SecurityLevel.ELEVATED
            else -> CodeExecutionEngine.SecurityLevel.SAFE
        }
    }
    
    private fun formatOutput(session: WorkflowSession): String {
        val output = StringBuilder()
        
        output.appendLine("🤖 智能工作流执行结果")
        output.appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━")
        
        // 任务信息
        session.taskDescription?.let { task ->
            output.appendLine("📋 任务类型: ${commandProcessor.getIntentDescription(task.intentType)}")
            output.appendLine("📊 复杂度: ${commandProcessor.getComplexityDescription(task.complexityLevel)}")
            output.appendLine("🎯 置信度: ${String.format("%.1f", task.confidence * 100)}%")
            output.appendLine()
        }
        
        // 执行计划
        session.executionPlan?.let { plan ->
            output.appendLine("📝 执行计划:")
            output.appendLine("   子任务数量: ${plan.subTasks.size}")
            output.appendLine("   预估时间: ${plan.estimatedTotalTime}ms")
            output.appendLine("   风险评估: ${plan.riskAssessment.riskLevel}")
            output.appendLine()
        }
        
        // 执行结果
        session.executionResult?.let { result ->
            output.appendLine("⚡ 执行结果:")
            output.appendLine("   状态: ${if (result.success) "✅ 成功" else "❌ 失败"}")
            output.appendLine("   执行时间: ${result.executionTime}ms")
            if (result.generatedFiles.isNotEmpty()) {
                output.appendLine("   生成文件: ${result.generatedFiles.size}个")
            }
            if (result.output.isNotEmpty()) {
                output.appendLine("   输出:")
                output.appendLine("   ${result.output.replace("\n", "\n   ")}")
            }
            if (result.errorOutput.isNotEmpty()) {
                output.appendLine("   错误: ${result.errorOutput}")
            }
            output.appendLine()
        }
        
        // 分析报告
        session.analysisReport?.let { report ->
            output.appendLine("📈 分析报告:")
            output.appendLine("   质量评级: ${getQualityDescription(report.overallQuality)}")
            output.appendLine("   成功率: ${String.format("%.1f", report.successRate * 100)}%")
            if (report.detectedIssues.isNotEmpty()) {
                output.appendLine("   检测问题: ${report.detectedIssues.size}个")
            }
            if (report.recommendations.isNotEmpty()) {
                output.appendLine("   优化建议: ${report.recommendations.size}个")
            }
        }
        
        return output.toString()
    }
    
    private fun generateRecommendations(session: WorkflowSession): List<String> {
        val recommendations = mutableListOf<String>()
        
        // 基于分析报告的建议
        session.analysisReport?.recommendations?.forEach { rec ->
            recommendations.add("${rec.category}: ${rec.title}")
        }
        
        // 基于学习引擎的建议
        learningEngine.getOptimizationSuggestions().forEach { suggestion ->
            recommendations.add("${suggestion.category}: ${suggestion.description}")
        }
        
        // 基于任务类型的通用建议
        session.taskDescription?.let { task ->
            when (task.intentType) {
                IntelligentCommandProcessor.Companion.IntentType.DATA_ANALYSIS -> {
                    recommendations.add("数据处理: 考虑使用更大的数据集进行验证")
                }
                IntelligentCommandProcessor.Companion.IntentType.PROGRAMMING_TASK -> {
                    recommendations.add("代码质量: 添加单元测试提高代码可靠性")
                }
                IntelligentCommandProcessor.Companion.IntentType.COMPLEX_WORKFLOW -> {
                    recommendations.add("工作流优化: 考虑将复杂任务分解为更小的步骤")
                }
                else -> {}
            }
        }
        
        return recommendations.distinct().take(5)
    }
    
    private fun getQualityDescription(quality: ResultAnalysisEngine.ResultQuality): String {
        return when (quality) {
            ResultAnalysisEngine.ResultQuality.EXCELLENT -> "优秀 🌟"
            ResultAnalysisEngine.ResultQuality.GOOD -> "良好 👍"
            ResultAnalysisEngine.ResultQuality.FAIR -> "一般 ⚠️"
            ResultAnalysisEngine.ResultQuality.POOR -> "较差 ❌"
            ResultAnalysisEngine.ResultQuality.CRITICAL -> "严重 🚨"
        }
    }
    
    /**
     * 获取系统状态概览
     */
    fun getSystemOverview(): Map<String, Any> {
        val stats = learningEngine.getLearningStatistics()
        val sandboxStatus = codeExecutionEngine.getSandboxStatus()
        
        return mapOf(
            "workflow_state" to _workflowState.value.name,
            "total_executions" to stats.totalExecutions,
            "success_rate" to stats.successRate,
            "learned_patterns" to stats.learnedPatterns,
            "sandbox_files" to (sandboxStatus["temp_files"] as Int + sandboxStatus["data_files"] as Int),
            "current_session" to (_currentSession.value?.sessionId ?: "none"),
            "system_health" to if (stats.successRate > 0.8f) "healthy" else "needs_attention"
        )
    }
}