package com.ai.assistance.operit.data.model.script

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.ai.assistance.operit.core.tools.automatic.UIOperation
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Entity(tableName = "scripts")
@TypeConverters(ScriptConverters::class)
data class ScriptEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val code: String,
    val language: ScriptLanguage = ScriptLanguage.KOTLIN,
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isEnabled: Boolean = true,
    val category: ScriptCategory = ScriptCategory.AUTOMATION,
    val version: Int = 1
)

enum class ScriptLanguage {
    KOTLIN,
    JAVASCRIPT,
    JSON
}

enum class ScriptCategory {
    AUTOMATION,
    UTILITY,
    INTEGRATION,
    CUSTOM
}

class ScriptConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }
}

data class ScriptValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

data class ScriptStep(
    val id: String = UUID.randomUUID().toString(),
    val order: Int,
    val description: String,
    val operation: String,
    val parameters: Map<String, Any> = emptyMap()
)
