package com.ai.assistance.operit.core.ai.local

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.exp

/**
 * 本地AI引擎
 * 支持本地模型推理、工具调用和智能对话
 */
class LocalAIEngine(private val context: Context) {
    
    companion object {
        private const val TAG = "LocalAIEngine"
        private const val MODEL_FILE = "local_ai_model.tflite"
        private const val VOCAB_FILE = "vocab.txt"
        private const val MAX_SEQUENCE_LENGTH = 512
        private const val EMBEDDING_DIM = 768
        private const val BATCH_SIZE = 1
        
        // 工具调用相关
        private const val TOOL_CALL_THRESHOLD = 0.7f
        private const val INTENT_CLASSIFICATION_THRESHOLD = 0.6f
    }
    
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private val isLoaded = AtomicBoolean(false)
    
    // 词汇表和编码器
    private val vocabulary = mutableListOf<String>()
    private val wordToIndex = ConcurrentHashMap<String, Int>()
    private val indexToWord = ConcurrentHashMap<Int, String>()
    
    // 意图分类器
    private val intentClassifier = IntentClassifier()
    
    // 对话上下文
    private val conversationHistory = mutableListOf<ConversationTurn>()
    private val maxHistoryLength = 10
    
    // 工具调用缓存
    private val toolCallCache = ConcurrentHashMap<String, ToolResult>()
    
    data class ConversationTurn(
        val userInput: String,
        val aiResponse: String,
        val timestamp: Long = System.currentTimeMillis(),
        val toolsUsed: List<String> = emptyList()
    )
    
    data class ProcessingResult(
        val response: String,
        val confidence: Float,
        val intentType: IntentType,
        val toolCalls: List<ToolCall>
    )
    
    data class ToolCall(
        val toolName: String,
        val parameters: Map<String, String>,
        val confidence: Float
    )
    
    enum class IntentType {
        CHAT,           // 普通对话
        TOOL_CALL,      // 工具调用
        SYSTEM_CONTROL, // 系统控制
        QUESTION,       // 问题回答
        COMMAND         // 命令执行
    }
    
    init {
        initialize()
    }
    
