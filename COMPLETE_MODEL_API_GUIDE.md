# Operit AI - 完整模型API调用指南

## 项目概述

Operit AI是一个支持多种大语言模型提供商的Android智能助手应用。项目架构采用模块化设计，支持OpenAI、Claude、Gemini、Qwen、DeepSeek等多种LLM提供商。

## 1. 核心架构组件

### 1.1 提供商类型枚举

```kotlin
// 文件: app/src/main/java/com/ai/assistance/operit/data/model/ModelConfigData.kt
enum class ApiProviderType {
    OPENAI,         // OpenAI (GPT系列)
    ANTHROPIC,      // Anthropic (Claude系列)
    GOOGLE,         // Google (Gemini系列)
    BAIDU,          // 百度 (文心一言系列)
    ALIYUN,         // 阿里云 (通义千问系列)
    XUNFEI,         // 讯飞 (星火认知系列)
    ZHIPU,          // 智谱AI (ChatGLM系列)
    BAICHUAN,       // 百川大模型
    MOONSHOT,       // 月之暗面大模型
    DEEPSEEK,       // Deepseek大模型
    SILICONFLOW,    // 硅基流动
    OPENROUTER,     // OpenRouter (多模型聚合)
    INFINIAI,       // 无问芯穹
    LMSTUDIO,       // LM Studio本地模型服务
    OTHER           // 其他提供商
}
```

### 1.2 AI服务工厂

```kotlin
// 文件: app/src/main/java/com/ai/assistance/operit/api/chat/AIServiceFactory.kt
object AIServiceFactory {
    fun createService(
        apiProviderType: ApiProviderType,
        apiEndpoint: String,
        apiKey: String,
        modelName: String
    ): AIService {
        return when (apiProviderType) {
            ApiProviderType.OPENAI -> OpenAIProvider(apiEndpoint, apiKey, modelName)
            ApiProviderType.ANTHROPIC -> ClaudeProvider(apiEndpoint, apiKey, modelName)
            ApiProviderType.GOOGLE -> GeminiProvider(apiEndpoint, apiKey, modelName)
            ApiProviderType.ALIYUN -> QwenAIProvider(apiEndpoint, apiKey, modelName)
            ApiProviderType.LMSTUDIO -> OpenAIProvider(apiEndpoint, apiKey, modelName)
            // 其他提供商使用OpenAI兼容格式
            ApiProviderType.DEEPSEEK,
            ApiProviderType.MOONSHOT,
            ApiProviderType.BAICHUAN,
            ApiProviderType.SILICONFLOW,
            ApiProviderType.OPENROUTER -> OpenAIProvider(apiEndpoint, apiKey, modelName)
            else -> OpenAIProvider(apiEndpoint, apiKey, modelName)
        }
    }
}
```

## 2. 所有提供商的URL和端点配置

### 2.1 模型列表URL生成

