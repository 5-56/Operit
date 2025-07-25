package com.ai.assistance.operit.core.agent

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

/**
 * Agent类型定义
 */
enum class AgentType {
    GENERAL,        // 通用Agent
    SPECIALIST,     // 专业Agent
    COORDINATOR,    // 协调Agent
    VALIDATOR       // 验证Agent
}

/**
 * Agent能力定义
 */
data class AgentCapability(
    val name: String,
    val description: String,
    val skillLevel: Float, // 0.0-1.0
    val domains: List<String>
)

/**
 * Agent实例
 */
data class AgentInstance(
    val id: String,
    val name: String,
    val type: AgentType,
    val capabilities: List<AgentCapability>,
    var status: AgentStatus = AgentStatus.IDLE,
    var currentTask: String? = null,
    var workload: Int = 0,
    var successRate: Float = 1.0f,
    var lastActiveTime: Long = System.currentTimeMillis()
)

/**
 * Agent状态
 */
enum class AgentStatus {
    IDLE,           // 空闲
    BUSY,           // 忙碌
    WAITING,        // 等待
    ERROR,          // 错误
    OFFLINE         // 离线
}

/**
 * 协作任务
 */
data class CollaborativeTask(
    val id: String,
    var title: String,
    var description: String,
    var requiredCapabilities: List<String>,
    var priority: TaskPriority = TaskPriority.MEDIUM,
    var estimatedTime: Long = 0L,
    var dependencies: List<String> = emptyList(),
    var assignedAgents: MutableList<String> = mutableListOf(),
    var status: TaskStatus = TaskStatus.PENDING,
    var createdTime: Long = System.currentTimeMillis()
)

/**
 * 任务优先级
 */
enum class TaskPriority {
    LOW, MEDIUM, HIGH, URGENT
}

/**
 * 任务状态
 */
enum class TaskStatus {
    PENDING,        // 待处理
    ASSIGNED,       // 已分配
    IN_PROGRESS,    // 进行中
    COMPLETED,      // 已完成
    FAILED,         // 失败
    CANCELLED       // 已取消
}

/**
 * 协作结果
 */
data class CollaborationResult(
    val taskId: String,
    val success: Boolean,
    val results: Map<String, Any>,
    val participatingAgents: List<String>,
    val executionTime: Long,
    val message: String
)

/**
 * 多Agent协作系统
 */
class MultiAgentSystem(private val context: Context) : CoroutineScope {
    
