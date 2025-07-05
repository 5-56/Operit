package com.ai.assistance.operit.auraflow.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.math.*

/**
 * 手势类型枚举
 */
enum class GestureType {
    TAP,                // 点击
    LONG_PRESS,         // 长按
    SWIPE,              // 滑动
    PINCH,              // 捏合
    SPREAD,             // 展开
    ROTATE,             // 旋转
    MULTI_TAP,          // 多点击
    DRAG_DROP,          // 拖拽
    FLING,              // 甩动
    CUSTOM_PATH         // 自定义路径
}

/**
 * 手势触摸点
 */
@Serializable
data class TouchPoint(
    val x: Float,
    val y: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val pressure: Float = 1.0f,
    val fingerId: Int = 0
)

/**
 * 手势描述
 */
@Serializable
data class GestureDefinition(
    val id: String,
    val name: String,
    val type: GestureType,
    val points: List<TouchPoint>,
    val duration: Long,
    val delay: Long = 0,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * 手势录制器
 */
class GestureRecorder {
    private var isRecording = false
    private val recordedPoints = mutableListOf<TouchPoint>()
    private var startTime = 0L
    
    fun startRecording(): Boolean {
        if (isRecording) return false
        
        isRecording = true
        recordedPoints.clear()
        startTime = System.currentTimeMillis()
        Log.d("GestureRecorder", "开始录制手势")
        return true
    }
    
    fun addPoint(x: Float, y: Float, fingerId: Int = 0, pressure: Float = 1.0f) {
        if (!isRecording) return
        
        val point = TouchPoint(
            x = x,
            y = y,
            timestamp = System.currentTimeMillis() - startTime,
            pressure = pressure,
            fingerId = fingerId
        )
        recordedPoints.add(point)
    }
    
    fun stopRecording(): GestureDefinition? {
        if (!isRecording) return null
        
        isRecording = false
        val duration = System.currentTimeMillis() - startTime
        
        if (recordedPoints.isEmpty()) {
            Log.w("GestureRecorder", "录制的手势为空")
            return null
        }
        
        val gestureType = analyzeGestureType(recordedPoints)
        val gesture = GestureDefinition(
            id = generateGestureId(),
            name = "录制手势_${System.currentTimeMillis()}",
            type = gestureType,
            points = recordedPoints.toList(),
            duration = duration
        )
        
        Log.d("GestureRecorder", "手势录制完成: ${gesture.name}, 类型: ${gesture.type}")
        return gesture
    }
    
    private fun analyzeGestureType(points: List<TouchPoint>): GestureType {
        if (points.size <= 2) return GestureType.TAP
        
        val fingerIds = points.map { it.fingerId }.distinct()
        if (fingerIds.size > 1) {
            return if (isSpreadGesture(points)) GestureType.SPREAD
            else if (isPinchGesture(points)) GestureType.PINCH
            else GestureType.MULTI_TAP
        }
        
        val distance = calculateDistance(points.first(), points.last())
        return if (distance > 100) GestureType.SWIPE else GestureType.TAP
    }
    
    private fun isSpreadGesture(points: List<TouchPoint>): Boolean {
        // 分析两个手指的距离变化
        val finger1Points = points.filter { it.fingerId == 0 }
        val finger2Points = points.filter { it.fingerId == 1 }
        
        if (finger1Points.size < 2 || finger2Points.size < 2) return false
        
        val initialDistance = calculateDistance(finger1Points.first(), finger2Points.first())
        val finalDistance = calculateDistance(finger1Points.last(), finger2Points.last())
        
        return finalDistance > initialDistance * 1.5f
    }
    
    private fun isPinchGesture(points: List<TouchPoint>): Boolean {
        val finger1Points = points.filter { it.fingerId == 0 }
        val finger2Points = points.filter { it.fingerId == 1 }
        
        if (finger1Points.size < 2 || finger2Points.size < 2) return false
        
        val initialDistance = calculateDistance(finger1Points.first(), finger2Points.first())
        val finalDistance = calculateDistance(finger1Points.last(), finger2Points.last())
        
        return finalDistance < initialDistance * 0.5f
    }
    
    private fun calculateDistance(p1: TouchPoint, p2: TouchPoint): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt(dx * dx + dy * dy)
    }
    
    private fun generateGestureId(): String {
        return "gesture_${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}"
    }
}

