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
 * 🛠️ 性能工具类
 * 
 * 功能特性：
 * - 高精度时间测量工具
 * - 内存使用情况分析
 * - 设备性能等级检测
 * - 智能优化建议生成
 * - 性能基准测试
 * - 实用的性能检测扩展函数
 */
object PerformanceUtils {
    
    private const val TAG = "PerformanceUtils"
    
    // 设备性能等级枚举
    enum class DevicePerformanceLevel {
        LOW_END,      // 低端设备
        MID_RANGE,    // 中端设备  
        HIGH_END,     // 高端设备
        FLAGSHIP      // 旗舰设备
    }
    
    // 优化建议类型
    enum class OptimizationType {
        MEMORY,       // 内存优化
        CPU,          // CPU优化
        NETWORK,      // 网络优化
        STORAGE,      // 存储优化
        BATTERY,      // 电池优化
        UI            // UI优化
    }
    
    /**
     * ⏱️ 时间测量工具
     */
    class TimeMeasurer(private val name: String = "Operation") {
        private var startTime = 0L
        private var endTime = 0L
        private val measurements = mutableListOf<Long>()
        
        fun start(): TimeMeasurer {
            startTime = System.currentTimeMillis()
            return this
        }
        
        fun stop(): Long {
            endTime = System.currentTimeMillis()
            val duration = (endTime - startTime)
            measurements.add(duration)
            Log.d(TAG, "$name completed in ${duration}ms")
            return duration
        }
        
        fun getAverageTime(): Double {
            return if (measurements.isNotEmpty()) {
                measurements.average()
            } else 0.0
        }
        
        fun getMinTime(): Long = measurements.minOrNull() ?: 0L
        fun getMaxTime(): Long = measurements.maxOrNull() ?: 0L
        fun getTotalMeasurements(): Int = measurements.size
        
        fun reset() {
            measurements.clear()
            startTime = 0L
            endTime = 0L
        }
    }
    
    /**
     * 📊 内存分析器
     */
    object MemoryAnalyzer {
        
        fun analyzeMemoryUsage(context: Context): MemoryAnalysis {
            val runtime = Runtime.getRuntime()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val usedMemory = totalMemory - freeMemory
            val maxMemory = runtime.maxMemory()
            
            // 获取进程内存信息
            val pids = intArrayOf(android.os.Process.myPid())
            val processMemInfo = android.app.ActivityManager.MemoryInfo()
            android.app.ActivityManager.getMyMemoryState(pids, processMemInfo)
            
            return MemoryAnalysis(
                systemTotalMemory = totalMemory,
                systemAvailableMemory = freeMemory,
                systemUsedMemory = usedMemory,
                systemMemoryUsageRatio = (usedMemory.toFloat() / maxMemory.toFloat()) * 100,
                isLowMemory = processMemInfo.lowMemory,
                heapUsed = usedMemory,
                heapMax = maxMemory,
                heapFree = freeMemory,
                heapUsageRatio = (usedMemory.toFloat() / maxMemory.toFloat()) * 100,
                dalvikPss = processMemInfo.dalvikPss * 1024L,
                nativePss = processMemInfo.nativePss * 1024L,
                otherPss = processMemInfo.otherPss * 1024L,
                totalPss = processMemInfo.totalPss * 1024L
            )
        }
        
        fun getMemoryRecommendations(analysis: MemoryAnalysis): List<String> {
            val recommendations = mutableListOf<String>()
            
            if (analysis.systemMemoryUsageRatio > 90f) {
                recommendations.add("系统内存使用率过高，建议关闭其他应用")
                recommendations.add("启用激进内存清理模式")
            }
            
            if (analysis.heapUsageRatio > 80f) {
                recommendations.add("应用堆内存使用率过高，建议清理对象缓存")
                recommendations.add("检查是否存在内存泄漏")
            }
            
            if (analysis.isLowMemory) {
                recommendations.add("系统处于低内存状态，建议减少后台任务")
                recommendations.add("优先使用对象池减少内存分配")
            }
            
            val dalvikRatio = analysis.dalvikPss.toFloat() / analysis.totalPss
            if (dalvikRatio > 70f) {
                recommendations.add("Dalvik内存占比过高，考虑优化Java/Kotlin代码")
            }
            
            val nativeRatio = analysis.nativePss.toFloat() / analysis.totalPss
            if (nativeRatio > 50f) {
                recommendations.add("Native内存占比过高，检查JNI代码和图像资源")
            }
            
            return recommendations
        }
        
