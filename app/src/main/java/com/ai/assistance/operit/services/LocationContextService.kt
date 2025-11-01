package com.ai.assistance.operit.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.automatic.AutomationTools
import com.ai.assistance.operit.data.dao.TriggerRuleDao
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.AutomationAction
import com.ai.assistance.operit.data.model.ToolAction
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.TriggerActionType
import com.ai.assistance.operit.data.model.TriggerCondition
import com.ai.assistance.operit.data.model.TriggerRule
import com.ai.assistance.operit.data.model.TriggerType
import com.ai.assistance.operit.data.preferences.TriggerPreferences
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class LocationContextService : Service() {
    
    private val TAG = "LocationContextService"
    private val NOTIFICATION_ID = 1002
    private val CHANNEL_ID = "location_context_channel"
    
    private lateinit var triggerRuleDao: TriggerRuleDao
    private lateinit var triggerPreferences: TriggerPreferences
    private lateinit var automationTools: AutomationTools
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val json = Json { ignoreUnknownKeys = true }
    
    private val geofenceStates = mutableMapOf<Long, Boolean>() // Track whether we're inside each geofence
    
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                serviceScope.launch {
                    checkGeofences(location)
                }
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "LocationContextService created")
        
        val database = AppDatabase.getDatabase(this)
        triggerRuleDao = database.triggerRuleDao()
        triggerPreferences = TriggerPreferences.getInstance(this)
        
        val toolHandler = AIToolHandler.getInstance(this)
        automationTools = AutomationTools(this, toolHandler)
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        
        serviceScope.launch {
            if (!triggerPreferences.isGeofenceTriggersEnabled()) {
                Log.d(TAG, "Geofence triggers are disabled, stopping service")
                stopSelf()
                return@launch
            }
            
            if (!hasLocationPermission()) {
                Log.e(TAG, "Location permission not granted, stopping service")
                stopSelf()
                return@launch
            }
            
            startLocationUpdates()
        }
        
        return START_STICKY
    }
    
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            Log.e(TAG, "Cannot start location updates without permission")
            return
        }
        
        try {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                60000L // Update every 60 seconds
            ).apply {
                setMinUpdateIntervalMillis(30000L) // But not more often than every 30 seconds
                setMaxUpdateDelayMillis(120000L)
            }.build()
            
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            
            Log.i(TAG, "Location updates started")
            
            serviceScope.launch {
                triggerPreferences.setLocationAccessGranted(true)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception when requesting location updates", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location updates", e)
        }
    }
    
    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Log.i(TAG, "Location updates stopped")
        
        serviceScope.launch {
            triggerPreferences.setLocationAccessGranted(false)
        }
    }
    
    private suspend fun checkGeofences(location: Location) {
        try {
            // Get enabled geofence rules
            val rules = triggerRuleDao.getEnabledRulesByType(TriggerType.GEOFENCE).first()
            
            if (rules.isEmpty()) {
                return
            }
            
            Log.d(TAG, "Checking ${rules.size} geofence rules at location: ${location.latitude}, ${location.longitude}")
            
            for (rule in rules) {
                if (rule.triggerCondition !is TriggerCondition.GeofenceCondition) {
                    continue
                }
                
                val condition = rule.triggerCondition
                val wasInside = geofenceStates[rule.id] ?: false
                val isInside = isInsideGeofence(location, condition)
                
                // Check if state changed
                if (wasInside != isInside) {
                    geofenceStates[rule.id] = isInside
                    
                    // Trigger based on configuration
                    val shouldTrigger = (isInside && condition.triggerOnEnter) || (!isInside && condition.triggerOnExit)
                    
                    if (shouldTrigger) {
                        val eventType = if (isInside) "entered" else "exited"
                        Log.i(TAG, "Trigger rule '${rule.name}' activated: $eventType geofence '${condition.name}'")
                        executeAction(rule)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking geofences", e)
        }
    }
    
    private fun isInsideGeofence(location: Location, condition: TriggerCondition.GeofenceCondition): Boolean {
        val geofenceLocation = Location("").apply {
            latitude = condition.latitude
            longitude = condition.longitude
        }
        
        val distance = location.distanceTo(geofenceLocation)
        return distance <= condition.radiusMeters
    }
    
    private suspend fun executeAction(rule: TriggerRule) {
        try {
            Log.d(TAG, "Executing action for rule '${rule.name}': ${rule.actionType}")
            
            when (rule.actionType) {
                TriggerActionType.RUN_AUTOMATION -> {
                    val action = json.decodeFromString<AutomationAction>(rule.actionData)
                    executeAutomation(action)
                }
                TriggerActionType.EXECUTE_TOOL -> {
                    val action = json.decodeFromString<ToolAction>(rule.actionData)
                    executeTool(action)
                }
                TriggerActionType.CUSTOM_ACTION -> {
                    // Can be extended for custom actions
                    Log.d(TAG, "Custom action not yet implemented")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing action for rule '${rule.name}'", e)
        }
    }
    
    private suspend fun executeAutomation(action: AutomationAction) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Executing automation: ${action.functionName}")
                
                // Get plan parameters
                val getPlanTool = AITool(
                    name = "get_plan_parameters",
                    parameters = buildList {
                        add(ToolParameter("function_name", action.functionName))
                        if (action.packageName != null) {
                            add(ToolParameter("package_name", action.packageName))
                        }
                    }
                )
                
                val planResult = automationTools.getPlanParameters(getPlanTool)
                if (!planResult.success) {
                    Log.e(TAG, "Failed to get plan parameters: ${planResult.error}")
                    return@withContext
                }
                
                // Execute plan with provided parameters
                val parametersJson = if (action.parameters.isEmpty()) {
                    "{}"
                } else {
                    json.encodeToString(action.parameters as Map<String, String>)
                }
                
                val executeTool = AITool(
                    name = "execute_plan",
                    parameters = listOf(
                        ToolParameter("parameters", parametersJson)
                    )
                )
                
                val executeResult = automationTools.executePlan(executeTool)
                if (executeResult.success) {
                    Log.i(TAG, "Automation executed successfully: ${action.functionName}")
                } else {
                    Log.e(TAG, "Failed to execute automation: ${executeResult.error}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing automation", e)
            }
        }
    }
    
    private suspend fun executeTool(action: ToolAction) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Executing tool: ${action.toolName}")
                
                val tool = AITool(
                    name = action.toolName,
                    parameters = action.parameters.map { (name, value) ->
                        ToolParameter(name, value)
                    }
                )
                
                val toolHandler = AIToolHandler.getInstance(this@LocationContextService)
                val result = toolHandler.executeTool(tool)
                
                if (result.success) {
                    Log.i(TAG, "Tool executed successfully: ${action.toolName}")
                } else {
                    Log.e(TAG, "Failed to execute tool: ${result.error}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing tool", e)
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Location Context Service"
            val descriptionText = "Monitors location for context-based automation triggers"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Location Context Active")
            .setContentText("Monitoring location for automation triggers")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(getPendingIntent())
            .build()
    
    private fun getPendingIntent(): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE
            else 0
        )
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        serviceScope.cancel()
        Log.d(TAG, "LocationContextService destroyed")
    }
}
