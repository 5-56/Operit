# Action Recorder

The Action Recorder is a powerful feature that allows you to record user interactions with the device and tool invocations, then convert them into replayable scripts.

## Features

### Core Functionality
- **Action Recording**: Captures UI gestures (clicks, swipes, text input, etc.) from ActionManager
- **Tool Invocation Recording**: Records AI tool executions with parameters
- **Timing Information**: Automatically tracks delays between actions
- **Annotation Support**: Add notes and comments to your recordings

### Privacy & Security
- **Configurable Privacy Filters**: Filter sensitive data during recording
- **Password Detection**: Automatically exclude password fields
- **Custom Regex Filters**: Define patterns to filter sensitive information
- **Package/Resource ID Filtering**: Exclude specific apps or UI elements

### Script Generation
- **Structured Output**: Generates organized scripts with steps
- **Multiple Step Types**:
  - UI Gesture Steps (clicks, swipes, inputs)
  - Tool Invocation Steps
  - Delay Steps (for timing)
  - Annotation Steps (comments)

## Usage

### Basic Recording

```kotlin
val recorder = ActionRecorder.getInstance(context)

// Start recording
val result = recorder.startRecording("My Recording Session")
if (result.success) {
    // Recording started successfully
}

// Perform actions...
// Actions are automatically captured

// Stop recording
val (stopResult, script) = recorder.stopRecording()
if (stopResult.success && script != null) {
    // Use the generated script
    println("Recorded ${script.steps.size} steps")
}
```

### Adding Annotations

```kotlin
// While recording
recorder.addAnnotation("This is an important step")
```

### Recording Tool Invocations

```kotlin
// Manually record a tool invocation
recorder.recordToolInvocation(
    toolName = "search",
    parameters = mapOf("query" to "hello world"),
    category = "SEARCH"
)
```

### Privacy Configuration

```kotlin
val privacyConfig = PrivacyFilterConfig(
    enabled = true,
    filterPasswords = true,
    filterSensitiveInputs = true,
    sensitiveResourceIds = setOf(
        "com.example:id/password_field",
        "com.example:id/credit_card_input"
    ),
    sensitivePackages = setOf(
        "com.banking.app"
    ),
    customFilters = listOf(
        "\\d{4}-\\d{4}-\\d{4}-\\d{4}", // Credit card pattern
        "\\b\\d{3}-\\d{2}-\\d{4}\\b"   // SSN pattern
    )
)

// Apply before or during recording
recorder.updatePrivacyFilter(privacyConfig)
```

### UI Integration (with RecorderDelegate)

```kotlin
class ChatViewModel(context: Context) : ViewModel() {
    private val recorderDelegate = RecorderDelegate(context, viewModelScope)
    
    // Observe recording state
    val isRecording = recorderDelegate.isRecording
    val stepCount = recorderDelegate.recordingStepCount
    
    // Control recording
    fun startRecording() = recorderDelegate.startRecording()
    fun stopRecording() = recorderDelegate.stopRecording()
    fun toggleRecording() = recorderDelegate.toggleRecording()
    
    // Add annotations
    fun addAnnotation(text: String) = recorderDelegate.addAnnotation(text)
    
    // Configure privacy
    fun updatePrivacyFilter(config: PrivacyFilterConfig) = 
        recorderDelegate.updatePrivacyFilter(config)
}
```

### Compose UI Components

```kotlin
@Composable
fun MyRecorderScreen(viewModel: ChatViewModel) {
    val isRecording by viewModel.isRecording.collectAsState()
    val stepCount by viewModel.stepCount.collectAsState()
    val showAnnotationDialog by viewModel.showAnnotationDialog.collectAsState()
    val showPrivacyDialog by viewModel.showPrivacyFilterDialog.collectAsState()
    
    // Recorder controls
    RecorderControls(
        isRecording = isRecording,
        stepCount = stepCount,
        onStartStop = { viewModel.toggleRecording() },
        onAnnotate = { viewModel.showAnnotationDialog() },
        onPrivacySettings = { viewModel.showPrivacyFilterDialog() }
    )
    
    // Annotation dialog
    if (showAnnotationDialog) {
        AnnotationDialog(
            onDismiss = { viewModel.hideAnnotationDialog() },
            onConfirm = { annotation ->
                viewModel.addAnnotation(annotation)
            }
        )
    }
    
    // Privacy settings dialog
    if (showPrivacyDialog) {
        val currentConfig by viewModel.privacyFilterConfig.collectAsState()
        PrivacyFilterDialog(
            currentConfig = currentConfig,
            onDismiss = { viewModel.hidePrivacyFilterDialog() },
            onConfirm = { config ->
                viewModel.updatePrivacyFilter(config)
            }
        )
    }
}
```