/**
 * 高级手势执行器
 */
class AdvancedGestureExecutor(private val accessibilityService: AccessibilityService) {
    
    companion object {
        private const val TAG = "AdvancedGestureExecutor"
        private const val DEFAULT_GESTURE_DURATION = 300L
        private const val MIN_GESTURE_DURATION = 50L
        private const val MAX_GESTURE_DURATION = 10000L
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private val gestureRecorder = GestureRecorder()
    private val json = Json { prettyPrint = true }
    
    // 执行状态流
    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()
    
    // 录制状态流
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    
    /**
     * 执行简单点击
     */
    suspend fun performTap(x: Float, y: Float, duration: Long = 100): Boolean {
        return executeGesture {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, duration)
            GestureDescription.Builder().addStroke(stroke).build()
        }
    }
    
    /**
     * 执行多点击
     */
    suspend fun performMultiTap(points: List<PointF>, duration: Long = 100): Boolean {
        if (points.isEmpty()) return false
        
        return executeGesture {
            val builder = GestureDescription.Builder()
            
            points.forEachIndexed { index, point ->
                val path = Path().apply { moveTo(point.x, point.y) }
                val stroke = GestureDescription.StrokeDescription(
                    path, 
                    index * 50L, // 每个点击间隔50ms
                    duration
                )
                builder.addStroke(stroke)
            }
            
            builder.build()
        }
    }
    
