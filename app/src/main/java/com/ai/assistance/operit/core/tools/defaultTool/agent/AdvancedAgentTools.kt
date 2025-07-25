package com.ai.assistance.operit.core.tools.defaultTool.agent

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.agent.*
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.ui.permissions.ToolCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * 高级Agent工具类 - 提供多Agent协作、记忆管理和性能监控功能
 */
class AdvancedAgentTools(private val context: Context) {
    
    companion object {
        private const val TAG = "AdvancedAgentTools"
    }
    
    private val multiAgentSystem = MultiAgentSystem.getInstance(context)
    private val memoryManager = AgentMemoryManager.getInstance(context)
    private val performanceMonitor = AgentPerformanceMonitor.getInstance(context)
    
    /**
     * 多Agent协作任务执行工具
     */
    inner class ExecuteCollaborativeTask : ToolExecutor {
        override fun invoke(tool: AITool): ToolResult {
            return try {
                val title = tool.parameters.find { it.name == "title" }?.value ?: "协作任务"
                val description = tool.parameters.find { it.name == "description" }?.value ?: ""
                val capabilities = tool.parameters.find { it.name == "required_capabilities" }?.value?.split(",") ?: emptyList()
                val priority = tool.parameters.find { it.name == "priority" }?.value ?: "MEDIUM"
                
                val taskPriority = when (priority.uppercase()) {
                    "LOW" -> TaskPriority.LOW
                    "HIGH" -> TaskPriority.HIGH
                    "URGENT" -> TaskPriority.URGENT
                    else -> TaskPriority.MEDIUM
                }
                
                val result = runBlocking {
                    val taskId = multiAgentSystem.submitCollaborativeTask(title, description, capabilities, taskPriority)
                    
                    val results = mutableListOf<String>()
                    multiAgentSystem.executeCollaborativeTask(taskId).collect { collaborationResult ->
                        results.add(collaborationResult.message)
                    }
                    
                    JSONObject().apply {
                        put("taskId", taskId)
                        put("success", true)
                        put("results", JSONArray(results))
                        put("message", "协作任务执行完成")
                    }
                }
                
                ToolResult(true, result.toString())
                
            } catch (e: Exception) {
                Log.e(TAG, "执行协作任务失败", e)
                ToolResult(false, "执行协作任务失败: ${e.message}")
            }
        }
        
        override fun invokeAndStream(tool: AITool): Flow<ToolResult> = flow {
            try {
                val title = tool.parameters.find { it.name == "title" }?.value ?: "协作任务"
                val description = tool.parameters.find { it.name == "description" }?.value ?: ""
                val capabilities = tool.parameters.find { it.name == "required_capabilities" }?.value?.split(",") ?: emptyList()
                val priority = tool.parameters.find { it.name == "priority" }?.value ?: "MEDIUM"
                
                val taskPriority = when (priority.uppercase()) {
                    "LOW" -> TaskPriority.LOW
                    "HIGH" -> TaskPriority.HIGH
                    "URGENT" -> TaskPriority.URGENT
                    else -> TaskPriority.MEDIUM
                }
                
                val taskId = multiAgentSystem.submitCollaborativeTask(title, description, capabilities, taskPriority)
                
                multiAgentSystem.executeCollaborativeTask(taskId).collect { collaborationResult ->
                    val result = JSONObject().apply {
                        put("taskId", taskId)
                        put("success", collaborationResult.success)
                        put("message", collaborationResult.message)
                        put("participatingAgents", JSONArray(collaborationResult.participatingAgents))
                        put("executionTime", collaborationResult.executionTime)
                    }
                    emit(ToolResult(collaborationResult.success, result.toString()))
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "执行协作任务失败", e)
                emit(ToolResult(false, "执行协作任务失败: ${e.message}"))
            }
        }
        
        override fun getCategory(): ToolCategory = ToolCategory.AI_AGENT
    }
    
