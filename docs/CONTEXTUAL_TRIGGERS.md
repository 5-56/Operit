# Contextual Triggers

## Overview

Contextual Triggers is a feature that allows Operit to automatically execute automations based on notifications or location context. This enables powerful automation scenarios like:

- When a specific app sends a notification, run an automation
- When entering/exiting a geographic area (geofence), execute actions
- When at home, enable WiFi automatically
- When a message arrives from a specific contact, trigger a response

## Architecture

The contextual triggers system consists of several components:

### Data Layer
- **TriggerRule**: Entity stored in Room database containing rule configuration
- **TriggerRuleDao**: Database access object for CRUD operations
- **TriggerRuleRepository**: Repository pattern for managing trigger rules
- **TriggerPreferences**: DataStore-based preferences for user opt-in and feature settings

### Service Layer
- **NotificationMonitor**: NotificationListenerService that monitors incoming notifications
- **LocationContextService**: Foreground service that tracks location and detects geofence entry/exit
- **TriggerManager**: Manages service lifecycle and permissions

### UI Layer
- **TriggerManagementScreen**: Main screen for managing triggers and permissions
- Shows active triggers, opt-in status, permission state
- Allows enabling/disabling notification and geofence triggers

## Privacy & Security

### Explicit Opt-In Required
Users must explicitly opt-in to use contextual triggers. The opt-in dialog explains:
- What data is accessed (notifications, location)
- How it's used (to trigger automations)
- That data never leaves the device
- How to disable the feature

### Permission Management
The system requires specific permissions:
- **Notification Access**: To monitor incoming notifications
- **Location Permission**: To track device location for geofences

Users are guided through granting these permissions within the app.

### Opt-Out
Users can opt-out at any time:
- Disables all triggers immediately
- Stops monitoring services
- Optionally clears all configured rules

## Trigger Types

### Notification Triggers
Monitor notifications from specific apps and match against patterns:
- **Package Name**: Which app's notifications to monitor
- **Title Pattern**: Optional pattern to match notification title
- **Text Pattern**: Optional pattern to match notification text
- **Match Mode**: EXACT, CONTAINS, or REGEX

### Geofence Triggers
Monitor user location and trigger when entering/exiting areas:
- **Location**: Latitude and longitude of geofence center
- **Radius**: Radius in meters
- **Trigger on Enter**: Execute action when entering geofence
- **Trigger on Exit**: Execute action when exiting geofence

## Action Types

### Run Automation
Execute a pre-configured UI automation:
- **Function Name**: Name of automation function to run
- **Package Name**: Optional target app package
- **Parameters**: Optional parameters for the automation

### Execute Tool
Execute an AI tool directly:
- **Tool Name**: Name of tool to execute
- **Parameters**: Parameters to pass to the tool

### Custom Action
Reserved for future extensibility.

## Usage Example

### Example 1: WiFi Auto-Enable at Home
```kotlin
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

triggerRepository.insertRule(rule)
```

### Example 2: Auto-respond to Notifications
```kotlin
val notificationCondition = TriggerCondition.NotificationCondition(
    packageName = "com.whatsapp",
    titlePattern = "John Doe",
    textPattern = null,
    matchMode = MatchMode.CONTAINS
)

val automationAction = AutomationAction(
    functionName = "send_message",
    packageName = "com.whatsapp",
    parameters = mapOf(
        "contact" to "John Doe",
        "message" to "I'll get back to you soon"
    )
)

val rule = TriggerRule(
    name = "Auto-respond to John",
    type = TriggerType.NOTIFICATION,
    enabled = true,
    triggerCondition = notificationCondition,
    actionType = TriggerActionType.RUN_AUTOMATION,
    actionData = json.encodeToString(automationAction)
)

triggerRepository.insertRule(rule)
```

## Testing

The feature includes comprehensive tests:
- `TriggerRuleTest`: Tests database operations and rule management
- Tests cover insertion, retrieval, update, delete operations
- Tests verify proper serialization of trigger conditions and actions

Run tests with:
```bash
./gradlew :app:connectedAndroidTest
```

## Implementation Notes

### Location Service
- Uses Google Play Services Fused Location Provider
- Updates location every 60 seconds (configurable)
- Runs as a foreground service for reliability
- Automatically stops when all geofence triggers are disabled

### Notification Service
- Uses Android's NotificationListenerService
- Requires explicit permission grant from Settings
- Automatically rebinds if disconnected
- Can be disabled without affecting other app functionality

### Performance
- Location updates are throttled to minimize battery impact
- Notification processing is asynchronous
- Database queries use Flow for reactive updates
- Services stop automatically when not needed

## Future Enhancements

Possible future additions:
- Time-based conditions (only trigger during specific hours)
- Combined conditions (AND/OR logic)
- More match modes (wildcard patterns, fuzzy matching)
- Action chains (execute multiple actions in sequence)
- Trigger history and logging
- Import/export trigger configurations
- Smart suggestions based on user behavior
