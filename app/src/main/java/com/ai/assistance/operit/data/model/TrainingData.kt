package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 训练数据模型
 * 用于存储和管理本地模型训练数据
 */
@Entity(tableName = "training_data")
@TypeConverters(TrainingDataConverters::class)
data class TrainingData(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * 用户输入
     */
    val input: String,
    
    /**
     * 期望输出
     */
    val output: String,
    
    /**
     * 创建时间戳
     */
    val timestamp: Long = System.currentTimeMillis(),
    
    /**
     * 使用的工具列表
     */
    val toolsUsed: List<String> = emptyList(),
    
    /**
     * 数据质量评分 (0.0 - 1.0)
     */
    val quality: Float = 1.0f,
    
    /**
     * 数据来源
     */
    val source: DataSource = DataSource.ONLINE_API,
    
    /**
     * 用户反馈
     */
    val userFeedback: UserFeedback? = null,
    
    /**
     * 上下文信息
     */
    val context: ConversationContext? = null,
    
    /**
     * 标签
     */
    val tags: List<String> = emptyList(),
    
    /**
     * 语言
     */
    val language: String = "zh-CN",
    
    /**
     * 是否已用于训练
     */
    val isUsedForTraining: Boolean = false,
    
    /**
     * 训练轮次
     */
    val trainingEpoch: Int = 0,
    
    /**
     * 任务类型
     */
    val taskType: TaskType = TaskType.CONVERSATION,
    
    /**
     * 难度级别
     */
    val difficultyLevel: DifficultyLevel = DifficultyLevel.MEDIUM,
    
    /**
     * 验证状态
     */
    val validationStatus: ValidationStatus = ValidationStatus.PENDING
)

/**
 * 数据来源枚举
 */
enum class DataSource {
    ONLINE_API,        // 在线API
    USER_CORRECTION,   // 用户纠正
    SYNTHETIC,         // 合成数据
    IMPORTED,          // 导入数据
    HUMAN_ANNOTATED    // 人工标注
}

/**
 * 用户反馈
 */
