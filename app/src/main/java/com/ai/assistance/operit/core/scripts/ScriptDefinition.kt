package com.ai.assistance.operit.core.scripts

import kotlinx.serialization.Serializable

/**
 * Root structure for a complete script definition.
 * This represents the human-readable format that can be serialized to/from YAML/JSON5.
 */
@Serializable
data class ScriptDefinition(
    val metadata: ScriptMetadata,
    val steps: List<ScriptStep>
)

/**
 * Metadata for a script (used in serialization format)
 */
@Serializable
data class ScriptMetadata(
    val name: String,
    val description: String = "",
    val author: String = "",
    val version: String = "1.0.0",
    val tags: List<String> = emptyList(),
    val parameters: List<ScriptParameter> = emptyList()
)

/**
 * Parameter definition for script inputs
 */
@Serializable
data class ScriptParameter(
    val name: String,
    val type: ParameterType,
    val description: String = "",
    val defaultValue: String? = null,
    val required: Boolean = true
)

/**
 * Parameter types supported by scripts
 */
@Serializable
enum class ParameterType {
    STRING,
    NUMBER,
    BOOLEAN,
    FILE_PATH,
    URL,
    JSON
}

/**
 * A single step in a script execution flow
 */
@Serializable
data class ScriptStep(
    val id: String,
    val name: String,
    val description: String = "",
    val action: ScriptAction,
    val condition: StepCondition? = null,
    val onError: ErrorHandling = ErrorHandling.STOP,
    val timeout: Long? = null
)

/**
 * Action to be performed in a script step
 */
@Serializable
sealed class ScriptAction {
    @Serializable
    data class ExecuteCommand(
        val command: String,
        val args: List<String> = emptyList(),
        val workingDir: String? = null,
        val captureOutput: Boolean = true
    ) : ScriptAction()
    
    @Serializable
    data class SendHttpRequest(
        val url: String,
        val method: HttpMethod = HttpMethod.GET,
        val headers: Map<String, String> = emptyMap(),
        val body: String? = null
    ) : ScriptAction()
    
    @Serializable
    data class FileOperation(
        val operation: FileOperationType,
        val sourcePath: String,
        val targetPath: String? = null
    ) : ScriptAction()
    
    @Serializable
    data class WaitDelay(
        val durationMs: Long
    ) : ScriptAction()
    
    @Serializable
    data class UiAction(
        val actionType: UiActionType,
        val target: String,
        val value: String? = null
    ) : ScriptAction()
    
    @Serializable
    data class InvokeTool(
        val toolName: String,
        val parameters: Map<String, String> = emptyMap()
    ) : ScriptAction()
    
    @Serializable
    data class LogMessage(
        val message: String,
        val level: LogLevel = LogLevel.INFO
    ) : ScriptAction()
    
    @Serializable
    data class SetVariable(
        val name: String,
        val value: String,
        val scope: VariableScope = VariableScope.SCRIPT
    ) : ScriptAction()
}

/**
 * HTTP methods for network requests
 */
@Serializable
enum class HttpMethod {
    GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS
}

/**
 * File operation types
 */
@Serializable
enum class FileOperationType {
    COPY, MOVE, DELETE, CREATE, READ, WRITE, APPEND
}

/**
 * UI automation action types
 */
@Serializable
enum class UiActionType {
    CLICK, TYPE, SWIPE, SCROLL, WAIT_FOR_ELEMENT, ASSERT_EXISTS
}

/**
 * Log levels for logging actions
 */
@Serializable
enum class LogLevel {
    DEBUG, INFO, WARNING, ERROR
}

/**
 * Variable scope for script variables
 */
@Serializable
enum class VariableScope {
    STEP, SCRIPT, GLOBAL
}

/**
 * Condition for conditional step execution
 */
@Serializable
data class StepCondition(
    val expression: String,
    val type: ConditionType = ConditionType.EXPRESSION
)

/**
 * Condition evaluation types
 */
@Serializable
enum class ConditionType {
    EXPRESSION,
    VARIABLE_EQUALS,
    VARIABLE_NOT_EQUALS,
    VARIABLE_CONTAINS,
    FILE_EXISTS,
    NETWORK_AVAILABLE
}

/**
 * Error handling strategy for steps
 */
@Serializable
enum class ErrorHandling {
    STOP,
    CONTINUE,
    RETRY,
    SKIP
}
