package com.ai.assistance.operit.core.tools.automatic

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Represents a recorded automation script that can be replayed.
 * Scripts are sequences of UI operations with parameter support.
 *
 * @property id Unique identifier for the script
 * @property name Human-readable name
 * @property description Description of what the script does
 * @property packageName Target application package name
 * @property steps List of UI operations to execute
 * @property requiredParameters Parameters needed for script execution
 * @property createdAt Creation timestamp
 * @property tags Categorization tags
 */
@Serializable
data class AutomationScript(
    val id: String,
    val name: String,
    val description: String,
    val packageName: String,
    val steps: List<ScriptStep>,
    val requiredParameters: List<ScriptParameter> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val tags: List<String> = emptyList()
) {
    /**
     * Check if all required parameters are provided
     */
    fun areParametersMet(providedParams: Map<String, Any>): Boolean {
        return requiredParameters
            .filter { it.isRequired }
            .all { providedParams.containsKey(it.key) }
    }

    /**
     * Get missing required parameters
     */
    fun getMissingParameters(providedParams: Map<String, Any>): List<ScriptParameter> {
        return requiredParameters
            .filter { it.isRequired && !providedParams.containsKey(it.key) }
    }
}

/**
 * Represents a single step in an automation script.
 * Wraps UIOperation with additional metadata for execution tracking.
 *
 * @property operation The UI operation to execute
 * @property stepNumber Sequential number (1-based)
 * @property description Human-readable description of this step
 * @property continueOnError Whether to continue script execution if this step fails
 * @property retryCount Number of times to retry on failure
 */
@Serializable
data class ScriptStep(
    @Transient val operation: UIOperation = UIOperation.NoOp,
    val stepNumber: Int,
    val description: String,
    val continueOnError: Boolean = false,
    val retryCount: Int = 0
) {
    // For serialization: store operation as JSON string
    // Note: In a real implementation, you'd need custom serializers for UIOperation
    // This is a simplified version
}

/**
 * Defines a parameter required by a script
 *
 * @property key Parameter identifier (used in templates)
 * @property description Human-readable description
 * @property type Parameter type
 * @property isRequired Whether this parameter must be provided
 * @property defaultValue Optional default value
 */
@Serializable
data class ScriptParameter(
    val key: String,
    val description: String,
    val type: String = "String",
    val isRequired: Boolean = true,
    val defaultValue: String? = null
)

/**
 * Result of script execution
 *
 * @property scriptId ID of the executed script
 * @property success Whether execution completed successfully
 * @property completedSteps Number of steps completed
 * @property totalSteps Total number of steps in script
 * @property logs Execution logs for each step
 * @property error Error message if execution failed
 * @property executionTimeMs Total execution time in milliseconds
 */
data class ScriptExecutionResult(
    val scriptId: String,
    val success: Boolean,
    val completedSteps: Int,
    val totalSteps: Int,
    val logs: List<ScriptExecutionLog>,
    val error: String? = null,
    val executionTimeMs: Long
)

/**
 * Log entry for a single step execution
 *
 * @property stepNumber Step number (1-based)
 * @property stepDescription Description of the step
 * @property status Execution status
 * @property message Log message
 * @property timestamp When this step was executed
 * @property error Error details if step failed
 */
data class ScriptExecutionLog(
    val stepNumber: Int,
    val stepDescription: String,
    val status: StepStatus,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val error: String? = null
)

/**
 * Status of a script step execution
 */
enum class StepStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    SKIPPED
}