    /**
     * 执行滑动手势
     */
    suspend fun performSwipe(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        duration: Long = DEFAULT_GESTURE_DURATION
    ): Boolean {
        return executeGesture {
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, duration)
            GestureDescription.Builder().addStroke(stroke).build()
        }
    }
    
    /**
     * 执行曲线滑动
     */
    suspend fun performCurvedSwipe(
        startX: Float, startY: Float,
        controlX: Float, controlY: Float,
        endX: Float, endY: Float,
        duration: Long = DEFAULT_GESTURE_DURATION
    ): Boolean {
        return executeGesture {
            val path = Path().apply {
                moveTo(startX, startY)
                quadTo(controlX, controlY, endX, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, duration)
            GestureDescription.Builder().addStroke(stroke).build()
        }
    }
    
    /**
     * 执行捏合手势
     */
    suspend fun performPinch(
        centerX: Float, centerY: Float,
        startRadius: Float, endRadius: Float,
        duration: Long = DEFAULT_GESTURE_DURATION
    ): Boolean {
        if (startRadius <= endRadius) {
            Log.w(TAG, "捏合手势要求起始半径大于结束半径")
            return false
        }
        
        return executeGesture {
            val builder = GestureDescription.Builder()
            
            // 第一个手指路径
            val path1 = Path().apply {
                moveTo(centerX - startRadius, centerY)
                lineTo(centerX - endRadius, centerY)
            }
            
            // 第二个手指路径
            val path2 = Path().apply {
                moveTo(centerX + startRadius, centerY)
                lineTo(centerX + endRadius, centerY)
            }
            
            val stroke1 = GestureDescription.StrokeDescription(path1, 0, duration)
            val stroke2 = GestureDescription.StrokeDescription(path2, 0, duration)
            
            builder.addStroke(stroke1).addStroke(stroke2).build()
        }
    }
    
    /**
     * 执行展开手势
     */
    suspend fun performSpread(
        centerX: Float, centerY: Float,
        startRadius: Float, endRadius: Float,
        duration: Long = DEFAULT_GESTURE_DURATION
    ): Boolean {
        if (startRadius >= endRadius) {
            Log.w(TAG, "展开手势要求起始半径小于结束半径")
            return false
        }
        
        return executeGesture {
            val builder = GestureDescription.Builder()
            
            // 第一个手指路径
            val path1 = Path().apply {
                moveTo(centerX - startRadius, centerY)
                lineTo(centerX - endRadius, centerY)
            }
            
            // 第二个手指路径
            val path2 = Path().apply {
                moveTo(centerX + startRadius, centerY)
                lineTo(centerX + endRadius, centerY)
            }
            
            val stroke1 = GestureDescription.StrokeDescription(path1, 0, duration)
            val stroke2 = GestureDescription.StrokeDescription(path2, 0, duration)
            
            builder.addStroke(stroke1).addStroke(stroke2).build()
        }
    }
    
    /**
     * 执行旋转手势
     */
    suspend fun performRotation(
        centerX: Float, centerY: Float,
        radius: Float,
        startAngle: Float, endAngle: Float,
        duration: Long = DEFAULT_GESTURE_DURATION
    ): Boolean {
        return executeGesture {
            val builder = GestureDescription.Builder()
            
            // 计算两个手指的旋转路径
            val angleStep = (endAngle - startAngle) / 10f
            val timeStep = duration / 10f
            
            // 第一个手指路径
            val path1 = Path()
            val startX1 = centerX + radius * cos(Math.toRadians(startAngle.toDouble())).toFloat()
            val startY1 = centerY + radius * sin(Math.toRadians(startAngle.toDouble())).toFloat()
            path1.moveTo(startX1, startY1)
            
            for (i in 1..10) {
                val angle = startAngle + angleStep * i
                val x = centerX + radius * cos(Math.toRadians(angle.toDouble())).toFloat()
                val y = centerY + radius * sin(Math.toRadians(angle.toDouble())).toFloat()
                path1.lineTo(x, y)
            }
            
            // 第二个手指路径（相对180度）
            val path2 = Path()
            val oppositeStartAngle = startAngle + 180f
            val startX2 = centerX + radius * cos(Math.toRadians(oppositeStartAngle.toDouble())).toFloat()
            val startY2 = centerY + radius * sin(Math.toRadians(oppositeStartAngle.toDouble())).toFloat()
            path2.moveTo(startX2, startY2)
            
            for (i in 1..10) {
                val angle = oppositeStartAngle + angleStep * i
                val x = centerX + radius * cos(Math.toRadians(angle.toDouble())).toFloat()
                val y = centerY + radius * sin(Math.toRadians(angle.toDouble())).toFloat()
                path2.lineTo(x, y)
            }
            
            val stroke1 = GestureDescription.StrokeDescription(path1, 0, duration)
            val stroke2 = GestureDescription.StrokeDescription(path2, 0, duration)
            
            builder.addStroke(stroke1).addStroke(stroke2).build()
        }
    }
    
    /**
     * 执行自定义路径手势
     */
    suspend fun performCustomPath(paths: List<Path>, duration: Long = DEFAULT_GESTURE_DURATION): Boolean {
        if (paths.isEmpty()) return false
        
        return executeGesture {
            val builder = GestureDescription.Builder()
            
            paths.forEachIndexed { index, path ->
                val stroke = GestureDescription.StrokeDescription(
                    path, 
                    0, 
                    duration
                )
                builder.addStroke(stroke)
            }
            
            builder.build()
        }
    }
    
    /**
     * 执行录制的手势
     */
    suspend fun performRecordedGesture(gesture: GestureDefinition): Boolean {
        return when (gesture.type) {
            GestureType.TAP -> {
                val point = gesture.points.firstOrNull() ?: return false
                performTap(point.x, point.y, gesture.duration)
            }
            GestureType.SWIPE -> {
                if (gesture.points.size < 2) return false
                val start = gesture.points.first()
                val end = gesture.points.last()
                performSwipe(start.x, start.y, end.x, end.y, gesture.duration)
            }
            GestureType.MULTI_TAP -> {
                val points = gesture.points.map { PointF(it.x, it.y) }
                performMultiTap(points, gesture.duration)
            }
            GestureType.CUSTOM_PATH -> {
                performCustomPathFromPoints(gesture.points, gesture.duration)
            }
            else -> {
                Log.w(TAG, "不支持的手势类型: ${gesture.type}")
                false
            }
        }
    }
    
    /**
     * 从点列表创建自定义路径
     */
    private suspend fun performCustomPathFromPoints(points: List<TouchPoint>, duration: Long): Boolean {
        val fingerGroups = points.groupBy { it.fingerId }
        val paths = fingerGroups.map { (_, fingerPoints) ->
            val path = Path()
            fingerPoints.sortedBy { it.timestamp }.forEachIndexed { index, point ->
                if (index == 0) {
                    path.moveTo(point.x, point.y)
                } else {
                    path.lineTo(point.x, point.y)
                }
            }
            path
        }
        
        return performCustomPath(paths, duration)
    }
    
    /**
     * 开始录制手势
     */
    fun startGestureRecording(): Boolean {
        val success = gestureRecorder.startRecording()
        if (success) {
            _isRecording.value = true
        }
        return success
    }
    
    /**
     * 停止录制手势
     */
    fun stopGestureRecording(): GestureDefinition? {
        val gesture = gestureRecorder.stopRecording()
        _isRecording.value = false
        return gesture
    }
    
    /**
     * 添加录制点
     */
    fun addRecordingPoint(x: Float, y: Float, fingerId: Int = 0, pressure: Float = 1.0f) {
        gestureRecorder.addPoint(x, y, fingerId, pressure)
    }
    
    /**
     * 保存手势到文件
     */
    fun saveGesture(gesture: GestureDefinition, filePath: String): Boolean {
        return try {
            val gestureJson = json.encodeToString(gesture)
            java.io.File(filePath).writeText(gestureJson)
            Log.d(TAG, "手势已保存到: $filePath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "保存手势失败", e)
            false
        }
    }
    
    /**
     * 从文件加载手势
     */
    fun loadGesture(filePath: String): GestureDefinition? {
        return try {
            val gestureJson = java.io.File(filePath).readText()
            json.decodeFromString<GestureDefinition>(gestureJson)
        } catch (e: Exception) {
            Log.e(TAG, "加载手势失败", e)
            null
        }
    }
    
    /**
     * 执行手势的通用方法
     */
    private suspend fun executeGesture(gestureBuilder: () -> GestureDescription): Boolean {
        return withContext(Dispatchers.Main) {
            try {
                _isExecuting.value = true
                
                val gesture = gestureBuilder()
                var result = false
                
                val callback = object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        Log.d(TAG, "手势执行完成")
                        result = true
                    }
                    
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        Log.w(TAG, "手势执行被取消")
                        result = false
                    }
                }
                
                val success = accessibilityService.dispatchGesture(gesture, callback, null)
                if (!success) {
                    Log.e(TAG, "分发手势失败")
                    return@withContext false
                }
                
                // 等待手势完成
                delay(gesture.duration + 100)
                result
                
            } catch (e: Exception) {
                Log.e(TAG, "执行手势异常", e)
                false
            } finally {
                _isExecuting.value = false
            }
        }
    }
    
    /**
     * 批量执行手势序列
     */
    suspend fun performGestureSequence(
        gestures: List<GestureDefinition>,
        delayBetweenGestures: Long = 100
    ): Boolean {
        var allSuccess = true
        
        for (gesture in gestures) {
            if (gesture.delay > 0) {
                delay(gesture.delay)
            }
            
            val success = performRecordedGesture(gesture)
            if (!success) {
                allSuccess = false
                Log.w(TAG, "手势序列中的手势执行失败: ${gesture.name}")
            }
            
            if (delayBetweenGestures > 0 && gesture != gestures.last()) {
                delay(delayBetweenGestures)
            }
        }
        
        return allSuccess
    }
    
    /**
     * 创建预定义手势
     */
    fun createPredefinedGestures(): Map<String, GestureDefinition> {
        return mapOf(
            "back" to GestureDefinition(
                id = "predefined_back",
                name = "返回手势",
                type = GestureType.SWIPE,
                points = listOf(
                    TouchPoint(0f, 500f, 0),
                    TouchPoint(300f, 500f, 300)
                ),
                duration = 300
            ),
            "home" to GestureDefinition(
                id = "predefined_home",
                name = "主页手势",
                type = GestureType.SWIPE,
                points = listOf(
                    TouchPoint(500f, 1800f, 0),
                    TouchPoint(500f, 1000f, 500)
                ),
                duration = 500
            ),
            "recent_apps" to GestureDefinition(
                id = "predefined_recent",
                name = "最近应用手势",
                type = GestureType.SWIPE,
                points = listOf(
                    TouchPoint(500f, 1800f, 0),
                    TouchPoint(500f, 1000f, 200),
                    TouchPoint(500f, 1800f, 600)
                ),
                duration = 600
            )
        )
    }
}