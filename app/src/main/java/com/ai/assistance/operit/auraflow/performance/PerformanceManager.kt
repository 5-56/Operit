package com.ai.assistance.operit.auraflow.performance

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Debug
import android.os.PowerManager
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.*
import kotlinx.serialization.Serializable
import kotlin.system.measureTimeMillis

/**
 * 性能统计数据
 */
@Serializable
data class PerformanceStats(
    val timestamp: Long = System.currentTimeMillis(),
    val memoryUsage: MemoryUsage,
    val batteryInfo: BatteryInfo,
    val cpuUsage: CpuUsage,
    val networkUsage: NetworkUsage,
    val appPerformance: AppPerformance
)

/**
 * 内存使用信息
 */
@Serializable
data class MemoryUsage(
    val totalMemory: Long,
    val usedMemory: Long,
    val freeMemory: Long,
    val heapSize: Long,
    val heapUsed: Long,
    val heapFree: Long,
    val nativeHeap: Long,
    val cacheSize: Long,
    val memoryPressure: String // LOW, MODERATE, CRITICAL
)

/**
 * 电池信息
 */
@Serializable
data class BatteryInfo(
    val level: Int,
    val temperature: Float,
    val voltage: Int,
    val status: String,
    val health: String,
    val plugged: String,
    val isCharging: Boolean,
    val estimatedTimeRemaining: Long,
    val powerSaveMode: Boolean
)

/**
 * CPU使用信息
 */
@Serializable
data class CpuUsage(
    val overallUsage: Float,
    val appUsage: Float,
    val systemUsage: Float,
    val coreCount: Int,
    val frequency: Long,
    val loadAverage: FloatArray = floatArrayOf()
)

/**
 * 网络使用信息
 */
@Serializable
data class NetworkUsage(
    val bytesReceived: Long,
    val bytesSent: Long,
    val packetsReceived: Long,
    val packetsSent: Long,
    val connectionType: String,
    val isConnected: Boolean,
    val signalStrength: Int
)

/**
 * 应用性能信息
 */
@Serializable
data class AppPerformance(
    val startupTime: Long,
    val averageResponseTime: Long,
    val frameDrops: Int,
    val anrCount: Int,
    val crashCount: Int,
    val gcCount: Int,
    val gcTime: Long,
    val threadCount: Int
)

/**
 * 对象池接口
 */
interface ObjectPool<T> {
    fun acquire(): T?
    fun release(obj: T)
    fun clear()
    fun size(): Int
}

/**
 * 通用对象池实现
 */
class GenericObjectPool<T>(
    private val factory: () -> T,
    private val reset: (T) -> Unit = {},
    private val maxSize: Int = 10
) : ObjectPool<T> {
    
    private val pool = ConcurrentLinkedQueue<T>()
    private val size = AtomicInteger(0)
    
    override fun acquire(): T? {
        return pool.poll()?.also { 
            size.decrementAndGet()
        } ?: factory()
    }
    
    override fun release(obj: T) {
        if (size.get() < maxSize) {
            reset(obj)
            pool.offer(obj)
            size.incrementAndGet()
        }
    }
    
    override fun clear() {
        pool.clear()
        size.set(0)
    }
    
    override fun size(): Int = size.get()
}

/**
 * 缓存管理器
 */
class CacheManager(maxSize: Long) {
    
    // 图片缓存
    private val bitmapCache = LruCache<String, android.graphics.Bitmap>(
        (maxSize / 4).toInt()
    )
    
    // 字符串缓存
    private val stringCache = LruCache<String, String>(1000)
    
    // 数据缓存
    private val dataCache = LruCache<String, Any>(500)
    
    // 文件缓存目录
    private var diskCacheDir: File? = null
    
    fun initDiskCache(context: Context) {
        diskCacheDir = File(context.cacheDir, "auraflow_cache")
        if (!diskCacheDir!!.exists()) {
            diskCacheDir!!.mkdirs()
        }
    }
    
