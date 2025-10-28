package com.ai.assistance.operit.data.repository

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.data.model.ScriptHistory
import com.ai.assistance.operit.data.model.ScriptVersionRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class ScriptVersionRepository(context: Context) {
    private val versionDir: File = File(context.getExternalFilesDir(null), "script_versions")
    private val contentDir: File = File(versionDir, "contents")
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true 
    }

    companion object {
        private const val TAG = "ScriptVersionRepository"
    }

    init {
        versionDir.mkdirs()
        contentDir.mkdirs()
    }

    suspend fun createOrUpdateScript(
        filePath: String,
        name: String,
        content: String,
        scriptType: String = "javascript",
        description: String = "",
        commitMessage: String = "",
        author: String = "user",
        isAutoSave: Boolean = true
    ): Result<ScriptHistory> = withContext(Dispatchers.IO) {
        try {
            val scriptId = calculateHash(filePath)
            val historyFile = File(versionDir, "$scriptId.json")
            
            val history = if (historyFile.exists()) {
                json.decodeFromString<ScriptHistory>(historyFile.readText())
            } else {
                ScriptHistory(
                    scriptId = scriptId,
                    name = name,
                    filePath = filePath,
                    scriptType = scriptType,
                    description = description,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    currentVersionId = null,
                    versions = emptyList()
                )
            }
            
            val contentHash = calculateHash(content)
            val lastVersion = history.versions.maxByOrNull { it.versionNumber }
            
            if (lastVersion != null && lastVersion.contentHash == contentHash) {
                Log.d(TAG, "Content unchanged, skipping version creation")
                return@withContext Result.success(history)
            }
            
            val nextVersionNumber = (lastVersion?.versionNumber ?: 0) + 1
            val versionId = UUID.randomUUID().toString()
            val contentPath = saveVersionContent(scriptId, versionId, content)
            
            val newVersion = ScriptVersionRecord(
                versionId = versionId,
                versionNumber = nextVersionNumber,
                createdAt = System.currentTimeMillis(),
                commitMessage = commitMessage.ifBlank {
                    if (isAutoSave) "Auto-save #$nextVersionNumber" else "Manual save #$nextVersionNumber"
                },
                author = author,
                isAutoSave = isAutoSave,
                tags = emptyList(),
                contentHash = contentHash,
                fileSize = content.toByteArray().size,
                contentPath = contentPath
            )
            
            val updatedHistory = history.copy(
                updatedAt = System.currentTimeMillis(),
                currentVersionId = versionId,
                versions = history.versions + newVersion
            )
            
            historyFile.writeText(json.encodeToString(updatedHistory))
            
            Log.d(TAG, "Created version $nextVersionNumber for script: $name")
            Result.success(updatedHistory)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating/updating script", e)
            Result.failure(e)
        }
    }

    fun getScriptByPath(filePath: String): ScriptHistory? {
        return try {
            val scriptId = calculateHash(filePath)
            val historyFile = File(versionDir, "$scriptId.json")
            if (historyFile.exists()) {
                json.decodeFromString(historyFile.readText())
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting script by path", e)
            null
        }
    }

    fun getAllScripts(): List<ScriptHistory> {
        return try {
            versionDir.listFiles { file -> file.extension == "json" }
                ?.mapNotNull { file ->
                    try {
                        json.decodeFromString<ScriptHistory>(file.readText())
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing history file: ${file.name}", e)
                        null
                    }
                } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all scripts", e)
            emptyList()
        }
    }

    fun getVersionContent(version: ScriptVersionRecord): String? {
        return try {
            val contentFile = File(contentDir, version.contentPath)
            if (contentFile.exists()) {
                contentFile.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading version content", e)
            null
        }
    }

    suspend fun rollbackToVersion(
        filePath: String,
        versionId: String
    ): Result<ScriptVersionRecord> = withContext(Dispatchers.IO) {
        try {
            val history = getScriptByPath(filePath)
                ?: return@withContext Result.failure(Exception("Script not found"))
            
            val targetVersion = history.versions.find { it.versionId == versionId }
                ?: return@withContext Result.failure(Exception("Version not found"))
            
            val content = getVersionContent(targetVersion)
                ?: return@withContext Result.failure(Exception("Version content not found"))
            
            val nextVersionNumber = history.versions.maxOf { it.versionNumber } + 1
            val newVersionId = UUID.randomUUID().toString()
            val contentPath = saveVersionContent(history.scriptId, newVersionId, content)
            
            val rollbackVersion = ScriptVersionRecord(
                versionId = newVersionId,
                versionNumber = nextVersionNumber,
                createdAt = System.currentTimeMillis(),
                commitMessage = "Rollback to version ${targetVersion.versionNumber}",
                author = "system",
                isAutoSave = false,
                tags = listOf("rollback"),
                contentHash = targetVersion.contentHash,
                fileSize = targetVersion.fileSize,
                contentPath = contentPath
            )
            
            val updatedHistory = history.copy(
                updatedAt = System.currentTimeMillis(),
                currentVersionId = newVersionId,
                versions = history.versions + rollbackVersion
            )
            
            val historyFile = File(versionDir, "${history.scriptId}.json")
            historyFile.writeText(json.encodeToString(updatedHistory))
            
            Log.d(TAG, "Rolled back to version ${targetVersion.versionNumber}")
            Result.success(rollbackVersion)
        } catch (e: Exception) {
            Log.e(TAG, "Error rolling back version", e)
            Result.failure(e)
        }
    }

    suspend fun deleteScript(filePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val scriptId = calculateHash(filePath)
            val historyFile = File(versionDir, "$scriptId.json")
            
            val history = if (historyFile.exists()) {
                json.decodeFromString<ScriptHistory>(historyFile.readText())
            } else {
                return@withContext Result.failure(Exception("Script not found"))
            }
            
            history.versions.forEach { version ->
                val contentFile = File(contentDir, version.contentPath)
                contentFile.delete()
            }
            
            historyFile.delete()
            
            Log.d(TAG, "Deleted script and ${history.versions.size} versions")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting script", e)
            Result.failure(e)
        }
    }

    suspend fun deleteVersion(
        filePath: String,
        versionId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val history = getScriptByPath(filePath)
                ?: return@withContext Result.failure(Exception("Script not found"))
            
            if (history.versions.size <= 1) {
                return@withContext Result.failure(Exception("Cannot delete the only version"))
            }
            
            val versionToDelete = history.versions.find { it.versionId == versionId }
                ?: return@withContext Result.failure(Exception("Version not found"))
            
            val contentFile = File(contentDir, versionToDelete.contentPath)
            contentFile.delete()
            
            val updatedHistory = history.copy(
                versions = history.versions.filter { it.versionId != versionId },
                currentVersionId = if (history.currentVersionId == versionId) {
                    history.versions.last { it.versionId != versionId }.versionId
                } else {
                    history.currentVersionId
                }
            )
            
            val historyFile = File(versionDir, "${history.scriptId}.json")
            historyFile.writeText(json.encodeToString(updatedHistory))
            
            Log.d(TAG, "Deleted version $versionId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting version", e)
            Result.failure(e)
        }
    }

    fun compareVersions(
        filePath: String,
        version1Id: String,
        version2Id: String
    ): VersionDiff? {
        val history = getScriptByPath(filePath) ?: return null
        val version1 = history.versions.find { it.versionId == version1Id } ?: return null
        val version2 = history.versions.find { it.versionId == version2Id } ?: return null
        val content1 = getVersionContent(version1) ?: return null
        val content2 = getVersionContent(version2) ?: return null
        
        return VersionDiff(
            version1 = version1,
            version2 = version2,
            differences = calculateDiff(content1, content2)
        )
    }

    suspend fun exportScriptWithHistory(filePath: String): Result<ScriptExportData> = 
        withContext(Dispatchers.IO) {
        try {
            val history = getScriptByPath(filePath)
                ?: return@withContext Result.failure(Exception("Script not found"))
            
            val versionsWithContent = history.versions.map { version ->
                val content = getVersionContent(version) ?: ""
                VersionWithContent(version, content)
            }
            
            val exportData = ScriptExportData(
                history = history,
                versionsWithContent = versionsWithContent
            )
            
            Result.success(exportData)
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting script", e)
            Result.failure(e)
        }
    }

    suspend fun importScriptWithHistory(exportData: ScriptExportData): Result<ScriptHistory> = 
        withContext(Dispatchers.IO) {
        try {
            val existingScript = getScriptByPath(exportData.history.filePath)
            if (existingScript != null) {
                return@withContext Result.failure(Exception("Script with this path already exists"))
            }
            
            exportData.versionsWithContent.forEach { versionWithContent ->
                saveVersionContent(
                    exportData.history.scriptId,
                    versionWithContent.version.versionId,
                    versionWithContent.content
                )
            }
            
            val newHistory = exportData.history.copy(
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            
            val historyFile = File(versionDir, "${newHistory.scriptId}.json")
            historyFile.writeText(json.encodeToString(newHistory))
            
            Log.d(TAG, "Imported script with ${newHistory.versions.size} versions")
            Result.success(newHistory)
        } catch (e: Exception) {
            Log.e(TAG, "Error importing script", e)
            Result.failure(e)
        }
    }

    private fun saveVersionContent(scriptId: String, versionId: String, content: String): String {
        val contentFileName = "${scriptId}_${versionId}.txt"
        val contentFile = File(contentDir, contentFileName)
        contentFile.writeText(content)
        return contentFileName
    }

    private fun calculateHash(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(content.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun calculateDiff(content1: String, content2: String): List<DiffLine> {
        val lines1 = content1.split("\n")
        val lines2 = content2.split("\n")
        
        val diffs = mutableListOf<DiffLine>()
        var i1 = 0
        var i2 = 0
        
        while (i1 < lines1.size || i2 < lines2.size) {
            val line1 = lines1.getOrNull(i1)
            val line2 = lines2.getOrNull(i2)
            
            when {
                line1 == line2 && line1 != null -> {
                    diffs.add(DiffLine(i2 + 1, line2, DiffType.UNCHANGED))
                    i1++
                    i2++
                }
                line1 == null -> {
                    diffs.add(DiffLine(i2 + 1, line2!!, DiffType.ADDED))
                    i2++
                }
                line2 == null -> {
                    diffs.add(DiffLine(i1 + 1, line1, DiffType.REMOVED))
                    i1++
                }
                else -> {
                    diffs.add(DiffLine(i1 + 1, line1, DiffType.REMOVED))
                    diffs.add(DiffLine(i2 + 1, line2, DiffType.ADDED))
                    i1++
                    i2++
                }
            }
        }
        
        return diffs
    }
}

data class VersionDiff(
    val version1: ScriptVersionRecord,
    val version2: ScriptVersionRecord,
    val differences: List<DiffLine>
)

data class DiffLine(
    val lineNumber: Int,
    val content: String,
    val type: DiffType
)

enum class DiffType {
    ADDED,
    REMOVED,
    UNCHANGED,
    MODIFIED
}

@kotlinx.serialization.Serializable
data class ScriptExportData(
    val history: ScriptHistory,
    val versionsWithContent: List<VersionWithContent>
)

@kotlinx.serialization.Serializable
data class VersionWithContent(
    val version: ScriptVersionRecord,
    val content: String
)
