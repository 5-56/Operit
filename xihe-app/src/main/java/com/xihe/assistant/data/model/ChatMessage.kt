package com.xihe.assistant.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val conversationId: String? = null,
    val attachments: List<AttachmentInfo> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
)

data class AttachmentInfo(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val mimeType: String,
    val timestamp: Long = System.currentTimeMillis()
)