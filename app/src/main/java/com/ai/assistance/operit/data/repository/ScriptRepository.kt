package com.ai.assistance.operit.data.repository

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.scripts.ScriptDefinition
import com.ai.assistance.operit.core.scripts.ScriptSerializer
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.data.model.ContentFormat
import com.ai.assistance.operit.data.model.ExecutionStatus
import com.ai.assistance.operit.data.model.Script
import com.ai.assistance.operit.data.model.ScriptTag
import com.ai.assistance.operit.data.model.ScriptTag_
import com.ai.assistance.operit.data.model.ScriptVersion
import com.ai.assistance.operit.data.model.Script_
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

/**
 * Repository for handling Script data operations.
 * Manages CRUD operations, version history, import/export, and serialization.
 */
class ScriptRepository(private val context: Context, profileId: String = "default") {
    
    companion object {
        private const val TAG = "ScriptRepository"
    }
    
    private val store = ObjectBoxManager.get(context, profileId)
    private val scriptBox: Box<Script> = store.boxFor()
    private val tagBox: Box<ScriptTag> = store.boxFor()
    private val versionBox: Box<ScriptVersion> = store.boxFor()
    
    // --- CRUD Operations ---
    
    /**
     * Creates or updates a script.
     * @param script The script to save
     * @return The ID of the saved script
     */
    suspend fun saveScript(script: Script): Long = withContext(Dispatchers.IO) {
        script.updatedAt = Date()
        scriptBox.put(script)
    }
    
    /**
     * Find a script by its ID.
     * @param id The ID of the script
     * @return The script or null if not found
     */
    suspend fun findScriptById(id: Long): Script? = withContext(Dispatchers.IO) {
        scriptBox.get(id)
    }
    
    /**
     * Find a script by its UUID.
     * @param uuid The UUID of the script
     * @return The script or null if not found
     */
    suspend fun findScriptByUuid(uuid: String): Script? = withContext(Dispatchers.IO) {
        scriptBox.query(Script_.uuid.equal(uuid)).build().findFirst()
    }
    
    /**
     * Find a script by its name.
     * @param name The name of the script
     * @return The script or null if not found
     */
    suspend fun findScriptByName(name: String): Script? = withContext(Dispatchers.IO) {
        scriptBox.query(Script_.name.equal(name, QueryBuilder.StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst()
    }
    
    /**
     * Get all scripts.
     * @return List of all scripts
     */
    suspend fun getAllScripts(): List<Script> = withContext(Dispatchers.IO) {
        scriptBox.all
    }
    
    /**
     * Search scripts by name or description.
     * @param query The search query
     * @return List of matching scripts
     */
    suspend fun searchScripts(query: String): List<Script> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext getAllScripts()
        
        val nameCondition = Script_.name.contains(query, QueryBuilder.StringOrder.CASE_INSENSITIVE)
        val descCondition = Script_.description.contains(query, QueryBuilder.StringOrder.CASE_INSENSITIVE)
        
        scriptBox.query(nameCondition.or(descCondition))
            .build()
            .find()
    }
    
    /**
     * Delete a script and all its versions.
     * @param scriptId The ID of the script to delete
     * @return True if successful
     */
    suspend fun deleteScript(scriptId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = findScriptById(scriptId) ?: return@withContext false
            
            val versionIds = script.versions.map { it.id }
            if (versionIds.isNotEmpty()) {
                versionBox.removeByIds(versionIds)
            }
            
            scriptBox.remove(script)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting script $scriptId", e)
            false
        }
    }
    
    // --- Tag Operations ---
    
    /**
     * Add a tag to a script.
     * @param script The script
     * @param tagName The name of the tag
     * @return The tag
     */
    suspend fun addTagToScript(script: Script, tagName: String): ScriptTag = withContext(Dispatchers.IO) {
        val tag = tagBox.query()
            .equal(ScriptTag_.name, tagName, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build()
            .findFirst()
            ?: ScriptTag(name = tagName).also { tagBox.put(it) }
        
        if (!script.tags.any { it.id == tag.id }) {
            script.tags.add(tag)
            scriptBox.put(script)
        }
        tag
    }
    
    /**
     * Remove a tag from a script.
     * @param script The script
     * @param tagName The name of the tag
     */
    suspend fun removeTagFromScript(script: Script, tagName: String) = withContext(Dispatchers.IO) {
        val tag = script.tags.find { it.name == tagName }
        if (tag != null) {
            script.tags.remove(tag)
            scriptBox.put(script)
        }
    }
    
    /**
     * Get all scripts with a specific tag.
     * @param tagName The name of the tag
     * @return List of scripts with the tag
     */
    suspend fun getScriptsByTag(tagName: String): List<Script> = withContext(Dispatchers.IO) {
        val tag = tagBox.query()
            .equal(ScriptTag_.name, tagName, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build()
            .findFirst()
        
        tag?.scripts ?: emptyList()
    }
    
    // --- Version Management ---
    
    /**
     * Create a new version of a script.
     * @param script The script to version
     * @param changeDescription Description of the changes
     * @return The created version
     */
    suspend fun createVersion(
        script: Script,
        changeDescription: String = ""
    ): ScriptVersion = withContext(Dispatchers.IO) {
        val version = ScriptVersion(
            versionLabel = script.currentVersion,
            serializedContent = script.serializedContent,
            contentFormat = script.contentFormat,
            changeDescription = changeDescription,
            createdAt = Date(),
            author = script.author
        ).apply {
            this.script.target = script
        }
        
        versionBox.put(version)
        script.versions.add(version)
        scriptBox.put(script)
        version
    }
    
    /**
     * Get all versions of a script.
     * @param scriptId The ID of the script
     * @return List of versions sorted by creation date
     */
    suspend fun getVersions(scriptId: Long): List<ScriptVersion> = withContext(Dispatchers.IO) {
        val script = findScriptById(scriptId) ?: return@withContext emptyList()
        script.versions.sortedBy { it.createdAt }
    }
    
    /**
     * Restore a script to a specific version.
     * @param scriptId The ID of the script
     * @param versionLabel The version label to restore
     * @return True if successful
     */
    suspend fun restoreVersion(scriptId: Long, versionLabel: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = findScriptById(scriptId) ?: return@withContext false
            val version = script.versions.find { it.versionLabel == versionLabel }
                ?: return@withContext false
            
            createVersion(script, "Snapshot before restoring $versionLabel")
            
            script.serializedContent = version.serializedContent
            script.contentFormat = version.contentFormat
            script.currentVersion = version.versionLabel
            script.updatedAt = Date()
            scriptBox.put(script)
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring version $versionLabel for script $scriptId", e)
            false
        }
    }
    
    // --- Import/Export Operations ---
    
    /**
     * Export a script to a file.
     * @param scriptId The ID of the script to export
     * @param file The target file
     * @param format The export format (JSON5 or YAML)
     * @return True if successful
     */
    suspend fun exportScript(
        scriptId: Long,
        file: File,
        format: ContentFormat = ContentFormat.JSON5
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = findScriptById(scriptId) ?: return@withContext false
            val definition = deserializeScript(script) ?: return@withContext false
            
            val content = ScriptSerializer.serialize(definition, format)
            file.writeText(content)
            
            Log.d(TAG, "Exported script ${script.name} to ${file.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting script $scriptId", e)
            false
        }
    }
    
