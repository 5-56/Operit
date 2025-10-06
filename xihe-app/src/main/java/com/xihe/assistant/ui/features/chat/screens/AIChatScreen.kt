package com.xihe.assistant.ui.features.chat.screens

import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xihe.assistant.ui.components.CustomScaffold
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.tooling.preview.Preview
import com.xihe.assistant.R
import com.xihe.assistant.core.tools.AIToolHandler
import com.xihe.assistant.data.model.AITool
import com.xihe.assistant.data.model.ApiProviderType
import com.xihe.assistant.data.model.AttachmentInfo
import com.xihe.assistant.data.model.ToolParameter
import com.xihe.assistant.data.preferences.ApiPreferences
import com.xihe.assistant.data.preferences.UserPreferencesManager
import com.xihe.assistant.ui.components.ErrorDialog
import com.xihe.assistant.ui.features.chat.components.*
import com.xihe.assistant.ui.features.chat.util.ConfigurationStateHolder
import com.xihe.assistant.ui.features.chat.viewmodel.ChatViewModel
import com.xihe.assistant.ui.features.chat.viewmodel.ChatViewModelFactory
import com.xihe.assistant.ui.main.LocalTopBarActions
import com.xihe.assistant.ui.main.components.LocalAppBarContentColor
import com.xihe.assistant.ui.main.screens.GestureStateHolder
import java.io.File
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.sample
import androidx.compose.runtime.snapshotFlow
import com.xihe.assistant.data.preferences.CharacterCardManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * 羲和智能助手聊天界面
 * 提供更智能、更自动化的AI对话体验
 */
