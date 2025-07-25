package com.ai.assistance.operit.core.agent

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.javascript.JsEngine
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.preferences.ApiPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.util.UUID

/**
 * Agent执行计划的单个步骤
 */
@Serializable
data class AgentStep(
    val id: String = UUID.randomUUID().toString(),
    val type: AgentStepType,
    val description: String,
    var script: String? = null,
    val expectedOutput: String? = null,
    val dependencies: List<String> = emptyList(),
    var status: AgentStepStatus = AgentStepStatus.PENDING
)

@Serializable
enum class AgentStepType {
    ANALYSIS,      // 分析需求
    PLANNING,      // 制定计划
    SCRIPT_GEN,    // 生成脚本
    EXECUTION,     // 执行脚本
    VALIDATION,    // 验证结果
    OPTIMIZATION   // 优化改进
}

@Serializable
enum class AgentStepStatus {
    PENDING,       // 等待执行
    RUNNING,       // 正在执行
    COMPLETED,     // 已完成
    FAILED,        // 执行失败
    SKIPPED        // 已跳过
}

/**
 * Agent执行计划
 */
@Serializable
data class AgentPlan(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val steps: MutableList<AgentStep> = mutableListOf(),
    val context: MutableMap<String, String> = mutableMapOf(),
    val status: AgentPlanStatus = AgentPlanStatus.CREATED
)

@Serializable
enum class AgentPlanStatus {
    CREATED,       // 已创建
    EXECUTING,     // 执行中
    COMPLETED,     // 已完成
    FAILED,        // 执行失败
    PAUSED         // 已暂停
}

/**
 * Agent执行结果
 */
data class AgentResult(
    val success: Boolean,
    val message: String,
    val data: Any? = null,
    val plan: AgentPlan? = null,
    val currentStep: AgentStep? = null
)

/**
 * Agent核心类 - 实现智能agent功能
 * 
 * 主要功能：
 * 1. 理解用户需求并生成执行计划
 * 2. 自动编写和执行脚本代码
 * 3. 根据执行结果评估和优化
 * 4. 提供完整的agent工作流
 */
class AgentCore(private val context: Context) {
    