```kotlin
// 文件: app/src/main/java/com/ai/assistance/operit/api/chat/ModelListFetcher.kt
fun getModelsListUrl(apiEndpoint: String, apiProviderType: ApiProviderType): String {
    return when (apiProviderType) {
        // OpenAI格式
        ApiProviderType.OPENAI -> "${extractBaseUrl(apiEndpoint)}/v1/models"
        
        // Anthropic格式
        ApiProviderType.ANTHROPIC -> "${extractBaseUrl(apiEndpoint)}/v1/models"
        
        // Google Gemini格式
        ApiProviderType.GOOGLE -> {
            if (apiEndpoint.contains("generativelanguage.googleapis.com")) {
                if (apiEndpoint.endsWith("/models")) {
                    apiEndpoint
                } else {
                    val version = if (apiEndpoint.contains("/v1/")) "v1" else "v1beta"
                    "https://generativelanguage.googleapis.com/$version/models"
                }
            } else if (apiEndpoint.contains("aiplatform.googleapis.com")) {
                // Vertex AI格式
                val projectMatch = Regex("projects/([^/]+)").find(apiEndpoint)
                val locationMatch = Regex("locations/([^/]+)").find(apiEndpoint)
                if (projectMatch != null && locationMatch != null) {
                    val project = projectMatch.groupValues[1]
                    val location = locationMatch.groupValues[1]
                    "https://$location-aiplatform.googleapis.com/v1/projects/$project/locations/$location/publishers/google/models"
                } else {
                    "https://generativelanguage.googleapis.com/v1beta/models"
                }
            } else {
                "https://generativelanguage.googleapis.com/v1beta/models"
            }
        }
        
        // 其他提供商的模型列表端点
        ApiProviderType.DEEPSEEK -> "${extractBaseUrl(apiEndpoint)}/v1/models"
        ApiProviderType.OPENROUTER -> "${extractBaseUrl(apiEndpoint)}/api/v1/models"
        ApiProviderType.MOONSHOT -> "${extractBaseUrl(apiEndpoint)}/v1/models"
        ApiProviderType.SILICONFLOW -> "${extractBaseUrl(apiEndpoint)}/v1/models"
        ApiProviderType.BAICHUAN -> "${extractBaseUrl(apiEndpoint)}/v1/models"
        ApiProviderType.INFINIAI -> "${extractBaseUrl(apiEndpoint)}/maas/v1/models"
        ApiProviderType.LMSTUDIO -> "${extractBaseUrl(apiEndpoint)}/v1/models"
        
        // 默认使用OpenAI兼容格式
        else -> "${extractBaseUrl(apiEndpoint)}/v1/models"
    }
}
```

### 2.2 所有提供商的具体URL配置

```kotlin
// 完整的提供商配置
object ProviderConfigurations {
    
    // OpenAI配置
    object OpenAI {
        const val CHAT_ENDPOINT = "https://api.openai.com/v1/chat/completions"
        const val MODELS_ENDPOINT = "https://api.openai.com/v1/models"
        const val AUTH_HEADER = "Authorization: Bearer YOUR_API_KEY"
        val SUPPORTED_MODELS = listOf(
            "gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo"
        )
    }
    
    // Claude (Anthropic)配置
    object Claude {
        const val CHAT_ENDPOINT = "https://api.anthropic.com/v1/messages"
        const val MODELS_ENDPOINT = "https://api.anthropic.com/v1/models"
        const val AUTH_HEADER = "x-api-key: YOUR_API_KEY"
        const val VERSION_HEADER = "anthropic-version: 2023-06-01"
        val SUPPORTED_MODELS = listOf(
            "claude-3-5-sonnet-20241022", "claude-3-haiku-20240307", "claude-3-opus-20240229"
        )
    }
    
    // Google Gemini配置
    object Gemini {
        const val CHAT_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent"
        const val MODELS_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"
        const val AUTH_PARAM = "?key=YOUR_API_KEY"
        val SUPPORTED_MODELS = listOf(
            "gemini-pro", "gemini-pro-vision", "gemini-1.5-pro"
        )
    }
    
    // 阿里云通义千问配置
    object Qwen {
        const val CHAT_ENDPOINT = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"
        const val MODELS_ENDPOINT = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/models"
        const val AUTH_HEADER = "Authorization: Bearer YOUR_API_KEY"
        val SUPPORTED_MODELS = listOf(
            "qwen-turbo", "qwen-plus", "qwen-max"
        )
    }
    
    // DeepSeek配置
    object DeepSeek {
        const val CHAT_ENDPOINT = "https://api.deepseek.com/v1/chat/completions"
        const val MODELS_ENDPOINT = "https://api.deepseek.com/v1/models"
        const val AUTH_HEADER = "Authorization: Bearer YOUR_API_KEY"
        val SUPPORTED_MODELS = listOf(
            "deepseek-chat", "deepseek-coder"
        )
    }
    
    // 月之暗面配置
    object Moonshot {
        const val CHAT_ENDPOINT = "https://api.moonshot.cn/v1/chat/completions"
        const val MODELS_ENDPOINT = "https://api.moonshot.cn/v1/models"
        const val AUTH_HEADER = "Authorization: Bearer YOUR_API_KEY"
        val SUPPORTED_MODELS = listOf(
            "moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k"
        )
    }
    
    // 其他提供商...
    object SiliconFlow {
        const val CHAT_ENDPOINT = "https://api.siliconflow.cn/v1/chat/completions"
        const val MODELS_ENDPOINT = "https://api.siliconflow.cn/v1/models"
        const val AUTH_HEADER = "Authorization: Bearer YOUR_API_KEY"
    }
    
    object OpenRouter {
        const val CHAT_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
        const val MODELS_ENDPOINT = "https://openrouter.ai/api/v1/models"
        const val AUTH_HEADER = "Authorization: Bearer YOUR_API_KEY"
        const val REFERER_HEADER = "HTTP-Referer: ai.assistance.operit"
        const val TITLE_HEADER = "X-Title: Assistance App"
    }
}
```

