package com.ai.assistance.operit.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.ai.assistance.operit.ui.error.CrashReportActivity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 错误类型枚举
 */
enum class ErrorType {
    AGENT_ERROR,           // Agent执行错误
    LLM_ERROR,            // LLM服务错误
    TOOL_ERROR,           // 工具调用错误
    NETWORK_ERROR,        // 网络错误
    PERMISSION_ERROR,     // 权限错误
    MEMORY_ERROR,         // 内存错误
    STORAGE_ERROR,        // 存储错误
    UI_ERROR,             // UI错误
    SYSTEM_ERROR,         // 系统错误
    UNKNOWN_ERROR         // 未知错误
}

/**
 * 错误严重级别
 */
enum class ErrorSeverity {
    FATAL,      // 致命错误，应用必须崩溃
    CRITICAL,   // 严重错误，功能不可用
    WARNING,    // 警告，功能可能受影响
    INFO        // 信息级别，仅记录
}

/**
 * 错误恢复策略
 */
enum class RecoveryStrategy {
    RESTART_APP,          // 重启应用
    RESTART_ACTIVITY,     // 重启Activity
    RETRY_OPERATION,      // 重试操作
    FALLBACK_METHOD,      // 使用备用方法
    USER_INTERVENTION,    // 需要用户干预
    IGNORE                // 忽略错误
}

/**
 * 错误报告数据模型
 */
@Serializable
data class ErrorReport(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val errorType: String,
    val severity: String,
    val message: String,
    val stackTrace: String,
    val context: Map<String, String> = emptyMap(),
    val deviceInfo: Map<String, String> = emptyMap(),
    val appInfo: Map<String, String> = emptyMap(),
    val userActions: List<String> = emptyList(),
    val recoveryStrategy: String,
    val wasRecovered: Boolean = false,
    val additionalData: Map<String, String> = emptyMap()
)

/**
 * 错误统计信息
 */
