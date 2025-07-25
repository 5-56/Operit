package com.ai.assistance.operit.core.tools.defaultTool.agent

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.agent.AgentCore
import com.ai.assistance.operit.core.agent.AgentResult
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.ui.permissions.ToolCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * Agent工具类 - 提供智能agent功能的工具
 */
class AgentTools(private val context: Context) {
    
    companion object {
        private const val TAG = "AgentTools"
    }
    
    private val agentCore = AgentCore.getInstance(context)
    
    /**
     * 执行Agent任务工具
     */
    inner class ExecuteAgentTask : ToolExecutor {
        override fun invoke(tool: AITool): ToolResult {
            return runBlocking {
                try {
                    val userRequest = tool.parameters["request"]?.toString() 
                        ?: return@runBlocking ToolResult(false, "缺少必要参数: request")
                    
                    Log.d(TAG, "开始执行Agent任务: $userRequest")
                    
                    val results = mutableListOf<String>()
                    var finalResult: AgentResult? = null
                    
                    // 收集所有执行结果
                    agentCore.processUserRequest(userRequest).collect { result ->
                        results.add("${result.message}")
                        finalResult = result
                        
                        // 如果有计划信息，添加到结果中
                        result.plan?.let { plan ->
                            results.add("计划: ${plan.title} - ${plan.description}")
                            results.add("状态: ${plan.status}")
                        }
                        
                        // 如果有当前步骤信息，添加到结果中
                        result.currentStep?.let { step ->
                            results.add("当前步骤: ${step.description} (${step.status})")
                        }
                    }
                    
                    val success = finalResult?.success ?: false
                    val message = results.joinToString("\n")
                    
                    ToolResult(success, message, finalResult?.data)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Agent任务执行失败", e)
                    ToolResult(false, "Agent任务执行失败: ${e.message}")
                }
            }
        }
        
        override fun invokeAndStream(tool: AITool): Flow<ToolResult> = flow {
            try {
                val userRequest = tool.parameters["request"]?.toString() 
                    ?: run {
                        emit(ToolResult(false, "缺少必要参数: request"))
                        return@flow
                    }
                
                Log.d(TAG, "开始流式执行Agent任务: $userRequest")
                
                // 流式输出执行过程
                agentCore.processUserRequest(userRequest).collect { result ->
                    val message = buildString {
                        append(result.message)
                        
                        result.plan?.let { plan ->
                            append("\n计划: ${plan.title}")
                            append("\n描述: ${plan.description}")
                            append("\n状态: ${plan.status}")
                            
                            if (plan.steps.isNotEmpty()) {
                                append("\n步骤:")
                                plan.steps.forEach { step ->
                                    append("\n  - ${step.description} (${step.status})")
                                }
                            }
                        }
                        
                        result.currentStep?.let { step ->
                            append("\n当前步骤: ${step.description}")
                            append("\n步骤状态: ${step.status}")
                            if (!step.script.isNullOrEmpty()) {
                                append("\n脚本: ${step.script!!.take(200)}...")
                            }
                        }
                    }
                    
                    emit(ToolResult(result.success, message, result.data))
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Agent任务流式执行失败", e)
                emit(ToolResult(false, "Agent任务流式执行失败: ${e.message}"))
            }
        }
        
        override fun getCategory(): ToolCategory = ToolCategory.AI_AGENT
    }
    
    /**
     * 获取当前Agent计划工具
     */
    inner class GetCurrentPlan : ToolExecutor {
        override fun invoke(tool: AITool): ToolResult {
            return try {
                val currentPlan = agentCore.getCurrentPlan()
                
                if (currentPlan != null) {
                    val planInfo = JSONObject().apply {
                        put("id", currentPlan.id)
                        put("title", currentPlan.title)
                        put("description", currentPlan.description)
                        put("status", currentPlan.status.toString())
                        put("steps", currentPlan.steps.map { step ->
                            JSONObject().apply {
                                put("id", step.id)
                                put("type", step.type.toString())
                                put("description", step.description)
                                put("status", step.status.toString())
                                put("hasScript", !step.script.isNullOrEmpty())
                            }
                        })
                    }
                    
                    ToolResult(true, "当前执行计划信息", planInfo.toString())
                } else {
                    ToolResult(true, "当前没有正在执行的计划")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "获取当前计划失败", e)
                ToolResult(false, "获取当前计划失败: ${e.message}")
            }
        }
        
        override fun getCategory(): ToolCategory = ToolCategory.AI_AGENT
    }
    
    /**
     * 获取Agent执行历史工具
     */
    inner class GetExecutionHistory : ToolExecutor {
        override fun invoke(tool: AITool): ToolResult {
            return try {
                val history = agentCore.getExecutionHistory()
                val limit = tool.parameters["limit"]?.toString()?.toIntOrNull() ?: 10
                
                val historyInfo = history.takeLast(limit).map { plan ->
                    JSONObject().apply {
                        put("id", plan.id)
                        put("title", plan.title)
                        put("description", plan.description)
                        put("status", plan.status.toString())
                        put("stepsCount", plan.steps.size)
                        put("completedSteps", plan.steps.count { it.status.toString() == "COMPLETED" })
                    }
                }
                
                ToolResult(true, "Agent执行历史 (最近${historyInfo.size}条)", historyInfo.toString())
                
            } catch (e: Exception) {
                Log.e(TAG, "获取执行历史失败", e)
                ToolResult(false, "获取执行历史失败: ${e.message}")
            }
        }
        
        override fun getCategory(): ToolCategory = ToolCategory.AI_AGENT
    }
    
    /**
     * 暂停当前Agent计划工具
     */
    inner class PauseCurrentPlan : ToolExecutor {
        override fun invoke(tool: AITool): ToolResult {
            return try {
                agentCore.pauseCurrentPlan()
                ToolResult(true, "已暂停当前Agent计划")
            } catch (e: Exception) {
                Log.e(TAG, "暂停计划失败", e)
                ToolResult(false, "暂停计划失败: ${e.message}")
            }
        }
        
        override fun getCategory(): ToolCategory = ToolCategory.AI_AGENT
    }
    
    /**
     * 恢复当前Agent计划工具
     */
    inner class ResumeCurrentPlan : ToolExecutor {
        override fun invoke(tool: AITool): ToolResult {
            return try {
                agentCore.resumeCurrentPlan()
                ToolResult(true, "已恢复当前Agent计划")
            } catch (e: Exception) {
                Log.e(TAG, "恢复计划失败", e)
                ToolResult(false, "恢复计划失败: ${e.message}")
            }
        }
        
        override fun getCategory(): ToolCategory = ToolCategory.AI_AGENT
    }
    
    /**
     * 取消当前Agent计划工具
     */
    inner class CancelCurrentPlan : ToolExecutor {
        override fun invoke(tool: AITool): ToolResult {
            return try {
                agentCore.cancelCurrentPlan()
                ToolResult(true, "已取消当前Agent计划")
            } catch (e: Exception) {
                Log.e(TAG, "取消计划失败", e)
                ToolResult(false, "取消计划失败: ${e.message}")
            }
        }
        
        override fun getCategory(): ToolCategory = ToolCategory.AI_AGENT
    }
    
    /**
     * 智能脚本生成和执行工具
     */
    inner class SmartScriptExecution : ToolExecutor {
        override fun invoke(tool: AITool): ToolResult {
            return runBlocking {
                try {
                    val taskDescription = tool.parameters["task"]?.toString() 
                        ?: return@runBlocking ToolResult(false, "缺少必要参数: task")
                    
                    val context = tool.parameters["context"]?.toString() ?: ""
                    
                    Log.d(TAG, "开始智能脚本执行: $taskDescription")
                    
                    // 使用Agent核心功能，但只执行脚本生成和执行步骤
                    val request = "请为以下任务生成并执行JavaScript脚本：$taskDescription${if (context.isNotEmpty()) "\n上下文：$context" else ""}"
                    
                    var finalResult: AgentResult? = null
                    val results = mutableListOf<String>()
                    
                    agentCore.processUserRequest(request).collect { result ->
                        results.add(result.message)
                        finalResult = result
                    }
                    
                    val success = finalResult?.success ?: false
                    val message = results.joinToString("\n")
                    
                    ToolResult(success, message, finalResult?.data)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "智能脚本执行失败", e)
                    ToolResult(false, "智能脚本执行失败: ${e.message}")
                }
            }
        }
        
        override fun invokeAndStream(tool: AITool): Flow<ToolResult> = flow {
            try {
                val taskDescription = tool.parameters["task"]?.toString() 
                    ?: run {
                        emit(ToolResult(false, "缺少必要参数: task"))
                        return@flow
                    }
                
                val context = tool.parameters["context"]?.toString() ?: ""
                
                Log.d(TAG, "开始流式智能脚本执行: $taskDescription")
                
                val request = "请为以下任务生成并执行JavaScript脚本：$taskDescription${if (context.isNotEmpty()) "\n上下文：$context" else ""}"
                
                agentCore.processUserRequest(request).collect { result ->
                    emit(ToolResult(result.success, result.message, result.data))
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "智能脚本流式执行失败", e)
                emit(ToolResult(false, "智能脚本流式执行失败: ${e.message}"))
            }
        }
        
        override fun getCategory(): ToolCategory = ToolCategory.AI_AGENT
    }
}