    fun putBitmap(key: String, bitmap: android.graphics.Bitmap) {
        bitmapCache.put(key, bitmap)
    }
    
    fun getBitmap(key: String): android.graphics.Bitmap? {
        return bitmapCache.get(key)
    }
    
    fun putString(key: String, value: String) {
        stringCache.put(key, value)
    }
    
    fun getString(key: String): String? {
        return stringCache.get(key)
    }
    
    fun putData(key: String, data: Any) {
        dataCache.put(key, data)
    }
    
    fun getData(key: String): Any? {
        return dataCache.get(key)
    }
    
    fun clearMemoryCache() {
        bitmapCache.evictAll()
        stringCache.evictAll()
        dataCache.evictAll()
    }
    
    fun clearDiskCache() {
        diskCacheDir?.deleteRecursively()
    }
    
    fun getCacheSize(): Long {
        return bitmapCache.size() + stringCache.size() + dataCache.size()
    }
}

/**
 * 电池优化管理器
 */
class BatteryOptimizer(private val context: Context) {
    
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // 电池状态监听
    private val _batteryInfo = MutableStateFlow<BatteryInfo?>(null)
    val batteryInfo: StateFlow<BatteryInfo?> = _batteryInfo.asStateFlow()
    
    // 省电模式
    private val _powerSaveMode = MutableStateFlow(false)
    val powerSaveMode: StateFlow<Boolean> = _powerSaveMode.asStateFlow()
    
    init {
        monitorBatteryStatus()
    }
    
    /**
     * 监控电池状态
     */
    private fun monitorBatteryStatus() {
        scope.launch {
            while (true) {
                updateBatteryInfo()
                delay(30000) // 每30秒更新一次
            }
        }
    }
    
    private fun updateBatteryInfo() {
        try {
            val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (batteryStatus != null) {
                val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val temperature = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10f
                val voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
                val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val health = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
                val plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                
                val batteryPct = level * 100 / scale.toFloat()
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                               status == BatteryManager.BATTERY_STATUS_FULL
                
                val battery = BatteryInfo(
                    level = batteryPct.toInt(),
                    temperature = temperature,
                    voltage = voltage,
                    status = getBatteryStatusString(status),
                    health = getBatteryHealthString(health),
                    plugged = getBatteryPluggedString(plugged),
                    isCharging = isCharging,
                    estimatedTimeRemaining = calculateTimeRemaining(batteryPct, isCharging),
                    powerSaveMode = powerManager.isPowerSaveMode
                )
                
                _batteryInfo.value = battery
                _powerSaveMode.value = powerManager.isPowerSaveMode
            }
        } catch (e: Exception) {
            Log.e("BatteryOptimizer", "更新电池信息失败", e)
        }
    }
    
