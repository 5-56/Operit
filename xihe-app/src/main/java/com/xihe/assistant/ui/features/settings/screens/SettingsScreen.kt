package com.xihe.assistant.ui.features.settings.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    navigateToToolPermissions: () -> Unit,
    onNavigateToUserPreferences: () -> Unit,
    navigateToModelConfig: () -> Unit,
    navigateToThemeSettings: () -> Unit,
    navigateToModelPrompts: () -> Unit,
    navigateToFunctionalPrompts: () -> Unit,
    navigateToFunctionalConfig: () -> Unit,
    navigateToChatHistorySettings: () -> Unit,
    navigateToLanguageSettings: () -> Unit,
    navigateToSpeechServicesSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // AI功能设置
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "AI功能",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                SettingsItem(
                    title = "模型与参数配置",
                    subtitle = "配置AI模型和参数",
                    onClick = navigateToModelConfig
                )
                
                SettingsItem(
                    title = "模型提示词设置",
                    subtitle = "设置模型提示词",
                    onClick = navigateToModelPrompts
                )
                
                SettingsItem(
                    title = "功能模型配置",
                    subtitle = "配置功能模型",
                    onClick = navigateToFunctionalConfig
                )
                
                SettingsItem(
                    title = "功能提示词配置",
                    subtitle = "配置功能提示词",
                    onClick = navigateToFunctionalPrompts
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 用户设置
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "用户设置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                SettingsItem(
                    title = "用户偏好设置",
                    subtitle = "设置个人偏好",
                    onClick = onNavigateToUserPreferences
                )
                
                SettingsItem(
                    title = "主题设置",
                    subtitle = "设置应用主题",
                    onClick = navigateToThemeSettings
                )
                
                SettingsItem(
                    title = "语言设置",
                    subtitle = "设置应用语言",
                    onClick = navigateToLanguageSettings
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 功能设置
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "功能设置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                SettingsItem(
                    title = "工具权限",
                    subtitle = "管理工具权限",
                    onClick = navigateToToolPermissions
                )
                
                SettingsItem(
                    title = "语音服务设置",
                    subtitle = "设置语音服务",
                    onClick = navigateToSpeechServicesSettings
                )
                
                SettingsItem(
                    title = "聊天记录管理",
                    subtitle = "管理聊天记录",
                    onClick = navigateToChatHistorySettings
                )
            }
        }
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}