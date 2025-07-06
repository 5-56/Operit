package com.ai.assistance.operit.core

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.*
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue

/**
 * 内存管理器
 * 负责优化应用内存使用，包括对象池管理、内存监控和自动清理
 */
class MemoryManager private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: MemoryManager? = null
        
        fun getInstance(context: Context): MemoryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MemoryManager(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        // 内存压力阈值
        private const val MEMORY_PRESSURE_THRESHOLD = 0.8f
        private const val LOW_MEMORY_THRESHOLD = 0.9f
        
        // 对象池大小限制
        private const val DEFAULT_POOL_SIZE = 20
        private const val MAX_POOL_SIZE = 50
    }
    
    // 对象池映射
    private val objectPools = ConcurrentHashMap<Class<*>, LinkedBlockingQueue<Any>>()
    
    // 弱引用缓存
    private val weakReferenceCache = ConcurrentHashMap<String, WeakReference<Any>>()
    
    // 内存监控
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val memoryInfo = ActivityManager.MemoryInfo()
    
    // 协程作用域
    private val memoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // 监控标志
    @Volatile
    private var isMonitoring = false
    
    init {
        startMemoryMonitoring()
    }
    
    /**
     * 从对象池获取对象
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> obtain(clazz: Class<T>): T? {
        val pool = objectPools[clazz] ?: return null
        return pool.poll() as? T
    }
    
    /**
     * 回收对象到对象池
     */
    fun <T : Any> recycle(obj: T) {
        val clazz = obj.javaClass
        val pool = objectPools.getOrPut(clazz) { 
            LinkedBlockingQueue<Any>(DEFAULT_POOL_SIZE) 
        }
        
        // 清理对象状态（如果对象实现了Recyclable接口）
        if (obj is Recyclable) {
            obj.reset()
        }
        
        // 如果池未满，则回收对象
        if (pool.size < DEFAULT_POOL_SIZE) {
            pool.offer(obj)
        }
    }
    
    /**
     * 创建或获取对象
     */
    inline fun <reified T : Any> obtainOrCreate(factory: () -> T): T {
        return obtain(T::class.java) ?: factory()
    }
    
    /**
     * 缓存对象（弱引用）
     */
    fun cacheObject(key: String, obj: Any) {
        weakReferenceCache[key] = WeakReference(obj)
    }
    
    /**
     * 获取缓存对象
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getCachedObject(key: String): T? {
        return weakReferenceCache[key]?.get() as? T
    }
    
    /**
     * 清理缓存
     */
    fun clearCache() {
        weakReferenceCache.clear()
    }
    
    /**
     * 获取内存使用信息
     */
    fun getMemoryInfo(): MemoryStatus {
        activityManager.getMemoryInfo(memoryInfo)
        
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val totalMemory = runtime.totalMemory()
        val maxMemory = runtime.maxMemory()
        
        val memoryUsageRatio = usedMemory.toFloat() / maxMemory.toFloat()
        
        return MemoryStatus(
            usedMemory = usedMemory,
            totalMemory = totalMemory,
            maxMemory = maxMemory,
            availableMemory = memoryInfo.availMem,
            memoryUsageRatio = memoryUsageRatio,
            isLowMemory = memoryInfo.lowMemory,
            isMemoryPressure = memoryUsageRatio > MEMORY_PRESSURE_THRESHOLD
        )
    }
    
    /**
     * 强制垃圾回收
     */
    fun forceGarbageCollection() {
        System.gc()
        System.runFinalization()
    }
    
    /**
     * 内存压力处理
     */
    private fun handleMemoryPressure() {
        memoryScope.launch {
            // 清理对象池
            cleanupObjectPools()
            
            // 清理弱引用缓存
            cleanupWeakReferences()
            
            // 强制垃圾回收
            forceGarbageCollection()
            
            delay(1000) // 等待GC完成
        }
    }
    
    /**
     * 清理对象池
     */
    private fun cleanupObjectPools() {
        objectPools.values.forEach { pool ->
            // 保留一半的对象
            val keepSize = pool.size / 2
            repeat(pool.size - keepSize) {
                pool.poll()
            }
        }
    }
    
    /**
     * 清理失效的弱引用
     */
    private fun cleanupWeakReferences() {
        val iterator = weakReferenceCache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.get() == null) {
                iterator.remove()
            }
        }
    }
    
    /**
     * 开始内存监控
     */
    private fun startMemoryMonitoring() {
        if (isMonitoring) return
        
        isMonitoring = true
        memoryScope.launch {
            while (isMonitoring) {
                val memoryStatus = getMemoryInfo()
                
                when {
                    memoryStatus.isLowMemory || memoryStatus.memoryUsageRatio > LOW_MEMORY_THRESHOLD -> {
                        handleMemoryPressure()
                    }
                    memoryStatus.isMemoryPressure -> {
                        cleanupWeakReferences()
                    }
                }
                
                delay(5000) // 每5秒检查一次
            }
        }
    }
    
    /**
     * 停止内存监控
     */
    fun stopMemoryMonitoring() {
        isMonitoring = false
        memoryScope.cancel()
    }
    
    /**
     * 获取内存调试信息
     */
    fun getDebugInfo(): MemoryDebugInfo {
        val memInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memInfo)
        
        return MemoryDebugInfo(
            dalvikPrivateDirty = memInfo.dalvikPrivateDirty,
            dalvikPss = memInfo.dalvikPss,
            nativePrivateDirty = memInfo.nativePrivateDirty,
            nativePss = memInfo.nativePss,
            otherPrivateDirty = memInfo.otherPrivateDirty,
            otherPss = memInfo.otherPss,
            totalPrivateDirty = memInfo.totalPrivateDirty,
            totalPss = memInfo.totalPss,
            objectPoolsCount = objectPools.size,
            totalPooledObjects = objectPools.values.sumOf { it.size },
            cachedObjectsCount = weakReferenceCache.size
        )
    }
    
    /**
     * 可回收对象接口
     */
    interface Recyclable {
        fun reset()
    }
    
    /**
     * 内存状态数据类
     */
    data class MemoryStatus(
        val usedMemory: Long,
        val totalMemory: Long,
        val maxMemory: Long,
        val availableMemory: Long,
        val memoryUsageRatio: Float,
        val isLowMemory: Boolean,
        val isMemoryPressure: Boolean
    )
    
    /**
     * 内存调试信息数据类
     */
    data class MemoryDebugInfo(
        val dalvikPrivateDirty: Int,
        val dalvikPss: Int,
        val nativePrivateDirty: Int,
        val nativePss: Int,
        val otherPrivateDirty: Int,
        val otherPss: Int,
        val totalPrivateDirty: Int,
        val totalPss: Int,
        val objectPoolsCount: Int,
        val totalPooledObjects: Int,
        val cachedObjectsCount: Int
    )
}

/**
 * 内存管理器扩展函数
 */
inline fun <reified T : Any> MemoryManager.use(crossinline block: (T) -> Unit) {
    val obj = obtainOrCreate { T::class.java.newInstance() }
    try {
        block(obj)
    } finally {
        recycle(obj)
    }
}