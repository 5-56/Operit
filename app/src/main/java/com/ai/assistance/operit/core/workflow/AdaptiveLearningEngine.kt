package com.ai.assistance.operit.core.workflow

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

/**
 * 模块5: 自适应学习与优化系统
 * 
 * - 记录每次任务执行的成功率和用户反馈
 * - 基于历史数据优化代码生成策略
 * - 学习用户偏好和常用模式
 * - 持续改进任务分解和执行效率
 */
class AdaptiveLearningEngine private constructor(private val context: Context) {
    
    companion object {
        private const TAG = "AdaptiveLearningEngine"
        private const PREFS_NAME = "adaptive_learning_prefs"
        
        @Volatile
        private var INSTANCE: AdaptiveLearningEngine? = null
        
        fun getInstance(context: Context): AdaptiveLearningEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdaptiveLearningEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        enum class LearningState {
            IDLE, LEARNING, OPTIMIZING, COMPLETED
        }
        
        enum class PatternType {
            TASK_PREFERENCE, CODE_STYLE, EXECUTION_PATTERN, ERROR_PATTERN
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _learningState = MutableStateFlow<LearningState>(LearningState.IDLE)
    val learningState: StateFlow<LearningState> = _learningState.asStateFlow()
    
    /**
     * 执行记录
     */
    data class ExecutionRecord(
        val id: String,
        val taskDescription: String,
        val intentType: IntelligentCommandProcessor.Companion.IntentType,
        val complexityLevel: IntelligentCommandProcessor.Companion.ComplexityLevel,
        val success: Boolean,
        val executionTime: Long,
        val userFeedback: UserFeedback?,
        val generatedCode: String,
        val issues: List<String>,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * 用户反馈
     */
    data class UserFeedback(
        val satisfaction: Int, // 1-5星评分
        val usefulness: Int, // 1-5星评分
        val comments: String = "",
        val improvements: List<String> = emptyList()
    )
    
    /**
     * 学习模式
     */
    data class LearnedPattern(
        val id: String,
        val type: PatternType,
        val pattern: String,
        val confidence: Float, // 0.0 - 1.0
        val frequency: Int,
        val successRate: Float,
        val metadata: Map<String, Any> = emptyMap(),
        val lastUpdated: Long = System.currentTimeMillis()
    )
    
    /**
     * 优化建议
     */
    data class OptimizationSuggestion(
        val id: String,
        val category: String,
        val description: String,
        val expectedImprovement: String,
        val confidence: Float,
        val implementation: String
    )
    
    /**
     * 学习统计
     */
    data class LearningStatistics(
        val totalExecutions: Int,
        val successRate: Float,
        val averageExecutionTime: Long,
        val userSatisfactionAvg: Float,
        val topIntentTypes: List<Pair<IntelligentCommandProcessor.Companion.IntentType, Int>>,
        val learnedPatterns: Int,
        val optimizationsApplied: Int
    )
    
    /**
     * 记录任务执行
     */
    suspend fun recordExecution(
        taskDescription: String,
        intentType: IntelligentCommandProcessor.Companion.IntentType,
        complexityLevel: IntelligentCommandProcessor.Companion.ComplexityLevel,
        executionResult: CodeExecutionEngine.ExecutionResult,
        generatedCode: String,
        userFeedback: UserFeedback? = null
    ) {
        withContext(Dispatchers.IO) {
            _learningState.value = LearningState.LEARNING
            
            try {
                val record = ExecutionRecord(
                    id = UUID.randomUUID().toString(),
                    taskDescription = taskDescription,
                    intentType = intentType,
                    complexityLevel = complexityLevel,
                    success = executionResult.success,
                    executionTime = executionResult.executionTime,
                    userFeedback = userFeedback,
                    generatedCode = generatedCode,
                    issues = extractIssues(executionResult)
                )
                
                saveExecutionRecord(record)
                updatePatterns(record)
                Log.d(TAG, "执行记录已保存: ${record.id}")
                
                _learningState.value = LearningState.COMPLETED
                
            } catch (e: Exception) {
                Log.e(TAG, "记录执行失败", e)
                _learningState.value = LearningState.IDLE
            }
        }
    }
    
    /**
     * 优化代码生成策略
     */
    suspend fun optimizeCodeGeneration(
        taskDescription: String,
        intentType: IntelligentCommandProcessor.Companion.IntentType
    ): Map<String, Any> {
        return withContext(Dispatchers.Default) {
            _learningState.value = LearningState.OPTIMIZING
            
            try {
                val optimizations = mutableMapOf<String, Any>()
                
                // 1. 基于历史成功率的库选择
                val recommendedLibraries = getRecommendedLibraries(intentType)
                optimizations["recommended_libraries"] = recommendedLibraries
                
                // 2. 基于用户偏好的代码风格
                val codeStyle = getPreferredCodeStyle(intentType)
                optimizations["code_style"] = codeStyle
                
                // 3. 基于性能历史的优化
                val performanceOptimizations = getPerformanceOptimizations(intentType)
                optimizations["performance_optimizations"] = performanceOptimizations
                
                // 4. 常见错误避免策略
                val errorAvoidance = getErrorAvoidanceStrategies(intentType)
                optimizations["error_avoidance"] = errorAvoidance
                
                Log.d(TAG, "代码生成优化完成: $optimizations")
                optimizations
                
            } catch (e: Exception) {
                Log.e(TAG, "优化失败", e)
                emptyMap()
            } finally {
                _learningState.value = LearningState.IDLE
            }
        }
    }
    
    /**
     * 学习用户偏好
     */
    fun learnUserPreferences(feedback: List<UserFeedback>) {
        try {
            val preferences = mutableMapOf<String, Any>()
            
            // 分析满意度模式
            val avgSatisfaction = feedback.map { it.satisfaction }.average()
            preferences["average_satisfaction"] = avgSatisfaction
            
            // 分析有用性模式
            val avgUsefulness = feedback.map { it.usefulness }.average()
            preferences["average_usefulness"] = avgUsefulness
            
            // 提取改进建议模式
            val allImprovements = feedback.flatMap { it.improvements }
            val improvementCounts = allImprovements.groupingBy { it }.eachCount()
            preferences["top_improvements"] = improvementCounts.toList().sortedByDescending { it.second }.take(5)
            
            // 保存用户偏好
            saveUserPreferences(preferences)
            Log.d(TAG, "用户偏好学习完成")
            
        } catch (e: Exception) {
            Log.e(TAG, "学习用户偏好失败", e)
        }
    }
    
    /**
     * 获取学习统计
     */
    fun getLearningStatistics(): LearningStatistics {
        val records = getExecutionRecords()
        
        val totalExecutions = records.size
        val successRate = if (totalExecutions > 0) {
            records.count { it.success }.toFloat() / totalExecutions
        } else 0.0f
        
        val averageExecutionTime = if (totalExecutions > 0) {
            records.map { it.executionTime }.average().toLong()
        } else 0L
        
        val userSatisfactionAvg = records.mapNotNull { it.userFeedback?.satisfaction }
            .let { if (it.isNotEmpty()) it.average().toFloat() else 0.0f }
        
        val topIntentTypes = records.groupingBy { it.intentType }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(5)
        
        val learnedPatterns = getLearnedPatterns().size
        val optimizationsApplied = getOptimizationsApplied()
        
        return LearningStatistics(
            totalExecutions = totalExecutions,
            successRate = successRate,
            averageExecutionTime = averageExecutionTime,
            userSatisfactionAvg = userSatisfactionAvg,
            topIntentTypes = topIntentTypes,
            learnedPatterns = learnedPatterns,
            optimizationsApplied = optimizationsApplied
        )
    }
    
    /**
     * 获取优化建议
     */
    fun getOptimizationSuggestions(): List<OptimizationSuggestion> {
        val suggestions = mutableListOf<OptimizationSuggestion>()
        val records = getExecutionRecords()
        
        // 基于成功率的建议
        val successRate = records.count { it.success }.toFloat() / records.size.coerceAtLeast(1)
        if (successRate < 0.8f) {
            suggestions.add(OptimizationSuggestion(
                id = UUID.randomUUID().toString(),
                category = "代码质量",
                description = "当前成功率较低(${String.format("%.1f", successRate * 100)}%)，建议增强错误处理",
                expectedImprovement = "成功率提升15-25%",
                confidence = 0.8f,
                implementation = "添加更多的异常处理和输入验证"
            ))
        }
        
        // 基于执行时间的建议
        val avgTime = records.map { it.executionTime }.average()
        if (avgTime > 20000) {
            suggestions.add(OptimizationSuggestion(
                id = UUID.randomUUID().toString(),
                category = "性能优化",
                description = "平均执行时间较长(${avgTime.toLong()}ms)，建议优化算法",
                expectedImprovement = "执行时间减少30-50%",
                confidence = 0.7f,
                implementation = "使用更高效的数据结构和算法"
            ))
        }
        
        return suggestions
    }
    
    private fun extractIssues(result: CodeExecutionEngine.ExecutionResult): List<String> {
        val issues = mutableListOf<String>()
        
        if (!result.success) {
            issues.add("执行失败")
        }
        
        if (result.errorOutput.isNotEmpty()) {
            issues.add("错误输出: ${result.errorOutput}")
        }
        
        if (result.exception != null) {
            issues.add("异常: ${result.exception}")
        }
        
        if (result.executionTime > 30000) {
            issues.add("执行时间过长")
        }
        
        return issues
    }
    
    private fun saveExecutionRecord(record: ExecutionRecord) {
        val records = getExecutionRecords().toMutableList()
        records.add(record)
        
        // 只保留最近1000条记录
        if (records.size > 1000) {
            records.removeAt(0)
        }
        
        val jsonArray = JSONArray()
        records.forEach { record ->
            val jsonObject = JSONObject().apply {
                put("id", record.id)
                put("taskDescription", record.taskDescription)
                put("intentType", record.intentType.name)
                put("complexityLevel", record.complexityLevel.name)
                put("success", record.success)
                put("executionTime", record.executionTime)
                put("generatedCode", record.generatedCode)
                put("issues", JSONArray(record.issues))
                put("timestamp", record.timestamp)
                
                record.userFeedback?.let { feedback ->
                    put("userFeedback", JSONObject().apply {
                        put("satisfaction", feedback.satisfaction)
                        put("usefulness", feedback.usefulness)
                        put("comments", feedback.comments)
                        put("improvements", JSONArray(feedback.improvements))
                    })
                }
            }
            jsonArray.put(jsonObject)
        }
        
        prefs.edit().putString("execution_records", jsonArray.toString()).apply()
    }
    
    private fun getExecutionRecords(): List<ExecutionRecord> {
        val recordsJson = prefs.getString("execution_records", "[]") ?: "[]"
        val records = mutableListOf<ExecutionRecord>()
        
        try {
            val jsonArray = JSONArray(recordsJson)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                
                val userFeedback = if (jsonObject.has("userFeedback")) {
                    val feedbackJson = jsonObject.getJSONObject("userFeedback")
                    val improvements = mutableListOf<String>()
                    if (feedbackJson.has("improvements")) {
                        val improvementsArray = feedbackJson.getJSONArray("improvements")
                        for (j in 0 until improvementsArray.length()) {
                            improvements.add(improvementsArray.getString(j))
                        }
                    }
                    
                    UserFeedback(
                        satisfaction = feedbackJson.getInt("satisfaction"),
                        usefulness = feedbackJson.getInt("usefulness"),
                        comments = feedbackJson.optString("comments", ""),
                        improvements = improvements
                    )
                } else null
                
                val issues = mutableListOf<String>()
                if (jsonObject.has("issues")) {
                    val issuesArray = jsonObject.getJSONArray("issues")
                    for (j in 0 until issuesArray.length()) {
                        issues.add(issuesArray.getString(j))
                    }
                }
                
                records.add(ExecutionRecord(
                    id = jsonObject.getString("id"),
                    taskDescription = jsonObject.getString("taskDescription"),
                    intentType = IntelligentCommandProcessor.Companion.IntentType.valueOf(
                        jsonObject.getString("intentType")
                    ),
                    complexityLevel = IntelligentCommandProcessor.Companion.ComplexityLevel.valueOf(
                        jsonObject.getString("complexityLevel")
                    ),
                    success = jsonObject.getBoolean("success"),
                    executionTime = jsonObject.getLong("executionTime"),
                    userFeedback = userFeedback,
                    generatedCode = jsonObject.getString("generatedCode"),
                    issues = issues,
                    timestamp = jsonObject.getLong("timestamp")
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析执行记录失败", e)
        }
        
        return records
    }
    
    private fun updatePatterns(record: ExecutionRecord) {
        // 更新任务偏好模式
        updateTaskPreferencePattern(record)
        
        // 更新代码风格模式
        updateCodeStylePattern(record)
        
        // 更新执行模式
        updateExecutionPattern(record)
        
        // 更新错误模式
        if (!record.success) {
            updateErrorPattern(record)
        }
    }
    
    private fun updateTaskPreferencePattern(record: ExecutionRecord) {
        val patterns = getLearnedPatterns().toMutableList()
        val existingPattern = patterns.find { 
            it.type == PatternType.TASK_PREFERENCE && 
            it.pattern == record.intentType.name 
        }
        
        if (existingPattern != null) {
            val updatedPattern = existingPattern.copy(
                frequency = existingPattern.frequency + 1,
                successRate = calculateSuccessRate(record.intentType),
                lastUpdated = System.currentTimeMillis()
            )
            patterns[patterns.indexOf(existingPattern)] = updatedPattern
        } else {
            patterns.add(LearnedPattern(
                id = UUID.randomUUID().toString(),
                type = PatternType.TASK_PREFERENCE,
                pattern = record.intentType.name,
                confidence = 0.5f,
                frequency = 1,
                successRate = if (record.success) 1.0f else 0.0f
            ))
        }
        
        saveLearnedPatterns(patterns)
    }
    
    private fun updateCodeStylePattern(record: ExecutionRecord) {
        // 分析代码特征
        val codeFeatures = analyzeCodeFeatures(record.generatedCode)
        codeFeatures.forEach { feature ->
            val patterns = getLearnedPatterns().toMutableList()
            val existingPattern = patterns.find { 
                it.type == PatternType.CODE_STYLE && 
                it.pattern == feature 
            }
            
            if (existingPattern != null) {
                val updatedPattern = existingPattern.copy(
                    frequency = existingPattern.frequency + 1,
                    lastUpdated = System.currentTimeMillis()
                )
                patterns[patterns.indexOf(existingPattern)] = updatedPattern
            } else {
                patterns.add(LearnedPattern(
                    id = UUID.randomUUID().toString(),
                    type = PatternType.CODE_STYLE,
                    pattern = feature,
                    confidence = 0.3f,
                    frequency = 1,
                    successRate = if (record.success) 1.0f else 0.0f
                ))
            }
            
            saveLearnedPatterns(patterns)
        }
    }
    
    private fun updateExecutionPattern(record: ExecutionRecord) {
        val pattern = "${record.complexityLevel.name}_${record.executionTime / 1000}s"
        val patterns = getLearnedPatterns().toMutableList()
        val existingPattern = patterns.find { 
            it.type == PatternType.EXECUTION_PATTERN && 
            it.pattern == pattern 
        }
        
        if (existingPattern != null) {
            val updatedPattern = existingPattern.copy(
                frequency = existingPattern.frequency + 1,
                lastUpdated = System.currentTimeMillis()
            )
            patterns[patterns.indexOf(existingPattern)] = updatedPattern
        } else {
            patterns.add(LearnedPattern(
                id = UUID.randomUUID().toString(),
                type = PatternType.EXECUTION_PATTERN,
                pattern = pattern,
                confidence = 0.4f,
                frequency = 1,
                successRate = if (record.success) 1.0f else 0.0f
            ))
        }
        
        saveLearnedPatterns(patterns)
    }
    
    private fun updateErrorPattern(record: ExecutionRecord) {
        record.issues.forEach { issue ->
            val patterns = getLearnedPatterns().toMutableList()
            val existingPattern = patterns.find { 
                it.type == PatternType.ERROR_PATTERN && 
                it.pattern == issue 
            }
            
            if (existingPattern != null) {
                val updatedPattern = existingPattern.copy(
                    frequency = existingPattern.frequency + 1,
                    lastUpdated = System.currentTimeMillis()
                )
                patterns[patterns.indexOf(existingPattern)] = updatedPattern
            } else {
                patterns.add(LearnedPattern(
                    id = UUID.randomUUID().toString(),
                    type = PatternType.ERROR_PATTERN,
                    pattern = issue,
                    confidence = 0.6f,
                    frequency = 1,
                    successRate = 0.0f
                ))
            }
            
            saveLearnedPatterns(patterns)
        }
    }
    
    private fun calculateSuccessRate(intentType: IntelligentCommandProcessor.Companion.IntentType): Float {
        val records = getExecutionRecords().filter { it.intentType == intentType }
        return if (records.isNotEmpty()) {
            records.count { it.success }.toFloat() / records.size
        } else 0.0f
    }
    
    private fun analyzeCodeFeatures(code: String): List<String> {
        val features = mutableListOf<String>()
        
        if (code.contains("import pandas")) features.add("uses_pandas")
        if (code.contains("import numpy")) features.add("uses_numpy")
        if (code.contains("import matplotlib")) features.add("uses_matplotlib")
        if (code.contains("try:")) features.add("has_error_handling")
        if (code.contains("def ")) features.add("uses_functions")
        if (code.contains("class ")) features.add("uses_classes")
        if (code.lines().size > 50) features.add("long_code")
        if (code.contains("# ")) features.add("well_commented")
        
        return features
    }
    
    private fun getRecommendedLibraries(intentType: IntelligentCommandProcessor.Companion.IntentType): List<String> {
        val records = getExecutionRecords().filter { 
            it.intentType == intentType && it.success 
        }
        
        val libraryUsage = mutableMapOf<String, Int>()
        records.forEach { record ->
            if (record.generatedCode.contains("import pandas")) libraryUsage["pandas"] = libraryUsage.getOrDefault("pandas", 0) + 1
            if (record.generatedCode.contains("import numpy")) libraryUsage["numpy"] = libraryUsage.getOrDefault("numpy", 0) + 1
            if (record.generatedCode.contains("import matplotlib")) libraryUsage["matplotlib"] = libraryUsage.getOrDefault("matplotlib", 0) + 1
            if (record.generatedCode.contains("import requests")) libraryUsage["requests"] = libraryUsage.getOrDefault("requests", 0) + 1
        }
        
        return libraryUsage.toList().sortedByDescending { it.second }.take(3).map { it.first }
    }
    
    private fun getPreferredCodeStyle(intentType: IntelligentCommandProcessor.Companion.IntentType): Map<String, Any> {
        val patterns = getLearnedPatterns().filter { 
            it.type == PatternType.CODE_STYLE 
        }.sortedByDescending { it.frequency }
        
        return mapOf(
            "preferred_features" to patterns.take(5).map { it.pattern },
            "complexity_preference" to getComplexityPreference(intentType)
        )
    }
    
    private fun getComplexityPreference(intentType: IntelligentCommandProcessor.Companion.IntentType): String {
        val records = getExecutionRecords().filter { 
            it.intentType == intentType && it.success 
        }
        
        val complexityCount = records.groupingBy { it.complexityLevel }.eachCount()
        return complexityCount.maxByOrNull { it.value }?.key?.name ?: "MODERATE"
    }
    
    private fun getPerformanceOptimizations(intentType: IntelligentCommandProcessor.Companion.IntentType): List<String> {
        val optimizations = mutableListOf<String>()
        
        val records = getExecutionRecords().filter { it.intentType == intentType }
        val avgTime = records.map { it.executionTime }.average()
        
        if (avgTime > 15000) {
            optimizations.add("减少循环复杂度")
            optimizations.add("使用更高效的数据结构")
        }
        
        if (avgTime > 30000) {
            optimizations.add("并行处理")
            optimizations.add("缓存计算结果")
        }
        
        return optimizations
    }
    
    private fun getErrorAvoidanceStrategies(intentType: IntelligentCommandProcessor.Companion.IntentType): List<String> {
        val errorPatterns = getLearnedPatterns().filter { 
            it.type == PatternType.ERROR_PATTERN 
        }.sortedByDescending { it.frequency }
        
        return errorPatterns.take(3).map { pattern ->
            when {
                pattern.pattern.contains("执行失败") -> "添加更多的异常处理"
                pattern.pattern.contains("执行时间过长") -> "设置合理的超时时间"
                pattern.pattern.contains("错误输出") -> "增强输入验证"
                else -> "改进代码质量"
            }
        }
    }
    
    private fun saveLearnedPatterns(patterns: List<LearnedPattern>) {
        val jsonArray = JSONArray()
        patterns.forEach { pattern ->
            val jsonObject = JSONObject().apply {
                put("id", pattern.id)
                put("type", pattern.type.name)
                put("pattern", pattern.pattern)
                put("confidence", pattern.confidence)
                put("frequency", pattern.frequency)
                put("successRate", pattern.successRate)
                put("lastUpdated", pattern.lastUpdated)
            }
            jsonArray.put(jsonObject)
        }
        
        prefs.edit().putString("learned_patterns", jsonArray.toString()).apply()
    }
    
    private fun getLearnedPatterns(): List<LearnedPattern> {
        val patternsJson = prefs.getString("learned_patterns", "[]") ?: "[]"
        val patterns = mutableListOf<LearnedPattern>()
        
        try {
            val jsonArray = JSONArray(patternsJson)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                patterns.add(LearnedPattern(
                    id = jsonObject.getString("id"),
                    type = PatternType.valueOf(jsonObject.getString("type")),
                    pattern = jsonObject.getString("pattern"),
                    confidence = jsonObject.getDouble("confidence").toFloat(),
                    frequency = jsonObject.getInt("frequency"),
                    successRate = jsonObject.getDouble("successRate").toFloat(),
                    lastUpdated = jsonObject.getLong("lastUpdated")
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析学习模式失败", e)
        }
        
        return patterns
    }
    
    private fun saveUserPreferences(preferences: Map<String, Any>) {
        val jsonObject = JSONObject(preferences)
        prefs.edit().putString("user_preferences", jsonObject.toString()).apply()
    }
    
    private fun getOptimizationsApplied(): Int {
        return prefs.getInt("optimizations_applied", 0)
    }
}