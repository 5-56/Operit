package com.ai.assistance.operit

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.util.Log
import com.ai.assistance.operit.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 🚀 Operit AI Application 类
 * 
 * 集成所有核心性能优化组件：
 * - 内存管理器
 * - 启动优化器  
 * - AI模型管理器
 * - 性能监控器
 * - 性能工具类
 */
class OperitApplication : Application(), ComponentCallbacks2 {
    
    companion object {
        private const val TAG = "OperitApplication"
        
        // 全局组件实例
        lateinit var memoryManager: MemoryManager
            private set
        lateinit var startupOptimizer: StartupOptimizer
            private set
        lateinit var aiModelManager: AIModelManager
            private set
        lateinit var performanceMonitor: PerformanceMonitor
            private set
        
        // 应用实例
        lateinit var instance: OperitApplication
            private set
    }
    
    // 应用级协程作用域
    private val applicationScope = CoroutineScope(
        Dispatchers.Main + SupervisorJob()
    )
    
    override fun onCreate() {
        super.onCreate()
        
        // 记录应用启动开始时间
        val appStartTime = System.currentTimeMillis()
        
        try {
            // 设置应用实例
            instance = this
            
            // 初始化核心组件
            initializeCoreComponents()
            
            // 设置启动任务
            setupStartupTasks()
            
            // 启动优化器开始工作
            startupOptimizer.initialize()
            
            // 启用性能监控
            enablePerformanceMonitoring()
            
            // 预加载核心AI模型
            preloadEssentialModels()
            
            val totalStartupTime = System.currentTimeMillis() - appStartTime
            Log.i(TAG, "🎉 Operit AI 应用初始化完成，耗时: ${totalStartupTime}ms")
            
        } catch (e: Exception) {
            Log.e(TAG, "应用初始化失败", e)
        }
    }
    
    /**
     * 🔧 初始化核心组件
     */
    private fun initializeCoreComponents() {
        Log.d(TAG, "🔧 初始化核心性能组件...")
        
        // 1. 内存管理器 (最高优先级)
        memoryManager = MemoryManager.getInstance(this)
        
        // 2. 启动优化器
        startupOptimizer = StartupOptimizer.getInstance(this)
        
        // 3. AI模型管理器
        aiModelManager = AIModelManager.getInstance(this)
        
        // 4. 性能监控器
        performanceMonitor = PerformanceMonitor.getInstance(this)
        
        Log.d(TAG, "✅ 核心组件初始化完成")
    }
    
    /**
     * 🎯 设置启动任务
     */
    private fun setupStartupTasks() {
        Log.d(TAG, "🎯 配置启动任务...")
        
        // 早期阶段任务 (关键，同步执行)
        startupOptimizer.addTask(
            name = "初始化内存管理",
            stage = StartupOptimizer.STAGE_EARLY,
            priority = 100,
            executionType = StartupOptimizer.StartupTask.ExecutionType.MAIN_THREAD
        ) { app ->
            // 内存管理器已在initializeCoreComponents中初始化
            Log.d(TAG, "✅ 内存管理器已准备就绪")
        }
        
        startupOptimizer.addTask(
            name = "初始化AI模型管理",
            stage = StartupOptimizer.STAGE_EARLY,
            priority = 95,
            dependencies = setOf("初始化内存管理")
        ) { app ->
            // AI模型管理器已初始化
            Log.d(TAG, "✅ AI模型管理器已准备就绪")
        }
        
        // 正常阶段任务 (主要功能)
        startupOptimizer.addTask(
            name = "启动性能监控",
            stage = StartupOptimizer.STAGE_NORMAL,
            priority = 90
        ) { app ->
            performanceMonitor.startMonitoring()
            Log.d(TAG, "✅ 性能监控已启动")
        }
        
        startupOptimizer.addTask(
            name = "预热核心类",
            stage = StartupOptimizer.STAGE_NORMAL,
            priority = 80,
            executionType = StartupOptimizer.StartupTask.ExecutionType.CPU_INTENSIVE
        ) { app ->
            preloadCoreClasses()
            Log.d(TAG, "✅ 核心类预热完成")
        }
        
        // 延迟阶段任务 (非关键功能)
        startupOptimizer.addTask(
            name = "优化设备性能",
            stage = StartupOptimizer.STAGE_LAZY,
            priority = 60
        ) { app ->
            optimizeForDevice()
            Log.d(TAG, "✅ 设备性能优化完成")
        }
        
        // 后台阶段任务 (后台服务)
        startupOptimizer.addTask(
            name = "清理启动缓存",
            stage = StartupOptimizer.STAGE_BACKGROUND,
            priority = 40
        ) { app ->
            memoryManager.clearTemporaryCache()
            Log.d(TAG, "✅ 启动缓存清理完成")
        }
        
        // 预加载任务
        startupOptimizer.addPreloadTask {
            preloadSystemServices()
        }
        
        startupOptimizer.addPreloadTask {
            preloadFrequentlyUsedClasses()
        }
        
        Log.d(TAG, "✅ 启动任务配置完成")
    }
    
