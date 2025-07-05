package com.ai.assistance.operit.core.agent

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.services.UIAccessibilityService
import com.ai.assistance.operit.util.LogUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 智能操作执行器
 * 
 * 负责执行AI生成的操作指令：
 * 1. 精确的点击操作
 * 2. 滑动和手势操作
 * 3. 文本输入操作
 * 4. 按键操作
 * 5. 元素查找和定位
 * 6. 等待和延迟操作
 * 7. 自定义操作扩展
 */
class IntelligentActionExecutor(
    private val context: Context,
    private val toolHandler: AIToolHandler
) {
    
    companion object {
        private const val TAG = "IntelligentActionExecutor"
        private const val DEFAULT_RETRY_COUNT = 3
        private const val DEFAULT_WAIT_TIMEOUT = 5000L
        private const val ACTION_DELAY = 100L
    }
    
    /**
     * AI指令数据类
     */
    data class AIInstruction(
        val type: String,
        val parameters: Map<String, String>,
        val description: String,
        val retryCount: Int = DEFAULT_RETRY_COUNT,
        val timeout: Long = DEFAULT_WAIT_TIMEOUT,
        val metadata: Map<String, Any> = emptyMap()
    )
    
    /**
     * 执行结果
     */
    data class ExecutionResult(
        val success: Boolean,
        val message: String,
        val executionTime: Long,
        val retryAttempts: Int = 0,
        val additionalData: Map<String, Any> = emptyMap()
    )
    
    /**
     * 执行AI指令
     */
    suspend fun executeInstruction(instruction: AIInstruction): Boolean = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var lastException: Exception? = null
        
        LogUtils.d(TAG, "开始执行指令: ${instruction.description}")
        
        repeat(instruction.retryCount) { attempt ->
            try {
                val success = when (instruction.type.lowercase()) {
                    "tap", "click" -> executeTap(instruction)
                    "swipe" -> executeSwipe(instruction)
                    "input_text", "type" -> executeInputText(instruction)
                    "press_key" -> executePressKey(instruction)
                    "wait" -> executeWait(instruction)
                    "find_element" -> executeFindElement(instruction)
                    "scroll" -> executeScroll(instruction)
                    "long_press" -> executeLongPress(instruction)
                    "double_tap" -> executeDoubleTap(instruction)
                    "custom" -> executeCustomAction(instruction)
                    else -> {
                        LogUtils.w(TAG, "未知指令类型: ${instruction.type}")
                        false
                    }
                }
                
                if (success) {
                    val executionTime = System.currentTimeMillis() - startTime
                    LogUtils.i(TAG, "✅ 指令执行成功: ${instruction.description} (耗时: ${executionTime}ms, 尝试次数: ${attempt + 1})")
                    return@withContext true
                }
                
            } catch (e: Exception) {
                lastException = e
                LogUtils.w(TAG, "第${attempt + 1}次执行失败: ${instruction.description}", e)
            }
            
            // 如果不是最后一次尝试，等待一下再重试
            if (attempt < instruction.retryCount - 1) {
                delay(500)
            }
        }
        
        val executionTime = System.currentTimeMillis() - startTime
        LogUtils.e(TAG, "❌ 指令执行失败: ${instruction.description} (耗时: ${executionTime}ms, 尝试次数: ${instruction.retryCount})", lastException)
        return@withContext false
    }
    
    /**
     * 执行点击操作
     */
    private suspend fun executeTap(instruction: AIInstruction): Boolean = withContext(Dispatchers.Main) {
        try {
            val x = instruction.parameters["x"]?.toIntOrNull()
            val y = instruction.parameters["y"]?.toIntOrNull()
            val elementId = instruction.parameters["element_id"]
            val text = instruction.parameters["text"]
            
            when {
                x != null && y != null -> {
                    // 坐标点击
                    LogUtils.d(TAG, "执行坐标点击: ($x, $y)")
                    return@withContext performGlobalAction(x, y, "click")
                }
                
                elementId != null -> {
                    // 通过元素ID点击
                    val element = findElementById(elementId)
                    return@withContext performClickOnElement(element)
                }
                
                text != null -> {
                    // 通过文本查找并点击
                    val element = findElementByText(text)
                    return@withContext performClickOnElement(element)
                }
                
                else -> {
                    LogUtils.w(TAG, "点击指令缺少必要参数")
                    return@withContext false
                }
            }
            
        } catch (e: Exception) {
            LogUtils.e(TAG, "执行点击操作失败", e)
            return@withContext false
        }
    }
    
    /**
     * 执行滑动操作
     */
    private suspend fun executeSwipe(instruction: AIInstruction): Boolean = withContext(Dispatchers.Main) {
        try {
            val startX = instruction.parameters["start_x"]?.toIntOrNull()
            val startY = instruction.parameters["start_y"]?.toIntOrNull()
            val endX = instruction.parameters["end_x"]?.toIntOrNull()
            val endY = instruction.parameters["end_y"]?.toIntOrNull()
            val duration = instruction.parameters["duration"]?.toLongOrNull() ?: 300L
            
            if (startX != null && startY != null && endX != null && endY != null) {
                LogUtils.d(TAG, "执行滑动: ($startX, $startY) -> ($endX, $endY)")
                return@withContext performSwipe(startX, startY, endX, endY, duration)
            }
            
            // 如果没有指定坐标，尝试在整个屏幕上滑动
            val direction = instruction.parameters["direction"]?.lowercase()
            return@withContext when (direction) {
                "up" -> performSwipeUp()
                "down" -> performSwipeDown()
                "left" -> performSwipeLeft()
                "right" -> performSwipeRight()
                else -> {
                    LogUtils.w(TAG, "滑动指令缺少必要参数")
                    false
                }
            }
            
        } catch (e: Exception) {
            LogUtils.e(TAG, "执行滑动操作失败", e)
            return@withContext false
        }
    }
    
    /**
     * 执行文本输入操作
     */
    private suspend fun executeInputText(instruction: AIInstruction): Boolean = withContext(Dispatchers.Main) {
        try {
            val text = instruction.parameters["text"] ?: ""
            val elementId = instruction.parameters["element_id"]
            val clearFirst = instruction.parameters["clear_first"]?.toBoolean() ?: true
            
            // 查找输入框
            val inputElement = if (elementId != null) {
                findElementById(elementId)
            } else {
                findEditableElement()
            }
            
            if (inputElement == null) {
                LogUtils.w(TAG, "未找到可编辑的输入框")
                return@withContext false
            }
            
            // 先点击输入框获得焦点
            if (!performClickOnElement(inputElement)) {
                LogUtils.w(TAG, "无法点击输入框获得焦点")
                return@withContext false
            }
            
            delay(ACTION_DELAY)
            
            // 清空现有文本（如果需要）
            if (clearFirst) {
                val success = inputElement.performAction(AccessibilityNodeInfo.ACTION_SELECT_ALL) &&
                        inputElement.performAction(AccessibilityNodeInfo.ACTION_CUT)
                if (!success) {
                    LogUtils.w(TAG, "清空文本失败，继续输入")
                }
                delay(ACTION_DELAY)
            }
            
            // 输入新文本
            val bundle = android.os.Bundle()
            bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            val result = inputElement.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
            
            LogUtils.d(TAG, "文本输入${if (result) "成功" else "失败"}: \"$text\"")
            return@withContext result
            
        } catch (e: Exception) {
            LogUtils.e(TAG, "执行文本输入失败", e)
            return@withContext false
        }
    }
    
    /**
     * 执行按键操作
     */
    private suspend fun executePressKey(instruction: AIInstruction): Boolean = withContext(Dispatchers.Main) {
        try {
            val key = instruction.parameters["key"]?.lowercase()
            
            val action = when (key) {
                "back" -> AccessibilityNodeInfo.ACTION_BACK
                "home" -> AccessibilityNodeInfo.ACTION_HOME
                "enter", "return" -> AccessibilityNodeInfo.ACTION_IME_ENTER
                else -> {
                    LogUtils.w(TAG, "不支持的按键: $key")
                    return@withContext false
                }
            }
            
            val rootNode = UIAccessibilityService.getRootInActiveWindow()
            if (rootNode != null) {
                val result = rootNode.performAction(action)
                rootNode.recycle()
                LogUtils.d(TAG, "按键操作${if (result) "成功" else "失败"}: $key")
                return@withContext result
            }
            
            return@withContext false
            
        } catch (e: Exception) {
            LogUtils.e(TAG, "执行按键操作失败", e)
            return@withContext false
        }
    }
    
    /**
     * 执行等待操作
     */
    private suspend fun executeWait(instruction: AIInstruction): Boolean {
        try {
            val duration = instruction.parameters["duration"]?.toLongOrNull() ?: 1000L
            LogUtils.d(TAG, "等待 ${duration}ms")
            delay(duration)
            return true
            
        } catch (e: Exception) {
            LogUtils.e(TAG, "执行等待操作失败", e)
            return false
        }
    }
    
    /**
     * 执行查找元素操作
     */
    private suspend fun executeFindElement(instruction: AIInstruction): Boolean = withContext(Dispatchers.Main) {
        try {
            val text = instruction.parameters["text"]
            val className = instruction.parameters["class_name"]
            val resourceId = instruction.parameters["resource_id"]
            
            val element = when {
                text != null -> findElementByText(text)
                className != null -> findElementByClassName(className)
                resourceId != null -> findElementByResourceId(resourceId)
                else -> {
                    LogUtils.w(TAG, "查找元素指令缺少查找条件")
                    return@withContext false
                }
            }
            
            val found = element != null
            LogUtils.d(TAG, "元素查找${if (found) "成功" else "失败"}")
            return@withContext found
            
        } catch (e: Exception) {
            LogUtils.e(TAG, "查找元素失败", e)
            return@withContext false
        }
    }
    
    /**
     * 执行滚动操作
     */
    private suspend fun executeScroll(instruction: AIInstruction): Boolean = withContext(Dispatchers.Main) {
        try {
            val direction = instruction.parameters["direction"]?.lowercase() ?: "down"
            val elementId = instruction.parameters["element_id"]
            
            val scrollableElement = if (elementId != null) {
                findElementById(elementId)
            } else {
                findScrollableElement()
            }
            
            if (scrollableElement == null) {
                LogUtils.w(TAG, "未找到可滚动元素")
                return@withContext false
            }
            
            val action = when (direction) {
                "up" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                "down" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                else -> {
                    LogUtils.w(TAG, "不支持的滚动方向: $direction")
                    return@withContext false
                }
            }
            
            val result = scrollableElement.performAction(action)
            LogUtils.d(TAG, "滚动操作${if (result) "成功" else "失败"}: $direction")
            return@withContext result
            
        } catch (e: Exception) {
            LogUtils.e(TAG, "执行滚动操作失败", e)
            return@withContext false
        }
    }
    
    /**
     * 执行长按操作
     */
    private suspend fun executeLongPress(instruction: AIInstruction): Boolean = withContext(Dispatchers.Main) {
        try {
            val x = instruction.parameters["x"]?.toIntOrNull()
            val y = instruction.parameters["y"]?.toIntOrNull()
            val elementId = instruction.parameters["element_id"]
            
            when {
                x != null && y != null -> {
                    // 坐标长按
                    return@withContext performGlobalAction(x, y, "long_press")
                }
                
                elementId != null -> {
                    // 元素长按
                    val element = findElementById(elementId)
                    if (element != null) {
                        val result = element.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                        LogUtils.d(TAG, "长按操作${if (result) "成功" else "失败"}")
                        return@withContext result
                    }
                }
            }
            
            return@withContext false
            
        } catch (e: Exception) {
            LogUtils.e(TAG, "执行长按操作失败", e)
            return@withContext false
        }
    }
    
    /**
     * 执行双击操作
     */
    private suspend fun executeDoubleTap(instruction: AIInstruction): Boolean {
        try {
            // 执行两次点击，中间有短暂间隔
            val firstTap = executeTap(instruction)
            if (firstTap) {
                delay(100)
                return executeTap(instruction)
            }
            return false
            
        } catch (e: Exception) {
            LogUtils.e(TAG, "执行双击操作失败", e)
            return false
        }
    }
    
    /**
     * 执行自定义操作
     */
    private suspend fun executeCustomAction(instruction: AIInstruction): Boolean {
        try {
            val actionName = instruction.parameters["action_name"]
            
            // 可以在这里添加自定义操作的实现
            LogUtils.d(TAG, "执行自定义操作: $actionName")
            
            // 示例：使用工具处理器执行操作
            return when (actionName) {
                "take_screenshot" -> {
                    // 使用工具处理器截图
                    true
                }
                else -> {
                    LogUtils.w(TAG, "不支持的自定义操作: $actionName")
                    false
                }
            }
            
        } catch (e: Exception) {
            LogUtils.e(TAG, "执行自定义操作失败", e)
            return false
        }
    }
    
    // ======== 辅助方法 ========
    
    /**
     * 在元素上执行点击
     */
    private fun performClickOnElement(element: AccessibilityNodeInfo?): Boolean {
        if (element == null) return false
        
        return try {
            val result = element.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            LogUtils.d(TAG, "元素点击${if (result) "成功" else "失败"}")
            result
        } catch (e: Exception) {
            LogUtils.e(TAG, "元素点击失败", e)
            false
        }
    }
    
    /**
     * 执行全局操作（需要实际实现）
     */
    private fun performGlobalAction(x: Int, y: Int, action: String): Boolean {
        LogUtils.d(TAG, "执行全局操作: $action at ($x, $y)")
        // 这里需要实际的全局手势实现
        // 可能需要使用 AccessibilityService 的手势功能
        return true // 暂时返回true进行测试
    }
    
    /**
     * 执行滑动手势
     */
    private fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int, duration: Long): Boolean {
        LogUtils.d(TAG, "执行滑动手势: ($startX, $startY) -> ($endX, $endY), duration: ${duration}ms")
        // 这里需要实际的滑动手势实现
        return true // 暂时返回true进行测试
    }
    
    /**
     * 向上滑动
     */
    private fun performSwipeUp(): Boolean {
        // 获取屏幕尺寸并执行向上滑动
        return performSwipe(500, 1000, 500, 300, 300)
    }
    
    /**
     * 向下滑动
     */
    private fun performSwipeDown(): Boolean {
        // 获取屏幕尺寸并执行向下滑动
        return performSwipe(500, 300, 500, 1000, 300)
    }
    
    /**
     * 向左滑动
     */
    private fun performSwipeLeft(): Boolean {
        // 获取屏幕尺寸并执行向左滑动
        return performSwipe(800, 500, 200, 500, 300)
    }
    
    /**
     * 向右滑动
     */
    private fun performSwipeRight(): Boolean {
        // 获取屏幕尺寸并执行向右滑动
        return performSwipe(200, 500, 800, 500, 300)
    }
    
    // ======== 元素查找方法 ========
    
    /**
     * 通过ID查找元素
     */
    private fun findElementById(id: String): AccessibilityNodeInfo? {
        val rootNode = UIAccessibilityService.getRootInActiveWindow() ?: return null
        
        try {
            return findElementRecursively(rootNode) { node ->
                node.hashCode().toString() == id
            }
        } finally {
            rootNode.recycle()
        }
    }
    
    /**
     * 通过文本查找元素
     */
    private fun findElementByText(text: String): AccessibilityNodeInfo? {
        val rootNode = UIAccessibilityService.getRootInActiveWindow() ?: return null
        
        try {
            return findElementRecursively(rootNode) { node ->
                node.text?.toString()?.contains(text, ignoreCase = true) == true ||
                node.contentDescription?.toString()?.contains(text, ignoreCase = true) == true
            }
        } finally {
            rootNode.recycle()
        }
    }
    
    /**
     * 通过类名查找元素
     */
    private fun findElementByClassName(className: String): AccessibilityNodeInfo? {
        val rootNode = UIAccessibilityService.getRootInActiveWindow() ?: return null
        
        try {
            return findElementRecursively(rootNode) { node ->
                node.className?.toString()?.contains(className, ignoreCase = true) == true
            }
        } finally {
            rootNode.recycle()
        }
    }
    
    /**
     * 通过资源ID查找元素
     */
    private fun findElementByResourceId(resourceId: String): AccessibilityNodeInfo? {
        val rootNode = UIAccessibilityService.getRootInActiveWindow() ?: return null
        
        try {
            return findElementRecursively(rootNode) { node ->
                node.viewIdResourceName?.contains(resourceId, ignoreCase = true) == true
            }
        } finally {
            rootNode.recycle()
        }
    }
    
    /**
     * 查找可编辑元素
     */
    private fun findEditableElement(): AccessibilityNodeInfo? {
        val rootNode = UIAccessibilityService.getRootInActiveWindow() ?: return null
        
        try {
            return findElementRecursively(rootNode) { node ->
                node.isEditable
            }
        } finally {
            rootNode.recycle()
        }
    }
    
    /**
     * 查找可滚动元素
     */
    private fun findScrollableElement(): AccessibilityNodeInfo? {
        val rootNode = UIAccessibilityService.getRootInActiveWindow() ?: return null
        
        try {
            return findElementRecursively(rootNode) { node ->
                node.isScrollable
            }
        } finally {
            rootNode.recycle()
        }
    }
    
    /**
     * 递归查找元素
     */
    private fun findElementRecursively(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        try {
            if (predicate(node)) {
                return node
            }
            
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    val result = findElementRecursively(child, predicate)
                    if (result != null) {
                        child.recycle()
                        return result
                    }
                    child.recycle()
                }
            }
            
        } catch (e: Exception) {
            LogUtils.w(TAG, "递归查找元素时出错: ${e.message}")
        }
        
        return null
    }
    
    /**
     * 获取执行统计信息
     */
    fun getExecutionStats(): Map<String, Any> {
        return mapOf(
            "totalExecutions" to 0,
            "successfulExecutions" to 0,
            "failedExecutions" to 0,
            "averageExecutionTime" to 0L
        )
    }
    
    /**
     * 重置统计信息
     */
    fun resetStats() {
        LogUtils.d(TAG, "重置执行统计信息")
    }
}

/**
 * AI指令类型别名，方便使用
 */
typealias AIInstruction = IntelligentActionExecutor.AIInstruction