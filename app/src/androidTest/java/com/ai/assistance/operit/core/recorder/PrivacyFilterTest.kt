package com.ai.assistance.operit.core.recorder

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.core.tools.system.action.ActionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test privacy filtering functionality in ActionRecorder
 */
@RunWith(AndroidJUnit4::class)
class PrivacyFilterTest {

    private lateinit var context: Context
    private lateinit var actionManager: ActionManager
    private lateinit var recorder: ActionRecorder

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        actionManager = ActionManager.getInstance(context)
        recorder = ActionRecorder.getInstance(context)
    }

    @Test
    fun testPasswordParametersFiltered() = runBlocking {
        val config = PrivacyFilterConfig(
            enabled = true,
            filterPasswords = true
        )
        
        recorder.startRecording("Test Session", privacyConfig = config, startActionManager = false)
        
        // Try to record a tool with password parameter
        recorder.recordToolInvocation(
            toolName = "login",
            parameters = mapOf("username" to "user", "password" to "secret123")
        )
        
        delay(100)
        
        val (_, script) = recorder.stopRecording()
        
        // Tool with password should be filtered
        val hasLoginTool = script?.steps?.any { step ->
            step is ScriptStep.ToolInvocationStep && step.toolName == "login"
        } ?: false
        
        assertFalse("Tool with password parameter should be filtered", hasLoginTool)
    }

    @Test
    fun testSecretParametersFiltered() = runBlocking {
        val config = PrivacyFilterConfig(
            enabled = true,
            filterPasswords = true
        )
        
        recorder.startRecording("Test Session", privacyConfig = config, startActionManager = false)
        
        // Try to record a tool with secret parameter
        recorder.recordToolInvocation(
            toolName = "authenticate",
            parameters = mapOf("api_key" to "key123", "secret_token" to "verysecret")
        )
        
        delay(100)
        
        val (_, script) = recorder.stopRecording()
        
        // Tool with secret should be filtered
        val hasAuthTool = script?.steps?.any { step ->
            step is ScriptStep.ToolInvocationStep && step.toolName == "authenticate"
        } ?: false
        
        assertFalse("Tool with secret parameter should be filtered", hasAuthTool)
    }

    @Test
    fun testNonSensitiveToolsNotFiltered() = runBlocking {
        val config = PrivacyFilterConfig(
            enabled = true,
            filterPasswords = true
        )
        
        recorder.startRecording("Test Session", privacyConfig = config, startActionManager = false)
        
        // Record a non-sensitive tool
        recorder.recordToolInvocation(
            toolName = "get_weather",
            parameters = mapOf("city" to "New York", "units" to "metric")
        )
        
        delay(100)
        
        val (_, script) = recorder.stopRecording()
        
        // Non-sensitive tool should NOT be filtered
        val hasWeatherTool = script?.steps?.any { step ->
            step is ScriptStep.ToolInvocationStep && step.toolName == "get_weather"
        } ?: false
        
        assertTrue("Non-sensitive tool should not be filtered", hasWeatherTool)
    }

    @Test
    fun testPrivacyFilterDisabled() = runBlocking {
        val config = PrivacyFilterConfig(
            enabled = false,
            filterPasswords = true
        )
        
        recorder.startRecording("Test Session", privacyConfig = config, startActionManager = false)
        
        // Record a tool with password when filter is disabled
        recorder.recordToolInvocation(
            toolName = "login",
            parameters = mapOf("username" to "user", "password" to "secret123")
        )
        
        delay(100)
        
        val (_, script) = recorder.stopRecording()
        
        // When filter is disabled, tool should be recorded
        val hasLoginTool = script?.steps?.any { step ->
            step is ScriptStep.ToolInvocationStep && step.toolName == "login"
        } ?: false
        
        assertTrue("Tool should be recorded when filter is disabled", hasLoginTool)
    }

    @Test
    fun testSensitiveResourceIdFiltering() {
        val config = PrivacyFilterConfig(
            enabled = true,
            sensitiveResourceIds = setOf("com.example:id/password_field")
        )
        
        recorder.updatePrivacyFilter(config)
        
        assertEquals("Sensitive resource IDs should be configured", 
            setOf("com.example:id/password_field"), 
            recorder.privacyFilterConfig.value.sensitiveResourceIds)
    }

    @Test
    fun testSensitivePackageFiltering() {
        val config = PrivacyFilterConfig(
            enabled = true,
            sensitivePackages = setOf("com.banking.app", "com.wallet.app")
        )
        
        recorder.updatePrivacyFilter(config)
        
        assertEquals("Sensitive packages should be configured", 2, 
            recorder.privacyFilterConfig.value.sensitivePackages.size)
    }

    @Test
    fun testCustomRegexFilters() {
        val config = PrivacyFilterConfig(
            enabled = true,
            filterSensitiveInputs = true,
            customFilters = listOf("\\d{4}-\\d{4}-\\d{4}-\\d{4}", "\\b\\d{3}-\\d{2}-\\d{4}\\b")
        )
        
        recorder.updatePrivacyFilter(config)
        
        assertEquals("Custom regex filters should be configured", 2, 
            recorder.privacyFilterConfig.value.customFilters.size)
    }

    @Test
    fun testUpdatePrivacyFilterDuringRecording() = runBlocking {
        recorder.startRecording("Test Session", startActionManager = false)
        
        // Record a tool before updating filter
        recorder.recordToolInvocation(
            toolName = "tool1",
            parameters = mapOf("param" to "value")
        )
        
        delay(100)
        
        // Update privacy filter during recording
        val newConfig = PrivacyFilterConfig(
            enabled = true,
            filterPasswords = true
        )
        recorder.updatePrivacyFilter(newConfig)
        
        // Try to record a sensitive tool
        recorder.recordToolInvocation(
            toolName = "login",
            parameters = mapOf("password" to "secret")
        )
        
        delay(100)
        
        val (_, script) = recorder.stopRecording()
        
        // First tool should be recorded, sensitive tool should be filtered
        val hasTool1 = script?.steps?.any { step ->
            step is ScriptStep.ToolInvocationStep && step.toolName == "tool1"
        } ?: false
        
        val hasLoginTool = script?.steps?.any { step ->
            step is ScriptStep.ToolInvocationStep && step.toolName == "login"
        } ?: false
        
        assertTrue("First tool should be recorded", hasTool1)
        assertFalse("Sensitive tool should be filtered after config update", hasLoginTool)
    }

    @Test
    fun testComplexPrivacyScenario() = runBlocking {
        val config = PrivacyFilterConfig(
            enabled = true,
            filterPasswords = true,
            filterSensitiveInputs = true,
            sensitiveResourceIds = setOf("com.app:id/password"),
            sensitivePackages = setOf("com.banking.app"),
            customFilters = listOf("\\d{16}") // Credit card numbers
        )
        
        recorder.startRecording("Complex Test", privacyConfig = config, startActionManager = false)
        
        // Record various actions
        recorder.recordToolInvocation("safe_tool", mapOf("data" to "normal"))
        delay(50)
        recorder.recordToolInvocation("login", mapOf("password" to "secret"))
        delay(50)
        recorder.recordToolInvocation("payment", mapOf("card" to "1234567890123456"))
        delay(50)
        recorder.addAnnotation("Test annotation")
        
        delay(100)
        
        val (_, script) = recorder.stopRecording()
        
        assertNotNull("Script should be returned", script)
        
        // Should have safe tool and annotation, but not password or payment tools
        val safeTool = script?.steps?.any { it is ScriptStep.ToolInvocationStep && it.toolName == "safe_tool" } ?: false
        val loginTool = script?.steps?.any { it is ScriptStep.ToolInvocationStep && it.toolName == "login" } ?: false
        val paymentTool = script?.steps?.any { it is ScriptStep.ToolInvocationStep && it.toolName == "payment" } ?: false
        val annotation = script?.steps?.any { it is ScriptStep.AnnotationStep } ?: false
        
        assertTrue("Safe tool should be recorded", safeTool)
        assertFalse("Login tool should be filtered", loginTool)
        assertFalse("Payment tool should be filtered", paymentTool)
        assertTrue("Annotation should be recorded", annotation)
    }
}
