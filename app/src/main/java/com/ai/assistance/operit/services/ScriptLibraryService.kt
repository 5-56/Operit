package com.ai.assistance.operit.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.ai.assistance.operit.data.model.Script
import com.ai.assistance.operit.data.model.ScriptCategory
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.data.repository.ScriptLibraryRepository
import com.ai.assistance.operit.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.Date

/**
 * Service for managing the Script Library
 * Handles import/export, sharing, cloud sync (optional), and permission-aware storage
 */
class ScriptLibraryService private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ScriptLibraryService"
        private const val SCRIPTS_DIR = "scripts"
        private const val BACKUP_DIR = "script_backups"
        private const val EXPORT_FILE_EXTENSION = ".operit-script"
        private const val BUNDLE_FILE_EXTENSION = ".operit-scripts"

        @Volatile
        private var INSTANCE: ScriptLibraryService? = null

        fun getInstance(context: Context): ScriptLibraryService =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScriptLibraryService(context.applicationContext).also {
                    INSTANCE = it
                }
            }
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // Get the external scripts directory
    private val scriptsDir: File
        get() {
            val dir = File(context.getExternalFilesDir(null), SCRIPTS_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    // Get the backup directory
    private val backupDir: File
        get() {
            val dir = File(context.getExternalFilesDir(null), BACKUP_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    private suspend fun getRepository(): ScriptLibraryRepository {
        val profileId = preferencesManager.activeProfileIdFlow.first()
        return ScriptLibraryRepository(context, profileId)
    }

    // --- Import Operations ---

    /**
     * Import script from file
     */
    suspend fun importFromFile(fileUri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(fileUri)
                ?: return@withContext ImportResult(false, "Cannot open file")

            val content = inputStream.bufferedReader().use { it.readText() }
            val scriptData = json.decodeFromString<ExportedScript>(content)

            val repository = getRepository()
            
            // Check if script already exists
            val existingScript = repository.findScriptByName(scriptData.name)
            if (existingScript != null) {
                return@withContext ImportResult(
                    false, 
                    "Script with name '${scriptData.name}' already exists",
                    requiresConfirmation = true
                )
            }

            val script = scriptData.toScript()
            script.source = "file"
            script.isTrusted = false // Require user confirmation for file imports
            
            val scriptId = repository.saveScript(script)
            
            // Add tags
            scriptData.tags.forEach { tag ->
                repository.addTagToScript(script, tag)
            }

            Log.d(TAG, "Successfully imported script from file: ${script.name}")
            ImportResult(true, "Script imported successfully", scriptId = scriptId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import script from file", e)
            ImportResult(false, "Import failed: ${e.message}")
        }
    }

    /**
     * Import script from URL
     */
    suspend fun importFromUrl(url: String): ImportResult = withContext(Dispatchers.IO) {
        try {
            val content = URL(url).readText()
            val scriptData = json.decodeFromString<ExportedScript>(content)

            val repository = getRepository()
            
            // Check if script already exists
            val existingScript = repository.findScriptByName(scriptData.name)
            if (existingScript != null) {
                return@withContext ImportResult(
                    false,
                    "Script with name '${scriptData.name}' already exists",
                    requiresConfirmation = true
                )
            }

            val script = scriptData.toScript()
            script.source = "url"
            script.sourceUrl = url
            script.isTrusted = false // Require user confirmation for URL imports
            
            val scriptId = repository.saveScript(script)
            
            // Add tags
            scriptData.tags.forEach { tag ->
                repository.addTagToScript(script, tag)
            }

            Log.d(TAG, "Successfully imported script from URL: ${script.name}")
            ImportResult(true, "Script imported successfully", scriptId = scriptId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import script from URL: $url", e)
            ImportResult(false, "Import failed: ${e.message}")
        }
    }

    /**
     * Import multiple scripts from a bundle file
     */
    suspend fun importBundle(fileUri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(fileUri)
                ?: return@withContext ImportResult(false, "Cannot open bundle file")

            val content = inputStream.bufferedReader().use { it.readText() }
            val bundle = json.decodeFromString<ScriptBundle>(content)

            val errors = mutableListOf<String>()
            val restoreResult = restoreFromBundle(
                bundle = bundle,
                replaceExisting = false,
                source = "bundle",
                markNewScriptsTrusted = false,
                sourceUrl = null,
                errors = errors
            )

            val hasNewScripts = restoreResult.restored > 0
            ImportResult(
                success = restoreResult.success && hasNewScripts,
                message = restoreResult.message,
                details = if (errors.isNotEmpty()) errors.joinToString("\n") else null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import script bundle", e)
            ImportResult(false, "Bundle import failed: ${e.message}")
        }
    }

    // --- Export Operations ---

    /**
     * Export single script to file
     */
    suspend fun exportScript(scriptId: Long): ExportResult = withContext(Dispatchers.IO) {
        try {
            val repository = getRepository()
            val script = repository.findScriptById(scriptId)
                ?: return@withContext ExportResult(false, "Script not found")

            val exportData = ExportedScript.fromScript(script)
            val jsonContent = json.encodeToString(exportData)

            val fileName = "${script.name.replace(Regex("[^A-Za-z0-9_-]"), "_")}$EXPORT_FILE_EXTENSION"
            val file = File(scriptsDir, fileName)
            file.writeText(jsonContent)

            Log.d(TAG, "Exported script to: ${file.absolutePath}")
            ExportResult(true, "Script exported successfully", file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export script", e)
            ExportResult(false, "Export failed: ${e.message}")
        }
    }

    /**
     * Export multiple scripts as a bundle
     */
    suspend fun exportScripts(scriptIds: List<Long>): ExportResult = withContext(Dispatchers.IO) {
        try {
            val repository = getRepository()
            val scripts = scriptIds.mapNotNull { repository.findScriptById(it) }

            if (scripts.isEmpty()) {
                return@withContext ExportResult(false, "No scripts found to export")
            }

            val bundle = ScriptBundle(
                version = "1.0",
                exportedAt = Date(),
                scripts = scripts.map { ExportedScript.fromScript(it) }
            )

            val jsonContent = json.encodeToString(bundle)
            val fileName = "script_bundle_${System.currentTimeMillis()}$BUNDLE_FILE_EXTENSION"
            val file = File(scriptsDir, fileName)
            file.writeText(jsonContent)

            Log.d(TAG, "Exported ${scripts.size} scripts to: ${file.absolutePath}")
            ExportResult(true, "Exported ${scripts.size} scripts", file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export scripts", e)
            ExportResult(false, "Export failed: ${e.message}")
        }
    }

    /**
     * Share script via Android share sheet
     */
    suspend fun shareScript(scriptId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val exportResult = exportScript(scriptId)
            if (!exportResult.success || exportResult.file == null) {
                return@withContext false
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                exportResult.file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Operit Script: ${exportResult.file.nameWithoutExtension}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share Script").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share script", e)
            false
        }
    }

    // --- Backup/Restore Operations ---

    /**
     * Create backup of all scripts
     */
    suspend fun createBackup(): BackupResult = withContext(Dispatchers.IO) {
        try {
            val repository = getRepository()
            val scripts = repository.getAllScripts()

            val bundle = ScriptBundle(
                version = "1.0",
                exportedAt = Date(),
                scripts = scripts.map { ExportedScript.fromScript(it) }
            )

            val jsonContent = json.encodeToString(bundle)
            val fileName = "backup_${System.currentTimeMillis()}$BUNDLE_FILE_EXTENSION"
            val file = File(backupDir, fileName)
            file.writeText(jsonContent)

            Log.d(TAG, "Created backup of ${scripts.size} scripts: ${file.absolutePath}")
            BackupResult(true, "Backup created successfully", file, scripts.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create backup", e)
            BackupResult(false, "Backup failed: ${e.message}")
        }
    }

    /**
     * Restore scripts from backup
     */
    suspend fun restoreBackup(fileUri: Uri, replaceExisting: Boolean = false): RestoreResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(fileUri)
                ?: return@withContext RestoreResult(false, "Cannot open backup file")

            val content = inputStream.bufferedReader().use { it.readText() }
            val bundle = json.decodeFromString<ScriptBundle>(content)

            val errors = mutableListOf<String>()
            return@withContext restoreFromBundle(
                bundle = bundle,
                replaceExisting = replaceExisting,
                source = "backup",
                markNewScriptsTrusted = true,
                sourceUrl = null,
                errors = errors
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore backup", e)
            return@withContext RestoreResult(false, "Restore failed: ${e.message}")
        }
    }

    /**
     * Shared logic for restoring scripts from a bundle
     */
    private suspend fun restoreFromBundle(
        bundle: ScriptBundle,
        replaceExisting: Boolean,
        source: String,
        markNewScriptsTrusted: Boolean,
        sourceUrl: String?,
        errors: MutableList<String>? = null
    ): RestoreResult = withContext(Dispatchers.IO) {
        val repository = getRepository()
        var restored = 0
        var skipped = 0
        var updated = 0

        bundle.scripts.forEach { scriptData ->
            try {
                val existingScript = repository.findScriptByName(scriptData.name)
                if (existingScript != null) {
                    if (replaceExisting) {
                        applyExportedData(existingScript, scriptData, source, sourceUrl, markNewScriptsTrusted)
                        existingScript.tags.clear()
                        repository.saveScript(existingScript)
                        scriptData.tags.forEach { tag ->
                            repository.addTagToScript(existingScript, tag)
                        }
                        updated++
                    } else {
                        skipped++
                    }
                } else {
                    val newScript = scriptData.toScript().apply {
                        source = source
                        sourceUrl = sourceUrl
                        isTrusted = markNewScriptsTrusted
                    }
                    repository.saveScript(newScript)

                    scriptData.tags.forEach { tag ->
                        repository.addTagToScript(newScript, tag)
                    }
                    restored++
                }
            } catch (e: Exception) {
                val errorMsg = "Failed to restore script: ${scriptData.name}: ${e.message}"
                Log.e(TAG, errorMsg, e)
                errors?.add(errorMsg)
            }
        }

        val message = buildString {
            append("Restored $restored scripts")
            if (updated > 0) append(", updated $updated")
            if (skipped > 0) append(", skipped $skipped existing")
        }

        RestoreResult(errors.isNullOrEmpty(), message, restored, updated, skipped)
    }

    /**
     * Helper function to apply exported script data to an existing script
     */
    private fun applyExportedData(
        script: Script,
        exportedData: ExportedScript,
        source: String,
        sourceUrl: String?,
        markAsTrusted: Boolean
    ) {
        script.description = exportedData.description
        script.content = exportedData.content
        script.language = exportedData.language
        script.category = exportedData.category
        script.author = exportedData.author
        script.version = exportedData.version
        script.source = source
        script.sourceUrl = sourceUrl
        script.isTrusted = markAsTrusted
        script.updatedAt = if (exportedData.updatedAt > 0) Date(exportedData.updatedAt) else Date()
        if (exportedData.createdAt > 0) {
            script.createdAt = Date(exportedData.createdAt)
        }
    }

    /**
     * List available backups
     */
    suspend fun listBackups(): List<BackupInfo> = withContext(Dispatchers.IO) {
        backupDir.listFiles { file ->
            file.isFile && file.name.endsWith(BUNDLE_FILE_EXTENSION)
        }?.map { file ->
            BackupInfo(
                file = file,
                name = file.nameWithoutExtension,
                date = Date(file.lastModified()),
                size = file.length()
            )
        }?.sortedByDescending { it.date } ?: emptyList()
    }

    /**
     * Validate script for security concerns
     */
    fun validateScript(script: Script): ValidationResult {
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()

        // Check for potentially dangerous patterns
        val dangerousPatterns = listOf(
            "Runtime\\.getRuntime\\(\\)\\.exec" to "Executes system commands",
            "ProcessBuilder" to "Creates system processes",
            "java\\.lang\\.reflect" to "Uses reflection which may access private APIs",
            "System\\.exit" to "Can terminate the application",
            "android\\.os\\.Process\\.killProcess" to "Can kill processes",
            "__import__" to "Dynamic Python imports",
            "eval\\(" to "Evaluates arbitrary code",
            "exec\\(" to "Executes arbitrary code"
        )

        dangerousPatterns.forEach { (pattern, reason) ->
            if (Regex(pattern).containsMatchIn(script.content)) {
                warnings.add("⚠️ $reason")
            }
        }

        // Check if script is from untrusted source
        if (!script.isTrusted && script.source !in listOf("builtin", "user")) {
            warnings.add("⚠️ Script is from an untrusted source: ${script.source}")
        }

        // Check script size
        if (script.content.length > 100_000) {
            warnings.add("⚠️ Script is unusually large (${script.content.length} chars)")
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            warnings = warnings,
            errors = errors,
            requiresUserConfirmation = warnings.isNotEmpty() || !script.isTrusted
        )
    }
}

// --- Data Classes for Import/Export ---

@Serializable
data class ExportedScript(
    val name: String,
    val description: String,
    val content: String,
    val language: String,
    val category: String,
    val author: String? = null,
    val version: String = "1.0",
    val tags: List<String> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0
) {
    fun toScript(): Script = Script(
        name = name,
        description = description,
        content = content,
        language = language,
        category = category,
        author = author,
        version = version,
        createdAt = if (createdAt > 0) Date(createdAt) else Date(),
        updatedAt = if (updatedAt > 0) Date(updatedAt) else Date()
    )

    companion object {
        fun fromScript(script: Script): ExportedScript = ExportedScript(
            name = script.name,
            description = script.description,
            content = script.content,
            language = script.language,
            category = script.category,
            author = script.author,
            version = script.version,
            tags = script.tags.map { it.name },
            createdAt = script.createdAt.time,
            updatedAt = script.updatedAt.time
        )
    }
}

@Serializable
data class ScriptBundle(
    val version: String,
    val exportedAt: Long = System.currentTimeMillis(),
    val scripts: List<ExportedScript>
) {
    constructor(version: String, exportedAt: Date, scripts: List<ExportedScript>) : this(
        version, exportedAt.time, scripts
    )
}

// --- Result Classes ---

data class ImportResult(
    val success: Boolean,
    val message: String,
    val scriptId: Long? = null,
    val requiresConfirmation: Boolean = false,
    val details: String? = null
)

data class ExportResult(
    val success: Boolean,
    val message: String,
    val file: File? = null
)

data class BackupResult(
    val success: Boolean,
    val message: String,
    val file: File? = null,
    val scriptCount: Int = 0
)

data class RestoreResult(
    val success: Boolean,
    val message: String,
    val restored: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0
)

data class BackupInfo(
    val file: File,
    val name: String,
    val date: Date,
    val size: Long
)

data class ValidationResult(
    val isValid: Boolean,
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val requiresUserConfirmation: Boolean = false
)
