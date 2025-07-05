package com.ai.assistance.operit.core.agent.permission

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ai.assistance.operit.services.UIAccessibilityService
import com.ai.assistance.operit.util.LogUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AI Agent权限配置辅助工具
 * 
 * 提供：
 * 1. 权限状态检查
 * 2. 权限配置引导
 * 3. 一键跳转设置
 * 4. 权限验证
 */
class AIAgentPermissionHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "AIAgentPermissionHelper"
        
        @Volatile
        private var INSTANCE: AIAgentPermissionHelper? = null
        
        fun getInstance(context: Context): AIAgentPermissionHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AIAgentPermissionHelper(context.applicationContext).also { 
                    INSTANCE = it 
                }
            }
        }
    }
    
    /**
     * 权限项定义
     */
    data class PermissionItem(
        val name: String,
        val description: String,
        val icon: ImageVector,
        val isRequired: Boolean,
        val checkStatus: () -> Boolean,
        val openSettings: () -> Unit
    )
    
    /**
     * 权限状态
     */
    data class PermissionStatus(
        val isAccessibilityEnabled: Boolean = false,
        val isOverlayPermissionGranted: Boolean = false,
        val lastCheckTime: Long = System.currentTimeMillis()
    )
    
    private val _permissionStatus = MutableStateFlow(PermissionStatus())
    val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus.asStateFlow()
    
    /**
     * 检查所有权限状态
     */
    fun checkAllPermissions(): PermissionStatus {
        val status = PermissionStatus(
            isAccessibilityEnabled = UIAccessibilityService.isRunning(),
            isOverlayPermissionGranted = Settings.canDrawOverlays(context),
            lastCheckTime = System.currentTimeMillis()
        )
        
        _permissionStatus.value = status
        
        LogUtils.i(TAG, buildString {
            appendLine("权限状态检查结果:")
            appendLine("📱 无障碍服务: ${if (status.isAccessibilityEnabled) "✅ 已启用" else "❌ 未启用"}")
            appendLine("🎭 悬浮窗权限: ${if (status.isOverlayPermissionGranted) "✅ 已授予" else "❌ 未授予"}")
        })
        
        return status
    }
    
    /**
     * 获取权限项列表
     */
    fun getPermissionItems(): List<PermissionItem> {
        return listOf(
            PermissionItem(
                name = "无障碍服务",
                description = "用于感知屏幕信息和执行操作，是AI Agent的核心功能",
                icon = Icons.Default.Accessibility,
                isRequired = true,
                checkStatus = { UIAccessibilityService.isRunning() },
                openSettings = { openAccessibilitySettings() }
            ),
            PermissionItem(
                name = "悬浮窗权限",
                description = "用于显示操作反馈和浮动控制界面",
                icon = Icons.Default.Window,
                isRequired = true,
                checkStatus = { Settings.canDrawOverlays(context) },
                openSettings = { openOverlaySettings() }
            )
        )
    }
    
    /**
     * 打开无障碍设置
     */
    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            
            LogUtils.i(TAG, "已打开无障碍设置界面")
            
            // 显示引导提示
            showAccessibilityGuide()
            
        } catch (e: Exception) {
            LogUtils.e(TAG, "打开无障碍设置失败", e)
            Toast.makeText(context, "无法打开无障碍设置，请手动前往", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 打开悬浮窗权限设置
     */
    private fun openOverlaySettings() {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            
            LogUtils.i(TAG, "已打开悬浮窗权限设置界面")
            
            // 显示引导提示
            showOverlayGuide()
            
        } catch (e: Exception) {
            LogUtils.e(TAG, "打开悬浮窗权限设置失败", e)
            Toast.makeText(context, "无法打开悬浮窗权限设置，请手动前往", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 显示无障碍服务配置引导
     */
    private fun showAccessibilityGuide() {
        try {
            AlertDialog.Builder(context)
                .setTitle("🤖 配置无障碍服务")
                .setMessage("""
                    请按照以下步骤操作：
                    
                    1️⃣ 在无障碍设置中找到 "Operit AI"
                    2️⃣ 点击进入服务详情
                    3️⃣ 打开服务开关
                    4️⃣ 在弹出的对话框中点击"确定"
                    
                    ⚠️ 注意：启用后可能需要重启应用
                """.trimIndent())
                .setPositiveButton("我知道了") { dialog, _ ->
                    dialog.dismiss()
                }
                .setNegativeButton("查看帮助") { _, _ ->
                    showDetailedAccessibilityHelp()
                }
                .show()
        } catch (e: Exception) {
            LogUtils.e(TAG, "显示无障碍引导失败", e)
        }
    }
    
    /**
     * 显示悬浮窗权限配置引导
     */
    private fun showOverlayGuide() {
        try {
            AlertDialog.Builder(context)
                .setTitle("🎭 配置悬浮窗权限")
                .setMessage("""
                    请按照以下步骤操作：
                    
                    1️⃣ 在权限设置中找到 "显示在其他应用的上层"
                    2️⃣ 打开权限开关
                    3️⃣ 返回应用即可
                    
                    💡 提示：此权限用于显示操作反馈和浮动控制界面
                """.trimIndent())
                .setPositiveButton("我知道了") { dialog, _ ->
                    dialog.dismiss()
                }
                .setNegativeButton("查看帮助") { _, _ ->
                    showDetailedOverlayHelp()
                }
                .show()
        } catch (e: Exception) {
            LogUtils.e(TAG, "显示悬浮窗引导失败", e)
        }
    }
    
    /**
     * 显示详细的无障碍服务帮助
     */
    private fun showDetailedAccessibilityHelp() {
        try {
            AlertDialog.Builder(context)
                .setTitle("📚 无障碍服务详细说明")
                .setMessage("""
                    🔍 什么是无障碍服务？
                    无障碍服务是Android系统提供的功能，允许应用程序代表用户执行操作。
                    
                    🤖 AI Agent如何使用？
                    • 读取屏幕上的元素信息
                    • 执行点击、滑动等操作
                    • 获取应用状态和内容
                    
                    🔒 安全性保障：
                    • 只在用户明确指令下执行操作
                    • 所有操作都有安全检查机制
                    • 可以随时停止或撤销授权
                    
                    ❓ 常见问题：
                    • 找不到"Operit AI"？请确保应用已正确安装
                    • 开关无法打开？请检查设备是否支持
                    • 设置后不生效？请重启应用试试
                """.trimIndent())
                .setPositiveButton("明白了") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        } catch (e: Exception) {
            LogUtils.e(TAG, "显示无障碍详细帮助失败", e)
        }
    }
    
    /**
     * 显示详细的悬浮窗权限帮助
     */
    private fun showDetailedOverlayHelp() {
        try {
            AlertDialog.Builder(context)
                .setTitle("📚 悬浮窗权限详细说明")
                .setMessage("""
                    🎭 什么是悬浮窗权限？
                    允许应用在其他应用上方显示内容的权限。
                    
                    🎯 AI Agent如何使用？
                    • 显示操作反馈（点击、滑动效果）
                    • 显示浮动聊天窗口
                    • 显示AI思考过程
                    • 显示任务执行状态
                    
                    🔒 隐私保护：
                    • 只显示操作相关信息
                    • 不会截取或记录其他应用内容
                    • 可以随时关闭或撤销权限
                    
                    ❓ 常见问题：
                    • 找不到权限设置？请在应用管理中查找
                    • 权限无法授予？请检查设备安全设置
                    • 悬浮窗不显示？请检查勿扰模式设置
                """.trimIndent())
                .setPositiveButton("明白了") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        } catch (e: Exception) {
            LogUtils.e(TAG, "显示悬浮窗详细帮助失败", e)
        }
    }
    
    /**
     * 一键配置所有权限
     */
    fun requestAllPermissions() {
        LogUtils.i(TAG, "开始一键配置所有权限")
        
        val status = checkAllPermissions()
        val missingPermissions = mutableListOf<String>()
        
        if (!status.isAccessibilityEnabled) {
            missingPermissions.add("无障碍服务")
        }
        
        if (!status.isOverlayPermissionGranted) {
            missingPermissions.add("悬浮窗权限")
        }
        
        when {
            missingPermissions.isEmpty() -> {
                Toast.makeText(context, "🎉 所有权限已配置完成！", Toast.LENGTH_SHORT).show()
                LogUtils.i(TAG, "所有权限已就绪")
            }
            missingPermissions.size == 1 -> {
                Toast.makeText(context, "还需要配置: ${missingPermissions[0]}", Toast.LENGTH_LONG).show()
                // 直接打开对应设置
                when (missingPermissions[0]) {
                    "无障碍服务" -> openAccessibilitySettings()
                    "悬浮窗权限" -> openOverlaySettings()
                }
            }
            else -> {
                // 显示批量配置引导
                showBatchConfigurationGuide(missingPermissions)
            }
        }
    }
    
    /**
     * 显示批量配置引导
     */
    private fun showBatchConfigurationGuide(missingPermissions: List<String>) {
        try {
            val message = buildString {
                appendLine("🔧 需要配置以下权限以启用AI Agent：")
                appendLine()
                missingPermissions.forEach { permission ->
                    appendLine("❌ $permission")
                }
                appendLine()
                appendLine("建议按顺序逐一配置，配置完成后返回应用验证。")
            }
            
            AlertDialog.Builder(context)
                .setTitle("权限配置向导")
                .setMessage(message)
                .setPositiveButton("开始配置") { _, _ ->
                    // 先配置无障碍服务
                    if (missingPermissions.contains("无障碍服务")) {
                        openAccessibilitySettings()
                    } else {
                        openOverlaySettings()
                    }
                }
                .setNegativeButton("稍后配置") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        } catch (e: Exception) {
            LogUtils.e(TAG, "显示批量配置引导失败", e)
        }
    }
    
    /**
     * 验证权限配置结果
     */
    fun validatePermissions(): Boolean {
        val status = checkAllPermissions()
        val isValid = status.isAccessibilityEnabled && status.isOverlayPermissionGranted
        
        if (isValid) {
            Toast.makeText(context, "🎉 权限配置验证通过！AI Agent已准备就绪", Toast.LENGTH_LONG).show()
            LogUtils.i(TAG, "权限验证通过，AI Agent可以正常工作")
        } else {
            val missing = mutableListOf<String>()
            if (!status.isAccessibilityEnabled) missing.add("无障碍服务")
            if (!status.isOverlayPermissionGranted) missing.add("悬浮窗权限")
            
            Toast.makeText(context, "⚠️ 还需要配置: ${missing.joinToString(", ")}", Toast.LENGTH_LONG).show()
            LogUtils.w(TAG, "权限验证失败，缺少: ${missing.joinToString(", ")}")
        }
        
        return isValid
    }
    
    /**
     * 获取权限配置进度
     */
    fun getConfigurationProgress(): Float {
        val status = checkAllPermissions()
        val total = 2f
        var configured = 0f
        
        if (status.isAccessibilityEnabled) configured += 1f
        if (status.isOverlayPermissionGranted) configured += 1f
        
        return configured / total
    }
    
    /**
     * 生成权限状态报告
     */
    fun generateStatusReport(): String {
        val status = checkAllPermissions()
        val progress = getConfigurationProgress()
        val percentage = (progress * 100).toInt()
        
        return buildString {
            appendLine("📋 AI Agent 权限配置报告")
            appendLine("=" * 40)
            appendLine("生成时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}")
            appendLine()
            
            appendLine("🔐 权限状态:")
            appendLine("  📱 无障碍服务: ${if (status.isAccessibilityEnabled) "✅ 已启用" else "❌ 未启用"}")
            appendLine("  🎭 悬浮窗权限: ${if (status.isOverlayPermissionGranted) "✅ 已授予" else "❌ 未授予"}")
            appendLine()
            
            appendLine("📊 配置进度: $percentage% (${if (progress == 1f) "完成" else "进行中"})")
            appendLine()
            
            when {
                percentage == 100 -> {
                    appendLine("✨ 状态: 完美！AI Agent已准备就绪")
                    appendLine("🚀 建议: 现在可以开始使用AI Agent的所有功能")
                }
                percentage >= 50 -> {
                    appendLine("⚠️ 状态: 部分功能可用")
                    appendLine("🔧 建议: 请完成剩余权限配置以获得最佳体验")
                }
                else -> {
                    appendLine("❌ 状态: 功能受限")
                    appendLine("🔧 建议: 请尽快配置必要权限")
                }
            }
            
            appendLine()
            appendLine("=" * 40)
        }
    }
}

/**
 * AI Agent权限配置UI组件
 */
@Composable
fun AIAgentPermissionScreen(
    permissionHelper: AIAgentPermissionHelper = AIAgentPermissionHelper.getInstance(LocalContext.current)
) {
    val context = LocalContext.current
    val permissionStatus by permissionHelper.permissionStatus.collectAsStateWithLifecycle()
    val permissionItems = remember { permissionHelper.getPermissionItems() }
    
    // 定期检查权限状态
    LaunchedEffect(Unit) {
        permissionHelper.checkAllPermissions()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 标题
        Text(
            text = "🤖 AI Agent 权限配置",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        // 进度指示器
        val progress = permissionHelper.getConfigurationProgress()
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth()
        )
        
        Text(
            text = "配置进度: ${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // 权限列表
        permissionItems.forEach { item ->
            PermissionItemCard(
                item = item,
                isGranted = item.checkStatus()
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // 操作按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = { permissionHelper.checkAllPermissions() },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("刷新状态")
            }
            
            Button(
                onClick = { permissionHelper.requestAllPermissions() },
                modifier = Modifier.weight(1f),
                enabled = progress < 1f
            ) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (progress < 1f) "配置权限" else "已完成")
            }
        }
        
        // 验证按钮（仅在全部配置完成后显示）
        if (progress == 1f) {
            Button(
                onClick = { 
                    if (permissionHelper.validatePermissions()) {
                        Toast.makeText(context, "🎉 AI Agent已准备就绪！", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("验证并启用AI Agent")
            }
        }
    }
}

/**
 * 权限项卡片组件
 */
@Composable
private fun PermissionItemCard(
    item: AIAgentPermissionHelper.PermissionItem,
    isGranted: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = if (isGranted) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 信息
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    if (item.isRequired) {
                        Text(
                            text = "必需",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.errorContainer,
                                    MaterialTheme.shapes.small
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 状态和操作
            Column(
                horizontalAlignment = Alignment.End
            ) {
                // 状态指示器
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (isGranted) Color(0xFF4CAF50) else Color(0xFFF44336),
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Text(
                        text = if (isGranted) "已授予" else "未授予",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isGranted) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 操作按钮
                if (!isGranted) {
                    TextButton(onClick = item.openSettings) {
                        Text("去设置")
                    }
                }
            }
        }
    }
}