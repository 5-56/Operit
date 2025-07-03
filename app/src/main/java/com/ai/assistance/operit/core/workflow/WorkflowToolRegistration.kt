package com.ai.assistance.operit.core.workflow

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.ui.permissions.ToolCategory

/**
 * 智能工作流工具注册
 * 
 * 将所有工作流相关的工具注册到AIToolHandler中
 */
object WorkflowToolRegistration {
    
    private const val TAG = "WorkflowToolRegistration"
    
    /**
     * 注册所有工作流工具
     */
    fun registerWorkflowTools(handler: AIToolHandler, context: Context) {
        Log.d(TAG, "开始注册智能工作流工具")
        
        // 注册主要的智能工作流工具
        registerIntelligentWorkflowTool(handler, context)
        
        // 注册辅助工具
        registerWorkflowStatusTool(handler, context)
        registerWorkflowLearningTool(handler, context)
        registerWorkflowCancelTool(handler, context)
        
        Log.d(TAG, "智能工作流工具注册完成")
    }
    
    /**
     * 注册主要的智能工作流工具
     */
    private fun registerIntelligentWorkflowTool(handler: AIToolHandler, context: Context) {
        val workflowTool = IntelligentWorkflowTool(context)
        
        handler.registerTool(
            name = IntelligentWorkflowTool.TOOL_NAME,
            category = ToolCategory.AUTOMATION,
            dangerCheck = { tool ->
                // 检查安全级别
                val securityLevel = tool.parameters["security_level"]?.toString() ?: "safe"
                val userInput = tool.parameters["user_input"]?.toString() ?: ""
                
                // 如果安全级别为elevated或包含危险关键词则需要确认
                securityLevel == "elevated" || containsDangerousKeywords(userInput)
            },
            descriptionGenerator = { tool ->
                val userInput = tool.parameters["user_input"]?.toString() ?: ""
                val securityLevel = tool.parameters["security_level"]?.toString() ?: "safe"
                val analysisEnabled = tool.parameters["provide_analysis"]?.toString()?.toBoolean() ?: true
                
                val description = StringBuilder()
                description.append("执行智能工作流: $userInput")
                
                if (securityLevel != "safe") {
                    description.append(" [安全级别: $securityLevel]")
                }
                
                if (analysisEnabled) {
                    description.append(" [包含详细分析]")
                }
                
                description.toString()
            },
            executor = workflowTool
        )
        
        Log.d(TAG, "注册了智能工作流工具: ${IntelligentWorkflowTool.TOOL_NAME}")
    }
    
