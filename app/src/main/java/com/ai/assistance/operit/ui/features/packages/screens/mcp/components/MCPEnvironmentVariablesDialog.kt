package com.ai.assistance.operit.ui.features.packages.screens.mcp.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * 环境变量管理对话框
 *
 * 用于添加、编辑和删除MCP插件的环境变量
 *
 * @param environmentVariables 当前的环境变量
 * @param onDismiss 关闭对话框的回调
 * @param onConfirm 提交环境变量的回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCPEnvironmentVariablesDialog(
        environmentVariables: Map<String, String>,
        onDismiss: () -> Unit,
        onConfirm: (Map<String, String>) -> Unit
) {
    // 使用 mutableStateListOf 来管理环境变量列表
    val envVarsList = remember { mutableStateListOf<Pair<String, String>>() }

    // 初始化环境变量列表
    LaunchedEffect(environmentVariables) {
        envVarsList.clear()
        envVarsList.addAll(environmentVariables.toList())
    }

    // 新变量的键和值
    var newKey by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text(text = "管理环境变量", style = MaterialTheme.typography.headlineSmall)

                Spacer(modifier = Modifier.height(16.dp))

                // 环境变量列表
                if (envVarsList.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.weight(1f, false).fillMaxWidth()) {
                        items(envVarsList) { (key, value) ->
                            Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = key, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                            text = value,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { envVarsList.remove(Pair(key, value)) }) {
                                    Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "删除"
                                    )
                                }
                            }
                            if (key != envVarsList.last().first) {
                                Divider(modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 添加新环境变量
                OutlinedTextField(
                        value = newKey,
                        onValueChange = { newKey = it },
                        label = { Text("变量名") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                        value = newValue,
                        onValueChange = { newValue = it },
                        label = { Text("变量值") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                        onClick = {
                            if (newKey.isNotBlank()) {
                                envVarsList.add(Pair(newKey, newValue))
                                newKey = ""
                                newValue = ""
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "添加")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("添加变量")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 按钮行
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                            onClick = { onConfirm(envVarsList.associate { it.first to it.second }) }
                    ) { Text("确认") }
                }
            }
        }
    }
}
