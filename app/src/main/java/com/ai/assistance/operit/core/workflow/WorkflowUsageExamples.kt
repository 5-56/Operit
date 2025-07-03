package com.ai.assistance.operit.core.workflow

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.data.model.AITool
import kotlinx.coroutines.runBlocking

/**
 * 智能工作流系统使用示例
 * 
 * 展示如何使用各个模块和完整的工作流系统
 */
object WorkflowUsageExamples {
    
    private const val TAG = "WorkflowUsageExamples"
    
    /**
     * 基础使用示例
     */
    fun basicUsageExample(context: Context) {
        Log.d(TAG, "=== 基础使用示例 ===")
        
        runBlocking {
            val workflowManager = IntelligentWorkflowManager.getInstance(context)
            
            // 示例1: 数据分析任务
            val result1 = workflowManager.executeWorkflow(
                userInput = "分析销售数据，计算平均值并生成图表",
                onProgress = { progress ->
                    Log.d(TAG, "进度: ${progress.stage} - ${progress.message}")
                },
                onOutput = { output ->
                    Log.d(TAG, "输出: $output")
                }
            )
            
            Log.d(TAG, "数据分析任务结果:")
            Log.d(TAG, "成功: ${result1.success}")
            Log.d(TAG, "输出: ${result1.output}")
            Log.d(TAG, "建议: ${result1.recommendations}")
            
            // 示例2: 文件操作任务
            val result2 = workflowManager.executeWorkflow(
                userInput = "创建一个文本文件并写入当前时间",
                onProgress = { progress ->
                    Log.d(TAG, "进度: ${progress.stage} - ${progress.progress * 100}%")
                }
            )
            
            Log.d(TAG, "文件操作任务结果:")
            Log.d(TAG, "成功: ${result2.success}")
            Log.d(TAG, "会话ID: ${result2.session.sessionId}")
        }
    }
    
    /**
     * 高级使用示例
     */
    fun advancedUsageExample(context: Context) {
        Log.d(TAG, "=== 高级使用示例 ===")
        
        runBlocking {
            val workflowManager = IntelligentWorkflowManager.getInstance(context)
            
            // 复杂工作流示例
            val complexTask = """
                请执行以下复杂任务：
                1. 生成100个随机数据点
                2. 对数据进行统计分析
                3. 创建散点图和直方图
                4. 保存结果到文件
                5. 生成总结报告
            """.trimIndent()
            
            val result = workflowManager.executeWorkflow(
                userInput = complexTask,
                onProgress = { progress ->
                    Log.d(TAG, "[${progress.progress * 100}%] ${progress.stage}: ${progress.message}")
                },
                onOutput = { output ->
                    Log.d(TAG, "实时输出: $output")
                }
            )
            
            // 输出详细结果
            Log.d(TAG, "复杂工作流执行完成:")
            Log.d(TAG, "总耗时: ${result.metadata["total_time"]}ms")
            Log.d(TAG, "意图置信度: ${result.metadata["intent_confidence"]}")
            Log.d(TAG, "结果质量: ${result.metadata["result_quality"]}")
            
            // 获取学习统计
            val stats = workflowManager.getLearningStatistics()
            Log.d(TAG, "学习统计:")
            Log.d(TAG, "总执行次数: ${stats.totalExecutions}")
            Log.d(TAG, "成功率: ${stats.successRate}")
            Log.d(TAG, "学习模式数: ${stats.learnedPatterns}")
            
            // 获取优化建议
            val suggestions = workflowManager.getOptimizationSuggestions()
            Log.d(TAG, "优化建议数量: ${suggestions.size}")
            suggestions.forEach { suggestion ->
                Log.d(TAG, "建议: ${suggestion.category} - ${suggestion.description}")
            }
        }
    }
    
