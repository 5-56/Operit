package com.ai.assistance.operit.core.ai.remote

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 远程AI服务
 * 支持多种AI模型API调用，包括DeepSeek、GPT、Gemini等
 */
class RemoteAIService(private val context: Context) {
    
    companion object {
        private const val TAG = "RemoteAIService"
        private const val REQUEST_TIMEOUT_SECONDS = 30L
        private const val MAX_RETRIES = 3
        
        // API端点
        private const val DEEPSEEK_API_URL = "https://api.deepseek.com/v1/chat/completions"
        private const val OPENAI_API_URL = "https://api.openai.com/v1/chat/completions"
        private const val GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent"
        
        // 模型名称
        private const val DEEPSEEK_MODEL = "deepseek-chat"
        private const val GPT_MODEL = "gpt-3.5-turbo"
        private const val GEMINI_MODEL = "gemini-pro"
    }
    
    data class ProcessingResult(
        val response: String,
        val confidence: Float,
        val model: String,
        val toolsUsed: List<String> = emptyList(),
        val tokenUsage: TokenUsage? = null
    )
    
    data class TokenUsage(
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int
    )
    
    enum class AIModel {
        DEEPSEEK,
        GPT_3_5,
        GEMINI_PRO,
        AUTO // 自动选择最佳模型
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    private val preferencesManager = UserPreferencesManager(context)
    private val conversationHistory = mutableListOf<ConversationTurn>()
    private val maxHistoryLength = 10
    
    data class ConversationTurn(
        val role: String,
        val content: String,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * 处理用户输入
     */
    suspend fun processInput(
        input: String,
        toolHandler: AIToolHandler,
        preferredModel: AIModel = AIModel.AUTO
    ): ProcessingResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "远程AI服务处理输入: $input")
                
                // 选择最佳模型
                val selectedModel = selectBestModel(input, preferredModel)
                
                // 准备对话上下文
                val messages = prepareMessages(input, toolHandler)
                
                // 调用API
                val result = callAIAPI(selectedModel, messages, toolHandler)
                
                // 更新对话历史
                updateConversationHistory(input, result.response)
                
