package com.xihe.assistant.ui.features.chat.screens

import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import android.view.WindowManager
import androidx.compose.material.icons.filled.MoreVert
import com.xihe.assistant.R
import com.xihe.assistant.data.model.AttachmentInfo
import com.xihe.assistant.data.preferences.ApiPreferences
import com.xihe.assistant.data.preferences.UserPreferencesManager
import com.xihe.assistant.ui.components.ErrorDialog
import com.xihe.assistant.ui.features.chat.components.*
import com.xihe.assistant.ui.features.chat.components.AndroidExportDialog
import com.xihe.assistant.ui.features.chat.components.ExportCompleteDialog
import com.xihe.assistant.ui.features.chat.components.ExportPlatformDialog
import com.xihe.assistant.ui.features.chat.components.ExportProgressDialog
import com.xihe.assistant.ui.features.chat.components.WindowsExportDialog
import com.xihe.assistant.ui.features.chat.components.WorkspaceScreen
import com.xihe.assistant.ui.features.chat.components.exportAndroidApp
import com.xihe.assistant.ui.features.chat.components.exportWindowsApp
import com.xihe.assistant.ui.features.chat.util.ConfigurationStateHolder
import com.xihe.assistant.ui.features.chat.viewmodel.ChatViewModel
import com.xihe.assistant.ui.features.chat.viewmodel.ChatViewModelFactory
import com.xihe.assistant.ui.main.LocalTopBarActions
import com.xihe.assistant.ui.main.screens.GestureStateHolder
import java.io.File
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.PlayArrow
import com.xihe.assistant.ui.features.chat.screens.AgentConfigDialog
import com.xihe.assistant.core.agent.AgentConfig
import com.xihe.assistant.core.agent.AgentScriptSaver
import androidx.compose.material.icons.filled.Stop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatScreen(
    padding: PaddingValues,
    viewModel: ChatViewModel? = null,
    isFloatingMode: Boolean = false,
    onLoading: (Boolean) -> Unit = {},
    onError: (String) -> Unit = {},
    hasBackgroundImage: Boolean = false,
    onNavigateToTokenConfig: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onGestureConsumed: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colorScheme = MaterialTheme.colorScheme

    // Initialize ViewModel without using viewModel() function
    val factory = ChatViewModelFactory(context)
    val actualViewModel = viewModel ?: remember { factory.create(ChatViewModel::class.java) }

    // 设置权限系统的颜色方案
    LaunchedEffect(colorScheme) { actualViewModel.setPermissionSystemColorScheme(colorScheme) }

    // 添加麦克风权限请求启动器
    val requestMicrophonePermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                // This launcher is now used inside the ViewModel's permission check flow
                // It's kept here because it's tied to the composable lifecycle.
                // The actual logic is now triggered from within the ViewModel after the check.
            } else {
                // 权限被拒绝
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.microphone_permission_denied),
                    android.widget.Toast.LENGTH_SHORT
                )
                    .show()
            }
        }

    // Get background image state
    val backgroundImageState by actualViewModel.backgroundImageState.collectAsState()

    // Get user preferences
    val userPreferencesManager = remember { UserPreferencesManager(context) }
    val userPreferences by userPreferencesManager.getUserPreferencesFlow().collectAsState()

    // Get API preferences
    val apiPreferences = remember { ApiPreferences(context) }
    val showFpsCounter by apiPreferences.showFpsCounterFlow.collectAsState(initial = false)

    // Get chat state
    val chatState by actualViewModel.chatState.collectAsState()
    val messages by actualViewModel.messages.collectAsState()
    val isLoading by actualViewModel.isLoading.collectAsState()
    val errorMessage by actualViewModel.errorMessage.collectAsState()

    // Get attachment state
    val attachmentState by actualViewModel.attachmentState.collectAsState()

    // Get agent state
    val agentState by actualViewModel.agentState.collectAsState()

    // Get UI state
    val uiState by actualViewModel.uiState.collectAsState()

    // Get token statistics
    val tokenStats by actualViewModel.tokenStats.collectAsState()

    // Get plan items
    val planItems by actualViewModel.planItems.collectAsState()

    // Get chat history
    val chatHistory by actualViewModel.chatHistory.collectAsState()

    // Get floating window state
    val floatingWindowState by actualViewModel.floatingWindowState.collectAsState()

    // Get configuration state
    val configurationState = remember { ConfigurationStateHolder() }

    // Get focus manager
    val focusManager = LocalFocusManager.current

    // Get view for window manager
    val view = LocalView.current

    // Set up window manager for keyboard handling
    LaunchedEffect(Unit) {
        val windowManager = context.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
        val layoutParams = windowManager.defaultDisplay
        // Additional window manager setup if needed
    }

    // Handle error messages
    LaunchedEffect(errorMessage) {
        if (errorMessage.isNotEmpty()) {
            onError(errorMessage)
            actualViewModel.clearError()
        }
    }

    // Handle loading state
    LaunchedEffect(isLoading) {
        onLoading(isLoading)
    }

    // Handle gesture consumption
    LaunchedEffect(GestureStateHolder.isChatScreenGestureConsumed) {
        onGestureConsumed(GestureStateHolder.isChatScreenGestureConsumed)
    }

    // Set up TopAppBar actions
    val topBarActions = LocalTopBarActions.current
    LaunchedEffect(Unit) {
        topBarActions {
            Row {
                // Agent configuration button
                IconButton(
                    onClick = {
                        actualViewModel.showAgentConfigDialog()
                    }
                ) {
                    Icon(
                        imageVector = if (agentState.isAgentRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (agentState.isAgentRunning) "停止Agent" else "启动Agent"
                    )
                }

                // Settings button
                IconButton(
                    onClick = onNavigateToSettings
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "设置"
                    )
                }
            }
        }
    }

    // Main chat interface
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (hasBackgroundImage && backgroundImageState.backgroundImagePath.isNotEmpty()) {
                    Color.Transparent
                } else {
                    colorScheme.background
                }
            )
    ) {
        // Background image if available
        if (hasBackgroundImage && backgroundImageState.backgroundImagePath.isNotEmpty()) {
            BackgroundImageComponent(
                imagePath = backgroundImageState.backgroundImagePath,
                opacity = backgroundImageState.opacity
            )
        }

        // Main chat content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Messages list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(
                        message = message,
                        onRetry = { actualViewModel.retryMessage(message.id) },
                        onCopy = { actualViewModel.copyMessage(message.content) },
                        onDelete = { actualViewModel.deleteMessage(message.id) }
                    )
                }

                // Loading indicator
                if (isLoading) {
                    item {
                        LoadingIndicator()
                    }
                }
            }

            // Input area
            ChatInputArea(
                onSendMessage = { content ->
                    actualViewModel.sendMessage(content)
                },
                onSendVoiceMessage = { audioPath ->
                    actualViewModel.sendVoiceMessage(audioPath)
                },
                onAttachFile = { filePath ->
                    actualViewModel.attachFile(filePath)
                },
                isLoading = isLoading,
                hasAttachments = attachmentState.attachments.isNotEmpty(),
                onClearAttachments = {
                    actualViewModel.clearAttachments()
                }
            )
        }

        // FPS counter
        if (showFpsCounter) {
            FpsCounter(
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }

        // Agent configuration dialog
        if (agentState.showAgentConfigDialog) {
            AgentConfigDialog(
                agentConfig = agentState.agentConfig,
                onConfigChanged = { config ->
                    actualViewModel.updateAgentConfig(config)
                },
                onStartAgent = { config ->
                    actualViewModel.startAgent(config)
                },
                onStopAgent = {
                    actualViewModel.stopAgent()
                },
                onDismiss = {
                    actualViewModel.hideAgentConfigDialog()
                }
            )
        }

        // Export dialogs
        if (uiState.showExportPlatformDialog) {
            ExportPlatformDialog(
                onAndroidSelected = {
                    actualViewModel.showAndroidExportDialog()
                },
                onWindowsSelected = {
                    actualViewModel.showWindowsExportDialog()
                },
                onDismiss = {
                    actualViewModel.hideExportPlatformDialog()
                }
            )
        }

        if (uiState.showAndroidExportDialog) {
            AndroidExportDialog(
                onExport = { packageName, appName ->
                    actualViewModel.exportAndroidApp(packageName, appName)
                },
                onDismiss = {
                    actualViewModel.hideAndroidExportDialog()
                }
            )
        }

        if (uiState.showWindowsExportDialog) {
            WindowsExportDialog(
                onExport = { appName ->
                    actualViewModel.exportWindowsApp(appName)
                },
                onDismiss = {
                    actualViewModel.hideWindowsExportDialog()
                }
            )
        }

        if (uiState.showExportProgressDialog) {
            ExportProgressDialog(
                progress = uiState.exportProgress,
                onCancel = {
                    actualViewModel.cancelExport()
                }
            )
        }

        if (uiState.showExportCompleteDialog) {
            ExportCompleteDialog(
                exportPath = uiState.exportPath,
                onDismiss = {
                    actualViewModel.hideExportCompleteDialog()
                }
            )
        }

        // Error dialog
        if (errorMessage.isNotEmpty()) {
            ErrorDialog(
                message = errorMessage,
                onDismiss = {
                    actualViewModel.clearError()
                }
            )
        }
    }
}

