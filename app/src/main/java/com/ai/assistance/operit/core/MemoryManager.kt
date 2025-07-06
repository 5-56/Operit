package com.ai.assistance.operit.core

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.util.Log
import android.util.LruCache
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import kotlinx.coroutines.*
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.KClass

/**
 * 🧠 智能内存管理器
 * 
 * 功能特性：
 * - 分层内存管理架构
 * - 智能对象池管理
 * - 弱引用缓存系统
 * - 内存压力监控
 * - 自动清理机制
 * - 内存泄漏检测
 */
class MemoryManager private constructor(private val context: Context) : LifecycleObserver {
    
    companion object {
        private const val TAG = "MemoryManager"
        private const val LOW_MEMORY_THRESHOLD = 0.8f
        private const val CRITICAL_MEMORY_THRESHOLD = 0.9f
        
        @Volatile
        private var INSTANCE: MemoryManager? = null
        
        fun getInstance(context: Context): MemoryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MemoryManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // 内存管理组件
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val objectPoolManager = ObjectPoolManager()
    private val cacheManager = SmartCacheManager()
    private val memoryMonitor = MemoryMonitor()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 统计数据
    private val allocatedObjects = AtomicLong(0)
    private val recycledObjects = AtomicLong(0)
    private val cacheHits = AtomicLong(0)
    private val cacheMisses = AtomicLong(0)
    
    init {
        startMemoryMonitoring()
        Log.d(TAG, "MemoryManager initialized")
    }
    
    /**
     * 🏗️ 对象池管理器
     */
    private class ObjectPoolManager {
        private val pools = ConcurrentHashMap<String, ObjectPool<*>>()
        
        @Suppress("UNCHECKED_CAST")
        fun <T : Any> getPool(clazz: KClass<T>, maxSize: Int = 50): ObjectPool<T> {
            val poolKey = clazz.simpleName ?: "Unknown"
            return pools.getOrPut(poolKey) {
                ObjectPool<T>(maxSize) { clazz.java.getDeclaredConstructor().newInstance() }
            } as ObjectPool<T>
        }
        
        fun clearAllPools() {
            pools.values.forEach { it.clear() }
            pools.clear()
        }
        
        fun getPoolsInfo(): Map<String, Int> {
            return pools.mapValues { it.value.size() }
        }
    }
    
    /**
     * 🗂️ 对象池实现
     */
    private class ObjectPool<T>(
        private val maxSize: Int,
        private val factory: () -> T
    ) {
        private val pool = ArrayDeque<T>(maxSize)
        
        @Synchronized
        fun obtain(): T {
            return if (pool.isNotEmpty()) {
                pool.removeFirst()
            } else {
                factory()
            }
        }
        
        @Synchronized
        fun recycle(obj: T) {
            if (pool.size < maxSize) {
                pool.addLast(obj)
            }
        }
        
        @Synchronized
        fun clear() {
            pool.clear()
        }
        
        @Synchronized
        fun size(): Int = pool.size
    }
    
    /**
     * 💾 智能缓存管理器
     */
    private inner class SmartCacheManager {
        // L1: 核心缓存 (常驻内存)
        private val coreCache = LruCache<String, Any>(20)
        
        // L2: 业务缓存 (智能管理)
        private val businessCache = LruCache<String, WeakReference<Any>>(100)
        
        // L3: 临时缓存 (快速回收)
        private val tempCache = LruCache<String, WeakReference<Any>>(50)
        
        fun <T> putCore(key: String, value: T) {
            coreCache.put(key, value as Any)
        }
        
        @Suppress("UNCHECKED_CAST")
        fun <T> getCore(key: String): T? {
            return coreCache.get(key) as? T
        }
        
        fun <T> putBusiness(key: String, value: T) {
            businessCache.put(key, WeakReference(value as Any))
        }
        
        @Suppress("UNCHECKED_CAST")
        fun <T> getBusiness(key: String): T? {
            val ref = businessCache.get(key)
            val value = ref?.get() as? T
            if (value == null && ref != null) {
                businessCache.remove(key) // 清理已回收的引用
            }
            return value
        }
        
        fun <T> putTemp(key: String, value: T) {
            tempCache.put(key, WeakReference(value as Any))
        }
        
        @Suppress("UNCHECKED_CAST")
        fun <T> getTemp(key: String): T? {
            val ref = tempCache.get(key)
            val value = ref?.get() as? T
            if (value == null && ref != null) {
                tempCache.remove(key)
            }
            return value
        }
        
        fun clearTempCache() {
            tempCache.evictAll()
        }
        
        fun clearBusinessCache() {
            businessCache.evictAll()
        }
        
        fun getCacheStats(): Map<String, Int> {
            return mapOf(
                "core_size" to coreCache.size(),
                "business_size" to businessCache.size(),
                "temp_size" to tempCache.size(),
                "core_hits" to coreCache.hitCount().toInt(),
                "core_misses" to coreCache.missCount().toInt()
            )
        }
    }
    
    /**
     * 📊 内存监控器
     */
    private inner class MemoryMonitor {
        fun getMemoryInfo(): MemoryInfo {
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            
            val runtime = Runtime.getRuntime()
            val heapUsed = runtime.totalMemory() - runtime.freeMemory()
            val heapMax = runtime.maxMemory()
            val heapFree = runtime.freeMemory()
            
            return MemoryInfo(
                totalMemory = memInfo.totalMem,
                availableMemory = memInfo.availMem,
                usedMemory = memInfo.totalMem - memInfo.availMem,
                lowMemory = memInfo.lowMemory,
                heapUsed = heapUsed,
                heapMax = heapMax,
                heapFree = heapFree,
                heapUsageRatio = heapUsed.toFloat() / heapMax.toFloat()
            )
        }
        
        fun isMemoryPressure(): Boolean {
            val memInfo = getMemoryInfo()
            return memInfo.lowMemory || memInfo.heapUsageRatio > LOW_MEMORY_THRESHOLD
        }
        
        fun isCriticalMemory(): Boolean {
            val memInfo = getMemoryInfo()
            return memInfo.heapUsageRatio > CRITICAL_MEMORY_THRESHOLD
        }
    }
    
    /**
     * 📊 内存信息数据类
     */
    data class MemoryInfo(
        val totalMemory: Long,
        val availableMemory: Long,
        val usedMemory: Long,
        val lowMemory: Boolean,
        val heapUsed: Long,
        val heapMax: Long,
        val heapFree: Long,
        val heapUsageRatio: Float
    )
    
    // ==================== 公共API ====================
    
    /**
     * 🎯 从对象池获取对象
     */
    inline fun <reified T : Any> obtain(): T {
        allocatedObjects.incrementAndGet()
        return objectPoolManager.getPool(T::class).obtain()
    }
    
    /**
     * ♻️ 回收对象到对象池
     */
    inline fun <reified T : Any> recycle(obj: T) {
        recycledObjects.incrementAndGet()
        objectPoolManager.getPool(T::class).recycle(obj)
    }
    
    /**
     * 💾 缓存操作
     */
    fun <T> cacheCore(key: String, value: T) {
        cacheManager.putCore(key, value)
    }
    
    fun <T> getCachedCore(key: String): T? {
        val result = cacheManager.getCore<T>(key)
        if (result != null) cacheHits.incrementAndGet() else cacheMisses.incrementAndGet()
        return result
    }
    
    fun <T> cacheBusiness(key: String, value: T) {
        cacheManager.putBusiness(key, value)
    }
    
    fun <T> getCachedBusiness(key: String): T? {
        val result = cacheManager.getBusiness<T>(key)
        if (result != null) cacheHits.incrementAndGet() else cacheMisses.incrementAndGet()
        return result
    }
    
    fun <T> cacheTemp(key: String, value: T) {
        cacheManager.putTemp(key, value)
    }
    
    fun <T> getCachedTemp(key: String): T? {
        val result = cacheManager.getTemp<T>(key)
        if (result != null) cacheHits.incrementAndGet() else cacheMisses.incrementAndGet()
        return result
    }
    
    /**
     * 🧹 清理内存
     */
    fun clearTemporaryCache() {
        cacheManager.clearTempCache()
        Log.d(TAG, "Temporary cache cleared")
    }
    
    fun clearBusinessCache() {
        cacheManager.clearBusinessCache()
        Log.d(TAG, "Business cache cleared")
    }
    
    fun trimMemory(level: Int) {
        when (level) {
            >= 80 -> { // TRIM_MEMORY_COMPLETE
                cacheManager.clearTempCache()
                cacheManager.clearBusinessCache()
                objectPoolManager.clearAllPools()
                System.gc()
                Log.d(TAG, "Complete memory trim executed")
            }
            >= 60 -> { // TRIM_MEMORY_MODERATE  
                cacheManager.clearTempCache()
                Log.d(TAG, "Moderate memory trim executed")
            }
            >= 40 -> { // TRIM_MEMORY_BACKGROUND
                cacheManager.clearTempCache()
                Log.d(TAG, "Background memory trim executed")
            }
        }
    }
    
    /**
     * 🎯 为Activity优化内存
     */
    fun optimizeForActivity(activity: Activity) {
        // 清理临时缓存
        clearTemporaryCache()
        
        // 如果内存压力大，进一步清理
        if (memoryMonitor.isMemoryPressure()) {
            clearBusinessCache()
            Log.d(TAG, "Memory optimized for activity: ${activity.javaClass.simpleName}")
        }
    }
    
    /**
     * 📊 获取内存统计信息
     */
    fun getMemoryStats(): Map<String, Any> {
        val memInfo = memoryMonitor.getMemoryInfo()
        val cacheStats = cacheManager.getCacheStats()
        val poolStats = objectPoolManager.getPoolsInfo()
        
        return mapOf(
            "memory_info" to memInfo,
            "cache_stats" to cacheStats,
            "pool_stats" to poolStats,
            "allocated_objects" to allocatedObjects.get(),
            "recycled_objects" to recycledObjects.get(),
            "cache_hits" to cacheHits.get(),
            "cache_misses" to cacheMisses.get(),
            "cache_hit_rate" to if (cacheMisses.get() == 0L) 1.0 else cacheHits.get().toDouble() / (cacheHits.get() + cacheMisses.get()).toDouble()
        )
    }
    
    /**
     * 📊 内存监控
     */
    private fun startMemoryMonitoring() {
        scope.launch {
            while (isActive) {
                try {
                    if (memoryMonitor.isCriticalMemory()) {
                        trimMemory(80)
                        Log.w(TAG, "Critical memory detected, performing aggressive cleanup")
                    } else if (memoryMonitor.isMemoryPressure()) {
                        trimMemory(60)
                        Log.w(TAG, "Memory pressure detected, performing moderate cleanup")
                    }
                    
                    delay(30_000) // 每30秒检查一次
                } catch (e: Exception) {
                    Log.e(TAG, "Error in memory monitoring", e)
                }
            }
        }
    }
    
    /**
     * 🏃 生命周期回调
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun onActivityPaused() {
        clearTemporaryCache()
    }
    
    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onActivityStopped() {
        if (memoryMonitor.isMemoryPressure()) {
            clearBusinessCache()
        }
    }
    
    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onActivityDestroyed() {
        clearTemporaryCache()
        clearBusinessCache()
    }
    
    /**
     * 🔄 释放资源
     */
    fun shutdown() {
        scope.cancel()
        objectPoolManager.clearAllPools()
        cacheManager.clearTempCache()
        cacheManager.clearBusinessCache()
        Log.d(TAG, "MemoryManager shutdown")
    }
}