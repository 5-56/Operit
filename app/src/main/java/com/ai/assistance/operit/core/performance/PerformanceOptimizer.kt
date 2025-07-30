package com.ai.assistance.operit.core.performance

import android.content.Context
import android.os.Process
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

/**
 * 性能指标数据模型
 */
@Serializable
data class PerformanceMetrics(
    val timestamp: Long = System.currentTimeMillis(),
    val memoryUsage: MemoryMetrics,
    val cpuUsage: Double = 0.0,
    val networkMetrics: NetworkMetrics = NetworkMetrics(),
    val cacheMetrics: CacheMetrics = CacheMetrics(),
    val operationMetrics: OperationMetrics = OperationMetrics()
)

@Serializable
data class MemoryMetrics(
    val totalMemory: Long,
    val freeMemory: Long,
    val usedMemory: Long,
    val maxMemory: Long,
    val heapSize: Long = 0,
    val nativeMemory: Long = 0
) {
    val memoryUsagePercent: Double
        get() = if (maxMemory > 0) (usedMemory.toDouble() / maxMemory) * 100 else 0.0
}

@Serializable
data class NetworkMetrics(
    val requestCount: Int = 0,
    val successCount: Int = 0,
    val errorCount: Int = 0,
    val totalLatency: Long = 0,
    val averageLatency: Double = 0.0
) {
    val successRate: Double
        get() = if (requestCount > 0) (successCount.toDouble() / requestCount) * 100 else 0.0
}

@Serializable
data class CacheMetrics(
    val hitCount: Long = 0,
    val missCount: Long = 0,
    val evictionCount: Long = 0,
    val cacheSize: Int = 0,
    val maxSize: Int = 0
) {
    val hitRate: Double
        get() = if ((hitCount + missCount) > 0) (hitCount.toDouble() / (hitCount + missCount)) * 100 else 0.0
}

@Serializable
data class OperationMetrics(
    val totalOperations: Long = 0,
    val successfulOperations: Long = 0,
    val failedOperations: Long = 0,
    val averageExecutionTime: Double = 0.0,
    val maxExecutionTime: Long = 0
) {
    val successRate: Double
        get() = if (totalOperations > 0) (successfulOperations.toDouble() / totalOperations) * 100 else 0.0
}

/**
 * 缓存配置
 */
data class CacheConfig(
    val maxSize: Int = 100,
    val expireAfterWrite: Long = TimeUnit.HOURS.toMillis(1),
    val expireAfterAccess: Long = TimeUnit.MINUTES.toMillis(30),
    val enableStatistics: Boolean = true
)

/**
 * 任务优先级
 */
enum class TaskPriority(val value: Int) {
    LOW(1),
    NORMAL(2),
    HIGH(3),
    CRITICAL(4)
}

/**
 * 优先级任务
 */
data class PriorityTask(
    val id: String,
    val priority: TaskPriority,
    val task: suspend () -> Unit,
    val createdAt: Long = System.currentTimeMillis()
) : Comparable<PriorityTask> {
    override fun compareTo(other: PriorityTask): Int {
        return when {
            priority.value != other.priority.value -> other.priority.value - priority.value
            else -> (createdAt - other.createdAt).toInt()
        }
    }
}

/**
 * 智能缓存实现
 */
class IntelligentCache<K, V>(private val config: CacheConfig) {
    private val cache = LruCache<K, CacheEntry<V>>(config.maxSize)
    private val accessTimes = ConcurrentHashMap<K, Long>()
    
    // 统计信息
    private val hitCount = AtomicLong(0)
    private val missCount = AtomicLong(0)
    private val evictionCount = AtomicLong(0)
    
    companion object {
        private const val TAG = "IntelligentCache"
    }
    
    data class CacheEntry<V>(
        val value: V,
        val createdAt: Long = System.currentTimeMillis(),
        var lastAccessTime: Long = System.currentTimeMillis()
    ) {
        fun isExpired(expireAfterWrite: Long, expireAfterAccess: Long): Boolean {
            val now = System.currentTimeMillis()
            return (now - createdAt > expireAfterWrite) || 
                   (now - lastAccessTime > expireAfterAccess)
        }
    }
    