@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview(showBackground = true)
fun AIChatScreen(
    padding: PaddingValues = PaddingValues(),
    viewModel: ChatViewModel? = null,
    isFloatingMode: Boolean = false,
    onLoading: (Boolean) -> Unit = {},
    onError: (String) -> Unit = {},
    hasBackgroundImage: Boolean = false,
    onNavigateToTokenConfig: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToUserPreferences: () -> Unit = {},
    onNavigateToModelConfig: () -> Unit = {},
    onNavigateToModelPrompts: () -> Unit = {},
    onGestureConsumed: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colorScheme = MaterialTheme.colorScheme

    // 初始化ViewModel
    val factory = ChatViewModelFactory(context)
    val actualViewModel = viewModel ?: remember { factory.create(ChatViewModel::class.java) }

    // 设置权限系统的颜色方案
    LaunchedEffect(colorScheme) { actualViewModel.setPermissionSystemColorScheme(colorScheme) }

    // 添加麦克风权限请求启动器
    val requestMicrophonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 权限授予后的处理
        } else {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.microphone_permission_denied),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    // 获取背景图片状态
    val preferencesManager = remember { UserPreferencesManager(context) }
    val useBackgroundImage by preferencesManager.useBackgroundImage.collectAsState(initial = false)
    val backgroundImageUri by preferencesManager.backgroundImageUri.collectAsState(initial = null)
    val chatHeaderTransparent by preferencesManager.chatHeaderTransparent.collectAsState(initial = false)
    val chatInputTransparent by preferencesManager.chatInputTransparent.collectAsState(initial = false)
    val chatHeaderHistoryIconColor by preferencesManager.chatHeaderHistoryIconColor.collectAsState(initial = null)
    val chatHeaderPipIconColor by preferencesManager.chatHeaderPipIconColor.collectAsState(initial = null)
    val chatHeaderOverlayMode by preferencesManager.chatHeaderOverlayMode.collectAsState(initial = false)
    val showInputProcessingStatus by preferencesManager.showInputProcessingStatus.collectAsState(initial = true)
    val hasBackgroundImage = useBackgroundImage && backgroundImageUri != null

    // 收集聊天样式设置
    val chatStyleSetting by preferencesManager.chatStyle.collectAsState(initial = UserPreferencesManager.CHAT_STYLE_CURSOR)
    val chatStyle = remember(chatStyleSetting) {
        when (chatStyleSetting) {
            UserPreferencesManager.CHAT_STYLE_BUBBLE -> ChatStyle.BUBBLE
            else -> ChatStyle.CURSOR
        }
    }

    // 添加编辑按钮和编辑状态
    val editingMessageIndex = remember { mutableStateOf<Int?>(null) }
    val editingMessageContent = remember { mutableStateOf("") }

    // 从ViewModel收集状态
    val apiKey by actualViewModel.apiKey.collectAsState()
    val apiEndpoint by actualViewModel.apiEndpoint.collectAsState()
    val modelName by actualViewModel.modelName.collectAsState()
    val apiProviderType by actualViewModel.apiProviderType.collectAsState()
    val isConfigured by actualViewModel.isConfigured.collectAsState()
    val chatHistory by actualViewModel.chatHistory.collectAsState()
    val userMessage by actualViewModel.userMessage.collectAsState()
    val isLoading by actualViewModel.isLoading.collectAsState()
    val errorMessage by actualViewModel.errorMessage.collectAsState()
    val inputProcessingState by actualViewModel.inputProcessingState.collectAsState()

    val enableAiPlanning by actualViewModel.enableAiPlanning.collectAsState()
    val enableThinkingMode by actualViewModel.enableThinkingMode.collectAsState()
    val enableThinkingGuidance by actualViewModel.enableThinkingGuidance.collectAsState()
    val enableMemoryAttachment by actualViewModel.enableMemoryAttachment.collectAsState()
    val summaryTokenThreshold by actualViewModel.summaryTokenThreshold.collectAsState()
    val isAutoReadEnabled by actualViewModel.isAutoReadEnabled.collectAsState()
    val showChatHistorySelector by actualViewModel.showChatHistorySelector.collectAsState()
    val chatHistories by actualViewModel.chatHistories.collectAsState()
    val currentChatId by actualViewModel.currentChatId.collectAsState()
    val popupMessage by actualViewModel.popupMessage.collectAsState()
    val attachments by actualViewModel.attachments.collectAsState()
    val attachmentPanelState by actualViewModel.attachmentPanelState.collectAsState()
    val scrollToBottomEvent = actualViewModel.scrollToBottomEvent
    val shouldShowConfigDialog by actualViewModel.shouldShowConfigDialog.collectAsState()

    // 添加模型建议对话框状态
    var showModelSuggestionDialog by remember { mutableStateOf(false) }

    // 当模型名称加载后，检查是否为建议更换的模型
    LaunchedEffect(modelName) {
        if (modelName.isNotBlank() && modelName.contains("deepseek-r1-0528-qwen3-8b:free", ignoreCase = true)) {
            showModelSuggestionDialog = true
        }
    }

    // 模型建议对话框
    if (showModelSuggestionDialog) {
        AlertDialog(
            onDismissRequest = { showModelSuggestionDialog = false },
            title = { Text(stringResource(R.string.model_suggestion_title)) },
            text = { Text(stringResource(R.string.model_suggestion_message)) },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        showModelSuggestionDialog = false
                        actualViewModel.updateApiKey(ApiPreferences.DEFAULT_API_KEY)
                        actualViewModel.updateApiEndpoint(ApiPreferences.DEFAULT_API_ENDPOINT)
                        actualViewModel.updateModelName(ApiPreferences.DEFAULT_MODEL_NAME)
                        actualViewModel.updateApiProviderType(ApiProviderType.DEEPSEEK)
                        actualViewModel.saveApiSettings()
                        ConfigurationStateHolder.hasConfirmedDefaultInSession = false
                        actualViewModel.showConfigurationScreen()
                    }) {
                        Text(stringResource(R.string.change_model))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showModelSuggestionDialog = false }) {
                    Text(stringResource(R.string.ignore))
                }
            }
        )
    }

    // 添加WebView刷新相关状态
    val webViewRefreshCounter by actualViewModel.webViewRefreshCounter.collectAsState()

    // 收集回复状态
    val replyToMessage by actualViewModel.replyToMessage.collectAsState()

    // 悬浮窗模式状态
    val isFloatingMode by actualViewModel.isFloatingMode.collectAsState()
    val canDrawOverlays = remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    // UI状态
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val characterCardManager = remember { CharacterCardManager.getInstance(context) }

    // 确保每次应用启动时正确处理配置界面的显示逻辑
    LaunchedEffect(apiKey) {
        if (apiKey.isNotBlank()) {
            if (apiKey != ApiPreferences.DEFAULT_API_KEY) {
                ConfigurationStateHolder.hasConfirmedDefaultInSession = true
            }
        }
    }

    // 现代聊天UI颜色 - 羲和风格
    val backgroundColor = if (hasBackgroundImage) Color.Transparent else MaterialTheme.colorScheme.background
    val userMessageColor = MaterialTheme.colorScheme.primaryContainer
    val aiMessageColor = MaterialTheme.colorScheme.surface
    val userTextColor = MaterialTheme.colorScheme.onPrimaryContainer
    val aiTextColor = MaterialTheme.colorScheme.onSurface
    val systemMessageColor = MaterialTheme.colorScheme.surfaceVariant
    val systemTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val thinkingBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val thinkingTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    // 滚动状态
    var autoScrollToBottom by remember { mutableStateOf(true) }
    val onAutoScrollToBottomChange = remember { { it: Boolean -> autoScrollToBottom = it } }
    var showScrollButton by remember { mutableStateOf(false) }
    val onShowScrollButtonChange = remember { { it: Boolean -> showScrollButton = it } }

    // 核心滚动逻辑
    LaunchedEffect(scrollState) {
        var lastPosition = scrollState.value
        snapshotFlow { scrollState.value }.collect { currentPosition ->
            if (scrollState.isScrollInProgress) {
                val scrolledUp = currentPosition < lastPosition
                if (scrolledUp) {
                    if (autoScrollToBottom) {
                        Log.d("AIChatScreen", "用户向上滚动，禁用自动滚动")
                        autoScrollToBottom = false
                        showScrollButton = true
                    }
                } else {
                    val isNearBottom = scrollState.maxValue - currentPosition < 200
                    if (isNearBottom && !autoScrollToBottom) {
                        Log.d("AIChatScreen", "用户滚动到底部，启用自动滚动")
                        autoScrollToBottom = true
                        showScrollButton = false
                    }
                }
            }
            lastPosition = currentPosition
        }
    }

    // 处理来自ViewModel的滚动事件
    LaunchedEffect(Unit) {
        scrollToBottomEvent.collect {
            if (autoScrollToBottom) {
                try {
                    scrollState.animateScrollTo(scrollState.maxValue)
                } catch (e: Exception) {
                    // 滚动失败处理
                }
            }
        }
    }

    // 自动滚动处理
    LaunchedEffect(chatHistory.size) {
        if (autoScrollToBottom) {
            try {
                scrollState.animateScrollTo(scrollState.maxValue)
            } catch (e: Exception) {
                // 滚动失败处理
            }
        }
    }

    // 当聊天记录变化时，更新悬浮窗内容
    LaunchedEffect(Unit) {
        snapshotFlow { chatHistory }
            .sample(300L)
            .distinctUntilChanged()
            .collect { history ->
                if (actualViewModel.isFloatingMode.value) {
                    val filteredMessages = history.filter { it.sender != "think" }
                    actualViewModel.updateFloatingWindowMessages(filteredMessages)
                }
            }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // 错误弹窗
    errorMessage?.let { message ->
        ErrorDialog(errorMessage = message, onDismiss = { actualViewModel.clearError() })
    }

    // 处理toast事件
    val toastEvent by actualViewModel.toastEvent.collectAsState()

    toastEvent?.let { message ->
        LaunchedEffect(message) {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            actualViewModel.clearToastEvent()
        }
    }

    // 保存聊天记录
    DisposableEffect(Unit) {
        onDispose {
            // 由ViewModel处理
        }
    }

    // 判断是否有默认配置可用
    val hasDefaultConfig = apiKey.isNotBlank()

    // 确定是否显示配置界面
    val showConfig = shouldShowConfigDialog && !ConfigurationStateHolder.hasConfirmedDefaultInSession

    // 添加手势状态
    var chatScreenGestureConsumed by remember { mutableStateOf(false) }
    val onChatScreenGestureConsumedChange = remember {
        { it: Boolean -> chatScreenGestureConsumed = it }
    }

    // 添加累计滑动距离变量
    var currentDrag by remember { mutableStateOf(0f) }
    val onCurrentDragChange = remember { { it: Float -> currentDrag = it } }
    var verticalDrag by remember { mutableStateOf(0f) }
    val onVerticalDragChange = remember { { it: Float -> verticalDrag = it } }
    val dragThreshold = 40f

    // 收集WebView显示状态
    val showWebView by actualViewModel.showWebView.collectAsState()
    // 收集AI电脑显示状态
    val showAiComputer by actualViewModel.showAiComputer.collectAsState()
    val view = LocalView.current

    // 当手势状态改变时，通知父组件
    LaunchedEffect(chatScreenGestureConsumed, showWebView) {
        val finalGestureState = chatScreenGestureConsumed
        GestureStateHolder.isChatScreenGestureConsumed = finalGestureState
        onGestureConsumed(finalGestureState)
    }

    // 处理文件选择器请求
    val fileChooserRequest by actualViewModel.uiStateDelegate.fileChooserRequest.collectAsState()
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        actualViewModel.handleFileChooserResult(result.resultCode, result.data)
        actualViewModel.uiStateDelegate.clearFileChooserRequest()
    }

    // 启动文件选择器
    LaunchedEffect(fileChooserRequest) {
        fileChooserRequest?.let { fileChooserLauncher.launch(it) }
    }

    // 从CompositionLocal获取设置TopBar Actions的函数
    val setTopBarActions = LocalTopBarActions.current
    val appBarContentColor = LocalAppBarContentColor.current

    // 当showWebView或showAiComputer状态改变时，更新TopAppBar的actions
    DisposableEffect(showWebView, showAiComputer, appBarContentColor) {
        setTopBarActions {
            // AI电脑模式切换按钮
            IconButton(
                onClick = {
                    actualViewModel.onAiComputerButtonClick()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = "AI电脑",
                    tint = if (showAiComputer) MaterialTheme.colorScheme.primaryContainer
                    else appBarContentColor
                )
            }
            
            // Web开发模式切换按钮
            IconButton(
                onClick = {
                    actualViewModel.onWorkspaceButtonClick()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = "代码编辑器",
                    tint = if (showWebView) MaterialTheme.colorScheme.primaryContainer
                    else appBarContentColor
                )
            }
        }

        onDispose {
            // 当此Composable离开组合时，不再清空TopAppBar的actions
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CustomScaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!showConfig) {
                    ChatInputSection(
                        actualViewModel = actualViewModel,
                        userMessage = userMessage,
                        onUserMessageChange = { actualViewModel.updateUserMessage(it) },
                        onSendMessage = {
                            actualViewModel.sendUserMessage()
                            actualViewModel.resetAttachmentPanelState()
                        },
                        onCancelMessage = { actualViewModel.cancelCurrentMessage() },
                        isLoading = isLoading,
                        inputState = inputProcessingState,
                        allowTextInputWhileProcessing = true,
                        onAttachmentRequest = { filePath ->
                            actualViewModel.handleAttachment(filePath)
                        },
                        attachments = attachments,
                        onRemoveAttachment = { filePath ->
                            actualViewModel.removeAttachment(filePath)
                        },
                        onInsertAttachment = { attachment: AttachmentInfo ->
                            actualViewModel.insertAttachmentReference(attachment)
                        },
                        onAttachScreenContent = {
                            actualViewModel.captureScreenContent()
                        },
                        onAttachNotifications = {
                            actualViewModel.captureNotifications()
                        },
                        onAttachLocation = {
                            actualViewModel.captureLocation()
                        },
                        onTakePhoto = { uri ->
                            actualViewModel.handleTakenPhoto(uri)
                        },
                        hasBackgroundImage = hasBackgroundImage,
                        chatInputTransparent = chatInputTransparent,
                        externalAttachmentPanelState = attachmentPanelState,
                        onAttachmentPanelStateChange = { newState ->
                            actualViewModel.updateAttachmentPanelState(newState)
                        },
                        showInputProcessingStatus = showInputProcessingStatus,
                        replyToMessage = replyToMessage,
                        onClearReply = { actualViewModel.clearReplyToMessage() }
                    )
                }
            }
        ) { paddingValues ->
            if (showConfig) {
                ConfigurationScreen(
                    apiEndpoint = apiEndpoint,
                    apiKey = apiKey,
                    modelName = modelName,
                    onApiEndpointChange = { actualViewModel.updateApiEndpoint(it) },
                    onApiKeyChange = { actualViewModel.updateApiKey(it) },
                    onModelNameChange = { actualViewModel.updateModelName(it) },
                    onApiProviderTypeChange = { actualViewModel.updateApiProviderType(it) },
                    onSaveConfig = {
                        actualViewModel.saveApiSettings()
                        ConfigurationStateHolder.hasConfirmedDefaultInSession = true
                        actualViewModel.onConfigDialogConfirmed()
                    },
                    onError = { error -> actualViewModel.showErrorMessage(error) },
                    coroutineScope = coroutineScope,
                    onUseDefault = {
                        actualViewModel.useDefaultConfig()
                        ConfigurationStateHolder.hasConfirmedDefaultInSession = true
                        actualViewModel.onConfigDialogConfirmed()
                    },
                    isUsingDefault = true,
                    onNavigateToChat = {
                        actualViewModel.saveApiSettings()
                        ConfigurationStateHolder.hasConfirmedDefaultInSession = true
                        actualViewModel.onConfigDialogConfirmed()
                    },
                    onNavigateToTokenConfig = onNavigateToTokenConfig,
                    onNavigateToSettings = onNavigateToSettings
                )
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    Box(modifier = Modifier.weight(1f)) {
                        ChatScreenContent(
                            paddingValues = PaddingValues(),
                            actualViewModel = actualViewModel,
                            showChatHistorySelector = showChatHistorySelector,
                            chatHistory = chatHistory,
                            enableAiPlanning = enableAiPlanning,
                            isLoading = isLoading,
                            userMessageColor = userMessageColor,
                            aiMessageColor = aiMessageColor,
                            userTextColor = userTextColor,
                            aiTextColor = aiTextColor,
                            systemMessageColor = systemMessageColor,
                            systemTextColor = systemTextColor,
                            thinkingBackgroundColor = thinkingBackgroundColor,
                            thinkingTextColor = thinkingTextColor,
                            hasBackgroundImage = hasBackgroundImage,
                            editingMessageIndex = editingMessageIndex,
                            editingMessageContent = editingMessageContent,
                            chatScreenGestureConsumed = chatScreenGestureConsumed,
                            onChatScreenGestureConsumed = onChatScreenGestureConsumedChange,
                            currentDrag = currentDrag,
                            onCurrentDragChange = onCurrentDragChange,
                            verticalDrag = verticalDrag,
                            onVerticalDragChange = onVerticalDragChange,
                            dragThreshold = dragThreshold,
                            scrollState = scrollState,
                            showScrollButton = showScrollButton,
                            onShowScrollButtonChange = onShowScrollButtonChange,
                            autoScrollToBottom = autoScrollToBottom,
                            onAutoScrollToBottomChange = onAutoScrollToBottomChange,
                            coroutineScope = coroutineScope,
                            chatHistories = chatHistories,
                            currentChatId = currentChatId ?: "",
                            chatHeaderTransparent = chatHeaderTransparent,
                            chatHeaderHistoryIconColor = chatHeaderHistoryIconColor,
                            chatHeaderPipIconColor = chatHeaderPipIconColor,
                            chatHeaderOverlayMode = chatHeaderOverlayMode,
                            chatStyle = chatStyle,
                            onSwitchCharacter = { characterId ->
                                coroutineScope.launch {
                                    characterCardManager.setActiveCharacterCard(characterId)
                                }
                            }
                        )

                        // 设置栏
                        ChatSettingsBar(
                            modifier = Modifier.align(Alignment.BottomEnd),
                            enableAiPlanning = enableAiPlanning,
                            onToggleAiPlanning = { actualViewModel.toggleAiPlanning() },
                            permissionLevel = actualViewModel.masterPermissionLevel.collectAsState().value,
                            onTogglePermission = { actualViewModel.toggleMasterPermission() },
                            enableThinkingMode = enableThinkingMode,
                            onToggleThinkingMode = { actualViewModel.toggleThinkingMode() },
                            enableThinkingGuidance = enableThinkingGuidance,
                            onToggleThinkingGuidance = { actualViewModel.toggleThinkingGuidance() },
                            maxWindowSizeInK = actualViewModel.maxWindowSizeInK.collectAsState().value,
                            onContextLengthChange = { actualViewModel.updateContextLength(it) },
                            enableMemoryAttachment = enableMemoryAttachment,
                            onToggleMemoryAttachment = { actualViewModel.toggleMemoryAttachment() },
                            summaryTokenThreshold = summaryTokenThreshold,
                            onSummaryTokenThresholdChange = { actualViewModel.updateSummaryTokenThreshold(it) },
                            onNavigateToUserPreferences = onNavigateToUserPreferences,
                            onNavigateToModelConfig = onNavigateToModelConfig,
                            onNavigateToModelPrompts = onNavigateToModelPrompts,
                            isAutoReadEnabled = isAutoReadEnabled,
                            onToggleAutoRead = { actualViewModel.toggleAutoRead() },
                            onManualMemoryUpdate = { actualViewModel.manuallyUpdateMemory() }
                        )
                    }
                }
            }
        }

        // Web开发模式作为浮层
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                val currentChat = chatHistories.find { it.id == currentChatId }
                if (currentChat != null) {
                    WorkspaceScreen(
                        actualViewModel = actualViewModel,
                        currentChat = currentChat,
                        isVisible = showWebView,
                        onExportClick = { workDir ->
                            // 导出逻辑
                        }
                    )
                }
            }
        ) { measurables, constraints ->
            if (measurables.isEmpty()) {
                layout(0, 0) {}
            } else {
                val placeable = measurables.first().measure(constraints)
                layout(placeable.width, placeable.height) {
                    if (showWebView) {
                        placeable.placeRelative(0, 0)
                    } else {
                        placeable.placeRelative(-placeable.width, -placeable.height)
                    }
                }
            }
        }

        // AI电脑模式作为浮层
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                ComputerScreen()
            }
        ) { measurables, constraints ->
            if (measurables.isEmpty()) {
                layout(0, 0) {}
            } else {
                val placeable = measurables.first().measure(constraints)
                layout(placeable.width, placeable.height) {
                    if (showAiComputer) {
                        placeable.placeRelative(0, 0)
                    } else {
                        placeable.placeRelative(-placeable.width, -placeable.height)
                    }
                }
            }
        }
    }

    // 显示弹窗消息对话框
    popupMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { actualViewModel.clearPopupMessage() },
            title = { Text("提示") },
            text = { Text(message ?: "") },
            confirmButton = {
                TextButton(onClick = { actualViewModel.clearPopupMessage() }) { Text("确定") }
            }
        )
    }

    // 检查悬浮窗权限
    LaunchedEffect(Unit) {
        canDrawOverlays.value = Settings.canDrawOverlays(context)

        if (isFloatingMode && !canDrawOverlays.value) {
            actualViewModel.toggleFloatingMode()
            android.widget.Toast.makeText(
                context,
                "未获得悬浮窗权限，已关闭悬浮窗模式",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}