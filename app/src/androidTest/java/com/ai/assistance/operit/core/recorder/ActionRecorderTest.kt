package com.ai.assistance.operit.core.recorder

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.core.tools.system.action.ActionListener
import com.ai.assistance.operit.core.tools.system.action.ActionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test for ActionRecorder
 */
@RunWith(AndroidJUnit4::class)
class ActionRecorderTest {

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
    fun testRecorderInitialization() {
        assertNotNull("ActionRecorder should be initialized", recorder)
        assertFalse("Recorder should not be recording initially", recorder.isRecording.value)
        assertNull("No session should be active initially", recorder.getCurrentSession())
    }

    @Test
    fun testStartRecording() = runBlocking {
        val result = recorder.startRecording("Test Session", startActionManager = false)
        
        assertTrue("Start recording should succeed", result.success)
        assertTrue("Recorder should be recording", recorder.isRecording.value)
        assertNotNull("Session should be active", recorder.getCurrentSession())
        assertNotNull("Session ID should be returned", result.sessionId)
        
        // Cleanup
        recorder.stopRecording()
    }

    @Test
    fun testStopRecording() = runBlocking {
        // Start recording first
        recorder.startRecording("Test Session", startActionManager = false)
        assertTrue("Recorder should be recording", recorder.isRecording.value)
        
        // Stop recording
        val (result, script) = recorder.stopRecording()
        
        assertTrue("Stop recording should succeed", result.success)
        assertFalse("Recorder should not be recording", recorder.isRecording.value)
        assertNull("No session should be active", recorder.getCurrentSession())
        assertNotNull("Script should be returned", script)
        assertEquals("Script should have correct structure", script?.steps?.size ?: -1, 0)
    }

    @Test
    fun testCannotStartRecordingTwice() = runBlocking {
        // Start recording first time
        val result1 = recorder.startRecording("Test Session 1", startActionManager = false)
        assertTrue("First start should succeed", result1.success)
        
        // Try to start again
        val result2 = recorder.startRecording("Test Session 2", startActionManager = false)
        assertFalse("Second start should fail", result2.success)
        
        // Cleanup
        recorder.stopRecording()
    }

    @Test
    fun testAddAnnotation() = runBlocking {
        // Start recording
        recorder.startRecording("Test Session", startActionManager = false)
        
        // Add annotation
        recorder.addAnnotation("This is a test annotation")
        
        // Give it time to process
        delay(100)
        
        // Stop and check
        val (_, script) = recorder.stopRecording()
        assertNotNull("Script should be returned", script)
        
        val hasAnnotation = script?.steps?.any { step ->
            step is ScriptStep.AnnotationStep && 
            step.annotation == "This is a test annotation"
        } ?: false
        
        assertTrue("Script should contain the annotation", hasAnnotation)
    }

    @Test
    fun testToolInvocationRecording() = runBlocking {
        // Start recording
        recorder.startRecording("Test Session", startActionManager = false)
        
        // Record a tool invocation
        recorder.recordToolInvocation(
            toolName = "test_tool",
            parameters = mapOf("param1" to "value1", "param2" to "value2"),
            category = "TEST"
        )
        
        // Give it time to process
        delay(100)
        
        // Stop and check
        val (_, script) = recorder.stopRecording()
        assertNotNull("Script should be returned", script)
        
        val hasToolInvocation = script?.steps?.any { step ->
            step is ScriptStep.ToolInvocationStep &&
            step.toolName == "test_tool" &&
            step.parameters["param1"] == "value1"
        } ?: false
        
        assertTrue("Script should contain the tool invocation", hasToolInvocation)
    }

    @Test
    fun testPrivacyFilterForPasswords() {
        // Create a privacy filter config
        val config = PrivacyFilterConfig(
            enabled = true,
            filterPasswords = true
        )
        
        recorder.updatePrivacyFilter(config)
        assertEquals("Privacy filter should be updated", config, recorder.privacyFilterConfig.value)
    }

    @Test
    fun testPrivacyFilterForSensitiveResourceIds() {
        val config = PrivacyFilterConfig(
            enabled = true,
            sensitiveResourceIds = setOf("com.example:id/password_field", "com.example:id/secret_input")
        )
        
        recorder.updatePrivacyFilter(config)
        assertEquals("Sensitive resource IDs should be configured", 2, recorder.privacyFilterConfig.value.sensitiveResourceIds.size)
    }

    @Test
    fun testScriptStructure() = runBlocking {
        // Start recording
        recorder.startRecording("Test Session", startActionManager = false)
        
        // Simulate some actions
        recorder.addAnnotation("Step 1")
        delay(50)
        recorder.recordToolInvocation("tool1", mapOf("param" to "value"))
        delay(50)
        recorder.addAnnotation("Step 2")
        
        // Stop recording
        val (_, script) = recorder.stopRecording()
        
        assertNotNull("Script should be returned", script)
        assertNotNull("Script ID should be set", script?.id)
        assertNotNull("Script name should be set", script?.name)
        assertTrue("Script should have steps", (script?.steps?.size ?: 0) > 0)
        assertTrue("Script should have created timestamp", (script?.createdAt ?: 0) > 0)
    }

    @Test
    fun testDelayStepInsertion() = runBlocking {
        // Start recording
        recorder.startRecording("Test Session", startActionManager = false)
        
        // Record actions with delay between them
        recorder.recordToolInvocation("tool1", mapOf())
        delay(200) // Significant delay
        recorder.recordToolInvocation("tool2", mapOf())
        
        // Stop recording
        val (_, script) = recorder.stopRecording()
        
        assertNotNull("Script should be returned", script)
        
        // Check if delay step was inserted
        val hasDelayStep = script?.steps?.any { it is ScriptStep.DelayStep } ?: false
        assertTrue("Script should contain delay step", hasDelayStep)
    }

    @Test
    fun testRecordingEventFlow() = runBlocking {
        var startEventReceived = false
        var stopEventReceived = false
        var stepEventReceived = false
        
        // Collect events (simplified for test)
        val job = kotlinx.coroutines.launch {
            recorder.recordingEvents.collect { event ->
                when (event) {
                    is RecordingEvent.RecordingStarted -> startEventReceived = true
                    is RecordingEvent.RecordingStopped -> stopEventReceived = true
                    is RecordingEvent.StepRecorded -> stepEventReceived = true
                    else -> {}
                }
            }
        }
        
        // Perform recording operations
        recorder.startRecording("Test Session", startActionManager = false)
        delay(50)
        recorder.addAnnotation("Test")
        delay(50)
        recorder.stopRecording()
        delay(100)
        
        job.cancel()
        
        assertTrue("Start event should be received", startEventReceived)
        assertTrue("Step event should be received", stepEventReceived)
        assertTrue("Stop event should be received", stopEventReceived)
    }
}