    /**
     * Import a script from a file.
     * @param file The source file
     * @param format The import format (JSON5 or YAML)
     * @return The imported script or null if import failed
     */
    suspend fun importScript(
        file: File,
        format: ContentFormat = ContentFormat.JSON5
    ): Script? = withContext(Dispatchers.IO) {
        try {
            val content = file.readText()
            val definition = ScriptSerializer.deserialize(content, format)
                ?: return@withContext null
            
            val script = Script(
                name = definition.metadata.name,
                description = definition.metadata.description,
                author = definition.metadata.author,
                serializedContent = content,
                contentFormat = format,
                currentVersion = definition.metadata.version,
                createdAt = Date(),
                updatedAt = Date()
            )
            
            val scriptId = scriptBox.put(script)
            
            definition.metadata.tags.forEach { tagName ->
                addTagToScript(script, tagName)
            }
            
            createVersion(script, "Initial import from file")
            
            Log.d(TAG, "Imported script ${script.name} from ${file.absolutePath}")
            findScriptById(scriptId)
        } catch (e: Exception) {
            Log.e(TAG, "Error importing script from ${file.absolutePath}", e)
            null
        }
    }
    
    /**
     * Export a script as a ScriptDefinition object.
     * @param scriptId The ID of the script
     * @return The script definition or null if not found
     */
    suspend fun exportScriptDefinition(scriptId: Long): ScriptDefinition? = withContext(Dispatchers.IO) {
        val script = findScriptById(scriptId) ?: return@withContext null
        deserializeScript(script)
    }
    
    /**
     * Import a script from a ScriptDefinition object.
     * @param definition The script definition
     * @param format The format to store in (JSON5 or YAML)
     * @return The imported script
     */
    suspend fun importScriptDefinition(
        definition: ScriptDefinition,
        format: ContentFormat = ContentFormat.JSON5
    ): Script = withContext(Dispatchers.IO) {
        val serializedContent = ScriptSerializer.serialize(definition, format)
        
        val script = Script(
            name = definition.metadata.name,
            description = definition.metadata.description,
            author = definition.metadata.author,
            serializedContent = serializedContent,
            contentFormat = format,
            currentVersion = definition.metadata.version,
            createdAt = Date(),
            updatedAt = Date()
        )
        
        scriptBox.put(script)
        
        definition.metadata.tags.forEach { tagName ->
            addTagToScript(script, tagName)
        }
        
        createVersion(script, "Initial import from definition")
        script
    }
    
    // --- Execution Tracking ---
    
    /**
     * Update script execution statistics.
     * @param scriptId The ID of the script
     * @param status The execution status
     */
    suspend fun updateExecutionStatus(
        scriptId: Long,
        status: ExecutionStatus
    ) = withContext(Dispatchers.IO) {
        val script = findScriptById(scriptId) ?: return@withContext
        script.executionCount++
        script.lastExecutedAt = Date()
        script.lastExecutionStatus = status
        scriptBox.put(script)
    }
    
    // --- Helper Methods ---
    
    /**
     * Deserialize a script's content to a ScriptDefinition.
     * @param script The script
     * @return The script definition or null if deserialization fails
     */
    private fun deserializeScript(script: Script): ScriptDefinition? {
        return try {
            ScriptSerializer.deserialize(script.serializedContent, script.contentFormat)
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing script ${script.id}", e)
            null
        }
    }
    
    /**
     * Validate a script's content.
     * @param scriptId The ID of the script
     * @return True if the script content is valid
     */
    suspend fun validateScript(scriptId: Long): Boolean = withContext(Dispatchers.IO) {
        val script = findScriptById(scriptId) ?: return@withContext false
        ScriptSerializer.validate(script.serializedContent, script.contentFormat)
    }
}