    /**
     * 注册工作流状态查询工具
     */
    private fun registerWorkflowStatusTool(handler: AIToolHandler, context: Context) {
        handler.registerTool(
            name = "workflow_status",
            category = ToolCategory.SYSTEM_OPERATION,
            descriptionGenerator = { _ ->
                "获取智能工作流系统状态和统计信息"
            },
            executor = { tool ->
                try {
                    val workflowManager = IntelligentWorkflowManager.getInstance(context)
                    val systemOverview = workflowManager.getSystemOverview()
                    val learningStats = workflowManager.getLearningStatistics()
                    
                    val statusBuilder = StringBuilder()
                    statusBuilder.appendLine("🤖 智能工作流系统状态")
                    statusBuilder.appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    statusBuilder.appendLine("📊 当前状态: ${systemOverview["workflow_state"]}")
                    statusBuilder.appendLine("🔄 当前会话: ${systemOverview["current_session"]}")
                    statusBuilder.appendLine("🏥 系统健康: ${systemOverview["system_health"]}")
                    statusBuilder.appendLine()
                    
                    statusBuilder.appendLine("📈 执行统计:")
                    statusBuilder.appendLine("   总执行次数: ${learningStats.totalExecutions}")
                    statusBuilder.appendLine("   成功率: ${String.format("%.1f", learningStats.successRate * 100)}%")
                    statusBuilder.appendLine("   平均执行时间: ${learningStats.averageExecutionTime}ms")
                    statusBuilder.appendLine("   用户满意度: ${String.format("%.1f", learningStats.userSatisfactionAvg)}/5.0")
                    statusBuilder.appendLine("   学习模式数: ${learningStats.learnedPatterns}")
                    statusBuilder.appendLine()
                    
                    statusBuilder.appendLine("🧩 热门任务类型:")
                    learningStats.topIntentTypes.take(3).forEachIndexed { index, (type, count) ->
                        statusBuilder.appendLine("   ${index + 1}. ${getIntentTypeDescription(type)}: $count 次")
                    }
                    
                    if (systemOverview["sandbox_files"] as Int > 0) {
                        statusBuilder.appendLine()
                        statusBuilder.appendLine("📁 沙箱文件: ${systemOverview["sandbox_files"]} 个")
                    }
                    
                    com.ai.assistance.operit.data.model.ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = com.ai.assistance.operit.core.tools.StringResultData(statusBuilder.toString())
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "获取工作流状态失败", e)
                    com.ai.assistance.operit.data.model.ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = com.ai.assistance.operit.core.tools.StringResultData(""),
                        error = "获取状态失败: ${e.message}"
                    )
                }
            }
        )
        
        Log.d(TAG, "注册了工作流状态工具: workflow_status")
    }
    
    /**
     * 注册学习和优化工具
     */
    private fun registerWorkflowLearningTool(handler: AIToolHandler, context: Context) {
        handler.registerTool(
            name = "workflow_optimization",
            category = ToolCategory.SYSTEM_OPERATION,
            descriptionGenerator = { _ ->
                "获取智能工作流系统的优化建议和学习洞察"
            },
            executor = { tool ->
                try {
                    val workflowManager = IntelligentWorkflowManager.getInstance(context)
                    val suggestions = workflowManager.getOptimizationSuggestions()
                    
                    val optimizationBuilder = StringBuilder()
                    optimizationBuilder.appendLine("🧠 智能工作流优化建议")
                    optimizationBuilder.appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    
                    if (suggestions.isEmpty()) {
                        optimizationBuilder.appendLine("✅ 系统运行良好，暂无优化建议")
                    } else {
                        optimizationBuilder.appendLine("💡 发现 ${suggestions.size} 个优化机会:")
                        optimizationBuilder.appendLine()
                        
                        suggestions.forEachIndexed { index, suggestion ->
                            optimizationBuilder.appendLine("${index + 1}. ${suggestion.category}")
                            optimizationBuilder.appendLine("   📝 描述: ${suggestion.description}")
                            optimizationBuilder.appendLine("   🎯 预期改进: ${suggestion.expectedImprovement}")
                            optimizationBuilder.appendLine("   🔧 实施方案: ${suggestion.implementation}")
                            optimizationBuilder.appendLine("   📊 置信度: ${String.format("%.1f", suggestion.confidence * 100)}%")
                            optimizationBuilder.appendLine()
                        }
                    }
                    
                    // 添加学习洞察
                    val learningStats = workflowManager.getLearningStatistics()
                    if (learningStats.totalExecutions > 0) {
                        optimizationBuilder.appendLine("🔍 学习洞察:")
                        optimizationBuilder.appendLine("   • 系统已处理 ${learningStats.totalExecutions} 个任务")
                        optimizationBuilder.appendLine("   • 学习了 ${learningStats.learnedPatterns} 种模式")
                        optimizationBuilder.appendLine("   • 应用了 ${learningStats.optimizationsApplied} 次优化")
                        
                        if (learningStats.successRate < 0.8f) {
                            optimizationBuilder.appendLine("   ⚠️ 成功率偏低，建议关注错误处理和输入验证")
                        }
                        
                        if (learningStats.userSatisfactionAvg < 3.5f && learningStats.userSatisfactionAvg > 0) {
                            optimizationBuilder.appendLine("   ⚠️ 用户满意度有待提升，建议改进用户体验")
                        }
                    }
                    
                    com.ai.assistance.operit.data.model.ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = com.ai.assistance.operit.core.tools.StringResultData(optimizationBuilder.toString())
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "获取优化建议失败", e)
                    com.ai.assistance.operit.data.model.ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = com.ai.assistance.operit.core.tools.StringResultData(""),
                        error = "获取优化建议失败: ${e.message}"
                    )
                }
            }
        )
        
        Log.d(TAG, "注册了工作流优化工具: workflow_optimization")
    }
    
    /**
     * 注册工作流取消工具
     */
    private fun registerWorkflowCancelTool(handler: AIToolHandler, context: Context) {
        handler.registerTool(
            name = "cancel_workflow",
            category = ToolCategory.SYSTEM_OPERATION,
            descriptionGenerator = { _ ->
                "取消当前正在执行的智能工作流"
            },
            executor = { tool ->
                try {
                    val workflowManager = IntelligentWorkflowManager.getInstance(context)
                    workflowManager.cancelWorkflow()
                    
                    com.ai.assistance.operit.data.model.ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = com.ai.assistance.operit.core.tools.StringResultData(
                            "✅ 当前工作流已取消\n" +
                            "🔄 系统已重置为空闲状态\n" +
                            "💡 您现在可以开始新的工作流执行"
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "取消工作流失败", e)
                    com.ai.assistance.operit.data.model.ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = com.ai.assistance.operit.core.tools.StringResultData(""),
                        error = "取消工作流失败: ${e.message}"
                    )
                }
            }
        )
        
        Log.d(TAG, "注册了工作流取消工具: cancel_workflow")
    }
    
    /**
     * 检查是否包含危险关键词
     */
    private fun containsDangerousKeywords(input: String): Boolean {
        val dangerousKeywords = listOf(
            "删除", "delete", "remove", "rm",
            "格式化", "format", 
            "重启", "reboot", "restart",
            "关机", "shutdown",
            "root", "sudo", "su",
            "system", "etc", "bin",
            "密码", "password", "passwd",
            "支付", "pay", "payment",
            "购买", "buy", "purchase",
            "转账", "transfer"
        )
        
        val lowerInput = input.lowercase()
        return dangerousKeywords.any { keyword ->
            lowerInput.contains(keyword)
        }
    }
    
    /**
     * 获取意图类型的中文描述
     */
    private fun getIntentTypeDescription(intentType: IntelligentCommandProcessor.Companion.IntentType): String {
        return when (intentType) {
            IntelligentCommandProcessor.Companion.IntentType.DATA_ANALYSIS -> "数据分析"
            IntelligentCommandProcessor.Companion.IntentType.FILE_OPERATION -> "文件操作"
            IntelligentCommandProcessor.Companion.IntentType.SYSTEM_QUERY -> "系统查询"
            IntelligentCommandProcessor.Companion.IntentType.PROGRAMMING_TASK -> "编程任务"
            IntelligentCommandProcessor.Companion.IntentType.WEB_SEARCH -> "网络搜索"
            IntelligentCommandProcessor.Companion.IntentType.AUTOMATION -> "自动化任务"
            IntelligentCommandProcessor.Companion.IntentType.COMMUNICATION -> "通信任务"
            IntelligentCommandProcessor.Companion.IntentType.MEDIA_PROCESSING -> "媒体处理"
            IntelligentCommandProcessor.Companion.IntentType.CALCULATION -> "计算任务"
            IntelligentCommandProcessor.Companion.IntentType.GENERAL_CHAT -> "一般对话"
            IntelligentCommandProcessor.Companion.IntentType.COMPLEX_WORKFLOW -> "复杂工作流"
            IntelligentCommandProcessor.Companion.IntentType.UNKNOWN -> "未知类型"
        }
    }
}