## 3. 模型获取代码实现

### 3.1 通用模型获取接口

```kotlin
// 文件: app/src/main/java/com/ai/assistance/operit/api/chat/ModelListFetcher.kt
suspend fun getModelsList(
    apiKey: String,
    apiEndpoint: String,
    apiProviderType: ApiProviderType = ApiProviderType.OPENAI
): Result<List<ModelOption>> {
    return withContext(Dispatchers.IO) {
        val maxRetries = 2
        var retryCount = 0
        var lastException: Exception? = null

        while (retryCount <= maxRetries) {
            try {
                // 生成模型列表URL
                val modelsUrl = getModelsListUrl(apiEndpoint, apiProviderType)
                
                val requestBuilder = Request.Builder()
                    .url(modelsUrl)
                    .addHeader("Content-Type", "application/json")

                // 根据不同供应商添加认证头
                when (apiProviderType) {
                    ApiProviderType.GOOGLE -> {
                        // Google API 使用查询参数认证
                        val urlWithKey = if (modelsUrl.contains("?")) {
                            "$modelsUrl&key=$apiKey"
                        } else {
                            "$modelsUrl?key=$apiKey"
                        }
                        requestBuilder.url(urlWithKey)
                    }
                    ApiProviderType.OPENROUTER -> {
                        // OpenRouter需要特殊头部
                        requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                        requestBuilder.addHeader("HTTP-Referer", "ai.assistance.operit")
                        requestBuilder.addHeader("X-Title", "Assistance App")
                    }
                    else -> {
                        // 大多数API使用Bearer认证
                        requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                    }
                }

                val request = requestBuilder.get().build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "无错误详情"
                    return@withContext Result.failure(
                        IOException("API请求失败: ${response.code}, 错误: $errorBody")
                    )
                }

                val responseBody = response.body?.string()
                    ?: return@withContext Result.failure(IOException("响应体为空"))

                // 根据提供商类型解析响应
                val modelOptions = when (apiProviderType) {
                    ApiProviderType.OPENAI,
                    ApiProviderType.DEEPSEEK,
                    ApiProviderType.MOONSHOT,
                    ApiProviderType.SILICONFLOW,
                    ApiProviderType.BAICHUAN,
                    ApiProviderType.OPENROUTER,
                    ApiProviderType.INFINIAI,
                    ApiProviderType.LMSTUDIO -> parseOpenAIModelResponse(responseBody)
                    
                    ApiProviderType.ANTHROPIC -> parseAnthropicModelResponse(responseBody)
                    ApiProviderType.GOOGLE -> parseGoogleModelResponse(responseBody)
                    
                    else -> parseOpenAIModelResponse(responseBody)
                }

                return@withContext Result.success(modelOptions)
                
            } catch (e: Exception) {
                lastException = e
                retryCount++
                if (retryCount <= maxRetries) {
                    delay(1000L * retryCount) // 指数退避
                }
            }
        }
        
        Result.failure(lastException ?: IOException("获取模型列表失败"))
    }
}
```