    companion object {
        private const val TAG = "AgentCore"
        
        @Volatile
        private var INSTANCE: AgentCore? = null
        
        fun getInstance(context: Context): AgentCore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AgentCore(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val toolHandler = AIToolHandler.getInstance(context)
    private val jsEngine = JsEngine(context)
    private val aiService = EnhancedAIService.getInstance(context)
    private val apiPreferences = ApiPreferences(context)
    
    // 当前执行的计划
    private var currentPlan: AgentPlan? = null
    
    // 执行历史
    private val executionHistory = mutableListOf<AgentPlan>()
    
    /**
     * 处理用户需求，生成并执行agent计划
     */
    suspend fun processUserRequest(userRequest: String): Flow<AgentResult> = flow {
        try {
            Log.d(TAG, "开始处理用户需求: $userRequest")
            
            // 1. 分析用户需求
            emit(AgentResult(true, "正在分析用户需求...", null))
            val analysisResult = analyzeUserRequest(userRequest)
            
            // 2. 生成执行计划
            emit(AgentResult(true, "正在生成执行计划...", null))
            val plan = generateExecutionPlan(userRequest, analysisResult)
            currentPlan = plan
            
            emit(AgentResult(true, "计划生成完成", null, plan))
            
            // 3. 执行计划
            plan.status = AgentPlanStatus.EXECUTING
            emit(AgentResult(true, "开始执行计划...", null, plan))
            
            for (step in plan.steps) {
                val stepResult = executeStep(step, plan)
                emit(stepResult)
                
                if (!stepResult.success) {
                    // 尝试优化和重试
                    val optimizedResult = optimizeAndRetry(step, plan, stepResult.message)
                    emit(optimizedResult)
                    
                    if (!optimizedResult.success) {
                        plan.status = AgentPlanStatus.FAILED
                        emit(AgentResult(false, "计划执行失败", null, plan, step))
                        return@flow
                    }
                }
            }
            
            // 4. 完成执行
            plan.status = AgentPlanStatus.COMPLETED
            executionHistory.add(plan)
            emit(AgentResult(true, "计划执行完成！", null, plan))
            
        } catch (e: Exception) {
            Log.e(TAG, "处理用户需求时出错", e)
            emit(AgentResult(false, "处理请求时出错: ${e.message}"))
        }
    }
    
    /**
     * 分析用户需求
     */
    private suspend fun analyzeUserRequest(userRequest: String): String {
        val systemPrompt = """
        你是一个智能助手的需求分析模块。请分析用户的需求，并提供以下信息：

        1. 需求类型（如：文件操作、数据处理、自动化任务、信息查询等）
        2. 关键要素（涉及的文件、数据、系统等）
        3. 预期结果
        4. 可能的实现方式
        5. 潜在的挑战和注意事项

        请以JSON格式返回分析结果：
        {
            "type": "需求类型",
            "elements": ["关键要素1", "关键要素2"],
            "expected_result": "预期结果描述",
            "approaches": ["实现方式1", "实现方式2"],
            "challenges": ["挑战1", "挑战2"]
        }
        """.trimIndent()
        
        val messages = listOf(
            Pair("system", systemPrompt),
            Pair("user", userRequest)
        )
        
        val modelParameters = apiPreferences.getAllModelParameters()
        val responseBuilder = StringBuilder()
        
        aiService.sendMessage("", messages, modelParameters).collect { content ->
            responseBuilder.append(content)
        }
        
        return responseBuilder.toString().trim()
    }
    
    /**
     * 生成执行计划
     */
    private suspend fun generateExecutionPlan(userRequest: String, analysisResult: String): AgentPlan {
        val systemPrompt = """
        你是一个智能助手的计划生成模块。基于用户需求和分析结果，生成详细的执行计划。

        可用的工具和能力：
        1. JavaScript脚本执行（可以调用所有系统工具）
        2. 文件系统操作（读写、搜索、压缩等）
        3. HTTP网络请求
        4. UI自动化操作
        5. 系统管理功能
        6. 媒体处理工具

        请生成一个分步骤的执行计划，每个步骤包含：
        - 步骤描述
        - 步骤类型（analysis/planning/script_gen/execution/validation/optimization）
        - 具体的实现脚本（如果需要）
        - 预期输出

        以JSON格式返回：
        {
            "title": "计划标题",
            "description": "计划描述",
            "steps": [
                {
                    "type": "步骤类型",
                    "description": "步骤描述",
                    "script": "JavaScript代码（如果需要）",
                    "expectedOutput": "预期输出描述"
                }
            ]
        }
        """.trimIndent()
        
        val messages = listOf(
            Pair("system", systemPrompt),
            Pair("user", "用户需求: $userRequest\n\n分析结果: $analysisResult")
        )
        
        val modelParameters = apiPreferences.getAllModelParameters()
        val responseBuilder = StringBuilder()
        
        aiService.sendMessage("", messages, modelParameters).collect { content ->
            responseBuilder.append(content)
        }
        
        val response = responseBuilder.toString().trim()
        
        // 解析响应生成计划
        return try {
            val jsonResponse = JSONObject(response)
            val plan = AgentPlan(
                title = jsonResponse.optString("title", "Agent执行计划"),
                description = jsonResponse.optString("description", "自动生成的执行计划")
            )
            
            val stepsArray = jsonResponse.optJSONArray("steps")
            if (stepsArray != null) {
                for (i in 0 until stepsArray.length()) {
                    val stepJson = stepsArray.getJSONObject(i)
                    val step = AgentStep(
                        type = AgentStepType.valueOf(stepJson.optString("type", "EXECUTION").uppercase()),
                        description = stepJson.optString("description", "执行步骤"),
                        script = stepJson.optString("script").takeIf { it.isNotEmpty() },
                        expectedOutput = stepJson.optString("expectedOutput").takeIf { it.isNotEmpty() }
                    )
                    plan.steps.add(step)
                }
            }
            
            plan
        } catch (e: Exception) {
            Log.e(TAG, "解析执行计划失败", e)
            // 创建默认计划
            AgentPlan(
                title = "默认执行计划",
                description = "由于计划解析失败，创建的默认计划",
                steps = mutableListOf(
                    AgentStep(
                        type = AgentStepType.ANALYSIS,
                        description = "分析用户需求: $userRequest"
                    )
                )
            )
        }
    }
    
    /**
     * 执行单个步骤
     */
    private suspend fun executeStep(step: AgentStep, plan: AgentPlan): AgentResult {
        try {
            Log.d(TAG, "执行步骤: ${step.description}")
            step.status = AgentStepStatus.RUNNING
            
            when (step.type) {
                AgentStepType.ANALYSIS -> {
                    // 分析步骤
                    step.status = AgentStepStatus.COMPLETED
                    return AgentResult(true, "分析完成: ${step.description}", null, plan, step)
                }
                
                AgentStepType.PLANNING -> {
                    // 计划步骤
                    step.status = AgentStepStatus.COMPLETED
                    return AgentResult(true, "计划完成: ${step.description}", null, plan, step)
                }
                
                AgentStepType.SCRIPT_GEN -> {
                    // 脚本生成步骤
                    if (step.script.isNullOrEmpty()) {
                        val generatedScript = generateScript(step.description, plan.context)
                        step.script = generatedScript
                    }
                    step.status = AgentStepStatus.COMPLETED
                    return AgentResult(true, "脚本生成完成", step.script, plan, step)
                }
                
                AgentStepType.EXECUTION -> {
                    // 执行步骤
                    if (!step.script.isNullOrEmpty()) {
                        val executionResult = executeScript(step.script!!, plan.context)
                        step.status = if (executionResult.success) AgentStepStatus.COMPLETED else AgentStepStatus.FAILED
                        return AgentResult(executionResult.success, executionResult.message, executionResult.data, plan, step)
                    } else {
                        step.status = AgentStepStatus.COMPLETED
                        return AgentResult(true, "执行完成: ${step.description}", null, plan, step)
                    }
                }
                
                AgentStepType.VALIDATION -> {
                    // 验证步骤
                    val validationResult = validateStepResult(step, plan)
                    step.status = if (validationResult) AgentStepStatus.COMPLETED else AgentStepStatus.FAILED
                    return AgentResult(validationResult, if (validationResult) "验证通过" else "验证失败", null, plan, step)
                }
                
                AgentStepType.OPTIMIZATION -> {
                    // 优化步骤
                    step.status = AgentStepStatus.COMPLETED
                    return AgentResult(true, "优化完成: ${step.description}", null, plan, step)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "执行步骤失败", e)
            step.status = AgentStepStatus.FAILED
            return AgentResult(false, "步骤执行失败: ${e.message}", null, plan, step)
        }
    }
    
    /**
     * 生成脚本代码
     */
    private suspend fun generateScript(description: String, context: Map<String, String>): String {
        val systemPrompt = """
        你是一个JavaScript脚本生成器。请根据任务描述生成可执行的JavaScript代码。

        可用的API：
        1. toolCall(toolName, params) - 调用系统工具
        2. Tools.Files.read(path) - 读取文件
        3. Tools.Files.write(path, content) - 写入文件
        4. Tools.Net.httpGet(url) - HTTP GET请求
        5. Tools.System.execute(command) - 执行系统命令
        6. complete(result) - 返回执行结果

        上下文信息：
        ${context.entries.joinToString("\n") { "${it.key}: ${it.value}" }}

        请生成纯JavaScript代码，不要包含任何解释文字。
        """.trimIndent()
        
        val messages = listOf(
            Pair("system", systemPrompt),
            Pair("user", "任务描述: $description")
        )
        
        val modelParameters = apiPreferences.getAllModelParameters()
        val responseBuilder = StringBuilder()
        
        aiService.sendMessage("", messages, modelParameters).collect { content ->
            responseBuilder.append(content)
        }
        
        return responseBuilder.toString().trim()
    }
    
    /**
     * 执行JavaScript脚本
     */
    private suspend fun executeScript(script: String, context: Map<String, String>): AgentResult {
        return try {
            Log.d(TAG, "执行脚本: ${script.take(100)}...")
            
            // 准备脚本参数
            val params = context.toMutableMap()
            
            // 执行脚本
            val result = jsEngine.executeScriptFunction(script, "main", params)
            
            AgentResult(true, "脚本执行成功", result)
        } catch (e: Exception) {
            Log.e(TAG, "脚本执行失败", e)
            AgentResult(false, "脚本执行失败: ${e.message}")
        }
    }
    
    /**
     * 验证步骤结果
     */
    private fun validateStepResult(step: AgentStep, plan: AgentPlan): Boolean {
        // 基本验证逻辑
        return step.status == AgentStepStatus.COMPLETED
    }
    
    /**
     * 优化和重试
     */
    private suspend fun optimizeAndRetry(step: AgentStep, plan: AgentPlan, errorMessage: String): AgentResult {
        try {
            Log.d(TAG, "尝试优化步骤: ${step.description}")
            
            // 如果是脚本执行失败，尝试生成优化的脚本
            if (step.type == AgentStepType.EXECUTION && !step.script.isNullOrEmpty()) {
                val optimizedScript = optimizeScript(step.script!!, errorMessage, plan.context)
                step.script = optimizedScript
                
                // 重新执行
                return executeScript(optimizedScript, plan.context)
            }
            
            return AgentResult(false, "无法优化此步骤")
        } catch (e: Exception) {
            Log.e(TAG, "优化步骤失败", e)
            return AgentResult(false, "优化失败: ${e.message}")
        }
    }
    
    /**
     * 优化脚本代码
     */
    private suspend fun optimizeScript(originalScript: String, errorMessage: String, context: Map<String, String>): String {
        val systemPrompt = """
        你是一个JavaScript代码优化器。请根据错误信息优化脚本代码。

        原始脚本：
        $originalScript

        错误信息：
        $errorMessage

        上下文信息：
        ${context.entries.joinToString("\n") { "${it.key}: ${it.value}" }}

        请生成优化后的JavaScript代码，修复错误并提高稳定性。
        只返回纯JavaScript代码，不要包含任何解释文字。
        """.trimIndent()
        
        val messages = listOf(
            Pair("system", systemPrompt),
            Pair("user", "请优化这个脚本")
        )
        
        val modelParameters = apiPreferences.getAllModelParameters()
        val responseBuilder = StringBuilder()
        
        aiService.sendMessage("", messages, modelParameters).collect { content ->
            responseBuilder.append(content)
        }
        
        return responseBuilder.toString().trim()
    }
    
    /**
     * 获取当前执行计划
     */
    fun getCurrentPlan(): AgentPlan? = currentPlan
    
    /**
     * 获取执行历史
     */
    fun getExecutionHistory(): List<AgentPlan> = executionHistory.toList()
    
    /**
     * 暂停当前计划
     */
    fun pauseCurrentPlan() {
        currentPlan?.status = AgentPlanStatus.PAUSED
    }
    
    /**
     * 恢复当前计划
     */
    fun resumeCurrentPlan() {
        currentPlan?.status = AgentPlanStatus.EXECUTING
    }
    
    /**
     * 取消当前计划
     */
    fun cancelCurrentPlan() {
        currentPlan?.status = AgentPlanStatus.FAILED
        currentPlan = null
    }
}