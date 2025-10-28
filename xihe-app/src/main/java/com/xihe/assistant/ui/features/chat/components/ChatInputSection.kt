package com.xihe.assistant.ui.features.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xihe.assistant.R
import com.xihe.assistant.data.model.AttachmentInfo
import com.xihe.assistant.data.model.ChatMessage
import com.xihe.assistant.ui.features.chat.viewmodel.AttachmentPanelState
import com.xihe.assistant.ui.features.chat.viewmodel.ChatViewModel
import com.xihe.assistant.ui.features.chat.viewmodel.InputProcessingState

/**
 * 羲和智能助手聊天输入区域
 * 提供更智能的输入体验
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputSection(
    actualViewModel: ChatViewModel,
    userMessage: String,
    onUserMessageChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onCancelMessage: () -> Unit,
    isLoading: Boolean,
    inputState: InputProcessingState,
    allowTextInputWhileProcessing: Boolean = true,
    onAttachmentRequest: (String) -> Unit,
    attachments: List<AttachmentInfo>,
    onRemoveAttachment: (String) -> Unit,
    onInsertAttachment: (AttachmentInfo) -> Unit,
    onAttachScreenContent: () -> Unit,
    onAttachNotifications: () -> Unit,
    onAttachLocation: () -> Unit,
    onTakePhoto: (String) -> Unit,
    hasBackgroundImage: Boolean,
    chatInputTransparent: Boolean,
    externalAttachmentPanelState: AttachmentPanelState,
    onAttachmentPanelStateChange: (AttachmentPanelState) -> Unit,
    showInputProcessingStatus: Boolean,
    replyToMessage: ChatMessage?,
    onClearReply: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val colorScheme = MaterialTheme.colorScheme

    // 输入框背景色
    val inputBackgroundColor = if (hasBackgroundImage && chatInputTransparent) {
        colorScheme.surface.copy(alpha = 0.9f)
    } else {
        colorScheme.surface
    }

    // 输入框边框色
    val inputBorderColor = if (hasBackgroundImage && chatInputTransparent) {
        colorScheme.outline.copy(alpha = 0.5f)
    } else {
        colorScheme.outline
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (hasBackgroundImage && chatInputTransparent) {
                    colorScheme.surface.copy(alpha = 0.95f)
                } else {
                    colorScheme.surface
                }
            )
            .padding(16.dp)
    ) {
        // 回复消息显示
        replyToMessage?.let { message ->
            ReplyMessageCard(
                message = message,
                onClearReply = onClearReply,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }

        // 附件显示
        if (attachments.isNotEmpty()) {
            AttachmentList(
                attachments = attachments,
                onRemoveAttachment = onRemoveAttachment,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }

        // 输入处理状态显示
        if (showInputProcessingStatus && inputState != InputProcessingState.IDLE) {
            InputProcessingStatus(
                state = inputState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }

        // 主输入区域
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .border(
                    width = 1.dp,
                    color = inputBorderColor,
                    shape = RoundedCornerShape(24.dp)
                )
                .background(inputBackgroundColor)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 附件按钮
            IconButton(
                onClick = {
                    val newState = when (externalAttachmentPanelState) {
                        AttachmentPanelState.CLOSED -> AttachmentPanelState.OPEN
                        AttachmentPanelState.OPEN -> AttachmentPanelState.CLOSED
                        AttachmentPanelState.EXPANDED -> AttachmentPanelState.CLOSED
                    }
                    onAttachmentPanelStateChange(newState)
                }
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "附件",
                    tint = colorScheme.primary
                )
            }

            // 输入框
            BasicTextField(
                value = userMessage,
                onValueChange = { if (allowTextInputWhileProcessing || inputState == InputProcessingState.IDLE) onUserMessageChange(it) },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                textStyle = TextStyle(
                    color = colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                ),
                cursorBrush = SolidColor(colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (userMessage.isNotBlank() && !isLoading) {
                            onSendMessage()
                        }
                    }
                ),
                enabled = !isLoading || allowTextInputWhileProcessing,
                singleLine = false,
                maxLines = 5
            )

            // 语音输入按钮
            IconButton(
                onClick = {
                    // 实现语音输入逻辑
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "语音输入",
                    tint = colorScheme.primary
                )
            }

            // 发送按钮
            IconButton(
                onClick = {
                    if (userMessage.isNotBlank() && !isLoading) {
                        onSendMessage()
                    }
                },
                enabled = userMessage.isNotBlank() && !isLoading
            ) {
                Icon(
                    imageVector = if (isLoading) Icons.Default.Stop else Icons.Default.Send,
                    contentDescription = if (isLoading) "停止" else "发送",
                    tint = if (userMessage.isNotBlank() && !isLoading) colorScheme.primary else colorScheme.outline
                )
            }
        }

        // 附件面板
        if (externalAttachmentPanelState != AttachmentPanelState.CLOSED) {
            AttachmentPanel(
                onAttachFile = onAttachmentRequest,
                onAttachScreenContent = onAttachScreenContent,
                onAttachNotifications = onAttachNotifications,
                onAttachLocation = onAttachLocation,
                onTakePhoto = onTakePhoto,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }
    }
}

/**
 * 回复消息卡片
 */
