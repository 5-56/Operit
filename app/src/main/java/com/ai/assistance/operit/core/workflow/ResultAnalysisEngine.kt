package com.ai.assistance.operit.core.workflow

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.*

/**
 * 模块4: 结果分析与智能反馈系统
 * 
 * - 分析代码执行结果，包括输出、错误、性能指标
 * - 生成结构化的执行报告和数据可视化
 * - 提供清晰的成功/失败状态说明
 * - 对异常情况提供详细的诊断信息和修复建议
 */
class ResultAnalysisEngine private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ResultAnalysisEngine"
        
        @Volatile
        private var INSTANCE: ResultAnalysisEngine? = null
        
        fun getInstance(context: Context): ResultAnalysisEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ResultAnalysisEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        enum class AnalysisStatus {
            IDLE, ANALYZING, COMPLETED, FAILED
        }
        
        enum class ResultQuality {
            EXCELLENT, GOOD, FAIR, POOR, CRITICAL
        }
        
        enum class IssueType {
            PERFORMANCE, SECURITY, LOGIC, SYNTAX, RESOURCE, COMPATIBILITY
        }
    }
    
    private val _analysisState = MutableStateFlow<AnalysisStatus>(AnalysisStatus.IDLE)
    val analysisState: StateFlow<AnalysisStatus> = _analysisState.asStateFlow()
    
    /**
     * 分析报告
     */
    data class AnalysisReport(
        val reportId: String,
        val executionResult: CodeExecutionEngine.ExecutionResult,
        val overallQuality: ResultQuality,
        val successRate: Float, // 0.0 - 1.0
        val performanceMetrics: PerformanceMetrics,
        val detectedIssues: List<Issue>,
        val recommendations: List<Recommendation>,
        val visualizations: List<Visualization>,
        val summary: String,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    data class PerformanceMetrics(
        val executionTimeMs: Long,
        val memoryUsageMB: Float,
        val cpuUsagePercent: Float,
        val diskIOKB: Long,
        val networkRequestCount: Int,
        val efficiency: Float // 0.0 - 1.0
    )
    
    data class Issue(
        val id: String,
        val type: IssueType,
        val severity: String, // LOW, MEDIUM, HIGH, CRITICAL
        val description: String,
        val location: String?,
        val suggestedFix: String,
        val automatedFix: Boolean = false
    )
    
    data class Recommendation(
        val id: String,
        val category: String,
        val title: String,
        val description: String,
        val priority: Int, // 1-10
        val implementationGuide: String,
        val expectedBenefit: String
    )
    
    data class Visualization(
        val id: String,
        val type: String, // chart, graph, table, diagram
        val title: String,
        val data: Map<String, Any>,
        val config: Map<String, Any> = emptyMap()
    )
    
    /**
     * 分析执行结果
     */
    suspend fun analyzeResult(executionResult: CodeExecutionEngine.ExecutionResult): AnalysisReport {
        return withContext(Dispatchers.Default) {
            _analysisState.value = AnalysisStatus.ANALYZING
            
            try {
                Log.d(TAG, "开始分析执行结果: ${executionResult.codeId}")
                
                // 1. 基础质量评估
                val overallQuality = assessOverallQuality(executionResult)
                
                // 2. 成功率计算
                val successRate = calculateSuccessRate(executionResult)
                
                // 3. 性能指标分析
                val performanceMetrics = analyzePerformance(executionResult)
                
                // 4. 问题检测
                val detectedIssues = detectIssues(executionResult)
                
                // 5. 生成建议
                val recommendations = generateRecommendations(executionResult, detectedIssues)
                
                // 6. 创建可视化
                val visualizations = createVisualizations(executionResult, performanceMetrics)
                
                // 7. 生成摘要
                val summary = generateSummary(executionResult, overallQuality, detectedIssues)
                
                val report = AnalysisReport(
                    reportId = UUID.randomUUID().toString(),
                    executionResult = executionResult,
                    overallQuality = overallQuality,
                    successRate = successRate,
                    performanceMetrics = performanceMetrics,
                    detectedIssues = detectedIssues,
                    recommendations = recommendations,
                    visualizations = visualizations,
                    summary = summary
                )
                
                _analysisState.value = AnalysisStatus.COMPLETED
                Log.d(TAG, "结果分析完成: ${report.reportId}")
                
                report
                
            } catch (e: Exception) {
                Log.e(TAG, "结果分析失败", e)
                _analysisState.value = AnalysisStatus.FAILED
                throw e
            }
        }
    }
    
    private fun assessOverallQuality(result: CodeExecutionEngine.ExecutionResult): ResultQuality {
        var score = 100
        
        // 基于成功状态
        if (!result.success) score -= 50
        
        // 基于执行时间
        if (result.executionTime > 30000) score -= 20
        else if (result.executionTime > 15000) score -= 10
        
        // 基于错误输出
        if (result.errorOutput.isNotEmpty()) score -= 15
        
        // 基于异常
        if (result.exception != null) score -= 25
        
        return when {
            score >= 90 -> ResultQuality.EXCELLENT
            score >= 80 -> ResultQuality.GOOD
            score >= 60 -> ResultQuality.FAIR
            score >= 40 -> ResultQuality.POOR
            else -> ResultQuality.CRITICAL
        }
    }
    
    private fun calculateSuccessRate(result: CodeExecutionEngine.ExecutionResult): Float {
        return if (result.success) 1.0f else 0.0f
    }
    
    private fun analyzePerformance(result: CodeExecutionEngine.ExecutionResult): PerformanceMetrics {
        // 从资源使用信息中提取性能指标
        val memoryUsage = extractMemoryUsage(result.resourceUsage)
        val cpuUsage = extractCpuUsage(result.resourceUsage)
        val efficiency = calculateEfficiency(result)
        
        return PerformanceMetrics(
            executionTimeMs = result.executionTime,
            memoryUsageMB = memoryUsage,
            cpuUsagePercent = cpuUsage,
            diskIOKB = 0L,
            networkRequestCount = 0,
            efficiency = efficiency
        )
    }
    
    private fun extractMemoryUsage(resourceUsage: Map<String, Any>): Float {
        return resourceUsage["memory_used"]?.toString()?.let { 
            it.replace("MB", "").toFloatOrNull() 
        } ?: 0.0f
    }
    
    private fun extractCpuUsage(resourceUsage: Map<String, Any>): Float {
        return resourceUsage["cpu_time"]?.toString()?.let {
            it.replace("s", "").toFloatOrNull()?.times(100)
        } ?: 0.0f
    }
    
    private fun calculateEfficiency(result: CodeExecutionEngine.ExecutionResult): Float {
        return if (result.success && result.executionTime > 0) {
            val baseEfficiency = if (result.executionTime < 5000) 1.0f else 5000f / result.executionTime
            if (result.errorOutput.isEmpty()) baseEfficiency else baseEfficiency * 0.8f
        } else 0.0f
    }
    
    private fun detectIssues(result: CodeExecutionEngine.ExecutionResult): List<Issue> {
        val issues = mutableListOf<Issue>()
        
        // 执行失败问题
        if (!result.success) {
            issues.add(Issue(
                id = UUID.randomUUID().toString(),
                type = IssueType.LOGIC,
                severity = "HIGH",
                description = "代码执行失败: ${result.errorOutput}",
                location = "执行环境",
                suggestedFix = "检查代码逻辑和语法错误",
                automatedFix = false
            ))
        }
        
        // 性能问题
        if (result.executionTime > 30000) {
            issues.add(Issue(
                id = UUID.randomUUID().toString(),
                type = IssueType.PERFORMANCE,
                severity = "MEDIUM",
                description = "执行时间过长 (${result.executionTime}ms)",
                location = "性能",
                suggestedFix = "优化算法复杂度或减少计算量",
                automatedFix = false
            ))
        }
        
        // 异常问题
        if (result.exception != null) {
            issues.add(Issue(
                id = UUID.randomUUID().toString(),
                type = IssueType.SYNTAX,
                severity = "HIGH",
                description = "执行异常: ${result.exception}",
                location = "代码执行",
                suggestedFix = "修复语法错误或处理异常情况",
                automatedFix = false
            ))
        }
        
        return issues
    }
    
    private fun generateRecommendations(
        result: CodeExecutionEngine.ExecutionResult,
        issues: List<Issue>
    ): List<Recommendation> {
        val recommendations = mutableListOf<Recommendation>()
        
        // 基于问题生成建议
        issues.forEach { issue ->
            when (issue.type) {
                IssueType.PERFORMANCE -> {
                    recommendations.add(Recommendation(
                        id = UUID.randomUUID().toString(),
                        category = "性能优化",
                        title = "优化执行效率",
                        description = "改进代码性能以减少执行时间",
                        priority = 7,
                        implementationGuide = "使用更高效的算法，减少不必要的计算",
                        expectedBenefit = "执行时间减少20-50%"
                    ))
                }
                IssueType.LOGIC -> {
                    recommendations.add(Recommendation(
                        id = UUID.randomUUID().toString(),
                        category = "逻辑修复",
                        title = "修复逻辑错误",
                        description = "解决代码逻辑问题以确保正确执行",
                        priority = 9,
                        implementationGuide = "检查条件判断和循环逻辑",
                        expectedBenefit = "提高代码正确性和可靠性"
                    ))
                }
                else -> {}
            }
        }
        
        // 通用建议
        if (result.success) {
            recommendations.add(Recommendation(
                id = UUID.randomUUID().toString(),
                category = "代码质量",
                title = "添加错误处理",
                description = "增强代码的错误处理机制",
                priority = 5,
                implementationGuide = "添加try-catch块和输入验证",
                expectedBenefit = "提高代码健壮性"
            ))
        }
        
        return recommendations
    }
    
    private fun createVisualizations(
        result: CodeExecutionEngine.ExecutionResult,
        metrics: PerformanceMetrics
    ): List<Visualization> {
        val visualizations = mutableListOf<Visualization>()
        
        // 执行时间图表
        visualizations.add(Visualization(
            id = UUID.randomUUID().toString(),
            type = "bar_chart",
            title = "执行时间分析",
            data = mapOf(
                "execution_time" to result.executionTime,
                "average_time" to 10000L,
                "benchmark" to 5000L
            ),
            config = mapOf(
                "unit" to "毫秒",
                "color" to if (result.executionTime < 10000) "green" else "orange"
            )
        ))
        
        // 资源使用图表
        visualizations.add(Visualization(
            id = UUID.randomUUID().toString(),
            type = "pie_chart",
            title = "资源使用分布",
            data = mapOf(
                "memory" to metrics.memoryUsageMB,
                "cpu" to metrics.cpuUsagePercent,
                "disk" to metrics.diskIOKB.toFloat() / 1024
            )
        ))
        
        return visualizations
    }
    
    private fun generateSummary(
        result: CodeExecutionEngine.ExecutionResult,
        quality: ResultQuality,
        issues: List<Issue>
    ): String {
        val summary = StringBuilder()
        
        summary.appendLine("📊 执行结果分析摘要")
        summary.appendLine("━━━━━━━━━━━━━━━━━━━━")
        
        // 基本状态
        summary.appendLine("✅ 执行状态: ${if (result.success) "成功" else "失败"}")
        summary.appendLine("🎯 质量评级: ${getQualityDescription(quality)}")
        summary.appendLine("⏱️ 执行时间: ${result.executionTime}ms")
        
        // 问题统计
        if (issues.isNotEmpty()) {
            summary.appendLine("\n⚠️ 检测到问题:")
            val issueGroups = issues.groupBy { it.severity }
            issueGroups.forEach { (severity, issueList) ->
                summary.appendLine("  ${getSeverityIcon(severity)} $severity: ${issueList.size}个")
            }
        }
        
        // 输出统计
        if (result.output.isNotEmpty()) {
            summary.appendLine("\n📄 输出行数: ${result.output.lines().size}")
        }
        
        if (result.generatedFiles.isNotEmpty()) {
            summary.appendLine("📁 生成文件: ${result.generatedFiles.size}个")
        }
        
        return summary.toString()
    }
    
    private fun getQualityDescription(quality: ResultQuality): String {
        return when (quality) {
            ResultQuality.EXCELLENT -> "优秀 🌟"
            ResultQuality.GOOD -> "良好 👍"
            ResultQuality.FAIR -> "一般 ⚠️"
            ResultQuality.POOR -> "较差 ❌"
            ResultQuality.CRITICAL -> "严重 🚨"
        }
    }
    
    private fun getSeverityIcon(severity: String): String {
        return when (severity.uppercase()) {
            "CRITICAL" -> "🚨"
            "HIGH" -> "❌"
            "MEDIUM" -> "⚠️"
            "LOW" -> "ℹ️"
            else -> "📝"
        }
    }
}