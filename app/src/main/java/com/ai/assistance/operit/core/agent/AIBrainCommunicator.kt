package com.ai.assistance.operit.core.agent

import android.content.Context
import com.ai.assistance.operit.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * AI大脑通信器
 * 
 * 负责与服务器端AI进行实际通信：
 * 1. 发送屏幕感知数据到AI服务
 * 2. 接收AI生成的操作指令
 * 3. 处理AI思考过程反馈
 * 4. 管理通信会话和上下文
 * 5. 支持多种AI服务提供商
 */
class AIBrainCommunicator(private val context: Context) {
    
    companion object {
        private const val TAG = "AIBrainCommunicator"
        private const val DEFAULT_TIMEOUT = 30L // 30秒超时
        private const val MAX_RETRY_COUNT = 3
        private const val RETRY_DELAY = 1000L // 1秒重试延迟
    }
    
    // HTTP客户端
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
        .addInterceptor(LoggingInterceptor())
        .addInterceptor(AuthInterceptor())
        .build()
    
    // JSON序列化器
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }
    
    /**
     * AI服务配置
     */
    @Serializable
    data class AIServiceConfig(
        val baseUrl: String,
        val apiKey: String,
        val model: String = "gpt-4o",
        val maxTokens: Int = 2000,
        val temperature: Float = 0.7f,
        val provider: AIProvider = AIProvider.OPENAI
    )
    
    /**
     * AI服务提供商
     */
    enum class AIProvider {
        OPENAI,
        ANTHROPIC,
        CUSTOM,
        LOCAL
    }
    
    /**
     * AI请求数据
     */
    @Serializable
    data class AIRequest(
        val userIntent: String,
        val screenData: ScreenAnalysisData,
        val context: RequestContext,
        val sessionId: String = generateSessionId()
    )
    
    /**
     * 屏幕分析数据
     */
    @Serializable
    data class ScreenAnalysisData(
        val currentApp: String?,
        val screenSize: Pair<Int, Int>,
        val orientation: Int,
        val elements: List<ElementInfo>,
        val screenshot: String? = null, // Base64编码的截图
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * 元素信息
     */
    @Serializable
    data class ElementInfo(
        val id: String,
        val type: String,
        val text: String?,
        val description: String?,
        val bounds: BoundsInfo,
        val clickable: Boolean,
        val scrollable: Boolean,
        val editable: Boolean,
        val enabled: Boolean
    )
    
    /**
     * 边界信息
     */
    @Serializable
    data class BoundsInfo(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )
    
    /**
     * 请求上下文
     */
    @Serializable
    data class RequestContext(
        val previousActions: List<String> = emptyList(),
        val userPreferences: Map<String, String> = emptyMap(),
        val constraints: List<String> = emptyList(),
        val priority: String = "normal"
    )
    
    /**
     * AI响应
     */
    @Serializable
    data class AIResponse(
        val success: Boolean,
        val instructions: List<AIInstructionData> = emptyList(),
        val thinking: AIThinkingData? = null,
        val error: String? = null,
        val confidence: Float = 0.0f,
        val sessionId: String,
        val nextAction: String? = null
    )
    
    /**
     * AI指令数据
     */
    @Serializable
    data class AIInstructionData(
        val type: String,
        val parameters: Map<String, String>,
        val description: String,
        val priority: Int = 0,
        val timeout: Long = 5000L
    )
    
    /**
     * AI思考数据
     */
    @Serializable
    data class AIThinkingData(
        val step: String,
        val reasoning: String,
        val confidence: Float,
        val alternatives: List<String> = emptyList(),
        val nextAction: String
    )
    
    /**
     * 通信结果
     */
    data class CommunicationResult(
        val success: Boolean,
        val response: AIResponse? = null,
        val error: String? = null,
        val duration: Long = 0L
    )
    
    // 当前AI服务配置
    private var currentConfig: AIServiceConfig? = null
    
    /**
     * 配置AI服务
     */
    fun configureAIService(config: AIServiceConfig) {
        currentConfig = config
        LogUtils.i(TAG, "AI服务已配置: ${config.provider} - ${config.model}")
    }
    
    /**
     * 获取默认配置
     */
    fun getDefaultConfig(): AIServiceConfig {
        return AIServiceConfig(
            baseUrl = "https://api.openai.com/v1",
            apiKey = "your-api-key-here",
            model = "gpt-4o",
            provider = AIProvider.OPENAI
        )
    }
    
    /**
     * 发送用户意图到AI大脑
     */
    suspend fun sendUserIntentToAI(
        userIntent: String,
        screenData: EnhancedScreenPerception.ScreenPerceptionData,
        context: RequestContext = RequestContext()
    ): CommunicationResult = withContext(Dispatchers.IO) {
        
        val startTime = System.currentTimeMillis()
        var lastError: String? = null
        
        // 检查配置
        val config = currentConfig ?: getDefaultConfig()
        
        LogUtils.i(TAG, "发送用户意图到AI: $userIntent")
        
        repeat(MAX_RETRY_COUNT) { attempt ->
            try {
                // 转换屏幕数据
                val analysisData = convertScreenData(screenData)
                
                // 构建请求
                val request = AIRequest(
                    userIntent = userIntent,
                    screenData = analysisData,
                    context = context
                )
                
                // 发送请求
                val response = when (config.provider) {
                    AIProvider.OPENAI -> sendToOpenAI(request, config)
                    AIProvider.ANTHROPIC -> sendToAnthropic(request, config)
                    AIProvider.CUSTOM -> sendToCustomAPI(request, config)
                    AIProvider.LOCAL -> sendToLocalAI(request, config)
                }
                
                if (response.success) {
                    val duration = System.currentTimeMillis() - startTime
                    LogUtils.i(TAG, "AI通信成功，耗时: ${duration}ms，置信度: ${response.confidence}")
                    return@withContext CommunicationResult(true, response, duration = duration)
                } else {
                    lastError = response.error ?: "AI响应失败"
                    LogUtils.w(TAG, "第${attempt + 1}次尝试失败: $lastError")
                }
                
            } catch (e: Exception) {
                lastError = e.message ?: "通信异常"
                LogUtils.e(TAG, "第${attempt + 1}次通信异常", e)
            }
            
            // 重试延迟
            if (attempt < MAX_RETRY_COUNT - 1) {
                kotlinx.coroutines.delay(RETRY_DELAY)
            }
        }
        
        val duration = System.currentTimeMillis() - startTime
        LogUtils.e(TAG, "AI通信失败，总耗时: ${duration}ms，错误: $lastError")
        return@withContext CommunicationResult(false, error = lastError, duration = duration)
    }
    
    /**
     * 转换屏幕数据
     */
    private fun convertScreenData(screenData: EnhancedScreenPerception.ScreenPerceptionData): ScreenAnalysisData {
        val elements = screenData.uiStructure.elements.map { element ->
            ElementInfo(
                id = element.id,
                type = element.className.substringAfterLast('.'),
                text = element.text,
                description = element.contentDescription,
                bounds = BoundsInfo(
                    left = element.bounds.left,
                    top = element.bounds.top,
                    right = element.bounds.right,
                    bottom = element.bounds.bottom
                ),
                clickable = element.isClickable,
                scrollable = element.isScrollable,
                editable = element.isEditable,
                enabled = element.isEnabled
            )
        }
        
        return ScreenAnalysisData(
            currentApp = screenData.contextInfo.currentApp,
            screenSize = screenData.visualData?.screenSize ?: Pair(0, 0),
            orientation = screenData.contextInfo.orientation,
            elements = elements,
            screenshot = null // 可以添加截图的Base64编码
        )
    }
    
    /**
     * 发送到OpenAI
     */
    private suspend fun sendToOpenAI(request: AIRequest, config: AIServiceConfig): AIResponse = withContext(Dispatchers.IO) {
        try {
            val prompt = buildOpenAIPrompt(request)
            
            val requestBody = mapOf(
                "model" to config.model,
                "messages" to listOf(
                    mapOf(
                        "role" to "system",
                        "content" to "You are an AI agent that controls Android devices. Analyze the screen and generate precise instructions to fulfill user intents."
                    ),
                    mapOf(
                        "role" to "user",
                        "content" to prompt
                    )
                ),
                "max_tokens" to config.maxTokens,
                "temperature" to config.temperature
            )
            
            val requestBodyJson = json.encodeToString(
                kotlinx.serialization.serializer<Map<String, Any>>(),
                requestBody
            )
            
            val httpRequest = Request.Builder()
                .url("${config.baseUrl}/chat/completions")
                .header("Authorization", "Bearer ${config.apiKey}")
                .header("Content-Type", "application/json")
                .post(requestBodyJson.toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = httpClient.newCall(httpRequest).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                return@withContext parseOpenAIResponse(responseBody, request.sessionId)
            } else {
                return@withContext AIResponse(
                    success = false,
                    error = "HTTP ${response.code}: ${response.message}",
                    sessionId = request.sessionId
                )
            }
            
        } catch (e: Exception) {
            LogUtils.e(TAG, "OpenAI通信异常", e)
            return@withContext AIResponse(
                success = false,
                error = e.message ?: "OpenAI通信失败",
                sessionId = request.sessionId
            )
        }
    }
    
    /**
     * 发送到Anthropic
     */
    private suspend fun sendToAnthropic(request: AIRequest, config: AIServiceConfig): AIResponse {
        // TODO: 实现Anthropic API通信
        LogUtils.w(TAG, "Anthropic API暂未实现")
        return AIResponse(
            success = false,
            error = "Anthropic API暂未实现",
            sessionId = request.sessionId
        )
    }
    
    /**
     * 发送到自定义API
     */
    private suspend fun sendToCustomAPI(request: AIRequest, config: AIServiceConfig): AIResponse {
        // TODO: 实现自定义API通信
        LogUtils.w(TAG, "自定义API暂未实现")
        return AIResponse(
            success = false,
            error = "自定义API暂未实现",
            sessionId = request.sessionId
        )
    }
    
    /**
     * 发送到本地AI
     */
    private suspend fun sendToLocalAI(request: AIRequest, config: AIServiceConfig): AIResponse {
        // 模拟本地AI响应（用于测试）
        LogUtils.i(TAG, "使用本地AI模拟响应")
        
        kotlinx.coroutines.delay(1000) // 模拟处理时间
        
        val mockInstructions = listOf(
            AIInstructionData(
                type = "wait",
                parameters = mapOf("duration" to "1000"),
                description = "等待1秒钟"
            )
        )
        
        val mockThinking = AIThinkingData(
            step = "分析用户意图",
            reasoning = "用户想要执行: ${request.userIntent}",
            confidence = 0.8f,
            nextAction = "执行等待操作"
        )
        
        return AIResponse(
            success = true,
            instructions = mockInstructions,
            thinking = mockThinking,
            confidence = 0.8f,
            sessionId = request.sessionId,
            nextAction = "continue"
        )
    }
    
    /**
     * 构建OpenAI提示词
     */
    private fun buildOpenAIPrompt(request: AIRequest): String {
        return buildString {
            appendLine("用户意图: ${request.userIntent}")
            appendLine("")
            appendLine("当前屏幕信息:")
            appendLine("应用: ${request.screenData.currentApp}")
            appendLine("屏幕尺寸: ${request.screenData.screenSize}")
            appendLine("方向: ${request.screenData.orientation}")
            appendLine("元素数量: ${request.screenData.elements.size}")
            appendLine("")
            appendLine("主要可交互元素:")
            request.screenData.elements
                .filter { it.clickable || it.editable }
                .take(10)
                .forEach { element ->
                    appendLine("- ${element.type}: \"${element.text ?: element.description ?: "无文本"}\" [${element.bounds.left},${element.bounds.top},${element.bounds.right},${element.bounds.bottom}]")
                }
            appendLine("")
            appendLine("请分析当前屏幕状态，理解用户意图，并生成精确的操作指令。")
            appendLine("返回JSON格式，包含instructions数组，每个指令包含type、parameters、description字段。")
            appendLine("支持的指令类型: tap, swipe, input_text, press_key, wait, scroll")
        }
    }
    
    /**
     * 解析OpenAI响应
     */
    private fun parseOpenAIResponse(responseBody: String, sessionId: String): AIResponse {
        return try {
            // 这里需要实际解析OpenAI的响应格式
            // 示例实现，实际需要根据OpenAI API响应格式调整
            
            LogUtils.d(TAG, "OpenAI响应: $responseBody")
            
            // 模拟解析结果
            val mockInstructions = listOf(
                AIInstructionData(
                    type = "tap",
                    parameters = mapOf("x" to "500", "y" to "800"),
                    description = "点击屏幕中央"
                )
            )
            
            AIResponse(
                success = true,
                instructions = mockInstructions,
                confidence = 0.9f,
                sessionId = sessionId
            )
            
        } catch (e: Exception) {
            LogUtils.e(TAG, "解析OpenAI响应失败", e)
            AIResponse(
                success = false,
                error = "响应解析失败: ${e.message}",
                sessionId = sessionId
            )
        }
    }
    
    /**
     * 生成会话ID
     */
    private fun generateSessionId(): String {
        return "session_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
    
    /**
     * 日志拦截器
     */
    private class LoggingInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val startTime = System.nanoTime()
            
            LogUtils.d(TAG, "HTTP请求: ${request.method} ${request.url}")
            
            val response = chain.proceed(request)
            val endTime = System.nanoTime()
            val duration = (endTime - startTime) / 1_000_000 // 转换为毫秒
            
            LogUtils.d(TAG, "HTTP响应: ${response.code} (${duration}ms)")
            
            return response
        }
    }
    
    /**
     * 认证拦截器
     */
    private class AuthInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            
            // 可以在这里添加通用的认证逻辑
            // 例如刷新token、添加签名等
            
            return chain.proceed(request)
        }
    }
    
    /**
     * 测试AI连接
     */
    suspend fun testConnection(config: AIServiceConfig): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            configureAIService(config)
            
            val testRequest = AIRequest(
                userIntent = "测试连接",
                screenData = ScreenAnalysisData(
                    currentApp = "test",
                    screenSize = Pair(1080, 1920),
                    orientation = 1,
                    elements = emptyList()
                ),
                context = RequestContext()
            )
            
            val result = when (config.provider) {
                AIProvider.LOCAL -> true // 本地测试总是成功
                else -> {
                    val response = sendToOpenAI(testRequest, config)
                    response.success
                }
            }
            
            LogUtils.i(TAG, "AI连接测试${if (result) "成功" else "失败"}")
            result
            
        } catch (e: Exception) {
            LogUtils.e(TAG, "AI连接测试异常", e)
            false
        }
    }
    
    /**
     * 获取支持的AI提供商
     */
    fun getSupportedProviders(): List<AIProvider> {
        return listOf(
            AIProvider.OPENAI,
            AIProvider.ANTHROPIC,
            AIProvider.LOCAL,
            AIProvider.CUSTOM
        )
    }
    
    /**
     * 获取通信统计信息
     */
    fun getStatistics(): Map<String, Any> {
        return mapOf(
            "totalRequests" to 0,
            "successfulRequests" to 0,
            "failedRequests" to 0,
            "averageResponseTime" to 0L,
            "currentProvider" to (currentConfig?.provider?.name ?: "未配置")
        )
    }
}