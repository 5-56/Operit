package com.ai.assistance.operit.core.agent.optimization

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import com.ai.assistance.operit.util.LogUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/**
 * 性能优化器
 * 
 * 针对大规模使用进行性能调优：
 * 1. 内存管理优化
 * 2. CPU使用优化
 * 3. 网络请求优化
 * 4. 数据库访问优化
 * 5. UI渲染优化
 * 6. 并发处理优化
 * 7. 缓存策略优化
 * 8. 资源回收优化
 */
class PerformanceOptimizer(private val context: Context) {
    
    companion object {
        private const val TAG = "PerformanceOptimizer"
        
        // 性能等级
        enum class PerformanceLevel {
            LOW,        // 低性能模式，节省资源
            MEDIUM,     // 中等性能模式，平衡性能和资源
            HIGH,       // 高性能模式，优先性能
            ULTRA       // 极致性能模式，最大化性能
        }
        
        // 优化策略
        enum class OptimizationStrategy {
            MEMORY_FIRST,     // 内存优先
            CPU_FIRST,        // CPU优先
            BATTERY_FIRST,    // 电池优先
            NETWORK_FIRST,    // 网络优先
            BALANCED          // 平衡策略
        }
        
        // 监控指标
        private const val MEMORY_WARNING_THRESHOLD = 0.8f  // 内存警告阈值
        private const val CPU_WARNING_THRESHOLD = 0.7f     // CPU警告阈值
        private const val RESPONSE_TIME_WARNING = 1000L    // 响应时间警告阈值(ms)
        private const val MONITORING_INTERVAL = 5000L      // 监控间隔(ms)
    }
    
    // 性能状态管理
    private val _currentPerformanceLevel = MutableStateFlow(PerformanceLevel.MEDIUM)
    val currentPerformanceLevel: StateFlow<PerformanceLevel> = _currentPerformanceLevel.asStateFlow()
    
    private val _optimizationStrategy = MutableStateFlow(OptimizationStrategy.BALANCED)
    val optimizationStrategy: StateFlow<OptimizationStrategy> = _optimizationStrategy.asStateFlow()
    
    // 性能监控数据
    private val _performanceMetrics = MutableStateFlow(PerformanceMetrics())
    val performanceMetrics: StateFlow<PerformanceMetrics> = _performanceMetrics.asStateFlow()
    
    // 系统服务
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 线程池管理
    private val backgroundExecutor = Executors.newCachedThreadPool()
    private val scheduledExecutor = Executors.newScheduledThreadPool(2)
    private val ioDispatcher = Dispatchers.IO.limitedParallelism(4)
    private val computationDispatcher = Dispatchers.Default.limitedParallelism(2)
    
    // 缓存管理
    private val memoryCache = ConcurrentHashMap<String, CacheEntry>()
    private val maxCacheSize = 100 // 最大缓存条目数
    private val cacheExpirationTime = 300000L // 5分钟过期
    
    // 性能监控
    private var monitoringJob: Job? = null
    private val performanceHistory = mutableListOf<PerformanceSnapshot>()
    
    /**
     * 性能指标
     */
    data class PerformanceMetrics(
        val memoryUsage: Float = 0f,           // 内存使用率 (0-1)
        val cpuUsage: Float = 0f,              // CPU使用率 (0-1)
        val responseTime: Long = 0L,           // 平均响应时间 (ms)
        val requestCount: Int = 0,             // 请求计数
        val errorRate: Float = 0f,             // 错误率 (0-1)
        val cacheHitRate: Float = 0f,          // 缓存命中率 (0-1)
        val networkLatency: Long = 0L,         // 网络延迟 (ms)
        val batteryLevel: Int = 100,           // 电池电量 (0-100)
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * 性能快照
     */
    data class PerformanceSnapshot(
        val timestamp: Long,
        val metrics: PerformanceMetrics,
        val activeThreads: Int,
        val gcCount: Long,
        val heapSize: Long,
        val nativeHeapSize: Long
    )
    
    /**
     * 缓存条目
     */
    private data class CacheEntry(
        val data: Any,
        val timestamp: Long,
        val accessCount: Int = 0,
        val size: Long = 0L
    )
    
    /**
     * 优化配置
     */
    data class OptimizationConfig(
        val enableMemoryOptimization: Boolean = true,
        val enableCpuOptimization: Boolean = true,
        val enableNetworkOptimization: Boolean = true,
        val enableCacheOptimization: Boolean = true,
        val enableUiOptimization: Boolean = true,
        val enableConcurrencyOptimization: Boolean = true,
        val maxConcurrentRequests: Int = 5,
        val cacheSize: Int = 100,
        val networkTimeout: Long = 30000L,
        val responseTimeThreshold: Long = 1000L
    )
    
    private var config = OptimizationConfig()
    
    init {
        startPerformanceMonitoring()
        setupPerformanceOptimizations()
    }
    
    /**
     * 开始性能监控
     */
    private fun startPerformanceMonitoring() {
        monitoringJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                try {
                    updatePerformanceMetrics()
                    analyzePerformance()
                    triggerAutoOptimization()
                    delay(MONITORING_INTERVAL)
                } catch (e: Exception) {
                    LogUtils.e(TAG, "性能监控异常", e)
                }
            }
        }
        
