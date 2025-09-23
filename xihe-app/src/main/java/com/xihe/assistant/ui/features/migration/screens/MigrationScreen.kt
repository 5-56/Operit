package com.xihe.assistant.ui.features.migration.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xihe.assistant.data.migration.ChatHistoryMigrationManager
import kotlinx.coroutines.launch

@Composable
fun MigrationScreen(
    migrationManager: ChatHistoryMigrationManager,
    onComplete: () -> Unit
) {
    var isMigrating by remember { mutableStateOf(false) }
    var migrationProgress by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "数据迁移",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("正在迁移聊天记录数据...")
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LinearProgressIndicator(
            progress = migrationProgress,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text("${(migrationProgress * 100).toInt()}%")
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                scope.launch {
                    isMigrating = true
                    migrationManager.performMigration()
                    onComplete()
                }
            },
            enabled = !isMigrating
        ) {
            Text(if (isMigrating) "迁移中..." else "开始迁移")
        }
    }
}