        fun formatMemorySize(bytes: Long): String {
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var size = bytes.toDouble()
            var unitIndex = 0
            
            while (size >= 1024 && unitIndex < units.size - 1) {
                size /= 1024.0
                unitIndex++
            }
            
            return String.format("%.1f %s", size, units[unitIndex])
        }
    }
    
    /**
     * 📱 设备性能检测器
     */
    object DeviceProfiler {
        
        fun getDevicePerformanceLevel(context: Context): DevicePerformanceLevel {
            val score = calculatePerformanceScore(context)
            
            return when {
                score >= 80 -> DevicePerformanceLevel.FLAGSHIP
                score >= 60 -> DevicePerformanceLevel.HIGH_END
                score >= 40 -> DevicePerformanceLevel.MID_RANGE
                else -> DevicePerformanceLevel.LOW_END
            }
        }
        
        private fun calculatePerformanceScore(context: Context): Int {
            var score = 0
            
            // CPU核心数评分 (0-20分)
            val coreCount = Runtime.getRuntime().availableProcessors()
            score += when {
                coreCount >= 8 -> 20
                coreCount >= 6 -> 15
                coreCount >= 4 -> 10
                else -> 5
            }
            
            // 内存大小评分 (0-25分)
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            val totalMemoryGB = memInfo.totalMem / (1024f * 1024f * 1024f)
            
            score += when {
                totalMemoryGB >= 8 -> 25
                totalMemoryGB >= 6 -> 20
                totalMemoryGB >= 4 -> 15
                totalMemoryGB >= 2 -> 10
                else -> 5
            }
            
            // Android版本评分 (0-15分)
            score += when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> 15
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> 12
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> 10
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> 8
                else -> 5
            }
            
            // 屏幕密度评分 (0-15分)
            val displayMetrics = context.resources.displayMetrics
            val densityDpi = displayMetrics.densityDpi
            score += when {
                densityDpi >= android.util.DisplayMetrics.DENSITY_XXXHIGH -> 15
                densityDpi >= android.util.DisplayMetrics.DENSITY_XXHIGH -> 12
                densityDpi >= android.util.DisplayMetrics.DENSITY_XHIGH -> 10
                densityDpi >= android.util.DisplayMetrics.DENSITY_HIGH -> 8
                else -> 5
            }
            
            // GPU性能评分 (0-15分)
            score += when {
                hasAdvancedGpuFeatures(context) -> 15
                hasStandardGpuFeatures(context) -> 10
                else -> 5
            }
            
            // 传感器评分 (0-10分)
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
            val sensorCount = sensorManager.getSensorList(android.hardware.Sensor.TYPE_ALL).size
            score += when {
                sensorCount >= 20 -> 10
                sensorCount >= 15 -> 8
                sensorCount >= 10 -> 6
                else -> 3
            }
            
            return score.coerceIn(0, 100)
        }
        
