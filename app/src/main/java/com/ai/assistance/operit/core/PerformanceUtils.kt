package com.ai.assistance.operit.core

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.core.application.OperitApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis

/**
 * 性能工具类
 * 提供便捷的性能优化功能和监控工具
 */
object PerformanceUtils {
    
    private const val TAG = "PerformanceUtils"
    
    /**
     * 测量代码块执行时间
     */
    inline fun <T> measureTime(
        operation: String,
        logResult: Boolean = true,
        block: () -> T
    ): Pair<T, Long> {
        val startTime = System.currentTimeMillis()
        val result = block()
        val duration = System.currentTimeMillis() - startTime
        
        if (logResult) {
            Log.d(TAG, "操作 '$operation' 耗时: ${duration}ms")
        }
        
        return Pair(result, duration)
    }
    
    /**
     * 测量协程代码块执行时间
     */
    suspend inline fun <T> measureTimeAsync(
        operation: String,
        logResult: Boolean = true,
        crossinline block: suspend () -> T
    ): Pair<T, Long> = withContext(Dispatchers.Default) {
        val duration = measureTimeMillis {
            block()
        }
        
        if (logResult) {
            Log.d(TAG, "异步操作 '$operation' 耗时: ${duration}ms")
        }
        
        val result = block()
        Pair(result, duration)
    }
    
    /**
     * 获取当前内存使用情况
     */
    fun getMemoryUsage(): MemoryUsage {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val maxMemory = runtime.maxMemory()
        
        return MemoryUsage(
            used = usedMemory,
            free = freeMemory,
            total = totalMemory,
            max = maxMemory,
            usagePercentage = (usedMemory.toFloat() / maxMemory.toFloat()) * 100
        )
    }
    
    /**
     * 获取格式化的内存使用信息
     */
    fun getFormattedMemoryUsage(): String {
        val usage = getMemoryUsage()
        return "内存使用: ${formatBytes(usage.used)}/${formatBytes(usage.max)} (${String.format("%.1f", usage.usagePercentage)}%)"
    }
    
    /**
     * 格式化字节数
     */
    fun formatBytes(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIndex = 0
        
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024.0
            unitIndex++
        }
        
        return String.format("%.1f %s", size, units[unitIndex])
    }
    
    /**
     * 检查内存压力
     */
    fun checkMemoryPressure(): MemoryPressureLevel {
        val usage = getMemoryUsage()
        return when {
            usage.usagePercentage >= 90 -> MemoryPressureLevel.CRITICAL
            usage.usagePercentage >= 75 -> MemoryPressureLevel.HIGH
            usage.usagePercentage >= 50 -> MemoryPressureLevel.MODERATE
            else -> MemoryPressureLevel.LOW
        }
    }
    
    /**
     * 执行性能优化建议
     */
    fun optimizePerformance(context: Context, level: OptimizationLevel = OptimizationLevel.MODERATE) {
        Log.i(TAG, "执行性能优化，级别: $level")
        
        try {
            val memoryManager = OperitApplication.memoryManager
            val aiModelManager = OperitApplication.aiModelManager
            val performanceMonitor = OperitApplication.performanceMonitor
            
            when (level) {
                OptimizationLevel.LIGHT -> {
                    // 轻度优化
                    memoryManager.clearCache()
                }
                
                OptimizationLevel.MODERATE -> {
                    // 中等优化
                    memoryManager.clearCache()
                    aiModelManager.cleanupExpiredModels()
                    System.gc()
                }
                
                OptimizationLevel.AGGRESSIVE -> {
                    // 激进优化
                    memoryManager.clearCache()
                    memoryManager.forceGarbageCollection()
                    aiModelManager.cleanup()
                    System.gc()
                    System.runFinalization()
                }
            }
            
            // 记录优化事件
            performanceMonitor.recordEvent("performance_optimization_$level")
            
            Log.i(TAG, "性能优化完成")
            
        } catch (e: Exception) {
            Log.e(TAG, "性能优化失败", e)
        }
    }
    
    /**
     * 获取设备性能等级
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun getDevicePerformanceClass(context: Context): DevicePerformanceClass {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            when (activityManager.deviceConfigurationInfo.reqGlEsVersion) {
                in 0x30000..0x30001 -> DevicePerformanceClass.LOW
                in 0x30002..0x30003 -> DevicePerformanceClass.MEDIUM
                else -> DevicePerformanceClass.HIGH
            }
        } else {
            // 对于较旧的Android版本，使用内存大小来估算
            val memoryInfo = android.app.ActivityManager.MemoryInfo()
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            activityManager.getMemoryInfo(memoryInfo)
            
            val totalMemoryGB = memoryInfo.totalMem / (1024 * 1024 * 1024)
            when {
                totalMemoryGB >= 8 -> DevicePerformanceClass.HIGH
                totalMemoryGB >= 4 -> DevicePerformanceClass.MEDIUM
                else -> DevicePerformanceClass.LOW
            }
        }
    }
    
    /**
     * 智能预加载建议
     */
    fun getPreloadRecommendations(context: Context): List<PreloadRecommendation> {
        val recommendations = mutableListOf<PreloadRecommendation>()
        val memoryPressure = checkMemoryPressure()
        val deviceClass = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getDevicePerformanceClass(context)
        } else {
            DevicePerformanceClass.MEDIUM // 默认值
        }
        
        // 根据设备性能和内存压力给出建议
        when {
            deviceClass == DevicePerformanceClass.HIGH && memoryPressure == MemoryPressureLevel.LOW -> {
                recommendations.add(PreloadRecommendation.PRELOAD_ALL_MODELS)
                recommendations.add(PreloadRecommendation.ENABLE_AGGRESSIVE_CACHING)
            }
            
            deviceClass == DevicePerformanceClass.MEDIUM && memoryPressure <= MemoryPressureLevel.MODERATE -> {
                recommendations.add(PreloadRecommendation.PRELOAD_CORE_MODELS)
                recommendations.add(PreloadRecommendation.ENABLE_MODERATE_CACHING)
            }
            
            else -> {
                recommendations.add(PreloadRecommendation.PRELOAD_ESSENTIAL_ONLY)
                recommendations.add(PreloadRecommendation.ENABLE_CONSERVATIVE_CACHING)
            }
        }
        
        return recommendations
    }
    
    // ==================== 数据类定义 ====================
    
    data class MemoryUsage(
        val used: Long,
        val free: Long,
        val total: Long,
        val max: Long,
        val usagePercentage: Float
    )
    
    enum class MemoryPressureLevel {
        LOW, MODERATE, HIGH, CRITICAL
    }
    
    enum class OptimizationLevel {
        LIGHT, MODERATE, AGGRESSIVE
    }
    
    enum class DevicePerformanceClass {
        LOW, MEDIUM, HIGH
    }
    
    enum class PreloadRecommendation {
        PRELOAD_ALL_MODELS,
        PRELOAD_CORE_MODELS,
        PRELOAD_ESSENTIAL_ONLY,
        ENABLE_AGGRESSIVE_CACHING,
        ENABLE_MODERATE_CACHING,
        ENABLE_CONSERVATIVE_CACHING
    }
}

