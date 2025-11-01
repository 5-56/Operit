package com.ai.assistance.operit.data.model

import io.objectbox.annotation.Backlink
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.relation.ToMany
import java.util.Date

/**
 * Represents a script in the Script Library
 */
@Entity
data class Script(
    @Id var id: Long = 0,
    var name: String = "",
    var description: String = "",
    var content: String = "",
    var language: String = "javascript", // javascript, python, bash, etc.
    var category: String = "general",
    var isFavorite: Boolean = false,
    var isBuiltIn: Boolean = false,
    var isTrusted: Boolean = false, // Scripts from untrusted sources require warnings
    var source: String = "user", // user, url, file, builtin
    var sourceUrl: String? = null, // If imported from URL
    var author: String? = null,
    var version: String = "1.0",
    var createdAt: Date = Date(),
    var updatedAt: Date = Date(),
    var lastUsedAt: Date? = null,
    var usageCount: Long = 0
) {
    // Tags for scripts
    lateinit var tags: ToMany<ScriptTag>
}

/**
 * Script tag for categorization
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
 * Script category
 */
enum class ScriptCategory(val displayName: String) {
    AUTOMATION("Automation"),
    UTILITY("Utility"),
    DATA_PROCESSING("Data Processing"),
    NETWORK("Network"),
    FILE_OPERATIONS("File Operations"),
    SYSTEM("System"),
    TESTING("Testing"),
    CUSTOM("Custom"),
    GENERAL("General")
}

/**
 * Script execution result
 */
data class ScriptExecutionResult(
    val success: Boolean,
    val output: String = "",
    val error: String? = null,
    val executionTime: Long = 0
)
