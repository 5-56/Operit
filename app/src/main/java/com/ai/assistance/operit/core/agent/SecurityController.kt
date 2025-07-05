package com.ai.assistance.operit.core.agent

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import androidx.core.content.ContextCompat
import com.ai.assistance.operit.services.UIAccessibilityService
import com.ai.assistance.operit.util.LogUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * 安全控制器
 * 
 * 负责AI Agent的安全控制和权限管理：
 * 1. 操作权限验证
 * 2. 敏感区域保护
 * 3. 恶意行为检测
 * 4. 操作审计日志
 * 5. 安全策略管理
 * 6. 权限级别控制
 */
class SecurityController(private val context: Context) {
    
    companion object {
        private const val TAG = "SecurityController"
        
        // 安全级别
        enum class SecurityLevel {
            LOW,     // 低安全级别，允许大部分操作
            MEDIUM,  // 中等安全级别，限制敏感操作
            HIGH,    // 高安全级别，严格限制操作
            STRICT   // 严格模式，仅允许基本操作
        }
        
        // 操作类型
        enum class OperationType {
            TAP,
            SWIPE,
            INPUT_TEXT,
            PRESS_KEY,
            LONG_PRESS,
            SCROLL,
            CUSTOM
        }
        
        // 安全威胁类型
        enum class ThreatType {
            SENSITIVE_AREA_ACCESS,    // 敏感区域访问
            EXCESSIVE_OPERATIONS,     // 过度操作
            MALICIOUS_INPUT,         // 恶意输入
            UNAUTHORIZED_ACCESS,     // 未授权访问
            SUSPICIOUS_BEHAVIOR,     // 可疑行为
            PERMISSION_VIOLATION     // 权限违规
        }
    }
    
    // 当前安全级别
    private val _currentSecurityLevel = MutableStateFlow(SecurityLevel.MEDIUM)
    val currentSecurityLevel: StateFlow<SecurityLevel> = _currentSecurityLevel.asStateFlow()
    
    // 安全事件记录
    private val securityEvents = mutableListOf<SecurityEvent>()
    private val operationHistory = mutableListOf<OperationRecord>()
    
    // 敏感区域定义
    private val sensitiveAreas = mutableSetOf<SensitiveArea>()
    
    // 被禁止的包名和活动
    private val blockedPackages = mutableSetOf<String>()
    private val blockedActivities = mutableSetOf<String>()
    
    // 操作频率限制
    private val operationCounts = ConcurrentHashMap<OperationType, Int>()
    private var lastOperationTime = System.currentTimeMillis()
    
    /**
     * 安全事件
     */
    data class SecurityEvent(
        val threatType: ThreatType,
        val description: String,
        val timestamp: Long = System.currentTimeMillis(),
        val severity: Int, // 1-10, 10为最严重
        val context: Map<String, String> = emptyMap()
    )
    
    /**
     * 操作记录
     */
    data class OperationRecord(
        val operationType: OperationType,
        val target: String,
        val parameters: Map<String, String>,
        val timestamp: Long = System.currentTimeMillis(),
        val userId: String? = null,
        val approved: Boolean = true
    )
    
    /**
     * 敏感区域
     */
    data class SensitiveArea(
        val name: String,
        val bounds: Rect,
        val packageName: String? = null,
        val description: String,
        val securityLevel: SecurityLevel = SecurityLevel.HIGH
    )
    
    /**
     * 安全验证结果
     */
    data class SecurityValidationResult(
        val allowed: Boolean,
        val reason: String,
        val threatType: ThreatType? = null,
        val suggestedAction: String? = null
    )
    
    init {
        initializeSecuritySettings()
        setupDefaultSensitiveAreas()
        setupDefaultBlockedPackages()
    }
    
    /**
     * 初始化安全设置
     */
    private fun initializeSecuritySettings() {
        // 从SharedPreferences加载安全设置
        val prefs = context.getSharedPreferences("security_settings", Context.MODE_PRIVATE)
        val savedLevel = prefs.getString("security_level", SecurityLevel.MEDIUM.name)
        _currentSecurityLevel.value = SecurityLevel.valueOf(savedLevel ?: SecurityLevel.MEDIUM.name)
        
        LogUtils.i(TAG, "安全控制器初始化完成，当前安全级别: ${_currentSecurityLevel.value}")
    }
    
