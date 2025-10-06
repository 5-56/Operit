package com.xihe.assistant.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.util.*

/**
 * 聊天消息数据模型
 */
@Serializable
@Immutable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val sender: String, // "user", "assistant", "system", "think"
    val timestamp: Long = System.currentTimeMillis(),
    val messageType: MessageType = MessageType.TEXT,
    val attachments: List<AttachmentInfo> = emptyList(),
    val isEdited: Boolean = false,
    val editedAt: Long? = null,
    val replyTo: String? = null, // 回复的消息ID
    val metadata: Map<String, String> = emptyMap()
) {
    val isUser: Boolean get() = sender == "user"
    val isAssistant: Boolean get() = sender == "assistant"
    val isSystem: Boolean get() = sender == "system"
    val isThinking: Boolean get() = sender == "think"
}

@Serializable
enum class MessageType {
    TEXT,
    IMAGE,
    AUDIO,
    VIDEO,
    FILE,
    CODE,
    MARKDOWN,
    THINKING
}

@Serializable
data class AttachmentInfo(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: AttachmentType,
    val size: Long,
    val path: String,
    val mimeType: String? = null,
    val thumbnailPath: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
enum class AttachmentType {
    IMAGE,
    AUDIO,
    VIDEO,
    DOCUMENT,
    CODE,
    ARCHIVE,
    OTHER
}