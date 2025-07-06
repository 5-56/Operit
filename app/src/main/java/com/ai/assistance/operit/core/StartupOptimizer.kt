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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

/**
 * 🚀 启动优化器
 * 
 * 功能特性：
 * - 分阶段启动任务调度
 * - 并行任务执行
 * - 依赖关系管理
 * - 进程优先级优化
 * - 预加载机制
 * - 启动时间监控
 */
class StartupOptimizer private constructor(private val application: Application) {
    
    companion object {
        private const val TAG = "StartupOptimizer"
        
        // 启动阶段常量
        const val STAGE_EARLY = 0      // 早期阶段：核心功能初始化
        const val STAGE_NORMAL = 1     // 正常阶段：主要功能初始化  
        const val STAGE_LAZY = 2       // 延迟阶段：非关键功能初始化
        const val STAGE_BACKGROUND = 3 // 后台阶段：后台服务初始化
        
        @Volatile
        private var INSTANCE: StartupOptimizer? = null
        
        fun getInstance(application: Application): StartupOptimizer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StartupOptimizer(application).also { INSTANCE = it }
            }
        }
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
        Dispatchers.Main.immediate + SupervisorJob() + 
        CoroutineName("StartupOptimizer")
    )
    
    // 性能监控
    private var startupStartTime = 0L
    private val stageTimings = mutableMapOf<Int, Long>()
    
    // 核心组件
    private val taskScheduler = TaskScheduler()
    private val dependencyManager = DependencyManager()
    private val performanceTracker = StartupPerformanceTracker()
    private val preloader = Preloader()
    
    // 状态管理
    private val currentStage = AtomicInteger(STAGE_EARLY)
    
    init {
        startupStartTime = System.currentTimeMillis()
        Log.d(TAG, "StartupOptimizer initialized")
    }
    
    /**
     * 📋 任务调度器
     */
    private inner class TaskScheduler {
        private val taskQueues = ConcurrentHashMap<Int, PriorityBlockingQueue<StartupTask>>()
        private val runningTasks = ConcurrentHashMap<String, Job>()
        
        init {
            // 初始化任务队列
            for (stage in STAGE_EARLY..STAGE_BACKGROUND) {
                taskQueues[stage] = PriorityBlockingQueue<StartupTask> { t1, t2 ->
                    t2.priority.compareTo(t1.priority) // 高优先级先执行
                }
            }
        }
        
        fun addTask(task: StartupTask) {
            val queue = taskQueues[task.stage] ?: return
            queue.offer(task)
            Log.d(TAG, "Task added: ${task.name} (stage=${task.stage}, priority=${task.priority})")
        }
        
        suspend fun executeStage(stage: Int) {
            val queue = taskQueues[stage] ?: return
            val tasks = mutableListOf<StartupTask>()
            
            // 收集当前阶段的所有任务
            while (queue.isNotEmpty()) {
                tasks.add(queue.poll())
            }
            
            if (tasks.isEmpty()) return
            
            Log.d(TAG, "Executing stage $stage with ${tasks.size} tasks")
            
            // 根据依赖关系和并发策略执行任务
            val batches = dependencyManager.resolveDependencies(tasks)
            
            for (batch in batches) {
                val jobs = batch.map { task ->
                    async(getDispatcherForTask(task)) {
                        executeTask(task)
                    }
                }
                
                // 等待当前批次所有任务完成
                jobs.awaitAll()
            }
            
            Log.d(TAG, "Stage $stage completed")
        }
        
        private suspend fun executeTask(task: StartupTask) {
            val startTime = System.currentTimeMillis()
            
            try {
                performanceTracker.trackTaskStart(task.name)
                
                val executeTime = measureTimeMillis {
                    task.execute(application)
                }
                
                performanceTracker.trackTaskComplete(task.name, executeTime)
                Log.d(TAG, "Task '${task.name}' completed in ${executeTime}ms")
                
            } catch (e: Exception) {
                performanceTracker.trackTaskError(task.name, e)
                Log.e(TAG, "Task '${task.name}' failed", e)
                
                // 根据任务配置决定是否继续执行
                if (!task.allowFailure) {
                    throw e
                }
            }
        }
        
        private fun getDispatcherForTask(task: StartupTask): CoroutineDispatcher {
            return when (task.executionType) {
                StartupTask.ExecutionType.MAIN_THREAD -> Dispatchers.Main.immediate
                StartupTask.ExecutionType.IO_THREAD -> Dispatchers.IO
                StartupTask.ExecutionType.CPU_INTENSIVE -> Dispatchers.Default
                StartupTask.ExecutionType.UNCONFINED -> Dispatchers.Unconfined
            }
        }
    }
    
    /**
     * 🔗 依赖关系管理器
     */
    private inner class DependencyManager {
        private val dependencies = ConcurrentHashMap<String, Set<String>>()
        
        fun addDependency(taskName: String, dependsOn: Set<String>) {
            dependencies[taskName] = dependsOn
        }
        
        fun resolveDependencies(tasks: List<StartupTask>): List<List<StartupTask>> {
            val taskMap = tasks.associateBy { it.name }
            val batches = mutableListOf<List<StartupTask>>()
            val completed = mutableSetOf<String>()
            val remaining = tasks.toMutableList()
            
            while (remaining.isNotEmpty()) {
                val readyTasks = remaining.filter { task ->
                    val deps = dependencies[task.name] ?: emptySet()
                    deps.all { it in completed }
                }
                
                if (readyTasks.isEmpty()) {
                    // 检测循环依赖
                    Log.w(TAG, "Potential circular dependency detected, executing remaining tasks")
                    batches.add(remaining.toList())
                    break
                }
                
                batches.add(readyTasks)
                completed.addAll(readyTasks.map { it.name })
                remaining.removeAll(readyTasks)
            }
            
            return batches
        }
    }
    
    /**
     * 📊 启动性能跟踪器
     */
    private inner class StartupPerformanceTracker {
        private val taskMetrics = ConcurrentHashMap<String, TaskMetric>()
        private val stageStartTimes = ConcurrentHashMap<Int, Long>()
        private val applicationStartTime = System.currentTimeMillis()
        
        fun trackStageStart(stage: Int) {
            stageStartTimes[stage] = System.currentTimeMillis()
            Log.d(TAG, "Stage $stage started")
        }
        
        fun trackStageComplete(stage: Int) {
            val startTime = stageStartTimes[stage] ?: return
            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, "Stage $stage completed in ${duration}ms")
        }
        
        fun trackTaskStart(taskName: String) {
            taskMetrics[taskName] = TaskMetric(
                name = taskName,
                startTime = System.currentTimeMillis()
            )
        }
        
        fun trackTaskComplete(taskName: String, duration: Long) {
            taskMetrics[taskName]?.let { metric ->
                metric.endTime = System.currentTimeMillis()
                metric.duration = duration
                metric.status = TaskStatus.COMPLETED
            }
        }
        
        fun trackTaskError(taskName: String, error: Exception) {
            taskMetrics[taskName]?.let { metric ->
                metric.endTime = System.currentTimeMillis()
                metric.duration = metric.endTime - metric.startTime
                metric.status = TaskStatus.FAILED
                metric.error = error.message
            }
        }
        
        fun getStartupReport(): StartupReport {
            val totalDuration = System.currentTimeMillis() - applicationStartTime
            val completedTasks = taskMetrics.values.filter { it.status == TaskStatus.COMPLETED }
            val failedTasks = taskMetrics.values.filter { it.status == TaskStatus.FAILED }
            
            return StartupReport(
                totalDuration = totalDuration,
                tasksCount = taskMetrics.size,
                completedTasksCount = completedTasks.size,
                failedTasksCount = failedTasks.size,
                averageTaskDuration = if (completedTasks.isNotEmpty()) {
                    completedTasks.map { it.duration }.average()
                } else 0.0,
                longestTask = completedTasks.maxByOrNull { it.duration },
                taskMetrics = taskMetrics.values.toList()
            )
        }
    }
    
    /**
     * 📚 预加载器
     */
    private inner class Preloader {
        private val preloadTasks = mutableListOf<suspend () -> Unit>()
        
        fun addPreloadTask(task: suspend () -> Unit) {
            preloadTasks.add(task)
        }
        
        suspend fun executePreload() {
            Log.d(TAG, "Starting preload with ${preloadTasks.size} tasks")
            
            val preloadJobs = preloadTasks.map { task ->
                async(Dispatchers.IO) {
                    try {
                        task()
                    } catch (e: Exception) {
                        Log.e(TAG, "Preload task failed", e)
                    }
                }
            }
            
            preloadJobs.awaitAll()
            Log.d(TAG, "Preload completed")
        }
    }
    
    // ==================== 数据类 ====================
    
    /**
     * 启动任务定义
     */
    data class StartupTask(
        val name: String,
        val stage: Int,
        val priority: Int = 50, // 优先级 0-100，越大越优先
        val dependencies: Set<String> = emptySet(),
        val allowFailure: Boolean = true,
        val executionType: ExecutionType = ExecutionType.IO_THREAD,
        val execute: suspend (Application) -> Unit
    ) {
        enum class ExecutionType {
            MAIN_THREAD,    // 主线程执行
            IO_THREAD,      // IO线程执行
            CPU_INTENSIVE,  // CPU密集型任务
            UNCONFINED      // 不限制线程
        }
    }
    
    /**
     * 简单启动任务实现
     */
    class SimpleStartupTask(
        name: String,
        stage: Int = STAGE_NORMAL,
        priority: Int = 50,
        private val task: (Application) -> Unit
    ) : StartupTask(
        name = name,
        stage = stage,
        priority = priority,
        execute = { app -> task(app) }
    )
    
    /**
     * 任务度量数据
     */
    data class TaskMetric(
        val name: String,
        val startTime: Long,
        var endTime: Long = 0,
        var duration: Long = 0,
        var status: TaskStatus = TaskStatus.RUNNING,
        var error: String? = null
    )
    
    enum class TaskStatus {
        RUNNING, COMPLETED, FAILED
    }
    
    /**
     * 启动报告
     */
    data class StartupReport(
        val totalDuration: Long,
        val tasksCount: Int,
        val completedTasksCount: Int,
        val failedTasksCount: Int,
        val averageTaskDuration: Double,
        val longestTask: TaskMetric?,
        val taskMetrics: List<TaskMetric>
    )
    
    // ==================== 公共API ====================
    
    /**
     * 🎯 添加启动任务
     */
    fun addTask(task: StartupTask) {
        if (task.dependencies.isNotEmpty()) {
            dependencyManager.addDependency(task.name, task.dependencies)
        }
        taskScheduler.addTask(task)
    }
    
    /**
     * 🎯 添加简单任务
     */
    fun addTask(
        name: String,
        stage: Int = STAGE_NORMAL,
        priority: Int = 50,
        dependencies: Set<String> = emptySet(),
        allowFailure: Boolean = true,
        executionType: StartupTask.ExecutionType = StartupTask.ExecutionType.IO_THREAD,
        task: suspend (Application) -> Unit
    ) {
        addTask(
            StartupTask(
                name = name,
                stage = stage,
                priority = priority,
                dependencies = dependencies,
                allowFailure = allowFailure,
                executionType = executionType,
                execute = task
            )
        )
    }
    
    /**
     * 🎯 添加预加载任务
     */
    fun addPreloadTask(task: suspend () -> Unit) {
        preloader.addPreloadTask(task)
    }
    
    /**
     * 🚀 开始启动优化
     */
    fun initialize() {
        if (!isInitialized.compareAndSet(false, true)) {
            Log.w(TAG, "StartupOptimizer already initialized")
            return
        }
        
        startupScope.launch {
            try {
                Log.d(TAG, "Starting startup optimization")
                
                // 优化进程优先级
                optimizeProcessPriority()
                
                // 执行预加载
                preloader.executePreload()
                
                // 分阶段执行启动任务
                for (stage in STAGE_EARLY..STAGE_BACKGROUND) {
                    currentStage.set(stage)
                    performanceTracker.trackStageStart(stage)
                    
                    when (stage) {
                        STAGE_EARLY -> {
                            // 早期阶段：同步执行关键任务
                            taskScheduler.executeStage(stage)
                        }
                        STAGE_NORMAL -> {
                            // 正常阶段：并行执行主要任务
                            taskScheduler.executeStage(stage)
                        }
                        STAGE_LAZY -> {
                            // 延迟阶段：延迟到空闲时执行
                            delay(100) // 短暂延迟
                            taskScheduler.executeStage(stage)
                        }
                        STAGE_BACKGROUND -> {
                            // 后台阶段：在后台低优先级执行
                            withContext(Dispatchers.IO) {
                                taskScheduler.executeStage(stage)
                            }
                        }
                    }
                    
                    performanceTracker.trackStageComplete(stage)
                }
                
                val report = performanceTracker.getStartupReport()
                Log.d(TAG, "Startup optimization completed in ${report.totalDuration}ms")
                Log.d(TAG, "Executed ${report.completedTasksCount}/${report.tasksCount} tasks successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Startup optimization failed", e)
            }
        }
    }
    
    /**
     * ⚡ 优化进程优先级
     */
    private fun optimizeProcessPriority() {
        try {
            // 设置进程优先级为高优先级（在启动期间）
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
            Log.d(TAG, "Process priority optimized for startup")
            
            // 启动完成后恢复正常优先级
            startupScope.launch {
                delay(5000) // 5秒后恢复
                Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT)
                Log.d(TAG, "Process priority restored to default")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to optimize process priority", e)
        }
    }
    
    /**
     * 📊 获取启动报告
     */
    fun getStartupReport(): StartupReport {
        return performanceTracker.getStartupReport()
    }
    
    /**
     * 📊 获取当前阶段
     */
    fun getCurrentStage(): Int = currentStage.get()
    
    /**
     * ✅ 检查是否初始化完成
     */
    fun isInitialized(): Boolean = isInitialized.get()
    
    /**
     * 🔄 释放资源
     */
    fun shutdown() {
        startupScope.cancel()
        Log.d(TAG, "StartupOptimizer shutdown")
    }
}

// ==================== 扩展函数 ====================

/**
 * 为Application添加启动优化扩展
 */
fun Application.optimizeStartup(configure: StartupOptimizer.() -> Unit) {
    val optimizer = StartupOptimizer.getInstance(this)
    optimizer.configure()
    optimizer.initialize()
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
        return StartupOptimizer.getInstance(context as Application).apply {
            if (context is Application) {
                initialize()
            }
        }
    }
    
    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}