    /**
     * 设置默认敏感区域
     */
    private fun setupDefaultSensitiveAreas() {
        // 系统设置相关敏感区域
        sensitiveAreas.addAll(listOf(
            SensitiveArea(
                name = "系统设置",
                bounds = Rect(0, 0, Int.MAX_VALUE, Int.MAX_VALUE),
                packageName = "com.android.settings",
                description = "系统设置应用，包含重要系统配置",
                securityLevel = SecurityLevel.HIGH
            ),
            SensitiveArea(
                name = "开发者选项",
                bounds = Rect(0, 0, Int.MAX_VALUE, Int.MAX_VALUE),
                packageName = "com.android.settings",
                description = "开发者选项页面",
                securityLevel = SecurityLevel.STRICT
            ),
            SensitiveArea(
                name = "应用权限管理",
                bounds = Rect(0, 0, Int.MAX_VALUE, Int.MAX_VALUE),
                packageName = "com.android.permissioncontroller",
                description = "应用权限管理界面",
                securityLevel = SecurityLevel.HIGH
            ),
            SensitiveArea(
                name = "安全设置",
                bounds = Rect(0, 0, Int.MAX_VALUE, Int.MAX_VALUE),
                packageName = "com.android.settings",
                description = "安全相关设置页面",
                securityLevel = SecurityLevel.STRICT
            )
        ))
        
        LogUtils.d(TAG, "已设置 ${sensitiveAreas.size} 个默认敏感区域")
    }
    
    /**
     * 设置默认被禁止的包
     */
    private fun setupDefaultBlockedPackages() {
        blockedPackages.addAll(listOf(
            "com.android.settings", // 系统设置（需要额外验证）
            "com.android.systemui", // 系统UI
            "android", // 系统核心
            "com.android.permissioncontroller" // 权限控制器
        ))
        
        LogUtils.d(TAG, "已设置 ${blockedPackages.size} 个被限制的包")
    }
    
    /**
     * 验证操作安全性
     */
    fun validateOperation(
        operationType: OperationType,
        target: String,
        parameters: Map<String, String>,
        currentApp: String?
    ): SecurityValidationResult {
        
        LogUtils.d(TAG, "验证操作安全性: $operationType -> $target")
        
        try {
            // 1. 检查基本权限
            val permissionCheck = checkBasicPermissions()
            if (!permissionCheck.allowed) {
                return permissionCheck
            }
            
            // 2. 检查安全级别限制
            val securityLevelCheck = checkSecurityLevelConstraints(operationType, currentApp)
            if (!securityLevelCheck.allowed) {
                return securityLevelCheck
            }
            
            // 3. 检查敏感区域
            val sensitiveAreaCheck = checkSensitiveAreas(operationType, target, parameters, currentApp)
            if (!sensitiveAreaCheck.allowed) {
                return sensitiveAreaCheck
            }
            
            // 4. 检查被禁止的包
            val blockedPackageCheck = checkBlockedPackages(currentApp)
            if (!blockedPackageCheck.allowed) {
                return blockedPackageCheck
            }
            
            // 5. 检查操作频率
            val frequencyCheck = checkOperationFrequency(operationType)
            if (!frequencyCheck.allowed) {
                return frequencyCheck
            }
            
            // 6. 检查恶意行为模式
            val maliciousCheck = checkMaliciousBehavior(operationType, target, parameters)
            if (!maliciousCheck.allowed) {
                return maliciousCheck
            }
            
            // 7. 检查输入内容安全性
            if (operationType == OperationType.INPUT_TEXT) {
                val inputCheck = validateInputContent(parameters["text"] ?: "")
                if (!inputCheck.allowed) {
                    return inputCheck
                }
            }
            
            // 记录操作
            recordOperation(operationType, target, parameters)
            
            LogUtils.d(TAG, "操作验证通过: $operationType")
            return SecurityValidationResult(
                allowed = true,
                reason = "操作通过安全验证"
            )
            
        } catch (e: Exception) {
            LogUtils.e(TAG, "安全验证过程中出现异常", e)
            recordSecurityEvent(
                ThreatType.PERMISSION_VIOLATION,
                "安全验证异常: ${e.message}",
                severity = 8
            )
            return SecurityValidationResult(
                allowed = false,
                reason = "安全验证异常",
                threatType = ThreatType.PERMISSION_VIOLATION
            )
        }
    }
    
