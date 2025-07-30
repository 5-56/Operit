package com.ai.assistance.operit.util

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * 通用错误处理工具类
 * 提供统一的错误处理、重试机制和用户友好的错误信息
 */
object ErrorHandler {
    private const val TAG = "ErrorHandler"
    
    /**
     * 错误类型枚举
     */
    enum class ErrorType(val displayName: String) {
        NETWORK("网络连接错误"),
        TIMEOUT("请求超时"),
        AUTHENTICATION("认证失败"),
        PERMISSION("权限不足"),
        VALIDATION("数据验证失败"),
        SERVER("服务器错误"),
        UNKNOWN("未知错误")
    }
    
    /**
     * 错误信息数据类
     */
    data class ErrorInfo(
        val type: ErrorType,
        val message: String,
        val userMessage: String,
        val suggestions: List<String> = emptyList(),
        val isRetryable: Boolean = false
    )
    
    /**
     * 重试配置
     */
    data class RetryConfig(
        val maxRetries: Int = 3,
        val initialDelayMs: Long = 1000L,
        val maxDelayMs: Long = 10000L,
        val backoffMultiplier: Double = 2.0
    )
    
    /**
     * 分析异常并返回错误信息
     */
    fun analyzeError(throwable: Throwable): ErrorInfo {
        return when (throwable) {
            is UnknownHostException -> ErrorInfo(
                type = ErrorType.NETWORK,
                message = "无法连接到服务器：${throwable.message}",
                userMessage = "网络连接失败，请检查网络设置",
                suggestions = listOf(
                    "检查网络连接是否正常",
                    "确认服务器地址是否正确",
                    "尝试切换网络环境"
                ),
                isRetryable = true
            )
            
            is SocketTimeoutException -> ErrorInfo(
                type = ErrorType.TIMEOUT,
                message = "请求超时：${throwable.message}",
                userMessage = "请求超时，请重试",
                suggestions = listOf(
                    "检查网络连接速度",
                    "稍后重试",
                    "联系技术支持"
                ),
                isRetryable = true
            )
            
            is SSLException -> ErrorInfo(
                type = ErrorType.NETWORK,
                message = "SSL连接错误：${throwable.message}",
                userMessage = "安全连接失败",
                suggestions = listOf(
                    "检查系统时间是否正确",
                    "更新应用到最新版本",
                    "联系技术支持"
                ),
                isRetryable = false
            )
            
            is IOException -> {
                val message = throwable.message ?: "IO操作失败"
                when {
                    message.contains("401", ignoreCase = true) -> ErrorInfo(
                        type = ErrorType.AUTHENTICATION,
                        message = "认证失败：$message",
                        userMessage = "API密钥无效或已过期",
                        suggestions = listOf(
                            "检查API密钥是否正确",
                            "确认API密钥是否已过期",
                            "重新生成API密钥"
                        ),
                        isRetryable = false
                    )
                    
                    message.contains("403", ignoreCase = true) -> ErrorInfo(
                        type = ErrorType.PERMISSION,
                        message = "权限不足：$message",
                        userMessage = "访问被拒绝，权限不足",
                        suggestions = listOf(
                            "检查API权限设置",
                            "确认账户配额是否充足",
                            "联系服务提供商"
                        ),
                        isRetryable = false
                    )
                    
                    message.contains("429", ignoreCase = true) -> ErrorInfo(
                        type = ErrorType.SERVER,
                        message = "请求频率过高：$message",
                        userMessage = "请求过于频繁，请稍后重试",
                        suggestions = listOf(
                            "稍等片刻后重试",
                            "降低请求频率",
                            "升级API配额"
                        ),
                        isRetryable = true
                    )
                    
                    message.contains("5", ignoreCase = true) -> ErrorInfo(
                        type = ErrorType.SERVER,
                        message = "服务器错误：$message",
                        userMessage = "服务器暂时不可用",
                        suggestions = listOf(
                            "稍后重试",
                            "检查服务状态",
                            "联系技术支持"
                        ),
                        isRetryable = true
                    )
                    
                    else -> ErrorInfo(
                        type = ErrorType.NETWORK,
                        message = "网络IO错误：$message",
                        userMessage = "网络操作失败",
                        suggestions = listOf(
                            "检查网络连接",
                            "重试操作",
                            "联系技术支持"
                        ),
                        isRetryable = true
                    )
                }
            }
            
            is CancellationException -> ErrorInfo(
                type = ErrorType.UNKNOWN,
                message = "操作已取消：${throwable.message}",
                userMessage = "操作已被取消",
                suggestions = listOf("如需继续，请重新执行操作"),
                isRetryable = false
            )
            
            is IllegalArgumentException -> ErrorInfo(
                type = ErrorType.VALIDATION,
                message = "参数错误：${throwable.message}",
                userMessage = "输入参数有误",
                suggestions = listOf(
                    "检查输入参数是否正确",
                    "确认数据格式是否符合要求",
                    "查看相关文档"
                ),
                isRetryable = false
            )
            
            else -> ErrorInfo(
                type = ErrorType.UNKNOWN,
                message = "未知错误：${throwable.message}",
                userMessage = "发生未知错误",
                suggestions = listOf(
                    "重试操作",
                    "重启应用",
                    "联系技术支持"
                ),
                isRetryable = true
            )
        }
    }
    
