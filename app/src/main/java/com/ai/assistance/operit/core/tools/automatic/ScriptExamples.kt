package com.ai.assistance.operit.core.tools.automatic

/**
 * Example automation scripts to demonstrate the script execution engine
 */
object ScriptExamples {
    /**
     * Example: Launch WeChat and send a message to a contact
     */
    fun createWeChatMessageScript(): AutomationScript {
        return AutomationScript(
            id = "wechat_send_message",
            name = "Send WeChat Message",
            description = "Launch WeChat app, find a contact, and send them a message",
            packageName = "com.tencent.mm",
            steps = listOf(
                ScriptStep(
                    operation = UIOperation.LaunchApp(
                        packageName = "com.tencent.mm",
                        description = "Launch WeChat"
                    ),
                    stepNumber = 1,
                    description = "Launch WeChat application"
                ),
                ScriptStep(
                    operation = UIOperation.Wait(
                        durationMs = 2000,
                        description = "Wait for app to load"
                    ),
                    stepNumber = 2,
                    description = "Wait for WeChat to fully load"
                ),
                ScriptStep(
                    operation = UIOperation.Click(
                        selector = UISelector.ByResourceId("com.tencent.mm:id/fhc"),
                        description = "Click search button"
                    ),
                    stepNumber = 3,
                    description = "Click the search button"
                ),
                ScriptStep(
                    operation = UIOperation.Input(
                        selector = UISelector.ByResourceId("com.tencent.mm:id/cd7"),
                        textVariableKey = "contact_name",
                        description = "Input contact name"
                    ),
                    stepNumber = 4,
                    description = "Enter contact name in search field",
                    retryCount = 2
                ),
                ScriptStep(
                    operation = UIOperation.Wait(
                        durationMs = 1000,
                        description = "Wait for search results"
                    ),
                    stepNumber = 5,
                    description = "Wait for search results to appear"
                ),
                ScriptStep(
                    operation = UIOperation.Click(
                        selector = UISelector.ByText("{{contact_name}}"),
                        description = "Click on contact"
                    ),
                    stepNumber = 6,
                    description = "Click on the contact from search results"
                ),
                ScriptStep(
                    operation = UIOperation.Input(
                        selector = UISelector.ByResourceId("com.tencent.mm:id/al_"),
                        textVariableKey = "message_text",
                        description = "Input message"
                    ),
                    stepNumber = 7,
                    description = "Enter the message text",
                    retryCount = 2
                ),
                ScriptStep(
                    operation = UIOperation.Click(
                        selector = UISelector.ByText("发送"),
                        description = "Click send button"
                    ),
                    stepNumber = 8,
                    description = "Click the send button"
                )
            ),
            requiredParameters = listOf(
                ScriptParameter(
                    key = "contact_name",
                    description = "Name of the contact to send message to",
                    type = "String",
                    isRequired = true
                ),
                ScriptParameter(
                    key = "message_text",
                    description = "The message content to send",
                    type = "String",
                    isRequired = true
                )
            ),
            tags = listOf("messaging", "wechat", "social")
        )
    }

    /**
     * Example: Simple app navigation script
     */
    fun createSimpleNavigationScript(): AutomationScript {
        return AutomationScript(
            id = "simple_navigation",
            name = "Simple App Navigation",
            description = "Navigate through screens in an app",
            packageName = "com.example.app",
            steps = listOf(
                ScriptStep(
                    operation = UIOperation.LaunchApp(
                        packageName = "com.example.app"
                    ),
                    stepNumber = 1,
                    description = "Launch the application"
                ),
                ScriptStep(
                    operation = UIOperation.Wait(1500),
                    stepNumber = 2,
                    description = "Wait for launch"
                ),
                ScriptStep(
                    operation = UIOperation.Click(
                        selector = UISelector.ByText("Settings"),
                        description = "Open settings"
                    ),
                    stepNumber = 3,
                    description = "Navigate to settings screen"
                ),
                ScriptStep(
                    operation = UIOperation.ValidateElement(
                        selector = UISelector.ByText("Settings"),
                        expectedValueKey = "page_title",
                        validationType = ValidationType.EXISTS,
                        description = "Verify settings screen loaded"
                    ),
                    stepNumber = 4,
                    description = "Verify we are on settings screen"
                )
            ),
            requiredParameters = emptyList(),
            tags = listOf("navigation", "testing")
        )
    }

    /**
     * Example: UI testing script with validation
     */
    fun createUITestScript(): AutomationScript {
        return AutomationScript(
            id = "ui_test_flow",
            name = "UI Test Flow",
            description = "Test a user flow with validation at each step",
            packageName = "com.test.app",
            steps = listOf(
                ScriptStep(
                    operation = UIOperation.LaunchApp("com.test.app"),
                    stepNumber = 1,
                    description = "Launch test app"
                ),
                ScriptStep(
                    operation = UIOperation.Click(
                        selector = UISelector.ByResourceId("com.test.app:id/login_button"),
                        description = "Click login button"
                    ),
                    stepNumber = 2,
                    description = "Click login",
                    continueOnError = false
                ),
                ScriptStep(
                    operation = UIOperation.Input(
                        selector = UISelector.ByResourceId("com.test.app:id/username"),
                        textVariableKey = "username"
                    ),
                    stepNumber = 3,
                    description = "Enter username",
                    retryCount = 1
                ),
                ScriptStep(
                    operation = UIOperation.Input(
                        selector = UISelector.ByResourceId("com.test.app:id/password"),
                        textVariableKey = "password"
                    ),
                    stepNumber = 4,
                    description = "Enter password",
                    retryCount = 1
                ),
                ScriptStep(
                    operation = UIOperation.Click(
                        selector = UISelector.ByText("Submit"),
                        description = "Submit login"
                    ),
                    stepNumber = 5,
                    description = "Submit credentials"
                ),
                ScriptStep(
                    operation = UIOperation.Wait(2000),
                    stepNumber = 6,
                    description = "Wait for login processing"
                ),
                ScriptStep(
                    operation = UIOperation.ValidateElement(
                        selector = UISelector.ByText("Welcome"),
                        expectedValueKey = "success_message",
                        validationType = ValidationType.EXISTS,
                        description = "Verify login success"
                    ),
                    stepNumber = 7,
                    description = "Verify successful login"
                )
            ),
            requiredParameters = listOf(
                ScriptParameter(
                    key = "username",
                    description = "Username for login",
                    isRequired = true
                ),
                ScriptParameter(
                    key = "password",
                    description = "Password for login",
                    isRequired = true
                )
            ),
            tags = listOf("testing", "login", "automation")
        )
    }

    /**
     * Get all example scripts
     */
    fun getAllExamples(): List<AutomationScript> {
        return listOf(
            createWeChatMessageScript(),
            createSimpleNavigationScript(),
            createUITestScript()
        )
    }
}