@Composable
private fun ReplyMessageCard(
    message: ChatMessage,
    onClearReply: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Reply,
                contentDescription = "回复",
                tint = colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = message.content.take(100) + if (message.content.length > 100) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            
            IconButton(
                onClick = onClearReply,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "取消回复",
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 附件列表
 */
@Composable
private fun AttachmentList(
    attachments: List<AttachmentInfo>,
    onRemoveAttachment: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
    attachment: AttachmentInfo,
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
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (attachment.type) {
                    AttachmentType.IMAGE -> Icons.Default.Image
                    AttachmentType.AUDIO -> Icons.Default.AudioFile
                    AttachmentType.VIDEO -> Icons.Default.VideoFile
                    AttachmentType.DOCUMENT -> Icons.Default.Description
                    AttachmentType.CODE -> Icons.Default.Code
                    AttachmentType.ARCHIVE -> Icons.Default.Archive
                    else -> Icons.Default.AttachFile
                },
                contentDescription = "附件类型",
                tint = colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp)
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
                modifier = Modifier.size(20.dp)
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
 * 输入处理状态
 */
@Composable
private fun InputProcessingStatus(
    state: InputProcessingState,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val statusText = when (state) {
        InputProcessingState.PROCESSING -> "处理中..."
        InputProcessingState.RECORDING -> "录音中..."
        InputProcessingState.TRANSCRIBING -> "转写中..."
        InputProcessingState.IDLE -> ""
    }
    
    if (statusText.isNotEmpty()) {
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
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 附件面板
 */
@Composable
private fun AttachmentPanel(
    onAttachFile: (String) -> Unit,
    onAttachScreenContent: () -> Unit,
    onAttachNotifications: () -> Unit,
    onAttachLocation: () -> Unit,
    onTakePhoto: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "选择附件类型",
                style = MaterialTheme.typography.titleSmall,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AttachmentOption(
                    icon = Icons.Default.Image,
                    label = "拍照",
                    onClick = { onTakePhoto("camera") }
                )
                
                AttachmentOption(
                    icon = Icons.Default.AttachFile,
                    label = "文件",
                    onClick = { onAttachFile("file") }
                )
                
                AttachmentOption(
                    icon = Icons.Default.Screenshot,
                    label = "屏幕",
                    onClick = onAttachScreenContent
                )
                
                AttachmentOption(
                    icon = Icons.Default.Notifications,
                    label = "通知",
                    onClick = onAttachNotifications
                )
                
                AttachmentOption(
                    icon = Icons.Default.LocationOn,
                    label = "位置",
                    onClick = onAttachLocation
                )
            }
        }
    }
}

/**
 * 附件选项
 */
@Composable
private fun AttachmentOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    
    Column(
        modifier = modifier
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant
        )
    }
}