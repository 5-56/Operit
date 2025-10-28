package com.ai.assistance.operit.core.tools

import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptParameterDialogIntegrationTest {

    @Test
    fun `default values are used when initializing dialog parameters`() {
        val tool = PackageTool(
            name = "myTool",
            description = "My test tool",
            parameters = listOf(
                PackageToolParameter(
                    name = "username",
                    description = "User name",
                    type = "string",
                    required = true
                ),
                PackageToolParameter(
                    name = "count",
                    description = "Item count",
                    type = "number",
                    required = false,
                    defaultValue = "10"
                ),
                PackageToolParameter(
                    name = "enabled",
                    description = "Enable feature",
                    type = "boolean",
                    required = false,
                    defaultValue = "true"
                )
            ),
            script = "const test = () => {};"
        )

        val initialParams = tool.parameters.associate {
            it.name to (it.defaultValue ?: "")
        }

        assertEquals(3, initialParams.size)
        assertEquals("", initialParams["username"])
        assertEquals("10", initialParams["count"])
        assertEquals("true", initialParams["enabled"])
    }

    @Test
    fun `prompt text is preferred over name for display`() {
        val tool = PackageTool(
            name = "myTool",
            description = "My test tool",
            parameters = listOf(
                PackageToolParameter(
                    name = "apiKey",
                    description = "The API key for authentication",
                    type = "string",
                    required = true,
                    prompt = "Enter your API Key"
                ),
                PackageToolParameter(
                    name = "timeout",
                    description = "Timeout in seconds",
                    type = "number",
                    required = false,
                    defaultValue = "30"
                )
            ),
            script = "const test = () => {};"
        )

        val apiKeyParam = tool.parameters.first { it.name == "apiKey" }
        val timeoutParam = tool.parameters.first { it.name == "timeout" }

        assertEquals("Enter your API Key", apiKeyParam.prompt)
        assertEquals(null, timeoutParam.prompt)
    }

    @Test
    fun `missing required parameters are detected correctly`() {
        val tool = PackageTool(
            name = "myTool",
            description = "My test tool",
            parameters = listOf(
                PackageToolParameter(
                    name = "required1",
                    description = "First required",
                    type = "string",
                    required = true
                ),
                PackageToolParameter(
                    name = "required2",
                    description = "Second required",
                    type = "string",
                    required = true
                ),
                PackageToolParameter(
                    name = "optional",
                    description = "Optional param",
                    type = "string",
                    required = false,
                    defaultValue = "default"
                )
            ),
            script = "const test = () => {};"
        )

        val paramValues = mapOf(
            "required1" to "value1",
            "required2" to "",
            "optional" to ""
        )

        val missingParams = tool.parameters
            .filter { it.required }
            .map { it.name }
            .filter { paramValues[it].isNullOrEmpty() }

        assertEquals(1, missingParams.size)
        assertTrue(missingParams.contains("required2"))
    }

    @Test
    fun `execution uses default values when parameter not provided`() {
        val packageTool = PackageTool(
            name = "greetUser",
            description = "Greet a user",
            parameters = listOf(
                PackageToolParameter(
                    name = "name",
                    description = "User name",
                    type = "string",
                    required = true
                ),
                PackageToolParameter(
                    name = "greeting",
                    description = "Greeting message",
                    type = "string",
                    required = false,
                    defaultValue = "Hello"
                ),
                PackageToolParameter(
                    name = "enthusiastic",
                    description = "Use enthusiastic tone",
                    type = "boolean",
                    required = false,
                    defaultValue = "false"
                )
            ),
            script = """
                async function greetUser(params) {
                    complete({
                        success: true,
                        message: params.greeting + " " + params.name + (params.enthusiastic === "true" ? "!" : ".")
                    });
                }
            """.trimIndent()
        )

        val tool = AITool(
            name = "TestPackage:greetUser",
            parameters = listOf(
                ToolParameter(name = "name", value = "Alice")
            )
        )

        val resolvedParams = resolvePackageToolParameters(tool, packageTool)

        assertEquals(3, resolvedParams.size)
        val nameParam = resolvedParams.first { it.name == "name" }
        val greetingParam = resolvedParams.first { it.name == "greeting" }
        val enthusiasticParam = resolvedParams.first { it.name == "enthusiastic" }

        assertEquals("Alice", nameParam.value)
        assertEquals("Hello", greetingParam.value)
        assertEquals("false", enthusiasticParam.value)
    }
}
