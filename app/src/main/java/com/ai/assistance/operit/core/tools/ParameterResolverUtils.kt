package com.ai.assistance.operit.core.tools

import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter

/**
 * Resolves tool parameters by applying default values for missing or empty parameters.
 * Returns a new list with resolved parameters.
 */
internal fun resolvePackageToolParameters(tool: AITool, packageTool: PackageTool): List<ToolParameter> {
    if (packageTool.parameters.isEmpty()) {
        return tool.parameters
    }

    val provided = tool.parameters.associateBy { it.name }
    val resolvedParams = packageTool.parameters.mapNotNull { definition ->
        val providedValue = provided[definition.name]?.value
        when {
            !providedValue.isNullOrEmpty() -> ToolParameter(definition.name, providedValue)
            !definition.defaultValue.isNullOrEmpty() -> ToolParameter(definition.name, definition.defaultValue)
            providedValue != null -> ToolParameter(definition.name, providedValue)
            else -> null
        }
    }

    val additionalParams = tool.parameters.filter { definition ->
        packageTool.parameters.none { it.name == definition.name }
    }

    return resolvedParams + additionalParams
}
