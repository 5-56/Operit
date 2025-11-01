package com.ai.assistance.operit

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TriggerRuleTest {
    
    private lateinit var database: AppDatabase
    private lateinit var context: Context
    private val json = Json { ignoreUnknownKeys = true }
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
    }
    
    @After
    fun tearDown() {
        database.close()
    }
    
    @Test
    fun testInsertAndRetrieveNotificationRule() = runBlocking {
        val triggerDao = database.triggerRuleDao()
        
        val notificationCondition = TriggerCondition.NotificationCondition(
            packageName = "com.example.app",
            titlePattern = "Test",
            textPattern = null,
            matchMode = MatchMode.CONTAINS
        )
        
        val automationAction = AutomationAction(
            functionName = "open_settings",
            packageName = "android",
            parameters = mapOf("setting" to "wifi")
        )
        
        val rule = TriggerRule(
            name = "Test Notification Rule",
            type = TriggerType.NOTIFICATION,
            enabled = true,
            triggerCondition = notificationCondition,
            actionType = TriggerActionType.RUN_AUTOMATION,
            actionData = json.encodeToString(automationAction)
        )
        
        val id = triggerDao.insertRule(rule)
        assertTrue("Rule ID should be greater than 0", id > 0)
        
        val retrievedRule = triggerDao.getRuleById(id)
        assertNotNull("Rule should be retrieved", retrievedRule)
        assertEquals("Rule name should match", rule.name, retrievedRule?.name)
        assertEquals("Rule type should match", rule.type, retrievedRule?.type)
        
        val retrievedCondition = retrievedRule?.triggerCondition as? TriggerCondition.NotificationCondition
        assertNotNull("Condition should be NotificationCondition", retrievedCondition)
        assertEquals("Package name should match", "com.example.app", retrievedCondition?.packageName)
    }
    
    @Test
    fun testInsertAndRetrieveGeofenceRule() = runBlocking {
        val triggerDao = database.triggerRuleDao()
        
        val geofenceCondition = TriggerCondition.GeofenceCondition(
            name = "Home",
            latitude = 37.7749,
            longitude = -122.4194,
            radiusMeters = 100f,
            triggerOnEnter = true,
            triggerOnExit = false
        )
        
        val toolAction = ToolAction(
            toolName = "enable_wifi",
            parameters = emptyMap()
        )
        
        val rule = TriggerRule(
            name = "Home WiFi Auto-Enable",
            type = TriggerType.GEOFENCE,
            enabled = true,
            triggerCondition = geofenceCondition,
            actionType = TriggerActionType.EXECUTE_TOOL,
            actionData = json.encodeToString(toolAction)
        )
        
        val id = triggerDao.insertRule(rule)
        assertTrue("Rule ID should be greater than 0", id > 0)
        
        val retrievedRule = triggerDao.getRuleById(id)
        assertNotNull("Rule should be retrieved", retrievedRule)
        assertEquals("Rule name should match", rule.name, retrievedRule?.name)
        
        val retrievedCondition = retrievedRule?.triggerCondition as? TriggerCondition.GeofenceCondition
        assertNotNull("Condition should be GeofenceCondition", retrievedCondition)
        assertEquals("Geofence name should match", "Home", retrievedCondition?.name)
        assertEquals("Latitude should match", 37.7749, retrievedCondition?.latitude ?: 0.0, 0.0001)
        assertEquals("Radius should match", 100f, retrievedCondition?.radiusMeters ?: 0f, 0.01f)
    }
    
    @Test
    fun testEnableDisableRule() = runBlocking {
        val triggerDao = database.triggerRuleDao()
        
        val condition = TriggerCondition.NotificationCondition(
            packageName = "com.test.app",
            titlePattern = null,
            textPattern = null,
            matchMode = MatchMode.EXACT
        )
        
        val rule = TriggerRule(
            name = "Test Rule",
            type = TriggerType.NOTIFICATION,
            enabled = true,
            triggerCondition = condition,
            actionType = TriggerActionType.CUSTOM_ACTION,
            actionData = "{}"
        )
        
        val id = triggerDao.insertRule(rule)
        
        // Disable the rule
        triggerDao.setRuleEnabled(id, false, System.currentTimeMillis())
        
        val disabledRule = triggerDao.getRuleById(id)
        assertFalse("Rule should be disabled", disabledRule?.enabled ?: true)
        
        // Enable the rule
        triggerDao.setRuleEnabled(id, true, System.currentTimeMillis())
        
        val enabledRule = triggerDao.getRuleById(id)
        assertTrue("Rule should be enabled", enabledRule?.enabled ?: false)
    }
    
    @Test
    fun testGetRulesByType() = runBlocking {
        val triggerDao = database.triggerRuleDao()
        
        // Insert notification rules
        repeat(3) { i ->
            val condition = TriggerCondition.NotificationCondition(
                packageName = "com.test.app$i",
                titlePattern = null,
                textPattern = null,
                matchMode = MatchMode.EXACT
            )
            
            val rule = TriggerRule(
                name = "Notification Rule $i",
                type = TriggerType.NOTIFICATION,
                enabled = true,
                triggerCondition = condition,
                actionType = TriggerActionType.CUSTOM_ACTION,
                actionData = "{}"
            )
            
            triggerDao.insertRule(rule)
        }
        
        // Insert geofence rules
        repeat(2) { i ->
            val condition = TriggerCondition.GeofenceCondition(
                name = "Location $i",
                latitude = 0.0,
                longitude = 0.0,
                radiusMeters = 100f,
                triggerOnEnter = true,
                triggerOnExit = false
            )
            
            val rule = TriggerRule(
                name = "Geofence Rule $i",
                type = TriggerType.GEOFENCE,
                enabled = true,
                triggerCondition = condition,
                actionType = TriggerActionType.CUSTOM_ACTION,
                actionData = "{}"
            )
            
            triggerDao.insertRule(rule)
        }
        
        val notificationRules = triggerDao.getRulesByType(TriggerType.NOTIFICATION).first()
        assertEquals("Should have 3 notification rules", 3, notificationRules.size)
        
        val geofenceRules = triggerDao.getRulesByType(TriggerType.GEOFENCE).first()
        assertEquals("Should have 2 geofence rules", 2, geofenceRules.size)
    }
    
    @Test
    fun testDeleteRule() = runBlocking {
        val triggerDao = database.triggerRuleDao()
        
        val condition = TriggerCondition.NotificationCondition(
            packageName = "com.test.app",
            titlePattern = null,
            textPattern = null,
            matchMode = MatchMode.EXACT
        )
        
        val rule = TriggerRule(
            name = "Test Rule",
            type = TriggerType.NOTIFICATION,
            enabled = true,
            triggerCondition = condition,
            actionType = TriggerActionType.CUSTOM_ACTION,
            actionData = "{}"
        )
        
        val id = triggerDao.insertRule(rule)
        
        // Verify rule exists
        assertNotNull("Rule should exist", triggerDao.getRuleById(id))
        
        // Delete rule
        triggerDao.deleteRuleById(id)
        
        // Verify rule is deleted
        assertNull("Rule should be deleted", triggerDao.getRuleById(id))
    }
}