    private fun getBatteryStatusString(status: Int): String {
        return when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "充电中"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "放电中"
            BatteryManager.BATTERY_STATUS_FULL -> "已充满"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "未充电"
            BatteryManager.BATTERY_STATUS_UNKNOWN -> "未知"
            else -> "未知"
        }
    }
    
    private fun getBatteryHealthString(health: Int): String {
        return when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "良好"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "过热"
            BatteryManager.BATTERY_HEALTH_DEAD -> "损坏"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "过压"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "未知故障"
            BatteryManager.BATTERY_HEALTH_COLD -> "过冷"
            else -> "未知"
        }
    }
    
    private fun getBatteryPluggedString(plugged: Int): String {
        return when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC充电器"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB充电"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "无线充电"
            0 -> "未插入"
            else -> "未知"
        }
    }
    
    private fun calculateTimeRemaining(batteryLevel: Float, isCharging: Boolean): Long {
        // 简单估算，实际应用中可以根据历史数据进行更精确计算
        return if (isCharging) {
            ((100 - batteryLevel) * 120000).toLong() // 估算充电时间
        } else {
            (batteryLevel * 180000).toLong() // 估算使用时间
        }
    }
    
    /**
     * 请求唤醒锁
     */
    fun acquireWakeLock(tag: String, timeout: Long = 0) {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AuraFlow:$tag"
            )
            
            if (timeout > 0) {
                wakeLock?.acquire(timeout)
            } else {
                wakeLock?.acquire()
            }
            
            Log.d("BatteryOptimizer", "获取唤醒锁: $tag")
        } catch (e: Exception) {
            Log.e("BatteryOptimizer", "获取唤醒锁失败", e)
        }
    }
    
    /**
     * 释放唤醒锁
     */
    fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d("BatteryOptimizer", "释放唤醒锁")
            }
        } catch (e: Exception) {
            Log.e("BatteryOptimizer", "释放唤醒锁失败", e)
        }
    }
    
    /**
     * 进入省电模式
     */
    fun enterPowerSaveMode() {
        // 降低后台任务频率
        // 减少网络请求
        // 关闭不必要的功能
        Log.d("BatteryOptimizer", "进入省电模式")
    }
    
    /**
     * 退出省电模式
     */
    fun exitPowerSaveMode() {
        // 恢复正常任务频率
        Log.d("BatteryOptimizer", "退出省电模式")
    }
    
    fun cleanup() {
        releaseWakeLock()
        scope.cancel()
    }
}

/**
 * 内存管理器
 */
class MemoryManager(private val context: Context) {
    
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val memoryInfo = ActivityManager.MemoryInfo()
    
    // 对象池集合
    private val objectPools = mutableMapOf<String, ObjectPool<*>>()
    
    // 内存状态监听
    private val _memoryPressure = MutableStateFlow("LOW")
    val memoryPressure: StateFlow<String> = _memoryPressure.asStateFlow()
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    init {
        monitorMemoryUsage()
        setupObjectPools()
    }
    
    /**
     * 设置对象池
     */
    private fun setupObjectPools() {
        // StringBuilder对象池
        objectPools["StringBuilder"] = GenericObjectPool(
            factory = { StringBuilder() },
            reset = { it.clear() },
            maxSize = 20
        )
        
        // ByteArray对象池
        objectPools["ByteArray"] = GenericObjectPool(
            factory = { ByteArray(8192) },
            reset = { it.fill(0) },
            maxSize = 10
        )
    }
    
    /**
     * 监控内存使用
     */
    private fun monitorMemoryUsage() {
        scope.launch {
            while (true) {
                updateMemoryInfo()
                delay(10000) // 每10秒检查一次
            }
        }
    }
    
    private fun updateMemoryInfo() {
        try {
            activityManager.getMemoryInfo(memoryInfo)
            
            val pressureLevel = when {
                memoryInfo.lowMemory -> "CRITICAL"
                memoryInfo.availMem < memoryInfo.threshold * 1.5 -> "MODERATE"
                else -> "LOW"
            }
            
            _memoryPressure.value = pressureLevel
            
            if (pressureLevel != "LOW") {
                handleMemoryPressure(pressureLevel)
            }
            
        } catch (e: Exception) {
            Log.e("MemoryManager", "更新内存信息失败", e)
        }
    }
    
    /**
     * 处理内存压力
     */
    private fun handleMemoryPressure(level: String) {
        when (level) {
            "MODERATE" -> {
                // 清理部分缓存
                clearSoftCaches()
                Log.w("MemoryManager", "内存压力中等，清理软缓存")
            }
            "CRITICAL" -> {
                // 清理所有可清理的缓存
                clearAllCaches()
                forceGarbageCollection()
                Log.e("MemoryManager", "内存压力严重，强制清理")
            }
        }
    }
    
