package com.ai.assistance.operit.core.workflow

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.ui.permissions.ToolCategory
import kotlinx.coroutines.runBlocking

/**
 * 智能工作流工具
 * 
 * 将完整的AI助手工作流系统包装为一个工具，可以通过现有的工具调用机制使用
 */
class IntelligentWorkflowTool(private val context: Context) : ToolExecutor {
    
    companion object {
        private const val TAG = "IntelligentWorkflowTool"
        const val TOOL_NAME = "intelligent_workflow"
        
        fun getToolDefinition(): AITool {
            return AITool(
                name = TOOL_NAME,
                description = "执行智能工作流，包含指令理解、任务规划、代码生成、执行分析和学习优化等完整流程",
                category = ToolCategory.AUTOMATION,
                parameters = listOf(
                    ToolParameter(
                        name = "user_input",
                        type = "string",
                        description = "用户的自然语言输入，描述要执行的任务",
                        required = true
                    ),
                    ToolParameter(
                        name = "enable_learning",
                        type = "boolean",
                        description = "是否启用学习模式，记录执行结果用于优化",
                        required = false,
                        defaultValue = "true"
                    ),
                    ToolParameter(
                        name = "provide_analysis",
                        type = "boolean",
                        description = "是否提供详细的分析报告",
                        required = false,
                        defaultValue = "true"
                    ),
                    ToolParameter(
                        name = "security_level",
                        type = "string",
                        description = "安全级别: safe, moderate, elevated",
                        required = false,
                        defaultValue = "safe"
                    )
                )
            )
        }
    }
    
    private val workflowManager = IntelligentWorkflowManager.getInstance(context)
    
