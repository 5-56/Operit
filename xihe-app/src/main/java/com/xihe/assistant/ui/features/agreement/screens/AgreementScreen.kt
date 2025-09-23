package com.xihe.assistant.ui.features.agreement.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AgreementScreen(onAgreementAccepted: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "用户协议",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "欢迎使用羲和助手",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("请仔细阅读以下条款：")
                Text("1. 本应用为AI智能助手，旨在帮助用户完成各种任务")
                Text("2. 用户需要遵守相关法律法规，不得用于违法用途")
                Text("3. 我们重视用户隐私，会保护用户数据安全")
                Text("4. 使用本应用即表示您同意以上条款")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onAgreementAccepted,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("我同意并继续")
        }
    }
}