    /**
     * 智能任务分解工具
     */
    inner class DecomposeComplexTask : ToolExecutor {
        override fun invoke(tool: AITool): ToolResult {
            return try {
                val taskDescription = tool.parameters.find { it.name == "task_description" }?.value ?: ""
                val maxSubTasks = tool.parameters.find { it.name == "max_sub_tasks" }?.value?.toIntOrNull() ?: 5
                
                val subTasks = runBlocking {
                    multiAgentSystem.decomposeComplexTask(taskDescription, maxSubTasks)
                }
                
                val result = JSONObject().apply {
                    put("success", true)
                    put("originalTask", taskDescription)
                    put("subTaskCount", subTasks.size)
                    put("subTasks", JSONArray().apply {
                        subTasks.forEach { task ->
                            put(JSONObject().apply {
                                put("id", task.id)
                                put("title", task.title)
                                put("description", task.description)
                                put("requiredCapabilities", JSONArray(task.requiredCapabilities))
                            })
                        }
                    })
                }
                
                ToolResult(true, result.toString())
                
            } catch (e: Exception) {
                Log.e(TAG, "任务分解失败", e)
                ToolResult(false, "任务分解失败: ${e.message}")
            }
        }
        
        override fun getCategory(): ToolCategory = ToolCategory.AI_AGENT
    }
    
    /**
     * Agent系统状态查询工具
     */
    inner class GetAgentSystemStatus : ToolExecutor {
        override fun invoke(tool: AITool): ToolResult {
            return try {
                val systemLoad = multiAgentSystem.getSystemLoad()
                val performanceReport = multiAgentSystem.getAgentPerformanceReport()
                val allAgents = multiAgentSystem.getAllAgents()
                
                val result = JSONObject().apply {
                    put("systemLoad", JSONObject(systemLoad))
                    put("performanceReport", JSONObject(performanceReport))
                    put("agents", JSONArray().apply {
                        allAgents.forEach { agent ->
                            put(JSONObject().apply {
                                put("id", agent.id)
                                put("name", agent.name)
                                put("type", agent.type.toString())
                                put("status", agent.status.toString())
                                put("workload", agent.workload)
                                put("successRate", agent.successRate)
                                put("capabilities", JSONArray().apply {
                                    agent.capabilities.forEach { capability ->
                                        put(JSONObject().apply {
                                            put("name", capability.name)
                                            put("skillLevel", capability.skillLevel)
                                            put("domains", JSONArray(capability.domains))
                                        })
                                    }
                                })
                            })
                        }
                    })
                    put("timestamp", System.currentTimeMillis())
                }
                
                ToolResult(true, result.toString())
                
            } catch (e: Exception) {
                Log.e(TAG, "获取系统状态失败", e)
                ToolResult(false, "获取系统状态失败: ${e.message}")
            }
        }
        
        override fun getCategory(): ToolCategory = ToolCategory.AI_AGENT
    }
    
    /**
     * 记忆管理工具
     */
    inner class ManageAgentMemory : ToolExecutor {
        override fun invoke(tool: AITool): ToolResult {
            return try {
                val action = tool.parameters.find { it.name == "action" }?.value ?: "get_stats"
                
                val result = when (action.lowercase()) {
                    "get_stats" -> {
                        val stats = memoryManager.getMemoryStats()
                        JSONObject().apply {
                            put("action", "get_stats")
                            put("stats", JSONObject(stats))
                        }
                    }
                    
                    "get_preferences" -> {
                        val category = tool.parameters.find { it.name == "category" }?.value
                        val preferences = if (category != null) {
                            memoryManager.getPreferencesByCategory(category)
                        } else {
                            memoryManager.preferencesFlow.value.values.toList()
                        }
                        
                        JSONObject().apply {
                            put("action", "get_preferences")
                            put("preferences", JSONArray().apply {
                                preferences.forEach { pref ->
                                    put(JSONObject().apply {
                                        put("key", pref.key)
                                        put("value", pref.value)
                                        put("category", pref.category)
                                        put("confidence", pref.confidence)
                                        put("usageCount", pref.usageCount)
                                    })
                                }
                            })
                        }
                    }
                    
                    "get_experiences" -> {
                        val taskType = tool.parameters.find { it.name == "task_type" }?.value
                        val experience = if (taskType != null) {
                            memoryManager.getTaskExperience(taskType)
                        } else null
                        
                        JSONObject().apply {
                            put("action", "get_experiences")
                            if (experience != null) {
                                put("experience", JSONObject().apply {
                                    put("taskType", experience.taskType)
                                    put("successRate", experience.successRate)
                                    put("averageExecutionTime", experience.averageExecutionTime)
                                    put("optimizationTips", JSONArray(experience.optimizationTips))
                                })
                            } else {
                                put("experience", JSONObject())
                            }
                        }
                    }
                    
                    "cleanup" -> {
                        memoryManager.cleanupExpiredMemories()
                        JSONObject().apply {
                            put("action", "cleanup")
                            put("message", "过期记忆清理完成")
                        }
                    }
                    
                    "export" -> {
                        val exportData = memoryManager.exportMemoryData()
                        JSONObject().apply {
                            put("action", "export")
                            put("data", exportData)
                        }
                    }
                    
                    "reset" -> {
                        memoryManager.resetAllMemories()
                        JSONObject().apply {
                            put("action", "reset")
                            put("message", "所有记忆已重置")
                        }
                    }
                    
                    else -> {
                        JSONObject().apply {
                            put("error", "未知操作: $action")
                            put("availableActions", JSONArray(listOf("get_stats", "get_preferences", "get_experiences", "cleanup", "export", "reset")))
                        }
                    }
                }
                
                ToolResult(true, result.toString())
                
            } catch (e: Exception) {
                Log.e(TAG, "记忆管理操作失败", e)
                ToolResult(false, "记忆管理操作失败: ${e.message}")
            }
        }
        
        override fun getCategory(): ToolCategory = ToolCategory.AI_AGENT
    }
    
