package com.xihe.assistant.core.agent

data class AgentConfig(
    val maxOptimizationRounds: Int = 3,
    val llmProvider: String = "openai",
    val apiKey: String = "",
    val model: String = "gpt-3.5-turbo",
    val temperature: Double = 0.7,
    val maxTokens: Int = 2000,
    val enableAutoOptimization: Boolean = true,
    val enableScriptSaving: Boolean = true
)

class AgentScriptSaver {
    fun saveScript(script: String, metadata: Map<String, String> = emptyMap()) {
        // 保存脚本逻辑
    }
    
    fun loadScript(scriptId: String): String? {
        // 加载脚本逻辑
        return null
    }
    
    fun listScripts(): List<ScriptInfo> {
        // 列出脚本逻辑
        return emptyList()
    }
}

data class ScriptInfo(
    val id: String,
    val name: String,
    val description: String,
    val createdAt: Long,
    val lastModified: Long
)