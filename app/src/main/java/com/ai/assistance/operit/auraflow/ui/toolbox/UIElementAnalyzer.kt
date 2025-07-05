package com.ai.assistance.operit.auraflow.ui.toolbox

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import android.util.Log
import kotlinx.coroutines.*
import java.util.*

/**
 * UI元素信息
 */
@Serializable
data class UIElementInfo(
    val id: String,
    val className: String?,
    val text: String?,
    val contentDescription: String?,
    val resourceId: String?,
    val packageName: String?,
    val bounds: ElementBounds,
    val isClickable: Boolean,
    val isScrollable: Boolean,
    val isFocusable: Boolean,
    val isSelected: Boolean,
    val isEnabled: Boolean,
    val isPassword: Boolean,
    val isCheckable: Boolean,
    val isChecked: Boolean,
    val depth: Int,
    val childCount: Int,
    val index: Int,
    val parentId: String?,
    val properties: Map<String, String> = emptyMap(),
    val actions: List<String> = emptyList(),
    val xpath: String = "",
    val hierarchy: String = ""
)

/**
 * 元素边界
 */
@Serializable
data class ElementBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val width: Int = right - left,
    val height: Int = bottom - top,
    val centerX: Int = left + width / 2,
    val centerY: Int = top + height / 2
)

/**
 * UI层次结构节点
 */
data class UIHierarchyNode(
    val element: UIElementInfo,
    val children: MutableList<UIHierarchyNode> = mutableListOf(),
    var parent: UIHierarchyNode? = null
) {
    fun addChild(child: UIHierarchyNode) {
        children.add(child)
        child.parent = this
    }
    
    fun removeChild(child: UIHierarchyNode) {
        children.remove(child)
        child.parent = null
    }
    
    fun findById(id: String): UIHierarchyNode? {
        if (element.id == id) return this
        for (child in children) {
            val found = child.findById(id)
            if (found != null) return found
        }
        return null
    }
    
    fun getAllDescendants(): List<UIHierarchyNode> {
        val descendants = mutableListOf<UIHierarchyNode>()
        for (child in children) {
            descendants.add(child)
            descendants.addAll(child.getAllDescendants())
        }
        return descendants
    }
    
    fun getPath(): List<UIHierarchyNode> {
        val path = mutableListOf<UIHierarchyNode>()
        var current: UIHierarchyNode? = this
        while (current != null) {
            path.add(0, current)
            current = current.parent
        }
        return path
    }
}

/**
 * 搜索条件
 */
data class ElementSearchCriteria(
    val text: String? = null,
    val contentDescription: String? = null,
    val resourceId: String? = null,
    val className: String? = null,
    val packageName: String? = null,
    val isClickable: Boolean? = null,
    val isScrollable: Boolean? = null,
    val isEnabled: Boolean? = null,
    val bounds: ElementBounds? = null,
    val minDepth: Int? = null,
    val maxDepth: Int? = null
)

/**
 * 分析统计信息
 */
data class UIAnalysisStats(
    val totalElements: Int,
    val clickableElements: Int,
    val scrollableElements: Int,
    val textElements: Int,
    val editableElements: Int,
    val buttonsCount: Int,
    val imagesCount: Int,
    val maxDepth: Int,
    val packageCounts: Map<String, Int>,
    val classCounts: Map<String, Int>,
    val actionCounts: Map<String, Int>
)

/**
 * UI元素分析器
 */
class UIElementAnalyzer {
    
    companion object {
        private const val TAG = "UIElementAnalyzer"
        private const val MAX_ANALYSIS_DEPTH = 20
        private const val ANALYSIS_TIMEOUT_MS = 5000L
    }
    
    // 当前UI层次结构
    private val _hierarchy = MutableStateFlow<UIHierarchyNode?>(null)
    val hierarchy: StateFlow<UIHierarchyNode?> = _hierarchy.asStateFlow()
    
    // 所有元素列表
    private val _elements = MutableStateFlow<List<UIElementInfo>>(emptyList())
    val elements: StateFlow<List<UIElementInfo>> = _elements.asStateFlow()
    
    // 分析统计
    private val _stats = MutableStateFlow<UIAnalysisStats?>(null)
    val stats: StateFlow<UIAnalysisStats?> = _stats.asStateFlow()
    
    // 高亮元素
    private val _highlightedElements = MutableStateFlow<Set<String>>(emptySet())
    val highlightedElements: StateFlow<Set<String>> = _highlightedElements.asStateFlow()
    
    // 选中元素
    private val _selectedElement = MutableStateFlow<UIElementInfo?>(null)
    val selectedElement: StateFlow<UIElementInfo?> = _selectedElement.asStateFlow()
    
    // 分析状态
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()
    
