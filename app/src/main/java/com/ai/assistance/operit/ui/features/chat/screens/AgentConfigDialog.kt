package com.ai.assistance.operit.ui.features.chat.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.agent.AgentConfig

@Composable
fun AgentConfigDialog(
    initialConfig: AgentConfig,
    onDismiss: () -> Unit,
    onConfirm: (AgentConfig) -> Unit
) {
    var llmProvider by remember { mutableStateOf(initialConfig.llmProvider) }
    var apiKey by remember { mutableStateOf(initialConfig.llmApiKey) }
    var maxIterations by remember { mutableStateOf(initialConfig.maxIterations.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agent 配置") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("大模型:", modifier = Modifier.width(80.dp))
                    DropdownMenu(
                        expanded = false,
                        onDismissRequest = {},
                        modifier = Modifier.width(120.dp)
                    ) {
                        DropdownMenuItem(text = { Text("OpenAI") }, onClick = { llmProvider = "openai" })
                        DropdownMenuItem(text = { Text("Qwen") }, onClick = { llmProvider = "qwen" })
                        DropdownMenuItem(text = { Text("Claude") }, onClick = { llmProvider = "claude" })
                    }
                    TextField(
                        value = llmProvider,
                        onValueChange = { llmProvider = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("模型类型") }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = maxIterations,
                    onValueChange = { maxIterations = it.filter { c -> c.isDigit() } },
                    label = { Text("最大轮数") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(
                    initialConfig.copy(
                        llmProvider = llmProvider,
                        llmApiKey = apiKey,
                        maxIterations = maxIterations.toIntOrNull() ?: 3
                    )
                )
            }) { Text("确定") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("取消") }
        }
    )
}