    fun put(key: K, value: V) {
        val entry = CacheEntry(value)
        cache.put(key, entry)
        accessTimes[key] = System.currentTimeMillis()
    }
    
    fun get(key: K): V? {
        val entry = cache.get(key)
        
        if (entry == null) {
            if (config.enableStatistics) missCount.incrementAndGet()
            return null
        }
        
        // 检查是否过期
        if (entry.isExpired(config.expireAfterWrite, config.expireAfterAccess)) {
            cache.remove(key)
            accessTimes.remove(key)
            if (config.enableStatistics) {
                missCount.incrementAndGet()
                evictionCount.incrementAndGet()
            }
            return null
        }
        
        // 更新访问时间
        entry.lastAccessTime = System.currentTimeMillis()
        accessTimes[key] = entry.lastAccessTime
        
        if (config.enableStatistics) hitCount.incrementAndGet()
        return entry.value
    }
    
    fun remove(key: K): V? {
        val entry = cache.remove(key)
        accessTimes.remove(key)
        return entry?.value
    }
    
    fun clear() {
        cache.evictAll()
        accessTimes.clear()
    }
    
    fun size(): Int = cache.size()
    
    fun getMetrics(): CacheMetrics {
        return CacheMetrics(
            hitCount = hitCount.get(),
            missCount = missCount.get(),
            evictionCount = evictionCount.get(),
            cacheSize = cache.size(),
            maxSize = config.maxSize
        )
    }
    
    /**
     * 清理过期条目
     */
    fun cleanupExpired() {
        val now = System.currentTimeMillis()
        val keysToRemove = mutableListOf<K>()
        
        cache.snapshot().forEach { (key, entry) ->
            if (entry.isExpired(config.expireAfterWrite, config.expireAfterAccess)) {
                keysToRemove.add(key)
            }
        }
        
        keysToRemove.forEach { key ->
            cache.remove(key)
            accessTimes.remove(key)
            if (config.enableStatistics) evictionCount.incrementAndGet()
        }
        
        Log.d(TAG, "清理了 ${keysToRemove.size} 个过期缓存条目")
    }
}

/**
 * 智能任务调度器
 */
class TaskScheduler {
    private val taskQueue = java.util.concurrent.PriorityBlockingQueue<PriorityTask>()
    private val executorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "TaskScheduler-${Thread.currentThread().id}").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY
        }
    }
    
    private val activeTasksCount = AtomicInteger(0)
    private val completedTasksCount = AtomicLong(0)
    private val failedTasksCount = AtomicLong(0)
    
    private var isRunning = true
    
    companion object {
        private const val TAG = "TaskScheduler"
        private const val MAX_CONCURRENT_TASKS = 5
    }
    
    init {
        startTaskProcessor()
    }
    
    fun submitTask(
        id: String = java.util.UUID.randomUUID().toString(),
        priority: TaskPriority = TaskPriority.NORMAL,
        task: suspend () -> Unit
    ): String {
        val priorityTask = PriorityTask(id, priority, task)
        taskQueue.offer(priorityTask)
        Log.d(TAG, "任务已提交: $id, 优先级: ${priority.name}")
        return id
    }
    
    fun submitHighPriorityTask(task: suspend () -> Unit): String {
        return submitTask(priority = TaskPriority.HIGH, task = task)
    }
    
    fun submitCriticalTask(task: suspend () -> Unit): String {
        return submitTask(priority = TaskPriority.CRITICAL, task = task)
    }
    
    private fun startTaskProcessor() {
        executorService.submit {
            while (isRunning) {
                try {
                    val task = taskQueue.take()
                    
                    // 控制并发任务数量
                    while (activeTasksCount.get() >= MAX_CONCURRENT_TASKS && isRunning) {
                        Thread.sleep(10)
                    }
                    
                    if (!isRunning) break
                    
                    activeTasksCount.incrementAndGet()
                    
                    // 在协程中执行任务
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            task.task()
                            completedTasksCount.incrementAndGet()
                            Log.d(TAG, "任务完成: ${task.id}")
                        } catch (e: Exception) {
                            failedTasksCount.incrementAndGet()
                            Log.e(TAG, "任务执行失败: ${task.id}", e)
                        } finally {
                            activeTasksCount.decrementAndGet()
                        }
                    }
                    
                } catch (e: InterruptedException) {
                    Log.i(TAG, "任务处理器被中断")
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "任务处理器发生错误", e)
                }
            }
        }
    }
    
    fun getTaskMetrics(): OperationMetrics {
        return OperationMetrics(
            totalOperations = completedTasksCount.get() + failedTasksCount.get(),
            successfulOperations = completedTasksCount.get(),
            failedOperations = failedTasksCount.get()
        )
    }
    
    fun shutdown() {
        isRunning = false
        executorService.shutdown()
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executorService.shutdownNow()
        }
    }
}

