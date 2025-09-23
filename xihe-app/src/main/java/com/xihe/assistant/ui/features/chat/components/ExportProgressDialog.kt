package com.xihe.assistant.ui.features.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ExportProgressDialog(
    progress: Float,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* 不允许取消 */ },
        title = {
            Text("导出中...")
        },
        text = {
            Column {
                Text("正在导出应用，请稍候...")
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("${(progress * 100).toInt()}%")
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text("取消")
            }
        }
    )
}