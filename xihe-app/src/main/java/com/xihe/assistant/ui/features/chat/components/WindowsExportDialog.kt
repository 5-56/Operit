package com.xihe.assistant.ui.features.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun WindowsExportDialog(
    onExport: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var appName by remember { mutableStateOf("MyApp") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("导出Windows应用")
        },
        text = {
            Column {
                OutlinedTextField(
                    value = appName,
                    onValueChange = { appName = it },
                    label = { Text("应用名称") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onExport(appName)
                    onDismiss()
                }
            ) {
                Text("导出")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}