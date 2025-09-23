package com.xihe.assistant.ui.features.toolbox.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun ToolboxScreen(
    navController: NavController,
    onFormatConverterSelected: () -> Unit,
    onFileManagerSelected: () -> Unit,
    onTerminalSelected: () -> Unit,
    onTerminalAutoConfigSelected: () -> Unit,
    onAppPermissionsSelected: () -> Unit,
    onUIDebuggerSelected: () -> Unit,
    onFFmpegToolboxSelected: () -> Unit,
    onShellExecutorSelected: () -> Unit,
    onLogcatSelected: () -> Unit,
    onMarkdownDemoSelected: () -> Unit,
    onTextToSpeechSelected: () -> Unit,
    onSpeechToTextSelected: () -> Unit,
    onToolTesterSelected: () -> Unit,
    onAgreementSelected: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "工具箱",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 文件工具
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "文件工具",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                ToolItem(
                    title = "万能格式转换",
                    subtitle = "支持各种文件格式转换",
                    onClick = onFormatConverterSelected
                )
                
                ToolItem(
                    title = "文件管理器",
                    subtitle = "管理文件和文件夹",
                    onClick = onFileManagerSelected
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 系统工具
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "系统工具",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                ToolItem(
                    title = "命令终端",
                    subtitle = "执行系统命令",
                    onClick = onTerminalSelected
                )
                
                ToolItem(
                    title = "终端自动配置",
                    subtitle = "自动配置终端环境",
                    onClick = onTerminalAutoConfigSelected
                )
                
                ToolItem(
                    title = "应用权限管理",
                    subtitle = "管理应用权限",
                    onClick = onAppPermissionsSelected
                )
                
                ToolItem(
                    title = "UI调试工具",
                    subtitle = "调试UI界面",
                    onClick = onUIDebuggerSelected
                )
                
                ToolItem(
                    title = "命令执行器",
                    subtitle = "执行各种命令",
                    onClick = onShellExecutorSelected
                )
                
                ToolItem(
                    title = "日志查看器",
                    subtitle = "查看系统日志",
                    onClick = onLogcatSelected
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 媒体工具
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "媒体工具",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                ToolItem(
                    title = "FFmpeg工具箱",
                    subtitle = "视频音频处理",
                    onClick = onFFmpegToolboxSelected
                )
                
                ToolItem(
                    title = "文本转语音",
                    subtitle = "将文本转换为语音",
                    onClick = onTextToSpeechSelected
                )
                
                ToolItem(
                    title = "语音识别",
                    subtitle = "将语音转换为文本",
                    onClick = onSpeechToTextSelected
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 其他工具
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "其他工具",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                ToolItem(
                    title = "流式Markdown演示",
                    subtitle = "演示Markdown渲染",
                    onClick = onMarkdownDemoSelected
                )
                
                ToolItem(
                    title = "工具测试中心",
                    subtitle = "测试各种工具",
                    onClick = onToolTesterSelected
                )
            }
        }
    }
}

@Composable
fun ToolItem(
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