data class UserFeedback(
    val rating: Int,           // 1-5星评分
    val isHelpful: Boolean,    // 是否有帮助
    val comment: String? = null, // 用户评论
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 对话上下文
 */
data class ConversationContext(
    val conversationId: String,
    val turnIndex: Int,
    val previousTurns: List<ConversationTurn> = emptyList(),
    val userIntent: String? = null,
    val entities: List<Entity> = emptyList(),
    val mood: String? = null,
    val location: String? = null,
    val timeOfDay: String? = null
)

/**
 * 对话轮次
 */
data class ConversationTurn(
    val role: String,          // "user" or "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 实体
 */
data class Entity(
    val type: String,
    val value: String,
    val confidence: Float
)

/**
 * 任务类型枚举
 */
enum class TaskType {
    CONVERSATION,      // 对话
    TOOL_CALLING,      // 工具调用
    SYSTEM_CONTROL,    // 系统控制
    QUESTION_ANSWERING,// 问答
    CODE_GENERATION,   // 代码生成
    TEXT_WRITING,      // 文本写作
    TRANSLATION,       // 翻译
    SUMMARIZATION,     // 摘要
    ANALYSIS,          // 分析
    CREATIVE_WRITING   // 创意写作
}

/**
 * 难度级别枚举
 */
enum class DifficultyLevel {
    EASY,      // 简单
    MEDIUM,    // 中等
    HARD,      // 困难
    EXPERT     // 专家级
}

/**
 * 验证状态枚举
 */
enum class ValidationStatus {
    PENDING,    // 待验证
    VALIDATED,  // 已验证
    REJECTED,   // 已拒绝
    MODIFIED    // 已修改
}

/**
 * 训练数据统计
 */
data class TrainingDataStats(
    val totalCount: Int,
    val bySource: Map<DataSource, Int>,
    val byTaskType: Map<TaskType, Int>,
    val byDifficultyLevel: Map<DifficultyLevel, Int>,
    val byValidationStatus: Map<ValidationStatus, Int>,
    val averageQuality: Float,
    val totalTokens: Int,
    val usedForTraining: Int,
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * 训练数据批次
 */
data class TrainingBatch(
    val id: String,
    val data: List<TrainingData>,
    val batchSize: Int,
    val epoch: Int,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 数据质量评估结果
 */
data class QualityAssessment(
    val overallScore: Float,
    val dimensions: Map<String, Float>,
    val issues: List<String>,
    val suggestions: List<String>
) {
    companion object {
        const val DIMENSION_RELEVANCE = "relevance"
        const val DIMENSION_ACCURACY = "accuracy"
        const val DIMENSION_COMPLETENESS = "completeness"
        const val DIMENSION_CLARITY = "clarity"
        const val DIMENSION_DIVERSITY = "diversity"
    }
}

/**
 * 数据预处理结果
 */
data class PreprocessingResult(
    val inputTokens: List<Int>,
    val outputTokens: List<Int>,
    val features: Map<String, Float>,
    val metadata: Map<String, String>
)

/**
 * 类型转换器
 */
class TrainingDataConverters {
    
    private val gson = Gson()
    
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return gson.toJson(value)
    }
    
    @TypeConverter
    fun toStringList(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType)
    }
    
    @TypeConverter
    fun fromUserFeedback(value: UserFeedback?): String? {
        return value?.let { gson.toJson(it) }
    }
    
    @TypeConverter
    fun toUserFeedback(value: String?): UserFeedback? {
        return value?.let { gson.fromJson(it, UserFeedback::class.java) }
    }
    
    @TypeConverter
    fun fromConversationContext(value: ConversationContext?): String? {
        return value?.let { gson.toJson(it) }
    }
    
    @TypeConverter
    fun toConversationContext(value: String?): ConversationContext? {
        return value?.let { gson.fromJson(it, ConversationContext::class.java) }
    }
    
    @TypeConverter
    fun fromDataSource(value: DataSource): String {
        return value.name
    }
    
    @TypeConverter
    fun toDataSource(value: String): DataSource {
        return DataSource.valueOf(value)
    }
    
    @TypeConverter
    fun fromTaskType(value: TaskType): String {
        return value.name
    }
    
    @TypeConverter
    fun toTaskType(value: String): TaskType {
        return TaskType.valueOf(value)
    }
    
    @TypeConverter
    fun fromDifficultyLevel(value: DifficultyLevel): String {
        return value.name
    }
    
    @TypeConverter
    fun toDifficultyLevel(value: String): DifficultyLevel {
        return DifficultyLevel.valueOf(value)
    }
    
    @TypeConverter
    fun fromValidationStatus(value: ValidationStatus): String {
        return value.name
    }
    
    @TypeConverter
    fun toValidationStatus(value: String): ValidationStatus {
        return ValidationStatus.valueOf(value)
    }
}

/**
 * 训练数据构建器
 */
class TrainingDataBuilder {
    private var input: String = ""
    private var output: String = ""
    private var toolsUsed: List<String> = emptyList()
    private var quality: Float = 1.0f
    private var source: DataSource = DataSource.ONLINE_API
    private var userFeedback: UserFeedback? = null
    private var context: ConversationContext? = null
    private var tags: List<String> = emptyList()
    private var language: String = "zh-CN"
    private var taskType: TaskType = TaskType.CONVERSATION
    private var difficultyLevel: DifficultyLevel = DifficultyLevel.MEDIUM
    
    fun setInput(input: String) = apply { this.input = input }
    fun setOutput(output: String) = apply { this.output = output }
    fun setToolsUsed(tools: List<String>) = apply { this.toolsUsed = tools }
    fun setQuality(quality: Float) = apply { this.quality = quality }
    fun setSource(source: DataSource) = apply { this.source = source }
    fun setUserFeedback(feedback: UserFeedback?) = apply { this.userFeedback = feedback }
    fun setContext(context: ConversationContext?) = apply { this.context = context }
    fun setTags(tags: List<String>) = apply { this.tags = tags }
    fun setLanguage(language: String) = apply { this.language = language }
    fun setTaskType(taskType: TaskType) = apply { this.taskType = taskType }
    fun setDifficultyLevel(level: DifficultyLevel) = apply { this.difficultyLevel = level }
    
    fun build(): TrainingData {
        require(input.isNotBlank()) { "输入不能为空" }
        require(output.isNotBlank()) { "输出不能为空" }
        require(quality in 0.0f..1.0f) { "质量评分必须在0.0到1.0之间" }
        
        return TrainingData(
            input = input,
            output = output,
            toolsUsed = toolsUsed,
            quality = quality,
            source = source,
            userFeedback = userFeedback,
            context = context,
            tags = tags,
            language = language,
            taskType = taskType,
            difficultyLevel = difficultyLevel
        )
    }
}

/**
 * 训练数据工具类
 */
object TrainingDataUtils {
    
    /**
     * 计算文本相似度
     */
    fun calculateSimilarity(text1: String, text2: String): Float {
        // 简化的相似度计算
        val words1 = text1.lowercase().split(" ")
        val words2 = text2.lowercase().split(" ")
        
        val intersection = words1.intersect(words2.toSet()).size
        val union = words1.union(words2.toSet()).size
        
        return if (union == 0) 0f else intersection.toFloat() / union
    }
    
    /**
     * 评估数据质量
     */
    fun assessQuality(data: TrainingData): QualityAssessment {
        val dimensions = mutableMapOf<String, Float>()
        val issues = mutableListOf<String>()
        val suggestions = mutableListOf<String>()
        
        // 评估相关性
        val relevance = assessRelevance(data.input, data.output)
        dimensions[QualityAssessment.DIMENSION_RELEVANCE] = relevance
        
        // 评估准确性
        val accuracy = assessAccuracy(data)
        dimensions[QualityAssessment.DIMENSION_ACCURACY] = accuracy
        
        // 评估完整性
        val completeness = assessCompleteness(data)
        dimensions[QualityAssessment.DIMENSION_COMPLETENESS] = completeness
        
        // 评估清晰度
        val clarity = assessClarity(data)
        dimensions[QualityAssessment.DIMENSION_CLARITY] = clarity
        
        // 计算总分
        val overallScore = dimensions.values.average().toFloat()
        
        // 生成建议
        if (relevance < 0.7f) {
            issues.add("输入与输出的相关性较低")
            suggestions.add("确保输出直接回答或响应输入")
        }
        
        if (completeness < 0.8f) {
            issues.add("数据可能不够完整")
            suggestions.add("补充必要的上下文信息")
        }
        
        return QualityAssessment(
            overallScore = overallScore,
            dimensions = dimensions,
            issues = issues,
            suggestions = suggestions
        )
    }
    
    private fun assessRelevance(input: String, output: String): Float {
        return calculateSimilarity(input, output)
    }
    
    private fun assessAccuracy(data: TrainingData): Float {
        // 基于用户反馈评估准确性
        return data.userFeedback?.let { feedback ->
            feedback.rating / 5.0f
        } ?: 0.8f // 默认准确性
    }
    
    private fun assessCompleteness(data: TrainingData): Float {
        var score = 0.5f
        
        // 检查基本字段
        if (data.input.length > 10) score += 0.1f
        if (data.output.length > 10) score += 0.1f
        if (data.toolsUsed.isNotEmpty()) score += 0.1f
        if (data.context != null) score += 0.1f
        if (data.tags.isNotEmpty()) score += 0.1f
        
        return score.coerceAtMost(1.0f)
    }
    
    private fun assessClarity(data: TrainingData): Float {
        // 简化的清晰度评估
        val inputWords = data.input.split(" ").size
        val outputWords = data.output.split(" ").size
        
        return when {
            inputWords < 3 || outputWords < 3 -> 0.5f
            inputWords in 3..20 && outputWords in 3..50 -> 0.9f
            else -> 0.7f
        }
    }
    
    /**
     * 去重相似数据
     */
    fun deduplicateData(dataList: List<TrainingData>): List<TrainingData> {
        val uniqueData = mutableListOf<TrainingData>()
        val threshold = 0.8f
        
        for (data in dataList) {
            val isDuplicate = uniqueData.any { existing ->
                calculateSimilarity(data.input, existing.input) > threshold &&
                calculateSimilarity(data.output, existing.output) > threshold
            }
            
            if (!isDuplicate) {
                uniqueData.add(data)
            }
        }
        
        return uniqueData
    }
    
    /**
     * 平衡数据集
     */
    fun balanceDataset(dataList: List<TrainingData>): List<TrainingData> {
        val groupedByTaskType = dataList.groupBy { it.taskType }
        val minCount = groupedByTaskType.values.minOfOrNull { it.size } ?: 0
        
        val balancedData = mutableListOf<TrainingData>()
        
        for ((taskType, data) in groupedByTaskType) {
            val sampledData = data.shuffled().take(minCount)
            balancedData.addAll(sampledData)
        }
        
        return balancedData.shuffled()
    }
}