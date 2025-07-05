package com.ai.assistance.operit.core.agent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.accessibility.AccessibilityNodeInfo
import android.view.WindowManager
import com.ai.assistance.operit.services.UIAccessibilityService
import com.ai.assistance.operit.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.min

/**
 * 增强的屏幕感知模块
 * 
 * 提供多维度的屏幕感知能力：
 * 1. UI结构分析 - 获取屏幕上所有可交互元素
 * 2. 视觉信息 - 屏幕截图和图像分析
 * 3. 上下文信息 - 当前应用、活动等
 * 4. AI优化格式 - 针对AI处理优化的数据格式
 */
class EnhancedScreenPerception(private val context: Context) {
    
    companion object {
        private const val TAG = "EnhancedScreenPerception"
        private const val MAX_SCREENSHOT_WIDTH = 1024
        private const val MAX_SCREENSHOT_HEIGHT = 1024
        private const val SCREENSHOT_QUALITY = 85
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
        val elements: List<UIElement>,
        val hierarchy: String,
        val focusedElement: UIElement?,
        val scrollableElements: List<UIElement>
    )
    
    /**
     * UI元素数据
     */
    data class UIElement(
        val id: String,
        val className: String,
        val text: String?,
        val contentDescription: String?,
        val bounds: Rect,
        val isClickable: Boolean,
        val isScrollable: Boolean,
        val isEditable: Boolean,
        val isEnabled: Boolean,
        val isSelected: Boolean,
        val isFocused: Boolean,
        val viewIdResourceName: String?,
        val packageName: String?,
        val depth: Int,
        val children: List<UIElement> = emptyList()
    )
    
    /**
     * 视觉数据
     */
    data class VisualData(
        val screenshot: Bitmap?,
        val compressedScreenshot: ByteArray?,
        val screenSize: Pair<Int, Int>,
        val density: Float
    )
    
    /**
     * 上下文信息
     */
    data class ContextInfo(
        val currentApp: String?,
        val currentActivity: String?,
        val orientation: Int,
        val isKeyboardVisible: Boolean,
        val systemBars: SystemBarsInfo
    )
    
    /**
     * 系统栏信息
     */
    data class SystemBarsInfo(
        val statusBarHeight: Int,
        val navigationBarHeight: Int,
        val hasNavigationBar: Boolean
    )
    
    /**
     * 获取增强的屏幕数据
     */
    suspend fun getEnhancedScreenData(
        includeScreenshot: Boolean = true,
        optimizeForAI: Boolean = true
    ): ScreenPerceptionData? = withContext(Dispatchers.IO) {
        try {
            LogUtils.d(TAG, "开始获取增强屏幕数据")
            
            // 检查无障碍服务状态
            if (!UIAccessibilityService.isRunning()) {
                LogUtils.w(TAG, "无障碍服务未运行，屏幕感知功能受限")
                return@withContext null
            }
            
            // 获取UI结构数据
            val uiStructure = getUIStructureData()
            
            // 获取视觉数据
            val visualData = if (includeScreenshot) {
                getVisualData(optimizeForAI)
            } else {
                null
            }
            
            // 获取上下文信息
            val contextInfo = getContextInfo()
            
            val result = ScreenPerceptionData(
                uiStructure = uiStructure,
                visualData = visualData,
                contextInfo = contextInfo
            )
            
            LogUtils.i(TAG, "✅ 屏幕数据获取完成: ${uiStructure.elements.size}个元素")
            result
            
        } catch (e: Exception) {
            LogUtils.e(TAG, "获取屏幕数据失败", e)
            null
        }
    }
    
    /**
     * 获取UI结构数据
     */
    private suspend fun getUIStructureData(): UIStructureData = withContext(Dispatchers.Main) {
        val rootNode = UIAccessibilityService.getRootInActiveWindow()
        
        if (rootNode == null) {
            LogUtils.w(TAG, "无法获取根节点")
            return@withContext UIStructureData(
                elements = emptyList(),
                hierarchy = "",
                focusedElement = null,
                scrollableElements = emptyList()
            )
        }
        
        try {
            val elements = mutableListOf<UIElement>()
            val scrollableElements = mutableListOf<UIElement>()
            var focusedElement: UIElement? = null
            
            // 递归遍历节点树
            traverseNode(rootNode, elements, scrollableElements, 0) { element ->
                if (element.isFocused) {
                    focusedElement = element
                }
            }
            
            // 生成层次结构字符串
            val hierarchy = generateHierarchyString(rootNode)
            
            UIStructureData(
                elements = elements,
                hierarchy = hierarchy,
                focusedElement = focusedElement,
                scrollableElements = scrollableElements
            )
            
        } finally {
            rootNode.recycle()
        }
    }
    
