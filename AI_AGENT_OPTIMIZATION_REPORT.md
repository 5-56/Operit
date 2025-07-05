package com.ai.assistance.operit.core.agent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.ai.assistance.operit.services.UIAccessibilityService
import com.ai.assistance.operit.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import android.util.Base64

/**
 * 增强的屏幕感知模块
 * 结合UI结构信息和视觉截图，为AI提供更丰富的感知数据
 */
class EnhancedScreenPerception(private val context: Context) {
    
    companion object {
        private const val TAG = "EnhancedScreenPerception"
        private const val MAX_SCREENSHOT_SIZE = 1024 // 最大截图尺寸
        private const val SCREENSHOT_QUALITY = 85 // JPEG质量
    }
    
    /**
     * 屏幕感知数据
     */
    data class ScreenPerceptionData(
        val uiStructure: UIStructureData,
        val visualData: VisualData?,
        val contextInfo: ContextInfo,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * UI结构数据
     */
    data class UIStructureData(
        val hierarchy: String,
        val focusedElements: List<FocusedElement>,
        val interactableElements: List<InteractableElement>,
        val textElements: List<TextElement>
    )
    
    /**
     * 视觉数据
     */
    data class VisualData(
        val screenshot: String, // Base64编码的截图
        val screenSize: Pair<Int, Int>,
        val density: Float
    )
    
    /**
     * 上下文信息
     */
    data class ContextInfo(
        val currentApp: String,
        val currentActivity: String,
        val orientation: String,
        val keyboardVisible: Boolean
    )
    
    /**
     * 焦点元素
     */
    data class FocusedElement(
        val bounds: Rect,
        val type: String,
        val description: String
    )
    
    /**
     * 可交互元素
     */
    data class InteractableElement(
        val bounds: Rect,
        val type: String, // button, input, clickable
        val resourceId: String?,
        val text: String?,
        val contentDesc: String?
    )
    
    /**
     * 文本元素
     */
    data class TextElement(
        val bounds: Rect,
        val text: String,
        val isEditable: Boolean
    )
    
    /**
     * 获取增强的屏幕感知数据
     */
    suspend fun getEnhancedScreenData(
        includeScreenshot: Boolean = true,
        optimizeForAI: Boolean = true
    ): ScreenPerceptionData? = withContext(Dispatchers.IO) {
        
        try {
            LogUtils.d(TAG, "开始获取增强屏幕感知数据")
            
            val accessibilityService = UIAccessibilityService.getInstance()
            if (accessibilityService == null) {
                LogUtils.e(TAG, "无障碍服务不可用")
                return@withContext null
            }
            
            // 1. 获取UI结构数据
            val uiStructure = getUIStructureData(accessibilityService)
            
            // 2. 获取视觉数据（如果需要）
            val visualData = if (includeScreenshot) {
                getVisualData()
            } else null
            
            // 3. 获取上下文信息
            val contextInfo = getContextInfo(accessibilityService)
            
            val perceptionData = ScreenPerceptionData(
                uiStructure = uiStructure,
                visualData = visualData,
                contextInfo = contextInfo
            )
            
            LogUtils.d(TAG, "屏幕感知数据获取完成")
            
            return@withContext if (optimizeForAI) {
                optimizeForAIProcessing(perceptionData)
            } else {
                perceptionData
            }
            
        } catch (e: Exception) {
            LogUtils.e(TAG, "获取屏幕感知数据失败", e)
            null
        }
    }
    
    /**
     * 获取UI结构数据
     */
    private suspend fun getUIStructureData(
        accessibilityService: UIAccessibilityService
    ): UIStructureData = withContext(Dispatchers.IO) {
        
        val hierarchy = accessibilityService.getUIHierarchy()
        
        // 解析UI层次结构，提取关键元素
        val focusedElements = mutableListOf<FocusedElement>()
        val interactableElements = mutableListOf<InteractableElement>()
        val textElements = mutableListOf<TextElement>()
        
        // TODO: 实现XML解析逻辑
        parseUIHierarchy(hierarchy, focusedElements, interactableElements, textElements)
        
        UIStructureData(
            hierarchy = hierarchy,
            focusedElements = focusedElements,
            interactableElements = interactableElements,
            textElements = textElements
        )
    }
    
    /**
     * 获取视觉数据
     */
    private suspend fun getVisualData(): VisualData? = withContext(Dispatchers.IO) {
        try {
            // 获取屏幕截图
            val screenshot = captureScreenshot()
            if (screenshot != null) {
                VisualData(
                    screenshot = bitmapToBase64(screenshot),
                    screenSize = Pair(screenshot.width, screenshot.height),
                    density = context.resources.displayMetrics.density
                )
            } else null
        } catch (e: Exception) {
            LogUtils.e(TAG, "获取视觉数据失败", e)
            null
        }
    }
    
    /**
     * 获取上下文信息
     */
    private suspend fun getContextInfo(
        accessibilityService: UIAccessibilityService
    ): ContextInfo = withContext(Dispatchers.IO) {
        
        // TODO: 实现上下文信息获取
        ContextInfo(
            currentApp = getCurrentApp(),
            currentActivity = getCurrentActivity(),
            orientation = getScreenOrientation(),
            keyboardVisible = isKeyboardVisible()
        )
    }
    
    /**
     * 为AI处理优化数据
     */
    private fun optimizeForAIProcessing(data: ScreenPerceptionData): ScreenPerceptionData {
        // 1. 简化UI层次结构（移除不重要的元素）
        // 2. 压缩截图（如果太大）
        // 3. 突出重要的交互元素
        
        return data // 目前返回原始数据
    }
    
    /**
     * 解析UI层次结构
     */
    private fun parseUIHierarchy(
        hierarchy: String,
        focusedElements: MutableList<FocusedElement>,
        interactableElements: MutableList<InteractableElement>,
        textElements: MutableList<TextElement>
    ) {
        // TODO: 实现XML解析逻辑
        LogUtils.d(TAG, "解析UI层次结构: ${hierarchy.length} 字符")
    }
    
    /**
     * 截取屏幕
     */
    private suspend fun captureScreenshot(): Bitmap? = withContext(Dispatchers.IO) {
        // TODO: 实现屏幕截图逻辑
        // 可以使用MediaProjection API或其他方法
        LogUtils.d(TAG, "截取屏幕（功能待实现）")
        null
    }
    
    /**
     * 转换Bitmap为Base64
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        
        // 调整截图大小以节省传输
        val scaledBitmap = if (bitmap.width > MAX_SCREENSHOT_SIZE || bitmap.height > MAX_SCREENSHOT_SIZE) {
            val scale = MAX_SCREENSHOT_SIZE.toFloat() / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else {
            bitmap
        }
        
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, SCREENSHOT_QUALITY, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
    
    // 辅助方法实现
    private fun getCurrentApp(): String = "com.example.app" // TODO: 实现
    private fun getCurrentActivity(): String = "MainActivity" // TODO: 实现
    private fun getScreenOrientation(): String = "portrait" // TODO: 实现
    private fun isKeyboardVisible(): Boolean = false // TODO: 实现
    
    /**
     * 将感知数据转换为AI友好的JSON格式
     */
    fun toAIFormat(data: ScreenPerceptionData): JSONObject {
        val json = JSONObject()
        
        // UI结构信息
        val uiJson = JSONObject().apply {
            put("hierarchy", data.uiStructure.hierarchy)
            put("interactable_elements", JSONArray().apply {
                data.uiStructure.interactableElements.forEach { element ->
                    put(JSONObject().apply {
                        put("type", element.type)
                        put("bounds", "${element.bounds}")
                        put("resource_id", element.resourceId ?: "")
                        put("text", element.text ?: "")
                        put("content_desc", element.contentDesc ?: "")
                    })
                }
            })
            put("text_elements", JSONArray().apply {
                data.uiStructure.textElements.forEach { element ->
                    put(JSONObject().apply {
                        put("text", element.text)
                        put("bounds", "${element.bounds}")
                        put("editable", element.isEditable)
                    })
                }
            })
        }
        
        // 视觉信息
        data.visualData?.let { visual ->
            json.put("visual", JSONObject().apply {
                put("screenshot", visual.screenshot)
                put("screen_size", JSONArray().apply {
                    put(visual.screenSize.first)
                    put(visual.screenSize.second)
                })
                put("density", visual.density)
            })
        }
        
        // 上下文信息
        json.put("context", JSONObject().apply {
            put("current_app", data.contextInfo.currentApp)
            put("current_activity", data.contextInfo.currentActivity)
            put("orientation", data.contextInfo.orientation)
            put("keyboard_visible", data.contextInfo.keyboardVisible)
        })
        
        json.put("ui_structure", uiJson)
        json.put("timestamp", data.timestamp)
        
        return json
    }
}