    /**
     * 📊 启用性能监控
     */
    private fun enablePerformanceMonitoring() {
        Log.d(TAG, "📊 启用性能监控系统...")
        
        applicationScope.launch {
            try {
                // 启动性能监控
                performanceMonitor.startMonitoring()
                
                // 监听性能警告
                performanceMonitor.alerts.collect { alerts ->
                    alerts.forEach { alert ->
                        when (alert.severity) {
                            PerformanceMonitor.AlertSeverity.CRITICAL -> {
                                Log.e(TAG, "🚨 严重性能警告: ${alert.message}")
                                handleCriticalPerformanceAlert(alert)
                            }
                            PerformanceMonitor.AlertSeverity.WARNING -> {
                                Log.w(TAG, "⚠️ 性能警告: ${alert.message}")
                                handlePerformanceWarning(alert)
                            }
                            PerformanceMonitor.AlertSeverity.INFO -> {
                                Log.i(TAG, "ℹ️ 性能提示: ${alert.message}")
                            }
                        }
                    }
                }
                
                Log.d(TAG, "✅ 性能监控系统已启动")
                
            } catch (e: Exception) {
                Log.e(TAG, "性能监控启动失败", e)
            }
        }
    }
    
    /**
     * 🤖 预加载核心AI模型
     */
    private fun preloadEssentialModels() {
        Log.d(TAG, "🤖 预加载核心AI模型...")
        
        applicationScope.launch {
            try {
                // 根据设备性能决定预加载策略
                val deviceLevel = PerformanceUtils.DeviceProfiler.getDevicePerformanceLevel(this@OperitApplication)
                
                when (deviceLevel) {
                    PerformanceUtils.DevicePerformanceLevel.FLAGSHIP,
                    PerformanceUtils.DevicePerformanceLevel.HIGH_END -> {
                        // 高端设备：预加载多个模型
                        aiModelManager.preloadModel("chat_model", AIModelManager.ModelType.CHAT, AIModelManager.LoadPriority.HIGH)
                        aiModelManager.preloadModel("voice_model", AIModelManager.ModelType.VOICE, AIModelManager.LoadPriority.NORMAL)
                        aiModelManager.preloadModel("vision_model", AIModelManager.ModelType.VISION, AIModelManager.LoadPriority.LOW)
                        Log.d(TAG, "📱 高端设备：预加载多个AI模型")
                    }
                    
                    PerformanceUtils.DevicePerformanceLevel.MID_RANGE -> {
                        // 中端设备：预加载核心模型
                        aiModelManager.preloadModel("chat_model", AIModelManager.ModelType.CHAT, AIModelManager.LoadPriority.HIGH)
                        aiModelManager.preloadModel("voice_model", AIModelManager.ModelType.VOICE, AIModelManager.LoadPriority.NORMAL)
                        Log.d(TAG, "📱 中端设备：预加载核心AI模型")
                    }
                    
                    PerformanceUtils.DevicePerformanceLevel.LOW_END -> {
                        // 低端设备：仅预加载必要模型
                        aiModelManager.preloadModel("chat_model", AIModelManager.ModelType.CHAT, AIModelManager.LoadPriority.HIGH)
                        Log.d(TAG, "📱 低端设备：预加载基础AI模型")
                    }
                }
                
                Log.d(TAG, "✅ AI模型预加载完成")
                
            } catch (e: Exception) {
                Log.e(TAG, "AI模型预加载失败", e)
            }
        }
    }
    
