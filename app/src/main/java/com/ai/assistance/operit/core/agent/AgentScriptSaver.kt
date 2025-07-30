package com.ai.assistance.operit.core.agent

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.security.MessageDigest
import kotlin.collections.HashMap

/**
 * 脚本元数据
 */
@Serializable
data class ScriptMetadata(
    val id: String,
    val userRequest: String,
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val quality: Float = 0f,
    val executionCount: Int = 0,
    val successCount: Int = 0,
    val tags: List<String> = emptyList(),
    val category: String = "general",
    val fileSize: Long = 0,
    val checksumMd5: String = "",
    val description: String = "",
    val author: String = "agent",
    val dependencies: List<String> = emptyList()
)

/**
 * 脚本版本信息
 */
@Serializable
data class ScriptVersion(
    val version: Int,
    val timestamp: Long,
    val quality: Float,
    val changes: String,
    val filePath: String
)

/**
 * 脚本索引，用于快速检索
 */
@Serializable
data class ScriptIndex(
    val scripts: MutableMap<String, ScriptMetadata> = mutableMapOf(),
    val categories: MutableMap<String, MutableList<String>> = mutableMapOf(),
    val tags: MutableMap<String, MutableList<String>> = mutableMapOf(),
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * 智能化的Agent脚本保存器
 * 支持版本管理、索引检索、自动分类、Git集成等功能
 */
object AgentScriptSaver {
    private const val TAG = "AgentScriptSaver"
    private const val SCRIPTS_DIR = "agent_scripts"
    private const val VERSIONS_DIR = "versions"
    private const val INDEX_FILE = "script_index.json"
    private const val MAX_VERSIONS_PER_SCRIPT = 10
    
    private var context: Context? = null
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = false
        prettyPrint = true
    }
    
    private var scriptIndex = ScriptIndex()
    
    /**
     * 初始化保存器
     */
    fun initialize(context: Context) {
        this.context = context
        loadIndex()
        ensureDirectories()
    }
    
    /**
     * 保存脚本，支持版本管理和智能分类
     */
    suspend fun saveScript(
        script: String,
        userRequest: String,
        category: String = "general",
        tags: List<String> = emptyList(),
        description: String = ""
    ): String = withContext(Dispatchers.IO) {
        
        try {
            val scriptId = generateScriptId(userRequest)
            val scriptsDir = getScriptsDirectory()
            val timestamp = System.currentTimeMillis()
            val dateFormatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val timeString = dateFormatter.format(Date(timestamp))
            
            // 创建脚本文件名
            val fileName = "${scriptId}_v${getNextVersion(scriptId)}_$timeString.js"
            val scriptFile = File(scriptsDir, fileName)
            
            // 保存脚本内容
            scriptFile.writeText(script)
            
            // 计算文件校验和
            val checksumMd5 = calculateMD5(script)
            
            // 创建或更新元数据
            val existingMetadata = scriptIndex.scripts[scriptId]
            val newVersion = existingMetadata?.version?.plus(1) ?: 1
            
            val metadata = ScriptMetadata(
                id = scriptId,
                userRequest = userRequest,
                version = newVersion,
                createdAt = existingMetadata?.createdAt ?: timestamp,
                updatedAt = timestamp,
                tags = tags.ifEmpty { generateAutoTags(userRequest, script) },
                category = category.ifEmpty { categorizeScript(userRequest, script) },
                fileSize = scriptFile.length(),
                checksumMd5 = checksumMd5,
                description = description.ifEmpty { generateAutoDescription(userRequest) },
                quality = existingMetadata?.quality ?: 0f,
                executionCount = existingMetadata?.executionCount ?: 0,
                successCount = existingMetadata?.successCount ?: 0
            )
            
            // 更新索引
            updateIndex(scriptId, metadata)
            
            // 保存版本信息
            saveVersionInfo(scriptId, newVersion, timestamp, 0f, "自动保存", scriptFile.absolutePath)
            
            // 清理旧版本
            cleanupOldVersions(scriptId)
            
            // 保存索引
            saveIndex()
            
            Log.i(TAG, "脚本保存成功: $fileName")
            return@withContext scriptFile.absolutePath
            
        } catch (e: Exception) {
            Log.e(TAG, "保存脚本失败", e)
            throw e
        }
    }
    
    /**
     * 加载脚本
     */
    suspend fun loadScript(scriptId: String, version: Int? = null): String? = withContext(Dispatchers.IO) {
        try {
            val metadata = scriptIndex.scripts[scriptId] ?: return@withContext null
            
            val targetVersion = version ?: metadata.version
            val versionInfo = loadVersionInfo(scriptId, targetVersion) ?: return@withContext null
            
            val scriptFile = File(versionInfo.filePath)
            if (!scriptFile.exists()) {
                Log.w(TAG, "脚本文件不存在: ${versionInfo.filePath}")
                return@withContext null
            }
            
            return@withContext scriptFile.readText()
            
        } catch (e: Exception) {
            Log.e(TAG, "加载脚本失败: $scriptId", e)
            return@withContext null
        }
    }
    
    /**
     * 搜索脚本
     */
    fun searchScripts(
        query: String = "",
        category: String = "",
        tags: List<String> = emptyList(),
        minQuality: Float = 0f
    ): List<ScriptMetadata> {
        
        return scriptIndex.scripts.values.filter { metadata ->
            // 查询条件过滤
            val matchesQuery = query.isEmpty() || 
                metadata.userRequest.contains(query, ignoreCase = true) ||
                metadata.description.contains(query, ignoreCase = true)
            
            val matchesCategory = category.isEmpty() || metadata.category == category
            
            val matchesTags = tags.isEmpty() || tags.any { tag -> 
                metadata.tags.any { it.contains(tag, ignoreCase = true) }
            }
            
            val matchesQuality = metadata.quality >= minQuality
            
            matchesQuery && matchesCategory && matchesTags && matchesQuality
        }.sortedByDescending { it.updatedAt }
    }
    
    /**
     * 获取脚本统计信息
     */
    fun getScriptStats(): Map<String, Any> {
        val totalScripts = scriptIndex.scripts.size
        val categories = scriptIndex.categories.size
        val totalTags = scriptIndex.tags.size
        val avgQuality = if (totalScripts > 0) {
            scriptIndex.scripts.values.map { it.quality }.average()
        } else 0.0
        
        val totalExecutions = scriptIndex.scripts.values.sumOf { it.executionCount }
        val totalSuccesses = scriptIndex.scripts.values.sumOf { it.successCount }
        val successRate = if (totalExecutions > 0) {
            totalSuccesses.toDouble() / totalExecutions
        } else 0.0
        
        return mapOf(
            "totalScripts" to totalScripts,
            "categories" to categories,
            "totalTags" to totalTags,
            "averageQuality" to avgQuality,
            "totalExecutions" to totalExecutions,
            "successRate" to successRate,
            "lastUpdated" to scriptIndex.lastUpdated
        )
    }
    
    /**
     * 更新脚本质量分数
     */
    suspend fun updateScriptQuality(scriptId: String, quality: Float) = withContext(Dispatchers.IO) {
        scriptIndex.scripts[scriptId]?.let { metadata ->
            val updatedMetadata = metadata.copy(quality = quality, updatedAt = System.currentTimeMillis())
            scriptIndex.scripts[scriptId] = updatedMetadata
            saveIndex()
        }
    }
    
    /**
     * 记录脚本执行结果
     */
    suspend fun recordExecution(scriptId: String, success: Boolean) = withContext(Dispatchers.IO) {
        scriptIndex.scripts[scriptId]?.let { metadata ->
            val updatedMetadata = metadata.copy(
                executionCount = metadata.executionCount + 1,
                successCount = metadata.successCount + if (success) 1 else 0,
                updatedAt = System.currentTimeMillis()
            )
            scriptIndex.scripts[scriptId] = updatedMetadata
            saveIndex()
        }
    }
    
    /**
     * 删除脚本
     */
    suspend fun deleteScript(scriptId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val metadata = scriptIndex.scripts[scriptId] ?: return@withContext false
            
            // 删除所有版本文件
            val versions = loadAllVersions(scriptId)
            versions.forEach { version ->
                val file = File(version.filePath)
                if (file.exists()) {
                    file.delete()
                }
            }
            
            // 删除版本信息文件
            val versionsDir = File(getScriptsDirectory(), VERSIONS_DIR)
            val versionFile = File(versionsDir, "$scriptId.json")
            if (versionFile.exists()) {
                versionFile.delete()
            }
            
            // 从索引中移除
            scriptIndex.scripts.remove(scriptId)
            
            // 更新分类和标签索引
            scriptIndex.categories[metadata.category]?.remove(scriptId)
            metadata.tags.forEach { tag ->
                scriptIndex.tags[tag]?.remove(scriptId)
            }
            
            saveIndex()
            
            Log.i(TAG, "脚本删除成功: $scriptId")
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "删除脚本失败: $scriptId", e)
            return@withContext false
        }
    }
    
    /**
     * 导出脚本
     */
    suspend fun exportScripts(exportDir: File, scriptIds: List<String>? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }
            
            val scriptsToExport = scriptIds?.mapNotNull { scriptIndex.scripts[it] } 
                ?: scriptIndex.scripts.values.toList()
            
            scriptsToExport.forEach { metadata ->
                val script = loadScript(metadata.id)
                if (script != null) {
                    val exportFile = File(exportDir, "${metadata.id}_v${metadata.version}.js")
                    exportFile.writeText(script)
                    
                    // 同时导出元数据
                    val metadataFile = File(exportDir, "${metadata.id}_metadata.json")
                    metadataFile.writeText(json.encodeToString(ScriptMetadata.serializer(), metadata))
                }
            }
            
            // 导出索引
            val indexFile = File(exportDir, INDEX_FILE)
            indexFile.writeText(json.encodeToString(ScriptIndex.serializer(), scriptIndex))
            
            Log.i(TAG, "脚本导出成功: ${scriptsToExport.size} 个脚本")
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "导出脚本失败", e)
            return@withContext false
        }
    }
    
    /**
     * Git集成 - 自动提交
     */
    suspend fun autoGitUpload(scriptPath: String, commitMessage: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val scriptsDir = getScriptsDirectory()
            
            // 检查是否是Git仓库
            val gitDir = File(scriptsDir, ".git")
            if (!gitDir.exists()) {
                Log.i(TAG, "初始化Git仓库")
                initGitRepository(scriptsDir)
            }
            
            // 执行Git命令
            val commands = listOf(
                "git add .",
                "git commit -m \"$commitMessage\"",
                "git push origin main"
            )
            
            commands.forEach { command ->
                val result = executeCommand(command, scriptsDir)
                if (!result.first) {
                    Log.w(TAG, "Git命令执行失败: $command, 错误: ${result.second}")
                    return@withContext false
                }
            }
            
            Log.i(TAG, "Git提交成功: $commitMessage")
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "Git提交失败", e)
            return@withContext false
        }
    }
    
    // === 私有辅助方法 ===
    
    private fun generateScriptId(userRequest: String): String {
        val sanitized = userRequest.take(50)
            .replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fff]"), "_")
            .trim('_')
        
        val hash = calculateMD5(userRequest).take(8)
        return "${sanitized}_$hash"
    }
    
    private fun getNextVersion(scriptId: String): Int {
        return scriptIndex.scripts[scriptId]?.version?.plus(1) ?: 1
    }
    
    private fun generateAutoTags(userRequest: String, script: String): List<String> {
        val tags = mutableListOf<String>()
        
        // 基于用户请求的关键词
        val requestKeywords = listOf("UI", "文件", "网络", "系统", "数据", "图片", "视频", "音频", "文档")
        requestKeywords.forEach { keyword ->
            if (userRequest.contains(keyword, ignoreCase = true)) {
                tags.add(keyword.lowercase())
            }
        }
        
        // 基于脚本内容的技术标签
        if (script.contains("fetch") || script.contains("XMLHttpRequest")) tags.add("网络")
        if (script.contains("file") || script.contains("File")) tags.add("文件")
        if (script.contains("click") || script.contains("touch")) tags.add("UI")
        if (script.contains("setTimeout") || script.contains("setInterval")) tags.add("定时")
        
        return tags.distinct()
    }
    
    private fun categorizeScript(userRequest: String, script: String): String {
        return when {
            userRequest.contains("UI", ignoreCase = true) || 
            script.contains("click") || script.contains("touch") -> "ui_automation"
            
            userRequest.contains("文件", ignoreCase = true) || 
            script.contains("file") -> "file_operation"
            
            userRequest.contains("网络", ignoreCase = true) || 
            script.contains("fetch") -> "network"
            
            userRequest.contains("数据", ignoreCase = true) -> "data_processing"
            
            userRequest.contains("系统", ignoreCase = true) -> "system"
            
            else -> "general"
        }
    }
    
    private fun generateAutoDescription(userRequest: String): String {
        return "根据用户需求自动生成: ${userRequest.take(100)}"
    }
    
    private fun calculateMD5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
    
    private fun getScriptsDirectory(): File {
        val context = this.context ?: throw IllegalStateException("Context not initialized")
        val scriptsDir = File(context.filesDir, SCRIPTS_DIR)
        if (!scriptsDir.exists()) {
            scriptsDir.mkdirs()
        }
        return scriptsDir
    }
    
    private fun ensureDirectories() {
        val scriptsDir = getScriptsDirectory()
        val versionsDir = File(scriptsDir, VERSIONS_DIR)
        if (!versionsDir.exists()) {
            versionsDir.mkdirs()
        }
    }
    
    private fun updateIndex(scriptId: String, metadata: ScriptMetadata) {
        scriptIndex.scripts[scriptId] = metadata
        
        // 更新分类索引
        scriptIndex.categories.computeIfAbsent(metadata.category) { mutableListOf() }
            .apply { if (!contains(scriptId)) add(scriptId) }
        
        // 更新标签索引
        metadata.tags.forEach { tag ->
            scriptIndex.tags.computeIfAbsent(tag) { mutableListOf() }
                .apply { if (!contains(scriptId)) add(scriptId) }
        }
        
        scriptIndex.lastUpdated = System.currentTimeMillis()
    }
    
    private fun loadIndex() {
        try {
            val context = this.context ?: return
            val indexFile = File(context.filesDir, "$SCRIPTS_DIR/$INDEX_FILE")
            if (indexFile.exists()) {
                val indexContent = indexFile.readText()
                scriptIndex = json.decodeFromString(ScriptIndex.serializer(), indexContent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载索引失败", e)
            scriptIndex = ScriptIndex()
        }
    }
    
    private fun saveIndex() {
        try {
            val context = this.context ?: return
            val indexFile = File(context.filesDir, "$SCRIPTS_DIR/$INDEX_FILE")
            val indexContent = json.encodeToString(ScriptIndex.serializer(), scriptIndex)
            indexFile.writeText(indexContent)
        } catch (e: Exception) {
            Log.e(TAG, "保存索引失败", e)
        }
    }
    
    private fun saveVersionInfo(scriptId: String, version: Int, timestamp: Long, quality: Float, changes: String, filePath: String) {
        try {
            val versionsDir = File(getScriptsDirectory(), VERSIONS_DIR)
            val versionFile = File(versionsDir, "$scriptId.json")
            
            val versions = if (versionFile.exists()) {
                val content = versionFile.readText()
                json.decodeFromString<MutableList<ScriptVersion>>(content)
            } else {
                mutableListOf()
            }
            
            versions.add(ScriptVersion(version, timestamp, quality, changes, filePath))
            
            // 限制版本数量
            if (versions.size > MAX_VERSIONS_PER_SCRIPT) {
                versions.removeAt(0)
            }
            
            versionFile.writeText(json.encodeToString(versions))
            
        } catch (e: Exception) {
            Log.e(TAG, "保存版本信息失败", e)
        }
    }
    
    private fun loadVersionInfo(scriptId: String, version: Int): ScriptVersion? {
        try {
            val versions = loadAllVersions(scriptId)
            return versions.find { it.version == version }
        } catch (e: Exception) {
            Log.e(TAG, "加载版本信息失败", e)
            return null
        }
    }
    
    private fun loadAllVersions(scriptId: String): List<ScriptVersion> {
        try {
            val versionsDir = File(getScriptsDirectory(), VERSIONS_DIR)
            val versionFile = File(versionsDir, "$scriptId.json")
            
            if (!versionFile.exists()) {
                return emptyList()
            }
            
            val content = versionFile.readText()
            return json.decodeFromString(content)
            
        } catch (e: Exception) {
            Log.e(TAG, "加载所有版本失败", e)
            return emptyList()
        }
    }
    
    private fun cleanupOldVersions(scriptId: String) {
        try {
            val versions = loadAllVersions(scriptId)
            if (versions.size > MAX_VERSIONS_PER_SCRIPT) {
                val versionsToDelete = versions.sortedBy { it.timestamp }
                    .take(versions.size - MAX_VERSIONS_PER_SCRIPT)
                
                versionsToDelete.forEach { version ->
                    val file = File(version.filePath)
                    if (file.exists()) {
                        file.delete()
                        Log.d(TAG, "删除旧版本文件: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理旧版本失败", e)
        }
    }
    
    private fun initGitRepository(directory: File): Boolean {
        return try {
            val commands = listOf(
                "git init",
                "git config user.name \"Operit Agent\"",
                "git config user.email \"agent@operit.ai\"",
                "git add .",
                "git commit -m \"Initial commit\""
            )
            
            commands.all { command ->
                executeCommand(command, directory).first
            }
        } catch (e: Exception) {
            Log.e(TAG, "初始化Git仓库失败", e)
            false
        }
    }
    
    private fun executeCommand(command: String, workingDir: File): Pair<Boolean, String> {
        return try {
            val process = ProcessBuilder(*command.split(" ").toTypedArray())
                .directory(workingDir)
                .redirectErrorStream(true)
                .start()
            
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            Pair(exitCode == 0, output)
        } catch (e: Exception) {
            Pair(false, e.message ?: "Unknown error")
        }
    }
}