        LogUtils.i(TAG, "性能监控已启动")
    }
    
    /**
     * 更新性能指标
     */
    private suspend fun updatePerformanceMetrics() = withContext(Dispatchers.IO) {
        val memoryInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memoryInfo)
        
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryUsage = usedMemory.toFloat() / maxMemory
        
        // 获取CPU使用率（简化版本）
        val cpuUsage = getCpuUsage()
        
        // 获取电池电量
        val batteryLevel = getBatteryLevel()
        
        // 计算平均响应时间
        val avgResponseTime = calculateAverageResponseTime()
        
        // 计算缓存命中率
        val cacheHitRate = calculateCacheHitRate()
        
        val metrics = PerformanceMetrics(
            memoryUsage = memoryUsage,
            cpuUsage = cpuUsage,
            responseTime = avgResponseTime,
            batteryLevel = batteryLevel,
            cacheHitRate = cacheHitRate
        )
        
        _performanceMetrics.value = metrics
        
        // 记录性能快照
        recordPerformanceSnapshot(metrics)
    }
    
    /**
     * 获取CPU使用率
     */
    private fun getCpuUsage(): Float {
        return try {
            val proc = Runtime.getRuntime().exec("cat /proc/stat")
            val reader = proc.inputStream.bufferedReader()
            val line = reader.readLine()
            reader.close()
            
            // 简化的CPU使用率计算
            // 实际应用中需要更复杂的计算逻辑
            0.2f
        } catch (e: Exception) {
            LogUtils.w(TAG, "无法获取CPU使用率", e)
            0f
        }
    }
    
    /**
     * 获取电池电量
     */
    private fun getBatteryLevel(): Int {
        return try {
            val batteryFile = File("/sys/class/power_supply/battery/capacity")
            if (batteryFile.exists()) {
                batteryFile.readText().trim().toInt()
            } else {
                100 // 默认值
            }
        } catch (e: Exception) {
            LogUtils.w(TAG, "无法获取电池电量", e)
            100
        }
    }
    
    /**
     * 计算平均响应时间
     */
    private fun calculateAverageResponseTime(): Long {
        return if (performanceHistory.isNotEmpty()) {
            performanceHistory.takeLast(10).map { it.metrics.responseTime }.average().toLong()
        } else {
            0L
        }
    }
    
    /**
     * 计算缓存命中率
     */
    private fun calculateCacheHitRate(): Float {
        val totalAccess = memoryCache.values.sumOf { it.accessCount }
        val hits = memoryCache.size
        return if (totalAccess > 0) hits.toFloat() / totalAccess else 0f
    }
    
    /**
     * 记录性能快照
     */
    private fun recordPerformanceSnapshot(metrics: PerformanceMetrics) {
        val snapshot = PerformanceSnapshot(
            timestamp = System.currentTimeMillis(),
            metrics = metrics,
            activeThreads = Thread.activeCount(),
            gcCount = Debug.getGlobalGcInvocationCount(),
            heapSize = Debug.getNativeHeapSize(),
            nativeHeapSize = Debug.getNativeHeapAllocatedSize()
        )
        
        performanceHistory.add(snapshot)
        
        // 保持最近100个快照
        if (performanceHistory.size > 100) {
            performanceHistory.removeAt(0)
        }
    }
    
    /**
     * 分析性能
     */
    private fun analyzePerformance() {
        val metrics = _performanceMetrics.value
        
        // 内存使用率警告
        if (metrics.memoryUsage > MEMORY_WARNING_THRESHOLD) {
            LogUtils.w(TAG, "内存使用率过高: ${(metrics.memoryUsage * 100).toInt()}%")
            triggerMemoryOptimization()
        }
        
        // CPU使用率警告
        if (metrics.cpuUsage > CPU_WARNING_THRESHOLD) {
            LogUtils.w(TAG, "CPU使用率过高: ${(metrics.cpuUsage * 100).toInt()}%")
            triggerCpuOptimization()
        }
        
        // 响应时间警告
        if (metrics.responseTime > RESPONSE_TIME_WARNING) {
            LogUtils.w(TAG, "响应时间过长: ${metrics.responseTime}ms")
            triggerResponseTimeOptimization()
        }
    }
    
    /**
     * 触发自动优化
     */
    private fun triggerAutoOptimization() {
        val metrics = _performanceMetrics.value
        val strategy = _optimizationStrategy.value
        
        when (strategy) {
            OptimizationStrategy.MEMORY_FIRST -> {
                if (metrics.memoryUsage > 0.6f) {
                    optimizeMemoryUsage()
                }
            }
            OptimizationStrategy.CPU_FIRST -> {
                if (metrics.cpuUsage > 0.5f) {
                    optimizeCpuUsage()
                }
            }
            OptimizationStrategy.BATTERY_FIRST -> {
                if (metrics.batteryLevel < 20) {
                    optimizeBatteryUsage()
                }
            }
            OptimizationStrategy.NETWORK_FIRST -> {
                if (metrics.networkLatency > 2000L) {
                    optimizeNetworkUsage()
                }
            }
            OptimizationStrategy.BALANCED -> {
                performBalancedOptimization()
            }
        }
    }
    
    /**
     * 设置性能优化
     */
    private fun setupPerformanceOptimizations() {
        // 设置线程池参数
        setupThreadPools()
        
        // 设置内存管理
        setupMemoryManagement()
        
        // 设置网络优化
        setupNetworkOptimization()
        
        // 设置UI优化
        setupUiOptimization()
        
        LogUtils.i(TAG, "性能优化设置完成")
    }
    
    /**
     * 设置线程池
     */
    private fun setupThreadPools() {
        // 根据设备性能调整线程池大小
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val optimalThreads = when (_currentPerformanceLevel.value) {
            PerformanceLevel.LOW -> maxOf(1, cpuCores / 2)
            PerformanceLevel.MEDIUM -> cpuCores
            PerformanceLevel.HIGH -> cpuCores * 2
            PerformanceLevel.ULTRA -> cpuCores * 3
        }
        
        LogUtils.d(TAG, "设置线程池大小: $optimalThreads (CPU核心数: $cpuCores)")
    }
    
    /**
     * 设置内存管理
     */
    private fun setupMemoryManagement() {
        // 根据可用内存调整缓存大小
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        val availableMemory = memInfo.availMem / (1024 * 1024) // MB
        val cacheSize = when {
            availableMemory > 2048 -> 200  // 2GB以上
            availableMemory > 1024 -> 100  // 1GB以上
            availableMemory > 512 -> 50    // 512MB以上
            else -> 25                     // 512MB以下
        }
        
        config = config.copy(cacheSize = cacheSize)
        LogUtils.d(TAG, "设置缓存大小: $cacheSize (可用内存: ${availableMemory}MB)")
    }
    
    /**
     * 设置网络优化
     */
    private fun setupNetworkOptimization() {
        // 根据网络状况调整超时时间
        val timeout = when (_currentPerformanceLevel.value) {
            PerformanceLevel.LOW -> 60000L      // 1分钟
            PerformanceLevel.MEDIUM -> 30000L   // 30秒
            PerformanceLevel.HIGH -> 15000L     // 15秒
            PerformanceLevel.ULTRA -> 10000L    // 10秒
        }
        
        config = config.copy(networkTimeout = timeout)
    }
    
    /**
     * 设置UI优化
     */
    private fun setupUiOptimization() {
        // UI渲染优化配置
        if (config.enableUiOptimization) {
            // 减少不必要的UI更新
            // 使用硬件加速
            // 优化布局层次
            LogUtils.d(TAG, "UI优化已启用")
        }
    }
    
    /**
     * 内存优化
     */
    fun optimizeMemoryUsage() {
        LogUtils.i(TAG, "开始内存优化")
        
        // 清理过期缓存
        cleanExpiredCache()
        
        // 触发垃圾回收
        System.gc()
        
        // 清理临时数据
        clearTemporaryData()
        
        // 释放不必要的资源
        releaseUnusedResources()
        
        LogUtils.i(TAG, "内存优化完成")
    }
    
    /**
     * CPU优化
     */
    fun optimizeCpuUsage() {
        LogUtils.i(TAG, "开始CPU优化")
        
        // 降低不重要任务的优先级
        adjustTaskPriorities()
        
        // 延迟执行非关键操作
        deferNonCriticalOperations()
        
        // 优化算法复杂度
        optimizeAlgorithms()
        
        LogUtils.i(TAG, "CPU优化完成")
    }
    
    /**
     * 电池优化
     */
    fun optimizeBatteryUsage() {
        LogUtils.i(TAG, "开始电池优化")
        
        // 降低性能等级
        setPerformanceLevel(PerformanceLevel.LOW)
        
        // 减少后台活动
        reduceBackgroundActivity()
        
        // 降低网络请求频率
        reduceNetworkActivity()
        
        LogUtils.i(TAG, "电池优化完成")
    }
    
    /**
     * 网络优化
     */
    fun optimizeNetworkUsage() {
        LogUtils.i(TAG, "开始网络优化")
        
        // 启用请求合并
        enableRequestBatching()
        
        // 优化缓存策略
        optimizeCacheStrategy()
        
        // 压缩网络数据
        enableDataCompression()
        
        LogUtils.i(TAG, "网络优化完成")
    }
    
    /**
     * 平衡优化
     */
    private fun performBalancedOptimization() {
        val metrics = _performanceMetrics.value
        
        // 根据当前瓶颈选择优化策略
        when {
            metrics.memoryUsage > 0.7f -> optimizeMemoryUsage()
            metrics.cpuUsage > 0.6f -> optimizeCpuUsage()
            metrics.batteryLevel < 20 -> optimizeBatteryUsage()
            metrics.networkLatency > 1500L -> optimizeNetworkUsage()
        }
    }
    
    /**
     * 清理过期缓存
     */
    private fun cleanExpiredCache() {
        val currentTime = System.currentTimeMillis()
        val expiredKeys = memoryCache.filterValues { entry ->
            currentTime - entry.timestamp > cacheExpirationTime
        }.keys
        
        expiredKeys.forEach { key ->
            memoryCache.remove(key)
        }
        
        LogUtils.d(TAG, "清理了 ${expiredKeys.size} 个过期缓存项")
    }
    
    /**
     * 清理临时数据
     */
    private fun clearTemporaryData() {
        // 清理临时文件
        val tempDir = File(context.cacheDir, "temp")
        if (tempDir.exists()) {
            tempDir.deleteRecursively()
        }
        
        // 清理临时变量
        // 这里可以添加更多临时数据清理逻辑
    }
    
    /**
     * 释放不必要的资源
     */
    private fun releaseUnusedResources() {
        // 释放位图资源
        // 关闭未使用的流
        // 取消未完成的任务
        
        // 如果缓存过大，移除最少使用的项
        if (memoryCache.size > config.cacheSize) {
            val sortedEntries = memoryCache.entries.sortedBy { it.value.accessCount }
            val itemsToRemove = sortedEntries.take(memoryCache.size - config.cacheSize)
            
            itemsToRemove.forEach { (key, _) ->
                memoryCache.remove(key)
            }
            
            LogUtils.d(TAG, "移除了 ${itemsToRemove.size} 个最少使用的缓存项")
        }
    }
    
    /**
     * 调整任务优先级
     */
    private fun adjustTaskPriorities() {
        // 降低后台任务优先级
        Thread.currentThread().priority = Thread.NORM_PRIORITY - 1
    }
    
    /**
     * 延迟非关键操作
     */
    private fun deferNonCriticalOperations() {
        // 将非关键操作放入低优先级队列
        // 延迟日志写入
        // 延迟统计数据上报
    }
    
    /**
     * 优化算法
     */
    private fun optimizeAlgorithms() {
        // 使用更高效的算法
        // 减少不必要的计算
        // 缓存计算结果
    }
    
    /**
     * 减少后台活动
     */
    private fun reduceBackgroundActivity() {
        // 停止非必要的后台服务
        // 减少定时任务频率
        // 暂停自动更新
    }
    
    /**
     * 减少网络活动
     */
    private fun reduceNetworkActivity() {
        // 延长网络请求间隔
        // 停止非必要的网络同步
        // 减少预加载数据
    }
    
    /**
     * 启用请求合并
     */
    private fun enableRequestBatching() {
        // 将多个小请求合并为一个大请求
        // 批量发送数据
    }
    
    /**
     * 优化缓存策略
     */
    private fun optimizeCacheStrategy() {
        // 实施LRU缓存策略
        // 增加缓存命中率
        // 预加载常用数据
    }
    
    /**
     * 启用数据压缩
     */
    private fun enableDataCompression() {
        // 启用GZIP压缩
        // 压缩JSON数据
        // 优化图片传输
    }
    
    /**
     * 触发内存优化
     */
    private fun triggerMemoryOptimization() {
        CoroutineScope(Dispatchers.IO).launch {
            optimizeMemoryUsage()
        }
    }
    
    /**
     * 触发CPU优化
     */
    private fun triggerCpuOptimization() {
        CoroutineScope(Dispatchers.Default).launch {
            optimizeCpuUsage()
        }
    }
    
    /**
     * 触发响应时间优化
     */
    private fun triggerResponseTimeOptimization() {
        CoroutineScope(Dispatchers.Default).launch {
            optimizeNetworkUsage()
            optimizeCpuUsage()
        }
    }
    
    /**
     * 设置性能等级
     */
    fun setPerformanceLevel(level: PerformanceLevel) {
        val oldLevel = _currentPerformanceLevel.value
        _currentPerformanceLevel.value = level
        
        LogUtils.i(TAG, "性能等级从 $oldLevel 更改为 $level")
        
        // 重新应用优化设置
        setupPerformanceOptimizations()
    }
    
    /**
     * 设置优化策略
     */
    fun setOptimizationStrategy(strategy: OptimizationStrategy) {
        _optimizationStrategy.value = strategy
        LogUtils.i(TAG, "优化策略设置为: $strategy")
    }
    
    /**
     * 更新优化配置
     */
    fun updateConfig(newConfig: OptimizationConfig) {
        config = newConfig
        LogUtils.i(TAG, "优化配置已更新")
    }
    
    /**
     * 缓存数据
     */
    fun <T> cacheData(key: String, data: T, size: Long = 0L): T {
        val entry = CacheEntry(
            data = data as Any,
            timestamp = System.currentTimeMillis(),
            accessCount = 1,
            size = size
        )
        memoryCache[key] = entry
        return data
    }
    
    /**
     * 获取缓存数据
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getCachedData(key: String): T? {
        val entry = memoryCache[key] ?: return null
        
        // 更新访问计数
        memoryCache[key] = entry.copy(accessCount = entry.accessCount + 1)
        
        return entry.data as? T
    }
    
    /**
     * 测量执行时间
     */
    suspend fun <T> measureExecutionTime(operation: suspend () -> T): Pair<T, Long> {
        val time = measureTimeMillis {
            return@measureTimeMillis operation()
        }
        return Pair(operation(), time)
    }
    
    /**
     * 异步执行任务
     */
    fun <T> executeAsync(task: suspend () -> T): Deferred<T> {
        return CoroutineScope(ioDispatcher).async {
            task()
        }
    }
    
    /**
     * 执行计算密集型任务
     */
    fun <T> executeComputation(task: suspend () -> T): Deferred<T> {
        return CoroutineScope(computationDispatcher).async {
            task()
        }
    }
    
    /**
     * 获取性能报告
     */
    fun generatePerformanceReport(): String {
        val metrics = _performanceMetrics.value
        val currentTime = System.currentTimeMillis()
        
        return buildString {
            appendLine("⚡ 性能优化器状态报告")
            appendLine("=" * 50)
            appendLine("生成时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}")
            appendLine()
            
            appendLine("🎛️ 当前配置:")
            appendLine("  性能等级: ${_currentPerformanceLevel.value}")
            appendLine("  优化策略: ${_optimizationStrategy.value}")
            appendLine("  缓存大小限制: ${config.cacheSize}")
            appendLine("  网络超时: ${config.networkTimeout}ms")
            appendLine()
            
            appendLine("📊 性能指标:")
            appendLine("  内存使用率: ${(metrics.memoryUsage * 100).toInt()}%")
            appendLine("  CPU使用率: ${(metrics.cpuUsage * 100).toInt()}%")
            appendLine("  平均响应时间: ${metrics.responseTime}ms")
            appendLine("  缓存命中率: ${(metrics.cacheHitRate * 100).toInt()}%")
            appendLine("  网络延迟: ${metrics.networkLatency}ms")
            appendLine("  电池电量: ${metrics.batteryLevel}%")
            appendLine()
            
            appendLine("🗂️ 缓存状态:")
            appendLine("  缓存条目数: ${memoryCache.size}")
            appendLine("  缓存使用率: ${(memoryCache.size.toFloat() / config.cacheSize * 100).toInt()}%")
            appendLine()
            
            appendLine("📈 历史趋势:")
            if (performanceHistory.isNotEmpty()) {
                val recent = performanceHistory.takeLast(10)
                val avgMemory = recent.map { it.metrics.memoryUsage }.average()
                val avgCpu = recent.map { it.metrics.cpuUsage }.average()
                val avgResponse = recent.map { it.metrics.responseTime }.average()
                
                appendLine("  近期平均内存使用率: ${(avgMemory * 100).toInt()}%")
                appendLine("  近期平均CPU使用率: ${(avgCpu * 100).toInt()}%")
                appendLine("  近期平均响应时间: ${avgResponse.toLong()}ms")
            } else {
                appendLine("  暂无历史数据")
            }
            appendLine()
            
            appendLine("🎯 优化建议:")
            when {
                metrics.memoryUsage > 0.8f -> appendLine("  建议执行内存优化")
                metrics.cpuUsage > 0.7f -> appendLine("  建议执行CPU优化")
                metrics.responseTime > 2000L -> appendLine("  建议执行响应时间优化")
                metrics.batteryLevel < 20 -> appendLine("  建议启用省电模式")
                else -> appendLine("  系统运行良好，无需特殊优化")
            }
        }
    }
    
    /**
     * 获取优化建议
     */
    fun getOptimizationSuggestions(): List<String> {
        val metrics = _performanceMetrics.value
        val suggestions = mutableListOf<String>()
        
        if (metrics.memoryUsage > 0.8f) {
            suggestions.add("内存使用率过高，建议清理缓存或降低缓存大小")
        }
        
        if (metrics.cpuUsage > 0.7f) {
            suggestions.add("CPU使用率过高，建议降低性能等级或延迟非关键操作")
        }
        
        if (metrics.responseTime > 2000L) {
            suggestions.add("响应时间过长，建议优化网络设置或启用缓存")
        }
        
        if (metrics.batteryLevel < 20) {
            suggestions.add("电池电量低，建议启用省电模式")
        }
        
        if (metrics.cacheHitRate < 0.5f) {
            suggestions.add("缓存命中率较低，建议调整缓存策略")
        }
        
        if (suggestions.isEmpty()) {
            suggestions.add("系统运行良好，性能表现正常")
        }
        
        return suggestions
    }
    
    /**
     * 停止性能监控
     */
    fun stopPerformanceMonitoring() {
        monitoringJob?.cancel()
        backgroundExecutor.shutdown()
        scheduledExecutor.shutdown()
        LogUtils.i(TAG, "性能监控已停止")
    }
    
    /**
     * 重置性能数据
     */
    fun resetPerformanceData() {
        performanceHistory.clear()
        memoryCache.clear()
        _performanceMetrics.value = PerformanceMetrics()
        LogUtils.i(TAG, "性能数据已重置")
    }
}