    private var currentRootNode: AccessibilityNodeInfo? = null
    
    /**
     * 分析UI结构
     */
    suspend fun analyzeUIStructure(rootNode: AccessibilityNodeInfo?) {
        if (rootNode == null) {
            Log.w(TAG, "根节点为空，无法分析UI结构")
            return
        }
        
        _isAnalyzing.value = true
        
        try {
            withTimeout(ANALYSIS_TIMEOUT_MS) {
                currentRootNode = rootNode
                
                // 分析层次结构
                val hierarchyRoot = analyzeHierarchy(rootNode)
                _hierarchy.value = hierarchyRoot
                
                // 收集所有元素
                val allElements = collectAllElements(hierarchyRoot)
                _elements.value = allElements
                
                // 生成统计信息
                val analysisStats = generateStats(allElements)
                _stats.value = analysisStats
                
                Log.d(TAG, "UI结构分析完成，共${allElements.size}个元素")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "UI分析超时")
        } catch (e: Exception) {
            Log.e(TAG, "UI分析异常", e)
        } finally {
            _isAnalyzing.value = false
        }
    }
    
    /**
     * 分析层次结构
     */
    private fun analyzeHierarchy(
        node: AccessibilityNodeInfo,
        depth: Int = 0,
        index: Int = 0,
        parentId: String? = null
    ): UIHierarchyNode {
        val element = extractElementInfo(node, depth, index, parentId)
        val hierarchyNode = UIHierarchyNode(element)
        
        // 递归分析子节点
        if (depth < MAX_ANALYSIS_DEPTH) {
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    try {
                        val childNode = analyzeHierarchy(child, depth + 1, i, element.id)
                        hierarchyNode.addChild(childNode)
                    } catch (e: Exception) {
                        Log.w(TAG, "分析子节点失败: ${e.message}")
                    } finally {
                        child.recycle()
                    }
                }
            }
        }
        
        return hierarchyNode
    }
    
    /**
     * 提取元素信息
     */
    private fun extractElementInfo(
        node: AccessibilityNodeInfo,
        depth: Int,
        index: Int,
        parentId: String?
    ): UIElementInfo {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        val elementId = generateElementId(node, depth, index)
        
        // 提取可用动作
        val actions = mutableListOf<String>()
        val actionList = node.actionList
        actionList?.forEach { action ->
            actions.add(action.label?.toString() ?: action.id.toString())
        }
        
        // 提取扩展属性
        val properties = mutableMapOf<String, String>()
        
        try {
            // 添加基本属性
            properties["viewIdResourceName"] = node.viewIdResourceName ?: ""
            properties["windowId"] = node.windowId.toString()
            properties["drawingOrder"] = node.drawingOrder.toString()
            properties["inputType"] = node.inputType.toString()
            properties["liveRegion"] = node.liveRegion.toString()
            properties["maxTextLength"] = node.maxTextLength.toString()
            properties["textSelectionStart"] = node.textSelectionStart.toString()
            properties["textSelectionEnd"] = node.textSelectionEnd.toString()
            
            // 添加状态属性
            properties["isVisibleToUser"] = node.isVisibleToUser.toString()
            properties["isImportantForAccessibility"] = node.isImportantForAccessibility.toString()
            properties["canOpenPopup"] = node.canOpenPopup().toString()
            properties["isDismissable"] = node.isDismissable.toString()
            properties["isMultiLine"] = node.isMultiLine.toString()
            properties["isEditable"] = node.isEditable.toString()
            properties["isContextClickable"] = node.isContextClickable.toString()
            
        } catch (e: Exception) {
            Log.w(TAG, "提取扩展属性失败: ${e.message}")
        }
        
        // 生成XPath
        val xpath = generateXPath(node, depth)
        
        // 生成层次结构字符串
        val hierarchy = generateHierarchyString(depth)
        
        return UIElementInfo(
            id = elementId,
            className = node.className?.toString(),
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            resourceId = node.viewIdResourceName,
            packageName = node.packageName?.toString(),
            bounds = ElementBounds(bounds.left, bounds.top, bounds.right, bounds.bottom),
            isClickable = node.isClickable,
            isScrollable = node.isScrollable,
            isFocusable = node.isFocusable,
            isSelected = node.isSelected,
            isEnabled = node.isEnabled,
            isPassword = node.isPassword,
            isCheckable = node.isCheckable,
            isChecked = node.isChecked,
            depth = depth,
            childCount = node.childCount,
            index = index,
            parentId = parentId,
            properties = properties,
            actions = actions,
            xpath = xpath,
            hierarchy = hierarchy
        )
    }
    
    /**
     * 生成元素ID
     */
    private fun generateElementId(node: AccessibilityNodeInfo, depth: Int, index: Int): String {
        val className = node.className?.toString()?.split(".")?.lastOrNull() ?: "Unknown"
        val resourceId = node.viewIdResourceName?.split("/")?.lastOrNull()
        
        return if (resourceId != null) {
            "${className}_${resourceId}_${depth}_${index}"
        } else {
            "${className}_${node.hashCode()}_${depth}_${index}"
        }
    }
    
    /**
     * 生成XPath
     */
    private fun generateXPath(node: AccessibilityNodeInfo, depth: Int): String {
        val className = node.className?.toString()?.split(".")?.lastOrNull() ?: "*"
        val resourceId = node.viewIdResourceName?.split("/")?.lastOrNull()
        
        return if (resourceId != null) {
            "//$className[@resource-id='$resourceId']"
        } else {
            val text = node.text?.toString()
            if (!text.isNullOrBlank()) {
                "//$className[@text='$text']"
            } else {
                "//$className[${depth}]"
            }
        }
    }
    
    /**
     * 生成层次结构字符串
     */
    private fun generateHierarchyString(depth: Int): String {
        return "  ".repeat(depth)
    }
    
    /**
     * 收集所有元素
     */
    private fun collectAllElements(root: UIHierarchyNode?): List<UIElementInfo> {
        if (root == null) return emptyList()
        
        val elements = mutableListOf<UIElementInfo>()
        val queue: Queue<UIHierarchyNode> = LinkedList()
        queue.offer(root)
        
        while (queue.isNotEmpty()) {
            val node = queue.poll()
            elements.add(node.element)
            queue.addAll(node.children)
        }
        
        return elements
    }
    
    /**
     * 生成统计信息
     */
    private fun generateStats(elements: List<UIElementInfo>): UIAnalysisStats {
        val clickableCount = elements.count { it.isClickable }
        val scrollableCount = elements.count { it.isScrollable }
        val textCount = elements.count { !it.text.isNullOrBlank() }
        val editableCount = elements.count { it.properties["isEditable"] == "true" }
        val buttonCount = elements.count { it.className?.contains("Button") == true }
        val imageCount = elements.count { 
            it.className?.contains("Image") == true || it.className?.contains("Icon") == true 
        }
        val maxDepth = elements.maxOfOrNull { it.depth } ?: 0
        
        val packageCounts = elements
            .filter { !it.packageName.isNullOrBlank() }
            .groupingBy { it.packageName!! }
            .eachCount()
        
        val classCounts = elements
            .filter { !it.className.isNullOrBlank() }
            .groupingBy { it.className!! }
            .eachCount()
        
        val actionCounts = elements
            .flatMap { it.actions }
            .groupingBy { it }
            .eachCount()
        
        return UIAnalysisStats(
            totalElements = elements.size,
            clickableElements = clickableCount,
            scrollableElements = scrollableCount,
            textElements = textCount,
            editableElements = editableCount,
            buttonsCount = buttonCount,
            imagesCount = imageCount,
            maxDepth = maxDepth,
            packageCounts = packageCounts,
            classCounts = classCounts,
            actionCounts = actionCounts
        )
    }
    
    /**
     * 搜索元素
     */
    fun searchElements(criteria: ElementSearchCriteria): List<UIElementInfo> {
        val elements = _elements.value
        
        return elements.filter { element ->
            // 文本匹配
            if (criteria.text != null) {
                val textMatch = element.text?.contains(criteria.text, ignoreCase = true) == true ||
                               element.contentDescription?.contains(criteria.text, ignoreCase = true) == true
                if (!textMatch) return@filter false
            }
            
            // 资源ID匹配
            if (criteria.resourceId != null) {
                if (!element.resourceId?.contains(criteria.resourceId, ignoreCase = true) == true) {
                    return@filter false
                }
            }
            
            // 类名匹配
            if (criteria.className != null) {
                if (!element.className?.contains(criteria.className, ignoreCase = true) == true) {
                    return@filter false
                }
            }
            
            // 包名匹配
            if (criteria.packageName != null) {
                if (element.packageName != criteria.packageName) {
                    return@filter false
                }
            }
            
            // 状态匹配
            if (criteria.isClickable != null && element.isClickable != criteria.isClickable) {
                return@filter false
            }
            
            if (criteria.isScrollable != null && element.isScrollable != criteria.isScrollable) {
                return@filter false
            }
            
            if (criteria.isEnabled != null && element.isEnabled != criteria.isEnabled) {
                return@filter false
            }
            
            // 深度匹配
            if (criteria.minDepth != null && element.depth < criteria.minDepth) {
                return@filter false
            }
            
            if (criteria.maxDepth != null && element.depth > criteria.maxDepth) {
                return@filter false
            }
            
            // 边界匹配
            if (criteria.bounds != null) {
                val bounds = criteria.bounds
                if (element.bounds.left < bounds.left || element.bounds.top < bounds.top ||
                    element.bounds.right > bounds.right || element.bounds.bottom > bounds.bottom) {
                    return@filter false
                }
            }
            
            true
        }
    }
    
    /**
     * 根据坐标查找元素
     */
    fun findElementAtPosition(x: Int, y: Int): UIElementInfo? {
        return _elements.value.find { element ->
            x >= element.bounds.left && x <= element.bounds.right &&
            y >= element.bounds.top && y <= element.bounds.bottom
        }
    }
    
    /**
     * 获取元素的子元素
     */
    fun getChildElements(elementId: String): List<UIElementInfo> {
        val hierarchy = _hierarchy.value ?: return emptyList()
        val node = hierarchy.findById(elementId) ?: return emptyList()
        return node.children.map { it.element }
    }
    
    /**
     * 获取元素的父元素
     */
    fun getParentElement(elementId: String): UIElementInfo? {
        val hierarchy = _hierarchy.value ?: return null
        val node = hierarchy.findById(elementId) ?: return null
        return node.parent?.element
    }
    
    /**
     * 获取元素路径
     */
    fun getElementPath(elementId: String): List<UIElementInfo> {
        val hierarchy = _hierarchy.value ?: return emptyList()
        val node = hierarchy.findById(elementId) ?: return emptyList()
        return node.getPath().map { it.element }
    }
    
    /**
     * 高亮元素
     */
    fun highlightElement(elementId: String) {
        val currentHighlighted = _highlightedElements.value.toMutableSet()
        currentHighlighted.add(elementId)
        _highlightedElements.value = currentHighlighted
    }
    
    /**
     * 取消高亮元素
     */
    fun unhighlightElement(elementId: String) {
        val currentHighlighted = _highlightedElements.value.toMutableSet()
        currentHighlighted.remove(elementId)
        _highlightedElements.value = currentHighlighted
    }
    
    /**
     * 清除所有高亮
     */
    fun clearHighlights() {
        _highlightedElements.value = emptySet()
    }
    
    /**
     * 选择元素
     */
    fun selectElement(elementId: String) {
        val element = _elements.value.find { it.id == elementId }
        _selectedElement.value = element
    }
    
    /**
     * 取消选择
     */
    fun clearSelection() {
        _selectedElement.value = null
    }
    
    /**
     * 按类型过滤元素
     */
    fun getElementsByType(type: String): List<UIElementInfo> {
        return when (type.lowercase()) {
            "clickable" -> _elements.value.filter { it.isClickable }
            "scrollable" -> _elements.value.filter { it.isScrollable }
            "editable" -> _elements.value.filter { it.properties["isEditable"] == "true" }
            "text" -> _elements.value.filter { !it.text.isNullOrBlank() }
            "button" -> _elements.value.filter { it.className?.contains("Button") == true }
            "image" -> _elements.value.filter { 
                it.className?.contains("Image") == true || it.className?.contains("Icon") == true 
            }
            "input" -> _elements.value.filter { 
                it.className?.contains("Edit") == true || it.properties["isEditable"] == "true"
            }
            else -> emptyList()
        }
    }
    
    /**
     * 获取可点击元素
     */
    fun getClickableElements(): List<UIElementInfo> {
        return _elements.value.filter { it.isClickable && it.isEnabled }
    }
    
    /**
     * 获取可滚动元素
     */
    fun getScrollableElements(): List<UIElementInfo> {
        return _elements.value.filter { it.isScrollable }
    }
    
    /**
     * 导出分析结果
     */
    fun exportAnalysisResult(): Map<String, Any> {
        val hierarchy = _hierarchy.value
        val elements = _elements.value
        val stats = _stats.value
        
        return mapOf(
            "timestamp" to System.currentTimeMillis(),
            "hierarchy" to (hierarchy?.let { serializeHierarchy(it) } ?: emptyMap()),
            "elements" to elements,
            "stats" to (stats ?: UIAnalysisStats(0, 0, 0, 0, 0, 0, 0, 0, emptyMap(), emptyMap(), emptyMap())),
            "highlighted" to _highlightedElements.value,
            "selected" to _selectedElement.value
        )
    }
    
    /**
     * 序列化层次结构
     */
    private fun serializeHierarchy(node: UIHierarchyNode): Map<String, Any> {
        return mapOf(
            "element" to node.element,
            "children" to node.children.map { serializeHierarchy(it) }
        )
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        currentRootNode?.recycle()
        currentRootNode = null
        _hierarchy.value = null
        _elements.value = emptyList()
        _stats.value = null
        _highlightedElements.value = emptySet()
        _selectedElement.value = null
    }
}