### 3.2 各提供商的响应解析

```kotlin
// OpenAI格式响应解析
private fun parseOpenAIModelResponse(jsonResponse: String): List<ModelOption> {
    val modelList = mutableListOf<ModelOption>()
    try {
        val jsonObject = JSONObject(jsonResponse)
        val dataArray = jsonObject.getJSONArray("data")
        
        for (i in 0 until dataArray.length()) {
            val modelObj = dataArray.getJSONObject(i)
            val id = modelObj.getString("id")
            modelList.add(ModelOption(id = id, name = id))
        }
    } catch (e: JSONException) {
        throw e
    }
    return modelList.sortedBy { it.id }
}

// Anthropic格式响应解析
private fun parseAnthropicModelResponse(jsonResponse: String): List<ModelOption> {
    val modelList = mutableListOf<ModelOption>()
    try {
        val jsonObject = JSONObject(jsonResponse)
        val modelsArray = jsonObject.getJSONArray("models")
        
        for (i in 0 until modelsArray.length()) {
            val modelObj = modelsArray.getJSONObject(i)
            val id = modelObj.getString("name")
            val displayName = modelObj.optString("display_name", id)
            modelList.add(ModelOption(id = id, name = displayName))
        }
    } catch (e: JSONException) {
        throw e
    }
    return modelList.sortedBy { it.id }
}

// Google格式响应解析
private fun parseGoogleModelResponse(jsonResponse: String): List<ModelOption> {
    val modelList = mutableListOf<ModelOption>()
    try {
        val jsonObject = JSONObject(jsonResponse)
        
        if (jsonObject.has("models")) {
            val modelsArray = jsonObject.getJSONArray("models")
            
            for (i in 0 until modelsArray.length()) {
                val modelObj = modelsArray.getJSONObject(i)
                val id = modelObj.getString("name").split("/").last()
                val displayName = modelObj.optString("displayName", id)
                
                // 检查是否支持generateContent
                val supportedMethods = try {
                    if (modelObj.has("supportedGenerationMethods")) {
                        val methods = modelObj.getJSONArray("supportedGenerationMethods")
                        val methodsList = mutableListOf<String>()
                        for (j in 0 until methods.length()) {
                            methodsList.add(methods.getString(j))
                        }
                        methodsList
                    } else {
                        listOf("generateContent")
                    }
                } catch (e: Exception) {
                    listOf("generateContent")
                }
                
                if (supportedMethods.contains("generateContent")) {
                    modelList.add(ModelOption(id = id, name = displayName))
                }
            }
        }
    } catch (e: JSONException) {
        throw e
    }
    return modelList.sortedBy { it.id }
}
```

## 4. 具体提供商实现代码

### 4.1 OpenAI Provider实现