    override fun invoke(tool: AITool): ToolResult {
        return try {
            Log.d(TAG, "开始执行智能工作流工具")
            
            // 提取参数
            val userInput = tool.parameters["user_input"]?.toString()
                ?: return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "缺少必需参数 user_input"
                )
            
            val enableLearning = tool.parameters["enable_learning"]?.toString()?.toBoolean() ?: true
            val provideAnalysis = tool.parameters["provide_analysis"]?.toString()?.toBoolean() ?: true
            val securityLevel = tool.parameters["security_level"]?.toString() ?: "safe"
            
            Log.d(TAG, "执行工作流: input=$userInput, learning=$enableLearning, analysis=$provideAnalysis")
            
            // 执行工作流
            val result = runBlocking {
                val outputBuilder = StringBuilder()
                
                workflowManager.executeWorkflow(
                    userInput = userInput,
                    onProgress = { progress ->
                        Log.d(TAG, "工作流进度: ${progress.stage} - ${progress.message} (${progress.progress * 100}%)")
                    },
                    onOutput = { output ->
                        outputBuilder.appendLine(output)
                        Log.d(TAG, "工作流输出: $output")
                    }
                )
            }
            
            // 构建返回结果
            val resultBuilder = StringBuilder()
            
            resultBuilder.appendLine("🤖 智能工作流执行完成")
            resultBuilder.appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━")
            resultBuilder.appendLine("✅ 状态: ${if (result.success) "成功" else "失败"}")
            resultBuilder.appendLine("📝 会话ID: ${result.session.sessionId}")
            resultBuilder.appendLine()
            
            // 添加主要输出
            if (result.output.isNotEmpty()) {
                resultBuilder.appendLine("📄 执行结果:")
                resultBuilder.appendLine(result.output)
                resultBuilder.appendLine()
            }
            
            // 添加分析报告（如果启用）
            if (provideAnalysis && result.analysisReport != null) {
                val report = result.analysisReport
                resultBuilder.appendLine("📊 分析报告:")
                resultBuilder.appendLine("   质量评级: ${getQualityIcon(report.overallQuality)} ${report.overallQuality}")
                resultBuilder.appendLine("   成功率: ${String.format("%.1f", report.successRate * 100)}%")
                resultBuilder.appendLine("   执行时间: ${report.performanceMetrics.executionTimeMs}ms")
                
                if (report.detectedIssues.isNotEmpty()) {
                    resultBuilder.appendLine("   ⚠️ 检测到问题: ${report.detectedIssues.size}个")
                    report.detectedIssues.take(3).forEach { issue ->
                        resultBuilder.appendLine("     - ${issue.severity}: ${issue.description}")
                    }
                }
                
                if (report.recommendations.isNotEmpty()) {
                    resultBuilder.appendLine("   💡 优化建议: ${report.recommendations.size}个")
                    report.recommendations.take(3).forEach { rec ->
                        resultBuilder.appendLine("     - ${rec.category}: ${rec.title}")
                    }
                }
                resultBuilder.appendLine()
            }
            
            // 添加学习统计（如果启用）
            if (enableLearning) {
                val stats = workflowManager.getLearningStatistics()
                resultBuilder.appendLine("🧠 学习统计:")
                resultBuilder.appendLine("   总执行次数: ${stats.totalExecutions}")
                resultBuilder.appendLine("   整体成功率: ${String.format("%.1f", stats.successRate * 100)}%")
                resultBuilder.appendLine("   学习模式数: ${stats.learnedPatterns}")
                resultBuilder.appendLine("   用户满意度: ${String.format("%.1f", stats.userSatisfactionAvg)}/5.0")
                resultBuilder.appendLine()
            }
            
            // 添加推荐建议
            if (result.recommendations.isNotEmpty()) {
                resultBuilder.appendLine("🎯 推荐建议:")
                result.recommendations.take(3).forEach { recommendation ->
                    resultBuilder.appendLine("   • $recommendation")
                }
                resultBuilder.appendLine()
            }
            
            // 添加元数据
            resultBuilder.appendLine("ℹ️ 执行信息:")
            resultBuilder.appendLine("   总耗时: ${result.metadata["total_time"]}ms")
            result.metadata["intent_confidence"]?.let {
                resultBuilder.appendLine("   意图置信度: ${String.format("%.1f", (it as Float) * 100)}%")
            }
            result.metadata["result_quality"]?.let {
                resultBuilder.appendLine("   结果质量: $it")
            }
            
            Log.d(TAG, "智能工作流工具执行完成: success=${result.success}")
            
            ToolResult(
                toolName = tool.name,
                success = result.success,
                result = StringResultData(resultBuilder.toString()),
                error = if (!result.success) result.output else null
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "智能工作流工具执行失败", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "工作流执行失败: ${e.message}"
            )
        }
    }
    
    override fun validateParameters(tool: AITool): ToolValidationResult {
        val errors = mutableListOf<String>()
        
        // 验证必需参数
        if (!tool.parameters.containsKey("user_input") || 
            tool.parameters["user_input"]?.toString().isNullOrBlank()) {
            errors.add("user_input 参数是必需的且不能为空")
        }
        
        // 验证安全级别参数 - 现在接受所有级别，默认允许
        val securityLevel = tool.parameters["security_level"]?.toString()
        if (securityLevel != null && 
            securityLevel !in listOf("safe", "moderate", "elevated")) {
            // 如果提供了无效的安全级别，设置为safe
            tool.parameters["security_level"] = "safe"
        }
        
        // 验证布尔参数
        listOf("enable_learning", "provide_analysis").forEach { paramName ->
            val value = tool.parameters[paramName]?.toString()
            if (value != null && value.lowercase() !in listOf("true", "false")) {
                errors.add("$paramName 必须是 true 或 false")
            }
        }
        
        return if (errors.isEmpty()) {
            ToolValidationResult(isValid = true)
        } else {
            ToolValidationResult(isValid = false, errors = errors)
        }
    }
    
    override fun getCategory(): ToolCategory {
        return ToolCategory.AUTOMATION
    }
    
    private fun getQualityIcon(quality: ResultAnalysisEngine.ResultQuality): String {
        return when (quality) {
            ResultAnalysisEngine.ResultQuality.EXCELLENT -> "🌟"
            ResultAnalysisEngine.ResultQuality.GOOD -> "👍"
            ResultAnalysisEngine.ResultQuality.FAIR -> "⚠️"
            ResultAnalysisEngine.ResultQuality.POOR -> "❌"
            ResultAnalysisEngine.ResultQuality.CRITICAL -> "🚨"
        }
    }
    
    /**
     * 获取工作流系统状态
     */
    fun getSystemStatus(): Map<String, Any> {
        return workflowManager.getSystemOverview()
    }
    
    /**
     * 获取优化建议
     */
    fun getOptimizationSuggestions(): List<AdaptiveLearningEngine.OptimizationSuggestion> {
        return workflowManager.getOptimizationSuggestions()
    }
    
    /**
     * 取消当前工作流
     */
    fun cancelCurrentWorkflow() {
        workflowManager.cancelWorkflow()
    }
}