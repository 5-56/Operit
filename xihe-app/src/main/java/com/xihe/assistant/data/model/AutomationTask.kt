package com.xihe.assistant.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.util.*

/**
 * 自动化任务数据模型
 */
@Serializable
@Immutable
data class AutomationTask(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val trigger: AutomationTrigger,
    val actions: List<String>,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastExecuted: Long? = null,
    val executionCount: Int = 0,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
sealed class AutomationTrigger {
    @Serializable
    data class TimeTrigger(
        val hour: Int,
        val minute: Int,
        val daysOfWeek: List<Int> = emptyList(), // 0-6, 0=Sunday
        val isRecurring: Boolean = true
    ) : AutomationTrigger()

    @Serializable
    data class EventTrigger(
        val eventType: EventType,
        val conditions: Map<String, String> = emptyMap()
    ) : AutomationTrigger()

    @Serializable
    data class ManualTrigger(
        val description: String = "手动触发"
    ) : AutomationTrigger()

    @Serializable
    data class VoiceTrigger(
        val command: String,
        val language: String = "zh-CN"
    ) : AutomationTrigger()
}

@Serializable
enum class EventType {
    APP_OPENED,
    APP_CLOSED,
    MESSAGE_RECEIVED,
    FILE_CREATED,
    FILE_MODIFIED,
    NETWORK_CONNECTED,
    NETWORK_DISCONNECTED,
    BATTERY_LOW,
    BATTERY_CHARGED,
    LOCATION_CHANGED,
    TIME_CHANGED,
    CUSTOM
}

@Serializable
data class AutomationWorkflow(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val tasks: List<AutomationTask>,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastExecuted: Long? = null,
    val executionCount: Int = 0,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class AutomationLog(
    val id: String = UUID.randomUUID().toString(),
    val taskId: String? = null,
    val workflowId: String? = null,
    val level: LogLevel,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
enum class LogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR
}