```kotlin
// 文件: app/src/main/java/com/ai/assistance/operit/api/chat/OpenAIProvider.kt
class OpenAIProvider(
    private val apiEndpoint: String,
    private val apiKey: String,
    private val modelName: String
) : AIService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(1000, TimeUnit.SECONDS)
        .writeTimeout(1000, TimeUnit.SECONDS)
        .build()
    
    private val JSON = "application/json; charset=utf-8".toMediaType()
    
    override suspend fun sendMessage(
        message: String,
        chatHistory: List<Pair<String, String>>,
        modelParameters: List<ModelParameter<*>>,
        enableThinking: Boolean
    ): Stream<String> {
        return stream { emitter ->
            try {
                val requestBody = createRequestBody(message, chatHistory, modelParameters, enableThinking)
                val request = Request.Builder()
                    .url(apiEndpoint)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()
                
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw IOException("请求失败: ${response.code} ${response.message}")
                }
                
                response.body?.charStream()?.use { reader ->
                    reader.forEachLine { line ->
                        if (line.startsWith("data: ")) {
                            val jsonData = line.substring(6)
                            if (jsonData != "[DONE]") {
                                try {
                                    val jsonObject = JSONObject(jsonData)
                                    val choices = jsonObject.getJSONArray("choices")
                                    if (choices.length() > 0) {
                                        val choice = choices.getJSONObject(0)
                                        val delta = choice.getJSONObject("delta")
                                        if (delta.has("content")) {
                                            val content = delta.getString("content")
                                            emitter.emit(content)
                                        }
                                    }
                                } catch (e: Exception) {
                                    // 忽略解析错误，继续处理下一行
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }
    
    private fun createRequestBody(
        message: String,
        chatHistory: List<Pair<String, String>>,
        modelParameters: List<ModelParameter<*>>,
        enableThinking: Boolean
    ): RequestBody {
        val jsonObject = JSONObject()
        jsonObject.put("model", modelName)
        jsonObject.put("stream", true)
        
        // 添加模型参数
        modelParameters.forEach { param ->
            if (param.isEnabled) {
                when (param.valueType) {
                    ParameterValueType.INT -> jsonObject.put(param.apiName, param.currentValue as Int)
                    ParameterValueType.FLOAT -> jsonObject.put(param.apiName, param.currentValue as Float)
                    ParameterValueType.STRING -> jsonObject.put(param.apiName, param.currentValue as String)
                    ParameterValueType.BOOLEAN -> jsonObject.put(param.apiName, param.currentValue as Boolean)
                }
            }
        }
        
        // 构建消息数组
        val messagesArray = JSONArray()
        
        // 添加历史消息
        chatHistory.forEach { (role, content) ->
            val messageObj = JSONObject()
            messageObj.put("role", role)
            messageObj.put("content", content)
            messagesArray.put(messageObj)
        }
        
        // 添加当前消息
        val currentMessageObj = JSONObject()
        currentMessageObj.put("role", "user")
        currentMessageObj.put("content", message)
        messagesArray.put(currentMessageObj)
        
        jsonObject.put("messages", messagesArray)
        
        return jsonObject.toString().toRequestBody(JSON)
    }
}
```

### 4.2 Claude Provider实现