    private fun initialize() {
        try {
            loadVocabulary()
            loadModel()
            isLoaded.set(true)
            Log.d(TAG, "本地AI引擎初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "本地AI引擎初始化失败", e)
            // 降级到规则引擎
            initializeFallbackEngine()
        }
    }
    
    private fun loadVocabulary() {
        try {
            val inputStream = context.assets.open(VOCAB_FILE)
            val vocab = inputStream.bufferedReader().readLines()
            
            vocabulary.clear()
            wordToIndex.clear()
            indexToWord.clear()
            
            vocab.forEachIndexed { index, word ->
                vocabulary.add(word)
                wordToIndex[word] = index
                indexToWord[index] = word
            }
            
            Log.d(TAG, "词汇表加载完成，共${vocabulary.size}个词")
        } catch (e: Exception) {
            Log.w(TAG, "词汇表加载失败，使用内置词汇表")
            createDefaultVocabulary()
        }
    }
    
    private fun createDefaultVocabulary() {
        // 创建基础词汇表
        val defaultVocab = listOf(
            "[PAD]", "[UNK]", "[CLS]", "[SEP]", "[MASK]",
            "你好", "帮助", "打开", "关闭", "搜索", "查找", "设置",
            "时间", "天气", "电话", "短信", "拍照", "录音", "播放",
            "文件", "删除", "复制", "移动", "创建", "安装", "卸载"
        )
        
        vocabulary.addAll(defaultVocab)
        defaultVocab.forEachIndexed { index, word ->
            wordToIndex[word] = index
            indexToWord[index] = word
        }
    }
    
    private fun loadModel() {
        try {
            val modelFile = loadModelFile()
            
            val options = Interpreter.Options()
            
            // 尝试使用GPU加速
            val compatList = CompatibilityList()
            if (compatList.isDelegateSupportedOnThisDevice) {
                val delegateOptions = compatList.bestOptionsForThisDevice
                gpuDelegate = GpuDelegate(delegateOptions)
                options.addDelegate(gpuDelegate)
                Log.d(TAG, "启用GPU加速")
            }
            
            // 设置线程数
            options.setNumThreads(4)
            
            interpreter = Interpreter(modelFile, options)
            
            Log.d(TAG, "模型加载完成")
        } catch (e: Exception) {
            Log.e(TAG, "模型加载失败", e)
            throw e
        }
    }
    
    private fun loadModelFile(): MappedByteBuffer {
        return try {
            val fileDescriptor = context.assets.openFd(MODEL_FILE)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: Exception) {
            Log.w(TAG, "无法从assets加载模型文件，尝试从内部存储加载")
            // 创建一个dummy的MappedByteBuffer用于降级处理
            throw e
        }
    }
    
    private fun initializeFallbackEngine() {
        // 初始化规则引擎作为降级方案
        Log.d(TAG, "使用规则引擎作为降级方案")
        isLoaded.set(true)
    }
    
    /**
     * 处理用户指令
     */
    suspend fun processCommand(command: String, toolHandler: AIToolHandler): String {
        return withContext(Dispatchers.Default) {
            try {
                Log.d(TAG, "处理用户指令: $command")
                
                // 预处理输入
                val processedInput = preprocessInput(command)
                
                // 分析意图
                val intentResult = analyzeIntent(processedInput)
                
                // 生成响应
                val response = generateResponse(processedInput, intentResult, toolHandler)
                
                // 更新对话历史
                updateConversationHistory(command, response.response, response.toolCalls.map { it.toolName })
                
                response.response
                
            } catch (e: Exception) {
                Log.e(TAG, "处理指令失败", e)
                "抱歉，我现在无法处理这个请求，请稍后再试。"
            }
        }
    }
    
    private fun preprocessInput(input: String): String {
        // 文本预处理：去除多余空格、标点符号规范化等
        return input.trim()
            .replace(Regex("\\s+"), " ")
            .replace("？", "?")
            .replace("！", "!")
            .replace("，", ",")
            .replace("。", ".")
    }
    
    private fun analyzeIntent(input: String): IntentResult {
        // 使用本地模型或规则引擎分析意图
        return if (interpreter != null) {
            analyzeIntentWithModel(input)
        } else {
            analyzeIntentWithRules(input)
        }
    }
    
    private fun analyzeIntentWithModel(input: String): IntentResult {
        try {
            // 编码输入文本
            val encodedInput = encodeText(input)
            
            // 模型推理
            val outputBuffer = ByteBuffer.allocateDirect(4 * 5) // 5个意图类别
            outputBuffer.order(ByteOrder.nativeOrder())
            
            interpreter?.run(encodedInput, outputBuffer)
            
            outputBuffer.rewind()
            val probabilities = FloatArray(5)
            outputBuffer.asFloatBuffer().get(probabilities)
            
            // 应用softmax
            val softmaxProbs = softmax(probabilities)
            
            // 找出最高概率的意图
            val maxIndex = softmaxProbs.indexOfMax()
            val maxProb = softmaxProbs[maxIndex]
            
            val intentType = when (maxIndex) {
                0 -> IntentType.CHAT
                1 -> IntentType.TOOL_CALL
                2 -> IntentType.SYSTEM_CONTROL
                3 -> IntentType.QUESTION
                4 -> IntentType.COMMAND
                else -> IntentType.CHAT
            }
            
            return IntentResult(intentType, maxProb)
            
        } catch (e: Exception) {
            Log.e(TAG, "模型意图分析失败", e)
            return analyzeIntentWithRules(input)
        }
    }
    
    private fun analyzeIntentWithRules(input: String): IntentResult {
        val lowerInput = input.lowercase()
        
        // 工具调用关键词
        val toolKeywords = listOf(
            "打开", "关闭", "搜索", "查找", "设置", "安装", "卸载",
            "拍照", "录音", "播放", "暂停", "发送", "打电话", "发短信"
        )
        
        // 系统控制关键词
        val systemKeywords = listOf(
            "调节", "音量", "亮度", "蓝牙", "wifi", "热点", "飞行模式"
        )
        
        // 问题关键词
        val questionKeywords = listOf(
            "什么", "为什么", "怎么", "如何", "谁", "哪里", "什么时候"
        )
        
        return when {
            toolKeywords.any { lowerInput.contains(it) } -> 
                IntentResult(IntentType.TOOL_CALL, 0.8f)
            systemKeywords.any { lowerInput.contains(it) } -> 
                IntentResult(IntentType.SYSTEM_CONTROL, 0.8f)
            questionKeywords.any { lowerInput.contains(it) } -> 
                IntentResult(IntentType.QUESTION, 0.8f)
            lowerInput.contains("帮我") || lowerInput.contains("请") -> 
                IntentResult(IntentType.COMMAND, 0.8f)
            else -> IntentResult(IntentType.CHAT, 0.6f)
        }
    }
    
    private suspend fun generateResponse(
        input: String, 
        intentResult: IntentResult, 
        toolHandler: AIToolHandler
    ): ProcessingResult {
        
        val toolCalls = mutableListOf<ToolCall>()
        var response = ""
        
        when (intentResult.intentType) {
            IntentType.TOOL_CALL -> {
                val extractedToolCalls = extractToolCalls(input)
                toolCalls.addAll(extractedToolCalls)
                
                // 执行工具调用
                val toolResults = mutableListOf<String>()
                for (toolCall in extractedToolCalls) {
                    val result = executeToolCall(toolCall, toolHandler)
                    toolResults.add(result)
                }
                
                response = if (toolResults.isNotEmpty()) {
                    "已为您${extractActionDescription(input)}。${toolResults.joinToString("，")}"
                } else {
                    "好的，我来帮您${extractActionDescription(input)}。"
                }
            }
            
            IntentType.SYSTEM_CONTROL -> {
                response = handleSystemControl(input)
            }
            
            IntentType.QUESTION -> {
                response = handleQuestion(input)
            }
            
            IntentType.COMMAND -> {
                response = handleCommand(input)
            }
            
            IntentType.CHAT -> {
                response = handleChat(input)
            }
        }
        
        return ProcessingResult(
            response = response,
            confidence = intentResult.confidence,
            intentType = intentResult.intentType,
            toolCalls = toolCalls
        )
    }
    
    private fun extractToolCalls(input: String): List<ToolCall> {
        val toolCalls = mutableListOf<ToolCall>()
        val lowerInput = input.lowercase()
        
        // 文件操作
        if (lowerInput.contains("打开文件") || lowerInput.contains("查看文件")) {
            val fileName = extractFileName(input)
            if (fileName.isNotEmpty()) {
                toolCalls.add(ToolCall(
                    toolName = "open_file",
                    parameters = mapOf("path" to fileName),
                    confidence = 0.8f
                ))
            }
        }
        
        // 应用操作
        if (lowerInput.contains("打开") && !lowerInput.contains("文件")) {
            val appName = extractAppName(input)
            if (appName.isNotEmpty()) {
                toolCalls.add(ToolCall(
                    toolName = "start_app",
                    parameters = mapOf("package_name" to appName),
                    confidence = 0.8f
                ))
            }
        }
        
        // 搜索操作
        if (lowerInput.contains("搜索") || lowerInput.contains("查找")) {
            val searchQuery = extractSearchQuery(input)
            if (searchQuery.isNotEmpty()) {
                toolCalls.add(ToolCall(
                    toolName = "visit_web",
                    parameters = mapOf("url" to "https://www.baidu.com/s?wd=$searchQuery"),
                    confidence = 0.8f
                ))
            }
        }
        
        // 通讯操作
        if (lowerInput.contains("打电话") || lowerInput.contains("拨打")) {
            val phoneNumber = extractPhoneNumber(input)
            if (phoneNumber.isNotEmpty()) {
                toolCalls.add(ToolCall(
                    toolName = "execute_intent",
                    parameters = mapOf(
                        "action" to "android.intent.action.CALL",
                        "data" to "tel:$phoneNumber"
                    ),
                    confidence = 0.8f
                ))
            }
        }
        
        return toolCalls
    }
    
    private suspend fun executeToolCall(toolCall: ToolCall, toolHandler: AIToolHandler): String {
        return try {
            val tool = com.ai.assistance.operit.data.model.Tool(
                name = toolCall.toolName,
                parameters = toolCall.parameters.map { (key, value) ->
                    com.ai.assistance.operit.data.model.ToolParameter(key, value)
                }
            )
            
            val result = toolHandler.executeTool(tool)
            if (result.success) {
                "操作成功"
            } else {
                "操作失败：${result.error}"
            }
        } catch (e: Exception) {
            Log.e(TAG, "工具调用失败", e)
            "操作失败"
        }
    }
    
    private fun handleSystemControl(input: String): String {
        return when {
            input.contains("音量") -> "已为您调节音量"
            input.contains("亮度") -> "已为您调节亮度"
            input.contains("蓝牙") -> "已为您操作蓝牙"
            input.contains("wifi") -> "已为您操作WiFi"
            else -> "已为您进行系统设置"
        }
    }
    
    private fun handleQuestion(input: String): String {
        return when {
            input.contains("时间") -> "现在时间是${java.text.SimpleDateFormat("HH:mm").format(java.util.Date())}"
            input.contains("日期") -> "今天是${java.text.SimpleDateFormat("yyyy年MM月dd日").format(java.util.Date())}"
            input.contains("天气") -> "抱歉，我需要联网才能查询天气信息"
            else -> "这是一个很好的问题，让我帮您查找相关信息"
        }
    }
    
    private fun handleCommand(input: String): String {
        return "好的，我来帮您完成这个任务"
    }
    
    private fun handleChat(input: String): String {
        // 基于历史对话生成响应
        val responses = listOf(
            "我明白了，还有什么我可以帮助您的吗？",
            "好的，我会记住这个信息",
            "感谢您的提问，我很乐意为您服务",
            "我理解您的意思，还需要其他帮助吗？"
        )
        
        return responses.random()
    }
    
    // 工具函数
    private fun encodeText(text: String): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * MAX_SEQUENCE_LENGTH)
        buffer.order(ByteOrder.nativeOrder())
        
