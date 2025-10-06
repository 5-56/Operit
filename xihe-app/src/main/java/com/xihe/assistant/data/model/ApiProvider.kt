package com.xihe.assistant.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * API提供商数据模型
 */
@Serializable
@Immutable
data class ApiProvider(
    val type: ApiProviderType,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val maxTokens: Int = 4096,
    val temperature: Float = 0.7f,
    val topP: Float = 1.0f,
    val frequencyPenalty: Float = 0.0f,
    val presencePenalty: Float = 0.0f,
    val isEnabled: Boolean = true,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
enum class ApiProviderType {
    DEEPSEEK,
    OPENAI,
    CLAUDE,
    GEMINI,
    CUSTOM
}

@Serializable
data class ChatRequest(
    val messages: List<ChatMessage>,
    val model: String,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val topP: Float = 1.0f,
    val frequencyPenalty: Float = 0.0f,
    val presencePenalty: Float = 0.0f,
    val stream: Boolean = true,
    val tools: List<AITool> = emptyList(),
    val toolChoice: String? = null
)

@Serializable
data class ChatResponse(
    val id: String,
    val object: String,
    val created: Long,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage? = null
)

@Serializable
data class Choice(
    val index: Int,
    val message: ChatMessage? = null,
    val delta: ChatMessage? = null,
    val finishReason: String? = null
)

@Serializable
data class Usage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

@Serializable
data class StreamResponse(
    val id: String,
    val object: String,
    val created: Long,
    val model: String,
    val choices: List<StreamChoice>
)

@Serializable
data class StreamChoice(
    val index: Int,
    val delta: ChatMessage,
    val finishReason: String? = null
)