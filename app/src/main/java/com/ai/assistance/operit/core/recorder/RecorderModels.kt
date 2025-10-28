package com.ai.assistance.operit.core.recorder

import com.ai.assistance.operit.core.tools.system.action.ActionListener
import kotlinx.serialization.Serializable

/**
 * Represents a recorded script containing a sequence of steps
 */
@Serializable
data class Script(
    val id: String,
    val name: String,
    val description: String = "",
    val steps: List<ScriptStep>,
    val createdAt: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Represents a single step in a recorded script
 */
@Serializable
sealed class ScriptStep {
    abstract val timestamp: Long
    abstract val description: String
    abstract val annotation: String?

    /**
     * A UI gesture action (click, swipe, input, etc.)
     */
    @Serializable
    data class UIGestureStep(
        override val timestamp: Long,
        override val description: String,
        override val annotation: String? = null,
        val actionType: ActionListener.ActionType,
        val coordinates: Coordinates? = null,
        val elementInfo: ElementInfo? = null,
        val inputText: String? = null,
        val additionalData: Map<String, String> = emptyMap()
    ) : ScriptStep()

    /**
     * A tool invocation action
     */
    @Serializable
    data class ToolInvocationStep(
        override val timestamp: Long,
        override val description: String,
        override val annotation: String? = null,
        val toolName: String,
        val parameters: Map<String, String> = emptyMap(),
        val category: String? = null
    ) : ScriptStep()

    /**
     * A timing/delay step
     */
    @Serializable
    data class DelayStep(
        override val timestamp: Long,
        override val description: String,
        override val annotation: String? = null,
        val delayMs: Long
    ) : ScriptStep()

    /**
     * An annotation/comment step
     */
    @Serializable
    data class AnnotationStep(
        override val timestamp: Long,
        override val description: String,
        override val annotation: String? = null
    ) : ScriptStep()
}

/**
 * Coordinates for UI gestures
 */
@Serializable
data class Coordinates(
    val x: Int,
    val y: Int
)

/**
 * Element information for UI gestures
 */
@Serializable
data class ElementInfo(
    val resourceId: String? = null,
    val className: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val bounds: String? = null,
    val packageName: String? = null
)

/**
 * Privacy filter configuration
 */
@Serializable
data class PrivacyFilterConfig(
    val enabled: Boolean = true,
    val filterPasswords: Boolean = true,
    val filterSensitiveInputs: Boolean = true,
    val sensitiveResourceIds: Set<String> = emptySet(),
    val sensitivePackages: Set<String> = emptySet(),
    val customFilters: List<String> = emptyList() // Regex patterns
)

/**
 * Recording session information
 */
data class RecordingSession(
    val id: String,
    val startTime: Long,
    var endTime: Long? = null,
    val steps: MutableList<ScriptStep> = mutableListOf(),
    val privacyFilterConfig: PrivacyFilterConfig = PrivacyFilterConfig()
)