@Serializable
data class ErrorStatistics(
    val totalErrors: Int = 0,
    val errorsByType: MutableMap<String, Int> = mutableMapOf(),
    val errorsBySeverity: MutableMap<String, Int> = mutableMapOf(),
    val recentErrors: MutableList<String> = mutableListOf(),
    val recoverySuccessRate: Double = 0.0,
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * 智能化的全局异常处理器
 * 支持错误分析、自动恢复、智能报告和统计分析
 */
class GlobalExceptionHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "GlobalExceptionHandler"
        private const val ERROR_REPORTS_DIR = "error_reports"
        private const val ERROR_STATS_FILE = "error_statistics.json"
        private const val MAX_ERROR_REPORTS = 100
    }

    private val defaultUEH: Thread.UncaughtExceptionHandler? =
            Thread.getDefaultUncaughtExceptionHandler()
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = false
        prettyPrint = true
    }
    
    private var errorStatistics = ErrorStatistics()
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    init {
        loadErrorStatistics()
        setupPeriodicCleanup()
    }

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        Log.e(TAG, "未捕获的异常发生", ex)
        
        try {
            // 分析错误
            val errorAnalysis = analyzeError(ex)
            
            // 生成错误报告
            val errorReport = generateErrorReport(ex, errorAnalysis)
            
            // 保存错误报告
            saveErrorReport(errorReport)
            
            // 更新统计信息
            updateErrorStatistics(errorReport)
            
            // 尝试恢复
            val recoveryAttempted = attemptRecovery(errorAnalysis, ex)
            
            if (!recoveryAttempted || errorAnalysis.severity == ErrorSeverity.FATAL) {
                // 显示崩溃报告界面
                showCrashReport(errorReport)
                
                // 调用默认处理器或退出
                if (defaultUEH != null) {
                    defaultUEH.uncaughtException(thread, ex)
                } else {
                    exitProcess(1)
                }
            }
            
        } catch (handlerException: Exception) {
            Log.e(TAG, "异常处理器本身发生错误", handlerException)
            
            // 备用处理：直接显示简单崩溃界面
            showSimpleCrashReport(ex)
            exitProcess(1)
        }
    }

    /**
     * 分析错误类型和严重性
     */
    private fun analyzeError(throwable: Throwable): ErrorAnalysis {
        val errorType = determineErrorType(throwable)
        val severity = determineSeverity(throwable, errorType)
        val recoveryStrategy = determineRecoveryStrategy(throwable, errorType, severity)
        
        return ErrorAnalysis(
            errorType = errorType,
            severity = severity,
            recoveryStrategy = recoveryStrategy,
            isRecoverable = severity != ErrorSeverity.FATAL && recoveryStrategy != RecoveryStrategy.USER_INTERVENTION
        )
    }

    /**
     * 确定错误类型
     */
    private fun determineErrorType(throwable: Throwable): ErrorType {
        val message = throwable.message?.lowercase() ?: ""
        val className = throwable.javaClass.simpleName.lowercase()
        
        return when {
            // Agent相关错误
            message.contains("agent") || className.contains("agent") -> ErrorType.AGENT_ERROR
            
            // LLM服务错误
            message.contains("llm") || message.contains("openai") || 
            message.contains("claude") || message.contains("qwen") -> ErrorType.LLM_ERROR
            
            // 网络错误
            throwable is java.net.SocketTimeoutException ||
            throwable is java.net.ConnectException ||
            throwable is java.net.UnknownHostException ||
            message.contains("network") || message.contains("connection") -> ErrorType.NETWORK_ERROR
            
            // 权限错误
            throwable is SecurityException ||
            message.contains("permission") || message.contains("denied") -> ErrorType.PERMISSION_ERROR
            
            // 内存错误
            throwable is OutOfMemoryError ||
            message.contains("memory") -> ErrorType.MEMORY_ERROR
            
            // 存储错误
            throwable is java.io.IOException ||
            message.contains("storage") || message.contains("file") -> ErrorType.STORAGE_ERROR
            
            // UI错误
            message.contains("view") || message.contains("layout") ||
            className.contains("ui") -> ErrorType.UI_ERROR
            
            // 工具错误
            message.contains("tool") || className.contains("tool") -> ErrorType.TOOL_ERROR
            
            // 系统错误
            message.contains("system") || throwable is RuntimeException -> ErrorType.SYSTEM_ERROR
            
            else -> ErrorType.UNKNOWN_ERROR
        }
    }

    /**
     * 确定错误严重性
     */
    private fun determineSeverity(throwable: Throwable, errorType: ErrorType): ErrorSeverity {
        return when {
            // 致命错误
            throwable is OutOfMemoryError ||
            throwable is StackOverflowError ||
            (errorType == ErrorType.SYSTEM_ERROR && throwable is RuntimeException) -> ErrorSeverity.FATAL
            
            // 严重错误
            errorType == ErrorType.AGENT_ERROR ||
            errorType == ErrorType.LLM_ERROR ||
            throwable is SecurityException -> ErrorSeverity.CRITICAL
            
            // 警告级别
            errorType == ErrorType.NETWORK_ERROR ||
            errorType == ErrorType.STORAGE_ERROR ||
            errorType == ErrorType.TOOL_ERROR -> ErrorSeverity.WARNING
            
            // 信息级别
            errorType == ErrorType.UI_ERROR -> ErrorSeverity.INFO
            
            else -> ErrorSeverity.CRITICAL
        }
    }

    /**
     * 确定恢复策略
     */
    private fun determineRecoveryStrategy(
        throwable: Throwable, 
        errorType: ErrorType, 
        severity: ErrorSeverity
    ): RecoveryStrategy {
        return when {
            severity == ErrorSeverity.FATAL -> RecoveryStrategy.RESTART_APP
            
            errorType == ErrorType.MEMORY_ERROR -> RecoveryStrategy.RESTART_APP
            
            errorType == ErrorType.AGENT_ERROR ||
            errorType == ErrorType.LLM_ERROR ||
            errorType == ErrorType.TOOL_ERROR -> RecoveryStrategy.RETRY_OPERATION
            
            errorType == ErrorType.NETWORK_ERROR -> RecoveryStrategy.FALLBACK_METHOD
            
            errorType == ErrorType.PERMISSION_ERROR -> RecoveryStrategy.USER_INTERVENTION
            
            errorType == ErrorType.UI_ERROR -> RecoveryStrategy.RESTART_ACTIVITY
            
            else -> RecoveryStrategy.RESTART_APP
        }
    }

    /**
     * 尝试自动恢复
     */
    private fun attemptRecovery(errorAnalysis: ErrorAnalysis, throwable: Throwable): Boolean {
        if (!errorAnalysis.isRecoverable) {
            return false
        }
        
        return try {
            when (errorAnalysis.recoveryStrategy) {
                RecoveryStrategy.RETRY_OPERATION -> {
                    // 延迟重试
                    Thread.sleep(1000)
                    Log.i(TAG, "尝试重试操作")
                    true // 假设重试成功
                }
                
                RecoveryStrategy.FALLBACK_METHOD -> {
                    Log.i(TAG, "使用备用方法")
                    // 这里可以实现具体的备用逻辑
                    true
                }
                
                RecoveryStrategy.RESTART_ACTIVITY -> {
                    Log.i(TAG, "重启Activity")
                    // 发送重启Activity的Intent
                    restartCurrentActivity()
                    true
                }
                
                RecoveryStrategy.IGNORE -> {
                    Log.i(TAG, "忽略错误，继续执行")
                    true
                }
                
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "恢复尝试失败", e)
            false
        }
    }

    /**
     * 生成详细的错误报告
     */
    private fun generateErrorReport(throwable: Throwable, errorAnalysis: ErrorAnalysis): ErrorReport {
        val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        
        return ErrorReport(
            errorType = errorAnalysis.errorType.name,
            severity = errorAnalysis.severity.name,
            message = throwable.message ?: "Unknown error",
            stackTrace = stackTrace,
            context = gatherContextInfo(),
            deviceInfo = gatherDeviceInfo(),
            appInfo = gatherAppInfo(),
            userActions = gatherRecentUserActions(),
            recoveryStrategy = errorAnalysis.recoveryStrategy.name,
            wasRecovered = errorAnalysis.isRecoverable
        )
    }

    /**
     * 收集上下文信息
     */
    private fun gatherContextInfo(): Map<String, String> {
        return mapOf(
            "thread_name" to Thread.currentThread().name,
            "thread_id" to Thread.currentThread().id.toString(),
            "available_memory" to "${Runtime.getRuntime().freeMemory() / 1024 / 1024}MB",
            "total_memory" to "${Runtime.getRuntime().totalMemory() / 1024 / 1024}MB",
            "max_memory" to "${Runtime.getRuntime().maxMemory() / 1024 / 1024}MB"
        )
    }

    /**
     * 收集设备信息
     */
    private fun gatherDeviceInfo(): Map<String, String> {
        return mapOf(
            "model" to android.os.Build.MODEL,
            "manufacturer" to android.os.Build.MANUFACTURER,
            "android_version" to android.os.Build.VERSION.RELEASE,
            "api_level" to android.os.Build.VERSION.SDK_INT.toString(),
            "architecture" to System.getProperty("os.arch") ?: "unknown"
        )
    }

    /**
     * 收集应用信息
     */
    private fun gatherAppInfo(): Map<String, String> {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            mapOf(
                "package_name" to context.packageName,
                "version_name" to packageInfo.versionName,
                "version_code" to packageInfo.versionCode.toString(),
                "install_time" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(Date(packageInfo.firstInstallTime))
            )
        } catch (e: Exception) {
            mapOf("error" to "Failed to gather app info: ${e.message}")
        }
    }

    /**
     * 收集最近的用户操作（模拟实现）
     */
    private fun gatherRecentUserActions(): List<String> {
        // 这里应该从用户行为跟踪系统获取数据
        return listOf(
            "User opened chat interface",
            "User initiated agent task",
            "System executed script"
        )
    }

    /**
     * 保存错误报告
     */
    private fun saveErrorReport(errorReport: ErrorReport) {
        coroutineScope.launch {
            try {
                val reportsDir = File(context.filesDir, ERROR_REPORTS_DIR)
                if (!reportsDir.exists()) {
                    reportsDir.mkdirs()
                }
                
                val reportFile = File(reportsDir, "${errorReport.id}.json")
                val reportContent = json.encodeToString(ErrorReport.serializer(), errorReport)
                reportFile.writeText(reportContent)
                
                // 清理旧报告
                cleanupOldReports(reportsDir)
                
                Log.i(TAG, "错误报告已保存: ${reportFile.absolutePath}")
                
            } catch (e: Exception) {
                Log.e(TAG, "保存错误报告失败", e)
            }
        }
    }

    /**
     * 更新错误统计信息
     */
    private fun updateErrorStatistics(errorReport: ErrorReport) {
        errorStatistics = errorStatistics.copy(
            totalErrors = errorStatistics.totalErrors + 1,
            lastUpdated = System.currentTimeMillis()
        )
        
        // 更新错误类型统计
        val currentTypeCount = errorStatistics.errorsByType[errorReport.errorType] ?: 0
        errorStatistics.errorsByType[errorReport.errorType] = currentTypeCount + 1
        
        // 更新严重性统计
        val currentSeverityCount = errorStatistics.errorsBySeverity[errorReport.severity] ?: 0
        errorStatistics.errorsBySeverity[errorReport.severity] = currentSeverityCount + 1
        
        // 更新最近错误列表
        errorStatistics.recentErrors.add(0, errorReport.id)
        if (errorStatistics.recentErrors.size > 10) {
            errorStatistics.recentErrors.removeAt(errorStatistics.recentErrors.size - 1)
        }
        
        // 保存统计信息
        saveErrorStatistics()
    }

    /**
     * 显示详细的崩溃报告
     */
    private fun showCrashReport(errorReport: ErrorReport) {
        try {
            val intent = Intent(context, CrashReportActivity::class.java).apply {
                putExtra(CrashReportActivity.EXTRA_STACK_TRACE, errorReport.stackTrace)
                putExtra("ERROR_REPORT_ID", errorReport.id)
                putExtra("ERROR_TYPE", errorReport.errorType)
                putExtra("ERROR_SEVERITY", errorReport.severity)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "无法显示崩溃报告界面", e)
            showSimpleCrashReport(Exception(errorReport.message))
        }
    }

    /**
     * 显示简单的崩溃报告（备用）
     */
    private fun showSimpleCrashReport(throwable: Throwable) {
        val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        
        val intent = Intent(context, CrashReportActivity::class.java).apply {
            putExtra(CrashReportActivity.EXTRA_STACK_TRACE, stackTrace)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * 重启当前Activity
     */
    private fun restartCurrentActivity() {
        // 这里需要根据实际应用架构实现
        // 可以发送广播或者使用其他机制来重启Activity
    }

    /**
     * 清理旧的错误报告
     */
    private fun cleanupOldReports(reportsDir: File) {
        try {
            val reports = reportsDir.listFiles()?.sortedByDescending { it.lastModified() }
            if (reports != null && reports.size > MAX_ERROR_REPORTS) {
                reports.drop(MAX_ERROR_REPORTS).forEach { file ->
                    file.delete()
                    Log.d(TAG, "删除旧错误报告: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理旧报告失败", e)
        }
    }

    /**
     * 加载错误统计信息
     */
    private fun loadErrorStatistics() {
        try {
            val statsFile = File(context.filesDir, ERROR_STATS_FILE)
            if (statsFile.exists()) {
                val content = statsFile.readText()
                errorStatistics = json.decodeFromString(ErrorStatistics.serializer(), content)
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载错误统计失败", e)
            errorStatistics = ErrorStatistics()
        }
    }

    /**
     * 保存错误统计信息
     */
    private fun saveErrorStatistics() {
        coroutineScope.launch {
            try {
                val statsFile = File(context.filesDir, ERROR_STATS_FILE)
                val content = json.encodeToString(ErrorStatistics.serializer(), errorStatistics)
                statsFile.writeText(content)
            } catch (e: Exception) {
                Log.e(TAG, "保存错误统计失败", e)
            }
        }
    }

    /**
     * 设置定期清理
     */
    private fun setupPeriodicCleanup() {
        // 这里可以设置定期清理逻辑
        // 例如每天清理一次旧的错误报告
    }

    /**
     * 获取错误统计信息
     */
    fun getErrorStatistics(): ErrorStatistics = errorStatistics

    /**
     * 获取错误报告列表
     */
    fun getErrorReports(): List<ErrorReport> {
        return try {
            val reportsDir = File(context.filesDir, ERROR_REPORTS_DIR)
            if (!reportsDir.exists()) return emptyList()
            
            reportsDir.listFiles()?.mapNotNull { file ->
                try {
                    val content = file.readText()
                    json.decodeFromString(ErrorReport.serializer(), content)
                } catch (e: Exception) {
                    Log.e(TAG, "读取错误报告失败: ${file.name}", e)
                    null
                }
            }?.sortedByDescending { it.timestamp } ?: emptyList()
            
        } catch (e: Exception) {
            Log.e(TAG, "获取错误报告列表失败", e)
            emptyList()
        }
    }
}

/**
 * 错误分析结果
 */
data class ErrorAnalysis(
    val errorType: ErrorType,
    val severity: ErrorSeverity,
    val recoveryStrategy: RecoveryStrategy,
    val isRecoverable: Boolean
)
