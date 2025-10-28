package com.ai.assistance.operit.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lightweight data structures for storing script version histories in JSON.
 */
@Serializable
data class ScriptHistory(
    @SerialName("script_id") val scriptId: String,
    @SerialName("name") val name: String,
    @SerialName("file_path") val filePath: String,
    @SerialName("script_type") val scriptType: String,
    @SerialName("description") val description: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("current_version_id") val currentVersionId: String?,
    @SerialName("versions") val versions: List<ScriptVersionRecord>
)

@Serializable
data class ScriptVersionRecord(
    @SerialName("version_id") val versionId: String,
    @SerialName("version_number") val versionNumber: Int,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("commit_message") val commitMessage: String,
    @SerialName("author") val author: String,
    @SerialName("is_auto_save") val isAutoSave: Boolean,
    @SerialName("tags") val tags: List<String>,
    @SerialName("content_hash") val contentHash: String,
    @SerialName("file_size") val fileSize: Int,
    @SerialName("content_path") val contentPath: String
)