```kotlin
// 文件: app/src/main/java/com/ai/assistance/operit/api/chat/ClaudeProvider.kt
class ClaudeProvider(
    private val apiEndpoint: String,
    private val apiKey: String,
    private val modelName: String
) : AIService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(1000, TimeUnit.SECONDS)
        .writeTimeout(1000, TimeUnit.SECONDS)
        .build()
    
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val ANTHROPIC_VERSION = "2023-06-01"
    
    override suspend fun sendMessage(
        message: String,
        chatHistory: List<Pair<String, String>>,
        modelParameters: List<ModelParameter<*>>,
        enableThinking: Boolean
    ): Stream<String> {
        return stream { emitter ->
            try {
                val requestBody = createRequestBody(message, chatHistory, modelParameters, enableThinking)
                val request = Request.Builder()
                    .url(apiEndpoint)
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", ANTHROPIC_VERSION)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()
                
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw IOException("Claude请求失败: ${response.code} ${response.message}")
                }
                
                response.body?.charStream()?.use { reader ->
                    reader.forEachLine { line ->
                        if (line.startsWith("data: ")) {
                            val jsonData = line.substring(6)
                            if (jsonData != "[DONE]") {
                                try {
                                    val jsonObject = JSONObject(jsonData)
                                    if (jsonObject.getString("type") == "content_block_delta") {
                                        val delta = jsonObject.getJSONObject("delta")
                                        if (delta.getString("type") == "text_delta") {
                                            val text = delta.getString("text")
                                            emitter.emit(text)
                                        }
                                    }
                                } catch (e: Exception) {
                                    // 忽略解析错误
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }
    
    private fun createRequestBody(
        message: String,
        chatHistory: List<Pair<String, String>>,
        modelParameters: List<ModelParameter<*>>,
        enableThinking: Boolean
    ): RequestBody {
        val jsonObject = JSONObject()
        jsonObject.put("model", modelName)
        jsonObject.put("stream", true)
        
        // 添加参数
        modelParameters.forEach { param ->
            if (param.isEnabled) {
                when (param.apiName) {
                    "max_tokens" -> jsonObject.put("max_tokens", param.currentValue as Int)
                    "temperature" -> jsonObject.put("temperature", param.currentValue as Float)
                    "top_p" -> jsonObject.put("top_p", param.currentValue as Float)
                }
            }
        }
        
        // 提取系统消息
        val systemMessages = chatHistory.filter { it.first.equals("system", ignoreCase = true) }
        if (systemMessages.isNotEmpty()) {
            val systemPrompt = systemMessages.joinToString("\n\n") { it.second }
            jsonObject.put("system", systemPrompt)
        }
        
        // 构建消息数组（不包括system消息）
        val messagesArray = JSONArray()
        val userAssistantHistory = chatHistory.filter { !it.first.equals("system", ignoreCase = true) }
        
        userAssistantHistory.forEach { (role, content) ->
            val messageObj = JSONObject()
            messageObj.put("role", if (role.equals("assistant", ignoreCase = true)) "assistant" else "user")
            messageObj.put("content", content)
            messagesArray.put(messageObj)
        }
        
        // 添加当前用户消息
        val currentMessageObj = JSONObject()
        currentMessageObj.put("role", "user")
        currentMessageObj.put("content", message)
        messagesArray.put(currentMessageObj)
        
        jsonObject.put("messages", messagesArray)
        
        return jsonObject.toString().toRequestBody(JSON)
    }
}
```

### 4.3 Gemini Provider实现

```kotlin
// 文件: app/src/main/java/com/ai/assistance/operit/api/chat/GeminiProvider.kt
class GeminiProvider(
    private val apiEndpoint: String,
    private val apiKey: String,
    private val modelName: String
) : AIService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(1000, TimeUnit.SECONDS)
        .writeTimeout(1000, TimeUnit.SECONDS)
        .build()
    
    private val JSON = "application/json; charset=utf-8".toMediaType()
    
    override suspend fun sendMessage(
        message: String,
        chatHistory: List<Pair<String, String>>,
        modelParameters: List<ModelParameter<*>>,
        enableThinking: Boolean
    ): Stream<String> {
        return stream { emitter ->
            try {
                val requestBody = createRequestBody(message, chatHistory, modelParameters)
                
                // 构建带API key的URL
                val urlWithKey = if (apiEndpoint.contains("?")) {
                    "$apiEndpoint&key=$apiKey&alt=sse"
                } else {
                    "$apiEndpoint?key=$apiKey&alt=sse"
                }
                
                val request = Request.Builder()
                    .url(urlWithKey)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()
                
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw IOException("Gemini请求失败: ${response.code} ${response.message}")
                }
                
                response.body?.charStream()?.use { reader ->
                    reader.forEachLine { line ->
                        if (line.startsWith("data: ")) {
                            val jsonData = line.substring(6)
                            try {
                                val jsonObject = JSONObject(jsonData)
                                if (jsonObject.has("candidates")) {
                                    val candidates = jsonObject.getJSONArray("candidates")
                                    if (candidates.length() > 0) {
                                        val candidate = candidates.getJSONObject(0)
                                        if (candidate.has("content")) {
                                            val content = candidate.getJSONObject("content")
                                            if (content.has("parts")) {
                                                val parts = content.getJSONArray("parts")
                                                if (parts.length() > 0) {
                                                    val part = parts.getJSONObject(0)
                                                    if (part.has("text")) {
                                                        val text = part.getString("text")
                                                        emitter.emit(text)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                // 忽略解析错误
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }
    
    private fun createRequestBody(
        message: String,
        chatHistory: List<Pair<String, String>>,
        modelParameters: List<ModelParameter<*>>
    ): RequestBody {
        val jsonObject = JSONObject()
        
        // 添加生成配置
        val generationConfig = JSONObject()
        modelParameters.forEach { param ->
            if (param.isEnabled) {
                when (param.apiName) {
                    "maxOutputTokens" -> generationConfig.put("maxOutputTokens", param.currentValue as Int)
                    "temperature" -> generationConfig.put("temperature", param.currentValue as Float)
                    "topP" -> generationConfig.put("topP", param.currentValue as Float)
                    "topK" -> generationConfig.put("topK", param.currentValue as Int)
                }
            }
        }
        if (generationConfig.length() > 0) {
            jsonObject.put("generationConfig", generationConfig)
        }
        
        // 构建内容数组
        val contentsArray = JSONArray()
        
        // 添加历史消息
        chatHistory.forEach { (role, content) ->
            val contentObj = JSONObject()
            contentObj.put("role", if (role.equals("assistant", ignoreCase = true)) "model" else "user")
            
            val partsArray = JSONArray()
            val partObj = JSONObject()
            partObj.put("text", content)
            partsArray.put(partObj)
            contentObj.put("parts", partsArray)
            
            contentsArray.put(contentObj)
        }
        
        // 添加当前消息
        val currentContentObj = JSONObject()
        currentContentObj.put("role", "user")
        
        val currentPartsArray = JSONArray()
        val currentPartObj = JSONObject()
        currentPartObj.put("text", message)
        currentPartsArray.put(currentPartObj)
        currentContentObj.put("parts", currentPartsArray)
        
        contentsArray.put(currentContentObj)
        
        jsonObject.put("contents", contentsArray)
        
        return jsonObject.toString().toRequestBody(JSON)
    }
}
```

