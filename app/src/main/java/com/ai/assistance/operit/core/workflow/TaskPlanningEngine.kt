package com.ai.assistance.operit.core.workflow

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.workflow.IntelligentCommandProcessor.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.*

/**
 * 模块2: 任务分解与规划引擎
 * 
 * - 对复杂任务进行智能分解，生成有序的子任务列表
 * - 分析任务间的依赖关系和执行顺序
 * - 评估资源需求和潜在风险点
 * - 生成详细的执行计划，包含回滚策略
 */
class TaskPlanningEngine private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "TaskPlanningEngine"
        
        @Volatile
        private var INSTANCE: TaskPlanningEngine? = null
        
        fun getInstance(context: Context): TaskPlanningEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TaskPlanningEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        // 子任务类型
        enum class SubTaskType {
            PREPARATION,    // 准备阶段
            EXECUTION,      // 执行阶段
            VALIDATION,     // 验证阶段
            CLEANUP,        // 清理阶段
            ROLLBACK        // 回滚阶段
        }
        
        // 依赖类型
        enum class DependencyType {
            SEQUENTIAL,     // 顺序依赖
            PARALLEL,       // 并行执行
            CONDITIONAL,    // 条件依赖
            OPTIONAL        // 可选依赖
        }
        
        // 资源类型
        enum class ResourceType {
            CPU, MEMORY, STORAGE, NETWORK, PERMISSION, TOOL, DATA, TIME
        }
        
        // 风险级别
        enum class RiskLevel {
            LOW, MEDIUM, HIGH, CRITICAL
        }
    }
    
    // 规划状态
    private val _planningState = MutableStateFlow<PlanningState>(PlanningState.Idle)
    val planningState: StateFlow<PlanningState> = _planningState.asStateFlow()
    
    // 规划状态密封类
    sealed class PlanningState {
        object Idle : PlanningState()
        object Planning : PlanningState()
        data class Completed(val executionPlan: ExecutionPlan) : PlanningState()
        data class Error(val message: String) : PlanningState()
    }
    
    /**
     * 子任务定义
     */
    data class SubTask(
        val id: String,
        val name: String,
        val description: String,
        val type: SubTaskType,
        val estimatedDuration: Long, // 预估时间（毫秒）
        val requiredTools: List<String>,
        val requiredResources: Map<ResourceType, String>,
        val parameters: Map<String, Any>,
        val preconditions: List<String>,
        val postconditions: List<String>,
        val riskLevel: RiskLevel,
        val riskFactors: List<String>,
        val rollbackSteps: List<String>,
        val priority: Int, // 1-10
        val isOptional: Boolean = false,
        val maxRetries: Int = 3,
        val timeout: Long = 30000L, // 30秒默认超时
        val order: Int = 0
    )
    
    /**
     * 任务依赖关系
     */
    data class TaskDependency(
        val fromTaskId: String,
        val toTaskId: String,
        val dependencyType: DependencyType,
        val condition: String? = null // 条件依赖的条件描述
    )
    
    /**
     * 资源需求评估
     */
    data class ResourceRequirement(
        val resourceType: ResourceType,
        val description: String,
        val isRequired: Boolean,
        val estimatedUsage: String,
        val alternatives: List<String> = emptyList()
    )
    
    /**
     * 风险评估
     */
    data class RiskAssessment(
        val riskLevel: RiskLevel,
        val riskFactors: List<String>,
        val mitigationStrategies: List<String>,
        val contingencyPlans: List<String>
    )
    
    /**
     * 执行计划
     */
    data class ExecutionPlan(
        val taskId: String,
        val originalTask: TaskDescription,
        val subTasks: List<SubTask>,
        val dependencies: List<TaskDependency>,
        val resourceRequirements: List<ResourceRequirement>,
        val riskAssessment: RiskAssessment,
        val estimatedTotalTime: Long,
        val executionOrder: List<String>, // 子任务ID的执行顺序
        val parallelGroups: List<List<String>>, // 可并行执行的任务组
        val rollbackPlan: List<String>, // 完整回滚计划
        val checkpoints: List<String>, // 检查点
        val createdAt: Long = System.currentTimeMillis()
    )
    
    /**
     * 为给定的任务描述生成执行计划
     */
    suspend fun createExecutionPlan(taskDescription: TaskDescription): ExecutionPlan {
        return withContext(Dispatchers.Default) {
            _planningState.value = PlanningState.Planning
            
            try {
                Log.d(TAG, "开始为任务创建执行计划: ${taskDescription.originalInput}")
                
                // 1. 任务分解
                val subTasks = decomposeTask(taskDescription)
                Log.d(TAG, "任务分解完成，共生成 ${subTasks.size} 个子任务")
                
                // 2. 依赖关系分析
                val dependencies = analyzeDependencies(subTasks, taskDescription)
                Log.d(TAG, "依赖关系分析完成，共 ${dependencies.size} 个依赖")
                
                // 3. 资源需求评估
                val resourceRequirements = assessResourceRequirements(subTasks, taskDescription)
                Log.d(TAG, "资源需求评估完成，共 ${resourceRequirements.size} 个资源需求")
                
                // 4. 风险评估
                val riskAssessment = assessRisks(subTasks, taskDescription)
                Log.d(TAG, "风险评估完成，风险级别: ${riskAssessment.riskLevel}")
                
                // 5. 生成执行顺序
                val executionOrder = generateExecutionOrder(subTasks, dependencies)
                Log.d(TAG, "执行顺序生成完成: $executionOrder")
                
                // 6. 识别并行执行组
                val parallelGroups = identifyParallelGroups(subTasks, dependencies)
                Log.d(TAG, "并行执行组识别完成，共 ${parallelGroups.size} 个组")
                
                // 7. 生成回滚计划
                val rollbackPlan = generateRollbackPlan(subTasks, executionOrder)
                Log.d(TAG, "回滚计划生成完成")
                
                // 8. 设置检查点
                val checkpoints = generateCheckpoints(subTasks, executionOrder)
                Log.d(TAG, "检查点设置完成，共 ${checkpoints.size} 个检查点")
                
                // 9. 计算总预估时间
                val estimatedTotalTime = calculateTotalTime(subTasks, parallelGroups)
                Log.d(TAG, "总预估时间: ${estimatedTotalTime}ms")
                
                val executionPlan = ExecutionPlan(
                    taskId = UUID.randomUUID().toString(),
                    originalTask = taskDescription,
                    subTasks = subTasks,
                    dependencies = dependencies,
                    resourceRequirements = resourceRequirements,
                    riskAssessment = riskAssessment,
                    estimatedTotalTime = estimatedTotalTime,
                    executionOrder = executionOrder,
                    parallelGroups = parallelGroups,
                    rollbackPlan = rollbackPlan,
                    checkpoints = checkpoints
                )
                
                _planningState.value = PlanningState.Completed(executionPlan)
                Log.d(TAG, "执行计划创建完成")
                
                executionPlan
                
            } catch (e: Exception) {
                Log.e(TAG, "创建执行计划时发生错误", e)
                _planningState.value = PlanningState.Error("规划失败: ${e.message}")
                throw e
            }
        }
    }
    
    /**
     * 任务分解
     */
    private fun decomposeTask(taskDescription: TaskDescription): List<SubTask> {
        val subTasks = mutableListOf<SubTask>()
        
        when (taskDescription.intentType) {
            IntentType.DATA_ANALYSIS -> {
                subTasks.addAll(decomposeDataAnalysisTask(taskDescription))
            }
            IntentType.FILE_OPERATION -> {
                subTasks.addAll(decomposeFileOperationTask(taskDescription))
            }
            IntentType.PROGRAMMING_TASK -> {
                subTasks.addAll(decomposeProgrammingTask(taskDescription))
            }
            IntentType.SYSTEM_QUERY -> {
                subTasks.addAll(decomposeSystemQueryTask(taskDescription))
            }
            IntentType.WEB_SEARCH -> {
                subTasks.addAll(decomposeWebSearchTask(taskDescription))
            }
            IntentType.AUTOMATION -> {
                subTasks.addAll(decomposeAutomationTask(taskDescription))
            }
            IntentType.COMPLEX_WORKFLOW -> {
                subTasks.addAll(decomposeComplexWorkflowTask(taskDescription))
            }
            else -> {
                subTasks.addAll(decomposeGenericTask(taskDescription))
            }
        }
        
        // 为所有子任务分配顺序编号
        subTasks.forEachIndexed { index, subTask ->
            subTasks[index] = subTask.copy(order = index + 1)
        }
        
        return subTasks
    }
    
    /**
     * 分解数据分析任务
     */
    private fun decomposeDataAnalysisTask(taskDescription: TaskDescription): List<SubTask> {
        val subTasks = mutableListOf<SubTask>()
        
        // 1. 数据准备
        subTasks.add(SubTask(
            id = "data_prep_${UUID.randomUUID()}",
            name = "数据准备",
            description = "加载和预处理数据",
            type = SubTaskType.PREPARATION,
            estimatedDuration = 5000L,
            requiredTools = listOf("data_loader", "data_validator"),
            requiredResources = mapOf(ResourceType.MEMORY to "适中", ResourceType.STORAGE to "读取权限"),
            parameters = taskDescription.extractedParameters,
            preconditions = listOf("数据源可访问"),
            postconditions = listOf("数据已加载到内存"),
            riskLevel = RiskLevel.MEDIUM,
            riskFactors = listOf("数据源不可用", "数据格式错误"),
            rollbackSteps = listOf("清理已加载的数据"),
            priority = 8
        ))
        
        // 2. 数据分析
        subTasks.add(SubTask(
            id = "data_analysis_${UUID.randomUUID()}",
            name = "数据分析",
            description = "执行统计分析和计算",
            type = SubTaskType.EXECUTION,
            estimatedDuration = 10000L,
            requiredTools = listOf("statistics_calculator", "data_processor"),
            requiredResources = mapOf(ResourceType.CPU to "高", ResourceType.MEMORY to "高"),
            parameters = mapOf("analysis_type" to (taskDescription.extractedParameters["statMethod"] ?: "basic")),
            preconditions = listOf("数据已准备完成"),
            postconditions = listOf("分析结果已生成"),
            riskLevel = RiskLevel.LOW,
            riskFactors = listOf("计算资源不足"),
            rollbackSteps = listOf("清理分析结果"),
            priority = 9
        ))
        
        // 3. 结果可视化（如果需要）
        if (taskDescription.extractedParameters.containsKey("chartType")) {
            subTasks.add(SubTask(
                id = "visualization_${UUID.randomUUID()}",
                name = "结果可视化",
                description = "生成图表和可视化",
                type = SubTaskType.EXECUTION,
                estimatedDuration = 7000L,
                requiredTools = listOf("chart_generator", "image_processor"),
                requiredResources = mapOf(ResourceType.MEMORY to "适中", ResourceType.STORAGE to "写入权限"),
                parameters = mapOf("chart_type" to taskDescription.extractedParameters["chartType"]!!),
                preconditions = listOf("分析结果已生成"),
                postconditions = listOf("可视化图表已创建"),
                riskLevel = RiskLevel.LOW,
                riskFactors = listOf("图形渲染失败"),
                rollbackSteps = listOf("删除生成的图表文件"),
                priority = 7
            ))
        }
        
        // 4. 结果验证
        subTasks.add(SubTask(
            id = "result_validation_${UUID.randomUUID()}",
            name = "结果验证",
            description = "验证分析结果的正确性",
            type = SubTaskType.VALIDATION,
            estimatedDuration = 3000L,
            requiredTools = listOf("result_validator"),
            requiredResources = mapOf(ResourceType.CPU to "低"),
            parameters = emptyMap(),
            preconditions = listOf("分析结果已生成"),
            postconditions = listOf("结果已验证"),
            riskLevel = RiskLevel.LOW,
            riskFactors = listOf("验证规则不完整"),
            rollbackSteps = listOf("标记结果为未验证"),
            priority = 6
        ))
        
        return subTasks
    }
    
    /**
     * 分解文件操作任务
     */
    private fun decomposeFileOperationTask(taskDescription: TaskDescription): List<SubTask> {
        val subTasks = mutableListOf<SubTask>()
        val operation = taskDescription.extractedParameters["operation"] as? String ?: "unknown"
        
        // 1. 权限检查
        subTasks.add(SubTask(
            id = "permission_check_${UUID.randomUUID()}",
            name = "权限检查",
            description = "检查文件操作权限",
            type = SubTaskType.PREPARATION,
            estimatedDuration = 1000L,
            requiredTools = listOf("permission_checker"),
            requiredResources = mapOf(ResourceType.PERMISSION to "文件系统访问"),
            parameters = mapOf("operation_type" to operation),
            preconditions = emptyList(),
            postconditions = listOf("权限验证通过"),
            riskLevel = RiskLevel.MEDIUM,
            riskFactors = listOf("权限不足"),
            rollbackSteps = emptyList(),
            priority = 10
        ))
        
        // 2. 文件操作执行
        subTasks.add(SubTask(
            id = "file_operation_${UUID.randomUUID()}",
            name = "文件操作执行",
            description = "执行具体的文件操作",
            type = SubTaskType.EXECUTION,
            estimatedDuration = 3000L,
            requiredTools = listOf("file_manager"),
            requiredResources = mapOf(
                ResourceType.STORAGE to "读写权限",
                ResourceType.MEMORY to "适中"
            ),
            parameters = taskDescription.extractedParameters,
            preconditions = listOf("权限验证通过"),
            postconditions = listOf("文件操作完成"),
            riskLevel = when (operation) {
                "删除", "delete" -> RiskLevel.HIGH
                else -> RiskLevel.MEDIUM
            },
            riskFactors = listOf("目标文件不存在", "磁盘空间不足", "文件被占用"),
            rollbackSteps = when (operation) {
                "删除", "delete" -> listOf("从回收站恢复文件")
                "移动", "move" -> listOf("将文件移回原位置")
                "创建", "create" -> listOf("删除新创建的文件")
                else -> listOf("撤销操作")
            },
            priority = 9
        ))
        
        // 3. 操作验证
        subTasks.add(SubTask(
            id = "operation_verification_${UUID.randomUUID()}",
            name = "操作验证",
            description = "验证文件操作是否成功",
            type = SubTaskType.VALIDATION,
            estimatedDuration = 1000L,
            requiredTools = listOf("file_validator"),
            requiredResources = mapOf(ResourceType.STORAGE to "读取权限"),
            parameters = emptyMap(),
            preconditions = listOf("文件操作完成"),
            postconditions = listOf("操作结果已验证"),
            riskLevel = RiskLevel.LOW,
            riskFactors = listOf("验证机制失效"),
            rollbackSteps = listOf("重新检查文件状态"),
            priority = 7
        ))
        
        return subTasks
    }
    
    /**
     * 分解编程任务
     */
    private fun decomposeProgrammingTask(taskDescription: TaskDescription): List<SubTask> {
        val subTasks = mutableListOf<SubTask>()
        
        // 1. 需求分析
        subTasks.add(SubTask(
            id = "requirement_analysis_${UUID.randomUUID()}",
            name = "需求分析",
            description = "分析编程需求和规格",
            type = SubTaskType.PREPARATION,
            estimatedDuration = 5000L,
            requiredTools = listOf("requirement_analyzer"),
            requiredResources = mapOf(ResourceType.CPU to "低"),
            parameters = taskDescription.extractedParameters,
            preconditions = emptyList(),
            postconditions = listOf("需求已明确"),
            riskLevel = RiskLevel.MEDIUM,
            riskFactors = listOf("需求不明确", "技术栈不匹配"),
            rollbackSteps = listOf("重新澄清需求"),
            priority = 8
        ))
        
        // 2. 代码生成
        subTasks.add(SubTask(
            id = "code_generation_${UUID.randomUUID()}",
            name = "代码生成",
            description = "生成符合需求的代码",
            type = SubTaskType.EXECUTION,
            estimatedDuration = 15000L,
            requiredTools = listOf("code_generator", "syntax_checker"),
            requiredResources = mapOf(
                ResourceType.CPU to "高",
                ResourceType.MEMORY to "高"
            ),
            parameters = taskDescription.extractedParameters,
            preconditions = listOf("需求已明确"),
            postconditions = listOf("代码已生成"),
            riskLevel = RiskLevel.MEDIUM,
            riskFactors = listOf("语法错误", "逻辑错误", "性能问题"),
            rollbackSteps = listOf("删除生成的代码", "恢复到上一版本"),
            priority = 9
        ))
        
        // 3. 代码测试
        subTasks.add(SubTask(
            id = "code_testing_${UUID.randomUUID()}",
            name = "代码测试",
            description = "测试生成代码的功能",
            type = SubTaskType.VALIDATION,
            estimatedDuration = 10000L,
            requiredTools = listOf("code_executor", "test_runner"),
            requiredResources = mapOf(
                ResourceType.CPU to "适中",
                ResourceType.MEMORY to "适中",
                ResourceType.PERMISSION to "代码执行权限"
            ),
            parameters = emptyMap(),
            preconditions = listOf("代码已生成"),
            postconditions = listOf("代码测试通过"),
            riskLevel = RiskLevel.HIGH,
            riskFactors = listOf("代码执行失败", "安全风险", "系统崩溃"),
            rollbackSteps = listOf("停止代码执行", "清理临时文件"),
            priority = 10
        ))
        
        return subTasks
    }
    
    /**
     * 分解通用任务
     */
    private fun decomposeGenericTask(taskDescription: TaskDescription): List<SubTask> {
        val subTasks = mutableListOf<SubTask>()
        
        // 根据复杂度决定分解策略
        when (taskDescription.complexityLevel) {
            ComplexityLevel.SIMPLE -> {
                subTasks.add(SubTask(
                    id = "simple_execution_${UUID.randomUUID()}",
                    name = "简单任务执行",
                    description = "执行简单任务",
                    type = SubTaskType.EXECUTION,
                    estimatedDuration = 3000L,
                    requiredTools = listOf("general_assistant"),
                    requiredResources = mapOf(ResourceType.CPU to "低"),
                    parameters = taskDescription.extractedParameters,
                    preconditions = emptyList(),
                    postconditions = listOf("任务完成"),
                    riskLevel = RiskLevel.LOW,
                    riskFactors = listOf("执行失败"),
                    rollbackSteps = listOf("重置状态"),
                    priority = 5
                ))
            }
            else -> {
                // 复杂任务的通用分解
                subTasks.add(SubTask(
                    id = "task_preparation_${UUID.randomUUID()}",
                    name = "任务准备",
                    description = "准备执行环境和资源",
                    type = SubTaskType.PREPARATION,
                    estimatedDuration = 2000L,
                    requiredTools = listOf("environment_setup"),
                    requiredResources = mapOf(ResourceType.MEMORY to "适中"),
                    parameters = emptyMap(),
                    preconditions = emptyList(),
                    postconditions = listOf("环境已准备"),
                    riskLevel = RiskLevel.LOW,
                    riskFactors = listOf("环境配置失败"),
                    rollbackSteps = listOf("清理环境"),
                    priority = 7
                ))
                
                subTasks.add(SubTask(
                    id = "main_execution_${UUID.randomUUID()}",
                    name = "主要任务执行",
                    description = "执行主要任务逻辑",
                    type = SubTaskType.EXECUTION,
                    estimatedDuration = 8000L,
                    requiredTools = taskDescription.requiredTools,
                    requiredResources = mapOf(ResourceType.CPU to "适中"),
                    parameters = taskDescription.extractedParameters,
                    preconditions = listOf("环境已准备"),
                    postconditions = listOf("主要任务完成"),
                    riskLevel = RiskLevel.MEDIUM,
                    riskFactors = listOf("执行中断", "资源不足"),
                    rollbackSteps = listOf("停止执行", "清理资源"),
                    priority = 8
                ))
                
                subTasks.add(SubTask(
                    id = "result_verification_${UUID.randomUUID()}",
                    name = "结果验证",
                    description = "验证任务执行结果",
                    type = SubTaskType.VALIDATION,
                    estimatedDuration = 2000L,
                    requiredTools = listOf("result_validator"),
                    requiredResources = mapOf(ResourceType.CPU to "低"),
                    parameters = emptyMap(),
                    preconditions = listOf("主要任务完成"),
                    postconditions = listOf("结果已验证"),
                    riskLevel = RiskLevel.LOW,
                    riskFactors = listOf("验证失败"),
                    rollbackSteps = listOf("标记结果为未验证"),
                    priority = 6
                ))
            }
        }
        
        return subTasks
    }
    
    /**
     * 简化的其他分解方法
     */
    private fun decomposeSystemQueryTask(taskDescription: TaskDescription): List<SubTask> {
        return listOf(
            SubTask(
                id = "system_query_${UUID.randomUUID()}",
                name = "系统查询",
                description = "查询系统信息",
                type = SubTaskType.EXECUTION,
                estimatedDuration = 2000L,
                requiredTools = listOf("system_info"),
                requiredResources = mapOf(ResourceType.PERMISSION to "系统信息访问"),
                parameters = taskDescription.extractedParameters,
                preconditions = emptyList(),
                postconditions = listOf("查询完成"),
                riskLevel = RiskLevel.LOW,
                riskFactors = listOf("权限不足"),
                rollbackSteps = emptyList(),
                priority = 6
            )
        )
    }
    
    private fun decomposeWebSearchTask(taskDescription: TaskDescription): List<SubTask> {
        return listOf(
            SubTask(
                id = "web_search_${UUID.randomUUID()}",
                name = "网络搜索",
                description = "执行网络搜索",
                type = SubTaskType.EXECUTION,
                estimatedDuration = 5000L,
                requiredTools = listOf("web_searcher"),
                requiredResources = mapOf(ResourceType.NETWORK to "互联网访问"),
                parameters = taskDescription.extractedParameters,
                preconditions = listOf("网络连接可用"),
                postconditions = listOf("搜索完成"),
                riskLevel = RiskLevel.MEDIUM,
                riskFactors = listOf("网络不可用", "搜索结果为空"),
                rollbackSteps = listOf("清理搜索缓存"),
                priority = 7
            )
        )
    }
    
    private fun decomposeAutomationTask(taskDescription: TaskDescription): List<SubTask> {
        return listOf(
            SubTask(
                id = "automation_setup_${UUID.randomUUID()}",
                name = "自动化设置",
                description = "配置自动化环境",
                type = SubTaskType.PREPARATION,
                estimatedDuration = 3000L,
                requiredTools = listOf("automation_engine"),
                requiredResources = mapOf(ResourceType.PERMISSION to "自动化权限"),
                parameters = taskDescription.extractedParameters,
                preconditions = emptyList(),
                postconditions = listOf("自动化环境已配置"),
                riskLevel = RiskLevel.MEDIUM,
                riskFactors = listOf("权限不足", "配置错误"),
                rollbackSteps = listOf("清理自动化配置"),
                priority = 8
            ),
            SubTask(
                id = "automation_execution_${UUID.randomUUID()}",
                name = "自动化执行",
                description = "执行自动化任务",
                type = SubTaskType.EXECUTION,
                estimatedDuration = 10000L,
                requiredTools = listOf("task_scheduler"),
                requiredResources = mapOf(ResourceType.CPU to "适中"),
                parameters = taskDescription.extractedParameters,
                preconditions = listOf("自动化环境已配置"),
                postconditions = listOf("自动化任务完成"),
                riskLevel = RiskLevel.HIGH,
                riskFactors = listOf("任务执行失败", "系统不稳定"),
                rollbackSteps = listOf("停止自动化任务", "恢复系统状态"),
                priority = 9
            )
        )
    }
    
    private fun decomposeComplexWorkflowTask(taskDescription: TaskDescription): List<SubTask> {
        return listOf(
            SubTask(
                id = "workflow_analysis_${UUID.randomUUID()}",
                name = "工作流分析",
                description = "分析复杂工作流需求",
                type = SubTaskType.PREPARATION,
                estimatedDuration = 8000L,
                requiredTools = listOf("workflow_analyzer"),
                requiredResources = mapOf(ResourceType.CPU to "高"),
                parameters = taskDescription.extractedParameters,
                preconditions = emptyList(),
                postconditions = listOf("工作流已分析"),
                riskLevel = RiskLevel.MEDIUM,
                riskFactors = listOf("需求复杂度超出处理能力"),
                rollbackSteps = listOf("重新简化工作流"),
                priority = 8
            ),
            SubTask(
                id = "workflow_execution_${UUID.randomUUID()}",
                name = "工作流执行",
                description = "执行复杂工作流",
                type = SubTaskType.EXECUTION,
                estimatedDuration = 20000L,
                requiredTools = listOf("workflow_engine", "execution_monitor"),
                requiredResources = mapOf(
                    ResourceType.CPU to "高",
                    ResourceType.MEMORY to "高"
                ),
                parameters = taskDescription.extractedParameters,
                preconditions = listOf("工作流已分析"),
                postconditions = listOf("工作流执行完成"),
                riskLevel = RiskLevel.HIGH,
                riskFactors = listOf("执行中断", "资源耗尽", "步骤失败"),
                rollbackSteps = listOf("停止工作流执行", "清理中间状态", "恢复初始状态"),
                priority = 10
            )
        )
    }
    
    /**
     * 分析任务依赖关系
     */
    private fun analyzeDependencies(subTasks: List<SubTask>, taskDescription: TaskDescription): List<TaskDependency> {
        val dependencies = mutableListOf<TaskDependency>()
        
        // 基本的顺序依赖：PREPARATION -> EXECUTION -> VALIDATION -> CLEANUP
        val preparationTasks = subTasks.filter { it.type == SubTaskType.PREPARATION }
        val executionTasks = subTasks.filter { it.type == SubTaskType.EXECUTION }
        val validationTasks = subTasks.filter { it.type == SubTaskType.VALIDATION }
        val cleanupTasks = subTasks.filter { it.type == SubTaskType.CLEANUP }
        
        // PREPARATION 任务之间可以并行
        // PREPARATION -> EXECUTION 依赖
        preparationTasks.forEach { prepTask ->
            executionTasks.forEach { execTask ->
                dependencies.add(TaskDependency(
                    fromTaskId = prepTask.id,
                    toTaskId = execTask.id,
                    dependencyType = DependencyType.SEQUENTIAL
                ))
            }
        }
        
        // EXECUTION -> VALIDATION 依赖
        executionTasks.forEach { execTask ->
            validationTasks.forEach { validTask ->
                dependencies.add(TaskDependency(
                    fromTaskId = execTask.id,
                    toTaskId = validTask.id,
                    dependencyType = DependencyType.SEQUENTIAL
                ))
            }
        }
        
        // VALIDATION -> CLEANUP 依赖
        validationTasks.forEach { validTask ->
            cleanupTasks.forEach { cleanupTask ->
                dependencies.add(TaskDependency(
                    fromTaskId = validTask.id,
                    toTaskId = cleanupTask.id,
                    dependencyType = DependencyType.SEQUENTIAL
                ))
            }
        }
        
        // 基于前置条件的依赖分析
        subTasks.forEach { task ->
            task.preconditions.forEach { condition ->
                val dependentTask = subTasks.find { it.postconditions.contains(condition) }
                if (dependentTask != null && dependentTask.id != task.id) {
                    dependencies.add(TaskDependency(
                        fromTaskId = dependentTask.id,
                        toTaskId = task.id,
                        dependencyType = DependencyType.SEQUENTIAL,
                        condition = condition
                    ))
                }
            }
        }
        
        return dependencies.distinctBy { "${it.fromTaskId}-${it.toTaskId}" }
    }
    
    /**
     * 评估资源需求
     */
    private fun assessResourceRequirements(subTasks: List<SubTask>, taskDescription: TaskDescription): List<ResourceRequirement> {
        val requirements = mutableListOf<ResourceRequirement>()
        
        // 聚合所有子任务的资源需求
        val aggregatedResources = mutableMapOf<ResourceType, MutableList<String>>()
        
        subTasks.forEach { subTask ->
            subTask.requiredResources.forEach { (resourceType, usage) ->
                aggregatedResources.getOrPut(resourceType) { mutableListOf() }.add(usage)
            }
        }
        
        // 生成资源需求
        aggregatedResources.forEach { (resourceType, usages) ->
            val maxUsage = when (resourceType) {
                ResourceType.CPU, ResourceType.MEMORY -> {
                    when {
                        usages.contains("高") -> "高"
                        usages.contains("适中") -> "适中"
                        else -> "低"
                    }
                }
                else -> usages.first()
            }
            
            requirements.add(ResourceRequirement(
                resourceType = resourceType,
                description = getResourceDescription(resourceType),
                isRequired = true,
                estimatedUsage = maxUsage,
                alternatives = getResourceAlternatives(resourceType)
            ))
        }
        
        return requirements
    }
    
    private fun getResourceDescription(resourceType: ResourceType): String {
        return when (resourceType) {
            ResourceType.CPU -> "处理器计算能力"
            ResourceType.MEMORY -> "内存空间"
            ResourceType.STORAGE -> "存储空间访问"
            ResourceType.NETWORK -> "网络连接"
            ResourceType.PERMISSION -> "系统权限"
            ResourceType.TOOL -> "所需工具"
            ResourceType.DATA -> "数据访问"
            ResourceType.TIME -> "执行时间"
        }
    }
    
    private fun getResourceAlternatives(resourceType: ResourceType): List<String> {
        return when (resourceType) {
            ResourceType.CPU -> listOf("降低并发度", "分批处理")
            ResourceType.MEMORY -> listOf("使用磁盘缓存", "分段处理")
            ResourceType.STORAGE -> listOf("使用临时存储", "云存储")
            ResourceType.NETWORK -> listOf("离线模式", "本地缓存")
            ResourceType.PERMISSION -> listOf("用户手动操作", "降级功能")
            else -> emptyList()
        }
    }
    
    /**
     * 评估风险
     */
    private fun assessRisks(subTasks: List<SubTask>, taskDescription: TaskDescription): RiskAssessment {
        val allRiskFactors = subTasks.flatMap { it.riskFactors }.distinct()
        val maxRiskLevel = subTasks.maxOfOrNull { it.riskLevel } ?: RiskLevel.LOW
        
        val mitigationStrategies = mutableListOf<String>()
        val contingencyPlans = mutableListOf<String>()
        
        // 基于风险级别生成缓解策略
        when (maxRiskLevel) {
            RiskLevel.HIGH, RiskLevel.CRITICAL -> {
                mitigationStrategies.addAll(listOf(
                    "实施详细的预检查",
                    "设置多个回滚点",
                    "启用实时监控",
                    "准备紧急恢复方案"
                ))
                contingencyPlans.addAll(listOf(
                    "立即停止执行",
                    "激活自动回滚",
                    "通知用户并请求指导",
                    "保存当前状态用于后续分析"
                ))
            }
            RiskLevel.MEDIUM -> {
                mitigationStrategies.addAll(listOf(
                    "执行基本预检查",
                    "设置关键回滚点",
                    "监控关键指标"
                ))
                contingencyPlans.addAll(listOf(
                    "尝试自动恢复",
                    "如果失败则回滚",
                    "记录错误信息"
                ))
            }
            RiskLevel.LOW -> {
                mitigationStrategies.add("基本错误处理")
                contingencyPlans.add("简单重试机制")
            }
        }
        
        return RiskAssessment(
            riskLevel = maxRiskLevel,
            riskFactors = allRiskFactors,
            mitigationStrategies = mitigationStrategies,
            contingencyPlans = contingencyPlans
        )
    }
    
    /**
     * 生成执行顺序
     */
    private fun generateExecutionOrder(subTasks: List<SubTask>, dependencies: List<TaskDependency>): List<String> {
        // 拓扑排序算法实现
        val inDegree = mutableMapOf<String, Int>()
        val graph = mutableMapOf<String, MutableList<String>>()
        
        // 初始化
        subTasks.forEach { task ->
            inDegree[task.id] = 0
            graph[task.id] = mutableListOf()
        }
        
        // 构建图和入度
        dependencies.forEach { dep ->
            if (dep.dependencyType == DependencyType.SEQUENTIAL) {
                graph[dep.fromTaskId]?.add(dep.toTaskId)
                inDegree[dep.toTaskId] = inDegree[dep.toTaskId]!! + 1
            }
        }
        
        // 拓扑排序
        val queue = ArrayDeque<String>()
        val result = mutableListOf<String>()
        
        // 找到所有入度为0的节点
        inDegree.forEach { (taskId, degree) ->
            if (degree == 0) {
                queue.offer(taskId)
            }
        }
        
        while (queue.isNotEmpty()) {
            val current = queue.poll()
            result.add(current)
            
            graph[current]?.forEach { neighbor ->
                inDegree[neighbor] = inDegree[neighbor]!! - 1
                if (inDegree[neighbor] == 0) {
                    queue.offer(neighbor)
                }
            }
        }
        
        // 如果结果数量不等于任务数量，说明有循环依赖，使用优先级排序
        if (result.size != subTasks.size) {
            Log.w(TAG, "检测到循环依赖，使用优先级排序")
            return subTasks.sortedWith(
                compareBy<SubTask> { it.type.ordinal }
                    .thenByDescending { it.priority }
                    .thenBy { it.order }
            ).map { it.id }
        }
        
        return result
    }
    
    /**
     * 识别并行执行组
     */
    private fun identifyParallelGroups(subTasks: List<SubTask>, dependencies: List<TaskDependency>): List<List<String>> {
        val groups = mutableListOf<List<String>>()
        val processed = mutableSetOf<String>()
        
        // 找出可以并行执行的任务
        subTasks.groupBy { it.type }.forEach { (type, tasksOfType) ->
            if (tasksOfType.size > 1) {
                val parallelTasks = tasksOfType.filter { task ->
                    !processed.contains(task.id) &&
                    // 检查这些任务之间是否没有直接依赖关系
                    !dependencies.any { dep ->
                        (dep.fromTaskId == task.id && tasksOfType.any { it.id == dep.toTaskId }) ||
                        (dep.toTaskId == task.id && tasksOfType.any { it.id == dep.fromTaskId })
                    }
                }.map { it.id }
                
                if (parallelTasks.size > 1) {
                    groups.add(parallelTasks)
                    processed.addAll(parallelTasks)
                }
            }
        }
        
        return groups
    }
    
    /**
     * 生成回滚计划
     */
    private fun generateRollbackPlan(subTasks: List<SubTask>, executionOrder: List<String>): List<String> {
        val rollbackSteps = mutableListOf<String>()
        
        // 按执行顺序的逆序生成回滚计划
        executionOrder.reversed().forEach { taskId ->
            val task = subTasks.find { it.id == taskId }
            if (task != null) {
                task.rollbackSteps.forEach { step ->
                    rollbackSteps.add("${task.name}: $step")
                }
            }
        }
        
        // 添加通用回滚步骤
        rollbackSteps.addAll(listOf(
            "清理临时文件",
            "释放占用资源",
            "恢复系统状态",
            "通知用户回滚完成"
        ))
        
        return rollbackSteps
    }
    
    /**
     * 生成检查点
     */
    private fun generateCheckpoints(subTasks: List<SubTask>, executionOrder: List<String>): List<String> {
        val checkpoints = mutableListOf<String>()
        
        // 在每个高风险任务前后设置检查点
        executionOrder.forEach { taskId ->
            val task = subTasks.find { it.id == taskId }
            if (task != null) {
                when (task.riskLevel) {
                    RiskLevel.HIGH, RiskLevel.CRITICAL -> {
                        checkpoints.add("${task.name}执行前检查点")
                        checkpoints.add("${task.name}执行后检查点")
                    }
                    RiskLevel.MEDIUM -> {
                        checkpoints.add("${task.name}执行后检查点")
                    }
                    else -> {}
                }
            }
        }
        
        // 添加阶段性检查点
        val phases = subTasks.groupBy { it.type }
        phases.forEach { (type, _) ->
            checkpoints.add("${type.name}阶段完成检查点")
        }
        
        return checkpoints.distinct()
    }
    
    /**
     * 计算总预估时间
     */
    private fun calculateTotalTime(subTasks: List<SubTask>, parallelGroups: List<List<String>>): Long {
        var totalTime = 0L
        val parallelTaskIds = parallelGroups.flatten().toSet()
        
        // 串行任务时间
        val serialTasks = subTasks.filter { !parallelTaskIds.contains(it.id) }
        totalTime += serialTasks.sumOf { it.estimatedDuration }
        
        // 并行任务时间（取每组的最大值）
        parallelGroups.forEach { group ->
            val groupTasks = subTasks.filter { group.contains(it.id) }
            val maxDuration = groupTasks.maxOfOrNull { it.estimatedDuration } ?: 0L
            totalTime += maxDuration
        }
        
        return totalTime
    }
}