    /**
     * 执行带重试机制的操作
     */
    suspend fun <T> executeWithRetry(
        config: RetryConfig = RetryConfig(),
        operation: suspend () -> T
    ): Result<T> {
        var lastException: Exception? = null
        var currentDelay = config.initialDelayMs
        
        repeat(config.maxRetries + 1) { attempt ->
            try {
                return Result.success(operation())
            } catch (e: CancellationException) {
                // 协程取消不重试
                throw e
            } catch (e: Exception) {
                lastException = e
                val errorInfo = analyzeError(e)
                
                Log.w(TAG, "操作失败 (尝试 ${attempt + 1}/${config.maxRetries + 1}): ${errorInfo.message}")
                
                // 检查是否可重试
                if (!errorInfo.isRetryable || attempt >= config.maxRetries) {
                    break
                }
                
                // 等待后重试
                if (attempt < config.maxRetries) {
                    delay(currentDelay)
                    currentDelay = (currentDelay * config.backoffMultiplier).toLong()
                        .coerceAtMost(config.maxDelayMs)
                }
            }
        }
        
        return Result.failure(lastException ?: Exception("操作失败"))
    }
    
    /**
     * 记录错误信息
     */
    fun logError(tag: String, errorInfo: ErrorInfo, throwable: Throwable? = null) {
        val logMessage = buildString {
            appendLine("错误类型: ${errorInfo.type.displayName}")
            appendLine("错误信息: ${errorInfo.message}")
            appendLine("用户信息: ${errorInfo.userMessage}")
            if (errorInfo.suggestions.isNotEmpty()) {
                appendLine("建议: ${errorInfo.suggestions.joinToString(", ")}")
            }
            appendLine("可重试: ${if (errorInfo.isRetryable) "是" else "否"}")
        }
        
        if (throwable != null) {
            Log.e(tag, logMessage, throwable)
        } else {
            Log.e(tag, logMessage)
        }
    }
    
    /**
     * 格式化用户友好的错误信息
     */
    fun formatUserError(errorInfo: ErrorInfo): String {
        return buildString {
            append(errorInfo.userMessage)
            if (errorInfo.suggestions.isNotEmpty()) {
                append("\n\n建议解决方案：")
                errorInfo.suggestions.forEachIndexed { index, suggestion ->
                    append("\n${index + 1}. $suggestion")
                }
            }
        }
    }
    
    /**
     * 检查错误是否为网络相关
     */
    fun isNetworkError(throwable: Throwable): Boolean {
        val errorInfo = analyzeError(throwable)
        return errorInfo.type in listOf(ErrorType.NETWORK, ErrorType.TIMEOUT)
    }
    
    /**
     * 检查错误是否可以重试
     */
    fun isRetryableError(throwable: Throwable): Boolean {
        return analyzeError(throwable).isRetryable
    }
    
    /**
     * 获取简化的错误描述
     */
    fun getSimpleErrorMessage(throwable: Throwable): String {
        return analyzeError(throwable).userMessage
    }
}