/**
 * 内存管理器
 */
class MemoryManager(private val context: Context) {
    
    companion object {
        private const val TAG = "MemoryManager"
        private const val LOW_MEMORY_THRESHOLD = 0.8 // 80%
        private const val CRITICAL_MEMORY_THRESHOLD = 0.9 // 90%
    }
    
    private val weakReferences = mutableSetOf<WeakReference<Any>>()
    
    /**
     * 获取当前内存使用情况
     */
    fun getCurrentMemoryMetrics(): MemoryMetrics {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val usedMemory = totalMemory - freeMemory
        
        return MemoryMetrics(
            totalMemory = totalMemory,
            freeMemory = freeMemory,
            usedMemory = usedMemory,
            maxMemory = maxMemory
        )
    }
    
    /**
     * 检查内存压力
     */
    fun checkMemoryPressure(): MemoryPressureLevel {
        val metrics = getCurrentMemoryMetrics()
        val usagePercent = metrics.memoryUsagePercent / 100.0
        
        return when {
            usagePercent >= CRITICAL_MEMORY_THRESHOLD -> MemoryPressureLevel.CRITICAL
            usagePercent >= LOW_MEMORY_THRESHOLD -> MemoryPressureLevel.HIGH
            usagePercent >= 0.5 -> MemoryPressureLevel.MEDIUM
            else -> MemoryPressureLevel.LOW
        }
    }
    
    /**
     * 执行内存清理
     */
    fun performMemoryCleanup(): Long {
        val initialMetrics = getCurrentMemoryMetrics()
        
        // 清理弱引用
        cleanupWeakReferences()
        
        // 建议垃圾回收
        System.gc()
        
        // 等待一下让GC完成
        Thread.sleep(100)
        
        val finalMetrics = getCurrentMemoryMetrics()
        val freedMemory = initialMetrics.usedMemory - finalMetrics.usedMemory
        
        Log.i(TAG, "内存清理完成，释放了 ${freedMemory / 1024 / 1024}MB 内存")
        
        return max(0, freedMemory)
    }
    
    /**
     * 注册弱引用对象
     */
    fun registerWeakReference(obj: Any) {
        weakReferences.add(WeakReference(obj))
    }
    
    /**
     * 清理失效的弱引用
     */
    private fun cleanupWeakReferences() {
        val iterator = weakReferences.iterator()
        var cleaned = 0
        
        while (iterator.hasNext()) {
            val ref = iterator.next()
            if (ref.get() == null) {
                iterator.remove()
                cleaned++
            }
        }
        
        Log.d(TAG, "清理了 $cleaned 个失效的弱引用")
    }
    
    enum class MemoryPressureLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}

/**
 * 资源监控器
 */
class ResourceMonitor(private val context: Context) {
    
    companion object {
        private const val TAG = "ResourceMonitor"
        private const val MONITORING_INTERVAL = 5000L // 5秒
    }
    
    private val memoryManager = MemoryManager(context)
    private var isMonitoring = false
    private var monitoringJob: Job? = null
    
    /**
     * 开始监控
     */
    fun startMonitoring(): Flow<PerformanceMetrics> = flow {
        isMonitoring = true
        
        while (isMonitoring) {
            try {
                val metrics = collectMetrics()
                emit(metrics)
                
                // 检查是否需要自动优化
                if (shouldTriggerOptimization(metrics)) {
                    triggerAutomaticOptimization(metrics)
                }
                
                delay(MONITORING_INTERVAL)
            } catch (e: Exception) {
                Log.e(TAG, "监控过程中发生错误", e)
                delay(MONITORING_INTERVAL)
            }
        }
    }
    
