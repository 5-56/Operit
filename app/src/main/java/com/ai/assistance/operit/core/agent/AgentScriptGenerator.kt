package com.ai.assistance.operit.core.agent

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.ai.assistance.operit.core.tools.javascript.JsEngine
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * 增强的Agent脚本生成器
 * 支持智能提示词、记忆功能、错误处理和多种优化策略
 */
object AgentScriptGenerator {
    
    private const val TAG = "AgentScriptGenerator"
    
    // 记忆系统
    private val memoryStore = ConcurrentHashMap<String, MutableList<MemoryItem>>()
    
    // 执行历史
    private val executionHistory = mutableListOf<ExecutionRecord>()
    
    // 错误模式匹配
    private val errorPatterns = mapOf(
        "ReferenceError" to "变量或函数未定义，请检查变量名称和作用域",
        "TypeError" to "类型错误，请检查变量类型和方法调用",
        "SyntaxError" to "语法错误，请检查代码语法",
        "TimeoutError" to "执行超时，请优化代码性能或减少复杂度",
        "NetworkError" to "网络错误，请检查网络连接和API端点",
        "PermissionError" to "权限错误，请检查应用权限设置"
    )
    
    /**
     * 智能生成脚本
     */
    suspend fun generateScript(
        userRequest: String, 
        planSteps: List<String>? = null,
        config: AgentConfig = AgentConfig()
    ): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始生成脚本，用户需求: $userRequest")
            
            val llmService = getLLMService(config)
            val enhancedPrompt = buildEnhancedPrompt(userRequest, planSteps, config)
            
            Log.d(TAG, "使用增强提示词生成脚本")
            val script = llmService.generateScript(enhancedPrompt)
            
            // 验证生成的脚本
            val validatedScript = validateAndEnhanceScript(script, config)
            
            // 保存到记忆
            if (config.enableMemory) {
                saveToMemory(userRequest, validatedScript, "generate")
            }
            
