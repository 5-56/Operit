package com.ai.assistance.operit.core.tools

import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageToolExecutorTest {

    @Test
    fun `resolvePackageToolParameters applies defaults for missing optional parameters`() {
        val packageTool = PackageTool(
            name = "toolA",
            description = "Test tool",
            parameters = listOf(
                PackageToolParameter(
                    name = "requiredParam",
                    description = "required",
                    type = "string",
                    required = true
                ),
                PackageToolParameter(
                    name = "optionalParam",
                    description = "optional",
                    type = "string",
                    required = false,
                    defaultValue = "defaultOptional"
                )
            ),
            script = """
                async function toolA(params) {
                    complete(params);
                }
            """.trimIndent()
        )

        val tool = AITool(
            name = "package:toolA",
            parameters = listOf(
                ToolParameter(name = "requiredParam", value = "value1")
            )
        )

        val resolved = resolvePackageToolParameters(tool, packageTool)

        assertEquals(2, resolved.size)
        assertTrue(resolved.any { it.name == "requiredParam" && it.value == "value1" })
        assertTrue(resolved.any { it.name == "optionalParam" && it.value == "defaultOptional" })
    }

    @Test
    fun `resolvePackageToolParameters does not override non-empty values`() {
        val packageTool = PackageTool(
            name = "toolB",
            description = "Test tool",
            parameters = listOf(
                PackageToolParameter(
                    name = "input",
                    description = "input",
                    type = "string",
                    required = true,
                    defaultValue = "default"
                )
            ),
            script = """
                async function toolB(params) {
                    complete(params);
                }
            """.trimIndent()
        )

        val tool = AITool(
            name = "package:toolB",
            parameters = listOf(
                ToolParameter(name = "input", value = "provided")
            )
        )

        val resolved = resolvePackageToolParameters(tool, packageTool)

        assertEquals(1, resolved.size)
        assertEquals("provided", resolved.first { it.name == "input" }.value)
    }

    @Test
    fun `resolvePackageToolParameters leaves empty result when no defaults`() {
        val packageTool = PackageTool(
            name = "toolC",
            description = "Test tool",
            parameters = listOf(
                PackageToolParameter(
                    name = "optionalParam",
                    description = "optional",
                    type = "string",
                    required = false,
                    defaultValue = null
                )
            ),
            script = "const noop = () => {};"
        )

        val tool = AITool(name = "package:toolC")

        val resolved = resolvePackageToolParameters(tool, packageTool)

        assertTrue(resolved.isEmpty())
    }
}
