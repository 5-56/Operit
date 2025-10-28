package com.xihe.assistant.ui.features.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xihe.assistant.R
import com.xihe.assistant.data.model.ChatMessage
import com.xihe.assistant.data.model.MessageType
import com.xihe.assistant.data.preferences.CharacterCardManager
import com.xihe.assistant.ui.features.chat.viewmodel.AttachmentPanelState
import com.xihe.assistant.ui.features.chat.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

/**
 * 羲和智能助手聊天屏幕内容
 * 提供更智能的聊天界面
 */
@Composable
fun ChatScreenContent(
    paddingValues: PaddingValues,
    actualViewModel: ChatViewModel,
    showChatHistorySelector: Boolean,
    chatHistory: List<ChatMessage>,
    enableAiPlanning: Boolean,
    isLoading: Boolean,
    userMessageColor: Color,
    aiMessageColor: Color,
    userTextColor: Color,
    aiTextColor: Color,
    systemMessageColor: Color,
    systemTextColor: Color,
    thinkingBackgroundColor: Color,
    thinkingTextColor: Color,
    hasBackgroundImage: Boolean,
    editingMessageIndex: MutableState<Int?>,
    editingMessageContent: MutableState<String>,
    chatScreenGestureConsumed: Boolean,
    onChatScreenGestureConsumed: (Boolean) -> Unit,
    currentDrag: Float,
    onCurrentDragChange: (Float) -> Unit,
    verticalDrag: Float,
    onVerticalDragChange: (Float) -> Unit,
    dragThreshold: Float,
    scrollState: androidx.compose.foundation.ScrollState,
    showScrollButton: Boolean,
    onShowScrollButtonChange: (Boolean) -> Unit,
    autoScrollToBottom: Boolean,
    onAutoScrollToBottomChange: (Boolean) -> Unit,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    chatHistories: List<com.xihe.assistant.data.model.ChatHistory>,
    currentChatId: String,
    chatHeaderTransparent: Boolean,
    chatHeaderHistoryIconColor: Color?,
    chatHeaderPipIconColor: Color?,
    chatHeaderOverlayMode: Boolean,
    chatStyle: ChatStyle,
    onSwitchCharacter: (String) -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val characterCardManager = remember { CharacterCardManager.getInstance(context) }

    // 聊天头部
    ChatHeader(
        showChatHistorySelector = showChatHistorySelector,
        chatHistories = chatHistories,
        currentChatId = currentChatId,
        transparent = chatHeaderTransparent,
        historyIconColor = chatHeaderHistoryIconColor,
        pipIconColor = chatHeaderPipIconColor,
        overlayMode = chatHeaderOverlayMode,
        onSwitchCharacter = onSwitchCharacter,
        modifier = Modifier.fillMaxWidth()
    )

    // 聊天消息列表
    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(chatHistory) { index, message ->
            ChatMessageItem(
                message = message,
                isUser = message.isUser,
                isAssistant = message.isAssistant,
                isSystem = message.isSystem,
                isThinking = message.isThinking,
                userMessageColor = userMessageColor,
                aiMessageColor = aiMessageColor,
                userTextColor = userTextColor,
                aiTextColor = aiTextColor,
                systemMessageColor = systemMessageColor,
                systemTextColor = systemTextColor,
                thinkingBackgroundColor = thinkingBackgroundColor,
                thinkingTextColor = thinkingTextColor,
                chatStyle = chatStyle,
                onEdit = { editingMessageIndex.value = index },
                onDelete = { /* 实现删除逻辑 */ },
                onCopy = { /* 实现复制逻辑 */ },
                onShare = { /* 实现分享逻辑 */ },
                onReply = { /* 实现回复逻辑 */ }
            )
        }

        // 加载指示器
        if (isLoading) {
            item {
                TypingIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }
    }

    // 滚动到底部按钮
    if (showScrollButton) {
        FloatingActionButton(
            onClick = {
                coroutineScope.launch {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
                onAutoScrollToBottomChange(true)
                onShowScrollButtonChange(false)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = colorScheme.primary,
            contentColor = colorScheme.onPrimary
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "滚动到底部"
            )
        }
    }
}

/**
 * 聊天头部
 */
@Composable
private fun ChatHeader(
    showChatHistorySelector: Boolean,
    chatHistories: List<com.xihe.assistant.data.model.ChatHistory>,
    currentChatId: String,
    transparent: Boolean,
    historyIconColor: Color?,
    pipIconColor: Color?,
    overlayMode: Boolean,
    onSwitchCharacter: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val backgroundColor = if (transparent) {
        colorScheme.surface.copy(alpha = 0.9f)
    } else {
        colorScheme.surface
    }

    Card(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (transparent) 0.dp else 4.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 标题
            Text(
                text = "羲和智能助手",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )

            // 操作按钮
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 历史记录按钮
                IconButton(
                    onClick = { /* 实现历史记录逻辑 */ }
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "历史记录",
                        tint = historyIconColor ?: colorScheme.primary
                    )
                }

                // 悬浮窗按钮
                IconButton(
                    onClick = { /* 实现悬浮窗逻辑 */ }
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureInPicture,
                        contentDescription = "悬浮窗",
                        tint = pipIconColor ?: colorScheme.primary
                    )
                }

                // 更多选项按钮
                IconButton(
                    onClick = { /* 实现更多选项逻辑 */ }
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多选项",
                        tint = colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * 聊天消息项
 */
@Composable
private fun ChatMessageItem(
    message: ChatMessage,
    isUser: Boolean,
    isAssistant: Boolean,
    isSystem: Boolean,
    isThinking: Boolean,
    userMessageColor: Color,
    aiMessageColor: Color,
    userTextColor: Color,
    aiTextColor: Color,
    systemMessageColor: Color,
    systemTextColor: Color,
    thinkingBackgroundColor: Color,
    thinkingTextColor: Color,
    chatStyle: ChatStyle,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onReply: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    when {
        isThinking -> {
            ThinkingMessage(
                message = message,
                backgroundColor = thinkingBackgroundColor,
                textColor = thinkingTextColor,
                modifier = modifier
            )
        }
        isSystem -> {
            SystemMessage(
                message = message,
                backgroundColor = systemMessageColor,
                textColor = systemTextColor,
                modifier = modifier
            )
        }
        isUser -> {
            UserMessage(
                message = message,
                backgroundColor = userMessageColor,
                textColor = userTextColor,
                chatStyle = chatStyle,
                onEdit = onEdit,
                onDelete = onDelete,
                onCopy = onCopy,
                onShare = onShare,
                onReply = onReply,
                modifier = modifier
            )
        }
        isAssistant -> {
            AssistantMessage(
                message = message,
                backgroundColor = aiMessageColor,
                textColor = aiTextColor,
                chatStyle = chatStyle,
                onEdit = onEdit,
                onDelete = onDelete,
                onCopy = onCopy,
                onShare = onShare,
                onReply = onReply,
                modifier = modifier
            )
        }
    }
}

/**
 * 用户消息
 */
@Composable
private fun UserMessage(
    message: ChatMessage,
    backgroundColor: Color,
    textColor: Color,
    chatStyle: ChatStyle,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onReply: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clickable { /* 实现点击逻辑 */ },
            colors = CardDefaults.cardColors(
                containerColor = backgroundColor
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 4.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )

                // 附件显示
                if (message.attachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AttachmentList(
                        attachments = message.attachments,
                        onRemoveAttachment = { /* 实现移除逻辑 */ }
                    )
                }

                // 时间戳
                Text(
                    text = formatTimestamp(message.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/**
 * 助手消息
 */
@Composable
private fun AssistantMessage(
    message: ChatMessage,
    backgroundColor: Color,
    textColor: Color,
    chatStyle: ChatStyle,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onReply: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clickable { /* 实现点击逻辑 */ },
            colors = CardDefaults.cardColors(
                containerColor = backgroundColor
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 4.dp,
                bottomEnd = 16.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )

                // 附件显示
                if (message.attachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AttachmentList(
                        attachments = message.attachments,
                        onRemoveAttachment = { /* 实现移除逻辑 */ }
                    )
                }

                // 时间戳
                Text(
                    text = formatTimestamp(message.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/**
 * 系统消息
 */
@Composable
private fun SystemMessage(
    message: ChatMessage,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = message.content,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        )
    }
}

/**
 * 思考消息
 */
@Composable
private fun ThinkingMessage(
    message: ChatMessage,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = textColor,
                strokeWidth = 2.dp
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = message.content,
                style = MaterialTheme.typography.bodySmall,
                color = textColor
            )
        }
    }
}

/**
 * 输入指示器
 */
@Composable
private fun TypingIndicator(
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = colorScheme.primary,
            strokeWidth = 2.dp
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "羲和正在思考...",
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 附件列表
 */
@Composable
private fun AttachmentList(
    attachments: List<com.xihe.assistant.data.model.AttachmentInfo>,
    onRemoveAttachment: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(attachments.size) { index ->
            val attachment = attachments[index]
            AttachmentChip(
                attachment = attachment,
                onRemove = { onRemoveAttachment(attachment.path) }
            )
        }
    }
}

/**
 * 附件芯片
 */
@Composable
private fun AttachmentChip(
    attachment: com.xihe.assistant.data.model.AttachmentInfo,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (attachment.type) {
                    com.xihe.assistant.data.model.AttachmentType.IMAGE -> Icons.Default.Image
                    com.xihe.assistant.data.model.AttachmentType.AUDIO -> Icons.Default.AudioFile
                    com.xihe.assistant.data.model.AttachmentType.VIDEO -> Icons.Default.VideoFile
                    com.xihe.assistant.data.model.AttachmentType.DOCUMENT -> Icons.Default.Description
                    com.xihe.assistant.data.model.AttachmentType.CODE -> Icons.Default.Code
                    com.xihe.assistant.data.model.AttachmentType.ARCHIVE -> Icons.Default.Archive
                    else -> Icons.Default.AttachFile
                },
                contentDescription = "附件类型",
                tint = colorScheme.onPrimaryContainer,
                modifier = Modifier.size(14.dp)
            )

            Spacer(modifier = Modifier.width(4.dp))

            Text(
                text = attachment.name,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onPrimaryContainer,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(18.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "移除附件",
                    tint = colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

/**
 * 格式化时间戳
 */
private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60000 -> "刚刚"
        diff < 3600000 -> "${diff / 60000}分钟前"
        diff < 86400000 -> "${diff / 3600000}小时前"
        else -> {
            val date = java.util.Date(timestamp)
            val formatter = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
            formatter.format(date)
        }
    }
}

/**
 * 聊天样式
 */
enum class ChatStyle {
    BUBBLE,
    CURSOR
}