    /**
     * 🔥 处理严重性能警告
     */
    private fun handleCriticalPerformanceAlert(alert: PerformanceMonitor.PerformanceAlert) {
        Log.w(TAG, "🔥 处理严重性能警告: ${alert.type}")
        
        when (alert.type) {
            PerformanceMonitor.AlertType.HIGH_MEMORY_USAGE -> {
                // 激进内存清理
                memoryManager.trimMemory(85)
                aiModelManager.trimMemory(85)
                Log.d(TAG, "🧹 执行激进内存清理")
            }
            
            PerformanceMonitor.AlertType.ANR -> {
                // ANR处理
                performanceMonitor.recordEvent("anr_detected", mapOf("duration" to alert.data["delay"]))
                Log.e(TAG, "🚫 ANR事件已记录")
            }
            
            PerformanceMonitor.AlertType.HIGH_CPU_USAGE -> {
                // CPU压力处理
                // 可以考虑降低AI模型复杂度或暂停非必要任务
                Log.w(TAG, "💻 CPU使用率过高，建议优化计算任务")
            }
            
            else -> {
                Log.d(TAG, "处理其他类型的性能警告: ${alert.type}")
            }
        }
    }
    
    /**
     * ⚠️ 处理性能警告
     */
    private fun handlePerformanceWarning(alert: PerformanceMonitor.PerformanceAlert) {
        when (alert.type) {
            PerformanceMonitor.AlertType.LOW_FRAME_RATE -> {
                // 帧率低处理
                Log.w(TAG, "🎮 帧率偏低，建议优化UI渲染")
            }
            
            PerformanceMonitor.AlertType.NETWORK_DISCONNECTED -> {
                // 网络断开处理
                Log.w(TAG, "🌐 网络连接断开")
            }
            
            else -> {
                Log.d(TAG, "处理性能警告: ${alert.type}")
            }
        }
    }
    
    /**
     * 🔄 预热核心类
     */
    private suspend fun preloadCoreClasses() {
        val classesToPreload = listOf(
            "android.widget.TextView",
            "android.widget.ImageView", 
            "androidx.compose.ui.platform.ComposeView",
            "androidx.recyclerview.widget.RecyclerView",
            "kotlinx.coroutines.Dispatchers"
        )
        
        classesToPreload.forEach { className ->
            try {
                Class.forName(className)
            } catch (e: ClassNotFoundException) {
                // 忽略找不到的类
            }
        }
    }
    
    /**
     * 🔧 根据设备优化
     */
    private fun optimizeForDevice() {
        val deviceSpecs = PerformanceUtils.DeviceProfiler.getDeviceSpecs(this)
        Log.d(TAG, "📱 设备信息: ${deviceSpecs.model}, 性能等级: ${deviceSpecs.performanceLevel}")
        
        // 根据设备性能调整配置
        when (deviceSpecs.performanceLevel) {
            PerformanceUtils.DevicePerformanceLevel.LOW_END -> {
                // 低端设备优化
                memoryManager.trimMemory(60) // 更激进的内存管理
                Log.d(TAG, "🔧 低端设备优化：启用保守模式")
            }
            
            PerformanceUtils.DevicePerformanceLevel.MID_RANGE -> {
                // 中端设备优化
                Log.d(TAG, "🔧 中端设备优化：平衡模式")
            }
            
            PerformanceUtils.DevicePerformanceLevel.HIGH_END,
            PerformanceUtils.DevicePerformanceLevel.FLAGSHIP -> {
                // 高端设备优化
                Log.d(TAG, "🔧 高端设备优化：性能模式")
            }
        }
    }
    