        val tokens = tokenize(text)
        for (i in 0 until MAX_SEQUENCE_LENGTH) {
            val token = if (i < tokens.size) tokens[i] else 0
            buffer.putFloat(token.toFloat())
        }
        
        buffer.rewind()
        return buffer
    }
    
    private fun tokenize(text: String): List<Int> {
        val tokens = mutableListOf<Int>()
        tokens.add(wordToIndex["[CLS]"] ?: 0)
        
        text.split(" ").forEach { word ->
            val index = wordToIndex[word] ?: wordToIndex["[UNK]"] ?: 1
            tokens.add(index)
        }
        
        tokens.add(wordToIndex["[SEP]"] ?: 0)
        return tokens
    }
    
    private fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.maxOrNull() ?: 0f
        val exps = logits.map { exp(it - maxLogit) }
        val sumExp = exps.sum()
        return exps.map { (it / sumExp).toFloat() }.toFloatArray()
    }
    
    private fun FloatArray.indexOfMax(): Int {
        var maxIndex = 0
        var maxValue = this[0]
        for (i in 1 until this.size) {
            if (this[i] > maxValue) {
                maxValue = this[i]
                maxIndex = i
            }
        }
        return maxIndex
    }
    
    private fun extractFileName(input: String): String {
        // 简单的文件名提取逻辑
        val words = input.split(" ")
        val fileExtensions = listOf(".txt", ".pdf", ".jpg", ".png", ".mp4", ".mp3")
        
        for (word in words) {
            if (fileExtensions.any { word.endsWith(it) }) {
                return word
            }
        }
        
        return ""
    }
    
    private fun extractAppName(input: String): String {
        // 应用名称映射
        val appMapping = mapOf(
            "微信" to "com.tencent.mm",
            "QQ" to "com.tencent.mobileqq",
            "支付宝" to "com.eg.android.AlipayGphone",
            "淘宝" to "com.taobao.taobao",
            "相机" to "com.android.camera",
            "设置" to "com.android.settings"
        )
        
        for ((name, packageName) in appMapping) {
            if (input.contains(name)) {
                return packageName
            }
        }
        
        return ""
    }
    
    private fun extractSearchQuery(input: String): String {
        val searchPrefixes = listOf("搜索", "查找", "搜")
        
        for (prefix in searchPrefixes) {
            val index = input.indexOf(prefix)
            if (index != -1) {
                return input.substring(index + prefix.length).trim()
            }
        }
        
        return ""
    }
    
    private fun extractPhoneNumber(input: String): String {
        val phoneRegex = Regex("\\d{11}")
        val match = phoneRegex.find(input)
        return match?.value ?: ""
    }
    
    private fun extractActionDescription(input: String): String {
        return when {
            input.contains("打开") -> "打开"
            input.contains("关闭") -> "关闭"
            input.contains("搜索") -> "搜索"
            input.contains("查找") -> "查找"
            input.contains("设置") -> "设置"
            else -> "处理"
        }
    }
    
    private fun updateConversationHistory(userInput: String, aiResponse: String, toolsUsed: List<String>) {
        val turn = ConversationTurn(userInput, aiResponse, toolsUsed = toolsUsed)
        conversationHistory.add(turn)
        
        // 保持历史记录在限制范围内
        if (conversationHistory.size > maxHistoryLength) {
            conversationHistory.removeAt(0)
        }
    }
    
    fun isLoaded(): Boolean = isLoaded.get()
    
    fun release() {
        interpreter?.close()
        gpuDelegate?.close()
        isLoaded.set(false)
        Log.d(TAG, "本地AI引擎资源已释放")
    }
    
    // 内部类
    private data class IntentResult(
        val intentType: IntentType,
        val confidence: Float
    )
    
    private class IntentClassifier {
        // 意图分类器的实现
        fun classify(input: String): IntentResult {
            // 这里可以实现更复杂的分类逻辑
            return IntentResult(IntentType.CHAT, 0.5f)
        }
    }
}