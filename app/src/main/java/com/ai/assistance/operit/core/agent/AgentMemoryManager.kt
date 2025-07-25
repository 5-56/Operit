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

/**
 * 用户偏好设置
 */
data class UserPreference(
    val key: String,
    val value: String,
    val category: String,
    val confidence: Float = 1.0f, // 置信度 0.0-1.0
    val lastUpdated: Long = System.currentTimeMillis(),
    val usageCount: Int = 1
)

/**
 * 任务执行经验
 */
data class TaskExperience(
    val taskType: String,
    val successPattern: String,
    val failurePattern: String,
    val optimizationTips: List<String>,
    val averageExecutionTime: Long,
    val successRate: Float,
    val lastUsed: Long = System.currentTimeMillis()
)

/**
 * 上下文记忆
 */
data class ContextMemory(
    val sessionId: String,
    val taskHistory: List<String>,
    val userIntent: String,
    val currentContext: Map<String, String>,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Agent记忆管理器 - 管理Agent的学习和记忆功能
 */
class AgentMemoryManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AgentMemoryManager"
        private const val PREFERENCES_FILE = "agent_user_preferences.json"
        private const val EXPERIENCES_FILE = "agent_task_experiences.json"
        private const val CONTEXT_MEMORY_FILE = "agent_context_memory.json"
        private const val MAX_CONTEXT_SESSIONS = 100
        private const val MAX_EXPERIENCES = 500
        
        @Volatile
        private var INSTANCE: AgentMemoryManager? = null
        
        fun getInstance(context: Context): AgentMemoryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AgentMemoryManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // 用户偏好
    private val userPreferences = ConcurrentHashMap<String, UserPreference>()
    private val _preferencesFlow = MutableStateFlow<Map<String, UserPreference>>(emptyMap())
    val preferencesFlow: StateFlow<Map<String, UserPreference>> = _preferencesFlow.asStateFlow()
    
    // 任务经验
    private val taskExperiences = ConcurrentHashMap<String, TaskExperience>()
    
    // 上下文记忆
    private val contextMemories = mutableListOf<ContextMemory>()
    
    // 当前会话
    private var currentSessionId: String? = null
    private val currentSessionContext = mutableMapOf<String, String>()
    
    init {
        loadAllMemories()
    }
    
    /**
     * 开始新的会话
     */
    fun startNewSession(): String {
        currentSessionId = "session_${System.currentTimeMillis()}"
        currentSessionContext.clear()
        Log.d(TAG, "开始新会话: $currentSessionId")
        return currentSessionId!!
    }
    
    /**
     * 记录用户偏好
     */
    fun recordUserPreference(
        key: String,
        value: String,
        category: String,
        confidence: Float = 1.0f
    ) {
        val existingPref = userPreferences[key]
        val newPref = if (existingPref != null) {
            // 更新现有偏好，提高置信度和使用次数
            existingPref.copy(
                value = value,
                confidence = minOf(1.0f, existingPref.confidence + confidence * 0.1f),
                lastUpdated = System.currentTimeMillis(),
                usageCount = existingPref.usageCount + 1
            )
        } else {
            // 创建新偏好
            UserPreference(key, value, category, confidence)
        }
        
        userPreferences[key] = newPref
        updatePreferencesFlow()
        saveUserPreferences()
        
        Log.d(TAG, "记录用户偏好: $key = $value (置信度: ${newPref.confidence})")
    }
    
    /**
     * 获取用户偏好
     */
    fun getUserPreference(key: String): UserPreference? {
        return userPreferences[key]
    }
    
    /**
     * 获取分类下的所有偏好
     */
    fun getPreferencesByCategory(category: String): List<UserPreference> {
        return userPreferences.values.filter { it.category == category }
    }
    
    /**
     * 智能推荐用户偏好
     */
    fun getRecommendedPreferences(context: String, limit: Int = 5): List<UserPreference> {
        return userPreferences.values
            .filter { pref ->
                // 基于上下文和置信度推荐
                pref.confidence > 0.3f && 
                (context.lowercase().contains(pref.category.lowercase()) ||
                 context.lowercase().contains(pref.key.lowercase()))
            }
            .sortedByDescending { it.confidence * it.usageCount }
            .take(limit)
    }
    
    /**
     * 记录任务执行经验
     */
    fun recordTaskExperience(
        taskType: String,
        success: Boolean,
        executionTime: Long,
        optimizationTip: String? = null
    ) {
        val existingExp = taskExperiences[taskType]
        
        val newExp = if (existingExp != null) {
            // 更新现有经验
            val totalExecutions = if (success) 1 else 0
            val newSuccessRate = if (success) {
                (existingExp.successRate + 1.0f) / 2.0f
            } else {
                existingExp.successRate * 0.9f
            }
            
            val newTips = if (optimizationTip != null && !existingExp.optimizationTips.contains(optimizationTip)) {
                existingExp.optimizationTips + optimizationTip
            } else {
                existingExp.optimizationTips
            }
            
            existingExp.copy(
                averageExecutionTime = (existingExp.averageExecutionTime + executionTime) / 2,
                successRate = newSuccessRate,
                optimizationTips = newTips.takeLast(10), // 保留最近10个提示
                lastUsed = System.currentTimeMillis()
            )
        } else {
            // 创建新经验
            TaskExperience(
                taskType = taskType,
                successPattern = if (success) "初次成功" else "",
                failurePattern = if (!success) "初次失败" else "",
                optimizationTips = if (optimizationTip != null) listOf(optimizationTip) else emptyList(),
                averageExecutionTime = executionTime,
                successRate = if (success) 1.0f else 0.0f
            )
        }
        
        taskExperiences[taskType] = newExp
        saveTaskExperiences()
        
        Log.d(TAG, "记录任务经验: $taskType, 成功率: ${newExp.successRate}")
    }
    
    /**
     * 获取任务经验
     */
    fun getTaskExperience(taskType: String): TaskExperience? {
        return taskExperiences[taskType]
    }
    
    /**
     * 获取相似任务的经验
     */
    fun getSimilarTaskExperiences(taskDescription: String, limit: Int = 3): List<TaskExperience> {
        val keywords = extractKeywords(taskDescription)
        
        return taskExperiences.values
            .filter { exp ->
                keywords.any { keyword ->
                    exp.taskType.lowercase().contains(keyword.lowercase()) ||
                    exp.optimizationTips.any { tip -> tip.lowercase().contains(keyword.lowercase()) }
                }
            }
            .sortedByDescending { it.successRate * it.lastUsed }
            .take(limit)
    }
    
    /**
     * 记录上下文信息
     */
    fun recordContext(key: String, value: String) {
        currentSessionContext[key] = value
        Log.d(TAG, "记录上下文: $key = $value")
    }
    
    /**
     * 获取上下文信息
     */
    fun getContext(key: String): String? {
        return currentSessionContext[key]
    }
    
    /**
     * 获取所有当前上下文
     */
    fun getCurrentContext(): Map<String, String> {
        return currentSessionContext.toMap()
    }
    
    /**
     * 保存会话记忆
     */
    fun saveSessionMemory(taskHistory: List<String>, userIntent: String) {
        if (currentSessionId == null) return
        
        val memory = ContextMemory(
            sessionId = currentSessionId!!,
            taskHistory = taskHistory,
            userIntent = userIntent,
            currentContext = currentSessionContext.toMap()
        )
        
        synchronized(contextMemories) {
            contextMemories.add(memory)
            // 保持记忆数量在限制内
            if (contextMemories.size > MAX_CONTEXT_SESSIONS) {
                contextMemories.removeAt(0)
            }
        }
        
        saveContextMemories()
        Log.d(TAG, "保存会话记忆: $currentSessionId")
    }
    
    /**
     * 获取相关的历史记忆
     */
    fun getRelevantMemories(currentTask: String, limit: Int = 5): List<ContextMemory> {
        val keywords = extractKeywords(currentTask)
        
        return synchronized(contextMemories) {
            contextMemories
                .filter { memory ->
                    keywords.any { keyword ->
                        memory.userIntent.lowercase().contains(keyword.lowercase()) ||
                        memory.taskHistory.any { task -> task.lowercase().contains(keyword.lowercase()) }
                    }
                }
                .sortedByDescending { it.timestamp }
                .take(limit)
        }
    }
    
    /**
     * 生成任务建议
     */
    fun generateTaskSuggestions(currentTask: String): List<String> {
        val suggestions = mutableListOf<String>()
        
        // 基于任务经验的建议
        val similarExperiences = getSimilarTaskExperiences(currentTask)
        similarExperiences.forEach { exp ->
            suggestions.addAll(exp.optimizationTips)
        }
        
        // 基于用户偏好的建议
        val keywords = extractKeywords(currentTask)
        keywords.forEach { keyword ->
            val relatedPrefs = userPreferences.values.filter { pref ->
                pref.key.lowercase().contains(keyword.lowercase()) ||
                pref.value.lowercase().contains(keyword.lowercase())
            }
            relatedPrefs.forEach { pref ->
                suggestions.add("考虑使用偏好设置: ${pref.key} = ${pref.value}")
            }
        }
        
        // 基于历史记忆的建议
        val relevantMemories = getRelevantMemories(currentTask)
        relevantMemories.forEach { memory ->
            if (memory.taskHistory.isNotEmpty()) {
                suggestions.add("参考历史任务: ${memory.taskHistory.last()}")
            }
        }
        
        return suggestions.distinct().take(10)
    }
    
    /**
     * 学习用户行为模式
     */
    fun learnFromUserBehavior(action: String, context: Map<String, String>) {
        // 分析用户行为模式
        val timeOfDay = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val dayOfWeek = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
        
        // 记录时间偏好
        recordUserPreference(
            "preferred_time_${action}",
            timeOfDay.toString(),
            "timing",
            0.1f
        )
        
        // 记录操作偏好
        context.forEach { (key, value) ->
            recordUserPreference(
                "action_context_${action}_${key}",
                value,
                "behavior",
                0.2f
            )
        }
        
        Log.d(TAG, "学习用户行为: $action")
    }
    
    /**
     * 清理过期记忆
     */
    fun cleanupExpiredMemories() {
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - (30 * 24 * 60 * 60 * 1000L)
        
        // 清理低置信度的偏好
        val toRemovePrefs = userPreferences.values.filter { pref ->
            pref.confidence < 0.1f && pref.lastUpdated < thirtyDaysAgo
        }
        toRemovePrefs.forEach { pref ->
            userPreferences.remove(pref.key)
        }
        
        // 清理旧的上下文记忆
        synchronized(contextMemories) {
            contextMemories.removeAll { memory ->
                memory.timestamp < thirtyDaysAgo
            }
        }
        
        // 清理低成功率的任务经验
        val toRemoveExps = taskExperiences.values.filter { exp ->
            exp.successRate < 0.2f && exp.lastUsed < thirtyDaysAgo
        }
        toRemoveExps.forEach { exp ->
            taskExperiences.remove(exp.taskType)
        }
        
        saveAllMemories()
        Log.d(TAG, "清理过期记忆完成")
    }
    
    /**
     * 获取记忆统计信息
     */
    fun getMemoryStats(): Map<String, Any> {
        return mapOf(
            "userPreferences" to userPreferences.size,
            "taskExperiences" to taskExperiences.size,
            "contextMemories" to contextMemories.size,
            "averageConfidence" to userPreferences.values.map { it.confidence }.average(),
            "averageSuccessRate" to taskExperiences.values.map { it.successRate }.average(),
            "currentSessionContext" to currentSessionContext.size
        )
    }
    
    /**
     * 导出记忆数据
     */
    fun exportMemoryData(): String {
        val exportData = JSONObject().apply {
            put("userPreferences", JSONArray().apply {
                userPreferences.values.forEach { pref ->
                    put(JSONObject().apply {
                        put("key", pref.key)
                        put("value", pref.value)
                        put("category", pref.category)
                        put("confidence", pref.confidence)
                        put("usageCount", pref.usageCount)
                    })
                }
            })
            
            put("taskExperiences", JSONArray().apply {
                taskExperiences.values.forEach { exp ->
                    put(JSONObject().apply {
                        put("taskType", exp.taskType)
                        put("successRate", exp.successRate)
                        put("averageExecutionTime", exp.averageExecutionTime)
                        put("optimizationTips", JSONArray(exp.optimizationTips))
                    })
                }
            })
            
            put("exportTime", System.currentTimeMillis())
        }
        
        return exportData.toString(2)
    }
    
    /**
     * 重置所有记忆
     */
    fun resetAllMemories() {
        userPreferences.clear()
        taskExperiences.clear()
        synchronized(contextMemories) {
            contextMemories.clear()
        }
        currentSessionContext.clear()
        
        updatePreferencesFlow()
        saveAllMemories()
        
        Log.d(TAG, "已重置所有Agent记忆")
    }
    
    // === 私有方法 ===
    
    private fun extractKeywords(text: String): List<String> {
        return text.lowercase()
            .split("\\s+".toRegex())
            .filter { it.length > 2 }
            .distinct()
            .take(10)
    }
    
    private fun updatePreferencesFlow() {
        _preferencesFlow.value = userPreferences.toMap()
    }
    
    private fun loadAllMemories() {
        loadUserPreferences()
        loadTaskExperiences()
        loadContextMemories()
    }
    
    private fun saveAllMemories() {
        saveUserPreferences()
        saveTaskExperiences()
        saveContextMemories()
    }
    
    private fun saveUserPreferences() {
        try {
            val jsonArray = JSONArray()
            userPreferences.values.forEach { pref ->
                val jsonObject = JSONObject().apply {
                    put("key", pref.key)
                    put("value", pref.value)
                    put("category", pref.category)
                    put("confidence", pref.confidence)
                    put("lastUpdated", pref.lastUpdated)
                    put("usageCount", pref.usageCount)
                }
                jsonArray.put(jsonObject)
            }
            
            val file = File(context.filesDir, PREFERENCES_FILE)
            file.writeText(jsonArray.toString())
            
        } catch (e: Exception) {
            Log.e(TAG, "保存用户偏好失败", e)
        }
    }
    
    private fun loadUserPreferences() {
        try {
            val file = File(context.filesDir, PREFERENCES_FILE)
            if (file.exists()) {
                val jsonContent = file.readText()
                val jsonArray = JSONArray(jsonContent)
                
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val pref = UserPreference(
                        key = jsonObject.getString("key"),
                        value = jsonObject.getString("value"),
                        category = jsonObject.getString("category"),
                        confidence = jsonObject.getDouble("confidence").toFloat(),
                        lastUpdated = jsonObject.getLong("lastUpdated"),
                        usageCount = jsonObject.getInt("usageCount")
                    )
                    userPreferences[pref.key] = pref
                }
                
                updatePreferencesFlow()
                Log.d(TAG, "加载了 ${userPreferences.size} 个用户偏好")
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载用户偏好失败", e)
        }
    }
    
    private fun saveTaskExperiences() {
        try {
            val jsonArray = JSONArray()
            taskExperiences.values.forEach { exp ->
                val jsonObject = JSONObject().apply {
                    put("taskType", exp.taskType)
                    put("successPattern", exp.successPattern)
                    put("failurePattern", exp.failurePattern)
                    put("optimizationTips", JSONArray(exp.optimizationTips))
                    put("averageExecutionTime", exp.averageExecutionTime)
                    put("successRate", exp.successRate)
                    put("lastUsed", exp.lastUsed)
                }
                jsonArray.put(jsonObject)
            }
            
            val file = File(context.filesDir, EXPERIENCES_FILE)
            file.writeText(jsonArray.toString())
            
        } catch (e: Exception) {
            Log.e(TAG, "保存任务经验失败", e)
        }
    }
    
    private fun loadTaskExperiences() {
        try {
            val file = File(context.filesDir, EXPERIENCES_FILE)
            if (file.exists()) {
                val jsonContent = file.readText()
                val jsonArray = JSONArray(jsonContent)
                
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val tipsArray = jsonObject.getJSONArray("optimizationTips")
                    val tips = mutableListOf<String>()
                    for (j in 0 until tipsArray.length()) {
                        tips.add(tipsArray.getString(j))
                    }
                    
                    val exp = TaskExperience(
                        taskType = jsonObject.getString("taskType"),
                        successPattern = jsonObject.getString("successPattern"),
                        failurePattern = jsonObject.getString("failurePattern"),
                        optimizationTips = tips,
                        averageExecutionTime = jsonObject.getLong("averageExecutionTime"),
                        successRate = jsonObject.getDouble("successRate").toFloat(),
                        lastUsed = jsonObject.getLong("lastUsed")
                    )
                    taskExperiences[exp.taskType] = exp
                }
                
                Log.d(TAG, "加载了 ${taskExperiences.size} 个任务经验")
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载任务经验失败", e)
        }
    }
    
    private fun saveContextMemories() {
        try {
            val jsonArray = JSONArray()
            synchronized(contextMemories) {
                contextMemories.forEach { memory ->
                    val jsonObject = JSONObject().apply {
                        put("sessionId", memory.sessionId)
                        put("taskHistory", JSONArray(memory.taskHistory))
                        put("userIntent", memory.userIntent)
                        put("currentContext", JSONObject(memory.currentContext))
                        put("timestamp", memory.timestamp)
                    }
                    jsonArray.put(jsonObject)
                }
            }
            
            val file = File(context.filesDir, CONTEXT_MEMORY_FILE)
            file.writeText(jsonArray.toString())
            
        } catch (e: Exception) {
            Log.e(TAG, "保存上下文记忆失败", e)
        }
    }
    
    private fun loadContextMemories() {
        try {
            val file = File(context.filesDir, CONTEXT_MEMORY_FILE)
            if (file.exists()) {
                val jsonContent = file.readText()
                val jsonArray = JSONArray(jsonContent)
                
                synchronized(contextMemories) {
                    contextMemories.clear()
                    
                    for (i in 0 until jsonArray.length()) {
                        val jsonObject = jsonArray.getJSONObject(i)
                        
                        val taskHistoryArray = jsonObject.getJSONArray("taskHistory")
                        val taskHistory = mutableListOf<String>()
                        for (j in 0 until taskHistoryArray.length()) {
                            taskHistory.add(taskHistoryArray.getString(j))
                        }
                        
                        val contextObject = jsonObject.getJSONObject("currentContext")
                        val context = mutableMapOf<String, String>()
                        contextObject.keys().forEach { key ->
                            context[key] = contextObject.getString(key)
                        }
                        
                        val memory = ContextMemory(
                            sessionId = jsonObject.getString("sessionId"),
                            taskHistory = taskHistory,
                            userIntent = jsonObject.getString("userIntent"),
                            currentContext = context,
                            timestamp = jsonObject.getLong("timestamp")
                        )
                        contextMemories.add(memory)
                    }
                }
                
                Log.d(TAG, "加载了 ${contextMemories.size} 个上下文记忆")
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载上下文记忆失败", e)
        }
    }
}