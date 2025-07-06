package com.ai.assistance.operit.core

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 性能监控器
 * 负责监控应用性能指标，包括CPU、内存、网络、UI渲染等
 */
class PerformanceMonitor private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: PerformanceMonitor? = null
        
        fun getInstance(context: Context): PerformanceMonitor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PerformanceMonitor(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        private const val TAG = "PerformanceMonitor"
        private const val MONITORING_INTERVAL = 5000L // 5秒
        private const val MAX_PERFORMANCE_RECORDS = 1000
        private const val PERFORMANCE_LOG_FILE = "performance_log.txt"
        
        // 性能阈值
        private const val HIGH_MEMORY_THRESHOLD = 0.85f
        private const val HIGH_CPU_THRESHOLD = 80f
        private const val SLOW_FRAME_THRESHOLD = 16f // 16ms
        private const val ANR_THRESHOLD = 5000L // 5秒
    }
    
    // 监控状态
    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()
    
    // 性能数据
    private val _performanceData = MutableStateFlow(PerformanceData())
    val performanceData: StateFlow<PerformanceData> = _performanceData.asStateFlow()
    
    // 性能记录
    private val performanceRecords = mutableListOf<PerformanceRecord>()
    private val performanceAlerts = mutableListOf<PerformanceAlert>()
    
    // 性能计数器
    private val performanceCounters = ConcurrentHashMap<String, AtomicLong>()
    
    // 协程管理
    private val monitorScope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() + CoroutineName("PerformanceMonitor")
    )
    
    // 系统服务
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val memoryInfo = ActivityManager.MemoryInfo()
    
    // 主线程Handler
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // ANR检测
    private var lastMainThreadUpdate = System.currentTimeMillis()
    private val anrWatchdog = Runnable {
        lastMainThreadUpdate = System.currentTimeMillis()
        scheduleAnrCheck()
    }
    
    init {
        initializeCounters()
    }
    
    /**
     * 初始化性能计数器
     */
    private fun initializeCounters() {
        performanceCounters["app_launches"] = AtomicLong(0)
        performanceCounters["screen_navigations"] = AtomicLong(0)
        performanceCounters["api_calls"] = AtomicLong(0)
        performanceCounters["crashes"] = AtomicLong(0)
        performanceCounters["anr_events"] = AtomicLong(0)
        performanceCounters["memory_warnings"] = AtomicLong(0)
        performanceCounters["frame_drops"] = AtomicLong(0)
    }
    
    /**
     * 开始性能监控
     */
    fun startMonitoring() {
        if (_isMonitoring.value) return
        
        _isMonitoring.value = true
        
        monitorScope.launch {
            while (_isMonitoring.value) {
                try {
                    collectPerformanceData()
                    delay(MONITORING_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "Error collecting performance data", e)
                }
            }
        }
        
        // 启动ANR检测
        scheduleAnrCheck()
        
        Log.i(TAG, "Performance monitoring started")
    }
    
    /**
     * 停止性能监控
     */
    fun stopMonitoring() {
        _isMonitoring.value = false
        mainHandler.removeCallbacks(anrWatchdog)
        Log.i(TAG, "Performance monitoring stopped")
    }
    
    /**
     * 收集性能数据
     */
    private suspend fun collectPerformanceData() {
        val timestamp = System.currentTimeMillis()
        
        // 收集内存信息
        val memoryMetrics = collectMemoryMetrics()
        
        // 收集CPU信息
        val cpuMetrics = collectCpuMetrics()
        
        // 收集网络信息
        val networkMetrics = collectNetworkMetrics()
        
        // 收集渲染信息
        val renderMetrics = collectRenderMetrics()
        
        // 创建性能数据
        val newPerformanceData = PerformanceData(
            timestamp = timestamp,
            memoryMetrics = memoryMetrics,
            cpuMetrics = cpuMetrics,
            networkMetrics = networkMetrics,
            renderMetrics = renderMetrics,
            counters = performanceCounters.mapValues { it.value.get() }
        )
        
        // 更新性能数据
        _performanceData.value = newPerformanceData
        
        // 添加到记录
        addPerformanceRecord(newPerformanceData)
        
        // 检查性能警告
        checkPerformanceAlerts(newPerformanceData)
    }
    
    /**
     * 收集内存指标
     */
    private fun collectMemoryMetrics(): MemoryMetrics {
        activityManager.getMemoryInfo(memoryInfo)
        
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val memoryUsageRatio = usedMemory.toFloat() / maxMemory.toFloat()
        
        // 获取详细内存信息
        val debugMemInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(debugMemInfo)
        
        return MemoryMetrics(
            usedMemory = usedMemory,
            totalMemory = runtime.totalMemory(),
            maxMemory = maxMemory,
            availableMemory = memoryInfo.availMem,
            memoryUsageRatio = memoryUsageRatio,
            isLowMemory = memoryInfo.lowMemory,
            dalvikPss = debugMemInfo.dalvikPss,
            nativePss = debugMemInfo.nativePss,
            otherPss = debugMemInfo.otherPss,
            totalPss = debugMemInfo.totalPss
        )
    }
    
    /**
     * 收集CPU指标
     */
    private fun collectCpuMetrics(): CpuMetrics {
        // 读取/proc/stat获取CPU使用率
        val cpuUsage = getCpuUsage()
        
        return CpuMetrics(
            appCpuUsage = cpuUsage,
            systemCpuUsage = getSystemCpuUsage(),
            threadCount = getThreadCount(),
            activeProcessors = Runtime.getRuntime().availableProcessors()
        )
    }
    
    /**
     * 收集网络指标
     */
    private fun collectNetworkMetrics(): NetworkMetrics {
        // 获取网络流量信息
        val rxBytes = android.net.TrafficStats.getUidRxBytes(android.os.Process.myUid())
        val txBytes = android.net.TrafficStats.getUidTxBytes(android.os.Process.myUid())
        
        return NetworkMetrics(
            rxBytes = if (rxBytes == android.net.TrafficStats.UNSUPPORTED.toLong()) 0 else rxBytes,
            txBytes = if (txBytes == android.net.TrafficStats.UNSUPPORTED.toLong()) 0 else txBytes,
            connectionCount = getActiveConnectionCount()
        )
    }
    
    /**
     * 收集渲染指标
     */
    private fun collectRenderMetrics(): RenderMetrics {
        return RenderMetrics(
            frameDrops = performanceCounters["frame_drops"]?.get() ?: 0,
            avgFrameTime = getAverageFrameTime(),
            jankFrames = getJankFrameCount()
        )
    }
    
    /**
     * 获取CPU使用率
     */
    private fun getCpuUsage(): Float {
        return try {
            val proc = Runtime.getRuntime().exec("top -n 1 -p ${android.os.Process.myPid()}")
            val reader = proc.inputStream.bufferedReader()
            var cpuUsage = 0f
            
            reader.useLines { lines ->
                lines.forEach { line ->
                    if (line.contains(android.os.Process.myPid().toString())) {
                        val parts = line.trim().split("\\s+".toRegex())
                        if (parts.size > 8) {
                            cpuUsage = parts[8].replace("%", "").toFloatOrNull() ?: 0f
                        }
                    }
                }
            }
            
            cpuUsage
        } catch (e: Exception) {
            0f
        }
    }
    
    /**
     * 获取系统CPU使用率
     */
    private fun getSystemCpuUsage(): Float {
        return try {
            val statFile = File("/proc/stat")
            val lines = statFile.readLines()
            val cpuLine = lines.first { it.startsWith("cpu ") }
            val values = cpuLine.split("\\s+".toRegex()).drop(1).map { it.toLong() }
            
            val idle = values[3]
            val total = values.sum()
            
            ((total - idle).toFloat() / total.toFloat()) * 100f
        } catch (e: Exception) {
            0f
        }
    }
    
    /**
     * 获取线程数量
     */
    private fun getThreadCount(): Int {
        return try {
            val statusFile = File("/proc/self/status")
            statusFile.readLines().find { it.startsWith("Threads:") }
                ?.split("\\s+".toRegex())
                ?.get(1)
                ?.toInt() ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * 获取活跃连接数
     */
    private fun getActiveConnectionCount(): Int {
        // 简化实现，实际应该读取/proc/net/tcp等
        return 0
    }
    
    /**
     * 获取平均帧时间
     */
    private fun getAverageFrameTime(): Float {
        // 简化实现，实际应该使用FrameMetricsAggregator
        return 16.67f // 60fps对应的帧时间
    }
    
    /**
     * 获取卡顿帧数
     */
    private fun getJankFrameCount(): Long {
        return performanceCounters["frame_drops"]?.get() ?: 0
    }
    
    /**
     * 添加性能记录
     */
    private fun addPerformanceRecord(data: PerformanceData) {
        synchronized(performanceRecords) {
            performanceRecords.add(PerformanceRecord(
                timestamp = data.timestamp,
                memoryUsage = data.memoryMetrics.memoryUsageRatio,
                cpuUsage = data.cpuMetrics.appCpuUsage,
                frameTime = data.renderMetrics.avgFrameTime
            ))
            
            // 限制记录数量
            if (performanceRecords.size > MAX_PERFORMANCE_RECORDS) {
                performanceRecords.removeAt(0)
            }
        }
    }
    
    /**
     * 检查性能警告
     */
    private fun checkPerformanceAlerts(data: PerformanceData) {
        val alerts = mutableListOf<PerformanceAlert>()
        
        // 内存警告
        if (data.memoryMetrics.memoryUsageRatio > HIGH_MEMORY_THRESHOLD) {
            alerts.add(PerformanceAlert(
                type = AlertType.HIGH_MEMORY,
                message = "High memory usage: ${(data.memoryMetrics.memoryUsageRatio * 100).toInt()}%",
                timestamp = data.timestamp,
                severity = AlertSeverity.WARNING
            ))
            performanceCounters["memory_warnings"]?.incrementAndGet()
        }
        
        // CPU警告
        if (data.cpuMetrics.appCpuUsage > HIGH_CPU_THRESHOLD) {
            alerts.add(PerformanceAlert(
                type = AlertType.HIGH_CPU,
                message = "High CPU usage: ${data.cpuMetrics.appCpuUsage.toInt()}%",
                timestamp = data.timestamp,
                severity = AlertSeverity.WARNING
            ))
        }
        
        // 渲染警告
        if (data.renderMetrics.avgFrameTime > SLOW_FRAME_THRESHOLD) {
            alerts.add(PerformanceAlert(
                type = AlertType.SLOW_RENDERING,
                message = "Slow frame time: ${data.renderMetrics.avgFrameTime}ms",
                timestamp = data.timestamp,
                severity = AlertSeverity.INFO
            ))
        }
        
        // 添加警告
        synchronized(performanceAlerts) {
            performanceAlerts.addAll(alerts)
            // 限制警告数量
            if (performanceAlerts.size > 100) {
                performanceAlerts.removeAt(0)
            }
        }
        
        // 记录到日志
        alerts.forEach { alert ->
            when (alert.severity) {
                AlertSeverity.ERROR -> Log.e(TAG, alert.message)
                AlertSeverity.WARNING -> Log.w(TAG, alert.message)
                AlertSeverity.INFO -> Log.i(TAG, alert.message)
            }
        }
    }
    
    /**
     * 调度ANR检查
     */
    private fun scheduleAnrCheck() {
        mainHandler.post(anrWatchdog)
        
        // 后台检查ANR
        monitorScope.launch {
            delay(ANR_THRESHOLD)
            val timeSinceUpdate = System.currentTimeMillis() - lastMainThreadUpdate
            
            if (timeSinceUpdate > ANR_THRESHOLD) {
                // 检测到ANR
                performanceCounters["anr_events"]?.incrementAndGet()
                
                val alert = PerformanceAlert(
                    type = AlertType.ANR,
                    message = "ANR detected: Main thread blocked for ${timeSinceUpdate}ms",
                    timestamp = System.currentTimeMillis(),
                    severity = AlertSeverity.ERROR
                )
                
                synchronized(performanceAlerts) {
                    performanceAlerts.add(alert)
                }
                
                Log.e(TAG, alert.message)
            }
            
            // 重新调度
            if (_isMonitoring.value) {
                scheduleAnrCheck()
            }
        }
    }
    
    /**
     * 记录性能事件
     */
    fun recordEvent(eventName: String) {
        performanceCounters[eventName]?.incrementAndGet()
    }
    
    /**
     * 获取性能报告
     */
    fun getPerformanceReport(): PerformanceReport {
        return PerformanceReport(
            timestamp = System.currentTimeMillis(),
            currentData = _performanceData.value,
            alerts = performanceAlerts.toList(),
            records = performanceRecords.toList(),
            counters = performanceCounters.mapValues { it.value.get() }
        )
    }
    
    /**
     * 导出性能日志
     */
    suspend fun exportPerformanceLog(): File {
        return withContext(Dispatchers.IO) {
            val logFile = File(context.getExternalFilesDir("logs"), PERFORMANCE_LOG_FILE)
            logFile.parentFile?.mkdirs()
            
            FileWriter(logFile).use { writer ->
                writer.appendLine("Performance Log - ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                writer.appendLine("=" * 50)
                writer.appendLine()
                
                // 写入计数器
                writer.appendLine("Performance Counters:")
                performanceCounters.forEach { (key, value) ->
                    writer.appendLine("  $key: ${value.get()}")
                }
                writer.appendLine()
                
                // 写入警告
                writer.appendLine("Performance Alerts:")
                performanceAlerts.forEach { alert ->
                    writer.appendLine("  [${alert.severity}] ${alert.type}: ${alert.message}")
                }
                writer.appendLine()
                
                // 写入记录
                writer.appendLine("Performance Records (last 50):")
                performanceRecords.takeLast(50).forEach { record ->
                    writer.appendLine("  ${record.timestamp}: Memory=${(record.memoryUsage * 100).toInt()}%, CPU=${record.cpuUsage.toInt()}%, Frame=${record.frameTime}ms")
                }
            }
            
            logFile
        }
    }
    
    /**
     * 清理性能数据
     */
    fun clearPerformanceData() {
        synchronized(performanceRecords) {
            performanceRecords.clear()
        }
        synchronized(performanceAlerts) {
            performanceAlerts.clear()
        }
        performanceCounters.values.forEach { it.set(0) }
    }
    
    // ==================== 数据类定义 ====================
    
    data class PerformanceData(
        val timestamp: Long = System.currentTimeMillis(),
        val memoryMetrics: MemoryMetrics = MemoryMetrics(),
        val cpuMetrics: CpuMetrics = CpuMetrics(),
        val networkMetrics: NetworkMetrics = NetworkMetrics(),
        val renderMetrics: RenderMetrics = RenderMetrics(),
        val counters: Map<String, Long> = emptyMap()
    )
    
    data class MemoryMetrics(
        val usedMemory: Long = 0,
        val totalMemory: Long = 0,
        val maxMemory: Long = 0,
        val availableMemory: Long = 0,
        val memoryUsageRatio: Float = 0f,
        val isLowMemory: Boolean = false,
        val dalvikPss: Int = 0,
        val nativePss: Int = 0,
        val otherPss: Int = 0,
        val totalPss: Int = 0
    )
    
    data class CpuMetrics(
        val appCpuUsage: Float = 0f,
        val systemCpuUsage: Float = 0f,
        val threadCount: Int = 0,
        val activeProcessors: Int = 0
    )
    
    data class NetworkMetrics(
        val rxBytes: Long = 0,
        val txBytes: Long = 0,
        val connectionCount: Int = 0
    )
    
    data class RenderMetrics(
        val frameDrops: Long = 0,
        val avgFrameTime: Float = 0f,
        val jankFrames: Long = 0
    )
    
    data class PerformanceRecord(
        val timestamp: Long,
        val memoryUsage: Float,
        val cpuUsage: Float,
        val frameTime: Float
    )
    
    data class PerformanceAlert(
        val type: AlertType,
        val message: String,
        val timestamp: Long,
        val severity: AlertSeverity
    )
    
    data class PerformanceReport(
        val timestamp: Long,
        val currentData: PerformanceData,
        val alerts: List<PerformanceAlert>,
        val records: List<PerformanceRecord>,
        val counters: Map<String, Long>
    )
    
    enum class AlertType {
        HIGH_MEMORY, HIGH_CPU, ANR, SLOW_RENDERING, NETWORK_ERROR
    }
    
    enum class AlertSeverity {
        INFO, WARNING, ERROR
    }
}

// 扩展函数
private operator fun String.times(count: Int): String {
    return this.repeat(count)
}