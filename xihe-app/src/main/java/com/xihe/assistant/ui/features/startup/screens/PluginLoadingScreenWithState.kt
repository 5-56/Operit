package com.xihe.assistant.ui.features.startup.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PluginLoadingScreenWithState(
    loadingState: PluginLoadingState,
    modifier: Modifier = Modifier
) {
    if (loadingState.isVisible) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.padding(32.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "羲和助手",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("正在加载插件...")
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    CircularProgressIndicator()
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = loadingState.status,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

class PluginLoadingState {
    var isVisible by mutableStateOf(false)
        private set
    
    var status by mutableStateOf("初始化中...")
        private set
    
    private var appContext: android.content.Context? = null
    private var skipCallback: (() -> Unit)? = null
    
    fun setAppContext(context: android.content.Context) {
        this.appContext = context
    }
    
    fun setOnSkipCallback(callback: () -> Unit) {
        this.skipCallback = callback
    }
    
    fun show() {
        isVisible = true
        status = "正在加载插件..."
    }
    
    fun hide() {
        isVisible = false
    }
    
    fun startTimeoutCheck(timeoutMs: Long, scope: CoroutineScope) {
        scope.launch {
            delay(timeoutMs)
            if (isVisible) {
                status = "加载超时，点击跳过"
                skipCallback?.invoke()
            }
        }
    }
    
    fun initializeMCPServer(context: android.content.Context, scope: CoroutineScope) {
        scope.launch {
            status = "初始化MCP服务器..."
            delay(1000)
            
            status = "加载插件..."
            delay(2000)
            
            status = "完成"
            delay(500)
            
            hide()
        }
    }
}