## Script Structure

A recorded script contains:

```kotlin
data class Script(
    val id: String,                    // Unique identifier
    val name: String,                  // Human-readable name
    val description: String,           // Description
    val steps: List<ScriptStep>,       // Recorded steps
    val createdAt: Long,               // Creation timestamp
    val metadata: Map<String, String>  // Additional metadata
)
```

### Step Types

#### UI Gesture Step
```kotlin
ScriptStep.UIGestureStep(
    timestamp = 1234567890L,
    description = "Click on 'Submit' button",
    actionType = ActionType.CLICK,
    coordinates = Coordinates(100, 200),
    elementInfo = ElementInfo(
        resourceId = "com.example:id/submit_button",
        className = "android.widget.Button",
        text = "Submit",
        contentDescription = "Submit form",
        bounds = "[0,0][100,50]",
        packageName = "com.example.app"
    ),
    inputText = null
)
```

#### Tool Invocation Step
```kotlin
ScriptStep.ToolInvocationStep(
    timestamp = 1234567890L,
    description = "Execute search(query=hello)",
    toolName = "search",
    parameters = mapOf("query" to "hello"),
    category = "SEARCH"
)
```

#### Delay Step
```kotlin
ScriptStep.DelayStep(
    timestamp = 1234567890L,
    description = "Wait 500ms",
    delayMs = 500
)
```

#### Annotation Step
```kotlin
ScriptStep.AnnotationStep(
    timestamp = 1234567890L,
    description = "User annotation",
    annotation = "This step is important"
)
```

## Privacy Filters

The recorder implements multiple layers of privacy protection:

### 1. Password Field Detection
Automatically filters any action on UI elements with "password" in the class name.

### 2. Sensitive Parameter Detection
Filters tool invocations with parameters containing:
- "password"
- "secret"
- "token"

### 3. Custom Regex Filters
Apply custom patterns to filter specific text:
```kotlin
customFilters = listOf(
    "\\d{16}",                    // 16-digit numbers (credit cards)
    "\\b\\d{3}-\\d{2}-\\d{4}\\b", // SSN format
    "[A-Z0-9]{20,}"               // Long alphanumeric strings
)
```

### 4. Resource ID Filtering
Exclude specific UI elements by their resource ID:
```kotlin
sensitiveResourceIds = setOf(
    "com.banking:id/account_number",
    "com.wallet:id/pin_input"
)
```

### 5. Package Filtering
Exclude entire applications:
```kotlin
sensitivePackages = setOf(
    "com.banking.secure",
    "com.payment.wallet"
)
```

## Event Flow

The recorder emits events that can be observed:

```kotlin
recorder.recordingEvents.collect { event ->
    when (event) {
        is RecordingEvent.RecordingStarted -> {
            // Recording began
        }
        is RecordingEvent.RecordingStopped -> {
            // Recording stopped, script available
            val script = event.script
        }
        is RecordingEvent.StepRecorded -> {
            // New step was recorded
            val step = event.step
        }
        is RecordingEvent.Error -> {
            // Error occurred
            val message = event.message
        }
    }
}
```

## Testing

Comprehensive test suites are provided:

### ActionRecorderTest
Tests core recording functionality:
- Initialization
- Start/stop recording
- Annotation support
- Tool invocation recording
- Script structure
- Delay step insertion
- Event flow

### PrivacyFilterTest
Tests privacy filtering:
- Password filtering
- Secret parameter filtering
- Non-sensitive data preservation
- Filter enable/disable
- Resource ID filtering
- Package filtering
- Custom regex filters
- Dynamic filter updates

Run tests:
```bash
./gradlew connectedAndroidTest --tests "*.ActionRecorderTest"
./gradlew connectedAndroidTest --tests "*.PrivacyFilterTest"
```

## Architecture

```
ActionRecorder
├── ActionManager (UI event source)
├── RecordingSession (current session state)
├── PrivacyFilterConfig (filtering rules)
└── Script (output)
    └── List<ScriptStep>
        ├── UIGestureStep
        ├── ToolInvocationStep
        ├── DelayStep
        └── AnnotationStep
```

## Best Practices

1. **Always use privacy filters** when recording user interactions
2. **Add annotations** to make scripts more understandable
3. **Test privacy filters** before deploying
4. **Stop recording** before handling sensitive data
5. **Review scripts** before sharing or executing them
6. **Use descriptive session names** for better organization

## Future Enhancements

Potential improvements:
- Script replay functionality
- Script editing capabilities
- Script sharing/export features
- Cloud synchronization
- Script templates
- Machine learning-based sensitive data detection
- Multi-device recording
- Real-time collaboration
