package com.ai.assistance.operit.core

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * 自适应性能调优系统
 * 根据设备性能、使用模式和系统状态自动调整性能参数
 */
class AdaptivePerformanceTuner private constructor(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "AdaptivePerformanceTuner"
        
        @Volatile
        private var INSTANCE: AdaptivePerformanceTuner? = null
        
        fun getInstance(context: Context): AdaptivePerformanceTuner {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdaptivePerformanceTuner(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tuningMutex = Mutex()
    
    // 依赖的性能组件
    private lateinit var memoryManager: MemoryManager
    private lateinit var performanceMonitor: PerformanceMonitor
    private lateinit var networkOptimizer: NetworkOptimizer
    private lateinit var databaseOptimizer: DatabaseOptimizer
    
    // 性能配置状态
    private val _currentConfig = MutableStateFlow(PerformanceConfig())
    val currentConfig: StateFlow<PerformanceConfig> = _currentConfig.asStateFlow()
    
    // 学习数据
    private val usagePatterns = ConcurrentHashMap<String, UsagePattern>()
    private val performanceHistory = mutableListOf<PerformanceSnapshot>()
    private val maxHistorySize = 1000
    
    // 调优策略
    private val tuningStrategies = mutableMapOf<String, TuningStrategy>()
    
    init {
        initializeTuningStrategies()
        startAdaptiveTuning()
    }
    
    /**
     * 初始化调优组件
     */
    fun initialize(
        memoryManager: MemoryManager,
        performanceMonitor: PerformanceMonitor,
        networkOptimizer: NetworkOptimizer,
        databaseOptimizer: DatabaseOptimizer
    ) {
        this.memoryManager = memoryManager
        this.performanceMonitor = performanceMonitor
        this.networkOptimizer = networkOptimizer
        this.databaseOptimizer = databaseOptimizer
        
        Log.d(TAG, "自适应性能调优系统已初始化")
    }
    
    /**
     * 初始化调优策略
     */
    private fun initializeTuningStrategies() {
        // 内存管理策略
        tuningStrategies["memory"] = MemoryTuningStrategy()
        
        // 网络优化策略
        tuningStrategies["network"] = NetworkTuningStrategy()
        
        // 数据库优化策略
        tuningStrategies["database"] = DatabaseTuningStrategy()
        
        // AI模型策略
        tuningStrategies["ai_model"] = AIModelTuningStrategy()
        
        // 启动优化策略
        tuningStrategies["startup"] = StartupTuningStrategy()
        
        Log.d(TAG, "调优策略已初始化: ${tuningStrategies.keys}")
    }
    
    /**
     * 开始自适应调优
     */
    private fun startAdaptiveTuning() {
        scope.launch {
            while (true) {
                try {
                    performAdaptiveTuning()
                    kotlinx.coroutines.delay(60_000) // 每分钟调优一次
                } catch (e: Exception) {
                    Log.e(TAG, "自适应调优失败", e)
                    kotlinx.coroutines.delay(300_000) // 失败时等待5分钟
                }
            }
        }
        
        // 监听性能数据变化
        scope.launch {
            if (::performanceMonitor.isInitialized) {
                performanceMonitor.performanceData.collect { data ->
                    recordPerformanceSnapshot(data)
                    analyzePerformancePatterns()
                }
            }
        }
    }
    
    /**
     * 执行自适应调优
     */
    private suspend fun performAdaptiveTuning() {
        tuningMutex.withLock {
            try {
                Log.d(TAG, "开始自适应性能调优...")
                
                // 1. 收集当前性能状态
                val currentSnapshot = collectCurrentPerformance()
                
                // 2. 分析性能趋势
                val performanceTrend = analyzePerformanceTrend()
                
                // 3. 识别性能瓶颈
                val bottlenecks = identifyBottlenecks(currentSnapshot)
                
                // 4. 生成调优建议
                val recommendations = generateTuningRecommendations(
                    currentSnapshot, performanceTrend, bottlenecks
                )
                
                // 5. 应用调优策略
                applyTuningRecommendations(recommendations)
                
                Log.d(TAG, "自适应调优完成，应用了 ${recommendations.size} 个优化建议")
                
            } catch (e: Exception) {
                Log.e(TAG, "自适应调优过程失败", e)
            }
        }
    }
    
    /**
     * 收集当前性能状态
     */
    private fun collectCurrentPerformance(): PerformanceSnapshot {
        val memoryUsage = PerformanceUtils.getMemoryUsage()
        val networkMetrics = if (::networkOptimizer.isInitialized) {
            networkOptimizer.networkMetrics.value
        } else NetworkOptimizer.NetworkMetrics()
        
        val databaseMetrics = if (::databaseOptimizer.isInitialized) {
            databaseOptimizer.databaseMetrics.value
        } else DatabaseOptimizer.DatabaseMetrics()
        
        return PerformanceSnapshot(
            timestamp = System.currentTimeMillis(),
            memoryUsagePercentage = memoryUsage.usagePercentage,
            cpuUsage = getCurrentCpuUsage(),
            networkLatency = networkMetrics.averageLatency,
            databaseLatency = databaseMetrics.averageLatency,
            cacheHitRate = databaseMetrics.cacheHitRate,
            batteryLevel = getBatteryLevel(),
            thermalState = getThermalState(),
            networkType = networkOptimizer.networkState.value
        )
    }
    
    /**
     * 获取当前CPU使用率
     */
    private fun getCurrentCpuUsage(): Float {
        return try {
            val statFile = java.io.File("/proc/stat")
            if (statFile.exists()) {
                val lines = statFile.readLines()
                if (lines.isNotEmpty()) {
                    val cpuLine = lines[0]
                    val values = cpuLine.split("\\s+".toRegex())
                    if (values.size >= 5) {
                        val idle = values[4].toLongOrNull() ?: 0L
                        val total = values.drop(1).take(4).sumOf { it.toLongOrNull() ?: 0L }
                        if (total > 0) {
                            ((total - idle).toFloat() / total * 100)
                        } else 0f
                    } else 0f
                } else 0f
            } else 0f
        } catch (e: Exception) {
            0f
        }
    }
    
    /**
     * 获取电池电量
     */
    private fun getBatteryLevel(): Float {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY).toFloat()
        } catch (e: Exception) {
            100f
        }
    }
    
    /**
     * 获取热状态
     */
    private fun getThermalState(): ThermalState {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                when (powerManager.currentThermalStatus) {
                    android.os.PowerManager.THERMAL_STATUS_NONE -> ThermalState.NORMAL
                    android.os.PowerManager.THERMAL_STATUS_LIGHT -> ThermalState.LIGHT
                    android.os.PowerManager.THERMAL_STATUS_MODERATE -> ThermalState.MODERATE
                    android.os.PowerManager.THERMAL_STATUS_SEVERE -> ThermalState.SEVERE
                    android.os.PowerManager.THERMAL_STATUS_CRITICAL -> ThermalState.CRITICAL
                    android.os.PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalState.EMERGENCY
                    android.os.PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalState.SHUTDOWN
                    else -> ThermalState.NORMAL
                }
            } catch (e: Exception) {
                ThermalState.NORMAL
            }
        } else {
            ThermalState.NORMAL
        }
    }
    
    /**
     * 记录性能快照
     */
    private fun recordPerformanceSnapshot(data: PerformanceMonitor.PerformanceData) {
        val snapshot = PerformanceSnapshot(
            timestamp = System.currentTimeMillis(),
            memoryUsagePercentage = data.memoryUsagePercentage,
            cpuUsage = data.cpuUsage,
            networkLatency = data.networkLatency,
            databaseLatency = 0.0, // 从数据库组件获取
            cacheHitRate = 0f,
            batteryLevel = getBatteryLevel(),
            thermalState = getThermalState(),
            networkType = if (::networkOptimizer.isInitialized) {
                networkOptimizer.networkState.value
            } else NetworkOptimizer.NetworkState.UNKNOWN
        )
        
        synchronized(performanceHistory) {
            performanceHistory.add(snapshot)
            if (performanceHistory.size > maxHistorySize) {
                performanceHistory.removeAt(0)
            }
        }
    }
    
    /**
     * 分析性能模式
     */
    private fun analyzePerformancePatterns() {
        // 分析时间段模式
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val hourPattern = usagePatterns.getOrPut("hour_$currentHour") { UsagePattern() }
        hourPattern.updateUsage()
        
        // 分析网络状态模式
        if (::networkOptimizer.isInitialized) {
            val networkType = networkOptimizer.networkState.value.toString()
            val networkPattern = usagePatterns.getOrPut("network_$networkType") { UsagePattern() }
            networkPattern.updateUsage()
        }
        
        // 分析电池状态模式
        val batteryLevel = getBatteryLevel()
        val batteryCategory = when {
            batteryLevel > 80 -> "high"
            batteryLevel > 50 -> "medium"
            batteryLevel > 20 -> "low"
            else -> "critical"
        }
        val batteryPattern = usagePatterns.getOrPut("battery_$batteryCategory") { UsagePattern() }
        batteryPattern.updateUsage()
    }
    
    /**
     * 分析性能趋势
     */
    private fun analyzePerformanceTrend(): PerformanceTrend {
        if (performanceHistory.size < 10) {
            return PerformanceTrend.STABLE
        }
        
        val recent = performanceHistory.takeLast(10)
        val older = performanceHistory.takeLast(20).take(10)
        
        val recentAvgMemory = recent.map { it.memoryUsagePercentage }.average()
        val olderAvgMemory = older.map { it.memoryUsagePercentage }.average()
        
        val recentAvgCpu = recent.map { it.cpuUsage }.average()
        val olderAvgCpu = older.map { it.cpuUsage }.average()
        
        return when {
            recentAvgMemory > olderAvgMemory * 1.2 || recentAvgCpu > olderAvgCpu * 1.2 -> 
                PerformanceTrend.DEGRADING
            recentAvgMemory < olderAvgMemory * 0.8 && recentAvgCpu < olderAvgCpu * 0.8 -> 
                PerformanceTrend.IMPROVING
            else -> PerformanceTrend.STABLE
        }
    }
    
    /**
     * 识别性能瓶颈
     */
    private fun identifyBottlenecks(snapshot: PerformanceSnapshot): List<PerformanceBottleneck> {
        val bottlenecks = mutableListOf<PerformanceBottleneck>()
        
        // 内存瓶颈
        if (snapshot.memoryUsagePercentage > 85) {
            bottlenecks.add(PerformanceBottleneck.MEMORY_HIGH)
        }
        
        // CPU瓶颈
        if (snapshot.cpuUsage > 80) {
            bottlenecks.add(PerformanceBottleneck.CPU_HIGH)
        }
        
        // 网络瓶颈
        if (snapshot.networkLatency > 2000) {
            bottlenecks.add(PerformanceBottleneck.NETWORK_SLOW)
        }
        
        // 数据库瓶颈
        if (snapshot.databaseLatency > 500) {
            bottlenecks.add(PerformanceBottleneck.DATABASE_SLOW)
        }
        
        // 缓存瓶颈
        if (snapshot.cacheHitRate < 30) {
            bottlenecks.add(PerformanceBottleneck.CACHE_INEFFICIENT)
        }
        
        // 电池瓶颈
        if (snapshot.batteryLevel < 20) {
            bottlenecks.add(PerformanceBottleneck.BATTERY_LOW)
        }
        
        // 热管理瓶颈
        if (snapshot.thermalState >= ThermalState.MODERATE) {
            bottlenecks.add(PerformanceBottleneck.THERMAL_HIGH)
        }
        
        return bottlenecks
    }
    
    /**
     * 生成调优建议
     */
    private fun generateTuningRecommendations(
        snapshot: PerformanceSnapshot,
        trend: PerformanceTrend,
        bottlenecks: List<PerformanceBottleneck>
    ): List<TuningRecommendation> {
        val recommendations = mutableListOf<TuningRecommendation>()
        
        // 根据瓶颈生成建议
        bottlenecks.forEach { bottleneck ->
            when (bottleneck) {
                PerformanceBottleneck.MEMORY_HIGH -> {
                    recommendations.add(
                        TuningRecommendation(
                            strategy = "memory",
                            action = TuningAction.AGGRESSIVE_CLEANUP,
                            priority = TuningPriority.HIGH,
                            description = "内存使用率过高，执行激进清理"
                        )
                    )
                }
                
                PerformanceBottleneck.CPU_HIGH -> {
                    recommendations.add(
                        TuningRecommendation(
                            strategy = "ai_model",
                            action = TuningAction.REDUCE_CONCURRENCY,
                            priority = TuningPriority.HIGH,
                            description = "CPU使用率过高，降低AI模型并发度"
                        )
                    )
                }
                
                PerformanceBottleneck.NETWORK_SLOW -> {
                    recommendations.add(
                        TuningRecommendation(
                            strategy = "network",
                            action = TuningAction.INCREASE_CACHE,
                            priority = TuningPriority.MEDIUM,
                            description = "网络延迟高，增加缓存策略"
                        )
                    )
                }
                
                PerformanceBottleneck.DATABASE_SLOW -> {
                    recommendations.add(
                        TuningRecommendation(
                            strategy = "database",
                            action = TuningAction.OPTIMIZE_QUERIES,
                            priority = TuningPriority.MEDIUM,
                            description = "数据库查询慢，优化查询策略"
                        )
                    )
                }
                
                PerformanceBottleneck.BATTERY_LOW -> {
                    recommendations.add(
                        TuningRecommendation(
                            strategy = "startup",
                            action = TuningAction.REDUCE_BACKGROUND_TASKS,
                            priority = TuningPriority.HIGH,
                            description = "电池电量低，减少后台任务"
                        )
                    )
                }
                
                PerformanceBottleneck.THERMAL_HIGH -> {
                    recommendations.add(
                        TuningRecommendation(
                            strategy = "ai_model",
                            action = TuningAction.REDUCE_MODEL_COMPLEXITY,
                            priority = TuningPriority.HIGH,
                            description = "设备过热，降低AI模型复杂度"
                        )
                    )
                }
                
                else -> {
                    // 其他瓶颈的处理
                }
            }
        }
        
        // 根据性能趋势生成建议
        when (trend) {
            PerformanceTrend.DEGRADING -> {
                recommendations.add(
                    TuningRecommendation(
                        strategy = "memory",
                        action = TuningAction.PREEMPTIVE_CLEANUP,
                        priority = TuningPriority.MEDIUM,
                        description = "性能下降趋势，执行预防性清理"
                    )
                )
            }
            
            PerformanceTrend.IMPROVING -> {
                recommendations.add(
                    TuningRecommendation(
                        strategy = "ai_model",
                        action = TuningAction.ENABLE_ADVANCED_FEATURES,
                        priority = TuningPriority.LOW,
                        description = "性能改善，可启用高级功能"
                    )
                )
            }
            
            else -> {
                // 稳定状态不需要额外调整
            }
        }
        
        return recommendations.sortedByDescending { it.priority.ordinal }
    }
    
    /**
     * 应用调优建议
     */
    private suspend fun applyTuningRecommendations(recommendations: List<TuningRecommendation>) {
        recommendations.forEach { recommendation ->
            try {
                val strategy = tuningStrategies[recommendation.strategy]
                strategy?.apply(recommendation, _currentConfig.value)
                
                Log.d(TAG, "应用调优建议: ${recommendation.description}")
                
            } catch (e: Exception) {
                Log.e(TAG, "应用调优建议失败: ${recommendation.description}", e)
            }
        }
        
        // 更新配置状态
        _currentConfig.value = _currentConfig.value.copy(
            lastTuningTime = System.currentTimeMillis(),
            appliedRecommendations = recommendations.size
        )
    }
    
    /**
     * 获取性能建议
     */
    fun getPerformanceRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()
        val snapshot = collectCurrentPerformance()
        val bottlenecks = identifyBottlenecks(snapshot)
        
        bottlenecks.forEach { bottleneck ->
            when (bottleneck) {
                PerformanceBottleneck.MEMORY_HIGH -> {
                    recommendations.add("内存使用率过高，建议关闭不必要的应用或清理缓存")
                }
                PerformanceBottleneck.CPU_HIGH -> {
                    recommendations.add("CPU使用率过高，建议减少同时运行的AI任务")
                }
                PerformanceBottleneck.NETWORK_SLOW -> {
                    recommendations.add("网络连接较慢，建议检查网络环境或切换到更快的网络")
                }
                PerformanceBottleneck.BATTERY_LOW -> {
                    recommendations.add("电池电量较低，建议开启省电模式")
                }
                PerformanceBottleneck.THERMAL_HIGH -> {
                    recommendations.add("设备温度较高，建议暂停高强度任务并降低屏幕亮度")
                }
                else -> {}
            }
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("当前性能状态良好，无需特殊优化")
        }
        
        return recommendations
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        usagePatterns.clear()
        performanceHistory.clear()
        Log.d(TAG, "自适应性能调优器资源已清理")
    }
    
    // ==================== 调优策略实现 ====================
    
    private inner class MemoryTuningStrategy : TuningStrategy {
        override suspend fun apply(recommendation: TuningRecommendation, config: PerformanceConfig) {
            when (recommendation.action) {
                TuningAction.AGGRESSIVE_CLEANUP -> {
                    if (::memoryManager.isInitialized) {
                        memoryManager.clearCache()
                        memoryManager.forceGarbageCollection()
                    }
                }
                TuningAction.PREEMPTIVE_CLEANUP -> {
                    if (::memoryManager.isInitialized) {
                        memoryManager.clearCache()
                    }
                }
                else -> {}
            }
        }
    }
    
    private inner class NetworkTuningStrategy : TuningStrategy {
        override suspend fun apply(recommendation: TuningRecommendation, config: PerformanceConfig) {
            when (recommendation.action) {
                TuningAction.INCREASE_CACHE -> {
                    // 网络缓存优化已在NetworkOptimizer中自动处理
                }
                else -> {}
            }
        }
    }
    
    private inner class DatabaseTuningStrategy : TuningStrategy {
        override suspend fun apply(recommendation: TuningRecommendation, config: PerformanceConfig) {
            when (recommendation.action) {
                TuningAction.OPTIMIZE_QUERIES -> {
                    if (::databaseOptimizer.isInitialized) {
                        databaseOptimizer.clearCache()
                    }
                }
                else -> {}
            }
        }
    }
    
    private inner class AIModelTuningStrategy : TuningStrategy {
        override suspend fun apply(recommendation: TuningRecommendation, config: PerformanceConfig) {
            // AI模型相关的调优逻辑
            when (recommendation.action) {
                TuningAction.REDUCE_CONCURRENCY -> {
                    // 降低AI模型并发度
                }
                TuningAction.REDUCE_MODEL_COMPLEXITY -> {
                    // 降低模型复杂度
                }
                TuningAction.ENABLE_ADVANCED_FEATURES -> {
                    // 启用高级功能
                }
                else -> {}
            }
        }
    }
    
    private inner class StartupTuningStrategy : TuningStrategy {
        override suspend fun apply(recommendation: TuningRecommendation, config: PerformanceConfig) {
            when (recommendation.action) {
                TuningAction.REDUCE_BACKGROUND_TASKS -> {
                    // 减少后台任务
                }
                else -> {}
            }
        }
    }
    
    // ==================== 数据类和枚举定义 ====================
    
    data class PerformanceConfig(
        val memoryThreshold: Float = 80f,
        val cpuThreshold: Float = 70f,
        val networkCacheSize: Long = 50 * 1024 * 1024L,
        val databaseCacheSize: Int = 100,
        val lastTuningTime: Long = 0L,
        val appliedRecommendations: Int = 0
    )
    
    data class PerformanceSnapshot(
        val timestamp: Long,
        val memoryUsagePercentage: Float,
        val cpuUsage: Float,
        val networkLatency: Double,
        val databaseLatency: Double,
        val cacheHitRate: Float,
        val batteryLevel: Float,
        val thermalState: ThermalState,
        val networkType: NetworkOptimizer.NetworkState
    )
    
    data class UsagePattern(
        var usageCount: Long = 0,
        var lastUsed: Long = System.currentTimeMillis(),
        var averagePerformance: Float = 0f
    ) {
        fun updateUsage() {
            usageCount++
            lastUsed = System.currentTimeMillis()
        }
    }
    
    data class TuningRecommendation(
        val strategy: String,
        val action: TuningAction,
        val priority: TuningPriority,
        val description: String
    )
    
    enum class PerformanceTrend {
        IMPROVING, STABLE, DEGRADING
    }
    
    enum class PerformanceBottleneck {
        MEMORY_HIGH,
        CPU_HIGH,
        NETWORK_SLOW,
        DATABASE_SLOW,
        CACHE_INEFFICIENT,
        BATTERY_LOW,
        THERMAL_HIGH
    }
    
    enum class ThermalState {
        NORMAL, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN
    }
    
    enum class TuningAction {
        AGGRESSIVE_CLEANUP,
        PREEMPTIVE_CLEANUP,
        INCREASE_CACHE,
        OPTIMIZE_QUERIES,
        REDUCE_CONCURRENCY,
        REDUCE_MODEL_COMPLEXITY,
        REDUCE_BACKGROUND_TASKS,
        ENABLE_ADVANCED_FEATURES
    }
    
    enum class TuningPriority {
        LOW, MEDIUM, HIGH
    }
    
    interface TuningStrategy {
        suspend fun apply(recommendation: TuningRecommendation, config: PerformanceConfig)
    }
}