### 4.4 通义千问Provider实现

```kotlin
// 文件: app/src/main/java/com/ai/assistance/operit/api/chat/QwenAIProvider.kt
class QwenAIProvider(
    apiEndpoint: String,
    apiKey: String,
    modelName: String
) : OpenAIProvider(apiEndpoint, apiKey, modelName) {

    override fun createRequestBody(
        message: String,
        chatHistory: List<Pair<String, String>>,
        modelParameters: List<ModelParameter<*>>,
        enableThinking: Boolean
    ): RequestBody {
        // 使用父类的实现获取基础请求体
        val baseRequestBodyJson = super.createRequestBodyInternal(message, chatHistory, modelParameters)
        val jsonObject = JSONObject(baseRequestBodyJson)

        // 如果启用了思考模式，添加Qwen特定参数
        if (enableThinking) {
            jsonObject.put("enable_thinking", true)
        }

        return jsonObject.toString().toRequestBody(JSON)
    }
}
```

## 5. Agent系统的LLM服务集成

### 5.1 Agent LLM服务工厂

```kotlin
// 文件: app/src/main/java/com/ai/assistance/operit/core/agent/LLMService.kt
object LLMServiceFactory {
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
            // ... 其他提供商配置
        )
    }
}
```

## 6. 完整的使用示例

### 6.1 基本模型调用

```kotlin
// 创建AI服务实例
val aiService = AIServiceFactory.createService(
    apiProviderType = ApiProviderType.OPENAI,
    apiEndpoint = "https://api.openai.com/v1/chat/completions",
    apiKey = "your-api-key",
    modelName = "gpt-4o-mini"
)

// 发送消息
val stream = aiService.sendMessage(
    message = "Hello, how are you?",
    chatHistory = listOf(
        "system" to "You are a helpful assistant.",
        "user" to "Hi there!"
    ),
    modelParameters = emptyList()
)

// 收集流式响应
stream.collect { content ->
    println(content)
}
```

### 6.2 获取模型列表

