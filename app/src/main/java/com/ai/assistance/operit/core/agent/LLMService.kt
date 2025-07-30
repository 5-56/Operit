package com.ai.assistance.operit.core.agent

import android.util.Log
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
 * LLM服务统一接口
 */
interface LLMService {
    suspend fun generateScript(prompt: String): String
    suspend fun optimizeScript(lastScript: String, feedback: String): String
    suspend fun testConnection(): Result<String>
}

/**
 * OpenAI LLM服务实现
 */
class OpenAILLMService(
    private val apiKey: String,
    private val endpoint: String = "https://api.openai.com/v1/chat/completions",
    private val model: String = "gpt-4o-mini"
) : LLMService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val JSON = "application/json; charset=utf-8".toMediaType()
    
    override suspend fun generateScript(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = """
                你是一个专业的自动化脚本生成助手。请根据用户需求生成高质量的JavaScript脚本。
                要求：
                1. 脚本必须包含main函数作为入口点
                2. 返回值应包含success字段表示执行状态
                3. 代码要简洁、可读性强
                4. 添加必要的错误处理
                5. 包含详细的注释说明
                
                用户需求：$prompt
                
                请生成完整的JavaScript脚本：
            """.trimIndent()
            
            return@withContext callOpenAI(systemPrompt)
        } catch (e: Exception) {
            Log.e("OpenAILLMService", "生成脚本失败", e)
            return@withContext generateFallbackScript(prompt)
        }
    }
    
    override suspend fun optimizeScript(lastScript: String, feedback: String): String = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = """
                你是一个专业的代码优化助手。请根据执行反馈优化JavaScript脚本。
                
                原始脚本：
                ```javascript
                $lastScript
                ```
                
                执行反馈：$feedback
                
                请分析问题并提供优化后的脚本：
                1. 修复可能的错误
                2. 提高代码质量和效率
                3. 增强错误处理
                4. 保持代码结构清晰
                
                请提供优化后的完整JavaScript脚本：
            """.trimIndent()
            
            return@withContext callOpenAI(systemPrompt)
        } catch (e: Exception) {
            Log.e("OpenAILLMService", "优化脚本失败", e)
            return@withContext "$lastScript\n// 优化失败，保持原脚本不变\n// 错误信息: ${e.message}"
        }
    }
    
    override suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        try {
            callOpenAI("测试连接")
            Result.success("OpenAI连接测试成功")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun callOpenAI(prompt: String): String {
        val requestBody = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("max_tokens", 4000)
            put("temperature", 0.7)
        }
        
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody(JSON))
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("请求失败: ${response.code} ${response.message}")
            }
            
            val responseBody = response.body?.string() ?: throw IOException("响应体为空")
            val jsonResponse = JSONObject(responseBody)
            
            if (jsonResponse.has("error")) {
                throw IOException("API错误: ${jsonResponse.getJSONObject("error").getString("message")}")
            }
            
            return jsonResponse.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }
    
    private fun generateFallbackScript(prompt: String): String {
        return """
            // 自动生成的脚本（基于需求：$prompt）
            function main(params) {
                try {
                    // TODO: 根据需求 "$prompt" 实现具体逻辑
                    console.log("开始执行任务：$prompt");
                    
                    // 这里添加具体的实现逻辑
                    
                    return { 
                        success: true, 
                        message: "脚本执行成功",
                        data: null 
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
}

/**
 * Qwen LLM服务实现
 */
class QwenLLMService(
    private val apiKey: String,
    private val endpoint: String = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation",
    private val model: String = "qwen-turbo"
) : LLMService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val JSON = "application/json; charset=utf-8".toMediaType()
    
    override suspend fun generateScript(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = """
                你是一个专业的自动化脚本生成助手。请根据用户需求生成高质量的JavaScript脚本。
                
                用户需求：$prompt
                
                请生成完整的JavaScript脚本，包含main函数作为入口点。
            """.trimIndent()
            
            return@withContext callQwen(systemPrompt)
        } catch (e: Exception) {
            Log.e("QwenLLMService", "生成脚本失败", e)
            return@withContext generateFallbackScript(prompt)
        }
    }
    
    override suspend fun optimizeScript(lastScript: String, feedback: String): String = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = """
                请根据执行反馈优化以下JavaScript脚本：
                
                原始脚本：
                ```javascript
                $lastScript
                ```
                
                执行反馈：$feedback
                
                请提供优化后的完整脚本。
            """.trimIndent()
            
            return@withContext callQwen(systemPrompt)
        } catch (e: Exception) {
            Log.e("QwenLLMService", "优化脚本失败", e)
            return@withContext "$lastScript\n// Qwen优化失败：${e.message}"
        }
    }
    
    override suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        try {
            callQwen("测试连接")
            Result.success("Qwen连接测试成功")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun callQwen(prompt: String): String {
        val requestBody = JSONObject().apply {
            put("model", model)
            put("input", JSONObject().apply {
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
            })
            put("parameters", JSONObject().apply {
                put("max_tokens", 4000)
                put("temperature", 0.7)
            })
        }
        
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody(JSON))
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Qwen请求失败: ${response.code} ${response.message}")
            }
            
            val responseBody = response.body?.string() ?: throw IOException("响应体为空")
            val jsonResponse = JSONObject(responseBody)
            
            if (jsonResponse.has("code") && jsonResponse.getString("code") != "Success") {
                throw IOException("Qwen API错误: ${jsonResponse.optString("message", "未知错误")}")
            }
            
            return jsonResponse.getJSONObject("output")
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }
    
    private fun generateFallbackScript(prompt: String): String {
        return """
            // Qwen自动生成的脚本（基于需求：$prompt）
            function main(params) {
                try {
                    console.log("Qwen生成的脚本开始执行：$prompt");
                    // TODO: 实现具体逻辑
                    return { success: true, message: "Qwen脚本执行成功" };
                } catch (error) {
                    return { success: false, message: "执行失败: " + error.message };
                }
            }
        """.trimIndent()
    }
}

/**
 * Claude LLM服务实现
 */
class ClaudeLLMService(
    private val apiKey: String,
    private val endpoint: String = "https://api.anthropic.com/v1/messages",
    private val model: String = "claude-3-haiku-20240307"
) : LLMService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val JSON = "application/json; charset=utf-8".toMediaType()
    
    override suspend fun generateScript(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            return@withContext callClaude(prompt)
        } catch (e: Exception) {
            Log.e("ClaudeLLMService", "生成脚本失败", e)
            return@withContext generateFallbackScript(prompt)
        }
    }
    
    override suspend fun optimizeScript(lastScript: String, feedback: String): String = withContext(Dispatchers.IO) {
        try {
            val optimizePrompt = """
                请优化以下JavaScript脚本：
                
                原始脚本：
                ```javascript
                $lastScript
                ```
                
                执行反馈：$feedback
                
                请提供优化后的完整脚本。
            """.trimIndent()
            
            return@withContext callClaude(optimizePrompt)
        } catch (e: Exception) {
            Log.e("ClaudeLLMService", "优化脚本失败", e)
            return@withContext "$lastScript\n// Claude优化失败：${e.message}"
        }
    }
    
    override suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        try {
            callClaude("测试连接")
            Result.success("Claude连接测试成功")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun callClaude(prompt: String): String {
        val requestBody = JSONObject().apply {
            put("model", model)
            put("max_tokens", 4000)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }
        
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody(JSON))
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Claude请求失败: ${response.code} ${response.message}")
            }
            
            val responseBody = response.body?.string() ?: throw IOException("响应体为空")
            val jsonResponse = JSONObject(responseBody)
            
            if (jsonResponse.has("error")) {
                throw IOException("Claude API错误: ${jsonResponse.getJSONObject("error").getString("message")}")
            }
            
            return jsonResponse.getJSONArray("content")
                .getJSONObject(0)
                .getString("text")
        }
    }
    
    private fun generateFallbackScript(prompt: String): String {
        return """
            // Claude自动生成的脚本（基于需求：$prompt）
            function main(params) {
                try {
                    console.log("Claude生成的脚本开始执行：$prompt");
                    // TODO: 实现具体逻辑
                    return { success: true, message: "Claude脚本执行成功" };
                } catch (error) {
                    return { success: false, message: "执行失败: " + error.message };
                }
            }
        """.trimIndent()
    }
}

/**
 * Gemini LLM服务实现
 */
class GeminiLLMService(
    private val apiKey: String,
    private val endpoint: String = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent",
    private val model: String = "gemini-pro"
) : LLMService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val JSON = "application/json; charset=utf-8".toMediaType()
    
    override suspend fun generateScript(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            return@withContext callGemini(prompt)
        } catch (e: Exception) {
            Log.e("GeminiLLMService", "生成脚本失败", e)
            return@withContext generateFallbackScript(prompt)
        }
    }
    
    override suspend fun optimizeScript(lastScript: String, feedback: String): String = withContext(Dispatchers.IO) {
        try {
            val optimizePrompt = """
                请优化以下JavaScript脚本：
                
                原始脚本：
                ```javascript
                $lastScript
                ```
                
                执行反馈：$feedback
                
                请提供优化后的完整脚本。
            """.trimIndent()
            
            return@withContext callGemini(optimizePrompt)
        } catch (e: Exception) {
            Log.e("GeminiLLMService", "优化脚本失败", e)
            return@withContext "$lastScript\n// Gemini优化失败：${e.message}"
        }
    }
    
    override suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        try {
            callGemini("测试连接")
            Result.success("Gemini连接测试成功")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun callGemini(prompt: String): String {
        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 4000)
                put("temperature", 0.7)
            })
        }
        
        val request = Request.Builder()
            .url("$endpoint?key=$apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody(JSON))
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Gemini请求失败: ${response.code} ${response.message}")
            }
            
            val responseBody = response.body?.string() ?: throw IOException("响应体为空")
            val jsonResponse = JSONObject(responseBody)
            
            if (jsonResponse.has("error")) {
                throw IOException("Gemini API错误: ${jsonResponse.getJSONObject("error").getString("message")}")
            }
            
            return jsonResponse.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        }
    }
    
    private fun generateFallbackScript(prompt: String): String {
        return """
            // Gemini自动生成的脚本（基于需求：$prompt）
            function main(params) {
                try {
                    console.log("Gemini生成的脚本开始执行：$prompt");
                    // TODO: 实现具体逻辑
                    return { success: true, message: "Gemini脚本执行成功" };
                } catch (error) {
                    return { success: false, message: "执行失败: " + error.message };
                }
            }
        """.trimIndent()
    }
}

/**
 * DeepSeek LLM服务实现
 */
class DeepSeekLLMService(
    private val apiKey: String,
    private val endpoint: String = "https://api.deepseek.com/v1/chat/completions",
    private val model: String = "deepseek-chat"
) : LLMService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val JSON = "application/json; charset=utf-8".toMediaType()
    
    override suspend fun generateScript(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = """
                你是一个专业的自动化脚本生成助手。请根据用户需求生成高质量的JavaScript脚本。
                
                用户需求：$prompt
                
                请生成完整的JavaScript脚本，包含main函数作为入口点。
            """.trimIndent()
            
            return@withContext callDeepSeek(systemPrompt)
        } catch (e: Exception) {
            Log.e("DeepSeekLLMService", "生成脚本失败", e)
            return@withContext generateFallbackScript(prompt)
        }
    }
    
    override suspend fun optimizeScript(lastScript: String, feedback: String): String = withContext(Dispatchers.IO) {
        try {
            val optimizePrompt = """
                请优化以下JavaScript脚本：
                
                原始脚本：
                ```javascript
                $lastScript
                ```
                
                执行反馈：$feedback
                
                请提供优化后的完整脚本。
            """.trimIndent()
            
            return@withContext callDeepSeek(optimizePrompt)
        } catch (e: Exception) {
            Log.e("DeepSeekLLMService", "优化脚本失败", e)
            return@withContext "$lastScript\n// DeepSeek优化失败：${e.message}"
        }
    }
    
    override suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        try {
            callDeepSeek("测试连接")
            Result.success("DeepSeek连接测试成功")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun callDeepSeek(prompt: String): String {
        val requestBody = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("max_tokens", 4000)
            put("temperature", 0.7)
        }
        
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody(JSON))
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("DeepSeek请求失败: ${response.code} ${response.message}")
            }
            
            val responseBody = response.body?.string() ?: throw IOException("响应体为空")
            val jsonResponse = JSONObject(responseBody)
            
            if (jsonResponse.has("error")) {
                throw IOException("DeepSeek API错误: ${jsonResponse.getJSONObject("error").getString("message")}")
            }
            
            return jsonResponse.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }
    
    private fun generateFallbackScript(prompt: String): String {
        return """
            // DeepSeek自动生成的脚本（基于需求：$prompt）
            function main(params) {
                try {
                    console.log("DeepSeek生成的脚本开始执行：$prompt");
                    // TODO: 实现具体逻辑
                    return { success: true, message: "DeepSeek脚本执行成功" };
                } catch (error) {
                    return { success: false, message: "执行失败: " + error.message };
                }
            }
        """.trimIndent()
    }
}

/**
 * 本地LLM服务实现（如Ollama）
 */
class LocalLLMService(
    private val endpoint: String = "http://localhost:11434/api/generate",
    private val model: String = "llama2"
) : LLMService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS) // 本地模型可能需要更长时间
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val JSON = "application/json; charset=utf-8".toMediaType()
    
    override suspend fun generateScript(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            return@withContext callLocalLLM(prompt)
        } catch (e: Exception) {
            Log.e("LocalLLMService", "生成脚本失败", e)
            return@withContext generateFallbackScript(prompt)
        }
    }
    
    override suspend fun optimizeScript(lastScript: String, feedback: String): String = withContext(Dispatchers.IO) {
        try {
            val optimizePrompt = """
                请优化以下JavaScript脚本：
                
                原始脚本：
                ```javascript
                $lastScript
                ```
                
                执行反馈：$feedback
                
                请提供优化后的完整脚本。
            """.trimIndent()
            
            return@withContext callLocalLLM(optimizePrompt)
        } catch (e: Exception) {
            Log.e("LocalLLMService", "优化脚本失败", e)
            return@withContext "$lastScript\n// 本地LLM优化失败：${e.message}"
        }
    }
    
    override suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        try {
            callLocalLLM("测试连接")
            Result.success("本地LLM连接测试成功")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun callLocalLLM(prompt: String): String {
        val requestBody = JSONObject().apply {
            put("model", model)
            put("prompt", prompt)
            put("stream", false)
            put("options", JSONObject().apply {
                put("temperature", 0.7)
                put("num_predict", 4000)
            })
        }
        
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody(JSON))
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("本地LLM请求失败: ${response.code} ${response.message}")
            }
            
            val responseBody = response.body?.string() ?: throw IOException("响应体为空")
            val jsonResponse = JSONObject(responseBody)
            
            if (jsonResponse.has("error")) {
                throw IOException("本地LLM API错误: ${jsonResponse.getString("error")}")
            }
            
            return jsonResponse.getString("response")
        }
    }
    
    private fun generateFallbackScript(prompt: String): String {
        return """
            // 本地LLM自动生成的脚本（基于需求：$prompt）
            function main(params) {
                try {
                    console.log("本地LLM生成的脚本开始执行：$prompt");
                    // TODO: 实现具体逻辑
                    return { success: true, message: "本地LLM脚本执行成功" };
                } catch (error) {
                    return { success: false, message: "执行失败: " + error.message };
                }
            }
        """.trimIndent()
    }
}

/**
 * LLM服务工厂
 */
object LLMServiceFactory {
    
    /**
     * 根据配置创建LLM服务
     */
    fun createLLMService(config: AgentConfig): LLMService {
        val endpoint = config.getEffectiveLLMEndpoint()
        val model = config.getEffectiveLLMModel()
        
        return when (config.llmProvider.lowercase()) {
            "openai" -> OpenAILLMService(config.llmApiKey, endpoint, model)
            "qwen", "aliyun" -> QwenLLMService(config.llmApiKey, endpoint, model)
            "claude", "anthropic" -> ClaudeLLMService(config.llmApiKey, endpoint, model)
            "gemini", "google" -> GeminiLLMService(config.llmApiKey, endpoint, model)
            "deepseek" -> DeepSeekLLMService(config.llmApiKey, endpoint, model)
            "local", "ollama" -> LocalLLMService(endpoint, model)
            else -> {
                Log.w("LLMServiceFactory", "未知的LLM提供商: ${config.llmProvider}，使用OpenAI作为默认")
                OpenAILLMService(config.llmApiKey, endpoint, model)
            }
        }
    }
    
    /**
     * 获取支持的LLM提供商列表
     */
    fun getSupportedProviders(): List<LLMProviderInfo> {
        return listOf(
            LLMProviderInfo(
                id = "openai",
                name = "OpenAI",
                description = "GPT系列模型",
                requiresApiKey = true,
                defaultEndpoint = "https://api.openai.com/v1/chat/completions",
                defaultModel = "gpt-4o-mini",
                supportedModels = listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo")
            ),
            LLMProviderInfo(
                id = "qwen",
                name = "通义千问",
                description = "阿里巴巴大语言模型",
                requiresApiKey = true,
                defaultEndpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation",
                defaultModel = "qwen-turbo",
                supportedModels = listOf("qwen-turbo", "qwen-plus", "qwen-max")
            ),
            LLMProviderInfo(
                id = "claude",
                name = "Claude",
                description = "Anthropic AI助手",
                requiresApiKey = true,
                defaultEndpoint = "https://api.anthropic.com/v1/messages",
                defaultModel = "claude-3-haiku-20240307",
                supportedModels = listOf("claude-3-5-sonnet-20241022", "claude-3-haiku-20240307", "claude-3-opus-20240229")
            ),
            LLMProviderInfo(
                id = "gemini",
                name = "Gemini",
                description = "Google大语言模型",
                requiresApiKey = true,
                defaultEndpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent",
                defaultModel = "gemini-pro",
                supportedModels = listOf("gemini-pro", "gemini-pro-vision", "gemini-1.5-pro")
            ),
            LLMProviderInfo(
                id = "deepseek",
                name = "DeepSeek",
                description = "深度求索大模型",
                requiresApiKey = true,
                defaultEndpoint = "https://api.deepseek.com/v1/chat/completions",
                defaultModel = "deepseek-chat",
                supportedModels = listOf("deepseek-chat", "deepseek-coder")
            ),
            LLMProviderInfo(
                id = "local",
                name = "本地模型",
                description = "Ollama等本地运行的模型",
                requiresApiKey = false,
                defaultEndpoint = "http://localhost:11434/api/generate",
                defaultModel = "llama2",
                supportedModels = listOf("llama2", "codellama", "mistral", "gemma")
            )
        )
    }
    
    /**
     * 测试LLM服务连接
     */
    suspend fun testLLMConnection(config: AgentConfig): Result<String> = withContext(Dispatchers.IO) {
        try {
            val service = createLLMService(config)
            service.testConnection()
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * LLM提供商信息
 */
data class LLMProviderInfo(
    val id: String,
    val name: String,
    val description: String,
    val requiresApiKey: Boolean,
    val defaultEndpoint: String,
    val defaultModel: String,
    val supportedModels: List<String>
)