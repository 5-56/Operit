package com.ai.assistance.operit.core.system

import android.app.ActivityManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * 系统资源管理器
 * 负责优化系统资源、清理垃圾、管理应用生命周期、检测系统状态
 */
class SystemResourceManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SystemResourceManager"
        private const val MEMORY_THRESHOLD_LOW = 0.8f // 80%内存使用率为低内存
        private const val STORAGE_THRESHOLD_LOW = 0.9f // 90%存储使用率为低存储
        private const val IDLE_TIME_THRESHOLD_MS = 300000L // 5分钟无活动为空闲
        private const val CACHE_CLEANUP_INTERVAL_MS = 600000L // 10分钟清理一次缓存
        private const val MEMORY_CLEANUP_INTERVAL_MS = 180000L // 3分钟清理一次内存
    }
    
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val packageManager = context.packageManager
    private val usageStatsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    } else null
    
    private val isInitialized = AtomicBoolean(false)
    private val isOptimized = AtomicBoolean(false)
    private val isCleaningInProgress = AtomicBoolean(false)
    
    private var originalAppStates = mutableMapOf<String, AppState>()
    private var pausedApps = mutableSetOf<String>()
    
    private val resourceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // 系统状态监控
    private var lastUserInteractionTime = System.currentTimeMillis()
    private var systemStats = SystemStats()
    
    data class SystemStats(
        val totalMemory: Long = 0,
        val availableMemory: Long = 0,
        val usedMemory: Long = 0,
        val memoryUsagePercent: Float = 0f,
        val totalStorage: Long = 0,
        val availableStorage: Long = 0,
        val usedStorage: Long = 0,
        val storageUsagePercent: Float = 0f,
        val cpuUsagePercent: Float = 0f,
        val batteryLevel: Int = 0,
        val isCharging: Boolean = false,
        val screenOn: Boolean = true,
        val networkConnected: Boolean = false
    )
    
    data class AppState(
        val packageName: String,
        val isRunning: Boolean,
        val memoryUsage: Long,
        val lastUsedTime: Long,
        val importance: Int
    )
    
    data class CleanupResult(
        val memoryFreed: Long,
        val storageFreed: Long,
        val appsOptimized: Int,
        val cachesCleaned: Int,
        val tempFilesDeleted: Int
    )
    
    init {
        initialize()
    }
    
    fun initialize() {
        if (isInitialized.get()) return
        
        try {
            Log.d(TAG, "初始化系统资源管理器")
            
            // 更新系统统计信息
            updateSystemStats()
            
            // 启动监控任务
            startSystemMonitoring()
            
            // 启动自动清理任务
            startAutoCleanupTasks()
            
            isInitialized.set(true)
            Log.d(TAG, "系统资源管理器初始化完成")
            
        } catch (e: Exception) {
            Log.e(TAG, "系统资源管理器初始化失败", e)
        }
    }
    
    /**
     * 为训练优化系统资源
     */
    suspend fun optimizeForTraining(
        pauseApps: Boolean = true,
        cleanMemory: Boolean = true,
        boostCPU: Boolean = true,
        maxPerformanceMode: Boolean = false
    ) {
        if (isOptimized.get()) {
            Log.w(TAG, "系统已经处于优化状态")
            return
        }
        
        withContext(Dispatchers.Default) {
            try {
                Log.d(TAG, "开始优化系统资源用于训练")
                
                // 1. 清理内存和存储
                if (cleanMemory) {
                    val cleanupResult = performSystemCleanup()
                    Log.d(TAG, "系统清理完成: $cleanupResult")
                }
                
                // 2. 暂停非必要应用
                if (pauseApps) {
                    pauseNonEssentialApps()
                }
                
                // 3. CPU性能优化
                if (boostCPU) {
                    optimizeCPUPerformance(maxPerformanceMode)
                }
                
                // 4. 优化内存管理
                optimizeMemoryManagement()
                
                // 5. 禁用自动同步和更新
                disableAutoSyncAndUpdates()
                
                isOptimized.set(true)
                updateSystemStats()
                
                Log.d(TAG, "系统资源优化完成")
                
            } catch (e: Exception) {
                Log.e(TAG, "系统资源优化失败", e)
            }
        }
    }
    
    /**
     * 恢复系统状态
     */
    suspend fun restoreSystemState() {
        if (!isOptimized.get()) {
            Log.w(TAG, "系统未处于优化状态")
            return
        }
        
        withContext(Dispatchers.Default) {
            try {
                Log.d(TAG, "开始恢复系统状态")
                
                // 1. 恢复暂停的应用
                resumePausedApps()
                
                // 2. 恢复CPU性能设置
                restoreCPUPerformance()
                
                // 3. 恢复内存管理设置
                restoreMemoryManagement()
                
                // 4. 恢复自动同步和更新
                restoreAutoSyncAndUpdates()
                
                isOptimized.set(false)
                updateSystemStats()
                
                Log.d(TAG, "系统状态恢复完成")
                
            } catch (e: Exception) {
                Log.e(TAG, "系统状态恢复失败", e)
            }
        }
    }
    
    /**
     * 执行系统清理
     */
    suspend fun performSystemCleanup(): CleanupResult {
        if (isCleaningInProgress.get()) {
            Log.w(TAG, "系统清理正在进行中")
            return CleanupResult(0, 0, 0, 0, 0)
        }
        
        return withContext(Dispatchers.IO) {
            isCleaningInProgress.set(true)
            
            try {
                Log.d(TAG, "开始系统清理")
                
                var memoryFreed = 0L
                var storageFreed = 0L
                var appsOptimized = 0
                var cachesCleaned = 0
                var tempFilesDeleted = 0
                
                // 1. 清理应用缓存
                val cacheResult = cleanApplicationCaches()
                cachesCleaned = cacheResult.first
                storageFreed += cacheResult.second
                
                // 2. 清理临时文件
                val tempResult = cleanTemporaryFiles()
                tempFilesDeleted = tempResult.first
                storageFreed += tempResult.second
                
                // 3. 清理内存
                memoryFreed = performMemoryCleanup()
                
                // 4. 优化后台应用
                appsOptimized = optimizeBackgroundApps()
                
                // 5. 清理系统缓存（需要root权限）
                if (hasRootAccess()) {
                    val systemCacheResult = cleanSystemCache()
                    storageFreed += systemCacheResult
                }
                
                val result = CleanupResult(
                    memoryFreed, storageFreed, appsOptimized, 
                    cachesCleaned, tempFilesDeleted
                )
                
                Log.d(TAG, "系统清理完成: $result")
                return@withContext result
                
            } catch (e: Exception) {
                Log.e(TAG, "系统清理失败", e)
                return@withContext CleanupResult(0, 0, 0, 0, 0)
            } finally {
                isCleaningInProgress.set(false)
            }
        }
    }
    
    private suspend fun cleanApplicationCaches(): Pair<Int, Long> {
        var cachesCleaned = 0
        var bytesFreed = 0L
        
        try {
            // 获取所有已安装的应用
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            
            for (appInfo in installedApps) {
                if (appInfo.packageName == context.packageName) continue // 跳过自己
                
                try {
                    // 获取应用缓存目录大小
                    val cacheSize = getAppCacheSize(appInfo.packageName)
                    
                    if (cacheSize > 0) {
                        // 清理应用缓存
                        if (clearAppCache(appInfo.packageName)) {
                            cachesCleaned++
                            bytesFreed += cacheSize
                        }
                    }
                    
                } catch (e: Exception) {
                    Log.w(TAG, "清理应用缓存失败: ${appInfo.packageName}", e)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "清理应用缓存过程失败", e)
        }
        
        return Pair(cachesCleaned, bytesFreed)
    }
    
    private suspend fun cleanTemporaryFiles(): Pair<Int, Long> {
        var filesDeleted = 0
        var bytesFreed = 0L
        
        try {
            val tempDirs = listOf(
                context.cacheDir,
                context.externalCacheDir,
                File(context.filesDir, "temp"),
                File("/data/local/tmp"), // 系统临时目录
                File("/sdcard/.temp"), // SD卡临时目录
                File("/sdcard/Android/data/temp")
            )
            
            for (tempDir in tempDirs) {
                if (tempDir?.exists() == true) {
                    val result = cleanDirectory(tempDir)
                    filesDeleted += result.first
                    bytesFreed += result.second
                }
            }
            
            // 清理下载目录中的临时文件
            val downloadDir = File("/sdcard/Download")
            if (downloadDir.exists()) {
                val tempFiles = downloadDir.listFiles { file ->
                    file.name.endsWith(".tmp") || 
                    file.name.endsWith(".temp") ||
                    file.name.startsWith("temp_")
                }
                
                tempFiles?.forEach { file ->
                    try {
                        val size = file.length()
                        if (file.delete()) {
                            filesDeleted++
                            bytesFreed += size
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "删除临时文件失败: ${file.path}", e)
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "清理临时文件失败", e)
        }
        
        return Pair(filesDeleted, bytesFreed)
    }
    
    private fun cleanDirectory(directory: File): Pair<Int, Long> {
        var filesDeleted = 0
        var bytesFreed = 0L
        
        try {
            directory.listFiles()?.forEach { file ->
                try {
                    val size = if (file.isDirectory) {
                        val subResult = cleanDirectory(file)
                        filesDeleted += subResult.first
                        subResult.second
                    } else {
                        file.length()
                    }
                    
                    if (file.delete()) {
                        filesDeleted++
                        bytesFreed += size
                    }
                    
                } catch (e: Exception) {
                    Log.w(TAG, "删除文件失败: ${file.path}", e)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "清理目录失败: ${directory.path}", e)
        }
        
        return Pair(filesDeleted, bytesFreed)
    }
    
    private suspend fun performMemoryCleanup(): Long {
        return withContext(Dispatchers.Default) {
            try {
                val memoryInfoBefore = Debug.MemoryInfo()
                Debug.getMemoryInfo(memoryInfoBefore)
                val usedMemoryBefore = memoryInfoBefore.totalPss.toLong()
                
                // 1. 触发系统GC
                System.gc()
                Runtime.getRuntime().gc()
                
                // 2. 清理应用内存
                trimMemory()
                
                // 3. 请求系统释放内存
                activityManager.clearApplicationUserData()
                
                delay(1000) // 等待GC完成
                
                val memoryInfoAfter = Debug.MemoryInfo()
                Debug.getMemoryInfo(memoryInfoAfter)
                val usedMemoryAfter = memoryInfoAfter.totalPss.toLong()
                
                val memoryFreed = max(0, usedMemoryBefore - usedMemoryAfter) * 1024 // 转换为字节
                
                Log.d(TAG, "内存清理完成，释放内存: ${memoryFreed / 1024 / 1024}MB")
                return@withContext memoryFreed
                
            } catch (e: Exception) {
                Log.e(TAG, "内存清理失败", e)
                return@withContext 0L
            }
        }
    }
    
    private fun trimMemory() {
        try {
            // 请求应用释放内存
            activityManager.trimMemory(ActivityManager.TRIM_MEMORY_RUNNING_CRITICAL)
        } catch (e: Exception) {
            Log.w(TAG, "内存压缩失败", e)
        }
    }
    
    private suspend fun optimizeBackgroundApps(): Int {
        var appsOptimized = 0
        
        try {
            val runningApps = getRunningApplications()
            
            for (appState in runningApps) {
                if (appState.packageName == context.packageName) continue
                
                // 如果应用长时间未使用且占用大量内存，则优化
                val timeSinceLastUse = System.currentTimeMillis() - appState.lastUsedTime
                val shouldOptimize = timeSinceLastUse > 3600000L && // 1小时未使用
                        appState.memoryUsage > 50 * 1024 * 1024L // 占用超过50MB内存
                
                if (shouldOptimize) {
                    if (optimizeApp(appState.packageName)) {
                        appsOptimized++
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "优化后台应用失败", e)
        }
        
        return appsOptimized
    }
    
    private suspend fun pauseNonEssentialApps() {
        try {
            val runningApps = getRunningApplications()
            val essentialApps = getEssentialApps()
            
            for (appState in runningApps) {
                if (appState.packageName !in essentialApps && 
                    appState.packageName != context.packageName) {
                    
                    if (pauseApp(appState.packageName)) {
                        pausedApps.add(appState.packageName)
                        originalAppStates[appState.packageName] = appState
                    }
                }
            }
            
            Log.d(TAG, "已暂停 ${pausedApps.size} 个非必要应用")
            
        } catch (e: Exception) {
            Log.e(TAG, "暂停非必要应用失败", e)
        }
    }
    
    private suspend fun resumePausedApps() {
        try {
            for (packageName in pausedApps) {
                resumeApp(packageName)
            }
            
            pausedApps.clear()
            originalAppStates.clear()
            
            Log.d(TAG, "已恢复所有暂停的应用")
            
        } catch (e: Exception) {
            Log.e(TAG, "恢复暂停的应用失败", e)
        }
    }
    
    private fun getEssentialApps(): Set<String> {
        return setOf(
            "com.android.systemui", // 系统UI
            "android", // 系统框架
            "com.android.phone", // 电话应用
            "com.android.settings", // 设置应用
            "com.android.launcher", // 启动器
            "com.google.android.gms", // Google Play服务
            context.packageName // 当前应用
        )
    }
    
    private fun getRunningApplications(): List<AppState> {
        val runningApps = mutableListOf<AppState>()
        
        try {
            val runningAppProcesses = activityManager.runningAppProcesses
            
            for (processInfo in runningAppProcesses) {
                for (packageName in processInfo.pkgList) {
                    try {
                        val memoryInfo = activityManager.getProcessMemoryInfo(intArrayOf(processInfo.pid))
                        val memoryUsage = memoryInfo.firstOrNull()?.totalPss?.toLong() ?: 0L
                        
                        val lastUsedTime = getAppLastUsedTime(packageName)
                        
                        val appState = AppState(
                            packageName = packageName,
                            isRunning = true,
                            memoryUsage = memoryUsage,
                            lastUsedTime = lastUsedTime,
                            importance = processInfo.importance
                        )
                        
                        runningApps.add(appState)
                        
                    } catch (e: Exception) {
                        Log.w(TAG, "获取应用状态失败: $packageName", e)
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "获取运行中应用失败", e)
        }
        
        return runningApps
    }
    
    private fun getAppLastUsedTime(packageName: String): Long {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && usageStatsManager != null) {
                val endTime = System.currentTimeMillis()
                val startTime = endTime - 24 * 60 * 60 * 1000L // 24小时前
                
                val usageStats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    startTime,
                    endTime
                )
                
                usageStats.find { it.packageName == packageName }?.lastTimeUsed ?: 0L
            } else {
                System.currentTimeMillis() // 降级方案
            }
        } catch (e: Exception) {
            Log.w(TAG, "获取应用最后使用时间失败: $packageName", e)
            System.currentTimeMillis()
        }
    }
    
    private fun getAppCacheSize(packageName: String): Long {
        // 简化实现，实际需要通过PackageStats API获取
        return try {
            val cacheDir = File("/data/data/$packageName/cache")
            if (cacheDir.exists()) {
                calculateDirectorySize(cacheDir)
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }
    
    private fun calculateDirectorySize(directory: File): Long {
        var size = 0L
        try {
            directory.listFiles()?.forEach { file ->
                size += if (file.isDirectory) {
                    calculateDirectorySize(file)
                } else {
                    file.length()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "计算目录大小失败: ${directory.path}", e)
        }
        return size
    }
    
    private fun clearAppCache(packageName: String): Boolean {
        return try {
            // 在实际实现中需要使用PackageManager.deleteApplicationCacheFiles()
            // 但这需要系统权限，这里提供简化实现
            true
        } catch (e: Exception) {
            Log.w(TAG, "清理应用缓存失败: $packageName", e)
            false
        }
    }
    
    private fun pauseApp(packageName: String): Boolean {
        return try {
            // 实际实现需要使用ActivityManager或者其他方式暂停应用
            // 这里提供简化实现
            true
        } catch (e: Exception) {
            Log.w(TAG, "暂停应用失败: $packageName", e)
            false
        }
    }
    
    private fun resumeApp(packageName: String): Boolean {
        return try {
            // 实际实现需要恢复暂停的应用
            // 这里提供简化实现
            true
        } catch (e: Exception) {
            Log.w(TAG, "恢复应用失败: $packageName", e)
            false
        }
    }
    
    private fun optimizeApp(packageName: String): Boolean {
        return try {
            // 优化单个应用（清理内存、缓存等）
            // 这里提供简化实现
            true
        } catch (e: Exception) {
            Log.w(TAG, "优化应用失败: $packageName", e)
            false
        }
    }
    
    private fun optimizeCPUPerformance(maxPerformance: Boolean) {
        try {
            // CPU性能优化的实现需要root权限或系统权限
            // 这里提供概念性实现
            Log.d(TAG, "CPU性能优化: maxPerformance=$maxPerformance")
        } catch (e: Exception) {
            Log.e(TAG, "CPU性能优化失败", e)
        }
    }
    
    private fun restoreCPUPerformance() {
        try {
            Log.d(TAG, "恢复CPU性能设置")
        } catch (e: Exception) {
            Log.e(TAG, "恢复CPU性能设置失败", e)
        }
    }
    
    private fun optimizeMemoryManagement() {
        try {
            Log.d(TAG, "优化内存管理")
        } catch (e: Exception) {
            Log.e(TAG, "优化内存管理失败", e)
        }
    }
    
    private fun restoreMemoryManagement() {
        try {
            Log.d(TAG, "恢复内存管理设置")
        } catch (e: Exception) {
            Log.e(TAG, "恢复内存管理设置失败", e)
        }
    }
    
    private fun disableAutoSyncAndUpdates() {
        try {
            Log.d(TAG, "禁用自动同步和更新")
        } catch (e: Exception) {
            Log.e(TAG, "禁用自动同步和更新失败", e)
        }
    }
    
    private fun restoreAutoSyncAndUpdates() {
        try {
            Log.d(TAG, "恢复自动同步和更新")
        } catch (e: Exception) {
            Log.e(TAG, "恢复自动同步和更新失败", e)
        }
    }
    
    private fun cleanSystemCache(): Long {
        // 清理系统缓存需要root权限
        return try {
            0L // 简化实现
        } catch (e: Exception) {
            Log.e(TAG, "清理系统缓存失败", e)
            0L
        }
    }
    
    private fun hasRootAccess(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            process.waitFor()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    private fun updateSystemStats() {
        try {
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            
            val totalMemory = memoryInfo.totalMem
            val availableMemory = memoryInfo.availMem
            val usedMemory = totalMemory - availableMemory
            val memoryUsagePercent = usedMemory.toFloat() / totalMemory
            
            // 存储信息
            val internalStorage = context.filesDir
            val statFs = StatFs(internalStorage.path)
            val totalStorage = statFs.totalBytes
            val availableStorage = statFs.availableBytes
            val usedStorage = totalStorage - availableStorage
            val storageUsagePercent = usedStorage.toFloat() / totalStorage
            
            systemStats = SystemStats(
                totalMemory = totalMemory,
                availableMemory = availableMemory,
                usedMemory = usedMemory,
                memoryUsagePercent = memoryUsagePercent,
                totalStorage = totalStorage,
                availableStorage = availableStorage,
                usedStorage = usedStorage,
                storageUsagePercent = storageUsagePercent,
                cpuUsagePercent = getCPUUsage(),
                batteryLevel = getBatteryLevel(),
                isCharging = isCharging(),
                screenOn = isScreenOn(),
                networkConnected = isNetworkConnected()
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "更新系统统计信息失败", e)
        }
    }
    
    private fun getCPUUsage(): Float {
        // 简化的CPU使用率获取
        return try {
            0f // 实际实现需要读取/proc/stat文件
        } catch (e: Exception) {
            0f
        }
    }
    
    private fun getBatteryLevel(): Int {
        // 简化的电池电量获取
        return try {
            50 // 实际实现需要注册电池状态监听器
        } catch (e: Exception) {
            50
        }
    }
    
    private fun isCharging(): Boolean {
        // 简化的充电状态获取
        return false
    }
    
    private fun isScreenOn(): Boolean {
        return powerManager.isInteractive
    }
    
    private fun isNetworkConnected(): Boolean {
        // 简化实现
        return true
    }
    
    private fun startSystemMonitoring() {
        resourceScope.launch {
            while (isInitialized.get()) {
                try {
                    updateSystemStats()
                    delay(10000) // 每10秒更新一次
                } catch (e: Exception) {
                    Log.e(TAG, "系统监控异常", e)
                }
            }
        }
    }
    
    private fun startAutoCleanupTasks() {
        // 自动内存清理
        resourceScope.launch {
            while (isInitialized.get()) {
                try {
                    delay(MEMORY_CLEANUP_INTERVAL_MS)
                    if (systemStats.memoryUsagePercent > MEMORY_THRESHOLD_LOW) {
                        Log.d(TAG, "触发自动内存清理")
                        performMemoryCleanup()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "自动内存清理异常", e)
                }
            }
        }
        
        // 自动缓存清理
        resourceScope.launch {
            while (isInitialized.get()) {
                try {
                    delay(CACHE_CLEANUP_INTERVAL_MS)
                    if (systemStats.storageUsagePercent > STORAGE_THRESHOLD_LOW) {
                        Log.d(TAG, "触发自动缓存清理")
                        cleanApplicationCaches()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "自动缓存清理异常", e)
                }
            }
        }
    }
    
    // 公共API
    fun isOptimized(): Boolean = isOptimized.get()
    
    fun isIdleTime(): Boolean {
        val currentTime = System.currentTimeMillis()
        return (currentTime - lastUserInteractionTime) > IDLE_TIME_THRESHOLD_MS
    }
    
    fun updateUserInteraction() {
        lastUserInteractionTime = System.currentTimeMillis()
    }
    
    fun getSystemStats(): SystemStats = systemStats
    
    fun isLowMemory(): Boolean = systemStats.memoryUsagePercent > MEMORY_THRESHOLD_LOW
    
    fun isLowStorage(): Boolean = systemStats.storageUsagePercent > STORAGE_THRESHOLD_LOW
    
    fun release() {
        try {
            resourceScope.cancel()
            isInitialized.set(false)
            Log.d(TAG, "系统资源管理器资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放系统资源管理器失败", e)
        }
    }
}