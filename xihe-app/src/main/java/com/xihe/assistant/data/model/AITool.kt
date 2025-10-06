package com.xihe.assistant.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.util.*

/**
 * AI工具数据模型
 */
@Serializable
@Immutable
data class AITool(
    val name: String,
    val parameters: List<ToolParameter> = emptyList(),
    val category: ToolCategory? = null,
    val description: String? = null,
    val isEnabled: Boolean = true,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class ToolParameter(
    val name: String,
    val value: String,
    val type: ParameterType = ParameterType.STRING,
    val required: Boolean = false,
    val description: String? = null
)

@Serializable
enum class ParameterType {
    STRING,
    NUMBER,
    BOOLEAN,
    ARRAY,
    OBJECT
}

@Serializable
enum class ToolCategory {
    FILE_SYSTEM,
    NETWORK,
    SYSTEM,
    UI_AUTOMATION,
    MEDIA,
    AI,
    AUTOMATION,
    VOICE,
    CAMERA,
    LOCATION,
    NOTIFICATION
}

@Serializable
data class ToolInvocation(
    val tool: AITool,
    val rawContent: String,
    val responseLocation: IntRange
)

@Serializable
sealed class ToolResult {
    abstract val toolName: String
    abstract val success: Boolean
    abstract val result: ToolResultData
    abstract val error: String?
}

@Serializable
data class SuccessToolResult(
    override val toolName: String,
    override val result: ToolResultData,
    override val success: Boolean = true,
    override val error: String? = null
) : ToolResult()

@Serializable
data class ErrorToolResult(
    override val toolName: String,
    override val error: String,
    override val success: Boolean = false,
    override val result: ToolResultData = StringResultData("")
) : ToolResult()

@Serializable
sealed class ToolResultData

@Serializable
data class StringResultData(val data: String) : ToolResultData()

@Serializable
data class JsonResultData(val data: Map<String, String>) : ToolResultData()

@Serializable
data class FileResultData(val path: String, val size: Long) : ToolResultData()

@Serializable
data class ImageResultData(val path: String, val width: Int, val height: Int) : ToolResultData()

@Serializable
data class ToolValidationResult(
    val valid: Boolean,
    val errorMessage: String? = null
)