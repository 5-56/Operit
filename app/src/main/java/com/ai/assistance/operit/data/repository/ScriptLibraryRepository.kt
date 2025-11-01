package com.ai.assistance.operit.data.repository

import android.content.Context
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.data.model.Script
import com.ai.assistance.operit.data.model.ScriptTag
import com.ai.assistance.operit.data.model.ScriptTag_
import com.ai.assistance.operit.data.model.Script_
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for handling Script Library data operations
 */
class ScriptLibraryRepository(private val context: Context, profileId: String) {

    private val store = ObjectBoxManager.get(context, profileId)
    private val scriptBox: Box<Script> = store.boxFor()
    private val tagBox: Box<ScriptTag> = store.boxFor()

    // --- Script CRUD Operations ---

    /**
     * Saves or updates a script
     */
    suspend fun saveScript(script: Script): Long = withContext(Dispatchers.IO) {
        scriptBox.put(script)
    }

    /**
     * Finds a script by its ID
     */
    suspend fun findScriptById(id: Long): Script? = withContext(Dispatchers.IO) {
        scriptBox.get(id)
    }

    /**
     * Finds scripts by name (exact match)
     */
    suspend fun findScriptByName(name: String): Script? = withContext(Dispatchers.IO) {
        scriptBox.query()
            .equal(Script_.name, name, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build()
            .findFirst()
    }

    /**
     * Gets all scripts
     */
    suspend fun getAllScripts(): List<Script> = withContext(Dispatchers.IO) {
        scriptBox.all
    }

    /**
     * Gets scripts by category
     */
    suspend fun getScriptsByCategory(category: String): List<Script> = withContext(Dispatchers.IO) {
        scriptBox.query()
            .equal(Script_.category, category, QueryBuilder.StringOrder.CASE_INSENSITIVE)
            .build()
            .find()
    }

    /**
     * Gets favorite scripts
     */
    suspend fun getFavoriteScripts(): List<Script> = withContext(Dispatchers.IO) {
        scriptBox.query()
            .equal(Script_.isFavorite, true)
            .build()
            .find()
    }

    /**
     * Gets recently used scripts
     */
    suspend fun getRecentlyUsedScripts(limit: Int = 10): List<Script> = withContext(Dispatchers.IO) {
        scriptBox.query()
            .notNull(Script_.lastUsedAt)
            .orderDesc(Script_.lastUsedAt)
            .build()
            .find(0, limit.toLong())
    }

    /**
     * Searches scripts by name or description
     */
    suspend fun searchScripts(query: String): List<Script> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext scriptBox.all

        val nameCondition = Script_.name.contains(query, QueryBuilder.StringOrder.CASE_INSENSITIVE)
        val descCondition = Script_.description.contains(query, QueryBuilder.StringOrder.CASE_INSENSITIVE)
        val contentCondition = Script_.content.contains(query, QueryBuilder.StringOrder.CASE_INSENSITIVE)

        scriptBox.query()
            .apply {
                or(nameCondition, descCondition, contentCondition)
            }
            .build()
            .find()
    }

    /**
     * Deletes a script
     */
    suspend fun deleteScript(scriptId: Long): Boolean = withContext(Dispatchers.IO) {
        scriptBox.remove(scriptId)
    }

    /**
     * Toggles favorite status
     */
    suspend fun toggleFavorite(scriptId: Long): Boolean = withContext(Dispatchers.IO) {
        val script = scriptBox.get(scriptId) ?: return@withContext false
        script.isFavorite = !script.isFavorite
        scriptBox.put(script)
        true
    }

    /**
     * Updates script usage statistics
     */
    suspend fun recordScriptUsage(scriptId: Long) = withContext(Dispatchers.IO) {
        val script = scriptBox.get(scriptId) ?: return@withContext
        script.usageCount++
        script.lastUsedAt = java.util.Date()
        scriptBox.put(script)
    }

    // --- Tag Operations ---

    /**
     * Adds a tag to a script
     */
    suspend fun addTagToScript(script: Script, tagName: String): ScriptTag = withContext(Dispatchers.IO) {
        // Find existing tag or create a new one
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
     * Removes a tag from a script
     */
    suspend fun removeTagFromScript(script: Script, tagName: String) = withContext(Dispatchers.IO) {
        val tag = tagBox.query()
            .equal(ScriptTag_.name, tagName, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build()
            .findFirst() ?: return@withContext

        script.tags.removeIf { it.id == tag.id }
        scriptBox.put(script)
    }

    /**
     * Gets all tags
     */
    suspend fun getAllTags(): List<ScriptTag> = withContext(Dispatchers.IO) {
        tagBox.all
    }

    /**
     * Gets scripts by tag
     */
    suspend fun getScriptsByTag(tagName: String): List<Script> = withContext(Dispatchers.IO) {
        val tag = tagBox.query()
            .equal(ScriptTag_.name, tagName, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build()
            .findFirst() ?: return@withContext emptyList()

        tag.scripts.toList()
    }

    /**
     * Gets all categories
     */
    suspend fun getAllCategories(): List<String> = withContext(Dispatchers.IO) {
        scriptBox.all.map { it.category }.distinct().sorted()
    }

    /**
     * Gets script statistics
     */
    suspend fun getScriptStatistics(): ScriptStatistics = withContext(Dispatchers.IO) {
        val all = scriptBox.all
        ScriptStatistics(
            totalScripts = all.size,
            favoriteCount = all.count { it.isFavorite },
            categoryCount = all.map { it.category }.distinct().size,
            tagCount = tagBox.count(),
            mostUsedScript = all.maxByOrNull { it.usageCount }
        )
    }
}

/**
 * Script statistics data class
 */
data class ScriptStatistics(
    val totalScripts: Int,
    val favoriteCount: Int,
    val categoryCount: Int,
    val tagCount: Long,
    val mostUsedScript: Script?
)
