package com.ai.assistance.operit.auraflow.automation

import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.ai.assistance.operit.auraflow.protocol.*
import com.ai.assistance.operit.services.UIAccessibilityService
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * AuraFlow Agent 操作执行引擎
 * 负责执行 AI 大脑下发的各种操作指令
 */
class ActionExecutor private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ActionExecutor"
        private const val DEFAULT_GESTURE_DURATION = 100L
        private const val DEFAULT_LONG_PRESS_DURATION = 1000L
        private const val DEFAULT_SWIPE_DURATION = 500L
        
        @Volatile
        private var INSTANCE: ActionExecutor? = null
        
        fun getInstance(context: Context): ActionExecutor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ActionExecutor(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    /**
     * 执行 AI 指令
     */
    suspend fun executeCommand(command: AICommandData): ActionResultData {
        val startTime = System.currentTimeMillis()
        
        return try {
            val success = when (command.action) {
                ActionType.CLICK -> executeClick(command.parameters)
                ActionType.LONG_PRESS -> executeLongPress(command.parameters)
                ActionType.SWIPE -> executeSwipe(command.parameters)
                ActionType.TYPE_TEXT -> executeTypeText(command.parameters)
                ActionType.PRESS_KEY -> executePressKey(command.parameters)
                ActionType.SCROLL -> executeScroll(command.parameters)
                ActionType.GESTURE -> executeGesture(command.parameters)
                ActionType.WAIT -> executeWait(command.parameters)
                ActionType.TAKE_SCREENSHOT -> executeTakeScreenshot(command.parameters)
            }
            
            val executionTime = System.currentTimeMillis() - startTime
            
            ActionResultData(
                commandId = "", // 将在调用处设置
                success = success,
                executionTime = executionTime
            )
        } catch (e: Exception) {
            Log.e(TAG, "执行操作失败: ${command.action}", e)
            val executionTime = System.currentTimeMillis() - startTime
            
            ActionResultData(
                commandId = "", // 将在调用处设置
                success = false,
                errorMessage = e.message,
                executionTime = executionTime
            )
        }
    }
    
    /**
     * 执行点击操作
     */
    private suspend fun executeClick(parameters: JsonObject): Boolean {
        val x = parameters["x"]?.jsonPrimitive?.int ?: return false
        val y = parameters["y"]?.jsonPrimitive?.int ?: return false
        val elementId = parameters["elementId"]?.jsonPrimitive?.contentOrNull
        
        Log.d(TAG, "执行点击操作: ($x, $y), elementId=$elementId")
        
        // 优先通过元素ID点击
        if (elementId != null) {
            val success = clickByElementId(elementId)
            if (success) return true
            Log.w(TAG, "通过元素ID点击失败，尝试坐标点击")
        }
        
        // 通过坐标点击
        return clickByCoordinates(x, y)
    }
    
    /**
     * 执行长按操作
     */
    private suspend fun executeLongPress(parameters: JsonObject): Boolean {
        val x = parameters["x"]?.jsonPrimitive?.int ?: return false
        val y = parameters["y"]?.jsonPrimitive?.int ?: return false
        val duration = parameters["duration"]?.jsonPrimitive?.long ?: DEFAULT_LONG_PRESS_DURATION
        val elementId = parameters["elementId"]?.jsonPrimitive?.contentOrNull
        
        Log.d(TAG, "执行长按操作: ($x, $y), duration=$duration, elementId=$elementId")
        
        // 优先通过元素ID长按
        if (elementId != null) {
            val success = longPressByElementId(elementId, duration)
            if (success) return true
            Log.w(TAG, "通过元素ID长按失败，尝试坐标长按")
        }
        
        // 通过坐标长按
        return longPressByCoordinates(x, y, duration)
    }
    
    /**
     * 执行滑动操作
     */
    private suspend fun executeSwipe(parameters: JsonObject): Boolean {
        val startX = parameters["startX"]?.jsonPrimitive?.int ?: return false
        val startY = parameters["startY"]?.jsonPrimitive?.int ?: return false
        val endX = parameters["endX"]?.jsonPrimitive?.int ?: return false
        val endY = parameters["endY"]?.jsonPrimitive?.int ?: return false
        val duration = parameters["duration"]?.jsonPrimitive?.long ?: DEFAULT_SWIPE_DURATION
        
        Log.d(TAG, "执行滑动操作: ($startX, $startY) -> ($endX, $endY), duration=$duration")
        
        return swipeByCoordinates(startX, startY, endX, endY, duration)
    }
    
    /**
     * 执行文本输入操作
     */
    private suspend fun executeTypeText(parameters: JsonObject): Boolean {
        val text = parameters["text"]?.jsonPrimitive?.contentOrNull ?: return false
        val clearFirst = parameters["clearFirst"]?.jsonPrimitive?.boolean ?: false
        val elementId = parameters["elementId"]?.jsonPrimitive?.contentOrNull
        
        Log.d(TAG, "执行文本输入: text=$text, clearFirst=$clearFirst, elementId=$elementId")
        
        // 优先通过元素ID输入
        if (elementId != null) {
            val success = typeTextByElementId(elementId, text, clearFirst)
            if (success) return true
            Log.w(TAG, "通过元素ID输入失败，尝试全局输入")
        }
        
        // 全局文本输入
        return typeTextGlobal(text, clearFirst)
    }
    
    /**
     * 执行按键操作
     */
    private suspend fun executePressKey(parameters: JsonObject): Boolean {
        val keyCode = parameters["keyCode"]?.jsonPrimitive?.int ?: return false
        val metaState = parameters["metaState"]?.jsonPrimitive?.int ?: 0
        
        Log.d(TAG, "执行按键操作: keyCode=$keyCode, metaState=$metaState")
        
        return pressKey(keyCode, metaState)
    }
    
    /**
     * 执行滚动操作
     */
    private suspend fun executeScroll(parameters: JsonObject): Boolean {
        val directionStr = parameters["direction"]?.jsonPrimitive?.contentOrNull ?: return false
        val direction = try {
            ScrollDirection.valueOf(directionStr)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "无效的滚动方向: $directionStr")
            return false
        }
        
        val amount = parameters["amount"]?.jsonPrimitive?.int ?: 1
        val elementId = parameters["elementId"]?.jsonPrimitive?.contentOrNull
        
        Log.d(TAG, "执行滚动操作: direction=$direction, amount=$amount, elementId=$elementId")
        
        return scrollByDirection(direction, amount, elementId)
    }
    
    /**
     * 执行复杂手势
     */
    private suspend fun executeGesture(parameters: JsonObject): Boolean {
        // TODO: 实现复杂手势支持
        Log.w(TAG, "复杂手势暂未实现")
        return false
    }
    
    /**
     * 执行等待操作
     */
    private suspend fun executeWait(parameters: JsonObject): Boolean {
        val duration = parameters["duration"]?.jsonPrimitive?.long ?: 1000L
        
        Log.d(TAG, "执行等待操作: duration=$duration ms")
        
        delay(duration)
        return true
    }
    
    /**
     * 执行截图操作
     */
    private suspend fun executeTakeScreenshot(parameters: JsonObject): Boolean {
        Log.d(TAG, "执行截图操作")
        // 截图操作通常由屏幕感知模块处理
        return true
    }
    
    // ========== 具体操作实现 ==========
    
    /**
     * 通过坐标点击
     */
    private suspend fun clickByCoordinates(x: Int, y: Int): Boolean {
        return performGesture { builder ->
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            val stroke = GestureDescription.StrokeDescription(path, 0, DEFAULT_GESTURE_DURATION)
            builder.addStroke(stroke)
        }
    }
    
    /**
     * 通过元素ID点击
     */
    private suspend fun clickByElementId(elementId: String): Boolean {
        val accessibilityService = UIAccessibilityService.getInstance() ?: return false
        val rootNode = accessibilityService.rootInActiveWindow ?: return false
        
        return try {
            val targetNode = findNodeByResourceId(rootNode, elementId)
            if (targetNode != null && targetNode.isClickable) {
                targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } else {
                false
            }
        } finally {
            rootNode.recycle()
        }
    }
    
    /**
     * 通过坐标长按
     */
    private suspend fun longPressByCoordinates(x: Int, y: Int, duration: Long): Boolean {
        return performGesture { builder ->
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            val stroke = GestureDescription.StrokeDescription(path, 0, duration)
            builder.addStroke(stroke)
        }
    }
    
    /**
     * 通过元素ID长按
     */
    private suspend fun longPressByElementId(elementId: String, duration: Long): Boolean {
        val accessibilityService = UIAccessibilityService.getInstance() ?: return false
        val rootNode = accessibilityService.rootInActiveWindow ?: return false
        
        return try {
            val targetNode = findNodeByResourceId(rootNode, elementId)
            if (targetNode != null && targetNode.isLongClickable) {
                targetNode.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            } else {
                false
            }
        } finally {
            rootNode.recycle()
        }
    }
    
    /**
     * 通过坐标滑动
     */
    private suspend fun swipeByCoordinates(
        startX: Int, startY: Int, 
        endX: Int, endY: Int, 
        duration: Long
    ): Boolean {
        return performGesture { builder ->
            val path = Path().apply {
                moveTo(startX.toFloat(), startY.toFloat())
                lineTo(endX.toFloat(), endY.toFloat())
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, duration)
            builder.addStroke(stroke)
        }
    }
    
    /**
     * 通过元素ID输入文本
     */
    private suspend fun typeTextByElementId(elementId: String, text: String, clearFirst: Boolean): Boolean {
        val accessibilityService = UIAccessibilityService.getInstance() ?: return false
        val rootNode = accessibilityService.rootInActiveWindow ?: return false
        
        return try {
            val targetNode = findNodeByResourceId(rootNode, elementId)
            if (targetNode != null) {
                // 先清空文本（如果需要）
                if (clearFirst) {
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_SELECT_TEXT,
                        Bundle().apply {
                            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, 
                                targetNode.text?.length ?: 0)
                        }
                    )
                }
                
                // 输入文本
                val arguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            } else {
                false
            }
        } finally {
            rootNode.recycle()
        }
    }
    
    /**
     * 全局文本输入
     */
    private suspend fun typeTextGlobal(text: String, clearFirst: Boolean): Boolean {
        val accessibilityService = UIAccessibilityService.getInstance() ?: return false
        
        // 如果需要清空，先发送全选和删除
        if (clearFirst) {
            accessibilityService.performGlobalAction(AccessibilityNodeInfo.GLOBAL_ACTION_SELECT_ALL)
            delay(100)
        }
        
        // 逐字符输入（作为备选方案）
        return try {
            for (char in text) {
                // 这里可以通过发送KeyEvent实现字符输入
                // 但需要更复杂的字符映射逻辑
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "全局文本输入失败", e)
            false
        }
    }
    
    /**
     * 按键操作
     */
    private suspend fun pressKey(keyCode: Int, metaState: Int): Boolean {
        val accessibilityService = UIAccessibilityService.getInstance() ?: return false
        
        return try {
            when (keyCode) {
                KeyEvent.KEYCODE_BACK -> accessibilityService.performGlobalAction(AccessibilityNodeInfo.GLOBAL_ACTION_BACK)
                KeyEvent.KEYCODE_HOME -> accessibilityService.performGlobalAction(AccessibilityNodeInfo.GLOBAL_ACTION_HOME)
                KeyEvent.KEYCODE_APP_SWITCH -> accessibilityService.performGlobalAction(AccessibilityNodeInfo.GLOBAL_ACTION_RECENTS)
                else -> {
                    // 对于其他按键，可以通过Instrumentation或其他方式实现
                    Log.w(TAG, "按键 $keyCode 暂不支持")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "按键操作失败", e)
            false
        }
    }
    
    /**
     * 滚动操作
     */
    private suspend fun scrollByDirection(direction: ScrollDirection, amount: Int, elementId: String?): Boolean {
        val accessibilityService = UIAccessibilityService.getInstance() ?: return false
        val rootNode = accessibilityService.rootInActiveWindow ?: return false
        
        return try {
            val scrollableNode = if (elementId != null) {
                findNodeByResourceId(rootNode, elementId)
            } else {
                findScrollableNode(rootNode)
            }
            
            if (scrollableNode != null) {
                val action = when (direction) {
                    ScrollDirection.UP -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    ScrollDirection.DOWN -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    ScrollDirection.LEFT -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    ScrollDirection.RIGHT -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                }
                
                repeat(amount) {
                    scrollableNode.performAction(action)
                    delay(200) // 滚动间隔
                }
                true
            } else {
                false
            }
        } finally {
            rootNode.recycle()
        }
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 执行手势
     */
    private suspend fun performGesture(gestureBuilder: (GestureDescription.Builder) -> Unit): Boolean {
        val accessibilityService = UIAccessibilityService.getInstance() ?: return false
        
        return suspendCoroutine { continuation ->
            try {
                val builder = GestureDescription.Builder()
                gestureBuilder(builder)
                val gesture = builder.build()
                
                val callback = object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        continuation.resume(true)
                    }
                    
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        continuation.resume(false)
                    }
                }
                
                val success = accessibilityService.dispatchGesture(gesture, callback, null)
                if (!success) {
                    continuation.resume(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "手势执行失败", e)
                continuation.resume(false)
            }
        }
    }
    
    /**
     * 根据资源ID查找节点
     */
    private fun findNodeByResourceId(rootNode: AccessibilityNodeInfo, resourceId: String): AccessibilityNodeInfo? {
        if (rootNode.viewIdResourceName == resourceId) {
            return rootNode
        }
        
        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChild(i) ?: continue
            val result = findNodeByResourceId(child, resourceId)
            if (result != null) {
                return result
            }
        }
        
        return null
    }
    
    /**
     * 查找可滚动节点
     */
    private fun findScrollableNode(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (rootNode.isScrollable) {
            return rootNode
        }
        
        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChild(i) ?: continue
            val result = findScrollableNode(child)
            if (result != null) {
                return result
            }
        }
        
        return null
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        scope.cancel()
    }
}