/**
 * 扩展函数：为ViewModel添加性能监控
 */
fun ViewModel.trackPerformance(event: String) {
    try {
        OperitApplication.performanceMonitor.recordEvent(event)
    } catch (e: Exception) {
        Log.w("PerformanceExt", "无法记录性能事件: $event", e)
    }
}

/**
 * 扩展函数：为ViewModel添加内存优化
 */
fun ViewModel.optimizeMemory() {
    viewModelScope.launch {
        try {
            OperitApplication.memoryManager.clearCache()
        } catch (e: Exception) {
            Log.w("PerformanceExt", "内存优化失败", e)
        }
    }
}

/**
 * 扩展函数：为Activity添加性能监控
 */
fun Activity.trackPerformance(event: String) {
    try {
        OperitApplication.performanceMonitor.recordEvent(event)
    } catch (e: Exception) {
        Log.w("PerformanceExt", "无法记录性能事件: $event", e)
    }
}

/**
 * 扩展函数：为CoroutineScope添加性能测量
 */
suspend fun <T> CoroutineScope.measurePerformance(
    operation: String,
    block: suspend () -> T
): T {
    val startTime = System.currentTimeMillis()
    val result = block()
    val duration = System.currentTimeMillis() - startTime
    
    Log.d("PerformanceExt", "协程操作 '$operation' 耗时: ${duration}ms")
    
    try {
        OperitApplication.performanceMonitor.recordEvent("coroutine_$operation")
    } catch (e: Exception) {
        Log.w("PerformanceExt", "无法记录性能事件", e)
    }
    
    return result
}

/**
 * Compose性能监控组件
 */
@Composable
fun PerformanceMonitor(
    screenName: String,
    onPerformanceData: ((PerformanceMonitor.PerformanceData) -> Unit)? = null
) {
    val context = LocalContext.current
    val performanceMonitor = remember { OperitApplication.performanceMonitor }
    val performanceData by performanceMonitor.performanceData.collectAsState()
    
    // 记录屏幕进入事件
    LaunchedEffect(screenName) {
        performanceMonitor.recordEvent("screen_$screenName")
    }
    
    // 监听性能数据变化
    LaunchedEffect(performanceData) {
        onPerformanceData?.invoke(performanceData)
    }
    
    // 屏幕退出时清理
    DisposableEffect(screenName) {
        onDispose {
            // 可以在这里记录屏幕退出事件
        }
    }
}

/**
 * Compose内存优化组件
 */
@Composable
fun MemoryOptimizer(
    autoOptimize: Boolean = false,
    optimizationLevel: PerformanceUtils.OptimizationLevel = PerformanceUtils.OptimizationLevel.LIGHT
) {
    val context = LocalContext.current
    
    if (autoOptimize) {
        LaunchedEffect(Unit) {
            val memoryPressure = PerformanceUtils.checkMemoryPressure()
            
            if (memoryPressure >= PerformanceUtils.MemoryPressureLevel.HIGH) {
                PerformanceUtils.optimizePerformance(context, optimizationLevel)
            }
        }
    }
}

/**
 * 智能缓存工具
 */
object SmartCache {
    private const val TAG = "SmartCache"
    
    /**
     * 智能缓存对象
     */
    inline fun <reified T : Any> cache(
        key: String,
        factory: () -> T
    ): T {
        val memoryManager = OperitApplication.memoryManager
        
        // 尝试从缓存获取
        memoryManager.getCachedObject<T>(key)?.let { return it }
        
        // 创建新对象并缓存
        val obj = factory()
        memoryManager.cacheObject(key, obj)
        
        Log.d(TAG, "缓存对象: $key")
        return obj
    }
    
    /**
     * 智能对象池获取
     */
    inline fun <reified T : Any> obtain(
        crossinline factory: () -> T
    ): T {
        val memoryManager = OperitApplication.memoryManager
        return memoryManager.obtain(T::class.java) ?: factory()
    }
    
    /**
     * 回收对象到池
     */
    fun <T : Any> recycle(obj: T) {
        OperitApplication.memoryManager.recycle(obj)
    }
}