    /**
     * 递归遍历节点
     */
    private fun traverseNode(
        node: AccessibilityNodeInfo,
        elements: MutableList<UIElement>,
        scrollableElements: MutableList<UIElement>,
        depth: Int,
        onElement: (UIElement) -> Unit = {}
    ) {
        try {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            
            val element = UIElement(
                id = node.hashCode().toString(),
                className = node.className?.toString() ?: "",
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
                bounds = bounds,
                isClickable = node.isClickable,
                isScrollable = node.isScrollable,
                isEditable = node.isEditable,
                isEnabled = node.isEnabled,
                isSelected = node.isSelected,
                isFocused = node.isFocused,
                viewIdResourceName = node.viewIdResourceName,
                packageName = node.packageName?.toString(),
                depth = depth
            )
            
            elements.add(element)
            onElement(element)
            
            if (element.isScrollable) {
                scrollableElements.add(element)
            }
            
            // 递归处理子节点
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    traverseNode(child, elements, scrollableElements, depth + 1, onElement)
                    child.recycle()
                }
            }
            
        } catch (e: Exception) {
            LogUtils.w(TAG, "遍历节点时出错: ${e.message}")
        }
    }
    
    /**
     * 生成层次结构字符串
     */
    private fun generateHierarchyString(rootNode: AccessibilityNodeInfo): String {
        return buildString {
            generateHierarchyRecursive(rootNode, this, 0)
        }
    }
    
    private fun generateHierarchyRecursive(
        node: AccessibilityNodeInfo,
        builder: StringBuilder,
        depth: Int
    ) {
        try {
            val indent = "  ".repeat(depth)
            val className = node.className?.toString()?.substringAfterLast('.') ?: "Unknown"
            val text = node.text?.toString()?.take(20) ?: ""
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            
            builder.appendLine("$indent$className${if (text.isNotEmpty()) " \"$text\"" else ""} [${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}]")
            
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    generateHierarchyRecursive(child, builder, depth + 1)
                    child.recycle()
                }
            }
            
        } catch (e: Exception) {
            LogUtils.w(TAG, "生成层次结构时出错: ${e.message}")
        }
    }
    
    /**
     * 获取视觉数据
     */
    private suspend fun getVisualData(optimizeForAI: Boolean): VisualData? = withContext(Dispatchers.IO) {
        try {
            // 获取屏幕尺寸
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            
            val screenSize = Pair(displayMetrics.widthPixels, displayMetrics.heightPixels)
            val density = displayMetrics.density
            
            // 获取截图 (这里需要实际的截图实现)
            val screenshot = getScreenshot()
            
            // 如果需要AI优化，压缩截图
            val compressedScreenshot = if (optimizeForAI && screenshot != null) {
                compressScreenshotForAI(screenshot)
            } else {
                null
            }
            
            VisualData(
                screenshot = screenshot,
                compressedScreenshot = compressedScreenshot,
                screenSize = screenSize,
                density = density
            )
            
        } catch (e: Exception) {
            LogUtils.e(TAG, "获取视觉数据失败", e)
            null
        }
    }
    
    /**
     * 获取截图 (需要实际实现)
     */
    private fun getScreenshot(): Bitmap? {
        // 这里需要实际的截图实现
        // 可能需要 MediaProjection API 或其他方式
        LogUtils.d(TAG, "获取截图功能需要实际实现")
        return null
    }
    
    /**
     * 为AI优化压缩截图
     */
    private fun compressScreenshotForAI(bitmap: Bitmap): ByteArray {
        // 计算缩放比例
        val scale = min(
            MAX_SCREENSHOT_WIDTH.toFloat() / bitmap.width,
            MAX_SCREENSHOT_HEIGHT.toFloat() / bitmap.height
        )
        
        val scaledBitmap = if (scale < 1.0f) {
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }
        
        // 压缩为JPEG
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, SCREENSHOT_QUALITY, outputStream)
        
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }
        
        return outputStream.toByteArray()
    }
    
    /**
     * 获取上下文信息
     */
    private fun getContextInfo(): ContextInfo {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        
        // 获取当前应用信息
        val rootNode = UIAccessibilityService.getRootInActiveWindow()
        val currentApp = rootNode?.packageName?.toString()
        rootNode?.recycle()
        
        // 获取系统栏信息
        val systemBars = getSystemBarsInfo()
        
        return ContextInfo(
            currentApp = currentApp,
            currentActivity = null, // 可以通过其他方式获取
            orientation = context.resources.configuration.orientation,
            isKeyboardVisible = false, // 可以通过其他方式检测
            systemBars = systemBars
        )
    }
    
    /**
     * 获取系统栏信息
     */
    private fun getSystemBarsInfo(): SystemBarsInfo {
        // 这里可以通过反射或其他方式获取系统栏高度
        return SystemBarsInfo(
            statusBarHeight = getStatusBarHeight(),
            navigationBarHeight = getNavigationBarHeight(),
            hasNavigationBar = hasNavigationBar()
        )
    }
    
    private fun getStatusBarHeight(): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            context.resources.getDimensionPixelSize(resourceId)
        } else {
            0
        }
    }
    
    private fun getNavigationBarHeight(): Int {
        val resourceId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            context.resources.getDimensionPixelSize(resourceId)
        } else {
            0
        }
    }
    
    private fun hasNavigationBar(): Boolean {
        val id = context.resources.getIdentifier("config_showNavigationBar", "bool", "android")
        return id > 0 && context.resources.getBoolean(id)
    }
    
    /**
     * 将屏幕数据转换为AI优化的JSON格式
     */
    fun toAIOptimizedJSON(data: ScreenPerceptionData): JSONObject {
        return JSONObject().apply {
            put("timestamp", data.timestamp)
            put("context", JSONObject().apply {
                put("currentApp", data.contextInfo.currentApp)
                put("screenSize", JSONArray().apply {
                    put(data.visualData?.screenSize?.first ?: 0)
                    put(data.visualData?.screenSize?.second ?: 0)
                })
                put("orientation", data.contextInfo.orientation)
            })
            
            put("elements", JSONArray().apply {
                data.uiStructure.elements.forEach { element ->
                    put(JSONObject().apply {
                        put("id", element.id)
                        put("type", element.className.substringAfterLast('.'))
                        put("text", element.text ?: "")
                        put("description", element.contentDescription ?: "")
                        put("bounds", JSONArray().apply {
                            put(element.bounds.left)
                            put(element.bounds.top)
                            put(element.bounds.right)
                            put(element.bounds.bottom)
                        })
                        put("clickable", element.isClickable)
                        put("scrollable", element.isScrollable)
                        put("editable", element.isEditable)
                        put("enabled", element.isEnabled)
                        put("depth", element.depth)
                    })
                }
            })
            
            if (data.uiStructure.focusedElement != null) {
                put("focusedElement", data.uiStructure.focusedElement.id)
            }
            
            put("scrollableCount", data.uiStructure.scrollableElements.size)
            put("totalElements", data.uiStructure.elements.size)
        }
    }
    
    /**
     * 生成AI友好的屏幕描述
     */
    fun generateAIDescription(data: ScreenPerceptionData): String {
        return buildString {
            appendLine("📱 屏幕感知报告")
            appendLine("=" * 30)
            
            appendLine("🎯 当前应用: ${data.contextInfo.currentApp ?: "未知"}")
            appendLine("📏 屏幕尺寸: ${data.visualData?.screenSize?.let { "${it.first}x${it.second}" } ?: "未知"}")
            appendLine("🔄 方向: ${if (data.contextInfo.orientation == 1) "竖屏" else "横屏"}")
            appendLine("")
            
            appendLine("🎛️ UI元素统计:")
            appendLine("  总元素数: ${data.uiStructure.elements.size}")
            appendLine("  可点击元素: ${data.uiStructure.elements.count { it.isClickable }}")
            appendLine("  可滚动元素: ${data.uiStructure.scrollableElements.size}")
            appendLine("  可编辑元素: ${data.uiStructure.elements.count { it.isEditable }}")
            appendLine("")
            
            if (data.uiStructure.focusedElement != null) {
                appendLine("🎯 当前焦点: ${data.uiStructure.focusedElement.text ?: data.uiStructure.focusedElement.className}")
                appendLine("")
            }
            
            appendLine("📋 主要交互元素:")
            data.uiStructure.elements
                .filter { it.isClickable && (it.text?.isNotEmpty() == true || it.contentDescription?.isNotEmpty() == true) }
                .take(10)
                .forEach { element ->
                    val label = element.text?.take(30) ?: element.contentDescription?.take(30) ?: element.className.substringAfterLast('.')
                    appendLine("  • $label [${element.bounds.centerX()},${element.bounds.centerY()}]")
                }
        }
    }
    
    /**
     * 查找元素
     */
    fun findElementsByText(data: ScreenPerceptionData, text: String, exactMatch: Boolean = false): List<UIElement> {
        return data.uiStructure.elements.filter { element ->
            if (exactMatch) {
                element.text == text || element.contentDescription == text
            } else {
                element.text?.contains(text, ignoreCase = true) == true ||
                element.contentDescription?.contains(text, ignoreCase = true) == true
            }
        }
    }
    
    /**
     * 查找可点击元素
     */
    fun findClickableElements(data: ScreenPerceptionData): List<UIElement> {
        return data.uiStructure.elements.filter { it.isClickable }
    }
    
    /**
     * 查找可编辑元素
     */
    fun findEditableElements(data: ScreenPerceptionData): List<UIElement> {
        return data.uiStructure.elements.filter { it.isEditable }
    }
    
    /**
     * 检查屏幕是否发生变化
     */
    fun hasScreenChanged(oldData: ScreenPerceptionData?, newData: ScreenPerceptionData): Boolean {
        if (oldData == null) return true
        
        // 简单的变化检测：比较元素数量和主要元素的位置
        if (oldData.uiStructure.elements.size != newData.uiStructure.elements.size) {
            return true
        }
        
        // 比较前几个主要元素的位置
        val oldMainElements = oldData.uiStructure.elements.take(5)
        val newMainElements = newData.uiStructure.elements.take(5)
        
        return oldMainElements.zip(newMainElements).any { (old, new) ->
            old.bounds != new.bounds || old.text != new.text
        }
    }
}