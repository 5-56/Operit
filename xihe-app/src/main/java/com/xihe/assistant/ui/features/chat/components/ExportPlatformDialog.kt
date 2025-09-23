package com.xihe.assistant.ui.features.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ExportPlatformDialog(
    onAndroidSelected: () -> Unit,
    onWindowsSelected: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("选择导出平台")
        },
        text = {
            Column {
                Text("选择要导出的平台：")
                Spacer(modifier = Modifier.height(16.dp))
                Text("• Android APK")
                Text("• Windows 可执行文件")
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onAndroidSelected) {
                    Text("Android")
                }
                TextButton(onClick = onWindowsSelected) {
                    Text("Windows")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}