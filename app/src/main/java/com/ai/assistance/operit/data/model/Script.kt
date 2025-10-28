package com.ai.assistance.operit.data.model

import io.objectbox.annotation.Backlink
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.relation.ToMany
import io.objectbox.relation.ToOne
import java.util.Date
import java.util.UUID

/**
 * Script entity for storing automation scripts in ObjectBox.
 * Stores metadata, version history, tags, and serialized script steps.
 */
@Entity
data class Script(
    @Id var id: Long = 0,
    var uuid: String = UUID.randomUUID().toString(),
    
    // Core metadata
    var name: String = "",
    var description: String = "",
    var author: String = "",
    
    // Serialized script content (steps in YAML/JSON5 format)
    var serializedContent: String = "",
    var contentFormat: ContentFormat = ContentFormat.JSON5,
    
    // Version tracking
    var currentVersion: String = "1.0.0",
    
    // Timestamps
    var createdAt: Date = Date(),
    var updatedAt: Date = Date(),
    
    // Execution statistics
    var executionCount: Int = 0,
    var lastExecutedAt: Date? = null,
    var lastExecutionStatus: ExecutionStatus = ExecutionStatus.NEVER_RUN
) {
    // Tags for categorization
    lateinit var tags: ToMany<ScriptTag>
    
    // Version history
    @Backlink(to = "script")
    lateinit var versions: ToMany<ScriptVersion>
}

/**
 * Script tag for categorization and organization
 */
@Entity
data class ScriptTag(
    @Id var id: Long = 0,
    var name: String = ""
) {
    @Backlink(to = "tags")
    lateinit var scripts: ToMany<Script>
}

/**
 * Script version for tracking version history
 */
@Entity
data class ScriptVersion(
    @Id var id: Long = 0,
    var versionLabel: String = "1.0.0",
    var serializedContent: String = "",
    var contentFormat: ContentFormat = ContentFormat.JSON5,
    var changeDescription: String = "",
    var createdAt: Date = Date(),
    var author: String = ""
) {
    lateinit var script: ToOne<Script>
}

/**
 * Enum representing the serialization format of the script content
 */
enum class ContentFormat {
    JSON5,
    YAML
}

/**
 * Enum representing the execution status of a script
 */
enum class ExecutionStatus {
    NEVER_RUN,
    SUCCESS,
    FAILED,
    CANCELLED
}