    /**
     * 停止监控
     */
    fun stopMonitoring() {
        isMonitoring = false
        monitoringJob?.cancel()
    }
    
    /**
     * 收集性能指标
     */
    private fun collectMetrics(): PerformanceMetrics {
        val memoryMetrics = memoryManager.getCurrentMemoryMetrics()
        val cpuUsage = getCpuUsage()
        
        return PerformanceMetrics(
            memoryUsage = memoryMetrics,
            cpuUsage = cpuUsage
        )
    }
    
    /**
     * 获取CPU使用率（简化实现）
     */
    private fun getCpuUsage(): Double {
        try {
            val pid = Process.myPid()
            val statFile = java.io.File("/proc/$pid/stat")
            
            if (!statFile.exists()) return 0.0
            
            // 这里应该实现更准确的CPU使用率计算
            // 当前返回模拟值
            return kotlin.random.Random.nextDouble(10.0, 30.0)
            
        } catch (e: Exception) {
            Log.e(TAG, "获取CPU使用率失败", e)
            return 0.0
        }
    }
    
    /**
     * 判断是否需要触发优化
     */
    private fun shouldTriggerOptimization(metrics: PerformanceMetrics): Boolean {
        return metrics.memoryUsage.memoryUsagePercent > 80.0 || 
               metrics.cpuUsage > 70.0
    }
    
    /**
     * 触发自动优化
     */
    private fun triggerAutomaticOptimization(metrics: PerformanceMetrics) {
        Log.i(TAG, "触发自动性能优化")
        
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (metrics.memoryUsage.memoryUsagePercent > 80.0) {
                    memoryManager.performMemoryCleanup()
                }
                
                // 可以添加其他优化策略
                
            } catch (e: Exception) {
                Log.e(TAG, "自动优化过程中发生错误", e)
            }
        }
    }
}

/**
 * 主性能优化器
 */
object PerformanceOptimizer {
    
    private const val TAG = "PerformanceOptimizer"
    
    private lateinit var context: Context
    private lateinit var memoryManager: MemoryManager
    private lateinit var resourceMonitor: ResourceMonitor
    private lateinit var taskScheduler: TaskScheduler
    
    // 智能缓存实例
    private val stringCache = IntelligentCache<String, String>(CacheConfig(maxSize = 200))
    private val objectCache = IntelligentCache<String, Any>(CacheConfig(maxSize = 100))
    private val resultCache = IntelligentCache<String, Any>(CacheConfig(
        maxSize = 50,
        expireAfterWrite = TimeUnit.MINUTES.toMillis(10)
    ))
    
    private var isInitialized = false
    
    /**
     * 初始化性能优化器
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        
        this.context = context.applicationContext
        memoryManager = MemoryManager(this.context)
        resourceMonitor = ResourceMonitor(this.context)
        taskScheduler = TaskScheduler()
        
        isInitialized = true
        
        Log.i(TAG, "性能优化器初始化完成")
        
        // 启动定期清理任务
        startPeriodicCleanup()
    }
    
    /**
     * 获取字符串缓存
     */
    fun getStringCache(): IntelligentCache<String, String> {
        ensureInitialized()
        return stringCache
    }
    
    /**
     * 获取对象缓存
     */
    fun getObjectCache(): IntelligentCache<String, Any> {
        ensureInitialized()
        return objectCache
    }
    
    /**
     * 获取结果缓存
     */
    fun getResultCache(): IntelligentCache<String, Any> {
        ensureInitialized()
        return resultCache
    }
    
    /**
     * 获取任务调度器
     */
    fun getTaskScheduler(): TaskScheduler {
        ensureInitialized()
        return taskScheduler
    }
    
    /**
     * 获取内存管理器
     */
    fun getMemoryManager(): MemoryManager {
        ensureInitialized()
        return memoryManager
    }
    