```kotlin
// 获取OpenAI模型列表
val result = ModelListFetcher.getModelsList(
    apiKey = "your-api-key",
    apiEndpoint = "https://api.openai.com/v1/chat/completions",
    apiProviderType = ApiProviderType.OPENAI
)

when {
    result.isSuccess -> {
        val models = result.getOrNull()
        models?.forEach { model ->
            println("Model: ${model.id} - ${model.name}")
        }
    }
    result.isFailure -> {
        println("Error: ${result.exceptionOrNull()?.message}")
    }
}
```

### 6.3 Agent系统调用

```kotlin
// 创建Agent配置
val agentConfig = AgentConfig(
    llmProvider = "openai",
    llmApiKey = "your-api-key",
    llmEndpoint = "https://api.openai.com/v1/chat/completions",
    llmModel = "gpt-4o-mini",
    maxIterations = 3,
    enableMemory = true,
    enableSafetyChecks = true
)

// 执行Agent任务
val result = AgentScriptGenerator.agentMain(
    userRequest = "创建一个简单的待办事项应用",
    planSteps = listOf(
        "设计数据结构",
        "实现添加功能",
        "实现删除功能",
        "实现编辑功能"
    ),
    config = agentConfig,
    context = applicationContext
)

// 处理结果
if (result.success) {
    println("脚本生成成功:")
    println("最终脚本: ${result.finalScript}")
    println("执行结果: ${result.lastResult}")
    println("成功率: ${result.successRate}")
    println("执行时间: ${result.executionTime}ms")
} else {
    println("执行失败: ${result.error}")
    result.errors.forEach { error ->
        println("错误: $error")
    }
}
```

## 7. 错误处理和重试机制

### 7.1 网络错误处理

```kotlin
// 在ModelListFetcher中的错误处理
try {
    // API调用
    val response = client.newCall(request).execute()
    
    if (!response.isSuccessful) {
        val errorBody = response.body?.string() ?: "无错误详情"
        return Result.failure(IOException("API请求失败: ${response.code}, 错误: $errorBody"))
    }
    
    // 处理响应
    
} catch (e: SocketTimeoutException) {
    // 网络超时重试
    retryCount++
    if (retryCount <= maxRetries) {
        delay(1000L * retryCount) // 指数退避
    }
} catch (e: UnknownHostException) {
    // DNS解析失败
    return Result.failure(IOException("无法连接到服务器，请检查网络连接和API地址是否正确", e))
} catch (e: IOException) {
    // 其他IO异常
    retryCount++
    if (retryCount <= maxRetries) {
        delay(1000L * retryCount)
    }
}
```

### 7.2 API错误处理

```kotlin
// 解析API错误响应
fun handleAPIError(response: Response): Exception {
    val errorBody = response.body?.string() ?: "未知错误"
    
    return when (response.code) {
        401 -> IOException("API密钥无效或已过期")
        403 -> IOException("访问被拒绝，请检查API权限")
        404 -> IOException("API端点不存在")
        429 -> IOException("请求频率过高，请稍后重试")
        500 -> IOException("服务器内部错误")
        502, 503, 504 -> IOException("服务暂时不可用，请稍后重试")
        else -> IOException("API请求失败: ${response.code}, 错误: $errorBody")
    }
}
```

## 8. 总结

这个完整的模型API调用指南涵盖了Operit AI项目中的所有模型连接实现：

1. **多提供商支持**: 支持OpenAI、Claude、Gemini、Qwen、DeepSeek等主流LLM提供商
2. **统一接口**: 通过AIService接口提供统一的调用方式
3. **工厂模式**: 使用工厂模式动态创建不同提供商的服务实例
4. **错误处理**: 完善的错误处理和重试机制
5. **流式响应**: 支持所有提供商的流式响应处理
6. **Agent集成**: 深度集成到Agent自动化系统中

通过这套架构，开发者可以轻松地添加新的LLM提供商，同时保持代码的一致性和可维护性。