package com.ai.assistance.operit.core.agent

/**
 * AgentConfig 用于自定义 agent 自动化流程参数和 hook。
 * 支持丰富的配置选项和智能化功能。
 */
data class AgentConfig(
    // === 基础配置 ===
    val maxIterations: Int = 3,
    val autoTerminateOnSuccess: Boolean = true,
    val showEachStep: Boolean = true,
    
    // === LLM 配置 ===
    val llmProvider: String = "openai", // 可选: openai/qwen/claude/local
    val llmApiKey: String = "",
    val llmEndpoint: String = "",
    val llmModel: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    
    // === 执行配置 ===
    val executeInSandbox: Boolean = true,
    val allowNetworkAccess: Boolean = true,
    val allowFileAccess: Boolean = true,
    val allowSystemAccess: Boolean = false,
    val timeoutSeconds: Int = 60,
    
    // === 智能化配置 ===
    val enableAutoOptimization: Boolean = true,
    val enableContextLearning: Boolean = true,
    val enableErrorRecovery: Boolean = true,
    val enablePerformanceAnalysis: Boolean = true,
    val enableSecurityChecks: Boolean = true,
    
    // === 缓存配置 ===
    val enableScriptCaching: Boolean = true,
    val enableResultCaching: Boolean = true,
    val cacheExpirationHours: Int = 24,
    
    // === 日志配置 ===
    val logLevel: LogLevel = LogLevel.INFO,
    val enableDetailedLogging: Boolean = false,
    val logToFile: Boolean = true,
    
    // === Hook 函数 ===
    val preProcessHook: ((String) -> Unit)? = null,
    val postProcessHook: ((String, String) -> Unit)? = null,
    val errorHook: ((Exception, String) -> Unit)? = null,
    val optimizationHook: ((String, String, String) -> Unit)? = null,
    
    // === 高级配置 ===
    val enableParallelExecution: Boolean = false,
    val enableResourceMonitoring: Boolean = true,
    val enableAdaptiveLearning: Boolean = true,
    val qualityThreshold: Float = 0.8f,
    val retryOnFailure: Boolean = true,
    val maxRetries: Int = 2,
    
    // === 用户体验配置 ===
    val enableProgressFeedback: Boolean = true,
    val enableRealTimeUpdates: Boolean = true,
    val enableInteractiveMode: Boolean = false,
    val enableVoiceFeedback: Boolean = false
) {
    
    enum class LogLevel {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }
    
    /**
     * 验证配置的有效性
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        
        if (maxIterations <= 0) {
            errors.add("maxIterations must be greater than 0")
        }
        
        if (llmApiKey.isBlank() && llmProvider != "local") {
            errors.add("llmApiKey is required for provider: $llmProvider")
        }
        
        if (llmEndpoint.isBlank()) {
            errors.add("llmEndpoint is required")
        }
        
        if (temperature < 0f || temperature > 2f) {
            errors.add("temperature must be between 0 and 2")
        }
        
        if (maxTokens <= 0) {
            errors.add("maxTokens must be greater than 0")
        }
        
        if (timeoutSeconds <= 0) {
            errors.add("timeoutSeconds must be greater than 0")
        }
        
        if (qualityThreshold < 0f || qualityThreshold > 1f) {
            errors.add("qualityThreshold must be between 0 and 1")
        }
        
        if (maxRetries < 0) {
            errors.add("maxRetries must be non-negative")
        }
        
        return errors
    }
    
    /**
     * 创建开发模式配置
     */
    fun toDevelopmentMode(): AgentConfig = copy(
        showEachStep = true,
        enableDetailedLogging = true,
        logLevel = LogLevel.DEBUG,
        executeInSandbox = true,
        enableSecurityChecks = true,
        enableErrorRecovery = true
    )
    
    /**
     * 创建生产模式配置
     */
    fun toProductionMode(): AgentConfig = copy(
        showEachStep = false,
        enableDetailedLogging = false,
        logLevel = LogLevel.WARN,
        executeInSandbox = true,
        enableSecurityChecks = true,
        enablePerformanceAnalysis = true
    )
    
    /**
     * 创建高性能模式配置
     */
    fun toHighPerformanceMode(): AgentConfig = copy(
        enableParallelExecution = true,
        enableScriptCaching = true,
        enableResultCaching = true,
        enableResourceMonitoring = true,
        timeoutSeconds = 120
    )
    
    /**
     * 创建安全模式配置
     */
    fun toSecurityMode(): AgentConfig = copy(
        executeInSandbox = true,
        allowNetworkAccess = false,
        allowFileAccess = false,
        allowSystemAccess = false,
        enableSecurityChecks = true,
        enableDetailedLogging = true
    )
    
    companion object {
        
        /**
         * 创建默认配置
         */
        fun default() = AgentConfig()
        
        /**
         * 创建快速配置
         */
        fun quick(
            llmProvider: String = "openai",
            apiKey: String = "",
            endpoint: String = ""
        ) = AgentConfig(
            llmProvider = llmProvider,
            llmApiKey = apiKey,
            llmEndpoint = endpoint,
            maxIterations = 1,
            showEachStep = false
        )
        
        /**
         * 创建智能配置（启用所有AI功能）
         */
        fun intelligent(
            llmProvider: String = "openai",
            apiKey: String = "",
            endpoint: String = ""
        ) = AgentConfig(
            llmProvider = llmProvider,
            llmApiKey = apiKey,
            llmEndpoint = endpoint,
            enableAutoOptimization = true,
            enableContextLearning = true,
            enableErrorRecovery = true,
            enableAdaptiveLearning = true,
            enablePerformanceAnalysis = true,
            maxIterations = 5
        )
        
        /**
         * 从环境变量创建配置
         */
        fun fromEnvironment(): AgentConfig {
            val provider = System.getenv("OPERIT_LLM_PROVIDER") ?: "openai"
            val apiKey = System.getenv("OPERIT_LLM_API_KEY") ?: ""
            val endpoint = System.getenv("OPERIT_LLM_ENDPOINT") ?: getDefaultEndpoint(provider)
            
            return AgentConfig(
                llmProvider = provider,
                llmApiKey = apiKey,
                llmEndpoint = endpoint
            )
        }
        
        private fun getDefaultEndpoint(provider: String): String = when (provider.lowercase()) {
            "openai" -> "https://api.openai.com/v1"
            "qwen" -> "https://dashscope.aliyuncs.com/api/v1"
            "claude" -> "https://api.anthropic.com/v1"
            else -> ""
        }
    }
}