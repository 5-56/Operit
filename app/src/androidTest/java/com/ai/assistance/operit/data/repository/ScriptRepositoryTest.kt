package com.ai.assistance.operit.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.core.scripts.ParameterType
import com.ai.assistance.operit.core.scripts.ScriptAction
import kotlinx.coroutines.runBlocking
import com.ai.assistance.operit.core.scripts.ScriptDefinition
import com.ai.assistance.operit.core.scripts.ScriptMetadata
import com.ai.assistance.operit.core.scripts.ScriptParameter
import com.ai.assistance.operit.core.scripts.ScriptStep
import com.ai.assistance.operit.data.model.ContentFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ScriptRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: ScriptRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Use a unique profile id for each test run to avoid data conflicts
        val profileId = UUID.randomUUID().toString()
        repository = ScriptRepository(context, profileId)
    }

    @After
    fun tearDown() {
        // Cleanup can be handled by deleting the ObjectBox store if needed
    }

    @Test
    fun testCreateScript() = runBlocking {
        val definition = createTestScriptDefinition()
        val script = repository.importScriptDefinition(definition, ContentFormat.JSON5)

        assertNotNull(script)
        assertEquals(definition.metadata.name, script.name)
        assertEquals(definition.metadata.description, script.description)
        assertEquals(definition.metadata.author, script.author)
        assertEquals(definition.metadata.version, script.currentVersion)

        val versions = repository.getVersions(script.id)
        assertTrue(versions.isNotEmpty())
        assertEquals("Initial import from definition", versions.first().changeDescription)
    }

    @Test
    fun testExportAndImportScript() = runBlocking {
        val definition = createTestScriptDefinition()
        val script = repository.importScriptDefinition(definition, ContentFormat.YAML)

        val tempFile = File(context.cacheDir, "test_script.yaml")
        repository.exportScript(script.id, tempFile, ContentFormat.YAML)

        assertTrue(tempFile.exists())
        val importedScript = repository.importScript(tempFile, ContentFormat.YAML)
        assertNotNull(importedScript)
        assertEquals(script.name, importedScript?.name)
    }

    @Test
    fun testVersionManagement() = runBlocking {
        val definition = createTestScriptDefinition()
        val script = repository.importScriptDefinition(definition, ContentFormat.JSON5)

        val initialVersionCount = repository.getVersions(script.id).size

        repository.createVersion(script, "Test version 2")

        val versions = repository.getVersions(script.id)
        assertEquals(initialVersionCount + 1, versions.size)
        assertEquals("Test version 2", versions.last().changeDescription)
    }

    @Test
    fun testTagManagement() = runBlocking {
        val definition = createTestScriptDefinition()
        val script = repository.importScriptDefinition(definition, ContentFormat.JSON5)

        repository.addTagToScript(script, "newTag")

        val updatedScript = repository.findScriptById(script.id)
        assertNotNull(updatedScript)

        val tagNames = updatedScript?.tags?.map { it.name }
        assertTrue(tagNames?.contains("newTag") == true)

        val scriptsByTag = repository.getScriptsByTag("newTag")
        assertTrue(scriptsByTag.any { it.id == script.id })
    }

    @Test
    fun testSearchScripts() = runBlocking {
        val definition1 = createTestScriptDefinition()
        repository.importScriptDefinition(definition1, ContentFormat.JSON5)

        val definition2 = ScriptDefinition(
            metadata = ScriptMetadata(
                name = "Another Script",
                description = "Another description",
                author = "Another Author",
                version = "2.0.0"
            ),
            steps = emptyList()
        )
        repository.importScriptDefinition(definition2, ContentFormat.JSON5)

        val results = repository.searchScripts("Test")
        assertTrue(results.any { it.name == "Test Script" })
    }

    @Test
    fun testSerializationRoundTrip() = runBlocking {
        val definition = createTestScriptDefinition()
        val script = repository.importScriptDefinition(definition, ContentFormat.JSON5)

        val exportedDefinition = repository.exportScriptDefinition(script.id)
        assertNotNull(exportedDefinition)
        assertEquals(definition.metadata.name, exportedDefinition?.metadata?.name)
        assertEquals(definition.steps.size, exportedDefinition?.steps?.size)
    }

    @Test
    fun testDeleteScript() = runBlocking {
        val definition = createTestScriptDefinition()
        val script = repository.importScriptDefinition(definition, ContentFormat.JSON5)

        val scriptId = script.id
        val success = repository.deleteScript(scriptId)

        assertTrue(success)
        val deletedScript = repository.findScriptById(scriptId)
        assertEquals(null, deletedScript)
    }

    private fun createTestScriptDefinition(): ScriptDefinition {
        val metadata = ScriptMetadata(
            name = "Test Script",
            description = "A test script for unit testing",
            author = "Test Author",
            version = "1.0.0",
            tags = listOf("test", "automation"),
            parameters = listOf(
                ScriptParameter(
                    name = "param1",
                    type = ParameterType.STRING,
                    description = "A test parameter"
                )
            )
        )

        val steps = listOf(
            ScriptStep(
                id = "step1",
                name = "First Step",
                description = "This is the first step",
                action = ScriptAction.LogMessage(
                    message = "Executing first step"
                )
            )
        )

        return ScriptDefinition(
            metadata = metadata,
            steps = steps
        )
    }
}
