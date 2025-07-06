package com.ai.assistance.operit.core

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.FileReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

/**
 * 📊 性能监控器
 * 
 * 功能特性：
 * - 实时CPU、内存、网络监控
 * - ANR检测和性能警告系统
 * - 性能数据记录和分析
 * - 可导出性能日志和报告
 * - 帧率监控和渲染性能分析
 * - 自动化性能优化建议
 */
class PerformanceMonitor private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "PerformanceMonitor"
        private const val MONITOR_INTERVAL_MS = 5000L // 监控间隔5秒
        private const val ANR_THRESHOLD_MS = 5000L // ANR阈值5秒
        private const val MEMORY_WARNING_THRESHOLD = 0.8f // 内存警告阈值80%
        private const val CPU_WARNING_THRESHOLD = 80f // CPU警告阈值80%
        private const val FRAME_DROP_WARNING_THRESHOLD = 10 // 掉帧警告阈值10帧
        
        @Volatile
        private var INSTANCE: PerformanceMonitor? = null
        
        fun getInstance(context: Context): PerformanceMonitor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PerformanceMonitor(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // 核心组件
    private val cpuMonitor = CpuMonitor()
    private val memoryMonitor = MemoryMonitor()
    private val networkMonitor = NetworkMonitor()
    private val frameMonitor = FrameRateMonitor()
    private val anrDetector = ANRDetector()
    private val performanceAnalyzer = PerformanceAnalyzer()
    
    // 系统服务
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    // 协程作用域
    private val monitorScope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() + CoroutineName("PerformanceMonitor")
    )
    
    // 状态管理
    private val _currentMetrics = MutableStateFlow(PerformanceMetrics())
    val currentMetrics: StateFlow<PerformanceMetrics> = _currentMetrics.asStateFlow()
    
    private val _alerts = MutableStateFlow<List<PerformanceAlert>>(emptyList())
    val alerts: StateFlow<List<PerformanceAlert>> = _alerts.asStateFlow()
    
    // 监控状态
    @Volatile
    private var isMonitoring = false
    private val monitoringJob = AtomicLong(0)
    
    // 性能数据存储
    private val metricsHistory = mutableListOf<PerformanceMetrics>()
    private val maxHistorySize = 1000 // 最多保存1000条记录
    
    init {
        Log.d(TAG, "PerformanceMonitor initialized")
    }
    
    /**
     * 🖥️ CPU监控器
     */
    private inner class CpuMonitor {
        private var lastCpuTime = 0L
        private var lastAppCpuTime = 0L
        private var lastSampleTime = 0L
        
        fun getCurrentCpuUsage(): CpuMetrics {
            val currentTime = System.currentTimeMillis()
            val totalCpuTime = getTotalCpuTime()
            val appCpuTime = getAppCpuTime()
            
            val cpuUsage = if (lastSampleTime > 0) {
                val totalDelta = totalCpuTime - lastCpuTime
                val appDelta = appCpuTime - lastAppCpuTime
                
                if (totalDelta > 0) {
                    (appDelta.toFloat() / totalDelta.toFloat()) * 100f
                } else 0f
            } else 0f
            
            lastCpuTime = totalCpuTime
            lastAppCpuTime = appCpuTime
            lastSampleTime = currentTime
            
            return CpuMetrics(
                totalCpuUsage = cpuUsage,
                appCpuUsage = min(cpuUsage, 100f),
                coreCount = Runtime.getRuntime().availableProcessors(),
                frequency = getCpuFrequency(),
                temperature = getCpuTemperature()
            )
        }
        
        private fun getTotalCpuTime(): Long {
            return try {
                BufferedReader(FileReader("/proc/stat")).use { reader ->
                    val line = reader.readLine()
                    val parts = line.split(" ").filter { it.isNotEmpty() }
                    if (parts.size >= 5) {
                        // user + nice + system + idle + iowait
                        parts.subList(1, 5).sumOf { it.toLong() }
                    } else 0L
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read total CPU time", e)
                0L
            }
        }
        
        private fun getAppCpuTime(): Long {
            return try {
                Debug.threadCpuTimeNanos() / 1_000_000 // 转换为毫秒
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read app CPU time", e)
                0L
            }
        }
        
        private fun getCpuFrequency(): Int {
            return try {
                BufferedReader(FileReader("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")).use { reader ->
                    reader.readLine().toInt() / 1000 // 转换为MHz
                }
            } catch (e: Exception) {
                0 // 无法获取频率信息
            }
        }
        
        private fun getCpuTemperature(): Float {
            return try {
                // 尝试读取CPU温度（路径可能因设备而异）
                val tempPaths = listOf(
                    "/sys/class/thermal/thermal_zone0/temp",
                    "/sys/devices/virtual/thermal/thermal_zone0/temp"
                )
                
                for (path in tempPaths) {
                    try {
                        BufferedReader(FileReader(path)).use { reader ->
                            return reader.readLine().toFloat() / 1000f // 转换为摄氏度
                        }
                    } catch (e: Exception) {
                        continue
                    }
                }
                0f
            } catch (e: Exception) {
                0f
            }
        }
    }
    
    /**
     * 💾 内存监控器
     */
    private inner class MemoryMonitor {
        fun getCurrentMemoryMetrics(): MemoryMetrics {
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            
            val runtime = Runtime.getRuntime()
            val debugMemInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(debugMemInfo)
            
            return MemoryMetrics(
                totalMemory = memInfo.totalMem,
                availableMemory = memInfo.availMem,
                usedMemory = memInfo.totalMem - memInfo.availMem,
                memoryUsageRatio = (memInfo.totalMem - memInfo.availMem).toFloat() / memInfo.totalMem.toFloat(),
                isLowMemory = memInfo.lowMemory,
                heapUsed = runtime.totalMemory() - runtime.freeMemory(),
                heapMax = runtime.maxMemory(),
                heapFree = runtime.freeMemory(),
                heapUsageRatio = (runtime.totalMemory() - runtime.freeMemory()).toFloat() / runtime.maxMemory().toFloat(),
                dalvikPss = debugMemInfo.dalvikPss * 1024L, // 转换为字节
                nativePss = debugMemInfo.nativePss * 1024L,
                otherPss = debugMemInfo.otherPss * 1024L,
                totalPss = debugMemInfo.totalPss * 1024L
            )
        }
        
        fun getMemoryTrend(): MemoryTrend {
            val recentMetrics = metricsHistory.takeLast(10).map { it.memory }
            if (recentMetrics.size < 2) return MemoryTrend.STABLE
            
            val firstUsage = recentMetrics.first().memoryUsageRatio
            val lastUsage = recentMetrics.last().memoryUsageRatio
            val difference = lastUsage - firstUsage
            
            return when {
                difference > 0.1f -> MemoryTrend.INCREASING
                difference < -0.1f -> MemoryTrend.DECREASING
                else -> MemoryTrend.STABLE
            }
        }
    }
    
    /**
     * 🌐 网络监控器
     */
    private inner class NetworkMonitor {
        private var lastRxBytes = 0L
        private var lastTxBytes = 0L
        private var lastNetworkSample = 0L
        
        fun getCurrentNetworkMetrics(): NetworkMetrics {
            val currentTime = System.currentTimeMillis()
            val rxBytes = getTotalRxBytes()
            val txBytes = getTotalTxBytes()
            
            val downloadSpeed = if (lastNetworkSample > 0) {
                val timeDelta = (currentTime - lastNetworkSample) / 1000f // 秒
                val rxDelta = rxBytes - lastRxBytes
                if (timeDelta > 0) (rxDelta / timeDelta).toLong() else 0L
            } else 0L
            
            val uploadSpeed = if (lastNetworkSample > 0) {
                val timeDelta = (currentTime - lastNetworkSample) / 1000f
                val txDelta = txBytes - lastTxBytes
                if (timeDelta > 0) (txDelta / timeDelta).toLong() else 0L
            } else 0L
            
            lastRxBytes = rxBytes
            lastTxBytes = txBytes
            lastNetworkSample = currentTime
            
            return NetworkMetrics(
                networkType = getNetworkType(),
                isConnected = isNetworkConnected(),
                signalStrength = getSignalStrength(),
                downloadSpeed = downloadSpeed,
                uploadSpeed = uploadSpeed,
                totalRxBytes = rxBytes,
                totalTxBytes = txBytes,
                latency = measureNetworkLatency()
            )
        }
        
        private fun getTotalRxBytes(): Long {
            return try {
                android.net.TrafficStats.getTotalRxBytes()
            } catch (e: Exception) {
                0L
            }
        }
        
        private fun getTotalTxBytes(): Long {
            return try {
                android.net.TrafficStats.getTotalTxBytes()
            } catch (e: Exception) {
                0L
            }
        }
        
        private fun getNetworkType(): NetworkType {
            val network = connectivityManager.activeNetwork ?: return NetworkType.NONE
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkType.NONE
            
            return when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
                else -> NetworkType.OTHER
            }
        }
        
        private fun isNetworkConnected(): Boolean {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        
        private fun getSignalStrength(): Int {
            // 简化的信号强度检测
            return when (getNetworkType()) {
                NetworkType.WIFI -> 85 // 模拟WiFi信号强度
                NetworkType.CELLULAR -> 75 // 模拟蜂窝信号强度
                else -> 0
            }
        }
        
        private fun measureNetworkLatency(): Long {
            // 简化的延迟测量，实际应用中可以ping一个服务器
            return when (getNetworkType()) {
                NetworkType.WIFI -> 20L
                NetworkType.CELLULAR -> 80L
                else -> -1L
            }
        }
    }
    
    /**
     * 🎮 帧率监控器
     */
    private inner class FrameRateMonitor {
        private val frameTimestamps = ArrayDeque<Long>(60)
        private var lastFrameTime = 0L
        private var droppedFrames = 0
        
        fun recordFrame() {
            val currentTime = System.currentTimeMillis()
            frameTimestamps.addLast(currentTime)
            
            // 保持最近60帧的数据
            while (frameTimestamps.size > 60) {
                frameTimestamps.removeFirst()
            }
            
            // 检测掉帧
            if (lastFrameTime > 0) {
                val frameTime = currentTime - lastFrameTime
                if (frameTime > 20) { // 超过20ms认为是掉帧 (50fps以下)
                    droppedFrames++
                }
            }
            
            lastFrameTime = currentTime
        }
        
        fun getCurrentFrameMetrics(): FrameMetrics {
            if (frameTimestamps.size < 2) {
                return FrameMetrics(
                    currentFps = 0f,
                    averageFps = 0f,
                    droppedFrames = 0,
                    frameTimeMs = 0f,
                    isSmooth = false
                )
            }
            
            val timeSpan = frameTimestamps.last() - frameTimestamps.first()
            val frameCount = frameTimestamps.size - 1
            
            val currentFps = if (timeSpan > 0) {
                (frameCount * 1000f) / timeSpan
            } else 0f
            
            val averageFrameTime = if (frameCount > 0) timeSpan.toFloat() / frameCount else 0f
            val isSmooth = currentFps >= 55f && droppedFrames < FRAME_DROP_WARNING_THRESHOLD
            
            return FrameMetrics(
                currentFps = currentFps,
                averageFps = currentFps, // 简化处理
                droppedFrames = droppedFrames,
                frameTimeMs = averageFrameTime,
                isSmooth = isSmooth
            )
        }
        
        fun resetFrameStats() {
            droppedFrames = 0
            frameTimestamps.clear()
        }
    }
    
    /**
     * 🚫 ANR检测器
     */
    private inner class ANRDetector {
        private val mainHandler = Handler(Looper.getMainLooper())
        private var lastMainThreadResponse = System.currentTimeMillis()
        private var anrCheckRunnable: Runnable? = null
        
        fun startDetection() {
            anrCheckRunnable = object : Runnable {
                override fun run() {
                    checkMainThread()
                    mainHandler.postDelayed(this, 1000) // 每秒检查一次
                }
            }
            mainHandler.post(anrCheckRunnable!!)
        }
        
        fun stopDetection() {
            anrCheckRunnable?.let { mainHandler.removeCallbacks(it) }
        }
        
        private fun checkMainThread() {
            val checkTime = System.currentTimeMillis()
            
            mainHandler.post {
                lastMainThreadResponse = System.currentTimeMillis()
            }
            
            // 在后台线程检查响应时间
            monitorScope.launch {
                delay(2000) // 等待2秒
                val responseDelay = System.currentTimeMillis() - lastMainThreadResponse
                
                if (responseDelay > ANR_THRESHOLD_MS) {
                    val alert = PerformanceAlert(
                        type = AlertType.ANR,
                        severity = AlertSeverity.CRITICAL,
                        message = "主线程无响应 ${responseDelay}ms",
                        timestamp = System.currentTimeMillis(),
                        data = mapOf("delay" to responseDelay)
                    )
                    addAlert(alert)
                    Log.w(TAG, "ANR detected: ${responseDelay}ms delay")
                }
            }
        }
    }
    
    /**
     * 📈 性能分析器
     */
    private inner class PerformanceAnalyzer {
        fun analyzePerformance(metrics: PerformanceMetrics): List<PerformanceAlert> {
            val alerts = mutableListOf<PerformanceAlert>()
            
            // 内存分析
            if (metrics.memory.memoryUsageRatio > MEMORY_WARNING_THRESHOLD) {
                alerts.add(PerformanceAlert(
                    type = AlertType.HIGH_MEMORY_USAGE,
                    severity = if (metrics.memory.memoryUsageRatio > 0.9f) AlertSeverity.CRITICAL else AlertSeverity.WARNING,
                    message = "内存使用率过高: ${(metrics.memory.memoryUsageRatio * 100).toInt()}%",
                    timestamp = System.currentTimeMillis(),
                    data = mapOf("usage_ratio" to metrics.memory.memoryUsageRatio)
                ))
            }
            
            // CPU分析
            if (metrics.cpu.appCpuUsage > CPU_WARNING_THRESHOLD) {
                alerts.add(PerformanceAlert(
                    type = AlertType.HIGH_CPU_USAGE,
                    severity = if (metrics.cpu.appCpuUsage > 90f) AlertSeverity.CRITICAL else AlertSeverity.WARNING,
                    message = "CPU使用率过高: ${metrics.cpu.appCpuUsage.toInt()}%",
                    timestamp = System.currentTimeMillis(),
                    data = mapOf("cpu_usage" to metrics.cpu.appCpuUsage)
                ))
            }
            
            // 帧率分析
            if (metrics.frame.currentFps < 30f) {
                alerts.add(PerformanceAlert(
                    type = AlertType.LOW_FRAME_RATE,
                    severity = if (metrics.frame.currentFps < 20f) AlertSeverity.CRITICAL else AlertSeverity.WARNING,
                    message = "帧率过低: ${metrics.frame.currentFps.toInt()}fps",
                    timestamp = System.currentTimeMillis(),
                    data = mapOf("fps" to metrics.frame.currentFps)
                ))
            }
            
            // 网络分析
            if (!metrics.network.isConnected) {
                alerts.add(PerformanceAlert(
                    type = AlertType.NETWORK_DISCONNECTED,
                    severity = AlertSeverity.WARNING,
                    message = "网络连接断开",
                    timestamp = System.currentTimeMillis(),
                    data = emptyMap()
                ))
            }
            
            return alerts
        }
        
        fun generateOptimizationSuggestions(metrics: PerformanceMetrics): List<String> {
            val suggestions = mutableListOf<String>()
            
            if (metrics.memory.memoryUsageRatio > 0.8f) {
                suggestions.add("建议清理内存缓存")
                suggestions.add("检查是否存在内存泄漏")
            }
            
            if (metrics.cpu.appCpuUsage > 70f) {
                suggestions.add("优化CPU密集型操作")
                suggestions.add("考虑将部分任务移至后台线程")
            }
            
            if (metrics.frame.currentFps < 45f) {
                suggestions.add("优化UI渲染性能")
                suggestions.add("减少不必要的重绘")
            }
            
            if (metrics.network.downloadSpeed < 100_000) { // 100KB/s
                suggestions.add("网络速度较慢，建议启用数据压缩")
                suggestions.add("考虑使用缓存减少网络请求")
            }
            
            return suggestions
        }
    }
    
    // ==================== 数据类 ====================
    
    data class PerformanceMetrics(
        val timestamp: Long = System.currentTimeMillis(),
        val cpu: CpuMetrics = CpuMetrics(),
        val memory: MemoryMetrics = MemoryMetrics(),
        val network: NetworkMetrics = NetworkMetrics(),
        val frame: FrameMetrics = FrameMetrics()
    )
    
    data class CpuMetrics(
        val totalCpuUsage: Float = 0f,
        val appCpuUsage: Float = 0f,
        val coreCount: Int = 0,
        val frequency: Int = 0,
        val temperature: Float = 0f
    )
    
    data class MemoryMetrics(
        val totalMemory: Long = 0L,
        val availableMemory: Long = 0L,
        val usedMemory: Long = 0L,
        val memoryUsageRatio: Float = 0f,
        val isLowMemory: Boolean = false,
        val heapUsed: Long = 0L,
        val heapMax: Long = 0L,
        val heapFree: Long = 0L,
        val heapUsageRatio: Float = 0f,
        val dalvikPss: Long = 0L,
        val nativePss: Long = 0L,
        val otherPss: Long = 0L,
        val totalPss: Long = 0L
    )
    
    data class NetworkMetrics(
        val networkType: NetworkType = NetworkType.NONE,
        val isConnected: Boolean = false,
        val signalStrength: Int = 0,
        val downloadSpeed: Long = 0L,
        val uploadSpeed: Long = 0L,
        val totalRxBytes: Long = 0L,
        val totalTxBytes: Long = 0L,
        val latency: Long = -1L
    )
    
    data class FrameMetrics(
        val currentFps: Float = 0f,
        val averageFps: Float = 0f,
        val droppedFrames: Int = 0,
        val frameTimeMs: Float = 0f,
        val isSmooth: Boolean = false
    )
    
    data class PerformanceAlert(
        val type: AlertType,
        val severity: AlertSeverity,
        val message: String,
        val timestamp: Long,
        val data: Map<String, Any>
    )
    
    enum class AlertType {
        HIGH_MEMORY_USAGE,
        HIGH_CPU_USAGE,
        LOW_FRAME_RATE,
        ANR,
        NETWORK_DISCONNECTED,
        STORAGE_LOW
    }
    
    enum class AlertSeverity {
        INFO, WARNING, CRITICAL
    }
    
    enum class NetworkType {
        NONE, WIFI, CELLULAR, ETHERNET, OTHER
    }
    
    enum class MemoryTrend {
        INCREASING, DECREASING, STABLE
    }
    
    data class PerformanceReport(
        val startTime: Long,
        val endTime: Long,
        val totalMetrics: Int,
        val averageMetrics: PerformanceMetrics,
        val peakMetrics: PerformanceMetrics,
        val alerts: List<PerformanceAlert>,
        val suggestions: List<String>,
        val memoryTrend: MemoryTrend
    )
    
    // ==================== 公共API ====================
    
    /**
     * 🚀 开始性能监控
     */
    fun startMonitoring() {
        if (isMonitoring) {
            Log.w(TAG, "Performance monitoring already started")
            return
        }
        
        isMonitoring = true
        
        // 启动ANR检测
        anrDetector.startDetection()
        
        // 启动定期监控
        monitorScope.launch {
            while (isMonitoring) {
                try {
                    collectMetrics()
                    delay(MONITOR_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in performance monitoring", e)
                }
            }
        }
        
        Log.d(TAG, "Performance monitoring started")
    }
    
    /**
     * ⏹️ 停止性能监控
     */
    fun stopMonitoring() {
        isMonitoring = false
        anrDetector.stopDetection()
        Log.d(TAG, "Performance monitoring stopped")
    }
    
    /**
     * 📊 收集性能指标
     */
    private suspend fun collectMetrics() {
        withContext(Dispatchers.Default) {
            val metrics = PerformanceMetrics(
                cpu = cpuMonitor.getCurrentCpuUsage(),
                memory = memoryMonitor.getCurrentMemoryMetrics(),
                network = networkMonitor.getCurrentNetworkMetrics(),
                frame = frameMonitor.getCurrentFrameMetrics()
            )
            
            // 更新当前指标
            _currentMetrics.value = metrics
            
            // 添加到历史记录
            synchronized(metricsHistory) {
                metricsHistory.add(metrics)
                if (metricsHistory.size > maxHistorySize) {
                    metricsHistory.removeAt(0)
                }
            }
            
            // 分析性能并生成警告
            val newAlerts = performanceAnalyzer.analyzePerformance(metrics)
            if (newAlerts.isNotEmpty()) {
                addAlerts(newAlerts)
            }
        }
    }
    
    /**
     * 🎮 记录帧渲染
     */
    fun recordFrame() {
        frameMonitor.recordFrame()
    }
    
    /**
     * 📝 记录事件
     */
    fun recordEvent(eventName: String, properties: Map<String, Any> = emptyMap()) {
        Log.d(TAG, "Event recorded: $eventName with properties: $properties")
        // 这里可以添加事件记录逻辑
    }
    
    /**
     * ⚠️ 添加警告
     */
    private fun addAlert(alert: PerformanceAlert) {
        val currentAlerts = _alerts.value.toMutableList()
        currentAlerts.add(alert)
        
        // 保持最近50个警告
        if (currentAlerts.size > 50) {
            currentAlerts.removeAt(0)
        }
        
        _alerts.value = currentAlerts
    }
    
    private fun addAlerts(alerts: List<PerformanceAlert>) {
        alerts.forEach { addAlert(it) }
    }
    
    /**
     * 🧹 清除警告
     */
    fun clearAlerts() {
        _alerts.value = emptyList()
    }
    
    /**
     * 📊 获取性能报告
     */
    fun generatePerformanceReport(
        startTime: Long = System.currentTimeMillis() - 3600_000L // 默认最近1小时
    ): PerformanceReport {
        val filteredMetrics = synchronized(metricsHistory) {
            metricsHistory.filter { it.timestamp >= startTime }
        }
        
        if (filteredMetrics.isEmpty()) {
            return PerformanceReport(
                startTime = startTime,
                endTime = System.currentTimeMillis(),
                totalMetrics = 0,
                averageMetrics = PerformanceMetrics(),
                peakMetrics = PerformanceMetrics(),
                alerts = _alerts.value.filter { it.timestamp >= startTime },
                suggestions = emptyList(),
                memoryTrend = MemoryTrend.STABLE
            )
        }
        
        val avgCpu = filteredMetrics.map { it.cpu.appCpuUsage }.average().toFloat()
        val avgMemory = filteredMetrics.map { it.memory.memoryUsageRatio }.average().toFloat()
        val avgFps = filteredMetrics.map { it.frame.currentFps }.average().toFloat()
        
        val peakCpu = filteredMetrics.maxByOrNull { it.cpu.appCpuUsage }?.cpu ?: CpuMetrics()
        val peakMemory = filteredMetrics.maxByOrNull { it.memory.memoryUsageRatio }?.memory ?: MemoryMetrics()
        
        val averageMetrics = PerformanceMetrics(
            cpu = CpuMetrics(appCpuUsage = avgCpu),
            memory = MemoryMetrics(memoryUsageRatio = avgMemory),
            frame = FrameMetrics(currentFps = avgFps)
        )
        
        val peakMetrics = PerformanceMetrics(
            cpu = peakCpu,
            memory = peakMemory
        )
        
        val recentAlerts = _alerts.value.filter { it.timestamp >= startTime }
        val suggestions = performanceAnalyzer.generateOptimizationSuggestions(averageMetrics)
        val memoryTrend = memoryMonitor.getMemoryTrend()
        
        return PerformanceReport(
            startTime = startTime,
            endTime = System.currentTimeMillis(),
            totalMetrics = filteredMetrics.size,
            averageMetrics = averageMetrics,
            peakMetrics = peakMetrics,
            alerts = recentAlerts,
            suggestions = suggestions,
            memoryTrend = memoryTrend
        )
    }
    
    /**
     * 📤 导出性能数据
     */
    fun exportPerformanceData(format: ExportFormat = ExportFormat.JSON): String {
        val report = generatePerformanceReport()
        
        return when (format) {
            ExportFormat.JSON -> exportAsJson(report)
            ExportFormat.CSV -> exportAsCsv(report)
        }
    }
    
    private fun exportAsJson(report: PerformanceReport): String {
        // 简化的JSON导出
        return """
        {
            "startTime": ${report.startTime},
            "endTime": ${report.endTime},
            "totalMetrics": ${report.totalMetrics},
            "averageCpuUsage": ${report.averageMetrics.cpu.appCpuUsage},
            "averageMemoryUsage": ${report.averageMetrics.memory.memoryUsageRatio},
            "averageFps": ${report.averageMetrics.frame.currentFps},
            "alertCount": ${report.alerts.size},
            "suggestionCount": ${report.suggestions.size}
        }
        """.trimIndent()
    }
    
    private fun exportAsCsv(report: PerformanceReport): String {
        val header = "Timestamp,CPU_Usage,Memory_Usage,FPS,Network_Speed\n"
        val data = synchronized(metricsHistory) {
            metricsHistory.joinToString("\n") { metrics ->
                "${metrics.timestamp},${metrics.cpu.appCpuUsage},${metrics.memory.memoryUsageRatio},${metrics.frame.currentFps},${metrics.network.downloadSpeed}"
            }
        }
        return header + data
    }
    
    enum class ExportFormat {
        JSON, CSV
    }
    
    /**
     * 🎯 获取当前性能状态
     */
    fun getCurrentPerformanceData(): PerformanceMetrics {
        return _currentMetrics.value
    }
    
    /**
     * 📈 获取性能历史数据
     */
    fun getPerformanceHistory(count: Int = 100): List<PerformanceMetrics> {
        return synchronized(metricsHistory) {
            metricsHistory.takeLast(count)
        }
    }
    
    /**
     * 🔄 重置性能统计
     */
    fun resetStatistics() {
        synchronized(metricsHistory) {
            metricsHistory.clear()
        }
        clearAlerts()
        frameMonitor.resetFrameStats()
        Log.d(TAG, "Performance statistics reset")
    }
    
    /**
     * 🔧 释放资源
     */
    fun shutdown() {
        stopMonitoring()
        monitorScope.cancel()
        Log.d(TAG, "PerformanceMonitor shutdown")
    }
}