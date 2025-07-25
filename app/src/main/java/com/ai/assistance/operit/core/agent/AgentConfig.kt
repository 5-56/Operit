package com.ai.assistance.operit.core.agent

/**
 * AgentConfig 用于自定义 agent 自动化流程参数和 hook。
 */
data class AgentConfig(
    val maxIterations: Int = 3,
    val autoTerminateOnSuccess: Boolean = true,
    val showEachStep: Boolean = true,
    val preProcessHook: ((String) -> Unit)? = null,
    val postProcessHook: ((String, String) -> Unit)? = null,
    val llmProvider: String = "openai", // 可选: openai/qwen/claude/local
    val llmApiKey: String = "",
    val llmEndpoint: String = ""
)