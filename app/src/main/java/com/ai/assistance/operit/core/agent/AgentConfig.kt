package com.ai.assistance.operit.core.agent

import kotlinx.serialization.Serializable

/**
 * AgentConfig 用于自定义 agent 自动化流程参数和 hook。
 * 支持多种LLM提供商和高级配置选项。
 */
@Serializable
data class AgentConfig(
    // === 基础配置 ===
    val maxIterations: Int = 3,
    val autoTerminateOnSuccess: Boolean = true,
    val showEachStep: Boolean = true,
    val enableDebugMode: Boolean = false,
    
    // === LLM配置 ===
    val llmProvider: String = "openai", // openai/qwen/claude/gemini/deepseek/local
    val llmApiKey: String = "",
    val llmEndpoint: String = "",
    val llmModel: String = "", // 如果为空，将使用默认模型
    
    // === 脚本生成配置 ===
    val scriptLanguage: String = "javascript", // javascript/python/shell
    val maxTokens: Int = 4000,
    val temperature: Float = 0.7f,
    val enableThinking: Boolean = false, // 启用思考模式(适用于Qwen等模型)
    
    // === 执行配置 ===
    val executionTimeout: Long = 30000L, // 脚本执行超时时间(毫秒)
    val maxRetryCount: Int = 2, // 失败重试次数
    val enableAutoSave: Boolean = true, // 自动保存脚本
    val enableAutoUpload: Boolean = false, // 自动上传到Git
    
    // === 优化配置 ===
    val enableAutoOptimization: Boolean = true, // 启用自动优化
    val optimizationStrategy: OptimizationStrategy = OptimizationStrategy.BALANCED,
    val successThreshold: Float = 0.8f, // 成功阈值(0.0-1.0)
    
    // === 安全配置 ===
    val enableSafetyChecks: Boolean = true, // 启用安全检查
    val allowSystemCommands: Boolean = false, // 允许系统命令
    val allowNetworkAccess: Boolean = true, // 允许网络访问
    val allowFileOperations: Boolean = true, // 允许文件操作
    
    // === 高级配置 ===
    val customPromptTemplate: String = "", // 自定义提示词模板
    val contextWindowSize: Int = 8000, // 上下文窗口大小
    val enableMemory: Boolean = false, // 启用记忆功能
    val memorySize: Int = 100, // 记忆条目数量
    
    // === Hook配置 ===
    val enablePreProcessHook: Boolean = false,
    val enablePostProcessHook: Boolean = false,
    val enableProgressCallback: Boolean = false,
    
    // === 扩展配置 ===
    val customParameters: Map<String, String> = emptyMap(), // 自定义参数
    val pluginConfigs: Map<String, String> = emptyMap(), // 插件配置
) {
    // Hook函数(非序列化)
    @kotlinx.serialization.Transient
    val preProcessHook: ((String) -> Unit)? = null
    
    @kotlinx.serialization.Transient
    val postProcessHook: ((String, String) -> Unit)? = null
    
    @kotlinx.serialization.Transient
    val progressCallback: ((Int, String, String) -> Unit)? = null // (iteration, script, result)
    
    @kotlinx.serialization.Transient
    val errorHandler: ((Exception) -> String)? = null
    
    /**
     * 获取有效的LLM端点URL
     */
    fun getEffectiveLLMEndpoint(): String {
        if (llmEndpoint.isNotBlank()) return llmEndpoint
        
        return when (llmProvider.lowercase()) {
            "openai" -> "https://api.openai.com/v1/chat/completions"
            "qwen" -> "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"
            "claude" -> "https://api.anthropic.com/v1/messages"
            "gemini" -> "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent"
            "deepseek" -> "https://api.deepseek.com/v1/chat/completions"
            else -> llmEndpoint
        }
    }
    
    /**
     * 获取有效的LLM模型名称
     */
    fun getEffectiveLLMModel(): String {
        if (llmModel.isNotBlank()) return llmModel
        
        return when (llmProvider.lowercase()) {
            "openai" -> "gpt-4o-mini"
            "qwen" -> "qwen-turbo"
            "claude" -> "claude-3-haiku-20240307"
            "gemini" -> "gemini-pro"
            "deepseek" -> "deepseek-chat"
            else -> "gpt-4o-mini"
        }
    }
    
    /**
     * 验证配置有效性
     */
    fun validate(): Result<Unit> {
        return try {
            require(maxIterations > 0) { "最大迭代次数必须大于0" }
            require(maxTokens > 0) { "最大token数必须大于0" }
            require(temperature in 0.0f..2.0f) { "temperature必须在0.0-2.0之间" }
            require(executionTimeout > 0) { "执行超时时间必须大于0" }
            require(maxRetryCount >= 0) { "重试次数不能为负数" }
            require(successThreshold in 0.0f..1.0f) { "成功阈值必须在0.0-1.0之间" }
            require(contextWindowSize > 0) { "上下文窗口大小必须大于0" }
            require(memorySize >= 0) { "记忆大小不能为负数" }
            
            if (llmApiKey.isBlank() && llmProvider != "local") {
                return Result.failure(IllegalArgumentException("LLM API Key不能为空(除非使用本地模型)"))
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 创建配置副本并应用修改
     */
    fun copy(modifier: AgentConfig.() -> AgentConfig): AgentConfig {
        return this.modifier()
    }
    
    companion object {
        /**
         * 创建默认的快速配置
         */
        fun createQuickConfig(
            provider: String = "openai",
            apiKey: String = "",
            maxIterations: Int = 2
        ): AgentConfig {
            return AgentConfig(
                llmProvider = provider,
                llmApiKey = apiKey,
                maxIterations = maxIterations,
                temperature = 0.5f,
                enableAutoOptimization = true,
                enableSafetyChecks = true
            )
        }
        
        /**
         * 创建高性能配置
         */
        fun createPerformanceConfig(
            provider: String = "openai",
            apiKey: String = ""
        ): AgentConfig {
            return AgentConfig(
                llmProvider = provider,
                llmApiKey = apiKey,
                maxIterations = 5,
                temperature = 0.3f,
                maxTokens = 8000,
                optimizationStrategy = OptimizationStrategy.PERFORMANCE,
                enableAutoOptimization = true,
                enableMemory = true,
                memorySize = 200,
                contextWindowSize = 16000
            )
        }
        
        /**
         * 创建安全配置
         */
        fun createSecureConfig(
            provider: String = "openai",
            apiKey: String = ""
        ): AgentConfig {
            return AgentConfig(
                llmProvider = provider,
                llmApiKey = apiKey,
                maxIterations = 3,
                temperature = 0.2f,
                enableSafetyChecks = true,
                allowSystemCommands = false,
                allowNetworkAccess = false,
                allowFileOperations = false,
                optimizationStrategy = OptimizationStrategy.SAFE
            )
        }
        
        /**
         * 创建调试配置
         */
        fun createDebugConfig(
            provider: String = "openai",
            apiKey: String = ""
        ): AgentConfig {
            return AgentConfig(
                llmProvider = provider,
                llmApiKey = apiKey,
                maxIterations = 1,
                showEachStep = true,
                enableDebugMode = true,
                enableAutoSave = true,
                enableProgressCallback = true,
                temperature = 0.1f
            )
        }
    }
}

/**
 * 优化策略枚举
 */
@Serializable
enum class OptimizationStrategy {
    FAST,        // 快速模式：优先速度
    BALANCED,    // 平衡模式：速度与质量平衡
    QUALITY,     // 质量模式：优先代码质量
    PERFORMANCE, // 性能模式：优先执行性能
    SAFE        // 安全模式：优先安全性
}

/**
 * Agent执行状态
 */
@Serializable
data class AgentExecutionState(
    val currentIteration: Int = 0,
    val totalIterations: Int = 0,
    val currentScript: String = "",
    val lastResult: String = "",
    val isRunning: Boolean = false,
    val startTime: Long = 0,
    val errors: List<String> = emptyList(),
    val successRate: Float = 0.0f
)