package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "trigger_rules")
@Serializable
data class TriggerRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: TriggerType,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val triggerCondition: TriggerCondition,
    val actionType: TriggerActionType,
    val actionData: String
)

@Serializable
enum class TriggerType {
    NOTIFICATION,
    GEOFENCE
}

@Serializable
enum class TriggerActionType {
    RUN_AUTOMATION,
    EXECUTE_TOOL,
    CUSTOM_ACTION
}

@Serializable
sealed class TriggerCondition {
    @Serializable
    data class NotificationCondition(
        val packageName: String,
        val titlePattern: String? = null,
        val textPattern: String? = null,
        val matchMode: MatchMode = MatchMode.CONTAINS
    ) : TriggerCondition()
    
    @Serializable
    data class GeofenceCondition(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Float,
        val triggerOnEnter: Boolean = true,
        val triggerOnExit: Boolean = false
    ) : TriggerCondition()
}

@Serializable
enum class MatchMode {
    EXACT,
    CONTAINS,
    REGEX
}

@Serializable
data class AutomationAction(
    val functionName: String,
    val packageName: String? = null,
    val parameters: Map<String, String> = emptyMap()
)

@Serializable
data class ToolAction(
    val toolName: String,
    val parameters: Map<String, String> = emptyMap()
)

class TriggerConditionConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromTriggerCondition(condition: TriggerCondition): String {
        return json.encodeToString<TriggerCondition>(condition)
    }

    @TypeConverter
    fun toTriggerCondition(value: String): TriggerCondition {
        return json.decodeFromString<TriggerCondition>(value)
    }
}

class TriggerTypeConverter {
    @TypeConverter
    fun fromTriggerType(type: TriggerType): String = type.name

    @TypeConverter
    fun toTriggerType(value: String): TriggerType = TriggerType.valueOf(value)
}

class TriggerActionTypeConverter {
    @TypeConverter
    fun fromTriggerActionType(type: TriggerActionType): String = type.name

    @TypeConverter
    fun toTriggerActionType(value: String): TriggerActionType = TriggerActionType.valueOf(value)
}
