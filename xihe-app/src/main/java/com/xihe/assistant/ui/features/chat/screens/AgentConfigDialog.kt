package com.xihe.assistant.ui.features.chat.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xihe.assistant.core.agent.AgentConfig

@Composable
fun AgentConfigDialog(
    agentConfig: AgentConfig,
    onConfigChanged: (AgentConfig) -> Unit,
    onStartAgent: (AgentConfig) -> Unit,
    onStopAgent: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Agent配置")
        },
        text = {
            Column {
                Text("Agent配置功能开发中...")
                Spacer(modifier = Modifier.height(16.dp))
                Text("将支持以下功能：")
                Text("• 自动理解需求")
                Text("• 生成执行脚本")
                Text("• 多轮优化")
                Text("• 脚本管理")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onStartAgent(agentConfig)
                    onDismiss()
                }
            ) {
                Text("启动Agent")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}