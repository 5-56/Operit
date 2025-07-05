package com.ai.assistance.operit.auraflow.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * 用户偏好配置
 */
@Serializable
data class UserPreferences(
    val skipPermissionCheck: Boolean = false,
    val autoGrantPermissions: Boolean = false,
    val preferredPermissionLevel: String = "NORMAL", // MINIMAL, NORMAL, FULL
    val showPermissionRationale: Boolean = true,
    val enableHapticFeedback: Boolean = true,
    val language: String = "zh-CN"
)

/**
 * AI大脑配置
 */
@Serializable
data class AIBrainConfig(
    val serverUrl: String = "",
    val apiKey: String = "",
    val model: String = "gpt-3.5-turbo",
    val maxTokens: Int = 4096,
    val temperature: Float = 0.7f,
    val timeout: Long = 30000,
    val retryAttempts: Int = 3,
    val enableStreaming: Boolean = true
)

/**
 * 浮动窗口配置
 */
@Serializable
data class FloatingWindowConfig(
    val enabled: Boolean = false,
    val defaultMode: String = "FULL", // FULL, COMPACT, MINI
    val position: WindowPosition = WindowPosition(100, 100),
    val autoHide: Boolean = false,
    val autoHideDelay: Long = 5000,
    val enableDragging: Boolean = true,
    val snapToEdge: Boolean = true,
    val showOnStartup: Boolean = false
)

/**
 * 窗口位置
 */
@Serializable
data class WindowPosition(
    val x: Int,
    val y: Int
)

/**
 * 性能配置
 */
@Serializable
data class PerformanceConfig(
    val screenshotQuality: Int = 80, // 0-100
    val screenshotMaxWidth: Int = 1080,
    val screenshotMaxHeight: Int = 1920,
    val actionExecutionDelay: Long = 100,
    val enableMemoryOptimization: Boolean = true,
    val enableBatteryOptimization: Boolean = true,
    val maxConcurrentOperations: Int = 3,
    val cacheSize: Long = 50 * 1024 * 1024 // 50MB
)

/**
 * UI调试配置
 */
@Serializable
data class UIDebugConfig(
    val showElementBounds: Boolean = true,
    val highlightClickableElements: Boolean = true,
    val showElementHierarchy: Boolean = true,
    val enableElementSearch: Boolean = true,
    val maxHierarchyDepth: Int = 10,
    val autoRefreshInterval: Long = 1000
)

/**
 * 完整的应用配置
 */
@Serializable
data class AuraFlowConfiguration(
    val version: String = "1.0.0",
    val lastModified: Long = System.currentTimeMillis(),
    val userPreferences: UserPreferences = UserPreferences(),
    val aiBrainConfig: AIBrainConfig = AIBrainConfig(),
    val floatingWindowConfig: FloatingWindowConfig = FloatingWindowConfig(),
    val performanceConfig: PerformanceConfig = PerformanceConfig(),
    val uiDebugConfig: UIDebugConfig = UIDebugConfig()
)

/**
 * 配置管理器
 * 负责所有配置的持久化存储和管理
 */
class ConfigurationManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ConfigurationManager"
        private const val PREFS_NAME = "auraflow_config"
        private const val CONFIG_FILE_NAME = "auraflow_configuration.json"
        private const val BACKUP_DIR = "config_backups"
        
        @Volatile
        private var INSTANCE: ConfigurationManager? = null
        
        fun getInstance(context: Context): ConfigurationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ConfigurationManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    // 配置状态流
    private val _configuration = MutableStateFlow(loadConfiguration())
    val configuration: StateFlow<AuraFlowConfiguration> = _configuration.asStateFlow()
    
    // 各个配置模块的流
    val userPreferences: StateFlow<UserPreferences> = _configuration
        .map { it.userPreferences }
        .stateIn(
            scope = kotlinx.coroutines.GlobalScope,
            started = SharingStarted.Eagerly,
            initialValue = UserPreferences()
        )
    
    val aiBrainConfig: StateFlow<AIBrainConfig> = _configuration
        .map { it.aiBrainConfig }
        .stateIn(
            scope = kotlinx.coroutines.GlobalScope,
            started = SharingStarted.Eagerly,
            initialValue = AIBrainConfig()
        )
    
    val floatingWindowConfig: StateFlow<FloatingWindowConfig> = _configuration
        .map { it.floatingWindowConfig }
        .stateIn(
            scope = kotlinx.coroutines.GlobalScope,
            started = SharingStarted.Eagerly,
            initialValue = FloatingWindowConfig()
        )
    
    val performanceConfig: StateFlow<PerformanceConfig> = _configuration
        .map { it.performanceConfig }
        .stateIn(
            scope = kotlinx.coroutines.GlobalScope,
            started = SharingStarted.Eagerly,
            initialValue = PerformanceConfig()
        )
    
    val uiDebugConfig: StateFlow<UIDebugConfig> = _configuration
        .map { it.uiDebugConfig }
        .stateIn(
            scope = kotlinx.coroutines.GlobalScope,
            started = SharingStarted.Eagerly,
            initialValue = UIDebugConfig()
        )
    
    init {
        Log.d(TAG, "配置管理器初始化")
        
        // 监听配置变化并自动保存
        kotlinx.coroutines.GlobalScope.launch {
            _configuration.collect { config ->
                saveConfigurationToFile(config)
            }
        }
    }
    
    /**
     * 加载配置
     */
    private fun loadConfiguration(): AuraFlowConfiguration {
        return try {
            // 首先尝试从文件加载
            val configFile = File(context.filesDir, CONFIG_FILE_NAME)
            if (configFile.exists()) {
                val configJson = configFile.readText()
                json.decodeFromString<AuraFlowConfiguration>(configJson)
            } else {
                // 文件不存在，尝试从SharedPreferences迁移
                migrateFromSharedPreferences()
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载配置失败，使用默认配置", e)
            AuraFlowConfiguration()
        }
    }
    
    /**
     * 从SharedPreferences迁移旧配置
     */
    private fun migrateFromSharedPreferences(): AuraFlowConfiguration {
        Log.d(TAG, "从SharedPreferences迁移配置")
        
        return AuraFlowConfiguration(
            userPreferences = UserPreferences(
                skipPermissionCheck = sharedPreferences.getBoolean("skip_permission_check", false),
                autoGrantPermissions = sharedPreferences.getBoolean("auto_grant_permissions", false),
                enableHapticFeedback = sharedPreferences.getBoolean("enable_haptic_feedback", true),
                language = sharedPreferences.getString("language", "zh-CN") ?: "zh-CN"
            ),
            aiBrainConfig = AIBrainConfig(
                serverUrl = sharedPreferences.getString("ai_server_url", "") ?: "",
                apiKey = sharedPreferences.getString("ai_api_key", "") ?: "",
                model = sharedPreferences.getString("ai_model", "gpt-3.5-turbo") ?: "gpt-3.5-turbo",
                timeout = sharedPreferences.getLong("ai_timeout", 30000)
            ),
            floatingWindowConfig = FloatingWindowConfig(
                enabled = sharedPreferences.getBoolean("floating_window_enabled", false),
                position = WindowPosition(
                    x = sharedPreferences.getInt("floating_window_x", 100),
                    y = sharedPreferences.getInt("floating_window_y", 100)
                )
            ),
            performanceConfig = PerformanceConfig(
                screenshotQuality = sharedPreferences.getInt("screenshot_quality", 80),
                actionExecutionDelay = sharedPreferences.getLong("action_execution_delay", 100)
            )
        )
    }
    
    /**
     * 保存配置到文件
     */
    private fun saveConfigurationToFile(config: AuraFlowConfiguration) {
        try {
            val configFile = File(context.filesDir, CONFIG_FILE_NAME)
            val updatedConfig = config.copy(
                lastModified = System.currentTimeMillis()
            )
            
            val configJson = json.encodeToString(updatedConfig)
            configFile.writeText(configJson)
            
            Log.d(TAG, "配置已保存到文件")
        } catch (e: Exception) {
            Log.e(TAG, "保存配置到文件失败", e)
        }
    }
    
    /**
     * 更新用户偏好
     */
    fun updateUserPreferences(preferences: UserPreferences) {
        _configuration.value = _configuration.value.copy(userPreferences = preferences)
        Log.d(TAG, "用户偏好已更新")
    }
    
    /**
     * 更新AI大脑配置
     */
    fun updateAIBrainConfig(config: AIBrainConfig) {
        _configuration.value = _configuration.value.copy(aiBrainConfig = config)
        Log.d(TAG, "AI大脑配置已更新")
    }
    
    /**
     * 更新浮动窗口配置
     */
    fun updateFloatingWindowConfig(config: FloatingWindowConfig) {
        _configuration.value = _configuration.value.copy(floatingWindowConfig = config)
        Log.d(TAG, "浮动窗口配置已更新")
    }
    
    /**
     * 更新性能配置
     */
    fun updatePerformanceConfig(config: PerformanceConfig) {
        _configuration.value = _configuration.value.copy(performanceConfig = config)
        Log.d(TAG, "性能配置已更新")
    }
    
    /**
     * 更新UI调试配置
     */
    fun updateUIDebugConfig(config: UIDebugConfig) {
        _configuration.value = _configuration.value.copy(uiDebugConfig = config)
        Log.d(TAG, "UI调试配置已更新")
    }
    
    /**
     * 保存浮动窗口位置
     */
    fun saveFloatingWindowPosition(x: Int, y: Int) {
        val currentConfig = _configuration.value.floatingWindowConfig
        val updatedConfig = currentConfig.copy(position = WindowPosition(x, y))
        updateFloatingWindowConfig(updatedConfig)
    }
    
    /**
     * 创建配置备份
     */
    fun createBackup(): String? {
        return try {
            val backupDir = File(context.filesDir, BACKUP_DIR)
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(backupDir, "config_backup_$timestamp.json")
            
            val configJson = json.encodeToString(_configuration.value)
            backupFile.writeText(configJson)
            
            Log.d(TAG, "配置备份已创建: ${backupFile.name}")
            backupFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "创建配置备份失败", e)
            null
        }
    }
    
    /**
     * 从备份恢复配置
     */
    fun restoreFromBackup(backupPath: String): Boolean {
        return try {
            val backupFile = File(backupPath)
            if (!backupFile.exists()) {
                Log.e(TAG, "备份文件不存在: $backupPath")
                return false
            }
            
            val configJson = backupFile.readText()
            val restoredConfig = json.decodeFromString<AuraFlowConfiguration>(configJson)
            
            _configuration.value = restoredConfig
            Log.d(TAG, "配置已从备份恢复: ${backupFile.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "从备份恢复配置失败", e)
            false
        }
    }
    
    /**
     * 导出配置到外部存储
     */
    fun exportConfiguration(exportPath: String): Boolean {
        return try {
            val exportFile = File(exportPath)
            val configJson = json.encodeToString(_configuration.value)
            
            FileOutputStream(exportFile).use { output ->
                output.write(configJson.toByteArray())
            }
            
            Log.d(TAG, "配置已导出到: $exportPath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "导出配置失败", e)
            false
        }
    }
    
    /**
     * 从外部文件导入配置
     */
    fun importConfiguration(importPath: String): Boolean {
        return try {
            val importFile = File(importPath)
            if (!importFile.exists()) {
                Log.e(TAG, "导入文件不存在: $importPath")
                return false
            }
            
            val configJson = FileInputStream(importFile).use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
            
            val importedConfig = json.decodeFromString<AuraFlowConfiguration>(configJson)
            _configuration.value = importedConfig
            
            Log.d(TAG, "配置已从文件导入: $importPath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "导入配置失败", e)
            false
        }
    }
    
    /**
     * 重置为默认配置
     */
    fun resetToDefaults() {
        _configuration.value = AuraFlowConfiguration()
        Log.d(TAG, "配置已重置为默认值")
    }
    
    /**
     * 获取配置备份列表
     */
    fun getBackupList(): List<String> {
        return try {
            val backupDir = File(context.filesDir, BACKUP_DIR)
            if (!backupDir.exists()) {
                return emptyList()
            }
            
            backupDir.listFiles { file ->
                file.isFile && file.name.startsWith("config_backup_") && file.name.endsWith(".json")
            }?.map { it.absolutePath }?.sorted()?.reversed() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "获取备份列表失败", e)
            emptyList()
        }
    }
    
    /**
     * 删除旧备份（保留最新的5个）
     */
    fun cleanupOldBackups() {
        try {
            val backups = getBackupList()
            if (backups.size > 5) {
                backups.drop(5).forEach { backupPath ->
                    val file = File(backupPath)
                    if (file.delete()) {
                        Log.d(TAG, "已删除旧备份: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理旧备份失败", e)
        }
    }
    
    /**
     * 验证配置完整性
     */
    fun validateConfiguration(): Boolean {
        return try {
            val config = _configuration.value
            
            // 检查必要的配置项
            val isValid = config.userPreferences.language.isNotEmpty() &&
                         config.performanceConfig.screenshotQuality in 1..100 &&
                         config.performanceConfig.actionExecutionDelay >= 0 &&
                         config.floatingWindowConfig.position.x >= 0 &&
                         config.floatingWindowConfig.position.y >= 0
            
            Log.d(TAG, "配置验证结果: $isValid")
            isValid
        } catch (e: Exception) {
            Log.e(TAG, "配置验证失败", e)
            false
        }
    }
    
    /**
     * 获取配置统计信息
     */
    fun getConfigurationStats(): Map<String, Any> {
        val config = _configuration.value
        return mapOf(
            "version" to config.version,
            "lastModified" to config.lastModified,
            "configSize" to json.encodeToString(config).length,
            "backupCount" to getBackupList().size,
            "isValid" to validateConfiguration()
        )
    }
}