@Composable
fun MessageBubble(
    message: com.xihe.assistant.data.model.ChatMessage,
    onRetry: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (message.isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyLarge,
                color = if (message.isUser) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            
            if (!message.isUser) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCopy) {
                        Text("复制")
                    }
                    TextButton(onClick = onRetry) {
                        Text("重试")
                    }
                    TextButton(onClick = onDelete) {
                        Text("删除")
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("AI正在思考...")
    }
}

@Composable
fun ChatInputArea(
    onSendMessage: (String) -> Unit,
    onSendVoiceMessage: (String) -> Unit,
    onAttachFile: (String) -> Unit,
    isLoading: Boolean,
    hasAttachments: Boolean,
    onClearAttachments: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Attachment indicator
            if (hasAttachments) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("已添加附件")
                    TextButton(onClick = onClearAttachments) {
                        Text("清除")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Input row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入消息...") },
                    enabled = !isLoading,
                    maxLines = 4
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Send button
                FloatingActionButton(
                    onClick = {
                        if (messageText.isNotEmpty()) {
                            onSendMessage(messageText)
                            messageText = ""
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    enabled = !isLoading && messageText.isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "发送"
                    )
                }
            }
        }
    }
}

@Composable
fun BackgroundImageComponent(
    imagePath: String,
    opacity: Float
) {
    // Background image implementation
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = opacity))
    ) {
        // Image loading would go here
        Text(
            text = "背景图片: $imagePath",
            color = Color.White,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
fun FpsCounter(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
        )
    ) {
        Text(
            text = "FPS: 60",
            modifier = Modifier.padding(8.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}