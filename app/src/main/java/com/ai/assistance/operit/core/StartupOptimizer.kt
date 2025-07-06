package com.ai.assistance.operit.core

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.startup.Initializer
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 启动优化器
 * 负责优化应用启动性能，包括延迟初始化、预加载和启动任务调度
 */
class StartupOptimizer private constructor() {
    
    companion object {
        @Volatile
        private var INSTANCE: StartupOptimizer? = null
        
        fun getInstance(): StartupOptimizer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StartupOptimizer().also { INSTANCE = it }
            }
        }
        
        // 启动阶段定义
        const val STAGE_EARLY = 0
        const val STAGE_NORMAL = 1
        const val STAGE_LAZY = 2
        const val STAGE_BACKGROUND = 3
    }
    
    // 启动任务管理
    private val startupTasks = mutableMapOf<Int, MutableList<StartupTask>>()
    private val completedTasks = mutableSetOf<String>()
    private val isInitialized = AtomicBoolean(false)
    
    // 线程池管理
    private val backgroundExecutor = Executors.newFixedThreadPool(
        maxOf(1, Runtime.getRuntime().availableProcessors() / 2)
    )
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 协程作用域
    private val startupScope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() + CoroutineName("StartupOptimizer")
    )
    
    // 性能监控
    private var startupStartTime = 0L
    private val stageTimings = mutableMapOf<Int, Long>()
    
    init {
        startupStartTime = System.currentTimeMillis()
    }
    
    /**
     * 初始化启动优化器
     */
    fun initialize(application: Application) {
        if (!isInitialized.compareAndSet(false, true)) {
            return
        }
        
        // 设置进程优先级
        optimizeProcessPriority()
        
        // 预加载核心组件
        preloadCoreComponents(application)
        
        // 注册生命周期回调
        registerActivityLifecycleCallbacks(application)
        
        // 执行启动任务
        executeStartupTasks(application)
    }
    
    /**
     * 添加启动任务
     */
    fun addTask(stage: Int, task: StartupTask) {
        startupTasks.getOrPut(stage) { mutableListOf() }.add(task)
    }
    
    /**
     * 批量添加启动任务
     */
    fun addTasks(vararg tasks: Pair<Int, StartupTask>) {
        tasks.forEach { (stage, task) ->
            addTask(stage, task)
        }
    }
    
    /**
     * 执行启动任务
     */
    private fun executeStartupTasks(application: Application) {
        startupScope.launch {
            // 早期阶段任务（阻塞主线程）
            executeStage(STAGE_EARLY, application, runOnMainThread = true)
            
            // 正常阶段任务（主线程）
            executeStage(STAGE_NORMAL, application, runOnMainThread = true)
            
            // 延迟500ms后执行懒加载任务
            delay(500)
            executeStage(STAGE_LAZY, application, runOnMainThread = false)
            
            // 后台任务
            executeStage(STAGE_BACKGROUND, application, runOnMainThread = false)
        }
    }
    
    /**
     * 执行特定阶段的任务
     */
    private suspend fun executeStage(
        stage: Int, 
        application: Application, 
        runOnMainThread: Boolean
    ) {
        val stageStartTime = System.currentTimeMillis()
        val tasks = startupTasks[stage] ?: return
        
        if (runOnMainThread) {
            // 主线程执行
            withContext(Dispatchers.Main) {
                tasks.forEach { task ->
                    executeTask(task, application)
                }
            }
        } else {
            // 并行执行
            tasks.map { task ->
                async {
                    executeTask(task, application)
                }
            }.awaitAll()
        }
        
        stageTimings[stage] = System.currentTimeMillis() - stageStartTime
    }
    
    /**
     * 执行单个任务
     */
    private suspend fun executeTask(task: StartupTask, application: Application) {
        try {
            val taskStartTime = System.currentTimeMillis()
            
            // 检查依赖
            if (!areDependenciesSatisfied(task.dependencies)) {
                // 等待依赖完成（最多等待5秒）
                var waitTime = 0L
                while (!areDependenciesSatisfied(task.dependencies) && waitTime < 5000) {
                    delay(100)
                    waitTime += 100
                }
                
                if (!areDependenciesSatisfied(task.dependencies)) {
                    throw IllegalStateException("Task ${task.name} dependencies not satisfied")
                }
            }
            
            // 执行任务
            task.execute(application)
            completedTasks.add(task.name)
            
            val executionTime = System.currentTimeMillis() - taskStartTime
            if (executionTime > 1000) {
                // 记录耗时较长的任务
                android.util.Log.w("StartupOptimizer", 
                    "Task ${task.name} took ${executionTime}ms to complete")
            }
            
        } catch (e: Exception) {
            android.util.Log.e("StartupOptimizer", 
                "Failed to execute task ${task.name}", e)
        }
    }
    
    /**
     * 检查依赖是否满足
     */
    private fun areDependenciesSatisfied(dependencies: List<String>): Boolean {
        return dependencies.all { it in completedTasks }
    }
    
    /**
     * 优化进程优先级
     */
    private fun optimizeProcessPriority() {
        try {
            // 临时提升进程优先级以加快启动
            Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE)
            
            // 2秒后恢复正常优先级
            mainHandler.postDelayed({
                Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT)
            }, 2000)
        } catch (e: Exception) {
            android.util.Log.w("StartupOptimizer", "Failed to optimize process priority", e)
        }
    }
    
    /**
     * 预加载核心组件
     */
    private fun preloadCoreComponents(application: Application) {
        startupScope.launch {
            try {
                // 预加载常用类
                preloadClasses()
                
                // 预热ClassLoader
                warmupClassLoader()
                
                // 预加载系统服务
                preloadSystemServices(application)
                
            } catch (e: Exception) {
                android.util.Log.e("StartupOptimizer", "Failed to preload core components", e)
            }
        }
    }
    
    /**
     * 预加载常用类
     */
    private suspend fun preloadClasses() {
        withContext(Dispatchers.Default) {
            val classesToPreload = listOf(
                "android.widget.TextView",
                "android.widget.ImageView",
                "android.widget.LinearLayout",
                "android.widget.RelativeLayout",
                "androidx.recyclerview.widget.RecyclerView",
                "androidx.compose.ui.platform.ComposeView"
            )
            
            classesToPreload.forEach { className ->
                try {
                    Class.forName(className)
                } catch (e: ClassNotFoundException) {
                    // 忽略找不到的类
                }
            }
        }
    }
    
    /**
     * 预热ClassLoader
     */
    private suspend fun warmupClassLoader() {
        withContext(Dispatchers.Default) {
            // 触发ClassLoader预热
            Thread.currentThread().contextClassLoader.loadClass("java.lang.String")
        }
    }
    
    /**
     * 预加载系统服务
     */
    private suspend fun preloadSystemServices(application: Application) {
        withContext(Dispatchers.Default) {
            try {
                // 预先获取常用系统服务
                application.getSystemService(Context.ACTIVITY_SERVICE)
                application.getSystemService(Context.WINDOW_SERVICE)
                application.getSystemService(Context.CONNECTIVITY_SERVICE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    application.getSystemService(Context.POWER_SERVICE)
                }
            } catch (e: Exception) {
                android.util.Log.w("StartupOptimizer", "Failed to preload system services", e)
            }
        }
    }
    
    /**
     * 注册Activity生命周期回调
     */
    private fun registerActivityLifecycleCallbacks(application: Application) {
        application.registerActivityLifecycleCallbacks(
            StartupActivityLifecycleCallbacks()
        )
    }
    
    /**
     * 获取启动性能报告
     */
    fun getStartupPerformanceReport(): StartupPerformanceReport {
        val totalStartupTime = System.currentTimeMillis() - startupStartTime
        
        return StartupPerformanceReport(
            totalStartupTime = totalStartupTime,
            stageTimings = stageTimings.toMap(),
            completedTasksCount = completedTasks.size,
            totalTasksCount = startupTasks.values.sumOf { it.size },
            isOptimized = true
        )
    }
    
    /**
     * 延迟执行任务
     */
    fun executeDelayed(delayMs: Long, task: () -> Unit) {
        startupScope.launch {
            delay(delayMs)
            task()
        }
    }
    
    /**
     * 在主线程延迟执行
     */
    fun executeDelayedOnMain(delayMs: Long, task: () -> Unit) {
        mainHandler.postDelayed(task, delayMs)
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        startupScope.cancel()
        backgroundExecutor.shutdown()
        startupTasks.clear()
        completedTasks.clear()
    }
    
    /**
     * 启动任务接口
     */
    interface StartupTask {
        val name: String
        val dependencies: List<String>
        suspend fun execute(application: Application)
    }
    
    /**
     * 简单启动任务实现
     */
    class SimpleStartupTask(
        override val name: String,
        override val dependencies: List<String> = emptyList(),
        private val block: suspend (Application) -> Unit
    ) : StartupTask {
        override suspend fun execute(application: Application) {
            block(application)
        }
    }
    
    /**
     * 启动性能报告
     */
    data class StartupPerformanceReport(
        val totalStartupTime: Long,
        val stageTimings: Map<Int, Long>,
        val completedTasksCount: Int,
        val totalTasksCount: Int,
        val isOptimized: Boolean
    )
}

/**
 * 启动Activity生命周期回调
 */
private class StartupActivityLifecycleCallbacks : 
    Application.ActivityLifecycleCallbacks by android.app.Application.ActivityLifecycleCallbacks() {
    
    private var firstActivityCreated = false
    
    override fun onActivityCreated(
        activity: android.app.Activity,
        savedInstanceState: android.os.Bundle?
    ) {
        if (!firstActivityCreated) {
            firstActivityCreated = true
            // 第一个Activity创建时的优化
            optimizeFirstActivity(activity)
        }
    }
    
    private fun optimizeFirstActivity(activity: android.app.Activity) {
        // 预先设置窗口属性以减少渲染延迟
        activity.window?.let { window ->
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
            }
        }
    }
}

/**
 * Startup Initializer 实现
 */
class StartupOptimizerInitializer : Initializer<StartupOptimizer> {
    override fun create(context: Context): StartupOptimizer {
        return StartupOptimizer.getInstance().apply {
            if (context is Application) {
                initialize(context)
            }
        }
    }
    
    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}