    /**
     * 获取内存使用信息
     */
    fun getMemoryUsage(): MemoryUsage {
        activityManager.getMemoryInfo(memoryInfo)
        
        val runtime = Runtime.getRuntime()
        val nativeHeap = Debug.getNativeHeapSize()
        
        return MemoryUsage(
            totalMemory = memoryInfo.totalMem,
            usedMemory = memoryInfo.totalMem - memoryInfo.availMem,
            freeMemory = memoryInfo.availMem,
            heapSize = runtime.totalMemory(),
            heapUsed = runtime.totalMemory() - runtime.freeMemory(),
            heapFree = runtime.freeMemory(),
            nativeHeap = nativeHeap,
            cacheSize = 0, // 需要从CacheManager获取
            memoryPressure = _memoryPressure.value
        )
    }
    
    /**
     * 清理软缓存
     */
    private fun clearSoftCaches() {
        // 清理对象池
        objectPools.values.forEach { pool ->
            if (pool.size() > 5) {
                repeat(pool.size() / 2) {
                    pool.acquire()?.let { /* 不放回池中 */ }
                }
            }
        }
    }
    
    /**
     * 清理所有缓存
     */
    private fun clearAllCaches() {
        objectPools.values.forEach { it.clear() }
    }
    
    /**
     * 强制垃圾回收
     */
    private fun forceGarbageCollection() {
        System.gc()
        System.runFinalization()
    }
    
    /**
     * 获取对象池
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getObjectPool(name: String): ObjectPool<T>? {
        return objectPools[name] as? ObjectPool<T>
    }
    
    fun cleanup() {
        scope.cancel()
        clearAllCaches()
    }
}

/**
 * 性能管理器
 */
class PerformanceManager(private val context: Context) {
    
