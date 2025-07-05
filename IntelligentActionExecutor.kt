package com.ai.assistance.operit.core.agent

import android.content.Context
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.ui.common.displays.UIOperationOverlay
import com.ai.assistance.operit.util.LogUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * 智能操作执行器
 * 负责接收AI指令并精确执行各种操作，提供实时反馈
 */
class IntelligentActionExecutor(
    private val context: Context,
    private val toolHandler: AIToolHandler
) {
    
    companion object {
        private const val TAG = "IntelligentActionExecutor"
        private const val DEFAULT_OPERATION_TIMEOUT = 30000L // 30秒超时
        private const val RETRY_MAX_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1000L
    }
    
    private val operationOverlay = UIOperationOverlay(context)
    private val executionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    /**
     * 执行状态
     */
    sealed class ExecutionState {
        object Idle : ExecutionState()
        object Executing : ExecutionState()
        object Success : ExecutionState()
        data class Failed(val error: String) : ExecutionState()
        data class Retry(val attempt: Int, val maxAttempts: Int) : ExecutionState()
    }
    
    /**
     * 操作反馈数据
     */
    data class OperationFeedback(
        val operationId: String,
        val state: ExecutionState,
        val result: ToolResult?,
        val visualFeedback: VisualFeedback?,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * 视觉反馈数据
     */
    data class VisualFeedback(
        val type: String, // tap, swipe, input, highlight
        val coordinates: Pair<Int, Int>?,
        val startCoordinates: Pair<Int, Int>? = null,
        val endCoordinates: Pair<Int, Int>? = null,
        val duration: Long = 1500L
    )
    
    private val _operationFeedback = MutableSharedFlow<OperationFeedback>(replay = 1)
    val operationFeedback: SharedFlow<OperationFeedback> = _operationFeedback.asSharedFlow()
    
    /**
     * 执行AI指令
     */
    suspend fun executeAIInstruction(
        instruction: AIInstruction
    ): OperationFeedback = withContext(Dispatchers.IO) {
        
        val operationId = generateOperationId()
        LogUtils.d(TAG, "开始执行AI指令: ${instruction.type}")
        
        try {
            // 发送开始执行反馈
            val startFeedback = OperationFeedback(
                operationId = operationId,
                state = ExecutionState.Executing,
                result = null,
                visualFeedback = null
            )
            _operationFeedback.emit(startFeedback)
            
            // 执行操作（带重试机制）
            val result = executeWithRetry(instruction)
            
            // 生成视觉反馈
            val visualFeedback = generateVisualFeedback(instruction, result)
            
            // 显示操作反馈
            showOperationFeedback(visualFeedback)
            
            // 生成成功反馈
            val successFeedback = OperationFeedback(
                operationId = operationId,
                state = ExecutionState.Success,
                result = result,
                visualFeedback = visualFeedback
            )
            
            _operationFeedback.emit(successFeedback)
            LogUtils.d(TAG, "AI指令执行成功: ${instruction.type}")
            
            return@withContext successFeedback
            
        } catch (e: Exception) {
            LogUtils.e(TAG, "AI指令执行失败: ${instruction.type}", e)
            
            val errorFeedback = OperationFeedback(
                operationId = operationId,
                state = ExecutionState.Failed(e.message ?: "Unknown error"),
                result = null,
                visualFeedback = null
            )
            
            _operationFeedback.emit(errorFeedback)
            return@withContext errorFeedback
        }
    }
    
    /**
     * 带重试机制的执行
     */
    private suspend fun executeWithRetry(instruction: AIInstruction): ToolResult {
        var lastException: Exception? = null
        
        repeat(RETRY_MAX_ATTEMPTS) { attempt ->
            try {
                if (attempt > 0) {
                    // 发送重试反馈
                    _operationFeedback.emit(
                        OperationFeedback(
                            operationId = generateOperationId(),
                            state = ExecutionState.Retry(attempt + 1, RETRY_MAX_ATTEMPTS),
                            result = null,
                            visualFeedback = null
                        )
                    )
                    
                    delay(RETRY_DELAY_MS)
                    LogUtils.d(TAG, "重试执行AI指令: ${instruction.type} (第 ${attempt + 1} 次)")
                }
                
                return executeDirectly(instruction)
                
            } catch (e: Exception) {
                lastException = e
                LogUtils.w(TAG, "执行失败，准备重试: ${e.message}")
            }
        }
        
        throw lastException ?: Exception("执行失败")
    }
    
    /**
     * 直接执行操作
     */
    private suspend fun executeDirectly(instruction: AIInstruction): ToolResult {
        return when (instruction.type) {
            "tap" -> executeTap(instruction)
            "swipe" -> executeSwipe(instruction)
            "input_text" -> executeInputText(instruction)
            "press_key" -> executePressKey(instruction)
            "find_element" -> executeFindElement(instruction)
            "wait" -> executeWait(instruction)
            "custom" -> executeCustomAction(instruction)
            else -> throw IllegalArgumentException("不支持的指令类型: ${instruction.type}")
        }
    }
    
    /**
     * 执行点击操作
     */
    private suspend fun executeTap(instruction: AIInstruction): ToolResult {
        val x = instruction.parameters["x"]?.toIntOrNull() 
            ?: throw IllegalArgumentException("缺少x坐标")
        val y = instruction.parameters["y"]?.toIntOrNull() 
            ?: throw IllegalArgumentException("缺少y坐标")
        
        val tool = AITool(
            name = "tap",
            parameters = listOf(
                ToolParameter("x", x.toString()),
                ToolParameter("y", y.toString())
            )
        )
        
        return toolHandler.executeTool(tool)
    }
    
    /**
     * 执行滑动操作
     */
    private suspend fun executeSwipe(instruction: AIInstruction): ToolResult {
        val startX = instruction.parameters["start_x"]?.toIntOrNull() 
            ?: throw IllegalArgumentException("缺少起始x坐标")
        val startY = instruction.parameters["start_y"]?.toIntOrNull() 
            ?: throw IllegalArgumentException("缺少起始y坐标")
        val endX = instruction.parameters["end_x"]?.toIntOrNull() 
            ?: throw IllegalArgumentException("缺少结束x坐标")
        val endY = instruction.parameters["end_y"]?.toIntOrNull() 
            ?: throw IllegalArgumentException("缺少结束y坐标")
        val duration = instruction.parameters["duration"]?.toIntOrNull() ?: 500
        
        val tool = AITool(
            name = "swipe",
            parameters = listOf(
                ToolParameter("start_x", startX.toString()),
                ToolParameter("start_y", startY.toString()),
                ToolParameter("end_x", endX.toString()),
                ToolParameter("end_y", endY.toString()),
                ToolParameter("duration", duration.toString())
            )
        )
        
        return toolHandler.executeTool(tool)
    }
    
    /**
     * 执行输入文本操作
     */
    private suspend fun executeInputText(instruction: AIInstruction): ToolResult {
        val text = instruction.parameters["text"] 
            ?: throw IllegalArgumentException("缺少输入文本")
        
        val tool = AITool(
            name = "set_input_text",
            parameters = listOf(
                ToolParameter("text", text)
            )
        )
        
        return toolHandler.executeTool(tool)
    }
    
    /**
     * 执行按键操作
     */
    private suspend fun executePressKey(instruction: AIInstruction): ToolResult {
        val keyCode = instruction.parameters["key_code"] 
            ?: throw IllegalArgumentException("缺少按键代码")
        
        val tool = AITool(
            name = "press_key",
            parameters = listOf(
                ToolParameter("key_code", keyCode)
            )
        )
        
        return toolHandler.executeTool(tool)
    }
    
    /**
     * 执行元素查找操作
     */
    private suspend fun executeFindElement(instruction: AIInstruction): ToolResult {
        val parameters = mutableListOf<ToolParameter>()
        
        instruction.parameters["resource_id"]?.let { 
            parameters.add(ToolParameter("resourceId", it))
        }
        instruction.parameters["class_name"]?.let { 
            parameters.add(ToolParameter("className", it))
        }
        instruction.parameters["text"]?.let { 
            parameters.add(ToolParameter("text", it))
        }
        
        if (parameters.isEmpty()) {
            throw IllegalArgumentException("至少需要一个查找条件")
        }
        
        val tool = AITool(
            name = "find_element",
            parameters = parameters
        )
        
        return toolHandler.executeTool(tool)
    }
    
    /**
     * 执行等待操作
     */
    private suspend fun executeWait(instruction: AIInstruction): ToolResult {
        val duration = instruction.parameters["duration"]?.toLongOrNull() ?: 1000L
        val limitedDuration = duration.coerceIn(0L, 10000L) // 最多等待10秒
        
        delay(limitedDuration)
        
        return ToolResult(
            toolName = "wait",
            success = true,
            result = com.ai.assistance.operit.core.tools.StringResultData("等待 ${limitedDuration}ms 完成")
        )
    }
    
    /**
     * 执行自定义操作
     */
    private suspend fun executeCustomAction(instruction: AIInstruction): ToolResult {
        val action = instruction.parameters["action"] 
            ?: throw IllegalArgumentException("缺少自定义操作定义")
        
        // 解析自定义操作JSON
        val actionJson = JSONObject(action)
        val toolName = actionJson.getString("tool")
        val params = actionJson.getJSONObject("parameters")
        
        val parameters = mutableListOf<ToolParameter>()
        params.keys().forEach { key ->
            parameters.add(ToolParameter(key, params.getString(key)))
        }
        
        val tool = AITool(
            name = toolName,
            parameters = parameters
        )
        
        return toolHandler.executeTool(tool)
    }
    
    /**
     * 生成视觉反馈
     */
    private fun generateVisualFeedback(
        instruction: AIInstruction, 
        result: ToolResult
    ): VisualFeedback? {
        if (!result.success) return null
        
        return when (instruction.type) {
            "tap" -> {
                val x = instruction.parameters["x"]?.toIntOrNull() ?: return null
                val y = instruction.parameters["y"]?.toIntOrNull() ?: return null
                VisualFeedback(
                    type = "tap",
                    coordinates = Pair(x, y)
                )
            }
            "swipe" -> {
                val startX = instruction.parameters["start_x"]?.toIntOrNull() ?: return null
                val startY = instruction.parameters["start_y"]?.toIntOrNull() ?: return null
                val endX = instruction.parameters["end_x"]?.toIntOrNull() ?: return null
                val endY = instruction.parameters["end_y"]?.toIntOrNull() ?: return null
                VisualFeedback(
                    type = "swipe",
                    coordinates = null,
                    startCoordinates = Pair(startX, startY),
                    endCoordinates = Pair(endX, endY)
                )
            }
            "input_text" -> {
                // 可以从当前焦点元素获取坐标
                VisualFeedback(
                    type = "input",
                    coordinates = Pair(500, 500) // 示例坐标
                )
            }
            else -> null
        }
    }
    
    /**
     * 显示操作反馈
     */
    private suspend fun showOperationFeedback(visualFeedback: VisualFeedback?) {
        visualFeedback?.let { feedback ->
            withContext(Dispatchers.Main) {
                when (feedback.type) {
                    "tap" -> {
                        feedback.coordinates?.let { (x, y) ->
                            operationOverlay.showTap(x, y, feedback.duration)
                        }
                    }
                    "swipe" -> {
                        val start = feedback.startCoordinates
                        val end = feedback.endCoordinates
                        if (start != null && end != null) {
                            operationOverlay.showSwipe(
                                start.first, start.second,
                                end.first, end.second,
                                feedback.duration
                            )
                        }
                    }
                    "input" -> {
                        feedback.coordinates?.let { (x, y) ->
                            operationOverlay.showTextInput(x, y, "输入文本", feedback.duration)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 生成操作ID
     */
    private fun generateOperationId(): String {
        return "op_${System.currentTimeMillis()}_${(0..999).random()}"
    }
    
    /**
     * 批量执行AI指令序列
     */
    suspend fun executeInstructionSequence(
        instructions: List<AIInstruction>,
        stopOnError: Boolean = false
    ): List<OperationFeedback> = withContext(Dispatchers.IO) {
        
        val results = mutableListOf<OperationFeedback>()
        
        for ((index, instruction) in instructions.withIndex()) {
            LogUtils.d(TAG, "执行指令序列 ${index + 1}/${instructions.size}: ${instruction.type}")
            
            val feedback = executeAIInstruction(instruction)
            results.add(feedback)
            
            // 检查是否应该在错误时停止
            if (stopOnError && feedback.state is ExecutionState.Failed) {
                LogUtils.w(TAG, "指令序列执行失败，停止执行: ${feedback.state.error}")
                break
            }
            
            // 添加指令间的短暂延迟
            if (index < instructions.size - 1) {
                delay(300)
            }
        }
        
        LogUtils.d(TAG, "指令序列执行完成，共执行 ${results.size}/${instructions.size} 条指令")
        return@withContext results
    }
    
    /**
     * 释放资源
     */
    fun release() {
        executionScope.cancel()
        operationOverlay.hide()
    }
}

/**
 * AI指令数据类
 */
data class AIInstruction(
    val type: String,
    val parameters: Map<String, String>,
    val description: String? = null,
    val timeout: Long = 30000L
)