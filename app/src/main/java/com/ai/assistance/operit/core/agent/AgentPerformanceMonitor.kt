package com.ai.assistance.operit.core.agent

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Agent执行统计数据
 */
data class AgentExecutionStats(
    val totalExecutions: Int = 0,
    val successfulExecutions: Int = 0,
    val failedExecutions: Int = 0,
    val averageExecutionTime: Long = 0L,
    val totalExecutionTime: Long = 0L,
    val mostUsedStepTypes: Map<AgentStepType, Int> = emptyMap(),
    val commonErrorTypes: Map<String, Int> = emptyMap(),
    val popularTaskCategories: Map<String, Int> = emptyMap(),
    val lastExecutionTime: Long = 0L
)

/**
 * Agent执行记录
 */
data class AgentExecutionRecord(
    val id: String,
    val startTime: Long,
    val endTime: Long,
    val duration: Long,
    val success: Boolean,
    val planTitle: String,
    val stepCount: Int,
    val completedSteps: Int,
    val errorMessage: String? = null,
    val taskCategory: String? = null
)

/**
 * Agent性能监控器 - 跟踪和分析Agent执行性能
 */
class AgentPerformanceMonitor(private val context: Context) {
    
    companion object {
        private const val TAG = "AgentPerformanceMonitor"
        private const val STATS_FILE = "agent_performance_stats.json"
        private const val RECORDS_FILE = "agent_execution_records.json"
        private const val MAX_RECORDS = 1000 // 最多保存1000条记录
        
        @Volatile
        private var INSTANCE: AgentPerformanceMonitor? = null
        
        fun getInstance(context: Context): AgentPerformanceMonitor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AgentPerformanceMonitor(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // 统计数据
    private val _stats = MutableStateFlow(AgentExecutionStats())
    val stats: StateFlow<AgentExecutionStats> = _stats.asStateFlow()
    
    // 执行记录
    private val executionRecords = mutableListOf<AgentExecutionRecord>()
    
    // 当前执行跟踪
    private val currentExecutions = ConcurrentHashMap<String, Long>() // planId -> startTime
    
    // 计数器
    private val totalExecutions = AtomicInteger(0)
    private val successfulExecutions = AtomicInteger(0)
    private val failedExecutions = AtomicInteger(0)
    private val totalExecutionTime = AtomicLong(0L)
    
    // 统计映射
    private val stepTypeCount = ConcurrentHashMap<AgentStepType, AtomicInteger>()
    private val errorTypeCount = ConcurrentHashMap<String, AtomicInteger>()
    private val taskCategoryCount = ConcurrentHashMap<String, AtomicInteger>()
    
    init {
        loadStats()
        loadRecords()
    }
    
    /**
     * 开始跟踪Agent执行
     */
    fun startExecution(planId: String) {
        val startTime = System.currentTimeMillis()
        currentExecutions[planId] = startTime
        Log.d(TAG, "开始跟踪Agent执行: $planId")
    }
    
    /**
     * 记录步骤执行
     */
    fun recordStepExecution(planId: String, stepType: AgentStepType) {
        stepTypeCount.computeIfAbsent(stepType) { AtomicInteger(0) }.incrementAndGet()
        updateStats()
    }
    
    /**
     * 记录执行错误
     */
    fun recordError(planId: String, errorMessage: String) {
        val errorType = extractErrorType(errorMessage)
        errorTypeCount.computeIfAbsent(errorType) { AtomicInteger(0) }.incrementAndGet()
        updateStats()
    }
    
    /**
     * 完成Agent执行跟踪
     */
    fun completeExecution(
        planId: String,
        plan: AgentPlan,
        success: Boolean,
        errorMessage: String? = null
    ) {
        val startTime = currentExecutions.remove(planId) ?: return
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        // 更新计数器
        totalExecutions.incrementAndGet()
        if (success) {
            successfulExecutions.incrementAndGet()
        } else {
            failedExecutions.incrementAndGet()
        }
        totalExecutionTime.addAndGet(duration)
        
        // 记录任务类别
        val taskCategory = detectTaskCategory(plan.description)
        if (taskCategory != null) {
            taskCategoryCount.computeIfAbsent(taskCategory) { AtomicInteger(0) }.incrementAndGet()
        }
        
        // 创建执行记录
        val record = AgentExecutionRecord(
            id = planId,
            startTime = startTime,
            endTime = endTime,
            duration = duration,
            success = success,
            planTitle = plan.title,
            stepCount = plan.steps.size,
            completedSteps = plan.steps.count { it.status == AgentStepStatus.COMPLETED },
            errorMessage = errorMessage,
            taskCategory = taskCategory
        )
        
        // 添加记录
        synchronized(executionRecords) {
            executionRecords.add(record)
            // 保持记录数量在限制内
            if (executionRecords.size > MAX_RECORDS) {
                executionRecords.removeAt(0)
            }
        }
        
        updateStats()
        saveStats()
        saveRecords()
        
        Log.d(TAG, "完成Agent执行跟踪: $planId, 成功: $success, 耗时: ${duration}ms")
    }
    
    /**
     * 获取执行记录
     */
    fun getExecutionRecords(limit: Int = 50): List<AgentExecutionRecord> {
        return synchronized(executionRecords) {
            executionRecords.takeLast(limit).reversed()
        }
    }
    
    /**
     * 获取性能趋势数据
     */
    fun getPerformanceTrends(days: Int = 7): Map<String, List<Float>> {
        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L
        val startTime = now - (days * dayMs)
        
        val trends = mutableMapOf<String, MutableList<Float>>()
        
        // 按天统计
        for (day in 0 until days) {
            val dayStart = startTime + (day * dayMs)
            val dayEnd = dayStart + dayMs
            
            val dayRecords = executionRecords.filter { record ->
                record.startTime >= dayStart && record.startTime < dayEnd
            }
            
            // 成功率
            val successRate = if (dayRecords.isEmpty()) 0f else {
                dayRecords.count { it.success }.toFloat() / dayRecords.size
            }
            trends.getOrPut("successRate") { mutableListOf() }.add(successRate)
            
            // 平均执行时间
            val avgTime = if (dayRecords.isEmpty()) 0f else {
                dayRecords.map { it.duration }.average().toFloat()
            }
            trends.getOrPut("avgExecutionTime") { mutableListOf() }.add(avgTime)
            
            // 执行数量
            trends.getOrPut("executionCount") { mutableListOf() }.add(dayRecords.size.toFloat())
        }
        
        return trends.mapValues { it.value.toList() }
    }
    
    /**
     * 获取最受欢迎的任务类型
     */
    fun getPopularTaskTypes(limit: Int = 10): List<Pair<String, Int>> {
        return taskCategoryCount.entries
            .sortedByDescending { it.value.get() }
            .take(limit)
            .map { it.key to it.value.get() }
    }
    
    /**
     * 获取常见错误类型
     */
    fun getCommonErrors(limit: Int = 10): List<Pair<String, Int>> {
        return errorTypeCount.entries
            .sortedByDescending { it.value.get() }
            .take(limit)
            .map { it.key to it.value.get() }
    }
    
    /**
     * 获取步骤类型使用统计
     */
    fun getStepTypeUsage(): Map<AgentStepType, Int> {
        return stepTypeCount.mapValues { it.value.get() }
    }
    
    /**
     * 重置统计数据
     */
    fun resetStats() {
        totalExecutions.set(0)
        successfulExecutions.set(0)
        failedExecutions.set(0)
        totalExecutionTime.set(0L)
        
        stepTypeCount.clear()
        errorTypeCount.clear()
        taskCategoryCount.clear()
        
        synchronized(executionRecords) {
            executionRecords.clear()
        }
        
        updateStats()
        saveStats()
        saveRecords()
        
        Log.d(TAG, "已重置Agent性能统计数据")
    }
    
    /**
     * 更新统计数据
     */
    private fun updateStats() {
        val total = totalExecutions.get()
        val successful = successfulExecutions.get()
        val failed = failedExecutions.get()
        val totalTime = totalExecutionTime.get()
        
        val avgTime = if (total > 0) totalTime / total else 0L
        
        val newStats = AgentExecutionStats(
            totalExecutions = total,
            successfulExecutions = successful,
            failedExecutions = failed,
            averageExecutionTime = avgTime,
            totalExecutionTime = totalTime,
            mostUsedStepTypes = stepTypeCount.mapValues { it.value.get() },
            commonErrorTypes = errorTypeCount.mapValues { it.value.get() },
            popularTaskCategories = taskCategoryCount.mapValues { it.value.get() },
            lastExecutionTime = System.currentTimeMillis()
        )
        
        _stats.value = newStats
    }
    
    /**
     * 从错误消息中提取错误类型
     */
    private fun extractErrorType(errorMessage: String): String {
        return when {
            errorMessage.contains("网络", ignoreCase = true) -> "网络错误"
            errorMessage.contains("文件", ignoreCase = true) -> "文件操作错误"
            errorMessage.contains("权限", ignoreCase = true) -> "权限错误"
            errorMessage.contains("超时", ignoreCase = true) -> "超时错误"
            errorMessage.contains("脚本", ignoreCase = true) -> "脚本执行错误"
            errorMessage.contains("解析", ignoreCase = true) -> "数据解析错误"
            else -> "其他错误"
        }
    }
    
    /**
     * 检测任务类别
     */
    private fun detectTaskCategory(description: String): String? {
        val lowerDesc = description.lowercase()
        return when {
            lowerDesc.contains("文件") || lowerDesc.contains("整理") || lowerDesc.contains("清理") -> "文件管理"
            lowerDesc.contains("系统") || lowerDesc.contains("监控") || lowerDesc.contains("性能") -> "系统监控"
            lowerDesc.contains("网络") || lowerDesc.contains("下载") || lowerDesc.contains("api") -> "网络操作"
            lowerDesc.contains("数据") || lowerDesc.contains("分析") || lowerDesc.contains("处理") -> "数据处理"
            lowerDesc.contains("应用") || lowerDesc.contains("安装") || lowerDesc.contains("apk") -> "应用管理"
            lowerDesc.contains("代码") || lowerDesc.contains("开发") || lowerDesc.contains("调试") -> "开发辅助"
            else -> null
        }
    }
    
    /**
     * 保存统计数据
     */
    private fun saveStats() {
        try {
            val jsonObject = JSONObject().apply {
                put("totalExecutions", totalExecutions.get())
                put("successfulExecutions", successfulExecutions.get())
                put("failedExecutions", failedExecutions.get())
                put("totalExecutionTime", totalExecutionTime.get())
                
                // 步骤类型统计
                val stepTypes = JSONObject()
                stepTypeCount.forEach { (type, count) ->
                    stepTypes.put(type.name, count.get())
                }
                put("stepTypeCount", stepTypes)
                
                // 错误类型统计
                val errorTypes = JSONObject()
                errorTypeCount.forEach { (type, count) ->
                    errorTypes.put(type, count.get())
                }
                put("errorTypeCount", errorTypes)
                
                // 任务类别统计
                val taskCategories = JSONObject()
                taskCategoryCount.forEach { (category, count) ->
                    taskCategories.put(category, count.get())
                }
                put("taskCategoryCount", taskCategories)
                
                put("lastSaveTime", System.currentTimeMillis())
            }
            
            val file = File(context.filesDir, STATS_FILE)
            file.writeText(jsonObject.toString())
            
        } catch (e: Exception) {
            Log.e(TAG, "保存统计数据失败", e)
        }
    }
    
    /**
     * 加载统计数据
     */
    private fun loadStats() {
        try {
            val file = File(context.filesDir, STATS_FILE)
            if (file.exists()) {
                val jsonContent = file.readText()
                val jsonObject = JSONObject(jsonContent)
                
                totalExecutions.set(jsonObject.optInt("totalExecutions", 0))
                successfulExecutions.set(jsonObject.optInt("successfulExecutions", 0))
                failedExecutions.set(jsonObject.optInt("failedExecutions", 0))
                totalExecutionTime.set(jsonObject.optLong("totalExecutionTime", 0L))
                
                // 加载步骤类型统计
                val stepTypes = jsonObject.optJSONObject("stepTypeCount")
                if (stepTypes != null) {
                    stepTypes.keys().forEach { key ->
                        try {
                            val stepType = AgentStepType.valueOf(key)
                            stepTypeCount[stepType] = AtomicInteger(stepTypes.getInt(key))
                        } catch (e: Exception) {
                            Log.w(TAG, "无法解析步骤类型: $key")
                        }
                    }
                }
                
                // 加载错误类型统计
                val errorTypes = jsonObject.optJSONObject("errorTypeCount")
                if (errorTypes != null) {
                    errorTypes.keys().forEach { key ->
                        errorTypeCount[key] = AtomicInteger(errorTypes.getInt(key))
                    }
                }
                
                // 加载任务类别统计
                val taskCategories = jsonObject.optJSONObject("taskCategoryCount")
                if (taskCategories != null) {
                    taskCategories.keys().forEach { key ->
                        taskCategoryCount[key] = AtomicInteger(taskCategories.getInt(key))
                    }
                }
                
                updateStats()
                Log.d(TAG, "加载统计数据成功")
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载统计数据失败", e)
        }
    }
    
    /**
     * 保存执行记录
     */
    private fun saveRecords() {
        try {
            val jsonArray = JSONArray()
            
            synchronized(executionRecords) {
                executionRecords.forEach { record ->
                    val jsonObject = JSONObject().apply {
                        put("id", record.id)
                        put("startTime", record.startTime)
                        put("endTime", record.endTime)
                        put("duration", record.duration)
                        put("success", record.success)
                        put("planTitle", record.planTitle)
                        put("stepCount", record.stepCount)
                        put("completedSteps", record.completedSteps)
                        put("errorMessage", record.errorMessage ?: "")
                        put("taskCategory", record.taskCategory ?: "")
                    }
                    jsonArray.put(jsonObject)
                }
            }
            
            val file = File(context.filesDir, RECORDS_FILE)
            file.writeText(jsonArray.toString())
            
        } catch (e: Exception) {
            Log.e(TAG, "保存执行记录失败", e)
        }
    }
    
    /**
     * 加载执行记录
     */
    private fun loadRecords() {
        try {
            val file = File(context.filesDir, RECORDS_FILE)
            if (file.exists()) {
                val jsonContent = file.readText()
                val jsonArray = JSONArray(jsonContent)
                
                synchronized(executionRecords) {
                    executionRecords.clear()
                    
                    for (i in 0 until jsonArray.length()) {
                        val jsonObject = jsonArray.getJSONObject(i)
                        val record = AgentExecutionRecord(
                            id = jsonObject.getString("id"),
                            startTime = jsonObject.getLong("startTime"),
                            endTime = jsonObject.getLong("endTime"),
                            duration = jsonObject.getLong("duration"),
                            success = jsonObject.getBoolean("success"),
                            planTitle = jsonObject.getString("planTitle"),
                            stepCount = jsonObject.getInt("stepCount"),
                            completedSteps = jsonObject.getInt("completedSteps"),
                            errorMessage = jsonObject.optString("errorMessage").takeIf { it.isNotEmpty() },
                            taskCategory = jsonObject.optString("taskCategory").takeIf { it.isNotEmpty() }
                        )
                        executionRecords.add(record)
                    }
                }
                
                Log.d(TAG, "加载了 ${executionRecords.size} 条执行记录")
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载执行记录失败", e)
        }
    }
}