        private fun hasAdvancedGpuFeatures(context: Context): Boolean {
            val packageManager = context.packageManager
            return packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL) ||
                   packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_OPENGLES_EXTENSION_PACK)
        }
        
        private fun hasStandardGpuFeatures(context: Context): Boolean {
            val packageManager = context.packageManager
            return packageManager.hasSystemFeature("android.hardware.opengles.aep")
        }
        
        fun getDeviceSpecs(context: Context): DeviceSpecs {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            
            val displayMetrics = context.resources.displayMetrics
            
            return DeviceSpecs(
                model = "${Build.MANUFACTURER} ${Build.MODEL}",
                androidVersion = Build.VERSION.RELEASE,
                apiLevel = Build.VERSION.SDK_INT,
                cpuCores = Runtime.getRuntime().availableProcessors(),
                totalMemoryGB = memInfo.totalMem / (1024f * 1024f * 1024f),
                screenDensity = displayMetrics.densityDpi,
                screenWidth = displayMetrics.widthPixels,
                screenHeight = displayMetrics.heightPixels,
                performanceLevel = getDevicePerformanceLevel(context)
            )
        }
    }
    
    /**
     * 🎯 性能基准测试
     */
    object BenchmarkTester {
        
        suspend fun runCpuBenchmark(): BenchmarkResult = withContext(Dispatchers.Default) {
            val operations = 1_000_000
            val times = mutableListOf<Long>()
            
            repeat(5) {
                val time = measureTimeMillis {
                    // CPU密集型计算：斐波那契数列
                    repeat(operations / 1000) {
                        fibonacci(20)
                    }
                }
                times.add(time)
            }
            
            BenchmarkResult(
                testName = "CPU Benchmark",
                averageTime = times.average(),
                minTime = times.minOrNull() ?: 0L,
                maxTime = times.maxOrNull() ?: 0L,
                operations = operations,
                unit = "operations/second"
            )
        }
        
        suspend fun runMemoryBenchmark(): BenchmarkResult = withContext(Dispatchers.Default) {
            val allocations = 10_000
            val times = mutableListOf<Long>()
            
            repeat(5) {
                val time = measureTimeMillis {
                    val arrays = mutableListOf<IntArray>()
                    repeat(allocations) {
                        arrays.add(IntArray(1000))
                    }
                    // 强制垃圾回收
                    System.gc()
                }
                times.add(time)
            }
            
            BenchmarkResult(
                testName = "Memory Benchmark",
                averageTime = times.average(),
                minTime = times.minOrNull() ?: 0L,
                maxTime = times.maxOrNull() ?: 0L,
                operations = allocations,
                unit = "allocations/second"
            )
        }
        
        private fun fibonacci(n: Int): Long {
            return if (n <= 1) n.toLong()
            else fibonacci(n - 1) + fibonacci(n - 2)
        }
    }
    
    /**
     * 💡 智能优化建议生成器
     */
    object OptimizationAdvisor {
        
        fun generateOptimizationSuggestions(
            context: Context,
            currentMetrics: PerformanceMetrics? = null
        ): List<OptimizationSuggestion> {
            val suggestions = mutableListOf<OptimizationSuggestion>()
            val deviceLevel = DeviceProfiler.getDevicePerformanceLevel(context)
            val memoryAnalysis = MemoryAnalyzer.analyzeMemoryUsage(context)
            
            // 基于设备等级的建议
            when (deviceLevel) {
                DevicePerformanceLevel.LOW_END -> {
                    suggestions.addAll(generateLowEndOptimizations())
                }
                DevicePerformanceLevel.MID_RANGE -> {
                    suggestions.addAll(generateMidRangeOptimizations())
                }
                DevicePerformanceLevel.HIGH_END,
                DevicePerformanceLevel.FLAGSHIP -> {
                    suggestions.addAll(generateHighEndOptimizations())
                }
            }
            
            // 基于内存状态的建议
            if (memoryAnalysis.systemMemoryUsageRatio > 80f) {
                suggestions.add(OptimizationSuggestion(
                    type = OptimizationType.MEMORY,
                    priority = SuggestionPriority.HIGH,
                    title = "内存使用率过高",
                    description = "系统内存使用率超过80%，建议启用内存优化",
                    actions = listOf(
                        "启用激进内存清理",
                        "减少缓存大小",
                        "优化对象生命周期"
                    )
                ))
            }
            
            // 基于性能指标的建议
            currentMetrics?.let { metrics ->
                if (metrics.cpu.appCpuUsage > 70f) {
                    suggestions.add(OptimizationSuggestion(
                        type = OptimizationType.CPU,
                        priority = SuggestionPriority.HIGH,
                        title = "CPU使用率过高",
                        description = "应用CPU使用率超过70%，建议优化计算密集型任务",
                        actions = listOf(
                            "将任务移至后台线程",
                            "减少复杂计算",
                            "使用协程优化并发"
                        )
                    ))
                }
                
                if (metrics.frame.currentFps < 45f) {
                    suggestions.add(OptimizationSuggestion(
                        type = OptimizationType.UI,
                        priority = SuggestionPriority.MEDIUM,
                        title = "帧率偏低",
                        description = "当前帧率低于45fps，建议优化UI渲染",
                        actions = listOf(
                            "减少UI复杂度",
                            "优化图片加载",
                            "使用硬件加速"
                        )
                    ))
                }
            }
            
            return suggestions.sortedByDescending { it.priority.ordinal }
        }
        
        private fun generateLowEndOptimizations(): List<OptimizationSuggestion> {
            return listOf(
                OptimizationSuggestion(
                    type = OptimizationType.MEMORY,
                    priority = SuggestionPriority.HIGH,
                    title = "启用低端设备模式",
                    description = "检测到低端设备，建议启用内存和性能优化",
                    actions = listOf(
                        "减少缓存大小",
                        "降低动画质量",
                        "禁用非必要功能"
                    )
                ),
                OptimizationSuggestion(
                    type = OptimizationType.UI,
                    priority = SuggestionPriority.MEDIUM,
                    title = "简化UI元素",
                    description = "低端设备建议使用简化的UI设计",
                    actions = listOf(
                        "减少阴影和透明效果",
                        "降低图片质量",
                        "减少动画效果"
                    )
                )
            )
        }
        
        private fun generateMidRangeOptimizations(): List<OptimizationSuggestion> {
            return listOf(
                OptimizationSuggestion(
                    type = OptimizationType.MEMORY,
                    priority = SuggestionPriority.MEDIUM,
                    title = "平衡性能和功能",
                    description = "中端设备可以启用大部分功能，但需要适度优化",
                    actions = listOf(
                        "适中的缓存策略",
                        "选择性启用高级功能",
                        "监控内存使用"
                    )
                )
            )
        }
        
        private fun generateHighEndOptimizations(): List<OptimizationSuggestion> {
            return listOf(
                OptimizationSuggestion(
                    type = OptimizationType.UI,
                    priority = SuggestionPriority.LOW,
                    title = "启用高级功能",
                    description = "高端设备可以充分利用硬件性能",
                    actions = listOf(
                        "启用高质量动画",
                        "使用硬件加速",
                        "启用AI增强功能"
                    )
                )
            )
        }
    }
    
    // ==================== 数据类 ====================
    
    data class MemoryAnalysis(
        val systemTotalMemory: Long,
        val systemAvailableMemory: Long,
        val systemUsedMemory: Long,
        val systemMemoryUsageRatio: Float,
        val isLowMemory: Boolean,
        val heapUsed: Long,
        val heapMax: Long,
        val heapFree: Long,
        val heapUsageRatio: Float,
        val dalvikPss: Long,
        val nativePss: Long,
        val otherPss: Long,
        val totalPss: Long
    )
    
    data class DeviceSpecs(
        val model: String,
        val androidVersion: String,
        val apiLevel: Int,
        val cpuCores: Int,
        val totalMemoryGB: Float,
        val screenDensity: Int,
        val screenWidth: Int,
        val screenHeight: Int,
        val performanceLevel: DevicePerformanceLevel
    )
    
    data class BenchmarkResult(
        val testName: String,
        val averageTime: Double,
        val minTime: Long,
        val maxTime: Long,
        val operations: Int,
        val unit: String
    )
    
    data class OptimizationSuggestion(
        val type: OptimizationType,
        val priority: SuggestionPriority,
        val title: String,
        val description: String,
        val actions: List<String>
    )
    
    enum class SuggestionPriority {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    // 为了兼容性，添加这个数据类（引用其他文件的类型）
    data class PerformanceMetrics(
        val cpu: CpuMetrics = CpuMetrics(),
        val memory: MemoryMetrics = MemoryMetrics(),
        val frame: FrameMetrics = FrameMetrics()
    )
    
    data class CpuMetrics(
        val appCpuUsage: Float = 0f
    )
    
    data class MemoryMetrics(
        val memoryUsageRatio: Float = 0f
    )
    
    data class FrameMetrics(
        val currentFps: Float = 0f
    )
    
    // ==================== 扩展函数 ====================
    
    /**
     * 测量代码块执行时间的扩展函数
     */
    inline fun <T> measureExecutionTime(
        operationName: String = "Operation",
        block: () -> T
    ): Pair<T, Long> {
        val startTime = System.currentTimeMillis()
        val result = block()
        val endTime = System.currentTimeMillis()
        val duration = (endTime - startTime)
        
        Log.d(TAG, "$operationName completed in ${duration}ms")
        return Pair(result, duration)
    }
    
    /**
     * 异步测量代码块执行时间
     */
    suspend inline fun <T> measureSuspendExecutionTime(
        operationName: String = "Async Operation",
        crossinline block: suspend () -> T
    ): Pair<T, Long> {
        val startTime = System.currentTimeMillis()
        val result = block()
        val endTime = System.currentTimeMillis()
        val duration = (endTime - startTime)
        
        Log.d(TAG, "$operationName completed in ${duration}ms")
        return Pair(result, duration)
    }
    
    /**
     * 主线程检查扩展函数
     */
    @MainThread
    fun ensureMainThread(operationName: String = "Operation") {
        if (Looper.getMainLooper() != Looper.myLooper()) {
            throw IllegalStateException("$operationName must be called from the main thread")
        }
    }
    
    /**
     * 工作线程检查扩展函数
     */
    @WorkerThread
    fun ensureWorkerThread(operationName: String = "Operation") {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            throw IllegalStateException("$operationName should not be called from the main thread")
        }
    }
    
    /**
     * 格式化数字的扩展函数
     */
    fun Number.formatPercentage(): String {
        val df = DecimalFormat("#.#")
        return "${df.format(this.toDouble())}%"
    }
    
    fun Long.formatAsMemorySize(): String {
        return MemoryAnalyzer.formatMemorySize(this)
    }
    
    /**
     * 安全地执行操作，捕获异常
     */
    inline fun <T> safeExecute(
        operationName: String = "Operation",
        defaultValue: T,
        block: () -> T
    ): T {
        return try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "Error in $operationName", e)
            defaultValue
        }
    }
    
    /**
     * 获取CPU信息
     */
    fun getCpuInfo(): String {
        return try {
            val cpuInfo = StringBuilder()
            BufferedReader(FileReader("/proc/cpuinfo")).use { reader ->
                reader.lineSequence().take(20).forEach { line ->
                    if (line.startsWith("model name") || 
                        line.startsWith("processor") || 
                        line.startsWith("cpu MHz")) {
                        cpuInfo.appendLine(line)
                    }
                }
            }
            cpuInfo.toString()
        } catch (e: Exception) {
            "CPU info not available"
        }
    }
    
    /**
     * 检查应用是否在前台
     */
    fun Context.isAppInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        val packageName = packageName
        
        return appProcesses.any { appProcess ->
            appProcess.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
            appProcess.processName == packageName
        }
    }
    
    /**
     * 创建时间测量器的便捷函数
     */
    fun createTimeMeasurer(name: String = "Measurement"): TimeMeasurer {
        return TimeMeasurer(name)
    }
    
    /**
     * 内存压力检测
     */
    fun Context.isMemoryPressure(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        return memInfo.lowMemory || 
               (memInfo.availMem.toFloat() / memInfo.totalMem) < 0.15f
    }
    
    /**
     * 设备温度检测（简化版）
     */
    fun getDeviceTemperature(): Float {
        return try {
            BufferedReader(FileReader("/sys/class/thermal/thermal_zone0/temp")).use { reader ->
                reader.readLine().toFloat() / 1000f // 转换为摄氏度
            }
        } catch (e: Exception) {
            0f // 无法获取温度
        }
    }
    
    /**
     * 电池电量检测
     */
    fun Context.getBatteryLevel(): Float {
        return try {
            val batteryIntent = registerReceiver(null, 
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            
            if (level == -1 || scale == -1) 0f
            else (level.toFloat() / scale.toFloat()) * 100f
        } catch (e: Exception) {
            0f
        }
    }
    
    /**
     * 网络状态快速检查
     */
    fun Context.isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
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
 * 集成内存缓存、网络缓存和数据库缓存的统一接口
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
    
    /**
     * 获取网络缓存大小
     */
    fun getNetworkCacheSize(): Long {
        return try {
            OperitApplication.networkOptimizer.getCacheSize()
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * 获取数据库缓存信息
     */
    fun getDatabaseCacheInfo(): String {
        return try {
            val metrics = OperitApplication.databaseOptimizer.databaseMetrics.value
            "数据库缓存: ${metrics.cacheSize} 项，命中率: ${String.format("%.1f", metrics.cacheHitRate)}%"
        } catch (e: Exception) {
            "数据库缓存信息不可用"
        }
    }
    
    /**
     * 清理所有缓存
     */
    suspend fun clearAllCaches() {
        try {
            // 清理内存缓存
            OperitApplication.memoryManager.clearCache()
            
            // 清理网络缓存
            OperitApplication.networkOptimizer.clearCache()
            
            // 清理数据库缓存
            OperitApplication.databaseOptimizer.clearCache()
            
            Log.i(TAG, "所有缓存已清理")
        } catch (e: Exception) {
            Log.e(TAG, "清理缓存失败", e)
        }
    }
    
    /**
     * 获取综合缓存统计信息
     */
    fun getCacheStats(): CacheStats {
        return try {
            val memoryUsage = getMemoryUsage()
            val networkCacheSize = getNetworkCacheSize()
            val databaseMetrics = OperitApplication.databaseOptimizer.databaseMetrics.value
            val networkMetrics = OperitApplication.networkOptimizer.networkMetrics.value
            
            CacheStats(
                memoryUsagePercentage = memoryUsage.usagePercentage,
                networkCacheSize = networkCacheSize,
                networkCacheHitRate = networkMetrics.cacheHitRate,
                databaseCacheSize = databaseMetrics.cacheSize,
                databaseCacheHitRate = databaseMetrics.cacheHitRate
            )
        } catch (e: Exception) {
            CacheStats()
        }
    }
    
    data class CacheStats(
        val memoryUsagePercentage: Float = 0f,
        val networkCacheSize: Long = 0L,
        val networkCacheHitRate: Float = 0f,
        val databaseCacheSize: Int = 0,
        val databaseCacheHitRate: Float = 0f
    )
}