            Log.d(TAG, "脚本生成完成")
            return@withContext validatedScript
            
        } catch (e: Exception) {
            Log.e(TAG, "脚本生成失败", e)
            return@withContext generateFallbackScript(userRequest, config)
        }
    }
    
    /**
     * 智能优化脚本
     */
    suspend fun optimizeScript(
        lastScript: String, 
        feedback: String,
        config: AgentConfig = AgentConfig()
    ): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始优化脚本")
            
            val llmService = getLLMService(config)
            val analysisResult = analyzeError(feedback)
            val optimizationPrompt = buildOptimizationPrompt(lastScript, feedback, analysisResult, config)
            
            val optimizedScript = llmService.optimizeScript(lastScript, optimizationPrompt)
            val validatedScript = validateAndEnhanceScript(optimizedScript, config)
            
            // 保存到记忆
            if (config.enableMemory) {
                saveToMemory(feedback, validatedScript, "optimize")
            }
            
            Log.d(TAG, "脚本优化完成")
            return@withContext validatedScript
            
        } catch (e: Exception) {
            Log.e(TAG, "脚本优化失败", e)
            return@withContext enhanceScriptWithFallback(lastScript, feedback, config)
        }
    }
    
    /**
     * Agent主流程 - 增强版
     */
    suspend fun agentMain(
        userRequest: String,
        planSteps: List<String>? = null,
        config: AgentConfig = AgentConfig(),
        context: Context? = null
    ): AgentExecutionResult = withContext(Dispatchers.IO) {
        
        // 验证配置
        config.validate().getOrElse { 
            return@withContext AgentExecutionResult.failure("配置验证失败: ${it.message}")
        }
        
        val executionState = AgentExecutionState(
            totalIterations = config.maxIterations,
            startTime = System.currentTimeMillis(),
            isRunning = true
        )
        
        var currentScript = ""
        var lastResult = ""
        var currentIteration = 0
        val errors = mutableListOf<String>()
        var successRate = 0.0f
        
        try {
            Log.d(TAG, "Agent主流程开始，用户需求: $userRequest")
            
            config.preProcessHook?.invoke(userRequest)
            
            // 生成初始脚本
            currentScript = generateScript(userRequest, planSteps, config)
            
            repeat(config.maxIterations) { iteration ->
                currentIteration = iteration + 1
                
                Log.d(TAG, "第${currentIteration}轮开始")
                
                // 保存脚本
                if (config.enableAutoSave) {
                    try {
                        AgentScriptSaver.saveScript(currentScript, userRequest)
                    } catch (e: Exception) {
                        Log.w(TAG, "保存脚本失败", e)
                    }
                }
                
                // 执行脚本
                val executionResult = executeScriptSafely(currentScript, context, config)
                lastResult = executionResult.result
                
                if (config.showEachStep || config.enableDebugMode) {
                    Log.d(TAG, "第${currentIteration}轮脚本:\n$currentScript")
                    Log.d(TAG, "第${currentIteration}轮结果: $lastResult")
                }
                
                // 进度回调
                config.progressCallback?.invoke(currentIteration, currentScript, lastResult)
                config.postProcessHook?.invoke(currentScript, lastResult)
                
                // 记录执行历史
                recordExecution(userRequest, currentScript, lastResult, currentIteration, executionResult.success)
                
                // 评估成功率
                successRate = evaluateSuccess(lastResult, config)
                
                if (executionResult.success && successRate >= config.successThreshold) {
                    if (config.autoTerminateOnSuccess) {
                        Log.d(TAG, "脚本执行成功，提前结束")
                        break
                    }
                }
                
                // 如果不是最后一轮，进行优化
                if (currentIteration < config.maxIterations) {
                    val feedback = buildFeedback(lastResult, executionResult, config)
                    currentScript = optimizeScript(currentScript, feedback, config)
                }
            }
            
            // 自动上传到Git
            if (config.enableAutoUpload) {
                try {
                    AgentScriptSaver.autoGitUpload(
                        AgentScriptSaver.saveScript(currentScript, userRequest), 
                        "auto: agent脚本优化完成"
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "自动上传失败", e)
                }
            }
            
            return@withContext AgentExecutionResult.success(
                finalScript = currentScript,
                lastResult = lastResult,
                iterations = currentIteration,
                successRate = successRate,
                executionTime = System.currentTimeMillis() - executionState.startTime
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Agent执行异常", e)
            errors.add(e.message ?: "未知错误")
            
            config.errorHandler?.invoke(e)
            
            return@withContext AgentExecutionResult.failure(
                error = e.message ?: "Agent执行失败",
                finalScript = currentScript,
                lastResult = lastResult,
                iterations = currentIteration,
                errors = errors
            )
        }
    }
    
    /**
     * 构建增强的提示词
     */
    private fun buildEnhancedPrompt(
        userRequest: String, 
        planSteps: List<String>?, 
        config: AgentConfig
    ): String {
        val promptBuilder = StringBuilder()
        
        // 使用自定义模板或默认模板
        if (config.customPromptTemplate.isNotBlank()) {
            promptBuilder.append(config.customPromptTemplate.replace("{user_request}", userRequest))
        } else {
            promptBuilder.append(getDefaultPromptTemplate(config.scriptLanguage))
        }
        
        // 添加用户需求
        promptBuilder.append("\n\n用户需求：$userRequest")
        
        // 添加计划步骤
        if (!planSteps.isNullOrEmpty()) {
            promptBuilder.append("\n\n执行计划：")
            planSteps.forEachIndexed { index, step ->
                promptBuilder.append("\n${index + 1}. $step")
            }
        }
        
        // 添加记忆信息
        if (config.enableMemory) {
            val relatedMemories = getRelatedMemories(userRequest, config.memorySize / 2)
            if (relatedMemories.isNotEmpty()) {
                promptBuilder.append("\n\n相关历史经验：")
                relatedMemories.forEach { memory ->
                    promptBuilder.append("\n- ${memory.context}: ${memory.solution}")
                }
            }
        }
        
        // 添加安全约束
        if (config.enableSafetyChecks) {
            promptBuilder.append("\n\n安全约束：")
            if (!config.allowSystemCommands) {
                promptBuilder.append("\n- 不允许执行系统命令")
            }
            if (!config.allowNetworkAccess) {
                promptBuilder.append("\n- 不允许网络访问")
            }
            if (!config.allowFileOperations) {
                promptBuilder.append("\n- 不允许文件操作")
            }
        }
        
        // 添加优化策略指导
        promptBuilder.append("\n\n优化策略：${getStrategyGuidance(config.optimizationStrategy)}")
        
        return promptBuilder.toString()
    }
    
    /**
     * 构建优化提示词
     */
    private fun buildOptimizationPrompt(
        lastScript: String,
        feedback: String,
        analysisResult: ErrorAnalysis,
        config: AgentConfig
    ): String {
        val promptBuilder = StringBuilder()
        
        promptBuilder.append("请根据以下信息优化JavaScript脚本：")
        promptBuilder.append("\n\n原始脚本：")
        promptBuilder.append("\n```${config.scriptLanguage}")
        promptBuilder.append("\n$lastScript")
        promptBuilder.append("\n```")
        
        promptBuilder.append("\n\n执行反馈：$feedback")
        
        if (analysisResult.errorType.isNotBlank()) {
            promptBuilder.append("\n\n错误分析：")
            promptBuilder.append("\n- 错误类型：${analysisResult.errorType}")
            promptBuilder.append("\n- 建议方案：${analysisResult.suggestion}")
            promptBuilder.append("\n- 风险等级：${analysisResult.riskLevel}")
        }
        
        promptBuilder.append("\n\n优化要求：")
        promptBuilder.append("\n1. 修复已识别的错误")
        promptBuilder.append("\n2. 提高代码健壮性")
        promptBuilder.append("\n3. 优化性能和效率")
        promptBuilder.append("\n4. 保持代码可读性")
        promptBuilder.append("\n5. 确保输出格式正确")
        
        promptBuilder.append("\n\n优化策略：${getStrategyGuidance(config.optimizationStrategy)}")
        
        return promptBuilder.toString()
    }
    
    /**
     * 验证和增强脚本
     */
    private fun validateAndEnhanceScript(script: String, config: AgentConfig): String {
        var enhancedScript = script
        
        // 确保包含main函数
        if (!enhancedScript.contains("function main")) {
            enhancedScript = addMainFunctionWrapper(enhancedScript)
        }
        
        // 添加错误处理
        if (!enhancedScript.contains("try") && !enhancedScript.contains("catch")) {
            enhancedScript = addErrorHandling(enhancedScript)
        }
        
        // 添加超时处理
        if (config.executionTimeout > 0) {
            enhancedScript = addTimeoutHandling(enhancedScript, config.executionTimeout)
        }
        
        // 添加安全检查
        if (config.enableSafetyChecks) {
            enhancedScript = addSafetyChecks(enhancedScript, config)
        }
        
        return enhancedScript
    }
    
    /**
     * 安全执行脚本
     */
    private suspend fun executeScriptSafely(
        script: String, 
        context: Context?, 
        config: AgentConfig
    ): ScriptExecutionResult = withContext(Dispatchers.IO) {
        
        if (context == null) {
            return@withContext ScriptExecutionResult(
                success = false,
                result = "未提供Context，无法执行脚本",
                executionTime = 0
            )
        }
        
        val startTime = System.currentTimeMillis()
        
        try {
            val result = withTimeoutOrNull(config.executionTimeout) {
                val jsEngine = JsEngine(context)
                jsEngine.executeScriptFunction(script, "main", mapOf())
            }
            
            val executionTime = System.currentTimeMillis() - startTime
            
            if (result == null) {
                return@withContext ScriptExecutionResult(
                    success = false,
                    result = "脚本执行超时(${config.executionTimeout}ms)",
                    executionTime = executionTime
                )
            }
            
            return@withContext ScriptExecutionResult(
                success = true,
                result = result.toString(),
                executionTime = executionTime
            )
            
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            Log.e(TAG, "脚本执行异常", e)
            
            return@withContext ScriptExecutionResult(
                success = false,
                result = "脚本执行异常: ${e.message}",
                executionTime = executionTime,
                exception = e
            )
        }
    }
    
    /**
     * 获取LLM服务
     */
    fun getLLMService(config: AgentConfig): LLMService {
        return LLMServiceFactory.createLLMService(config)
    }
    
    // === 辅助方法 ===
    
    private fun getDefaultPromptTemplate(language: String): String {
        return when (language.lowercase()) {
            "javascript" -> """
                你是一个专业的JavaScript自动化脚本生成助手。请根据用户需求生成高质量的脚本。
                
                要求：
                1. 脚本必须包含main函数作为入口点
                2. 返回值必须是JSON格式，包含success字段
                3. 代码要简洁、可读性强、性能优秀
                4. 添加必要的错误处理和输入验证
                5. 包含详细的注释说明
                6. 遵循JavaScript最佳实践
                
                输出格式：
                ```javascript
                function main(params) {
                    try {
                        // 实现逻辑
                        return { success: true, message: "执行成功", data: result };
                    } catch (error) {
                        return { success: false, message: error.message, error: error.toString() };
                    }
                }
                ```
            """.trimIndent()
            
            "python" -> """
                你是一个专业的Python自动化脚本生成助手。请根据用户需求生成高质量的脚本。
                
                要求：
                1. 脚本必须包含main函数作为入口点
                2. 返回值必须是字典格式，包含success字段
                3. 代码要符合PEP8规范
                4. 添加必要的错误处理和类型检查
                5. 包含详细的文档字符串
            """.trimIndent()
            
            else -> """
                你是一个专业的自动化脚本生成助手。请根据用户需求生成高质量的脚本。
                请确保代码结构清晰，包含适当的错误处理，并添加必要的注释。
            """.trimIndent()
        }
    }
    
    private fun getStrategyGuidance(strategy: OptimizationStrategy): String {
        return when (strategy) {
            OptimizationStrategy.FAST -> "优先生成简单快速的解决方案，减少复杂逻辑"
            OptimizationStrategy.BALANCED -> "平衡代码质量和执行效率"
            OptimizationStrategy.QUALITY -> "优先代码质量，确保最佳实践和可维护性"
            OptimizationStrategy.PERFORMANCE -> "优先执行性能，使用高效算法和数据结构"
            OptimizationStrategy.SAFE -> "优先安全性，添加全面的输入验证和错误处理"
        }
    }
    
    // === 记忆系统 ===
    
    private fun saveToMemory(context: String, solution: String, type: String) {
        val key = extractKeywords(context)
        val memory = MemoryItem(
            context = context,
            solution = solution,
            type = type,
            timestamp = System.currentTimeMillis(),
            useCount = 0
        )
        
        memoryStore.getOrPut(key) { mutableListOf() }.add(memory)
        
        // 限制记忆条目数量
        memoryStore.forEach { (_, memories) ->
            if (memories.size > 50) {
                memories.sortBy { it.timestamp }
                memories.removeAt(0)
            }
        }
    }
    
    private fun getRelatedMemories(context: String, limit: Int): List<MemoryItem> {
        val keywords = extractKeywords(context)
        val relatedMemories = mutableListOf<MemoryItem>()
        
        memoryStore[keywords]?.let { memories ->
            relatedMemories.addAll(memories.sortedByDescending { it.useCount + it.timestamp })
        }
        
        return relatedMemories.take(limit)
    }
    
    private fun extractKeywords(text: String): String {
        // 简单的关键词提取，实际可以使用更复杂的NLP算法
        return text.lowercase()
            .replace(Regex("[^a-z\\u4e00-\\u9fff\\s]"), "")
            .split("\\s+".toRegex())
            .filter { it.length > 2 }
            .take(3)
            .joinToString("_")
    }
    
    // === 错误分析 ===
    
    private fun analyzeError(feedback: String): ErrorAnalysis {
        for ((pattern, suggestion) in errorPatterns) {
            if (feedback.contains(pattern, ignoreCase = true)) {
                return ErrorAnalysis(
                    errorType = pattern,
                    suggestion = suggestion,
                    riskLevel = "中等"
                )
            }
        }
        
        return ErrorAnalysis(
            errorType = "未知错误",
            suggestion = "建议检查代码逻辑和输入参数",
            riskLevel = "低"
        )
    }
    
    // === 数据类 ===
    
    data class MemoryItem(
        val context: String,
        val solution: String,
        val type: String,
        val timestamp: Long,
        var useCount: Int
    )
    
    data class ErrorAnalysis(
        val errorType: String,
        val suggestion: String,
        val riskLevel: String
    )
    
    data class ScriptExecutionResult(
        val success: Boolean,
        val result: String,
        val executionTime: Long,
        val exception: Exception? = null
    )
    
    data class ExecutionRecord(
        val userRequest: String,
        val script: String,
        val result: String,
        val iteration: Int,
        val success: Boolean,
        val timestamp: Long
    )
    
    data class AgentExecutionResult(
        val success: Boolean,
        val finalScript: String,
        val lastResult: String,
        val iterations: Int,
        val successRate: Float = 0.0f,
        val executionTime: Long = 0,
        val errors: List<String> = emptyList(),
        val error: String? = null
    ) {
        companion object {
            fun success(
                finalScript: String,
                lastResult: String,
                iterations: Int,
                successRate: Float = 1.0f,
                executionTime: Long = 0
            ) = AgentExecutionResult(
                success = true,
                finalScript = finalScript,
                lastResult = lastResult,
                iterations = iterations,
                successRate = successRate,
                executionTime = executionTime
            )
            
            fun failure(
                error: String,
                finalScript: String = "",
                lastResult: String = "",
                iterations: Int = 0,
                errors: List<String> = emptyList()
            ) = AgentExecutionResult(
                success = false,
                finalScript = finalScript,
                lastResult = lastResult,
                iterations = iterations,
                errors = errors,
                error = error
            )
        }
    }
    
    // === 待实现的辅助方法 ===
    
    private fun generateFallbackScript(userRequest: String, config: AgentConfig): String {
        return """
            // 自动生成的回退脚本（需求：$userRequest）
            function main(params) {
                try {
                    console.log("开始执行任务：$userRequest");
                    
                    // TODO: 根据需求实现具体逻辑
                    // 这是一个回退脚本，请手动完善实现
                    
                    return { 
                        success: true, 
                        message: "脚本生成成功，但需要手动完善",
                        data: { request: "$userRequest" }
                    };
                } catch (error) {
                    return { 
                        success: false, 
                        message: "脚本执行失败: " + error.message,
                        error: error.toString() 
                    };
                }
            }
        """.trimIndent()
    }
    
    private fun enhanceScriptWithFallback(lastScript: String, feedback: String, config: AgentConfig): String {
        return """$lastScript
        
        // 优化失败，添加基本错误处理增强
        // 反馈信息: $feedback
        // 建议: 请检查上述错误信息并手动调整代码
        """.trimIndent()
    }
    
    private fun addMainFunctionWrapper(script: String): String {
        return """
            function main(params) {
                try {
                    $script
                    return { success: true, message: "执行完成" };
                } catch (error) {
                    return { success: false, message: error.message };
                }
            }
        """.trimIndent()
    }
    
    private fun addErrorHandling(script: String): String {
        // 基本的错误处理包装
        return script.replace(
            "function main(params) {",
            """function main(params) {
                try {"""
        ).replace(
            Regex("return\\s+\\{.*?\\};?\\s*}\\s*$"),
            """    return result;
                } catch (error) {
                    console.error("脚本执行异常:", error);
                    return { success: false, message: error.message, error: error.toString() };
                }
            }"""
        )
    }
    
    private fun addTimeoutHandling(script: String, timeout: Long): String {
        return """
            // 添加超时处理
            const startTime = Date.now();
            const TIMEOUT = $timeout;
            
            function checkTimeout() {
                if (Date.now() - startTime > TIMEOUT) {
                    throw new Error("脚本执行超时");
                }
            }
            
            $script
        """.trimIndent()
    }
    
    private fun addSafetyChecks(script: String, config: AgentConfig): String {
        var safeScript = script
        
        if (!config.allowSystemCommands) {
            safeScript = "// 系统命令已被禁用\n$safeScript"
        }
        
        if (!config.allowNetworkAccess) {
            safeScript = "// 网络访问已被禁用\n$safeScript"
        }
        
        if (!config.allowFileOperations) {
            safeScript = "// 文件操作已被禁用\n$safeScript"
        }
        
        return safeScript
    }
    
    private fun buildFeedback(result: String, executionResult: ScriptExecutionResult, config: AgentConfig): String {
        val feedback = StringBuilder()
        feedback.append("执行结果: $result")
        feedback.append("\n执行状态: ${if (executionResult.success) "成功" else "失败"}")
        feedback.append("\n执行时间: ${executionResult.executionTime}ms")
        
        if (!executionResult.success && executionResult.exception != null) {
            feedback.append("\n异常信息: ${executionResult.exception.message}")
        }
        
        return feedback.toString()
    }
    
    private fun evaluateSuccess(result: String, config: AgentConfig): Float {
        // 简单的成功率评估
        val successIndicators = listOf("success", "成功", "完成", "ok", "true")
        val failureIndicators = listOf("error", "错误", "失败", "exception", "false")
        
        val successMatches = successIndicators.count { result.contains(it, ignoreCase = true) }
        val failureMatches = failureIndicators.count { result.contains(it, ignoreCase = true) }
        
        return when {
            successMatches > failureMatches -> 0.8f + (successMatches * 0.1f)
            failureMatches > successMatches -> 0.2f - (failureMatches * 0.1f)
            else -> 0.5f
        }.coerceIn(0.0f, 1.0f)
    }
    
    private fun recordExecution(
        userRequest: String,
        script: String,
        result: String,
        iteration: Int,
        success: Boolean
    ) {
        val record = ExecutionRecord(
            userRequest = userRequest,
            script = script,
            result = result,
            iteration = iteration,
            success = success,
            timestamp = System.currentTimeMillis()
        )
        
        executionHistory.add(record)
        
        // 限制历史记录数量
        if (executionHistory.size > 1000) {
            executionHistory.removeAt(0)
        }
    }
}