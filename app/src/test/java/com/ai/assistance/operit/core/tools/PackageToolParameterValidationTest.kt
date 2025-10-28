package com.ai.assistance.operit.core.tools

import android.content.Context
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.ui.permissions.ToolCategory
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PackageToolParameterValidationTest {

    private lateinit var mockContext: Context
    private lateinit var mockPackageManager: PackageManager
    private lateinit var toolPackage: ToolPackage
    private lateinit var executor: PackageToolExecutor

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockPackageManager = mockk(relaxed = true)

        toolPackage = ToolPackage(
            name = "TestPackage",
            description = "Test package",
            tools = listOf(
                PackageTool(
                    name = "requiredTool",
                    description = "Tool with required params",
                    parameters = listOf(
                        PackageToolParameter(
                            name = "name",
                            description = "User name",
                            type = "string",
                            required = true
                        ),
                        PackageToolParameter(
                            name = "age",
                            description = "User age",
                            type = "number",
                            required = false,
                            defaultValue = "18"
                        )
                    ),
                    script = "const test = () => {};"
                ),
                PackageTool(
                    name = "optionalTool",
                    description = "Tool with optional params only",
                    parameters = listOf(
                        PackageToolParameter(
                            name = "message",
                            description = "Message",
                            type = "string",
                            required = false,
                            defaultValue = "Hello"
                        )
                    ),
                    script = "const test = () => {};"
                )
            ),
            category = ToolCategory.FILE_READ
        )

        executor = PackageToolExecutor(toolPackage, mockContext, mockPackageManager)
    }

    @Test
    fun `validation succeeds with all required params provided`() {
        val tool = AITool(
            name = "TestPackage:requiredTool",
            parameters = listOf(
                ToolParameter(name = "name", value = "John")
            )
        )

        val result = executor.validateParameters(tool)

        assertTrue(result.valid)
        assertTrue(result.errorMessage.isEmpty())
    }

    @Test
    fun `validation fails when required param is missing and no default`() {
        val tool = AITool(
            name = "TestPackage:requiredTool",
            parameters = emptyList()
        )

        val result = executor.validateParameters(tool)

        assertFalse(result.valid)
        assertTrue(result.errorMessage.contains("name"))
    }

    @Test
    fun `validation succeeds with all optional params having defaults`() {
        val tool = AITool(
            name = "TestPackage:optionalTool",
            parameters = emptyList()
        )

        val result = executor.validateParameters(tool)

        assertTrue(result.valid)
        assertTrue(result.errorMessage.isEmpty())
    }

    @Test
    fun `validation succeeds when required param has default value`() {
        val packageWithDefaults = ToolPackage(
            name = "TestPackage2",
            description = "Test package",
            tools = listOf(
                PackageTool(
                    name = "toolWithDefaults",
                    description = "Tool with defaults",
                    parameters = listOf(
                        PackageToolParameter(
                            name = "requiredWithDefault",
                            description = "Required but has default",
                            type = "string",
                            required = true,
                            defaultValue = "defaultValue"
                        )
                    ),
                    script = "const test = () => {};"
                )
            ),
            category = ToolCategory.FILE_READ
        )

        val executor2 = PackageToolExecutor(packageWithDefaults, mockContext, mockPackageManager)
        val tool = AITool(
            name = "TestPackage2:toolWithDefaults",
            parameters = emptyList()
        )

        val result = executor2.validateParameters(tool)

        assertTrue(result.valid)
    }

    @Test
    fun `validation fails when required param is empty and no default`() {
        val tool = AITool(
            name = "TestPackage:requiredTool",
            parameters = listOf(
                ToolParameter(name = "name", value = "")
            )
        )

        val result = executor.validateParameters(tool)

        assertFalse(result.valid)
        assertTrue(result.errorMessage.contains("name"))
    }
}
