package com.ai.assistance.operit.core.tools.javascript.debug

import kotlinx.serialization.Serializable

/**
 * Represents a breakpoint in the script
 */
@Serializable
data class Breakpoint(
    val id: String,
    val line: Int,
    val enabled: Boolean = true,
    val condition: String? = null
)

/**
 * Execution mode for the debugger
 */
enum class DebugMode {
    RUN,           // Normal execution without debugging
    DEBUG,         // Debug mode with breakpoint support
    STEP_OVER,     // Execute current line and pause at next line
    STEP_INTO,     // Step into function calls
    STEP_OUT       // Continue until current function returns
}

/**
 * Current state of the debugger
 */
enum class DebugState {
    IDLE,          // Not executing
    RUNNING,       // Executing normally
    PAUSED,        // Paused at a breakpoint or step
    COMPLETED,     // Execution completed
    ERROR          // Execution error
}

/**
 * Execution context at a specific point
 */
@Serializable
data class DebugContext(
    val currentLine: Int,
    val variables: Map<String, String>,
    val callStack: List<StackFrame>,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * A frame in the call stack
 */
@Serializable
data class StackFrame(
    val functionName: String,
    val line: Int,
    val source: String? = null
)

/**
 * A log entry from script execution
 */
@Serializable
data class DebugLog(
    val timestamp: Long,
    val level: LogLevel,
    val message: String,
    val context: String? = null
)

/**
 * Log level for debug logs
 */
enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

/**
 * Execution step for automation debugging
 */
@Serializable
data class AutomationStep(
    val stepIndex: Int,
    val operation: String,
    val description: String,
    val timestamp: Long,
    val screenshot: String? = null,  // Base64 encoded image
    val uiState: String? = null,     // JSON representation of UI state
    val success: Boolean = true,
    val error: String? = null
)

/**
 * Debug session information
 */
data class DebugSession(
    val sessionId: String,
    val scriptName: String,
    val startTime: Long,
    val mode: DebugMode,
    var state: DebugState = DebugState.IDLE,
    val breakpoints: MutableList<Breakpoint> = mutableListOf(),
    val logs: MutableList<DebugLog> = mutableListOf(),
    val automationSteps: MutableList<AutomationStep> = mutableListOf(),
    var currentContext: DebugContext? = null
)