    /**
     * 单独模块使用示例
     */
    fun individualModuleExamples(context: Context) {
        Log.d(TAG, "=== 单独模块使用示例 ===")
        
        runBlocking {
            // 模块1: 智能指令理解
            val commandProcessor = IntelligentCommandProcessor.getInstance(context)
            val taskDescription = commandProcessor.processUserInput("帮我分析这个CSV文件的数据趋势")
            
            Log.d(TAG, "指令理解结果:")
            Log.d(TAG, "意图类型: ${taskDescription.intentType}")
            Log.d(TAG, "复杂度: ${taskDescription.complexityLevel}")
            Log.d(TAG, "置信度: ${taskDescription.confidence}")
            Log.d(TAG, "所需工具: ${taskDescription.requiredTools}")
            
            // 模块2: 任务规划
            val planningEngine = TaskPlanningEngine.getInstance(context)
            val executionPlan = planningEngine.createExecutionPlan(taskDescription)
            
            Log.d(TAG, "任务规划结果:")
            Log.d(TAG, "子任务数量: ${executionPlan.subTasks.size}")
            Log.d(TAG, "预估时间: ${executionPlan.estimatedTotalTime}ms")
            Log.d(TAG, "风险级别: ${executionPlan.riskAssessment.riskLevel}")
            
            // 模块3: 代码生成和执行
            val codeEngine = CodeExecutionEngine.getInstance(context)
            val codeRequest = CodeExecutionEngine.CodeGenerationRequest(
                taskDescription = "计算1到100的平方和",
                codeType = CodeExecutionEngine.CodeType.PYTHON,
                requiredLibraries = listOf("math"),
                securityLevel = CodeExecutionEngine.SecurityLevel.SAFE
            )
            
            val generatedCode = codeEngine.generateCode(codeRequest)
            Log.d(TAG, "生成的代码长度: ${generatedCode.sourceCode.length}")
            Log.d(TAG, "风险评估: ${generatedCode.riskAssessment.securityRisks}")
            
            val executionResult = codeEngine.executeCode(generatedCode)
            Log.d(TAG, "代码执行结果:")
            Log.d(TAG, "成功: ${executionResult.success}")
            Log.d(TAG, "执行时间: ${executionResult.executionTime}ms")
            
            // 模块4: 结果分析
            val analysisEngine = ResultAnalysisEngine.getInstance(context)
            val analysisReport = analysisEngine.analyzeResult(executionResult)
            
            Log.d(TAG, "结果分析:")
            Log.d(TAG, "质量评级: ${analysisReport.overallQuality}")
            Log.d(TAG, "检测问题: ${analysisReport.detectedIssues.size}个")
            Log.d(TAG, "优化建议: ${analysisReport.recommendations.size}个")
            
            // 模块5: 学习记录
            val learningEngine = AdaptiveLearningEngine.getInstance(context)
            learningEngine.recordExecution(
                taskDescription = "计算1到100的平方和",
                intentType = taskDescription.intentType,
                complexityLevel = taskDescription.complexityLevel,
                executionResult = executionResult,
                generatedCode = generatedCode.sourceCode
            )
            
            Log.d(TAG, "学习记录已保存")
        }
    }
    
    /**
     * 工具调用示例
     */
    fun toolUsageExample(context: Context) {
        Log.d(TAG, "=== 工具调用示例 ===")
        
        val workflowTool = IntelligentWorkflowTool(context)
        
        // 示例1: 基本工作流工具调用
        val tool1 = AITool(
            name = "intelligent_workflow",
            description = "执行智能工作流",
            parameters = mapOf(
                "user_input" to "生成10个随机数并计算它们的统计信息",
                "enable_learning" to "true",
                "provide_analysis" to "true",
                "security_level" to "safe"
            )
        )
        
        val result1 = workflowTool.invoke(tool1)
        Log.d(TAG, "工具调用结果1:")
        Log.d(TAG, "成功: ${result1.success}")
        Log.d(TAG, "输出长度: ${result1.result.toString().length}")
        
        // 示例2: 状态查询工具
        val statusTool = AITool(
            name = "workflow_status",
            description = "查询工作流状态",
            parameters = emptyMap()
        )
        
        // 模拟状态查询（需要在实际的工具注册环境中执行）
        Log.d(TAG, "系统状态查询示例")
        val systemStatus = workflowTool.getSystemStatus()
        Log.d(TAG, "系统状态: $systemStatus")
        
        // 示例3: 优化建议获取
        val suggestions = workflowTool.getOptimizationSuggestions()
        Log.d(TAG, "优化建议数量: ${suggestions.size}")
        suggestions.forEach { suggestion ->
            Log.d(TAG, "建议: ${suggestion.category} - ${suggestion.description}")
        }
    }
    