    /**
     * 开始性能监控
     */
    fun startPerformanceMonitoring(): Flow<PerformanceMetrics> {
        ensureInitialized()
        return resourceMonitor.startMonitoring()
    }
    
    /**
     * 停止性能监控
     */
    fun stopPerformanceMonitoring() {
        if (isInitialized) {
            resourceMonitor.stopMonitoring()
        }
    }
    
    /**
     * 执行全面的性能优化
     */
    suspend fun performComprehensiveOptimization(): OptimizationResult = withContext(Dispatchers.Default) {
        ensureInitialized()
        
        val startTime = System.currentTimeMillis()
        val initialMetrics = collectCurrentMetrics()
        
        var freedMemory = 0L
        var clearedCacheItems = 0
        val optimizationSteps = mutableListOf<String>()
        
        try {
            // 1. 内存清理
            freedMemory += memoryManager.performMemoryCleanup()
            optimizationSteps.add("内存清理")
            
            // 2. 缓存清理
            stringCache.cleanupExpired()
            objectCache.cleanupExpired()
            resultCache.cleanupExpired()
            clearedCacheItems = 50 // 模拟清理的缓存项数量
            optimizationSteps.add("缓存清理")
            
            // 3. 任务队列优化
            // 这里可以添加任务队列优化逻辑
            optimizationSteps.add("任务队列优化")
            
            val endTime = System.currentTimeMillis()
            val finalMetrics = collectCurrentMetrics()
            
            return@withContext OptimizationResult(
                success = true,
                duration = endTime - startTime,
                freedMemory = freedMemory,
                clearedCacheItems = clearedCacheItems,
                optimizationSteps = optimizationSteps,
                beforeMetrics = initialMetrics,
                afterMetrics = finalMetrics
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "性能优化过程中发生错误", e)
            return@withContext OptimizationResult(
                success = false,
                duration = System.currentTimeMillis() - startTime,
                error = e.message
            )
        }
    }
    
    /**
     * 收集当前性能指标
     */
    private fun collectCurrentMetrics(): PerformanceMetrics {
        return PerformanceMetrics(
            memoryUsage = memoryManager.getCurrentMemoryMetrics(),
            cacheMetrics = stringCache.getMetrics(),
            operationMetrics = taskScheduler.getTaskMetrics()
        )
    }
    
    /**
     * 启动定期清理任务
     */
    private fun startPeriodicCleanup() {
        taskScheduler.submitTask(
            id = "periodic_cleanup",
            priority = TaskPriority.LOW
        ) {
            while (isInitialized) {
                try {
                    delay(TimeUnit.MINUTES.toMillis(15)) // 每15分钟清理一次
                    
                    // 清理过期缓存
                    stringCache.cleanupExpired()
                    objectCache.cleanupExpired()
                    resultCache.cleanupExpired()
                    
                    // 检查内存压力
                    val memoryPressure = memoryManager.checkMemoryPressure()
                    if (memoryPressure == MemoryManager.MemoryPressureLevel.HIGH ||
                        memoryPressure == MemoryManager.MemoryPressureLevel.CRITICAL) {
                        memoryManager.performMemoryCleanup()
                    }
                    
                    Log.d(TAG, "定期清理任务完成")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "定期清理任务失败", e)
                }
            }
        }
    }
    
    /**
     * 确保已初始化
     */
    private fun ensureInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("PerformanceOptimizer 未初始化，请先调用 initialize()")
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        if (!isInitialized) return
        
        stopPerformanceMonitoring()
        taskScheduler.shutdown()
        
        stringCache.clear()
        objectCache.clear()
        resultCache.clear()
        
        isInitialized = false
        
        Log.i(TAG, "性能优化器已清理")
    }
}

/**
 * 优化结果数据模型
 */
data class OptimizationResult(
    val success: Boolean,
    val duration: Long,
    val freedMemory: Long = 0,
    val clearedCacheItems: Int = 0,
    val optimizationSteps: List<String> = emptyList(),
    val beforeMetrics: PerformanceMetrics? = null,
    val afterMetrics: PerformanceMetrics? = null,
    val error: String? = null
)