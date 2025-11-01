package com.ai.assistance.operit.ui.features.settings.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.TriggerRule
import com.ai.assistance.operit.data.model.TriggerType
import com.ai.assistance.operit.data.model.TriggerCondition
import com.ai.assistance.operit.data.preferences.TriggerPreferences
import com.ai.assistance.operit.data.repository.TriggerRuleRepository
import com.ai.assistance.operit.services.TriggerManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggerManagementScreen(
    onNavigateBack: () -> Unit,
    onAddNotificationTrigger: () -> Unit,
    onAddGeofenceTrigger: () -> Unit,
    onEditTrigger: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val triggerPreferences = remember { TriggerPreferences.getInstance(context) }
    val triggerManager = remember { TriggerManager.getInstance(context) }
    val triggerRepository = remember { TriggerRuleRepository.getInstance(context) }
    
    val userOptedIn by triggerPreferences.userHasOptedIn.collectAsState(initial = false)
    val notificationEnabled by triggerPreferences.notificationTriggersEnabled.collectAsState(initial = false)
    val geofenceEnabled by triggerPreferences.geofenceTriggersEnabled.collectAsState(initial = false)
    val allRules by triggerRepository.getAllRules().collectAsState(initial = emptyList())
    
    var showAddMenu by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (granted) {
            scope.launch {
                triggerPreferences.setLocationAccessGranted(true)
                if (geofenceEnabled) {
                    triggerManager.startLocationContextService()
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contextual Triggers") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showClearAllDialog = true },
                        enabled = userOptedIn
                    ) {
                        Icon(Icons.Default.DeleteSweep, "Clear All")
                    }
                }
            )
        },
        floatingActionButton = {
            if (userOptedIn) {
                Box {
                    FloatingActionButton(onClick = { showAddMenu = true }) {
                        Icon(Icons.Default.Add, "Add Trigger")
                    }
                    DropdownMenu(
                        expanded = showAddMenu,
                        onDismissRequest = { showAddMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Notification Trigger") },
                            leadingIcon = { Icon(Icons.Default.Notifications, null) },
                            onClick = {
                                showAddMenu = false
                                onAddNotificationTrigger()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Geofence Trigger") },
                            leadingIcon = { Icon(Icons.Default.LocationOn, null) },
                            onClick = {
                                showAddMenu = false
                                onAddGeofenceTrigger()
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!userOptedIn) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Privacy Notice",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Contextual triggers allow Operit to automatically run automations based on notifications or your location. This feature requires:",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "• Notification access to monitor specific app notifications\n" +
                                        "• Location access to detect when you enter/exit geofences",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Your data never leaves your device. You can disable this feature at any time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Button(
                                onClick = {
                                    scope.launch {
                                        triggerPreferences.setUserOptIn(true)
                                        showOptInDialog = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Enable Contextual Triggers")
                            }
                        }
                    }
                }
            } else {
                item {
                    Text(
                        text = "Trigger Settings",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                
                item {
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Notification Triggers",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = if (triggerManager.hasNotificationAccess()) 
                                            "Access granted" 
                                        else 
                                            "Requires notification access",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (triggerManager.hasNotificationAccess())
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.error
                                    )
                                }
                                Switch(
                                    checked = notificationEnabled,
                                    onCheckedChange = { enabled ->
                                        scope.launch {
                                            triggerPreferences.setNotificationTriggersEnabled(enabled)
                                            if (enabled) {
                                                if (!triggerManager.hasNotificationAccess()) {
                                                    triggerManager.openNotificationAccessSettings()
                                                } else {
                                                    triggerManager.startNotificationMonitor()
                                                }
                                            } else {
                                                triggerManager.stopNotificationMonitor()
                                            }
                                        }
                                    }
                                )
                            }
                            if (notificationEnabled && !triggerManager.hasNotificationAccess()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { triggerManager.openNotificationAccessSettings() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Settings, null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Grant Notification Access")
                                }
                            }
                        }
                    }
                }
                
                item {
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Geofence Triggers",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = if (triggerManager.hasLocationPermission()) 
                                            "Location access granted" 
                                        else 
                                            "Requires location permission",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (triggerManager.hasLocationPermission())
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.error
                                    )
                                }
                                Switch(
                                    checked = geofenceEnabled,
                                    onCheckedChange = { enabled ->
                                        scope.launch {
                                            triggerPreferences.setGeofenceTriggersEnabled(enabled)
                                            if (enabled) {
                                                if (!triggerManager.hasLocationPermission()) {
                                                    locationPermissionLauncher.launch(
                                                        arrayOf(
                                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                                        )
                                                    )
                                                } else {
                                                    triggerManager.startLocationContextService()
                                                }
                                            } else {
                                                triggerManager.stopLocationContextService()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active Triggers (${allRules.count { it.enabled }})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = onAddNotificationTrigger) {
                                Icon(Icons.Default.Notifications, "Add Notification Trigger")
                            }
                            IconButton(onClick = onAddGeofenceTrigger) {
                                Icon(Icons.Default.LocationOn, "Add Geofence Trigger")
                            }
                        }
                    }
                }
                
                if (allRules.isEmpty()) {
                    item {
                        Card {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No triggers configured yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(allRules, key = { it.id }) { rule ->
                        TriggerRuleItem(
                            rule = rule,
                            onToggle = { enabled ->
                                scope.launch {
                                    triggerRepository.setRuleEnabled(rule.id, enabled)
                                }
                            },
                            onEdit = { onEditTrigger(rule.id) },
                            onDelete = {
                                scope.launch {
                                    triggerRepository.deleteRule(rule)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
    
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear All Triggers?") },
            text = { Text("This will disable all triggers and delete all rules. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            triggerManager.clearAll()
                            showClearAllDialog = false
                        }
                    }
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun TriggerRuleItem(
    rule: TriggerRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = when (rule.type) {
                            TriggerType.NOTIFICATION -> Icons.Default.Notifications
                            TriggerType.GEOFENCE -> Icons.Default.LocationOn
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (val condition = rule.triggerCondition) {
                        is TriggerCondition.NotificationCondition -> 
                            "Package: ${condition.packageName}"
                        is TriggerCondition.GeofenceCondition -> 
                            "${condition.name} (${condition.radiusMeters}m)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onToggle
                )
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Edit")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Delete")
                }
            }
        }
    }
}
