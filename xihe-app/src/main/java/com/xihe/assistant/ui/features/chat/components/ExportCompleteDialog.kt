package com.xihe.assistant.ui.features.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ExportCompleteDialog(
    exportPath: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("导出完成")
        },
        text = {
            Column {
                Text("应用导出成功！")
                Spacer(modifier = Modifier.height(8.dp))
                Text("文件路径：")
                Text(exportPath)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定")
            }
        }
    )
}