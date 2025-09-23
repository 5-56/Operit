package com.xihe.assistant.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class NavItem(
    val title: String,
    val icon: ImageVector,
    val description: String
) {
    // AI功能组
    data object AiChat : NavItem(
        title = "AI对话",
        icon = Icons.Default.Chat,
        description = "与AI助手进行智能对话"
    )
    
    data object AssistantConfig : NavItem(
        title = "助手配置",
        icon = Icons.Default.Settings,
        description = "配置AI助手的行为和参数"
    )
    
    data object Packages : NavItem(
        title = "插件管理",
        icon = Icons.Default.Extension,
        description = "管理AI助手插件和扩展"
    )
    
    data object MemoryBase : NavItem(
        title = "记忆库",
        icon = Icons.Default.Memory,
        description = "管理AI助手的知识和记忆"
    )
    
    data object TokenConfig : NavItem(
        title = "Token配置",
        icon = Icons.Default.Key,
        description = "配置API密钥和Token"
    )
    
    // 工具组
    data object Toolbox : NavItem(
        title = "工具箱",
        icon = Icons.Default.Build,
        description = "各种实用工具集合"
    )
    
    data object ShizukuCommands : NavItem(
        title = "系统命令",
        icon = Icons.Default.Terminal,
        description = "执行系统级命令和操作"
    )
    
    // 系统组
    data object Settings : NavItem(
        title = "设置",
        icon = Icons.Default.Settings,
        description = "应用设置和偏好"
    )
    
    data object Help : NavItem(
        title = "帮助",
        icon = Icons.Default.Help,
        description = "使用帮助和文档"
    )
    
    data object About : NavItem(
        title = "关于",
        icon = Icons.Default.Info,
        description = "关于羲和助手"
    )
    
    // 特殊导航项
    data object UserPreferencesGuide : NavItem(
        title = "用户偏好引导",
        icon = Icons.Default.Person,
        description = "设置个人偏好"
    )
    
    data object Agreement : NavItem(
        title = "用户协议",
        icon = Icons.Default.Description,
        description = "用户协议和隐私政策"
    )
}