    /**
     * 性能监控工具
     */
    inner class GetPerformanceMetrics : ToolExecutor {
        override fun invoke(tool: AITool): ToolResult {
            return try {
                val metricsType = tool.parameters.find { it.name == "metrics_type" }?.value ?: "overview"
                val limit = tool.parameters.find { it.name == "limit" }?.value?.toIntOrNull() ?: 10
                
                val result = when (metricsType.lowercase()) {
                    "overview" -> {
                        val stats = performanceMonitor.stats.value
                        JSONObject().apply {
                            put("type", "overview")
                            put("totalExecutions", stats.totalExecutions)
                            put("successfulExecutions", stats.successfulExecutions)
                            put("failedExecutions", stats.failedExecutions)
                            put("averageExecutionTime", stats.averageExecutionTime)
                            put("successRate", if (stats.totalExecutions > 0) stats.successfulExecutions.toFloat() / stats.totalExecutions else 0f)
                        }
                    }
                    
                    "trends" -> {
                        val days = tool.parameters.find { it.name == "days" }?.value?.toIntOrNull() ?: 7
                        val trends = performanceMonitor.getPerformanceTrends(days)
                        JSONObject().apply {
                            put("type", "trends")
                            put("days", days)
                            put("trends", JSONObject(trends))
                        }
                    }
                    
                    "popular_tasks" -> {
                        val popularTasks = performanceMonitor.getPopularTaskTypes(limit)
                        JSONObject().apply {
                            put("type", "popular_tasks")
                            put("tasks", JSONArray().apply {
                                popularTasks.forEach { (taskType, count) ->
                                    put(JSONObject().apply {
                                        put("taskType", taskType)
                                        put("count", count)
                                    })
                                }
                            })
                        }
                    }
                    
                    "common_errors" -> {
                        val commonErrors = performanceMonitor.getCommonErrors(limit)
                        JSONObject().apply {
                            put("type", "common_errors")
                            put("errors", JSONArray().apply {
                                commonErrors.forEach { (errorType, count) ->
                                    put(JSONObject().apply {
                                        put("errorType", errorType)
                                        put("count", count)
                                    })
                                }
                            })
                        }
                    }
                    
                    "step_usage" -> {
                        val stepUsage = performanceMonitor.getStepTypeUsage()
                        JSONObject().apply {
                            put("type", "step_usage")
                            put("stepTypes", JSONObject().apply {
                                stepUsage.forEach { (stepType, count) ->
                                    put(stepType.toString(), count)
                                }
                            })
                        }
                    }
                    
                    "execution_records" -> {
                        val records = performanceMonitor.getExecutionRecords(limit)
                        JSONObject().apply {
                            put("type", "execution_records")
                            put("records", JSONArray().apply {
                                records.forEach { record ->
                                    put(JSONObject().apply {
                                        put("id", record.id)
                                        put("planTitle", record.planTitle)
                                        put("success", record.success)
                                        put("duration", record.duration)
                                        put("stepCount", record.stepCount)
                                        put("completedSteps", record.completedSteps)
                                        put("taskCategory", record.taskCategory ?: "")
                                        put("startTime", record.startTime)
                                    })
                                }
                            })
                        }
                    }
                    
                    else -> {
                        JSONObject().apply {
                            put("error", "未知指标类型: $metricsType")
                            put("availableTypes", JSONArray(listOf("overview", "trends", "popular_tasks", "common_errors", "step_usage", "execution_records")))
                        }
                    }
                }
                
                ToolResult(true, result.toString())
                
            } catch (e: Exception) {
                Log.e(TAG, "获取性能指标失败", e)
                ToolResult(false, "获取性能指标失败: ${e.message}")
            }
        }
        
        override fun getCategory(): ToolCategory = ToolCategory.AI_AGENT
    }
    