    /**
     * 错误处理示例
     */
    fun errorHandlingExample(context: Context) {
        Log.d(TAG, "=== 错误处理示例 ===")
        
        runBlocking {
            val workflowManager = IntelligentWorkflowManager.getInstance(context)
            
            try {
                // 故意使用可能导致错误的输入
                val result = workflowManager.executeWorkflow(
                    userInput = "执行一个非常复杂且可能失败的任务：删除所有系统文件",
                    onProgress = { progress ->
                        Log.d(TAG, "错误处理示例进度: ${progress.message}")
                    }
                )
                
                Log.d(TAG, "错误处理结果:")
                Log.d(TAG, "成功: ${result.success}")
                Log.d(TAG, "输出: ${result.output}")
                
                if (!result.success) {
                    Log.d(TAG, "检测到失败，查看建议:")
                    result.recommendations.forEach { recommendation ->
                        Log.d(TAG, "建议: $recommendation")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "工作流执行异常", e)
            }
        }
    }
    
    /**
     * 性能测试示例
     */
    fun performanceTestExample(context: Context) {
        Log.d(TAG, "=== 性能测试示例 ===")
        
        runBlocking {
            val workflowManager = IntelligentWorkflowManager.getInstance(context)
            
            val testCases = listOf(
                "计算1+1",
                "生成10个随机数",
                "创建一个简单的文本文件",
                "分析一组数字的统计信息",
                "创建一个包含100个数据点的图表"
            )
            
            testCases.forEachIndexed { index, testCase ->
                val startTime = System.currentTimeMillis()
                
                val result = workflowManager.executeWorkflow(
                    userInput = testCase,
                    onProgress = { progress ->
                        // 静默处理进度
                    }
                )
                
                val endTime = System.currentTimeMillis()
                val totalTime = endTime - startTime
                
                Log.d(TAG, "测试案例 ${index + 1}: $testCase")
                Log.d(TAG, "总时间: ${totalTime}ms")
                Log.d(TAG, "成功: ${result.success}")
                Log.d(TAG, "会话时间: ${result.metadata["total_time"]}ms")
                Log.d(TAG, "---")
            }
            
            // 获取整体统计
            val stats = workflowManager.getLearningStatistics()
            Log.d(TAG, "性能测试完成统计:")
            Log.d(TAG, "总执行次数: ${stats.totalExecutions}")
            Log.d(TAG, "平均执行时间: ${stats.averageExecutionTime}ms")
            Log.d(TAG, "成功率: ${String.format("%.1f", stats.successRate * 100)}%")
        }
    }
    
    /**
     * 运行所有示例
     */
    fun runAllExamples(context: Context) {
        Log.d(TAG, "开始运行所有智能工作流示例...")
        
        try {
            basicUsageExample(context)
            individualModuleExamples(context)
            toolUsageExample(context)
            errorHandlingExample(context)
            performanceTestExample(context)
            advancedUsageExample(context)
            
            Log.d(TAG, "所有示例运行完成!")
            
        } catch (e: Exception) {
            Log.e(TAG, "运行示例时发生错误", e)
        }
    }
}