                result
                
            } catch (e: Exception) {
                Log.e(TAG, "远程AI服务处理失败", e)
                ProcessingResult(
                    response = "抱歉，远程AI服务暂时不可用：${e.message}",
                    confidence = 0f,
                    model = "error"
                )
            }
        }
    }
    
    private fun selectBestModel(input: String, preferredModel: AIModel): AIModel {
        return when (preferredModel) {
            AIModel.AUTO -> {
                when {
                    // 代码相关任务偏好DeepSeek
                    containsCodeKeywords(input) -> AIModel.DEEPSEEK
                    // 创意写作偏好GPT
                    containsCreativeKeywords(input) -> AIModel.GPT_3_5
                    // 分析任务偏好Gemini
                    containsAnalysisKeywords(input) -> AIModel.GEMINI_PRO
                    // 默认使用DeepSeek（性价比高）
                    else -> AIModel.DEEPSEEK
                }
            }
            else -> preferredModel
        }
    }
    
    private fun containsCodeKeywords(input: String): Boolean {
        val codeKeywords = listOf(
            "代码", "编程", "函数", "算法", "bug", "调试",
            "java", "kotlin", "python", "javascript", "sql",
            "class", "method", "variable", "array", "object"
        )
        return codeKeywords.any { input.lowercase().contains(it.lowercase()) }
    }
    
    private fun containsCreativeKeywords(input: String): Boolean {
        val creativeKeywords = listOf(
            "写作", "创作", "故事", "诗歌", "文章", "小说",
            "创意", "想象", "描述", "表达", "文学"
        )
        return creativeKeywords.any { input.lowercase().contains(it.lowercase()) }
    }
    
    private fun containsAnalysisKeywords(input: String): Boolean {
        val analysisKeywords = listOf(
            "分析", "比较", "总结", "解释", "原理", "机制",
            "数据", "统计", "报告", "研究", "调查"
        )
        return analysisKeywords.any { input.lowercase().contains(it.lowercase()) }
    }
    
    private fun prepareMessages(input: String, toolHandler: AIToolHandler): List<Map<String, String>> {
        val messages = mutableListOf<Map<String, String>>()
        
        // 系统提示
        val systemPrompt = buildSystemPrompt(toolHandler)
        messages.add(mapOf("role" to "system", "content" to systemPrompt))
        
        // 历史对话
        conversationHistory.takeLast(maxHistoryLength - 1).forEach { turn ->
            messages.add(mapOf("role" to turn.role, "content" to turn.content))
        }
        
        // 当前用户输入
        messages.add(mapOf("role" to "user", "content" to input))
        
        return messages
    }
    
    private fun buildSystemPrompt(toolHandler: AIToolHandler): String {
        val availableTools = toolHandler.getAvailableTools()
        
        return """
            你是一个智能助手，运行在用户的Android设备上。你具有以下能力：
            
            1. 自然语言对话和问答
            2. 文本创作和编程协助
            3. 调用设备上的各种工具和功能
            4. 系统自动化操作
            
            可用工具列表：
            ${availableTools.joinToString("\n") { "- ${it.name}: ${it.description}" }}
            
            回答要求：
            - 简洁明了，直接回答用户问题
            - 中文回答，语言自然流畅
            - 需要时主动建议使用相关工具
            - 提供具体可操作的建议
            - 避免提及无法完成的功能
            
            用户设备信息：
            - 系统：Android
            - 应用：Operit AI智能助手
            - 权限：已获得必要的系统权限
        """.trimIndent()
    }
    
    private suspend fun callAIAPI(
        model: AIModel,
        messages: List<Map<String, String>>,
        toolHandler: AIToolHandler
    ): ProcessingResult {
        return when (model) {
            AIModel.DEEPSEEK -> callDeepSeekAPI(messages, toolHandler)
            AIModel.GPT_3_5 -> callOpenAIAPI(messages, toolHandler)
            AIModel.GEMINI_PRO -> callGeminiAPI(messages, toolHandler)
            AIModel.AUTO -> callDeepSeekAPI(messages, toolHandler) // 默认
        }
    }
    
    private suspend fun callDeepSeekAPI(
        messages: List<Map<String, String>>,
        toolHandler: AIToolHandler
    ): ProcessingResult {
        val apiKey = preferencesManager.getApiKey("deepseek") ?: ""
        if (apiKey.isEmpty()) {
            throw IllegalStateException("DeepSeek API密钥未设置")
        }
        
        val requestBody = JSONObject().apply {
            put("model", DEEPSEEK_MODEL)
            put("messages", JSONArray(messages.map { JSONObject(it) }))
            put("max_tokens", 2048)
            put("temperature", 0.7)
            put("stream", false)
        }
        
        val request = Request.Builder()
            .url(DEEPSEEK_API_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()
        
        return executeRequest(request, DEEPSEEK_MODEL)
    }
    
    private suspend fun callOpenAIAPI(
        messages: List<Map<String, String>>,
        toolHandler: AIToolHandler
    ): ProcessingResult {
        val apiKey = preferencesManager.getApiKey("openai") ?: ""
        if (apiKey.isEmpty()) {
            throw IllegalStateException("OpenAI API密钥未设置")
        }
        
        val requestBody = JSONObject().apply {
            put("model", GPT_MODEL)
            put("messages", JSONArray(messages.map { JSONObject(it) }))
            put("max_tokens", 2048)
            put("temperature", 0.7)
        }
        
        val request = Request.Builder()
            .url(OPENAI_API_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()
        
        return executeRequest(request, GPT_MODEL)
    }
    
    private suspend fun callGeminiAPI(
        messages: List<Map<String, String>>,
        toolHandler: AIToolHandler
    ): ProcessingResult {
        val apiKey = preferencesManager.getApiKey("gemini") ?: ""
        if (apiKey.isEmpty()) {
            throw IllegalStateException("Gemini API密钥未设置")
        }
        
        // Gemini API格式不同，需要转换
        val contents = messages.filter { it["role"] != "system" }.map { message ->
            JSONObject().apply {
                put("role", if (message["role"] == "assistant") "model" else "user")
                put("parts", JSONArray().put(JSONObject().put("text", message["content"])))
            }
        }
        
        val requestBody = JSONObject().apply {
            put("contents", JSONArray(contents))
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 2048)
                put("temperature", 0.7)
            })
        }
        
        val request = Request.Builder()
            .url("$GEMINI_API_URL?key=$apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()
        
        return executeGeminiRequest(request, GEMINI_MODEL)
    }
    
    private suspend fun executeRequest(request: Request, modelName: String): ProcessingResult {
        return withContext(Dispatchers.IO) {
            try {
                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                
                if (!response.isSuccessful) {
                    throw IOException("API调用失败: ${response.code} - $responseBody")
                }
                
                parseStandardResponse(responseBody, modelName)
                
            } catch (e: Exception) {
                Log.e(TAG, "API请求执行失败", e)
                throw e
            }
        }
    }
    
    private suspend fun executeGeminiRequest(request: Request, modelName: String): ProcessingResult {
        return withContext(Dispatchers.IO) {
            try {
                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                
                if (!response.isSuccessful) {
                    throw IOException("Gemini API调用失败: ${response.code} - $responseBody")
                }
                
                parseGeminiResponse(responseBody, modelName)
                
            } catch (e: Exception) {
                Log.e(TAG, "Gemini API请求执行失败", e)
                throw e
            }
        }
    }
    
    private fun parseStandardResponse(responseBody: String, modelName: String): ProcessingResult {
        try {
            val jsonResponse = JSONObject(responseBody)
            val choices = jsonResponse.getJSONArray("choices")
            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.getJSONObject("message")
            val content = message.getString("content")
            
            // 解析token使用情况
            val usage = jsonResponse.optJSONObject("usage")
            val tokenUsage = usage?.let {
                TokenUsage(
                    promptTokens = it.optInt("prompt_tokens", 0),
                    completionTokens = it.optInt("completion_tokens", 0),
                    totalTokens = it.optInt("total_tokens", 0)
                )
            }
            
            // 计算置信度
            val confidence = calculateConfidence(content, tokenUsage)
            
            return ProcessingResult(
                response = content.trim(),
                confidence = confidence,
                model = modelName,
                tokenUsage = tokenUsage
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "解析API响应失败", e)
            throw IllegalArgumentException("响应格式无效: $responseBody")
        }
    }
    
    private fun parseGeminiResponse(responseBody: String, modelName: String): ProcessingResult {
        try {
            val jsonResponse = JSONObject(responseBody)
            val candidates = jsonResponse.getJSONArray("candidates")
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            val text = parts.getJSONObject(0).getString("text")
            
            // Gemini没有详细的token使用信息，使用简单估算
            val estimatedTokens = text.length / 4 // 粗略估算
            val tokenUsage = TokenUsage(
                promptTokens = 0,
                completionTokens = estimatedTokens,
                totalTokens = estimatedTokens
            )
            
            val confidence = calculateConfidence(text, tokenUsage)
            
            return ProcessingResult(
                response = text.trim(),
                confidence = confidence,
                model = modelName,
                tokenUsage = tokenUsage
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "解析Gemini响应失败", e)
            throw IllegalArgumentException("Gemini响应格式无效: $responseBody")
        }
    }
    
    private fun calculateConfidence(content: String, tokenUsage: TokenUsage?): Float {
        // 基于响应质量计算置信度
        return when {
            content.contains("抱歉") || content.contains("无法") -> 0.5f
            content.length < 20 -> 0.6f
            content.length < 100 -> 0.8f
            tokenUsage?.completionTokens ?: 0 > 100 -> 0.95f
            else -> 0.85f
        }
    }
    
    private fun updateConversationHistory(userInput: String, aiResponse: String) {
        conversationHistory.add(ConversationTurn("user", userInput))
        conversationHistory.add(ConversationTurn("assistant", aiResponse))
        
        // 保持历史记录在限制范围内
        while (conversationHistory.size > maxHistoryLength * 2) {
            conversationHistory.removeAt(0)
            conversationHistory.removeAt(0) // 移除成对的对话
        }
    }
    
    /**
     * 清理对话历史
     */
    fun clearConversationHistory() {
        conversationHistory.clear()
        Log.d(TAG, "对话历史已清理")
    }
    
    /**
     * 获取对话历史
     */
    fun getConversationHistory(): List<ConversationTurn> {
        return conversationHistory.toList()
    }
    
    /**
     * 检查API密钥是否配置
     */
    fun isConfigured(model: AIModel): Boolean {
        return when (model) {
            AIModel.DEEPSEEK -> preferencesManager.getApiKey("deepseek")?.isNotEmpty() == true
            AIModel.GPT_3_5 -> preferencesManager.getApiKey("openai")?.isNotEmpty() == true
            AIModel.GEMINI_PRO -> preferencesManager.getApiKey("gemini")?.isNotEmpty() == true
            AIModel.AUTO -> isConfigured(AIModel.DEEPSEEK) || 
                           isConfigured(AIModel.GPT_3_5) || 
                           isConfigured(AIModel.GEMINI_PRO)
        }
    }
    
    /**
     * 获取可用的模型列表
     */
    fun getAvailableModels(): List<AIModel> {
        return AIModel.values().filter { model ->
            when (model) {
                AIModel.AUTO -> true
                else -> isConfigured(model)
            }
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        try {
            // 取消所有进行中的请求
            httpClient.dispatcher.executorService.shutdown()
            httpClient.connectionPool.evictAll()
            
            clearConversationHistory()
            
            Log.d(TAG, "远程AI服务资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放远程AI服务资源失败", e)
        }
    }
}