    /**
     * 检查基本权限
     */
    private fun checkBasicPermissions(): SecurityValidationResult {
        // 检查无障碍服务权限
        if (!UIAccessibilityService.isRunning()) {
            return SecurityValidationResult(
                allowed = false,
                reason = "无障碍服务未启用",
                threatType = ThreatType.UNAUTHORIZED_ACCESS,
                suggestedAction = "请启用无障碍服务"
            )
        }
        
        // 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(context)) {
                LogUtils.w(TAG, "悬浮窗权限未授予，部分功能可能受限")
            }
        }
        
        return SecurityValidationResult(allowed = true, reason = "基本权限检查通过")
    }
    
    /**
     * 检查安全级别约束
     */
    private fun checkSecurityLevelConstraints(
        operationType: OperationType,
        currentApp: String?
    ): SecurityValidationResult {
        
        val currentLevel = _currentSecurityLevel.value
        
        return when (currentLevel) {
            SecurityLevel.LOW -> {
                // 低安全级别，允许大部分操作
                SecurityValidationResult(allowed = true, reason = "低安全级别，操作允许")
            }
            
            SecurityLevel.MEDIUM -> {
                // 中等安全级别，限制部分敏感操作
                when (operationType) {
                    OperationType.PRESS_KEY -> {
                        SecurityValidationResult(
                            allowed = false,
                            reason = "中等安全级别不允许按键操作",
                            threatType = ThreatType.PERMISSION_VIOLATION
                        )
                    }
                    else -> SecurityValidationResult(allowed = true, reason = "中等安全级别，操作允许")
                }
            }
            
            SecurityLevel.HIGH -> {
                // 高安全级别，严格限制操作
                when (operationType) {
                    OperationType.TAP, OperationType.SCROLL -> {
                        SecurityValidationResult(allowed = true, reason = "高安全级别，基本操作允许")
                    }
                    else -> {
                        SecurityValidationResult(
                            allowed = false,
                            reason = "高安全级别不允许此类操作",
                            threatType = ThreatType.PERMISSION_VIOLATION
                        )
                    }
                }
            }
            
            SecurityLevel.STRICT -> {
                // 严格模式，仅允许最基本操作
                if (operationType == OperationType.TAP) {
                    SecurityValidationResult(allowed = true, reason = "严格模式，仅允许点击操作")
                } else {
                    SecurityValidationResult(
                        allowed = false,
                        reason = "严格模式不允许此操作",
                        threatType = ThreatType.PERMISSION_VIOLATION
                    )
                }
            }
        }
    }
    
    /**
     * 检查敏感区域
     */
    private fun checkSensitiveAreas(
        operationType: OperationType,
        target: String,
        parameters: Map<String, String>,
        currentApp: String?
    ): SecurityValidationResult {
        
        // 检查是否在敏感应用中
        val relevantAreas = sensitiveAreas.filter { area ->
            area.packageName == currentApp
        }
        
        if (relevantAreas.isNotEmpty()) {
            val highestSecurityArea = relevantAreas.maxByOrNull { area ->
                area.securityLevel.ordinal
            }
            
            if (highestSecurityArea != null) {
                val requiredLevel = highestSecurityArea.securityLevel
                val currentLevel = _currentSecurityLevel.value
                
                if (currentLevel.ordinal < requiredLevel.ordinal) {
                    recordSecurityEvent(
                        ThreatType.SENSITIVE_AREA_ACCESS,
                        "尝试在敏感区域执行操作: ${highestSecurityArea.name}",
                        severity = 7
                    )
                    
                    return SecurityValidationResult(
                        allowed = false,
                        reason = "当前安全级别不足以访问敏感区域: ${highestSecurityArea.name}",
                        threatType = ThreatType.SENSITIVE_AREA_ACCESS,
                        suggestedAction = "提升安全级别或获得特殊权限"
                    )
                }
            }
        }
        
        return SecurityValidationResult(allowed = true, reason = "敏感区域检查通过")
    }
    
    /**
     * 检查被禁止的包
     */
    private fun checkBlockedPackages(currentApp: String?): SecurityValidationResult {
        if (currentApp != null && blockedPackages.contains(currentApp)) {
            recordSecurityEvent(
                ThreatType.UNAUTHORIZED_ACCESS,
                "尝试访问被禁止的应用: $currentApp",
                severity = 8
            )
            
            return SecurityValidationResult(
                allowed = false,
                reason = "当前应用被列为受限制应用",
                threatType = ThreatType.UNAUTHORIZED_ACCESS,
                suggestedAction = "获得特殊权限或更改目标应用"
            )
        }
        
        return SecurityValidationResult(allowed = true, reason = "应用访问检查通过")
    }
    
    /**
     * 检查操作频率
     */
    private fun checkOperationFrequency(operationType: OperationType): SecurityValidationResult {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastOperation = currentTime - lastOperationTime
        
        // 防止操作过于频繁
        if (timeSinceLastOperation < 100) { // 100ms内不允许重复操作
            recordSecurityEvent(
                ThreatType.EXCESSIVE_OPERATIONS,
                "操作频率过高: ${operationType}",
                severity = 5
            )
            
            return SecurityValidationResult(
                allowed = false,
                reason = "操作频率过高，请稍后再试",
                threatType = ThreatType.EXCESSIVE_OPERATIONS
            )
        }
        
        // 更新操作计数
        val currentCount = operationCounts.getOrDefault(operationType, 0)
        operationCounts[operationType] = currentCount + 1
        lastOperationTime = currentTime
        
        // 检查5分钟内的操作次数
        if (currentCount > 100) { // 5分钟内超过100次操作
            recordSecurityEvent(
                ThreatType.EXCESSIVE_OPERATIONS,
                "5分钟内操作次数过多: ${operationType} ($currentCount 次)",
                severity = 7
            )
            
            return SecurityValidationResult(
                allowed = false,
                reason = "操作次数过多，可能存在异常行为",
                threatType = ThreatType.EXCESSIVE_OPERATIONS
            )
        }
        
        return SecurityValidationResult(allowed = true, reason = "操作频率检查通过")
    }
    
    /**
     * 检查恶意行为模式
     */
    private fun checkMaliciousBehavior(
        operationType: OperationType,
        target: String,
        parameters: Map<String, String>
    ): SecurityValidationResult {
        
        // 检查可疑的坐标模式
        if (operationType == OperationType.TAP) {
            val x = parameters["x"]?.toIntOrNull()
            val y = parameters["y"]?.toIntOrNull()
            
            if (x != null && y != null) {
                // 检查是否点击了异常位置（如系统边缘）
                if (x < 50 || y < 50) {
                    recordSecurityEvent(
                        ThreatType.SUSPICIOUS_BEHAVIOR,
                        "可疑的点击位置: ($x, $y)",
                        severity = 4
                    )
                }
            }
        }
        
        // 检查连续相同操作（可能的脚本行为）
        val recentOperations = operationHistory.takeLast(5)
        if (recentOperations.size >= 5 && recentOperations.all { it.operationType == operationType }) {
            recordSecurityEvent(
                ThreatType.SUSPICIOUS_BEHAVIOR,
                "检测到连续相同操作模式: $operationType",
                severity = 6
            )
        }
        
        return SecurityValidationResult(allowed = true, reason = "恶意行为检查通过")
    }
    
    /**
     * 验证输入内容安全性
     */
    private fun validateInputContent(text: String): SecurityValidationResult {
        // 检查恶意脚本或代码注入
        val dangerousPatterns = listOf(
            Pattern.compile("<script.*?>.*?</script>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("eval\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("exec\\s*\\(", Pattern.CASE_INSENSITIVE)
        )
        
        for (pattern in dangerousPatterns) {
            if (pattern.matcher(text).find()) {
                recordSecurityEvent(
                    ThreatType.MALICIOUS_INPUT,
                    "检测到潜在恶意输入: ${text.take(50)}...",
                    severity = 8
                )
                
                return SecurityValidationResult(
                    allowed = false,
                    reason = "输入内容包含潜在恶意代码",
                    threatType = ThreatType.MALICIOUS_INPUT
                )
            }
        }
        
        // 检查过长的输入（可能的缓冲区溢出攻击）
        if (text.length > 10000) {
            recordSecurityEvent(
                ThreatType.MALICIOUS_INPUT,
                "输入内容过长: ${text.length} 字符",
                severity = 6
            )
            
            return SecurityValidationResult(
                allowed = false,
                reason = "输入内容过长，可能存在安全风险",
                threatType = ThreatType.MALICIOUS_INPUT
            )
        }
        
        return SecurityValidationResult(allowed = true, reason = "输入内容安全检查通过")
    }
    
    /**
     * 记录安全事件
     */
    private fun recordSecurityEvent(
        threatType: ThreatType,
        description: String,
        severity: Int,
        context: Map<String, String> = emptyMap()
    ) {
        val event = SecurityEvent(threatType, description, severity = severity, context = context)
        securityEvents.add(event)
        
        // 保持最近1000个事件
        if (securityEvents.size > 1000) {
            securityEvents.removeAt(0)
        }
        
        LogUtils.w(TAG, "安全事件: [$threatType] $description (严重程度: $severity)")
    }
    
    /**
     * 记录操作
     */
    private fun recordOperation(
        operationType: OperationType,
        target: String,
        parameters: Map<String, String>
    ) {
        val record = OperationRecord(operationType, target, parameters)
        operationHistory.add(record)
        
        // 保持最近500个操作记录
        if (operationHistory.size > 500) {
            operationHistory.removeAt(0)
        }
    }
    
    /**
     * 设置安全级别
     */
    fun setSecurityLevel(level: SecurityLevel) {
        val oldLevel = _currentSecurityLevel.value
        _currentSecurityLevel.value = level
        
        // 保存到SharedPreferences
        val prefs = context.getSharedPreferences("security_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("security_level", level.name).apply()
        
        LogUtils.i(TAG, "安全级别从 $oldLevel 更改为 $level")
        
        recordSecurityEvent(
            ThreatType.PERMISSION_VIOLATION,
            "安全级别更改: $oldLevel -> $level",
            severity = 3,
            context = mapOf("old_level" to oldLevel.name, "new_level" to level.name)
        )
    }
    
    /**
     * 添加敏感区域
     */
    fun addSensitiveArea(area: SensitiveArea) {
        sensitiveAreas.add(area)
        LogUtils.i(TAG, "添加敏感区域: ${area.name}")
    }
    
    /**
     * 移除敏感区域
     */
    fun removeSensitiveArea(name: String) {
        sensitiveAreas.removeIf { it.name == name }
        LogUtils.i(TAG, "移除敏感区域: $name")
    }
    
    /**
     * 添加被禁止的包
     */
    fun addBlockedPackage(packageName: String) {
        blockedPackages.add(packageName)
        LogUtils.i(TAG, "添加被禁止的包: $packageName")
    }
    
    /**
     * 移除被禁止的包
     */
    fun removeBlockedPackage(packageName: String) {
        blockedPackages.remove(packageName)
        LogUtils.i(TAG, "移除被禁止的包: $packageName")
    }
    
    /**
     * 清除操作计数（通常每5分钟调用一次）
     */
    fun clearOperationCounts() {
        operationCounts.clear()
        LogUtils.d(TAG, "操作计数已清零")
    }
    
    /**
     * 获取安全事件列表
     */
    fun getSecurityEvents(): List<SecurityEvent> {
        return securityEvents.toList()
    }
    
    /**
     * 获取最近的高危安全事件
     */
    fun getHighSeverityEvents(minSeverity: Int = 7): List<SecurityEvent> {
        return securityEvents.filter { it.severity >= minSeverity }
    }
    
    /**
     * 获取操作历史
     */
    fun getOperationHistory(): List<OperationRecord> {
        return operationHistory.toList()
    }
    
    /**
     * 生成安全报告
     */
    fun generateSecurityReport(): String {
        val currentTime = System.currentTimeMillis()
        val recentEvents = securityEvents.filter { currentTime - it.timestamp < 24 * 60 * 60 * 1000 } // 24小时内
        val highSeverityEvents = recentEvents.filter { it.severity >= 7 }
        
        return buildString {
            appendLine("🔒 安全控制器状态报告")
            appendLine("=" * 40)
            appendLine("生成时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}")
            appendLine()
            
            appendLine("📊 基本信息:")
            appendLine("  当前安全级别: ${_currentSecurityLevel.value}")
            appendLine("  敏感区域数量: ${sensitiveAreas.size}")
            appendLine("  被禁止包数量: ${blockedPackages.size}")
            appendLine()
            
            appendLine("📈 24小时内统计:")
            appendLine("  总安全事件: ${recentEvents.size}")
            appendLine("  高危事件: ${highSeverityEvents.size}")
            appendLine("  操作记录: ${operationHistory.size}")
            appendLine()
            
            if (highSeverityEvents.isNotEmpty()) {
                appendLine("⚠️ 最近高危事件:")
                highSeverityEvents.takeLast(5).forEach { event ->
                    appendLine("  [${event.threatType}] ${event.description} (严重程度: ${event.severity})")
                }
                appendLine()
            }
            
            appendLine("🎯 安全建议:")
            when (_currentSecurityLevel.value) {
                SecurityLevel.LOW -> appendLine("  考虑提升安全级别以获得更好的保护")
                SecurityLevel.MEDIUM -> appendLine("  当前安全级别适中，建议定期检查安全事件")
                SecurityLevel.HIGH -> appendLine("  安全级别较高，系统保护良好")
                SecurityLevel.STRICT -> appendLine("  最高安全级别，功能可能受限但安全性最佳")
            }
        }
    }
    
    /**
     * 重置安全设置
     */
    fun resetSecuritySettings() {
        _currentSecurityLevel.value = SecurityLevel.MEDIUM
        securityEvents.clear()
        operationHistory.clear()
        operationCounts.clear()
        
        // 保存默认设置
        val prefs = context.getSharedPreferences("security_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("security_level", SecurityLevel.MEDIUM.name).apply()
        
        LogUtils.i(TAG, "安全设置已重置为默认状态")
    }
}