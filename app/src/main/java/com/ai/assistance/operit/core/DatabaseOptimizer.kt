package com.ai.assistance.operit.core

import android.content.Context
import android.database.Cursor
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

/**
 * 数据库性能优化组件
 * 提供查询优化、缓存管理、性能监控等功能
 */
class DatabaseOptimizer private constructor(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "DatabaseOptimizer"
        
        @Volatile
        private var INSTANCE: DatabaseOptimizer? = null
        
        fun getInstance(context: Context): DatabaseOptimizer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DatabaseOptimizer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val optimizationMutex = Mutex()
    
    // 性能统计
    private val queryStats = ConcurrentHashMap<String, QueryStats>()
    private val _databaseMetrics = MutableStateFlow(DatabaseMetrics())
    val databaseMetrics: StateFlow<DatabaseMetrics> = _databaseMetrics.asStateFlow()
    
    // 查询缓存
    private val queryCache = ConcurrentHashMap<String, CachedQuery>()
    private val maxCacheSize = 100
    private val cacheExpirationTime = 5 * 60 * 1000L // 5分钟
    
    // 性能计数器
    private val totalQueries = AtomicLong(0)
    private val cacheHits = AtomicLong(0)
    private val slowQueries = AtomicLong(0)
    private val failedQueries = AtomicLong(0)
    
    init {
        startMetricsCollection()
        scheduleOptimizationTasks()
    }
    
    /**
     * 开始指标收集
     */
    private fun startMetricsCollection() {
        scope.launch {
            while (true) {
                updateDatabaseMetrics()
                kotlinx.coroutines.delay(30_000) // 每30秒更新
            }
        }
    }
    
    /**
     * 调度优化任务
     */
    private fun scheduleOptimizationTasks() {
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000 * 10) // 每10分钟
                performAutomaticOptimizations()
            }
        }
    }
    
    /**
     * 更新数据库指标
     */
    private fun updateDatabaseMetrics() {
        val totalCount = totalQueries.get()
        val hitCount = cacheHits.get()
        val slowCount = slowQueries.get()
        val failedCount = failedQueries.get()
        
        val averageLatency = if (queryStats.isNotEmpty()) {
            queryStats.values.sumOf { it.totalTime } / queryStats.values.sumOf { it.count }
        } else 0.0
        
        val cacheHitRate = if (totalCount > 0) {
            (hitCount.toFloat() / totalCount * 100)
        } else 0f
        
        val slowQueryRate = if (totalCount > 0) {
            (slowCount.toFloat() / totalCount * 100)
        } else 0f
        
        _databaseMetrics.value = DatabaseMetrics(
            totalQueries = totalCount,
            cacheHitRate = cacheHitRate,
            averageLatency = averageLatency,
            slowQueryRate = slowQueryRate,
            failedQueries = failedCount,
            activeConnections = getActiveConnectionCount(),
            cacheSize = queryCache.size
        )
    }
    
    /**
     * 获取活跃连接数
     */
    private fun getActiveConnectionCount(): Int {
        // 这里可以通过反射或其他方式获取Room数据库的连接数
        // 简化实现，返回估算值
        return 1
    }
    
    /**
     * 执行优化的查询
     */
    suspend fun executeOptimizedQuery(
        database: SupportSQLiteDatabase,
        query: String,
        args: Array<Any>? = null,
        useCache: Boolean = true
    ): Cursor? = withContext(Dispatchers.IO) {
        val queryKey = generateQueryKey(query, args)
        
        // 检查缓存
        if (useCache) {
            val cachedResult = getCachedQuery(queryKey)
            if (cachedResult != null) {
                cacheHits.incrementAndGet()
                Log.d(TAG, "缓存命中: $queryKey")
                return@withContext cachedResult
            }
        }
        
        totalQueries.incrementAndGet()
        val startTime = System.currentTimeMillis()
        
        return@withContext try {
            val cursor = if (args != null) {
                database.query(SimpleSQLiteQuery(query, args))
            } else {
                database.query(query)
            }
            
            val executionTime = System.currentTimeMillis() - startTime
            recordQueryStats(query, executionTime, success = true)
            
            // 缓存结果（对于小结果集）
            if (useCache && shouldCacheQuery(cursor, executionTime)) {
                cacheQuery(queryKey, cursor, executionTime)
            }
            
            cursor
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            recordQueryStats(query, executionTime, success = false)
            failedQueries.incrementAndGet()
            Log.e(TAG, "查询执行失败: $query", e)
            throw e
        }
    }
    
    /**
     * 生成查询缓存键
     */
    private fun generateQueryKey(query: String, args: Array<Any>?): String {
        return if (args != null) {
            "${query.hashCode()}_${args.contentHashCode()}"
        } else {
            query.hashCode().toString()
        }
    }
    
    /**
     * 获取缓存的查询结果
     */
    private fun getCachedQuery(queryKey: String): Cursor? {
        val cached = queryCache[queryKey]
        return if (cached != null && !cached.isExpired()) {
            cached.cursor
        } else {
            queryCache.remove(queryKey)
            null
        }
    }
    
    /**
     * 判断是否应该缓存查询
     */
    private fun shouldCacheQuery(cursor: Cursor?, executionTime: Long): Boolean {
        return cursor != null && 
               cursor.count <= 100 && // 小结果集
               executionTime > 50 && // 执行时间超过50ms
               queryCache.size < maxCacheSize
    }
    
    /**
     * 缓存查询结果
     */
    private fun cacheQuery(queryKey: String, cursor: Cursor, executionTime: Long) {
        if (queryCache.size >= maxCacheSize) {
            // 清理最旧的缓存项
            clearOldestCacheEntries(10)
        }
        
        queryCache[queryKey] = CachedQuery(
            cursor = cursor,
            cachedAt = System.currentTimeMillis(),
            executionTime = executionTime
        )
    }
    
    /**
     * 清理最旧的缓存项
     */
    private fun clearOldestCacheEntries(count: Int) {
        val sortedEntries = queryCache.entries.sortedBy { it.value.cachedAt }
        repeat(minOf(count, sortedEntries.size)) {
            val entryToRemove = sortedEntries[it]
            queryCache.remove(entryToRemove.key)
            entryToRemove.value.cursor?.close()
        }
    }
    
    /**
     * 记录查询统计
     */
    private fun recordQueryStats(query: String, executionTime: Long, success: Boolean) {
        val queryType = extractQueryType(query)
        val stats = queryStats.getOrPut(queryType) { QueryStats() }
        
        stats.count++
        stats.totalTime += executionTime
        
        if (success) {
            stats.successCount++
        } else {
            stats.failureCount++
        }
        
        if (executionTime > 1000) { // 慢查询阈值：1秒
            stats.slowCount++
            slowQueries.incrementAndGet()
            Log.w(TAG, "慢查询检测: $query (${executionTime}ms)")
        }
        
        stats.lastExecuted = System.currentTimeMillis()
    }
    
    /**
     * 提取查询类型
     */
    private fun extractQueryType(query: String): String {
        val trimmed = query.trim().uppercase()
        return when {
            trimmed.startsWith("SELECT") -> "SELECT"
            trimmed.startsWith("INSERT") -> "INSERT"
            trimmed.startsWith("UPDATE") -> "UPDATE"
            trimmed.startsWith("DELETE") -> "DELETE"
            trimmed.startsWith("CREATE") -> "CREATE"
            trimmed.startsWith("DROP") -> "DROP"
            trimmed.startsWith("ALTER") -> "ALTER"
            else -> "OTHER"
        }
    }
    
    /**
     * 执行自动优化
     */
    private suspend fun performAutomaticOptimizations() {
        optimizationMutex.withLock {
            try {
                Log.d(TAG, "开始自动数据库优化...")
                
                // 1. 清理过期缓存
                cleanExpiredCache()
                
                // 2. 分析慢查询
                analyzeSlowQueries()
                
                // 3. 优化查询缓存
                optimizeQueryCache()
                
                Log.d(TAG, "自动数据库优化完成")
                
            } catch (e: Exception) {
                Log.e(TAG, "自动优化失败", e)
            }
        }
    }
    
    /**
     * 清理过期缓存
     */
    private fun cleanExpiredCache() {
        val currentTime = System.currentTimeMillis()
        val expiredKeys = queryCache.entries.filter { 
            currentTime - it.value.cachedAt > cacheExpirationTime 
        }.map { it.key }
        
        expiredKeys.forEach { key ->
            queryCache.remove(key)?.cursor?.close()
        }
        
        if (expiredKeys.isNotEmpty()) {
            Log.d(TAG, "清理了 ${expiredKeys.size} 个过期缓存项")
        }
    }
    
    /**
     * 分析慢查询
     */
    private fun analyzeSlowQueries() {
        val slowQueries = queryStats.entries.filter { 
            it.value.slowCount > 0 
        }.sortedByDescending { 
            it.value.averageTime() 
        }
        
        if (slowQueries.isNotEmpty()) {
            Log.w(TAG, "发现 ${slowQueries.size} 种慢查询类型")
            slowQueries.take(5).forEach { (type, stats) ->
                Log.w(TAG, "慢查询: $type - 平均时间: ${stats.averageTime()}ms, 次数: ${stats.slowCount}")
            }
        }
    }
    
    /**
     * 优化查询缓存
     */
    private fun optimizeQueryCache() {
        if (queryCache.size > maxCacheSize * 0.8) {
            // 移除访问频率最低的缓存项
            val sortedByUsage = queryCache.entries.sortedBy { it.value.cachedAt }
            val toRemove = sortedByUsage.take(queryCache.size / 4)
            
            toRemove.forEach { entry ->
                queryCache.remove(entry.key)
                entry.value.cursor?.close()
            }
            
            Log.d(TAG, "优化缓存: 移除了 ${toRemove.size} 个低频访问项")
        }
    }
    
    /**
     * 配置Room数据库优化
     */
    fun configureRoomDatabase(builder: RoomDatabase.Builder<*>): RoomDatabase.Builder<*> {
        return builder
            .setJournalMode(RoomDatabase.JournalMode.WAL) // 使用WAL模式
            .setQueryCallback({ sqlQuery, bindArgs ->
                scope.launch {
                    val startTime = System.currentTimeMillis()
                    // 这里可以添加查询监控逻辑
                    Log.d(TAG, "执行查询: $sqlQuery")
                }
            }, scope::launch)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    optimizeDatabaseSettings(db)
                }
                
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    optimizeDatabaseSettings(db)
                }
            })
    }
    
    /**
     * 优化数据库设置
     */
    private fun optimizeDatabaseSettings(db: SupportSQLiteDatabase) {
        try {
            // 设置SQLite优化参数
            db.execSQL("PRAGMA synchronous = NORMAL") // 平衡性能和安全性
            db.execSQL("PRAGMA cache_size = 10000") // 增加缓存大小
            db.execSQL("PRAGMA temp_store = MEMORY") // 临时存储使用内存
            db.execSQL("PRAGMA mmap_size = 268435456") // 256MB内存映射
            db.execSQL("PRAGMA optimize") // 自动优化
            
            Log.d(TAG, "数据库优化设置已应用")
        } catch (e: Exception) {
            Log.e(TAG, "应用数据库优化设置失败", e)
        }
    }
    
    /**
     * 获取查询建议
     */
    fun getQueryRecommendations(): List<QueryRecommendation> {
        val recommendations = mutableListOf<QueryRecommendation>()
        
        // 分析慢查询
        queryStats.entries.forEach { (type, stats) ->
            if (stats.averageTime() > 500) { // 平均时间超过500ms
                recommendations.add(
                    QueryRecommendation(
                        type = QueryRecommendation.Type.SLOW_QUERY,
                        description = "$type 查询平均耗时 ${stats.averageTime()}ms，建议添加索引或优化查询逻辑",
                        priority = QueryRecommendation.Priority.HIGH,
                        queryType = type
                    )
                )
            }
        }
        
        // 分析缓存使用率
        val totalQueries = totalQueries.get()
        val hitRate = if (totalQueries > 0) cacheHits.get().toFloat() / totalQueries else 0f
        
        if (hitRate < 0.3f && totalQueries > 100) {
            recommendations.add(
                QueryRecommendation(
                    type = QueryRecommendation.Type.LOW_CACHE_HIT,
                    description = "查询缓存命中率较低 (${String.format("%.1f", hitRate * 100)}%)，建议优化查询模式",
                    priority = QueryRecommendation.Priority.MEDIUM
                )
            )
        }
        
        // 分析失败率
        val failureRate = if (totalQueries > 0) failedQueries.get().toFloat() / totalQueries else 0f
        
        if (failureRate > 0.05f) { // 失败率超过5%
            recommendations.add(
                QueryRecommendation(
                    type = QueryRecommendation.Type.HIGH_FAILURE_RATE,
                    description = "查询失败率较高 (${String.format("%.1f", failureRate * 100)}%)，请检查查询逻辑和数据完整性",
                    priority = QueryRecommendation.Priority.HIGH
                )
            )
        }
        
        return recommendations
    }
    
    /**
     * 清理缓存
     */
    suspend fun clearCache() {
        withContext(Dispatchers.IO) {
            queryCache.values.forEach { it.cursor?.close() }
            queryCache.clear()
            Log.d(TAG, "数据库查询缓存已清理")
        }
    }
    
    /**
     * 重置统计信息
     */
    fun resetStats() {
        queryStats.clear()
        totalQueries.set(0)
        cacheHits.set(0)
        slowQueries.set(0)
        failedQueries.set(0)
        Log.d(TAG, "数据库统计信息已重置")
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        scope.launch {
            clearCache()
            resetStats()
            Log.d(TAG, "数据库优化器资源已清理")
        }
    }
    
    // ==================== 数据类定义 ====================
    
    data class DatabaseMetrics(
        val totalQueries: Long = 0,
        val cacheHitRate: Float = 0f,
        val averageLatency: Double = 0.0,
        val slowQueryRate: Float = 0f,
        val failedQueries: Long = 0,
        val activeConnections: Int = 0,
        val cacheSize: Int = 0
    )
    
    data class QueryStats(
        var count: Long = 0,
        var successCount: Long = 0,
        var failureCount: Long = 0,
        var slowCount: Long = 0,
        var totalTime: Long = 0,
        var lastExecuted: Long = 0
    ) {
        fun averageTime(): Double = if (count > 0) totalTime.toDouble() / count else 0.0
    }
    
    data class CachedQuery(
        val cursor: Cursor?,
        val cachedAt: Long,
        val executionTime: Long
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - cachedAt > 5 * 60 * 1000L
    }
    
    data class QueryRecommendation(
        val type: Type,
        val description: String,
        val priority: Priority,
        val queryType: String? = null
    ) {
        enum class Type {
            SLOW_QUERY,
            LOW_CACHE_HIT,
            HIGH_FAILURE_RATE,
            OPTIMIZATION_SUGGESTION
        }
        
        enum class Priority {
            LOW, MEDIUM, HIGH
        }
    }
}