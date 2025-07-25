package com.ai.assistance.operit.core.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ai.assistance.operit.core.agent.AgentConfig
import com.ai.assistance.operit.core.agent.LLMService
import com.ai.assistance.operit.core.agent.OpenAILLMService
import com.ai.assistance.operit.core.agent.AgentScriptSaver
import com.ai.assistance.operit.core.agent.QwenLLMService
import com.ai.assistance.operit.core.agent.ClaudeLLMService
import com.ai.assistance.operit.core.tools.javascript.JsEngine
import android.content.Context

/**
 * AgentScriptGenerator 负责根据用户需求和计划自动生成、优化 JavaScript 脚本。
 * 可集成 LLM（如 OpenAI、Qwen 等）进行脚本生成与优化。
 */
object AgentScriptGenerator {
    /**
     * 根据用户需求和计划生成初始脚本
     * @param userRequest 用户需求描述
     * @param planSteps 计划步骤（可选）
     * @return 生成的 JavaScript 脚本
     */
    suspend fun generateScript(userRequest: String, planSteps: List<String>? = null): String {
        // 这里可集成 LLM API，根据 userRequest 和 planSteps 生成 JS 脚本
        // 示例：直接拼接伪代码，实际可调用 LLM
        val planComment = planSteps?.joinToString("\n// ", prefix = "// ") ?: ""
        return """
            // 根据用户需求自动生成
            // 用户需求: $userRequest
            $planComment
            function main(params) {
                // TODO: 实现自动化逻辑
                return { success: true, message: '脚本执行成功' };
            }
        """.trimIndent()
    }

    /**
     * 根据执行结果和反馈优化脚本
     * @param lastScript 上一次的脚本内容
     * @param feedback 执行结果与模型评价
     * @return 优化后的 JavaScript 脚本
     */
    suspend fun optimizeScript(lastScript: String, feedback: String): String {
        // 这里可集成 LLM API，根据反馈优化脚本
        // 示例：简单拼接注释，实际可调用 LLM
        return """
            // 上次反馈: $feedback
            $lastScript
            // TODO: 根据反馈进一步优化脚本
        """.trimIndent()
    }

    /**
     * 根据 config 选择 LLMService
     */
    fun getLLMService(config: AgentConfig): LLMService = when (config.llmProvider.lowercase()) {
        "qwen" -> QwenLLMService(config.llmApiKey)
        "claude" -> ClaudeLLMService(config.llmApiKey)
        else -> OpenAILLMService(config.llmApiKey)
    }

    /**
     * agent 主流程，支持自定义 config、llm、自动保存/上传、真实脚本执行与反馈
     */
    suspend fun agentMain(
        userRequest: String,
        planSteps: List<String>? = null,
        config: AgentConfig = AgentConfig(),
        context: Context? = null
    ): String {
        val llmService = getLLMService(config)
        config.preProcessHook?.invoke(userRequest)
        var script = llmService.generateScript(userRequest + (planSteps?.joinToString("\n") ?: ""))
        var lastFeedback = ""
        var result: String = ""
        var scriptPath: String? = null
        repeat(config.maxIterations) { iteration ->
            // 每轮保存脚本
            scriptPath = AgentScriptSaver.saveScript(script, userRequest)
            // 实际执行脚本并获取 result
            result = if (context != null) {
                try {
                    val jsEngine = JsEngine(context)
                    val execResult = jsEngine.executeScriptFunction(script, "main", mapOf())
                    execResult?.toString() ?: ""
                } catch (e: Exception) {
                    "脚本执行异常: ${e.message}"
                }
            } else {
                "未提供 context，未执行脚本"
            }
            config.postProcessHook?.invoke(script, result)
            if (config.showEachStep) {
                println("[Agent] 第${iteration+1}轮脚本:\n$script\n结果:$result")
            }
            // 反馈给 LLM
            lastFeedback = "用户需求: $userRequest\n计划: $planSteps\n脚本: $script\n执行结果: $result"
            val needOptimize = !result.contains("success") && !result.contains("成功")
            if (!needOptimize && config.autoTerminateOnSuccess) return@repeat
            script = llmService.optimizeScript(script, lastFeedback)
        }
        // 最终保存并上传
        scriptPath?.let { AgentScriptSaver.autoGitUpload(it, "auto: agent 脚本更新") }
        return script
    }
}