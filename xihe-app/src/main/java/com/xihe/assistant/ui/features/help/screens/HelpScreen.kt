package com.xihe.assistant.ui.features.help.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HelpScreen(onBackPressed: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "使用帮助",
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
                    text = "基本使用",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("1. 在聊天界面输入您的问题或需求")
                Text("2. AI助手会理解并执行相应的操作")
                Text("3. 支持语音输入和文件附件")
                Text("4. 可以使用各种内置工具")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "工具使用",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("• 文件操作：读写、搜索、转换文件")
                Text("• 网络请求：访问网页、下载文件")
                Text("• 系统操作：执行命令、管理应用")
                Text("• UI自动化：点击、滑动、输入")
                Text("• 媒体处理：视频转换、音频处理")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "常见问题",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("Q: 如何配置API密钥？")
                Text("A: 在设置中找到Token配置，输入您的API密钥")
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("Q: 如何启用悬浮窗？")
                Text("A: 在设置中开启悬浮窗权限，然后可以使用悬浮窗功能")
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("Q: 如何添加插件？")
                Text("A: 在插件管理页面可以安装和管理各种插件")
            }
        }
    }
}