    companion object {
        private const val TAG = "PerformanceManager"
        
        @Volatile
        private var INSTANCE: PerformanceManager? = null
        
        fun getInstance(context: Context): PerformanceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PerformanceManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val memoryManager = MemoryManager(context)
    private val batteryOptimizer = BatteryOptimizer(context)
    private val cacheManager = CacheManager(50 * 1024 * 1024) // 50MB
    
    // 性能统计
    private val _performanceStats = MutableStateFlow<PerformanceStats?>(null)
    val performanceStats: StateFlow<PerformanceStats?> = _performanceStats.asStateFlow()
    
    // 启用状态
    private val _optimizationEnabled = MutableStateFlow(true)
    val optimizationEnabled: StateFlow<Boolean> = _optimizationEnabled.asStateFlow()
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    init {
        Log.d(TAG, "性能管理器初始化")
        cacheManager.initDiskCache(context)
        startPerformanceMonitoring()
    }
    
    /**
     * 开始性能监控
     */
    private fun startPerformanceMonitoring() {
        scope.launch {
            while (true) {
                if (_optimizationEnabled.value) {
                    updatePerformanceStats()
                    applyOptimizations()
                }
                delay(30000) // 每30秒更新一次
            }
        }
    }
    
    /**
     * 更新性能统计
     */
    private fun updatePerformanceStats() {
        try {
            val memoryUsage = memoryManager.getMemoryUsage()
            val batteryInfo = batteryOptimizer.batteryInfo.value ?: createDefaultBatteryInfo()
            val cpuUsage = getCpuUsage()
            val networkUsage = getNetworkUsage()
            val appPerformance = getAppPerformance()
            
            val stats = PerformanceStats(
                memoryUsage = memoryUsage,
                batteryInfo = batteryInfo,
                cpuUsage = cpuUsage,
                networkUsage = networkUsage,
                appPerformance = appPerformance
            )
            
            _performanceStats.value = stats
            
        } catch (e: Exception) {
            Log.e(TAG, "更新性能统计失败", e)
        }
    }
    
    /**
     * 应用优化策略
     */
    private fun applyOptimizations() {
        val stats = _performanceStats.value ?: return
        
        // 内存优化
        when (stats.memoryUsage.memoryPressure) {
            "MODERATE", "CRITICAL" -> {
                cacheManager.clearMemoryCache()
                Log.d(TAG, "应用内存优化")
            }
        }
        
        // 电池优化
        if (stats.batteryInfo.level < 20 && !stats.batteryInfo.isCharging) {
            batteryOptimizer.enterPowerSaveMode()
            Log.d(TAG, "应用电池优化")
        }
        
        // CPU优化
        if (stats.cpuUsage.overallUsage > 80) {
            // 降低后台任务频率
            Log.d(TAG, "应用CPU优化")
        }
    }
    
    private fun createDefaultBatteryInfo(): BatteryInfo {
        return BatteryInfo(
            level = 50,
            temperature = 25f,
            voltage = 4000,
            status = "未知",
            health = "良好",
            plugged = "未插入",
            isCharging = false,
            estimatedTimeRemaining = 0,
            powerSaveMode = false
        )
    }
    
    private fun getCpuUsage(): CpuUsage {
        return CpuUsage(
            overallUsage = 0f,
            appUsage = 0f,
            systemUsage = 0f,
            coreCount = Runtime.getRuntime().availableProcessors(),
            frequency = 0L
        )
    }
    
    private fun getNetworkUsage(): NetworkUsage {
        return NetworkUsage(
            bytesReceived = 0L,
            bytesSent = 0L,
            packetsReceived = 0L,
            packetsSent = 0L,
            connectionType = "未知",
            isConnected = false,
            signalStrength = 0
        )
    }
    
    private fun getAppPerformance(): AppPerformance {
        return AppPerformance(
            startupTime = 0L,
            averageResponseTime = 0L,
            frameDrops = 0,
            anrCount = 0,
            crashCount = 0,
            gcCount = 0,
            gcTime = 0L,
            threadCount = Thread.activeCount()
        )
    }
    
    /**
     * 测量执行时间
     */
    suspend fun <T> measureExecutionTime(
        tag: String,
        block: suspend () -> T
    ): Pair<T, Long> {
        val time = measureTimeMillis {
            return@measureTimeMillis block()
        }
        Log.d(TAG, "$tag 执行时间: ${time}ms")
        return block() to time
    }
    
    /**
     * 优化图片
     */
    fun optimizeBitmap(
        bitmap: android.graphics.Bitmap,
        maxWidth: Int = 1080,
        maxHeight: Int = 1920,
        quality: Int = 80
    ): android.graphics.Bitmap {
        if (bitmap.width <= maxWidth && bitmap.height <= maxHeight) {
            return bitmap
        }
        
        val ratio = minOf(
            maxWidth.toFloat() / bitmap.width,
            maxHeight.toFloat() / bitmap.height
        )
        
        val newWidth = (bitmap.width * ratio).toInt()
        val newHeight = (bitmap.height * ratio).toInt()
        
        return android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * 获取缓存管理器
     */
    fun getCacheManager(): CacheManager = cacheManager
    
    /**
     * 获取内存管理器
     */
    fun getMemoryManager(): MemoryManager = memoryManager
    
    /**
     * 获取电池优化器
     */
    fun getBatteryOptimizer(): BatteryOptimizer = batteryOptimizer
    
    /**
     * 启用/禁用优化
     */
    fun setOptimizationEnabled(enabled: Boolean) {
        _optimizationEnabled.value = enabled
        Log.d(TAG, "性能优化${if (enabled) "启用" else "禁用"}")
    }
    
    /**
     * 手动触发优化
     */
    fun triggerOptimization() {
        scope.launch {
            updatePerformanceStats()
            applyOptimizations()
        }
    }
    
    /**
     * 导出性能报告
     */
    fun exportPerformanceReport(): Map<String, Any> {
        val stats = _performanceStats.value
        return mapOf(
            "timestamp" to System.currentTimeMillis(),
            "stats" to (stats ?: "无数据"),
            "optimizationEnabled" to _optimizationEnabled.value,
            "cacheSize" to cacheManager.getCacheSize(),
            "memoryPressure" to memoryManager.memoryPressure.value
        )
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        scope.cancel()
        memoryManager.cleanup()
        batteryOptimizer.cleanup()
        cacheManager.clearMemoryCache()
        Log.d(TAG, "性能管理器清理完成")
    }
}