package com.ai.assistance.operit.core.scripts

import android.util.Log
import com.ai.assistance.operit.data.model.ContentFormat
import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hjson.JsonValue
import org.hjson.Stringify

/**
 * Utility for serializing and deserializing script definitions to/from human-readable formats.
 * Supports JSON5 (via HJSON) and YAML.
 */
object ScriptSerializer {
    private const val TAG = "ScriptSerializer"
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
        classDiscriminator = "kind"
    }
    
    private val yaml = Yaml(
        configuration = YamlConfiguration(
            encodeDefaults = false,
            polymorphismStyle = PolymorphismStyle.Property,
            classDiscriminator = "kind"
        )
    )
    
    /**
     * Serialize a ScriptDefinition to the specified format.
     * @param definition The script definition to serialize
     * @param format The target format (JSON5 or YAML)
     * @return The serialized string
     */
    fun serialize(definition: ScriptDefinition, format: ContentFormat = ContentFormat.JSON5): String {
        return when (format) {
            ContentFormat.JSON5 -> serializeToJson5(definition)
            ContentFormat.YAML -> serializeToYaml(definition)
        }
    }
    
    /**
     * Deserialize a string to a ScriptDefinition.
     * @param content The serialized script content
     * @param format The source format (JSON5 or YAML)
     * @return The deserialized ScriptDefinition
     */
    fun deserialize(content: String, format: ContentFormat = ContentFormat.JSON5): ScriptDefinition? {
        return try {
            when (format) {
                ContentFormat.JSON5 -> deserializeFromJson5(content)
                ContentFormat.YAML -> deserializeFromYaml(content)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing script content", e)
            null
        }
    }
    
    /**
     * Serialize to JSON5 format (using HJSON library which supports JSON5-like syntax)
     */
    private fun serializeToJson5(definition: ScriptDefinition): String {
        val jsonString = json.encodeToString(definition)
        val hjsonValue = JsonValue.readJSON(jsonString)
        return hjsonValue.toString(Stringify.HJSON)
    }
    
    /**
     * Deserialize from JSON5 format (using HJSON library)
     */
    private fun deserializeFromJson5(content: String): ScriptDefinition {
        val hjsonValue = JsonValue.readHjson(content)
        val jsonString = hjsonValue.toString()
        return json.decodeFromString(jsonString)
    }
    
    /**
     * Serialize to YAML format using the kaml library.
     */
    private fun serializeToYaml(definition: ScriptDefinition): String {
        return yaml.encodeToString(ScriptDefinition.serializer(), definition)
    }
    
    /**
     * Deserialize from YAML format using the kaml library.
     */
    private fun deserializeFromYaml(content: String): ScriptDefinition {
        return yaml.decodeFromString(ScriptDefinition.serializer(), content)
    }
    
    /**
     * Validate a serialized script content.
     * @param content The serialized content
     * @param format The format of the content
     * @return True if the content is valid and can be deserialized
     */
    fun validate(content: String, format: ContentFormat = ContentFormat.JSON5): Boolean {
        return try {
            deserialize(content, format) != null
        } catch (e: Exception) {
            Log.e(TAG, "Validation failed for script content", e)
            false
        }
    }
}
