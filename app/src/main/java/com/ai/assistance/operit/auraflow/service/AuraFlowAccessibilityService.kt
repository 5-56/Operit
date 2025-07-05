package com.ai.assistance.operit.auraflow.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

/**
 * AuraFlow Agent 无障碍服务
 * 用于执行自动化操作和监控UI变化
 */
class AuraFlowAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "AuraFlowAccessibilityService"
        private var instance: AuraFlowAccessibilityService? = null
        
        fun getInstance(): AuraFlowAccessibilityService? = instance
        
        fun isServiceEnabled(): Boolean = instance != null
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // UI事件流
    private val _uiEvents = MutableSharedFlow<AccessibilityEvent>()
    val uiEvents: SharedFlow<AccessibilityEvent> = _uiEvents.asSharedFlow()
    
    // 窗口变化流
    private val _windowChanges = MutableSharedFlow<AccessibilityNodeInfo?>()
    val windowChanges: SharedFlow<AccessibilityNodeInfo?> = _windowChanges.asSharedFlow()
    
    // 操作结果回调
    private val operationCallbacks = ConcurrentHashMap<String, (Boolean, String?) -> Unit>()
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "AuraFlow 无障碍服务已连接")
        
        // 初始化服务
        initializeService()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
        Log.d(TAG, "AuraFlow 无障碍服务已销毁")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let { accessibilityEvent ->
            // 发送UI事件到流
            serviceScope.launch {
                _uiEvents.emit(accessibilityEvent)
                
                // 如果是窗口变化事件，发送窗口信息
                if (accessibilityEvent.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                    accessibilityEvent.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                    
                    val rootNode = rootInActiveWindow
                    _windowChanges.emit(rootNode)
                }
            }
            
            Log.v(TAG, "无障碍事件: ${getEventTypeString(accessibilityEvent.eventType)} - ${accessibilityEvent.packageName}")
        }
    }
    
    override fun onInterrupt() {
        Log.w(TAG, "AuraFlow 无障碍服务被中断")
    }
    
    /**
     * 初始化服务
     */
    private fun initializeService() {
        serviceScope.launch {
            Log.d(TAG, "无障碍服务初始化完成")
            
            // 发送初始窗口信息
            delay(1000) // 等待服务完全启动
            val rootNode = rootInActiveWindow
            _windowChanges.emit(rootNode)
        }
    }
    
    /**
     * 执行点击操作
     */
    fun performClick(x: Int, y: Int, operationId: String? = null): Boolean {
        return try {
            Log.d(TAG, "执行点击操作: ($x, $y)")
            
            val path = Path().apply {
                moveTo(x.toFloat(), y.toFloat())
            }
            
            val gestureBuilder = GestureDescription.Builder()
            val stroke = GestureDescription.StrokeDescription(path, 0, 100)
            gestureBuilder.addStroke(stroke)
            
            val success = dispatchGesture(
                gestureBuilder.build(),
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        Log.d(TAG, "点击手势完成")
                        operationId?.let { id ->
                            operationCallbacks[id]?.invoke(true, null)
                            operationCallbacks.remove(id)
                        }
                    }
                    
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        Log.w(TAG, "点击手势被取消")
                        operationId?.let { id ->
                            operationCallbacks[id]?.invoke(false, "手势被取消")
                            operationCallbacks.remove(id)
                        }
                    }
                },
                null
            )
            
            if (!success) {
                Log.e(TAG, "无法分发点击手势")
                operationId?.let { id ->
                    operationCallbacks[id]?.invoke(false, "无法分发手势")
                    operationCallbacks.remove(id)
                }
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "执行点击操作失败", e)
            operationId?.let { id ->
                operationCallbacks[id]?.invoke(false, "执行失败: ${e.message}")
                operationCallbacks.remove(id)
            }
            false
        }
    }
    
    /**
     * 执行滑动操作
     */
    fun performSwipe(
        startX: Int, startY: Int,
        endX: Int, endY: Int,
        duration: Long = 500,
        operationId: String? = null
    ): Boolean {
        return try {
            Log.d(TAG, "执行滑动操作: ($startX, $startY) -> ($endX, $endY), 时长: ${duration}ms")
            
            val path = Path().apply {
                moveTo(startX.toFloat(), startY.toFloat())
                lineTo(endX.toFloat(), endY.toFloat())
            }
            
            val gestureBuilder = GestureDescription.Builder()
            val stroke = GestureDescription.StrokeDescription(path, 0, duration)
            gestureBuilder.addStroke(stroke)
            
            val success = dispatchGesture(
                gestureBuilder.build(),
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        Log.d(TAG, "滑动手势完成")
                        operationId?.let { id ->
                            operationCallbacks[id]?.invoke(true, null)
                            operationCallbacks.remove(id)
                        }
                    }
                    
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        Log.w(TAG, "滑动手势被取消")
                        operationId?.let { id ->
                            operationCallbacks[id]?.invoke(false, "手势被取消")
                            operationCallbacks.remove(id)
                        }
                    }
                },
                null
            )
            
            if (!success) {
                Log.e(TAG, "无法分发滑动手势")
                operationId?.let { id ->
                    operationCallbacks[id]?.invoke(false, "无法分发手势")
                    operationCallbacks.remove(id)
                }
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "执行滑动操作失败", e)
            operationId?.let { id ->
                operationCallbacks[id]?.invoke(false, "执行失败: ${e.message}")
                operationCallbacks.remove(id)
            }
            false
        }
    }
    
    /**
     * 执行长按操作
     */
    fun performLongPress(x: Int, y: Int, duration: Long = 1000, operationId: String? = null): Boolean {
        return try {
            Log.d(TAG, "执行长按操作: ($x, $y), 时长: ${duration}ms")
            
            val path = Path().apply {
                moveTo(x.toFloat(), y.toFloat())
            }
            
            val gestureBuilder = GestureDescription.Builder()
            val stroke = GestureDescription.StrokeDescription(path, 0, duration)
            gestureBuilder.addStroke(stroke)
            
            val success = dispatchGesture(
                gestureBuilder.build(),
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        Log.d(TAG, "长按手势完成")
                        operationId?.let { id ->
                            operationCallbacks[id]?.invoke(true, null)
                            operationCallbacks.remove(id)
                        }
                    }
                    
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        Log.w(TAG, "长按手势被取消")
                        operationId?.let { id ->
                            operationCallbacks[id]?.invoke(false, "手势被取消")
                            operationCallbacks.remove(id)
                        }
                    }
                },
                null
            )
            
            if (!success) {
                Log.e(TAG, "无法分发长按手势")
                operationId?.let { id ->
                    operationCallbacks[id]?.invoke(false, "无法分发手势")
                    operationCallbacks.remove(id)
                }
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "执行长按操作失败", e)
            operationId?.let { id ->
                operationCallbacks[id]?.invoke(false, "执行失败: ${e.message}")
                operationCallbacks.remove(id)
            }
            false
        }
    }
    
    /**
     * 查找UI元素
     */
    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        return try {
            val rootNode = rootInActiveWindow ?: return null
            findNodeRecursively(rootNode, text)
        } catch (e: Exception) {
            Log.e(TAG, "查找UI元素失败", e)
            null
        }
    }
    
    /**
     * 查找UI元素通过资源ID
     */
    fun findNodeByResourceId(resourceId: String): AccessibilityNodeInfo? {
        return try {
            val rootNode = rootInActiveWindow ?: return null
            findNodeByResourceIdRecursively(rootNode, resourceId)
        } catch (e: Exception) {
            Log.e(TAG, "通过资源ID查找UI元素失败", e)
            null
        }
    }
    
    /**
     * 获取当前窗口信息
     */
    fun getCurrentWindowInfo(): WindowInfo? {
        return try {
            val rootNode = rootInActiveWindow ?: return null
            
            WindowInfo(
                packageName = rootNode.packageName?.toString() ?: "",
                className = rootNode.className?.toString() ?: "",
                bounds = Rect().apply { rootNode.getBoundsInScreen(this) },
                isScrollable = rootNode.isScrollable,
                childCount = rootNode.childCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "获取窗口信息失败", e)
            null
        }
    }
    
    /**
     * 注册操作回调
     */
    fun registerOperationCallback(operationId: String, callback: (Boolean, String?) -> Unit) {
        operationCallbacks[operationId] = callback
    }
    
    /**
     * 递归查找节点
     */
    private fun findNodeRecursively(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.text?.toString()?.contains(text, ignoreCase = true) == true ||
            node.contentDescription?.toString()?.contains(text, ignoreCase = true) == true) {
            return node
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            child?.let { childNode ->
                val result = findNodeRecursively(childNode, text)
                if (result != null) {
                    return result
                }
            }
        }
        
        return null
    }
    
    /**
     * 通过资源ID递归查找节点
     */
    private fun findNodeByResourceIdRecursively(node: AccessibilityNodeInfo, resourceId: String): AccessibilityNodeInfo? {
        if (node.viewIdResourceName?.contains(resourceId) == true) {
            return node
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            child?.let { childNode ->
                val result = findNodeByResourceIdRecursively(childNode, resourceId)
                if (result != null) {
                    return result
                }
            }
        }
        
        return null
    }
    
    /**
     * 获取事件类型字符串
     */
    private fun getEventTypeString(eventType: Int): String {
        return when (eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "VIEW_CLICKED"
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> "VIEW_LONG_CLICKED"
            AccessibilityEvent.TYPE_VIEW_SELECTED -> "VIEW_SELECTED"
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> "VIEW_FOCUSED"
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "VIEW_TEXT_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_STATE_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "WINDOW_CONTENT_CHANGED"
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> "VIEW_SCROLLED"
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> "VIEW_TEXT_SELECTION_CHANGED"
            else -> "UNKNOWN($eventType)"
        }
    }
    
    /**
     * 窗口信息数据类
     */
    data class WindowInfo(
        val packageName: String,
        val className: String,
        val bounds: Rect,
        val isScrollable: Boolean,
        val childCount: Int
    )
}