    companion object {
        private const val TAG = "MultiAgentSystem"
        private const val MAX_CONCURRENT_TASKS = 10
        private const val AGENT_TIMEOUT_MS = 300000L // 5分钟
        
        @Volatile
        private var INSTANCE: MultiAgentSystem? = null
        
        fun getInstance(context: Context): MultiAgentSystem {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MultiAgentSystem(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Default
    
    // Agent管理
    private val agents = ConcurrentHashMap<String, AgentInstance>()
    private val agentCores = ConcurrentHashMap<String, AgentCore>()
    
    // 任务管理
    private val tasks = ConcurrentHashMap<String, CollaborativeTask>()
    private val taskQueue = Channel<CollaborativeTask>(Channel.UNLIMITED)
    private val taskIdCounter = AtomicInteger(0)
    
    // 协作状态
    private val _systemStatus = MutableStateFlow(SystemStatus.IDLE)
    val systemStatus: StateFlow<SystemStatus> = _systemStatus.asStateFlow()
    
    private val _activeCollaborations = MutableStateFlow<List<CollaborativeTask>>(emptyList())
    val activeCollaborations: StateFlow<List<CollaborativeTask>> = _activeCollaborations.asStateFlow()
    
    // 性能监控
    private val performanceMonitor = AgentPerformanceMonitor.getInstance(context)
    
    init {
        initializeDefaultAgents()
        startTaskProcessor()
        startAgentMonitor()
    }
    
    /**
     * 系统状态
     */
    enum class SystemStatus {
        IDLE, ACTIVE, OVERLOADED, ERROR
    }
    
    /**
     * 初始化默认Agent
     */
    private fun initializeDefaultAgents() {
        // 通用Agent
        registerAgent(
            AgentInstance(
                id = "general_agent_1",
                name = "通用助手Alpha",
                type = AgentType.GENERAL,
                capabilities = listOf(
                    AgentCapability("文件操作", "处理文件相关任务", 0.9f, listOf("file_management")),
                    AgentCapability("系统监控", "监控系统状态", 0.8f, listOf("system_monitoring")),
                    AgentCapability("数据处理", "处理和分析数据", 0.7f, listOf("data_processing"))
                )
            )
        )
        
        // 专业Agent - 文件管理专家
        registerAgent(
            AgentInstance(
                id = "file_specialist",
                name = "文件管理专家",
                type = AgentType.SPECIALIST,
                capabilities = listOf(
                    AgentCapability("文件整理", "专业的文件整理服务", 1.0f, listOf("file_management")),
                    AgentCapability("重复文件检测", "检测和处理重复文件", 0.95f, listOf("file_management")),
                    AgentCapability("文件格式转换", "各种文件格式转换", 0.9f, listOf("file_management"))
                )
            )
        )
        
        // 专业Agent - 系统监控专家
        registerAgent(
            AgentInstance(
                id = "system_specialist",
                name = "系统监控专家",
                type = AgentType.SPECIALIST,
                capabilities = listOf(
                    AgentCapability("性能监控", "专业的系统性能监控", 1.0f, listOf("system_monitoring")),
                    AgentCapability("资源管理", "系统资源优化管理", 0.95f, listOf("system_monitoring")),
                    AgentCapability("故障诊断", "系统问题诊断和修复", 0.9f, listOf("system_monitoring"))
                )
            )
        )
        
        // 协调Agent
        registerAgent(
            AgentInstance(
                id = "coordinator",
                name = "任务协调者",
                type = AgentType.COORDINATOR,
                capabilities = listOf(
                    AgentCapability("任务分解", "将复杂任务分解为子任务", 0.95f, listOf("coordination")),
                    AgentCapability("资源调度", "优化资源分配", 0.9f, listOf("coordination")),
                    AgentCapability("进度跟踪", "跟踪任务执行进度", 0.85f, listOf("coordination"))
                )
            )
        )
        
        Log.d(TAG, "初始化了 ${agents.size} 个默认Agent")
    }
    
    /**
     * 注册Agent
     */
    fun registerAgent(agent: AgentInstance) {
        agents[agent.id] = agent
        agentCores[agent.id] = AgentCore.getInstance(context)
        Log.d(TAG, "注册Agent: ${agent.name} (${agent.type})")
    }
    
    /**
     * 注销Agent
     */
    fun unregisterAgent(agentId: String) {
        agents.remove(agentId)
        agentCores.remove(agentId)
        Log.d(TAG, "注销Agent: $agentId")
    }
    
    /**
     * 获取所有Agent
     */
    fun getAllAgents(): List<AgentInstance> {
        return agents.values.toList()
    }
    
    /**
     * 获取可用的Agent
     */
    fun getAvailableAgents(): List<AgentInstance> {
        return agents.values.filter { it.status == AgentStatus.IDLE }
    }
    
    /**
     * 提交协作任务
     */
    suspend fun submitCollaborativeTask(
        title: String,
        description: String,
        requiredCapabilities: List<String>,
        priority: TaskPriority = TaskPriority.MEDIUM
    ): String {
        val taskId = "task_${taskIdCounter.incrementAndGet()}"
        val task = CollaborativeTask(
            id = taskId,
            title = title,
            description = description,
            requiredCapabilities = requiredCapabilities,
            priority = priority
        )
        
        tasks[taskId] = task
        taskQueue.send(task)
        
        updateActiveCollaborations()
        Log.d(TAG, "提交协作任务: $title")
        
        return taskId
    }
    
    /**
     * 执行协作任务
     */
    suspend fun executeCollaborativeTask(taskId: String): Flow<CollaborationResult> = flow {
        val task = tasks[taskId] ?: run {
            emit(CollaborationResult(taskId, false, emptyMap(), emptyList(), 0L, "任务不存在"))
            return@flow
        }
        
        try {
            // 1. 分析任务并分配Agent
            emit(CollaborationResult(taskId, true, emptyMap(), emptyList(), 0L, "开始分析任务..."))
            
            val assignedAgents = assignAgentsToTask(task)
            if (assignedAgents.isEmpty()) {
                emit(CollaborationResult(taskId, false, emptyMap(), emptyList(), 0L, "没有可用的Agent"))
                return@flow
            }
            
            task.assignedAgents.clear()
            task.assignedAgents.addAll(assignedAgents.map { it.id })
            task.status = TaskStatus.ASSIGNED
            
            emit(CollaborationResult(
                taskId, true, emptyMap(), assignedAgents.map { it.id }, 0L,
                "已分配给 ${assignedAgents.size} 个Agent: ${assignedAgents.joinToString { it.name }}"
            ))
            
            // 2. 协调执行
            task.status = TaskStatus.IN_PROGRESS
            val startTime = System.currentTimeMillis()
            
            val results = mutableMapOf<String, Any>()
            val jobs = mutableListOf<Deferred<Pair<String, Any?>>>()
            
            // 并行执行
            assignedAgents.forEach { agent ->
                val job = async {
                    executeAgentTask(agent, task)
                }
                jobs.add(job)
            }
            
            // 等待所有Agent完成
            val agentResults = jobs.awaitAll()
            agentResults.forEach { (agentId, result) ->
                if (result != null) {
                    results[agentId] = result
                }
            }
            
            val executionTime = System.currentTimeMillis() - startTime
            val success = results.isNotEmpty()
            
            // 3. 更新任务状态
            task.status = if (success) TaskStatus.COMPLETED else TaskStatus.FAILED
            
            emit(CollaborationResult(
                taskId, success, results, assignedAgents.map { it.id },
                executionTime, if (success) "协作任务完成" else "协作任务失败"
            ))
            
        } catch (e: Exception) {
            Log.e(TAG, "执行协作任务失败", e)
            task.status = TaskStatus.FAILED
            emit(CollaborationResult(taskId, false, emptyMap(), emptyList(), 0L, "执行失败: ${e.message}"))
        } finally {
            updateActiveCollaborations()
        }
    }
    
    /**
     * 智能任务分解
     */
    suspend fun decomposeComplexTask(
        taskDescription: String,
        maxSubTasks: Int = 5
    ): List<CollaborativeTask> {
        val coordinator = agents.values.find { it.type == AgentType.COORDINATOR }
            ?: return emptyList()
        
        return try {
            // 使用协调Agent分解任务
            val agentCore = agentCores[coordinator.id] ?: return emptyList()
            
            val decompositionPrompt = """
            请将以下复杂任务分解为不超过${maxSubTasks}个子任务：
            
            任务描述：$taskDescription
            
            要求：
            1. 每个子任务应该相对独立
            2. 明确每个子任务的所需能力
            3. 考虑任务间的依赖关系
            
            请以JSON格式返回子任务列表。
            """.trimIndent()
            
            val subTasks = mutableListOf<CollaborativeTask>()
            
            // 这里简化处理，实际应该调用AI进行任务分解
            when {
                taskDescription.contains("文件") && taskDescription.contains("系统") -> {
                    subTasks.add(CollaborativeTask(
                        id = "subtask_${taskIdCounter.incrementAndGet()}",
                        title = "文件操作部分",
                        description = "处理任务中的文件相关操作",
                        requiredCapabilities = listOf("file_management")
                    ))
                    subTasks.add(CollaborativeTask(
                        id = "subtask_${taskIdCounter.incrementAndGet()}",
                        title = "系统监控部分", 
                        description = "处理任务中的系统监控操作",
                        requiredCapabilities = listOf("system_monitoring")
                    ))
                }
                else -> {
                    // 默认不分解
                    subTasks.add(CollaborativeTask(
                        id = "subtask_${taskIdCounter.incrementAndGet()}",
                        title = "完整任务",
                        description = taskDescription,
                        requiredCapabilities = listOf("general")
                    ))
                }
            }
            
            Log.d(TAG, "任务分解完成，生成 ${subTasks.size} 个子任务")
            subTasks
            
        } catch (e: Exception) {
            Log.e(TAG, "任务分解失败", e)
            emptyList()
        }
    }
    
    /**
     * 获取系统负载状态
     */
    fun getSystemLoad(): Map<String, Any> {
        val totalAgents = agents.size
        val busyAgents = agents.values.count { it.status == AgentStatus.BUSY }
        val activeTasks = tasks.values.count { it.status == TaskStatus.IN_PROGRESS }
        val pendingTasks = tasks.values.count { it.status == TaskStatus.PENDING }
        
        return mapOf(
            "totalAgents" to totalAgents,
            "busyAgents" to busyAgents,
            "idleAgents" to (totalAgents - busyAgents),
            "activeTasks" to activeTasks,
            "pendingTasks" to pendingTasks,
            "systemLoad" to if (totalAgents > 0) (busyAgents.toFloat() / totalAgents) else 0f
        )
    }
    
    /**
     * 获取Agent性能报告
     */
    fun getAgentPerformanceReport(): Map<String, Any> {
        val agentStats = agents.values.map { agent ->
            mapOf(
                "id" to agent.id,
                "name" to agent.name,
                "type" to agent.type.toString(),
                "status" to agent.status.toString(),
                "workload" to agent.workload,
                "successRate" to agent.successRate,
                "capabilities" to agent.capabilities.size
            )
        }
        
        return mapOf(
            "agents" to agentStats,
            "totalAgents" to agents.size,
            "averageSuccessRate" to agents.values.map { it.successRate }.average(),
            "systemUptime" to System.currentTimeMillis()
        )
    }
    
    // === 私有方法 ===
    
    /**
     * 为任务分配Agent
     */
    private fun assignAgentsToTask(task: CollaborativeTask): List<AgentInstance> {
        val availableAgents = getAvailableAgents()
        val assignedAgents = mutableListOf<AgentInstance>()
        
        // 按优先级和能力匹配分配Agent
        task.requiredCapabilities.forEach { requiredCapability ->
            val bestAgent = availableAgents
                .filter { agent ->
                    agent.capabilities.any { capability ->
                        capability.domains.any { domain ->
                            domain.contains(requiredCapability, ignoreCase = true) ||
                            requiredCapability.contains(domain, ignoreCase = true)
                        }
                    }
                }
                .filter { !assignedAgents.contains(it) }
                .maxByOrNull { agent ->
                    val capability = agent.capabilities.find { cap ->
                        cap.domains.any { domain ->
                            domain.contains(requiredCapability, ignoreCase = true) ||
                            requiredCapability.contains(domain, ignoreCase = true)
                        }
                    }
                    capability?.skillLevel ?: 0f
                }
            
            if (bestAgent != null) {
                assignedAgents.add(bestAgent)
            }
        }
        
        // 如果没有专业Agent，分配通用Agent
        if (assignedAgents.isEmpty()) {
            val generalAgent = availableAgents.find { it.type == AgentType.GENERAL }
            if (generalAgent != null) {
                assignedAgents.add(generalAgent)
            }
        }
        
        return assignedAgents
    }
    
    /**
     * 执行单个Agent任务
     */
    private suspend fun executeAgentTask(agent: AgentInstance, task: CollaborativeTask): Pair<String, Any?> {
        return try {
            // 更新Agent状态
            agents[agent.id] = agent.copy(
                status = AgentStatus.BUSY,
                currentTask = task.title,
                workload = agent.workload + 1
            )
            
            val agentCore = agentCores[agent.id] ?: return agent.id to null
            
            // 执行任务
            var result: Any? = null
            agentCore.processUserRequest(task.description).collect { agentResult ->
                if (agentResult.success) {
                    result = agentResult.data ?: agentResult.message
                }
            }
            
            // 更新Agent状态
            agents[agent.id] = agent.copy(
                status = AgentStatus.IDLE,
                currentTask = null,
                workload = maxOf(0, agent.workload - 1),
                lastActiveTime = System.currentTimeMillis()
            )
            
            agent.id to result
            
        } catch (e: Exception) {
            Log.e(TAG, "Agent ${agent.id} 执行任务失败", e)
            
            // 恢复Agent状态
            agents[agent.id] = agent.copy(
                status = AgentStatus.ERROR,
                currentTask = null
            )
            
            agent.id to null
        }
    }
    
    /**
     * 启动任务处理器
     */
    private fun startTaskProcessor() {
        launch {
            for (task in taskQueue) {
                if (task.status == TaskStatus.PENDING) {
                    launch {
                        executeCollaborativeTask(task.id).collect { result ->
                            Log.d(TAG, "任务 ${task.id} 执行结果: ${result.message}")
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 启动Agent监控
     */
    private fun startAgentMonitor() {
        launch {
            while (isActive) {
                try {
                    // 检查Agent健康状态
                    val now = System.currentTimeMillis()
                    agents.values.forEach { agent ->
                        if (agent.status == AgentStatus.BUSY && 
                            now - agent.lastActiveTime > AGENT_TIMEOUT_MS) {
                            // Agent超时，重置状态
                            agents[agent.id] = agent.copy(
                                status = AgentStatus.ERROR,
                                currentTask = null
                            )
                            Log.w(TAG, "Agent ${agent.id} 超时，重置状态")
                        }
                    }
                    
                    // 更新系统状态
                    val load = getSystemLoad()
                    val systemLoadPercent = load["systemLoad"] as Float
                    
                    _systemStatus.value = when {
                        systemLoadPercent > 0.9f -> SystemStatus.OVERLOADED
                        systemLoadPercent > 0.1f -> SystemStatus.ACTIVE
                        else -> SystemStatus.IDLE
                    }
                    
                    delay(30000) // 30秒检查一次
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Agent监控出错", e)
                    delay(60000) // 出错时等待1分钟
                }
            }
        }
    }
    
    /**
     * 更新活跃协作列表
     */
    private fun updateActiveCollaborations() {
        val activeTasks = tasks.values.filter { 
            it.status in listOf(TaskStatus.ASSIGNED, TaskStatus.IN_PROGRESS) 
        }
        _activeCollaborations.value = activeTasks
    }
}