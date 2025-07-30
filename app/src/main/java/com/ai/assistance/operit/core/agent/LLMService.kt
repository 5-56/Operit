package com.ai.assistance.operit.core.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import android.util.Log

/**
 * LLMService 统一大模型 API 接口
 */
interface LLMService {
    suspend fun generateScript(prompt: String): String
    suspend fun optimizeScript(lastScript: String, feedback: String): String
    suspend fun generateResponse(prompt: String, systemPrompt: String? = null): String
    suspend fun analyzeCode(code: String): String
    fun isConfigured(): Boolean
}

/**
 * LLM 请求和响应的数据模型
 */
@Serializable
data class LLMMessage(
    val role: String,
    val content: String
)

@Serializable
data class LLMRequest(
    val model: String,
    val messages: List<LLMMessage>,
    val temperature: Float = 0.7f,
    val max_tokens: Int = 2048
)

@Serializable
data class LLMChoice(
    val message: LLMMessage,
    val finish_reason: String? = null
)

@Serializable
data class LLMResponse(
    val choices: List<LLMChoice>,
    val usage: Map<String, Int>? = null
)

/**
 * 基础 LLM 服务实现
 */
abstract class BaseLLMService(
    protected val apiKey: String,
    protected val baseUrl: String,
    protected val model: String
) : LLMService {
    
    protected val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    protected val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
    
    companion object {
        private const val TAG = "LLMService"
    }

    override fun isConfigured(): Boolean {
        return apiKey.isNotBlank() && baseUrl.isNotBlank()
    }

    protected abstract fun getApiEndpoint(): String
    protected abstract fun createRequestHeaders(): Map<String, String>
    
    protected suspend fun makeApiCall(prompt: String, systemPrompt: String? = null): String = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            throw IllegalStateException("LLM service not configured properly")
        }
        
        try {
            val messages = mutableListOf<LLMMessage>()
            
            systemPrompt?.let { 
                messages.add(LLMMessage("system", it))
            }
            messages.add(LLMMessage("user", prompt))
            
            val request = LLMRequest(
                model = model,
                messages = messages,
                temperature = 0.7f,
                max_tokens = 2048
            )
            
            val requestBody = json.encodeToString(LLMRequest.serializer(), request)
                .toRequestBody("application/json".toMediaTypeOrNull())
            
            val httpRequest = Request.Builder()
                .url(getApiEndpoint())
                .post(requestBody)
                .apply {
                    createRequestHeaders().forEach { (key, value) ->
                        addHeader(key, value)
                    }
                }
                .build()
            
            val response = client.newCall(httpRequest).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "API call failed: ${response.code} ${response.message}")
                throw RuntimeException("API call failed: ${response.code} ${response.message}")
            }
            
            val responseBody = response.body?.string() ?: ""
            val llmResponse = json.decodeFromString(LLMResponse.serializer(), responseBody)
            
            return@withContext llmResponse.choices.firstOrNull()?.message?.content 
                ?: throw RuntimeException("No response content received")
                
        } catch (e: Exception) {
            Log.e(TAG, "Error making API call", e)
            throw e
        }
    }

    override suspend fun generateScript(prompt: String): String {
        val systemPrompt = """
            你是一个JavaScript脚本生成专家。根据用户需求生成可执行的JavaScript代码。
            
            要求：
            1. 生成的代码必须有main函数作为入口点
            2. main函数应该接受params参数
            3. 返回格式：{ success: boolean, message?: string, data?: any }
            4. 代码应该健壮，包含错误处理
            5. 如果需要调用工具，使用全局可用的工具函数
            
            示例格式：
            function main(params) {
                try {
                    // 你的实现代码
                    return { success: true, message: "执行成功" };
                } catch (error) {
                    return { success: false, message: error.message };
                }
            }
        """.trimIndent()
        
        return makeApiCall(prompt, systemPrompt)
    }

    override suspend fun optimizeScript(lastScript: String, feedback: String): String {
        val systemPrompt = """
            你是一个代码优化专家。根据执行反馈优化JavaScript脚本。
            
            要求：
            1. 分析反馈中的问题
            2. 保持main函数结构不变
            3. 修复错误和改进性能
            4. 保持代码的健壮性
            5. 添加必要的注释说明改进点
        """.trimIndent()
        
        val prompt = """
            原始脚本：
            ```javascript
            $lastScript
            ```
            
            执行反馈：
            $feedback
            
            请优化脚本以解决反馈中提到的问题。
        """.trimIndent()
        
        return makeApiCall(prompt, systemPrompt)
    }

    override suspend fun generateResponse(prompt: String, systemPrompt: String?): String {
        return makeApiCall(prompt, systemPrompt)
    }

    override suspend fun analyzeCode(code: String): String {
        val systemPrompt = """
            你是一个代码分析专家。分析提供的代码并给出改进建议。
            
            分析要点：
            1. 代码结构和可读性
            2. 潜在的错误和问题
            3. 性能优化建议
            4. 安全性考虑
            5. 最佳实践建议
        """.trimIndent()
        
        val prompt = """
            请分析以下代码：
            ```javascript
            $code
            ```
        """.trimIndent()
        
        return makeApiCall(prompt, systemPrompt)
    }
}

/**
 * OpenAI LLM 实现
 */
class OpenAILLMService(
    apiKey: String, 
    endpoint: String = "https://api.openai.com/v1",
    model: String = "gpt-3.5-turbo"
) : BaseLLMService(apiKey, endpoint, model) {
    
    override fun getApiEndpoint(): String = "$baseUrl/chat/completions"
    
    override fun createRequestHeaders(): Map<String, String> = mapOf(
        "Authorization" to "Bearer $apiKey",
        "Content-Type" to "application/json"
    )
}

/**
 * Qwen LLM 实现 (阿里云百炼平台)
 */
class QwenLLMService(
    apiKey: String,
    endpoint: String = "https://dashscope.aliyuncs.com/api/v1",
    model: String = "qwen-plus"
) : BaseLLMService(apiKey, endpoint, model) {
    
    override fun getApiEndpoint(): String = "$baseUrl/services/aigc/text-generation/generation"
    
    override fun createRequestHeaders(): Map<String, String> = mapOf(
        "Authorization" to "Bearer $apiKey",
        "Content-Type" to "application/json"
    )
}

/**
 * Claude LLM 实现 (Anthropic)
 */
class ClaudeLLMService(
    apiKey: String,
    endpoint: String = "https://api.anthropic.com/v1", 
    model: String = "claude-3-sonnet-20240229"
) : BaseLLMService(apiKey, endpoint, model) {
    
    override fun getApiEndpoint(): String = "$baseUrl/messages"
    
    override fun createRequestHeaders(): Map<String, String> = mapOf(
        "x-api-key" to apiKey,
        "Content-Type" to "application/json",
        "anthropic-version" to "2023-06-01"
    )
}

/**
 * 本地/自托管 LLM 实现
 */
class LocalLLMService(
    apiKey: String = "local",
    endpoint: String,
    model: String = "local-model"
) : BaseLLMService(apiKey, endpoint, model) {
    
    override fun getApiEndpoint(): String = "$baseUrl/chat/completions"
    
    override fun createRequestHeaders(): Map<String, String> = mapOf(
        "Content-Type" to "application/json"
    ).apply {
        if (apiKey != "local") {
            plus("Authorization" to "Bearer $apiKey")
        }
    }
    
    override fun isConfigured(): Boolean {
        return baseUrl.isNotBlank()
    }
}