    /**
     * 🔄 预加载系统服务
     */
    private suspend fun preloadSystemServices() {
        try {
            getSystemService(ACTIVITY_SERVICE)
            getSystemService(WINDOW_SERVICE)
            getSystemService(CONNECTIVITY_SERVICE)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                getSystemService(POWER_SERVICE)
            }
        } catch (e: Exception) {
            Log.w(TAG, "预加载系统服务失败", e)
        }
    }
    
    /**
     * 📚 预加载常用类
     */
    private suspend fun preloadFrequentlyUsedClasses() {
        try {
            // 预加载Kotlin标准库类
            Class.forName("kotlin.collections.CollectionsKt")
            Class.forName("kotlinx.coroutines.flow.FlowKt")
            
            // 预加载Android常用类
            Class.forName("android.os.Handler")
            Class.forName("android.content.Intent")
            
            Log.d(TAG, "📚 常用类预加载完成")
        } catch (e: Exception) {
            Log.w(TAG, "常用类预加载失败", e)
        }
    }
    
    /**
     * 💾 系统内存回调处理
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        
        Log.d(TAG, "💾 系统内存回收通知，级别: $level")
        
        // 传递内存压力到各个组件
        memoryManager.trimMemory(level)
        aiModelManager.trimMemory(level)
        
        // 记录内存压力事件
        performanceMonitor.recordEvent("memory_trim", mapOf("level" to level))
        
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                Log.w(TAG, "🔴 严重内存压力 - 完全清理")
            }
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                Log.w(TAG, "🟡 中等内存压力 - 适度清理")
            }
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                Log.d(TAG, "🟢 后台内存清理")
            }
        }
    }
    
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        
        // 记录配置变更事件
        performanceMonitor.recordEvent("configuration_changed", 
            mapOf("orientation" to newConfig.orientation))
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        
        Log.w(TAG, "⚠️ 系统内存不足警告")
        
        // 激进清理
        memoryManager.trimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
        aiModelManager.trimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
        
        // 记录低内存事件
        performanceMonitor.recordEvent("low_memory_warning")
    }
    
    /**
     * 📊 获取应用性能报告
     */
    fun getPerformanceReport(): String {
        val report = performanceMonitor.generatePerformanceReport()
        val memoryStats = memoryManager.getMemoryStats()
        val cacheStats = aiModelManager.getCacheStats()
        
        return """
        📊 Operit AI 性能报告
        
        📱 应用性能:
        - 总监控时间: ${(report.endTime - report.startTime) / 1000}秒
        - 平均CPU使用: ${report.averageMetrics.cpu.appCpuUsage.toInt()}%
        - 平均内存使用: ${(report.averageMetrics.memory.memoryUsageRatio * 100).toInt()}%
        - 平均帧率: ${report.averageMetrics.frame.currentFps.toInt()}fps
        
        🧠 内存管理:
        - 对象池命中率: ${(memoryStats["cache_hit_rate"] as? Double)?.let { (it * 100).toInt() } ?: 0}%
        - 已分配对象: ${memoryStats["allocated_objects"]}
        - 已回收对象: ${memoryStats["recycled_objects"]}
        
        🤖 AI模型缓存:
        - GPU缓存: ${cacheStats.gpuCacheSize}个模型
        - RAM缓存: ${cacheStats.ramCacheSize}个模型  
        - 磁盘缓存: ${cacheStats.diskCacheSize}个模型
        - 缓存总大小: ${PerformanceUtils.MemoryAnalyzer.formatMemorySize(cacheStats.totalCacheSize)}
        
        ⚠️ 性能警告: ${report.alerts.size}个
        💡 优化建议: ${report.suggestions.size}条
        """.trimIndent()
    }
    
    /**
     * 🔄 释放资源
     */
    override fun onTerminate() {
        super.onTerminate()
        
        Log.d(TAG, "🔄 应用终止，释放资源...")
        
        try {
            performanceMonitor.shutdown()
            aiModelManager.shutdown()
            memoryManager.shutdown()
            startupOptimizer.shutdown()
            
            Log.d(TAG, "✅ 资源释放完成")
        } catch (e: Exception) {
            Log.e(TAG, "资源释放失败", e)
        }
    }
}