    /**
     * 智能建议生成工具
     */
    inner class GenerateIntelligentSuggestions : ToolExecutor {
        override fun invoke(tool: AITool): ToolResult {
            return try {
                val currentTask = tool.parameters.find { it.name == "current_task" }?.value ?: ""
                val suggestionType = tool.parameters.find { it.name == "suggestion_type" }?.value ?: "all"
                
                val result = JSONObject().apply {
                    put("currentTask", currentTask)
                    put("suggestionType", suggestionType)
                    
                    when (suggestionType.lowercase()) {
                        "task_suggestions" -> {
                            val suggestions = memoryManager.generateTaskSuggestions(currentTask)
                            put("suggestions", JSONArray(suggestions))
                        }
                        
                        "similar_experiences" -> {
                            val experiences = memoryManager.getSimilarTaskExperiences(currentTask)
                            put("experiences", JSONArray().apply {
                                experiences.forEach { exp ->
                                    put(JSONObject().apply {
                                        put("taskType", exp.taskType)
                                        put("successRate", exp.successRate)
                                        put("optimizationTips", JSONArray(exp.optimizationTips))
                                    })
                                }
                            })
                        }
                        
                        "recommended_preferences" -> {
                            val preferences = memoryManager.getRecommendedPreferences(currentTask)
                            put("preferences", JSONArray().apply {
                                preferences.forEach { pref ->
                                    put(JSONObject().apply {
                                        put("key", pref.key)
                                        put("value", pref.value)
                                        put("confidence", pref.confidence)
                                    })
                                }
                            })
                        }
                        
                        "relevant_memories" -> {
                            val memories = memoryManager.getRelevantMemories(currentTask)
                            put("memories", JSONArray().apply {
                                memories.forEach { memory ->
                                    put(JSONObject().apply {
                                        put("sessionId", memory.sessionId)
                                        put("userIntent", memory.userIntent)
                                        put("taskHistory", JSONArray(memory.taskHistory))
                                        put("timestamp", memory.timestamp)
                                    })
                                }
                            })
                        }
                        
                        else -> {
                            // 综合建议
                            val taskSuggestions = memoryManager.generateTaskSuggestions(currentTask)
                            val experiences = memoryManager.getSimilarTaskExperiences(currentTask, 3)
                            val preferences = memoryManager.getRecommendedPreferences(currentTask, 3)
                            
                            put("taskSuggestions", JSONArray(taskSuggestions))
                            put("similarExperiences", JSONArray().apply {
                                experiences.forEach { exp ->
                                    put(JSONObject().apply {
                                        put("taskType", exp.taskType)
                                        put("successRate", exp.successRate)
                                        put("tips", JSONArray(exp.optimizationTips.take(3)))
                                    })
                                }
                            })
                            put("recommendedPreferences", JSONArray().apply {
                                preferences.forEach { pref ->
                                    put(JSONObject().apply {
                                        put("key", pref.key)
                                        put("value", pref.value)
                                        put("confidence", pref.confidence)
                                    })
                                }
                            })
                        }
                    }
                }
                
                ToolResult(true, result.toString())
                
            } catch (e: Exception) {
                Log.e(TAG, "生成智能建议失败", e)
                ToolResult(false, "生成智能建议失败: ${e.message}")
            }
        }
        
        override fun getCategory(): ToolCategory = ToolCategory.AI_AGENT
    }
}