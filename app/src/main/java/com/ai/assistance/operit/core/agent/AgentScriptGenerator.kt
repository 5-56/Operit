package com.ai.assistance.operit.core.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.javascript.JsEngine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

/**
 * 执行结果数据模型
 */
@Serializable
data class ExecutionResult(
    val success: Boolean,
    val message: String,
    val data: Map<String, String>? = null,
    val executionTime: Long = 0,
    val memoryUsage: Long = 0,
    val errorType: String? = null
)

/**
 * 脚本质量评估结果
 */
@Serializable
data class QualityAssessment(
    val score: Float,
    val reliability: Float,
    val performance: Float,
    val security: Float,
    val readability: Float,
    val suggestions: List<String> = emptyList()
)

/**
 * Agent执行上下文
 */
data class AgentContext(
    val sessionId: String = UUID.randomUUID().toString(),
    val userRequest: String,
    val planSteps: List<String>? = null,
    val config: AgentConfig,
    val iterationHistory: MutableList<AgentIteration> = mutableListOf(),
    val learnedPatterns: MutableMap<String, Any> = mutableMapOf(),
    val performanceMetrics: MutableMap<String, Double> = mutableMapOf()
)

/**
 * Agent迭代记录
 */
data class AgentIteration(
    val iteration: Int,
    val script: String,
    val result: ExecutionResult,
    val quality: QualityAssessment,
    val optimizations: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 智能化的AgentScriptGenerator，具备学习、优化、错误恢复等高级能力
 */
object AgentScriptGenerator {
    private const val TAG = "AgentScriptGenerator"
    
    // 全局缓存和学习系统
    private val scriptCache = ConcurrentHashMap<String, String>()
    private val resultCache = ConcurrentHashMap<String, ExecutionResult>()
    private val learningDatabase = ConcurrentHashMap<String, MutableList<AgentIteration>>()
    private val performanceStats = ConcurrentHashMap<String, AtomicInteger>()
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    /**
     * 智能Agent主流程 - 支持学习、优化、错误恢复的完整执行流程
     */
    suspend fun executeIntelligentAgent(
        userRequest: String,
        planSteps: List<String>? = null,
        config: AgentConfig = AgentConfig.default(),
        context: Context? = null
    ): AgentExecutionResult = withContext(Dispatchers.Default) {
        
        // 验证配置
        val configErrors = config.validate()
        if (configErrors.isNotEmpty()) {
            return@withContext AgentExecutionResult.failure("配置错误: ${configErrors.joinToString(", ")}")
        }
        
        val agentContext = AgentContext(
            userRequest = userRequest,
            planSteps = planSteps,
            config = config
        )
        
        Log.i(TAG, "开始智能Agent执行: ${agentContext.sessionId}")
        
        try {
            // 预处理阶段
            config.preProcessHook?.invoke(userRequest)
            
            // 上下文学习 - 从历史中学习相似请求的处理方式
            if (config.enableContextLearning) {
                learnFromHistory(agentContext)
            }
            
            val llmService = createLLMService(config)
            var bestScript = ""
            var bestResult: ExecutionResult? = null
            var bestQuality = 0f
            
            // 主执行循环
            repeat(config.maxIterations) { iteration ->
                try {
                    Log.d(TAG, "开始第${iteration + 1}轮执行")
                    
                    // 生成或优化脚本
                    val script = if (iteration == 0) {
                        generateIntelligentScript(agentContext, llmService)
                    } else {
                        optimizeScriptIntelligently(agentContext, llmService, bestScript, bestResult)
                    }
                    
                    // 安全检查
                    if (config.enableSecurityChecks && !passSecurityCheck(script)) {
                        Log.w(TAG, "脚本未通过安全检查，跳过执行")
                        continue
                    }
                    
                    // 执行脚本
                    val result = executeScriptSafely(script, agentContext, context)
                    
                    // 质量评估
                    val quality = assessScriptQuality(script, result, agentContext)
                    
                    // 记录迭代
                    val agentIteration = AgentIteration(
                        iteration = iteration,
                        script = script,
                        result = result,
                        quality = quality
                    )
                    agentContext.iterationHistory.add(agentIteration)
                    
                    // 更新最佳结果
                    if (quality.score > bestQuality) {
                        bestScript = script
                        bestResult = result
                        bestQuality = quality.score
                    }
                    
                    // 性能分析和自适应学习
                    if (config.enablePerformanceAnalysis) {
                        analyzePerformance(agentContext, agentIteration)
                    }
                    
                    if (config.enableAdaptiveLearning) {
                        updateLearningDatabase(agentContext, agentIteration)
                    }
                    
                    // 成功终止条件
                    if (config.autoTerminateOnSuccess && 
                        result.success && 
                        quality.score >= config.qualityThreshold) {
                        Log.i(TAG, "达到质量阈值，提前终止")
                        break
                    }
                    
                    // 进度反馈
                    if (config.enableProgressFeedback) {
                        config.postProcessHook?.invoke(script, result.message)
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "第${iteration + 1}轮执行出错", e)
                    
                    if (config.enableErrorRecovery) {
                        handleExecutionError(agentContext, e, iteration)
                    } else {
                        config.errorHook?.invoke(e, "iteration_$iteration")
                        break
                    }
                }
            }
            
            // 最终保存和清理
            val finalScript = bestScript.ifEmpty { generateFallbackScript(agentContext) }
            val finalResult = bestResult ?: ExecutionResult(false, "所有执行尝试均失败")
            
            if (config.enableScriptCaching) {
                saveToCache(agentContext, finalScript, finalResult)
            }
            
            AgentScriptSaver.saveScript(finalScript, userRequest)
            
            Log.i(TAG, "Agent执行完成: ${agentContext.sessionId}, 最终质量分数: $bestQuality")
            
            return@withContext AgentExecutionResult.success(
                script = finalScript,
                result = finalResult,
                iterations = agentContext.iterationHistory.size,
                qualityScore = bestQuality,
                sessionId = agentContext.sessionId
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Agent执行失败", e)
            config.errorHook?.invoke(e, agentContext.sessionId)
            return@withContext AgentExecutionResult.failure("Agent执行失败: ${e.message}")
        }
    }

    /**
     * 智能脚本生成 - 利用上下文信息和学习数据
     */
    private suspend fun generateIntelligentScript(
        agentContext: AgentContext,
        llmService: LLMService
    ): String {
        val cacheKey = generateCacheKey(agentContext.userRequest, agentContext.planSteps)
        
        // 尝试从缓存获取
        if (agentContext.config.enableScriptCaching) {
            scriptCache[cacheKey]?.let { cachedScript ->
                Log.d(TAG, "从缓存获取脚本")
                return cachedScript
            }
        }
        
        // 构建智能提示词
        val enhancedPrompt = buildIntelligentPrompt(agentContext)
        
        // 生成脚本
        val script = llmService.generateScript(enhancedPrompt)
        
        // 缓存结果
        if (agentContext.config.enableScriptCaching) {
            scriptCache[cacheKey] = script
        }
        
        return script
    }

    /**
     * 智能脚本优化 - 基于执行结果和学习数据进行优化
     */
    private suspend fun optimizeScriptIntelligently(
        agentContext: AgentContext,
        llmService: LLMService,
        lastScript: String,
        lastResult: ExecutionResult?
    ): String {
        val optimizationContext = buildOptimizationContext(agentContext, lastScript, lastResult)
        
        return llmService.optimizeScript(lastScript, optimizationContext)
    }

    /**
     * 安全执行脚本
     */
    private suspend fun executeScriptSafely(
        script: String,
        agentContext: AgentContext,
        context: Context?
    ): ExecutionResult = withContext(Dispatchers.IO) {
        
        val startTime = System.currentTimeMillis()
        val startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        
        try {
            if (context == null) {
                return@withContext ExecutionResult(
                    success = false,
                    message = "执行上下文未提供",
                    executionTime = System.currentTimeMillis() - startTime
                )
            }
            
            // 超时控制
            val timeoutMillis = agentContext.config.timeoutSeconds * 1000L
            
            val result = if (agentContext.config.executeInSandbox) {
                executeSandboxed(script, context, timeoutMillis)
            } else {
                executeDirectly(script, context)
            }
            
            val endTime = System.currentTimeMillis()
            val endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            
            return@withContext result.copy(
                executionTime = endTime - startTime,
                memoryUsage = endMemory - startMemory
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "脚本执行异常", e)
            return@withContext ExecutionResult(
                success = false,
                message = "脚本执行异常: ${e.message}",
                executionTime = System.currentTimeMillis() - startTime,
                errorType = e.javaClass.simpleName
            )
        }
    }

    /**
     * 沙箱执行
     */
    private suspend fun executeSandboxed(
        script: String,
        context: Context,
        timeoutMillis: Long
    ): ExecutionResult = coroutineScope {
        
        val executionJob = async {
            val jsEngine = JsEngine(context)
            val result = jsEngine.executeScriptFunction(script, "main", mapOf())
            
            when (result) {
                is Map<*, *> -> {
                    val success = result["success"] as? Boolean ?: false
                    val message = result["message"] as? String ?: ""
                    val data = result["data"] as? Map<String, String>
                    
                    ExecutionResult(success, message, data)
                }
                else -> ExecutionResult(true, result?.toString() ?: "执行完成")
            }
        }
        
        try {
            kotlinx.coroutines.withTimeout(timeoutMillis) {
                executionJob.await()
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            executionJob.cancel()
            ExecutionResult(false, "执行超时", errorType = "TimeoutException")
        }
    }

    /**
     * 直接执行
     */
    private suspend fun executeDirectly(script: String, context: Context): ExecutionResult {
        val jsEngine = JsEngine(context)
        val result = jsEngine.executeScriptFunction(script, "main", mapOf())
        
        return when (result) {
            is Map<*, *> -> {
                val success = result["success"] as? Boolean ?: false
                val message = result["message"] as? String ?: ""
                val data = result["data"] as? Map<String, String>
                
                ExecutionResult(success, message, data)
            }
            else -> ExecutionResult(true, result?.toString() ?: "执行完成")
        }
    }

    /**
     * 脚本质量评估
     */
    private suspend fun assessScriptQuality(
        script: String,
        result: ExecutionResult,
        agentContext: AgentContext
    ): QualityAssessment = withContext(Dispatchers.Default) {
        
        var reliability = if (result.success) 0.8f else 0.2f
        var performance = min(1.0f, 10000f / (result.executionTime + 1))
        var security = if (passSecurityCheck(script)) 0.9f else 0.3f
        var readability = assessReadability(script)
        
        // 基于历史数据调整评分
        if (agentContext.config.enableContextLearning) {
            adjustScoreBasedOnHistory(agentContext, reliability, performance)
        }
        
        val overallScore = (reliability * 0.4f + performance * 0.2f + security * 0.2f + readability * 0.2f)
        
        val suggestions = mutableListOf<String>()
        if (reliability < 0.7f) suggestions.add("提高代码可靠性")
        if (performance < 0.5f) suggestions.add("优化执行性能")
        if (security < 0.7f) suggestions.add("加强安全检查")
        if (readability < 0.6f) suggestions.add("改善代码可读性")
        
        QualityAssessment(
            score = overallScore,
            reliability = reliability,
            performance = performance,
            security = security,
            readability = readability,
            suggestions = suggestions
        )
    }

    /**
     * 从历史学习
     */
    private fun learnFromHistory(agentContext: AgentContext) {
        val similarRequests = findSimilarRequests(agentContext.userRequest)
        
        for ((request, iterations) in similarRequests) {
            val bestIteration = iterations.maxByOrNull { it.quality.score }
            bestIteration?.let { iteration ->
                // 提取成功模式
                agentContext.learnedPatterns["similar_${request.hashCode()}"] = mapOf(
                    "script_pattern" to extractScriptPattern(iteration.script),
                    "quality_score" to iteration.quality.score,
                    "success_factors" to iteration.optimizations
                )
            }
        }
    }

    /**
     * 安全检查
     */
    private fun passSecurityCheck(script: String): Boolean {
        val dangerousPatterns = listOf(
            "eval\\s*\\(",
            "new\\s+Function",
            "document\\.write",
            "innerHTML\\s*=",
            "localStorage",
            "sessionStorage",
            "window\\.",
            "global\\.",
            "process\\.",
            "require\\s*\\(",
            "__proto__"
        )
        
        return dangerousPatterns.none { pattern ->
            script.contains(Regex(pattern, RegexOption.IGNORE_CASE))
        }
    }

    /**
     * 可读性评估
     */
    private fun assessReadability(script: String): Float {
        val lines = script.lines()
        val totalLines = lines.size
        val commentLines = lines.count { it.trim().startsWith("//") || it.trim().startsWith("/*") }
        val emptyLines = lines.count { it.trim().isEmpty() }
        val longLines = lines.count { it.length > 120 }
        
        var score = 0.7f
        
        // 注释率
        val commentRatio = if (totalLines > 0) commentLines.toFloat() / totalLines else 0f
        score += if (commentRatio > 0.1f) 0.2f else 0f
        
        // 空行比例（适当的空行有助于可读性）
        val emptyLineRatio = if (totalLines > 0) emptyLines.toFloat() / totalLines else 0f
        score += if (emptyLineRatio in 0.05f..0.2f) 0.1f else 0f
        
        // 长行惩罚
        if (longLines > totalLines * 0.3) score -= 0.2f
        
        return score.coerceIn(0f, 1f)
    }

    // === 辅助方法 ===

    private fun createLLMService(config: AgentConfig): LLMService {
        return when (config.llmProvider.lowercase()) {
            "qwen" -> QwenLLMService(config.llmApiKey, config.llmEndpoint, config.llmModel.ifEmpty { "qwen-plus" })
            "claude" -> ClaudeLLMService(config.llmApiKey, config.llmEndpoint, config.llmModel.ifEmpty { "claude-3-sonnet-20240229" })
            "local" -> LocalLLMService(config.llmApiKey, config.llmEndpoint, config.llmModel.ifEmpty { "local-model" })
            else -> OpenAILLMService(config.llmApiKey, config.llmEndpoint, config.llmModel.ifEmpty { "gpt-3.5-turbo" })
        }
    }

    private fun buildIntelligentPrompt(agentContext: AgentContext): String {
        val basePrompt = "用户需求: ${agentContext.userRequest}"
        val planContext = agentContext.planSteps?.joinToString("\n", "计划步骤:\n") { "- $it" } ?: ""
        
        val learnedContext = if (agentContext.learnedPatterns.isNotEmpty()) {
            "从历史经验中学到的相关模式:\n${agentContext.learnedPatterns.entries.joinToString("\n") { "- ${it.key}: ${it.value}" }}"
        } else ""
        
        return listOf(basePrompt, planContext, learnedContext).filter { it.isNotEmpty() }.joinToString("\n\n")
    }

    private fun buildOptimizationContext(
        agentContext: AgentContext,
        lastScript: String,
        lastResult: ExecutionResult?
    ): String {
        val feedback = lastResult?.let { result ->
            """
            执行结果: ${if (result.success) "成功" else "失败"}
            消息: ${result.message}
            执行时间: ${result.executionTime}ms
            内存使用: ${result.memoryUsage} bytes
            ${result.errorType?.let { "错误类型: $it" } ?: ""}
            """.trimIndent()
        } ?: "无执行结果"
        
        val historyContext = if (agentContext.iterationHistory.isNotEmpty()) {
            "历史迭代问题:\n${agentContext.iterationHistory.joinToString("\n") { 
                "- 第${it.iteration}轮: ${it.result.message}"
            }}"
        } else ""
        
        return listOf(feedback, historyContext).filter { it.isNotEmpty() }.joinToString("\n\n")
    }

    private fun generateCacheKey(userRequest: String, planSteps: List<String>?): String {
        val stepsHash = planSteps?.joinToString("|")?.hashCode() ?: 0
        return "${userRequest.hashCode()}_$stepsHash"
    }

    private fun generateFallbackScript(agentContext: AgentContext): String {
        return """
            // 生成的备用脚本
            function main(params) {
                return {
                    success: false,
                    message: "所有自动生成的脚本都失败了，请检查用户需求: ${agentContext.userRequest}"
                };
            }
        """.trimIndent()
    }

    private fun findSimilarRequests(userRequest: String): Map<String, List<AgentIteration>> {
        // 简单的相似度匹配，实际可以使用更复杂的NLP算法
        return learningDatabase.filter { (request, _) ->
            val similarity = calculateSimilarity(userRequest, request)
            similarity > 0.7
        }
    }

    private fun calculateSimilarity(text1: String, text2: String): Double {
        // 简单的Jaccard相似度计算
        val words1 = text1.lowercase().split("\\s+".toRegex()).toSet()
        val words2 = text2.lowercase().split("\\s+".toRegex()).toSet()
        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size
        return if (union == 0) 0.0 else intersection.toDouble() / union
    }

    private fun extractScriptPattern(script: String): String {
        // 提取脚本的关键模式，如函数名、主要逻辑结构等
        val lines = script.lines()
        return lines.filter { line ->
            line.trim().startsWith("function") || 
            line.contains("return") ||
            line.contains("try") ||
            line.contains("catch")
        }.joinToString("\n")
    }

    private fun adjustScoreBasedOnHistory(
        agentContext: AgentContext,
        reliability: Float,
        performance: Float
    ) {
        // 根据历史数据调整评分，这里可以实现更复杂的机器学习算法
    }

    private fun analyzePerformance(agentContext: AgentContext, iteration: AgentIteration) {
        // 性能分析逻辑
        agentContext.performanceMetrics["avg_execution_time"] = 
            agentContext.iterationHistory.map { it.result.executionTime }.average()
            
        agentContext.performanceMetrics["success_rate"] = 
            agentContext.iterationHistory.count { it.result.success }.toDouble() / agentContext.iterationHistory.size
    }

    private fun updateLearningDatabase(agentContext: AgentContext, iteration: AgentIteration) {
        val key = agentContext.userRequest
        learningDatabase.computeIfAbsent(key) { mutableListOf() }.add(iteration)
        
        // 限制数据库大小，只保留最好的结果
        learningDatabase[key]?.let { iterations ->
            if (iterations.size > 10) {
                iterations.sortByDescending { it.quality.score }
                learningDatabase[key] = iterations.take(5).toMutableList()
            }
        }
    }

    private fun handleExecutionError(agentContext: AgentContext, error: Exception, iteration: Int) {
        Log.w(TAG, "处理执行错误: ${error.message}")
        
        // 错误恢复策略
        when (error) {
            is kotlinx.coroutines.TimeoutCancellationException -> {
                // 超时错误：减少脚本复杂度
                agentContext.learnedPatterns["timeout_recovery"] = "simplify_script"
            }
            is OutOfMemoryError -> {
                // 内存不足：优化内存使用
                agentContext.learnedPatterns["memory_recovery"] = "optimize_memory"
            }
            else -> {
                // 其他错误：通用错误处理
                agentContext.learnedPatterns["general_recovery"] = "add_error_handling"
            }
        }
        
        agentContext.config.errorHook?.invoke(error, "iteration_$iteration")
    }

    private fun saveToCache(agentContext: AgentContext, script: String, result: ExecutionResult) {
        val cacheKey = generateCacheKey(agentContext.userRequest, agentContext.planSteps)
        scriptCache[cacheKey] = script
        resultCache[cacheKey] = result
    }

    /**
     * 清理缓存和学习数据
     */
    fun clearCache() {
        scriptCache.clear()
        resultCache.clear()
    }

    fun clearLearningData() {
        learningDatabase.clear()
        performanceStats.clear()
    }
}

/**
 * Agent执行结果
 */
data class AgentExecutionResult(
    val success: Boolean,
    val script: String,
    val result: ExecutionResult,
    val iterations: Int,
    val qualityScore: Float,
    val sessionId: String,
    val message: String
) {
    companion object {
        fun success(
            script: String,
            result: ExecutionResult,
            iterations: Int,
            qualityScore: Float,
            sessionId: String
        ) = AgentExecutionResult(
            success = true,
            script = script,
            result = result,
            iterations = iterations,
            qualityScore = qualityScore,
            sessionId = sessionId,
            message = "执行成功"
        )
        
        fun failure(message: String) = AgentExecutionResult(
            success = false,
            script = "",
            result = ExecutionResult(false, message),
            iterations = 0,
            qualityScore = 0f,
            sessionId = "",
            message = message
        )
    }
}