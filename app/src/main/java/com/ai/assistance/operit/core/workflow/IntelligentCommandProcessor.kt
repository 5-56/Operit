package com.ai.assistance.operit.core.workflow

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.api.AIService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.regex.Pattern

/**
 * 模块1: 智能指令理解系统
 * 
 * 实现高级NLP处理引擎，能够解析用户的自然语言输入
 * - 识别用户意图类型（数据分析、文件操作、系统查询、编程任务等）
 * - 提取关键参数、约束条件和上下文信息
 * - 将自然语言转换为结构化的任务描述对象
 */
class IntelligentCommandProcessor private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "IntelligentCommandProcessor"
        
        @Volatile
        private var INSTANCE: IntelligentCommandProcessor? = null
        
        fun getInstance(context: Context): IntelligentCommandProcessor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: IntelligentCommandProcessor(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        // 意图类型枚举
        enum class IntentType {
            DATA_ANALYSIS,      // 数据分析
            FILE_OPERATION,     // 文件操作
            SYSTEM_QUERY,       // 系统查询
            PROGRAMMING_TASK,   // 编程任务
            WEB_SEARCH,         // 网络搜索
            AUTOMATION,         // 自动化任务
            COMMUNICATION,      // 通信任务
            MEDIA_PROCESSING,   // 媒体处理
            CALCULATION,        // 计算任务
            GENERAL_CHAT,       // 一般对话
            COMPLEX_WORKFLOW,   // 复杂工作流
            UNKNOWN             // 未知意图
        }
        
        // 复杂度级别
        enum class ComplexityLevel {
            SIMPLE,     // 简单 - 单步操作
            MODERATE,   // 中等 - 2-3步操作
            COMPLEX,    // 复杂 - 多步骤工作流
            ADVANCED    // 高级 - 需要深度分析和规划
        }
        
        // 数据类型
        enum class DataType {
            TEXT, NUMBER, FILE, URL, DATE, JSON, XML, CSV, IMAGE, VIDEO, AUDIO, UNKNOWN
        }
    }
    
    // 处理状态
    private val _processingState = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()
    
    // 意图模式库
    private val intentPatterns = mapOf(
        IntentType.DATA_ANALYSIS to listOf(
            Regex("分析.*(数据|统计|趋势|图表|报表).*", RegexOption.IGNORE_CASE),
            Regex(".*(可视化|绘制|画图|制图).*", RegexOption.IGNORE_CASE),
            Regex(".*(计算|统计|汇总|求和|平均).*", RegexOption.IGNORE_CASE),
            Regex("(analyze|analysis|statistics|chart|graph|plot)", RegexOption.IGNORE_CASE)
        ),
        IntentType.FILE_OPERATION to listOf(
            Regex(".*(文件|目录|文件夹|路径).*", RegexOption.IGNORE_CASE),
            Regex(".*(创建|删除|移动|复制|重命名).*文件.*", RegexOption.IGNORE_CASE),
            Regex(".*(打开|关闭|读取|写入|保存).*", RegexOption.IGNORE_CASE),
            Regex("(file|folder|directory|create|delete|move|copy|rename)", RegexOption.IGNORE_CASE)
        ),
        IntentType.SYSTEM_QUERY to listOf(
            Regex(".*(系统|设备|内存|CPU|存储|网络).*", RegexOption.IGNORE_CASE),
            Regex(".*(状态|信息|版本|配置).*", RegexOption.IGNORE_CASE),
            Regex("(system|device|memory|cpu|storage|network|status)", RegexOption.IGNORE_CASE)
        ),
        IntentType.PROGRAMMING_TASK to listOf(
            Regex(".*(代码|编程|脚本|函数|类|方法).*", RegexOption.IGNORE_CASE),
            Regex(".*(编写|生成|调试|测试|运行).*代码.*", RegexOption.IGNORE_CASE),
            Regex("(code|programming|script|function|class|method|debug)", RegexOption.IGNORE_CASE)
        ),
        IntentType.WEB_SEARCH to listOf(
            Regex(".*(搜索|查找|检索).*", RegexOption.IGNORE_CASE),
            Regex(".*(网上|互联网|在线).*", RegexOption.IGNORE_CASE),
            Regex("(search|find|google|internet|online|web)", RegexOption.IGNORE_CASE)
        ),
        IntentType.AUTOMATION to listOf(
            Regex(".*(自动化|批量|定时|循环).*", RegexOption.IGNORE_CASE),
            Regex(".*(执行|运行|启动|停止).*任务.*", RegexOption.IGNORE_CASE),
            Regex("(automation|batch|schedule|loop|execute|run)", RegexOption.IGNORE_CASE)
        ),
        IntentType.COMMUNICATION to listOf(
            Regex(".*(发送|接收|邮件|消息|短信).*", RegexOption.IGNORE_CASE),
            Regex(".*(通知|提醒|告知).*", RegexOption.IGNORE_CASE),
            Regex("(send|receive|email|message|sms|notify|reminder)", RegexOption.IGNORE_CASE)
        ),
        IntentType.MEDIA_PROCESSING to listOf(
            Regex(".*(图片|图像|照片|视频|音频|音乐).*", RegexOption.IGNORE_CASE),
            Regex(".*(编辑|处理|转换|压缩).*", RegexOption.IGNORE_CASE),
            Regex("(image|photo|video|audio|music|edit|process|convert)", RegexOption.IGNORE_CASE)
        ),
        IntentType.CALCULATION to listOf(
            Regex(".*(计算|算式|数学|公式).*", RegexOption.IGNORE_CASE),
            Regex(".*[+\\-*/=].*", RegexOption.IGNORE_CASE),
            Regex("(calculate|math|formula|equation)", RegexOption.IGNORE_CASE)
        )
    )
    
    // 复杂度关键词
    private val complexityKeywords = mapOf(
        ComplexityLevel.SIMPLE to listOf("简单", "快速", "直接", "立即", "马上", "simple", "quick", "direct"),
        ComplexityLevel.MODERATE to listOf("分步", "依次", "然后", "接着", "step", "then", "next"),
        ComplexityLevel.COMPLEX to listOf("复杂", "详细", "全面", "完整", "系统", "complex", "detailed", "comprehensive"),
        ComplexityLevel.ADVANCED to listOf("深度", "高级", "智能", "自动", "学习", "advanced", "intelligent", "automatic", "learning")
    )
    
    // 数据类型模式
    private val dataTypePatterns = mapOf(
        DataType.TEXT to Regex(".*文本.*|.*txt.*|.*text.*", RegexOption.IGNORE_CASE),
        DataType.NUMBER to Regex(".*数字.*|.*数值.*|.*number.*", RegexOption.IGNORE_CASE),
        DataType.FILE to Regex(".*文件.*|.*file.*", RegexOption.IGNORE_CASE),
        DataType.URL to Regex(".*https?://.*|.*网址.*|.*链接.*|.*url.*", RegexOption.IGNORE_CASE),
        DataType.DATE to Regex(".*日期.*|.*时间.*|.*date.*|.*time.*", RegexOption.IGNORE_CASE),
        DataType.JSON to Regex(".*json.*|.*\\{.*\\}.*", RegexOption.IGNORE_CASE),
        DataType.XML to Regex(".*xml.*|.*<.*>.*", RegexOption.IGNORE_CASE),
        DataType.CSV to Regex(".*csv.*|.*表格.*|.*excel.*", RegexOption.IGNORE_CASE),
        DataType.IMAGE to Regex(".*图片.*|.*图像.*|.*照片.*|.*image.*|.*photo.*", RegexOption.IGNORE_CASE),
        DataType.VIDEO to Regex(".*视频.*|.*video.*|.*mp4.*|.*avi.*", RegexOption.IGNORE_CASE),
        DataType.AUDIO to Regex(".*音频.*|.*音乐.*|.*audio.*|.*music.*|.*mp3.*", RegexOption.IGNORE_CASE)
    )
    
    // 处理状态密封类
    sealed class ProcessingState {
        object Idle : ProcessingState()
        object Processing : ProcessingState()
        data class Completed(val result: TaskDescription) : ProcessingState()
        data class Error(val message: String) : ProcessingState()
    }
    
    /**
     * 结构化任务描述对象
     */
    data class TaskDescription(
        val originalInput: String,
        val intentType: IntentType,
        val complexityLevel: ComplexityLevel,
        val confidence: Float, // 0.0 - 1.0
        val extractedParameters: Map<String, Any>,
        val constraints: List<String>,
        val contextInfo: Map<String, Any>,
        val requiredTools: List<String>,
        val estimatedSteps: Int,
        val dataTypes: List<DataType>,
        val language: String, // 检测到的语言
        val priority: Int, // 1-10, 10最高
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * 处理用户输入，解析自然语言并生成结构化任务描述
     */
    suspend fun processUserInput(input: String): TaskDescription {
        return withContext(Dispatchers.Default) {
            _processingState.value = ProcessingState.Processing
            
            try {
                Log.d(TAG, "开始处理用户输入: $input")
                
                // 1. 语言检测
                val detectedLanguage = detectLanguage(input)
                Log.d(TAG, "检测到语言: $detectedLanguage")
                
                // 2. 意图识别
                val intentResult = identifyIntent(input)
                Log.d(TAG, "识别意图: ${intentResult.first}, 置信度: ${intentResult.second}")
                
                // 3. 复杂度评估
                val complexity = assessComplexity(input)
                Log.d(TAG, "复杂度评估: $complexity")
                
                // 4. 参数提取
                val parameters = extractParameters(input, intentResult.first)
                Log.d(TAG, "提取参数: $parameters")
                
                // 5. 约束条件识别
                val constraints = identifyConstraints(input)
                Log.d(TAG, "识别约束: $constraints")
                
                // 6. 上下文信息收集
                val contextInfo = collectContextInfo(input, intentResult.first)
                Log.d(TAG, "收集上下文: $contextInfo")
                
                // 7. 所需工具识别
                val requiredTools = identifyRequiredTools(input, intentResult.first)
                Log.d(TAG, "所需工具: $requiredTools")
                
                // 8. 步骤数估算
                val estimatedSteps = estimateSteps(input, complexity)
                Log.d(TAG, "估算步骤: $estimatedSteps")
                
                // 9. 数据类型识别
                val dataTypes = identifyDataTypes(input)
                Log.d(TAG, "数据类型: $dataTypes")
                
                // 10. 优先级评估
                val priority = assessPriority(input, intentResult.first)
                Log.d(TAG, "优先级: $priority")
                
                // 构建任务描述
                val taskDescription = TaskDescription(
                    originalInput = input,
                    intentType = intentResult.first,
                    complexityLevel = complexity,
                    confidence = intentResult.second,
                    extractedParameters = parameters,
                    constraints = constraints,
                    contextInfo = contextInfo,
                    requiredTools = requiredTools,
                    estimatedSteps = estimatedSteps,
                    dataTypes = dataTypes,
                    language = detectedLanguage,
                    priority = priority
                )
                
                _processingState.value = ProcessingState.Completed(taskDescription)
                Log.d(TAG, "任务描述构建完成")
                
                taskDescription
                
            } catch (e: Exception) {
                Log.e(TAG, "处理用户输入时发生错误", e)
                _processingState.value = ProcessingState.Error("处理失败: ${e.message}")
                
                // 返回一个基本的任务描述
                TaskDescription(
                    originalInput = input,
                    intentType = IntentType.UNKNOWN,
                    complexityLevel = ComplexityLevel.SIMPLE,
                    confidence = 0.0f,
                    extractedParameters = emptyMap(),
                    constraints = emptyList(),
                    contextInfo = emptyMap(),
                    requiredTools = emptyList(),
                    estimatedSteps = 1,
                    dataTypes = listOf(DataType.TEXT),
                    language = "unknown",
                    priority = 5
                )
            }
        }
    }
    
    /**
     * 检测输入文本的语言
     */
    private fun detectLanguage(input: String): String {
        // 简单的语言检测逻辑
        val chinesePattern = Regex("[\\u4e00-\\u9fff]")
        val englishPattern = Regex("[a-zA-Z]")
        
        val chineseMatches = chinesePattern.findAll(input).count()
        val englishMatches = englishPattern.findAll(input).count()
        
        return when {
            chineseMatches > englishMatches * 2 -> "zh-CN"
            englishMatches > chineseMatches * 2 -> "en-US"
            chineseMatches > 0 && englishMatches > 0 -> "zh-EN"
            else -> "unknown"
        }
    }
    
    /**
     * 识别用户意图
     */
    private fun identifyIntent(input: String): Pair<IntentType, Float> {
        var bestIntent = IntentType.UNKNOWN
        var bestScore = 0.0f
        
        for ((intent, patterns) in intentPatterns) {
            val score = patterns.count { it.containsMatchIn(input) }.toFloat() / patterns.size
            if (score > bestScore) {
                bestScore = score
                bestIntent = intent
            }
        }
        
        // 如果没有明确匹配，使用启发式判断
        if (bestScore == 0.0f) {
            bestIntent = heuristicIntentDetection(input)
            bestScore = 0.3f // 启发式的置信度较低
        }
        
        // 复杂工作流检测
        if (input.length > 100 || input.count { it == '，' || it == ',' } > 3) {
            if (bestScore < 0.8f) {
                bestIntent = IntentType.COMPLEX_WORKFLOW
                bestScore = 0.7f
            }
        }
        
        return Pair(bestIntent, bestScore)
    }
    
    /**
     * 启发式意图检测
     */
    private fun heuristicIntentDetection(input: String): IntentType {
        return when {
            input.contains("?") || input.contains("？") -> IntentType.SYSTEM_QUERY
            input.contains("帮我") || input.contains("help me") -> IntentType.GENERAL_CHAT
            input.length > 50 -> IntentType.COMPLEX_WORKFLOW
            else -> IntentType.GENERAL_CHAT
        }
    }
    
    /**
     * 评估任务复杂度
     */
    private fun assessComplexity(input: String): ComplexityLevel {
        val inputLower = input.lowercase(Locale.getDefault())
        
        // 检查复杂度关键词
        for ((level, keywords) in complexityKeywords) {
            if (keywords.any { inputLower.contains(it) }) {
                return level
            }
        }
        
        // 基于长度和结构判断
        return when {
            input.length < 20 -> ComplexityLevel.SIMPLE
            input.length < 50 -> ComplexityLevel.MODERATE
            input.length < 100 -> ComplexityLevel.COMPLEX
            else -> ComplexityLevel.ADVANCED
        }
    }
    
    /**
     * 提取参数
     */
    private fun extractParameters(input: String, intentType: IntentType): Map<String, Any> {
        val parameters = mutableMapOf<String, Any>()
        
        // 数字提取
        val numberPattern = Regex("\\d+(?:\\.\\d+)?")
        val numbers = numberPattern.findAll(input).map { it.value }.toList()
        if (numbers.isNotEmpty()) {
            parameters["numbers"] = numbers
        }
        
        // 文件路径提取
        val pathPattern = Regex("[/\\\\]?[\\w\\-_]+(?:[/\\\\][\\w\\-_\\.]+)*")
        val paths = pathPattern.findAll(input).map { it.value }.toList()
        if (paths.isNotEmpty()) {
            parameters["paths"] = paths
        }
        
        // URL提取
        val urlPattern = Regex("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=]+")
        val urls = urlPattern.findAll(input).map { it.value }.toList()
        if (urls.isNotEmpty()) {
            parameters["urls"] = urls
        }
        
        // 根据意图类型提取特定参数
        when (intentType) {
            IntentType.FILE_OPERATION -> {
                extractFileOperationParams(input, parameters)
            }
            IntentType.DATA_ANALYSIS -> {
                extractDataAnalysisParams(input, parameters)
            }
            IntentType.PROGRAMMING_TASK -> {
                extractProgrammingParams(input, parameters)
            }
            else -> {}
        }
        
        return parameters
    }
    
    /**
     * 提取文件操作参数
     */
    private fun extractFileOperationParams(input: String, parameters: MutableMap<String, Any>) {
        // 操作类型
        val operations = listOf("创建", "删除", "移动", "复制", "重命名", "create", "delete", "move", "copy", "rename")
        val foundOperation = operations.find { input.contains(it, ignoreCase = true) }
        if (foundOperation != null) {
            parameters["operation"] = foundOperation
        }
        
        // 文件扩展名
        val extPattern = Regex("\\.(\\w+)")
        val extensions = extPattern.findAll(input).map { it.groupValues[1] }.toList()
        if (extensions.isNotEmpty()) {
            parameters["extensions"] = extensions
        }
    }
    
    /**
     * 提取数据分析参数
     */
    private fun extractDataAnalysisParams(input: String, parameters: MutableMap<String, Any>) {
        // 图表类型
        val chartTypes = listOf("柱状图", "折线图", "饼图", "散点图", "bar", "line", "pie", "scatter")
        val foundChart = chartTypes.find { input.contains(it, ignoreCase = true) }
        if (foundChart != null) {
            parameters["chartType"] = foundChart
        }
        
        // 统计方法
        val statMethods = listOf("平均", "总和", "最大", "最小", "average", "sum", "max", "min")
        val foundStat = statMethods.find { input.contains(it, ignoreCase = true) }
        if (foundStat != null) {
            parameters["statMethod"] = foundStat
        }
    }
    
    /**
     * 提取编程参数
     */
    private fun extractProgrammingParams(input: String, parameters: MutableMap<String, Any>) {
        // 编程语言
        val languages = listOf("python", "java", "kotlin", "javascript", "c++", "c#")
        val foundLang = languages.find { input.contains(it, ignoreCase = true) }
        if (foundLang != null) {
            parameters["language"] = foundLang
        }
        
        // 代码类型
        val codeTypes = listOf("函数", "类", "方法", "脚本", "function", "class", "method", "script")
        val foundType = codeTypes.find { input.contains(it, ignoreCase = true) }
        if (foundType != null) {
            parameters["codeType"] = foundType
        }
    }
    
    /**
     * 识别约束条件
     */
    private fun identifyConstraints(input: String): List<String> {
        val constraints = mutableListOf<String>()
        
        // 时间约束
        val timeConstraints = listOf("立即", "马上", "今天", "明天", "本周", "immediately", "today", "tomorrow")
        timeConstraints.forEach { constraint ->
            if (input.contains(constraint, ignoreCase = true)) {
                constraints.add("时间约束: $constraint")
            }
        }
        
        // 质量约束
        val qualityConstraints = listOf("高质量", "快速", "准确", "详细", "简单", "high quality", "fast", "accurate", "detailed", "simple")
        qualityConstraints.forEach { constraint ->
            if (input.contains(constraint, ignoreCase = true)) {
                constraints.add("质量约束: $constraint")
            }
        }
        
        // 安全约束
        val securityConstraints = listOf("安全", "私密", "加密", "secure", "private", "encrypted")
        securityConstraints.forEach { constraint ->
            if (input.contains(constraint, ignoreCase = true)) {
                constraints.add("安全约束: $constraint")
            }
        }
        
        return constraints
    }
    
    /**
     * 收集上下文信息
     */
    private fun collectContextInfo(input: String, intentType: IntentType): Map<String, Any> {
        val contextInfo = mutableMapOf<String, Any>()
        
        contextInfo["inputLength"] = input.length
        contextInfo["wordCount"] = input.split("\\s+".toRegex()).size
        contextInfo["sentenceCount"] = input.split("[.!?。！？]".toRegex()).size
        contextInfo["hasQuestion"] = input.contains("?") || input.contains("？")
        contextInfo["hasNumbers"] = Regex("\\d").containsMatchIn(input)
        contextInfo["hasUrls"] = Regex("https?://").containsMatchIn(input)
        contextInfo["intentType"] = intentType.name
        
        return contextInfo
    }
    
    /**
     * 识别所需工具
     */
    private fun identifyRequiredTools(input: String, intentType: IntentType): List<String> {
        val tools = mutableListOf<String>()
        
        when (intentType) {
            IntentType.FILE_OPERATION -> tools.addAll(listOf("file_manager", "path_resolver"))
            IntentType.DATA_ANALYSIS -> tools.addAll(listOf("data_processor", "chart_generator", "statistics_calculator"))
            IntentType.PROGRAMMING_TASK -> tools.addAll(listOf("code_generator", "syntax_checker", "code_executor"))
            IntentType.WEB_SEARCH -> tools.addAll(listOf("web_searcher", "content_parser"))
            IntentType.SYSTEM_QUERY -> tools.addAll(listOf("system_info", "device_monitor"))
            IntentType.AUTOMATION -> tools.addAll(listOf("task_scheduler", "automation_engine"))
            IntentType.MEDIA_PROCESSING -> tools.addAll(listOf("image_processor", "video_processor", "audio_processor"))
            IntentType.CALCULATION -> tools.addAll(listOf("calculator", "math_solver"))
            IntentType.COMPLEX_WORKFLOW -> tools.addAll(listOf("workflow_engine", "task_planner", "execution_monitor"))
            else -> tools.add("general_assistant")
        }
        
        return tools
    }
    
    /**
     * 估算执行步骤数
     */
    private fun estimateSteps(input: String, complexity: ComplexityLevel): Int {
        val baseSteps = when (complexity) {
            ComplexityLevel.SIMPLE -> 1
            ComplexityLevel.MODERATE -> 3
            ComplexityLevel.COMPLEX -> 5
            ComplexityLevel.ADVANCED -> 8
        }
        
        // 根据连接词和逗号数量调整
        val connectors = input.count { it == '，' || it == ',' } + 
                        listOf("然后", "接着", "再", "最后", "then", "next", "finally").count { input.contains(it) }
        
        return baseSteps + connectors
    }
    
    /**
     * 识别数据类型
     */
    private fun identifyDataTypes(input: String): List<DataType> {
        val detectedTypes = mutableListOf<DataType>()
        
        for ((dataType, pattern) in dataTypePatterns) {
            if (pattern.containsMatchIn(input)) {
                detectedTypes.add(dataType)
            }
        }
        
        if (detectedTypes.isEmpty()) {
            detectedTypes.add(DataType.TEXT)
        }
        
        return detectedTypes
    }
    
    /**
     * 评估优先级
     */
    private fun assessPriority(input: String, intentType: IntentType): Int {
        var priority = 5 // 默认中等优先级
        
        // 紧急关键词
        val urgentKeywords = listOf("紧急", "立即", "马上", "urgent", "immediately", "asap")
        if (urgentKeywords.any { input.contains(it, ignoreCase = true) }) {
            priority += 3
        }
        
        // 重要关键词
        val importantKeywords = listOf("重要", "关键", "必须", "important", "critical", "must")
        if (importantKeywords.any { input.contains(it, ignoreCase = true) }) {
            priority += 2
        }
        
        // 根据意图类型调整
        priority += when (intentType) {
            IntentType.SYSTEM_QUERY -> 1
            IntentType.PROGRAMMING_TASK -> 2
            IntentType.DATA_ANALYSIS -> 2
            IntentType.COMPLEX_WORKFLOW -> 3
            else -> 0
        }
        
        return priority.coerceIn(1, 10)
    }
    
    /**
     * 获取意图类型的中文描述
     */
    fun getIntentDescription(intentType: IntentType): String {
        return when (intentType) {
            IntentType.DATA_ANALYSIS -> "数据分析任务"
            IntentType.FILE_OPERATION -> "文件操作任务"
            IntentType.SYSTEM_QUERY -> "系统查询任务"
            IntentType.PROGRAMMING_TASK -> "编程开发任务"
            IntentType.WEB_SEARCH -> "网络搜索任务"
            IntentType.AUTOMATION -> "自动化任务"
            IntentType.COMMUNICATION -> "通信任务"
            IntentType.MEDIA_PROCESSING -> "媒体处理任务"
            IntentType.CALCULATION -> "计算任务"
            IntentType.GENERAL_CHAT -> "一般对话"
            IntentType.COMPLEX_WORKFLOW -> "复杂工作流"
            IntentType.UNKNOWN -> "未知任务类型"
        }
    }
    
    /**
     * 获取复杂度的中文描述
     */
    fun getComplexityDescription(complexity: ComplexityLevel): String {
        return when (complexity) {
            ComplexityLevel.SIMPLE -> "简单任务"
            ComplexityLevel.MODERATE -> "中等复杂度任务"
            ComplexityLevel.COMPLEX -> "复杂任务"
            ComplexityLevel.ADVANCED -> "高级任务"
        }
    }
}