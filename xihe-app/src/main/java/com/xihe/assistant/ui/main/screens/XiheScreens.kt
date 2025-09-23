package com.xihe.assistant.ui.main.screens

import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.xihe.assistant.ui.common.NavItem
import com.xihe.assistant.ui.features.about.screens.AboutScreen
import com.xihe.assistant.ui.features.assistant.screens.AssistantConfigScreen
import com.xihe.assistant.ui.features.chat.screens.AIChatScreen
import com.xihe.assistant.ui.features.demo.screens.ShizukuDemoScreen
import com.xihe.assistant.ui.features.help.screens.HelpScreen
import com.xihe.assistant.ui.features.packages.screens.PackageManagerScreen
import com.xihe.assistant.ui.features.memory.screens.MemoryScreen
import com.xihe.assistant.ui.features.settings.screens.ChatHistorySettingsScreen
import com.xihe.assistant.ui.features.settings.screens.FunctionalConfigScreen
import com.xihe.assistant.ui.features.settings.screens.FunctionalPromptConfigScreen
import com.xihe.assistant.ui.features.settings.screens.LanguageSettingsScreen
import com.xihe.assistant.ui.features.settings.screens.ModelConfigScreen
import com.xihe.assistant.ui.features.settings.screens.ModelPromptsSettingsScreen
import com.xihe.assistant.ui.features.settings.screens.SettingsScreen
import com.xihe.assistant.ui.features.settings.screens.SpeechServicesSettingsScreen
import com.xihe.assistant.ui.features.settings.screens.ThemeSettingsScreen
import com.xihe.assistant.ui.features.settings.screens.ToolPermissionSettingsScreen
import com.xihe.assistant.ui.features.settings.screens.UserPreferencesGuideScreen
import com.xihe.assistant.ui.features.settings.screens.UserPreferencesSettingsScreen
import com.xihe.assistant.ui.features.token.TokenConfigWebViewScreen
import com.xihe.assistant.ui.features.toolbox.screens.AppPermissionsToolScreen
import com.xihe.assistant.ui.features.toolbox.screens.FileManagerToolScreen
import com.xihe.assistant.ui.features.toolbox.screens.FormatConverterToolScreen
import com.xihe.assistant.ui.features.toolbox.screens.LogcatToolScreen
import com.xihe.assistant.ui.features.toolbox.screens.ShellExecutorToolScreen
import com.xihe.assistant.ui.features.toolbox.screens.StreamMarkdownDemoScreen
import com.xihe.assistant.ui.features.toolbox.screens.TerminalAutoConfigToolScreen
import com.xihe.assistant.ui.features.toolbox.screens.TerminalToolScreen
import com.xihe.assistant.ui.features.toolbox.screens.ToolboxScreen
import com.xihe.assistant.ui.features.toolbox.screens.UIDebuggerToolScreen
import com.xihe.assistant.ui.features.toolbox.screens.ffmpegtoolbox.FFmpegToolboxScreen
import com.xihe.assistant.ui.features.toolbox.screens.speechtotext.SpeechToTextToolScreen
import com.xihe.assistant.ui.features.toolbox.screens.texttospeech.TextToSpeechToolScreen
import com.xihe.assistant.ui.features.toolbox.screens.tooltester.ToolTesterScreen
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme

// 路由配置类
typealias ScreenNavigationHandler = (Screen) -> Unit
typealias NavItemChangeHandler = (NavItem) -> Unit

// 重构的Screen类，添加了路由相关属性和内容渲染函数
sealed class Screen(
    // 指定父屏幕，用于返回导航
    open val parentScreen: Screen? = null,
    // 对应的导航项，用于侧边栏高亮显示
    open val navItem: NavItem? = null,
    // 屏幕标题资源ID或字符串
    open val titleRes: String? = null
) {
    // 屏幕内容渲染函数
    @Composable
    open fun Content(
        navController: NavController,
        navigateTo: ScreenNavigationHandler,
        updateNavItem: NavItemChangeHandler,
        onGoBack: () -> Unit,
        hasBackgroundImage: Boolean,
        onLoading: (Boolean) -> Unit,
        onError: (String) -> Unit,
        onGestureConsumed: (Boolean) -> Unit
    ) {
        // 子类实现具体内容
    }

    // Main screens (primary)
    data object AiChat : Screen(navItem = NavItem.AiChat) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            AIChatScreen(
                padding = PaddingValues(0.dp),
                viewModel = null,
                isFloatingMode = false,
                hasBackgroundImage = hasBackgroundImage,
                onNavigateToTokenConfig = { navigateTo(TokenConfig) },
                onNavigateToSettings = {
                    navigateTo(Settings)
                    updateNavItem(NavItem.Settings)
                },
                onLoading = onLoading,
                onError = onError,
                onGestureConsumed = onGestureConsumed
            )
        }
    }

    data object MemoryBase : Screen(navItem = NavItem.MemoryBase, titleRes = "记忆库") {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            MemoryScreen()
        }
    }

    data object Packages : Screen(navItem = NavItem.Packages) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            PackageManagerScreen()
        }
    }

    data object Toolbox : Screen(navItem = NavItem.Toolbox) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            ToolboxScreen(
                navController = navController,
                onFormatConverterSelected = { navigateTo(FormatConverter) },
                onFileManagerSelected = { navigateTo(FileManager) },
                onTerminalSelected = { navigateTo(Terminal) },
                onTerminalAutoConfigSelected = { navigateTo(TerminalAutoConfig) },
                onAppPermissionsSelected = { navigateTo(AppPermissions) },
                onUIDebuggerSelected = { navigateTo(UIDebugger) },
                onFFmpegToolboxSelected = { navigateTo(FFmpegToolbox) },
                onShellExecutorSelected = { navigateTo(ShellExecutor) },
                onLogcatSelected = { navigateTo(Logcat) },
                onMarkdownDemoSelected = { navigateTo(MarkdownDemo) },
                onTextToSpeechSelected = { navigateTo(TextToSpeech) },
                onSpeechToTextSelected = { navigateTo(SpeechToText) },
                onToolTesterSelected = { navigateTo(ToolTester) },
                onAgreementSelected = { navigateTo(Agreement) }
            )
        }
    }

    data object ShizukuCommands : Screen(navItem = NavItem.ShizukuCommands) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            ShizukuDemoScreen()
        }
    }

    data object Settings : Screen(navItem = NavItem.Settings) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            SettingsScreen(
                navigateToToolPermissions = { navigateTo(ToolPermission) },
                onNavigateToUserPreferences = { navigateTo(UserPreferencesSettings) },
                navigateToModelConfig = { navigateTo(ModelConfig) },
                navigateToThemeSettings = { navigateTo(ThemeSettings) },
                navigateToModelPrompts = { navigateTo(ModelPromptsSettings) },
                navigateToFunctionalPrompts = { navigateTo(FunctionalPromptConfig) },
                navigateToFunctionalConfig = { navigateTo(FunctionalConfig) },
                navigateToChatHistorySettings = { navigateTo(ChatHistorySettings) },
                navigateToLanguageSettings = { navigateTo(LanguageSettings) },
                navigateToSpeechServicesSettings = { navigateTo(SpeechServicesSettings) }
            )
        }
    }

    data object Help : Screen(navItem = NavItem.Help) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            HelpScreen(onBackPressed = onGoBack)
        }
    }

    data object About : Screen(navItem = NavItem.About) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            AboutScreen()
        }
    }

    data object Agreement : Screen(navItem = NavItem.Agreement) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            com.xihe.assistant.ui.features.agreement.screens.AgreementScreen(onAgreementAccepted = onGoBack)
        }
    }

    data object AssistantConfig : Screen(navItem = NavItem.AssistantConfig) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            AssistantConfigScreen(
                navigateToModelConfig = { navigateTo(ModelConfig) },
                navigateToModelPrompts = { navigateTo(ModelPromptsSettings) },
                navigateToFunctionalConfig = { navigateTo(FunctionalConfig) },
                navigateToFunctionalPrompts = { navigateTo(FunctionalPromptConfig) },
                navigateToUserPreferences = { navigateTo(UserPreferencesSettings) }
            )
        }
    }

    data object TokenConfig : Screen(parentScreen = AiChat, navItem = NavItem.TokenConfig) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            TokenConfigWebViewScreen(onNavigateBack = onGoBack)
        }
    }

    // Secondary screens - Settings
    data object ToolPermission :
        Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = "工具权限") {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            ToolPermissionSettingsScreen(navigateBack = onGoBack)
        }
    }

    data class UserPreferencesGuide(var profileName: String = "", var profileId: String = "") :
        Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = "用户偏好引导") {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            UserPreferencesGuideScreen(
                profileName = profileName,
                profileId = profileId,
                onComplete = onGoBack,
                navigateToPermissions = {
                    navigateTo(ShizukuCommands)
                    updateNavItem(NavItem.ShizukuCommands)
                }
            )
        }
    }

    data object UserPreferencesSettings :
        Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = "用户偏好设置") {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            UserPreferencesSettingsScreen(
                onNavigateBack = onGoBack,
                onNavigateToGuide = { profileId, category ->
                    navigateTo(UserPreferencesGuide(profileId, category))
                }
            )
        }
    }

    data object ModelConfig :
        Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = "模型与参数配置") {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            ModelConfigScreen(onBackPressed = onGoBack)
        }
    }

    data object SpeechServicesSettings :
        Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = "语音服务设置") {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            SpeechServicesSettingsScreen(onBackPressed = onGoBack)
        }
    }

    data object FunctionalPromptConfig :
        Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = "功能提示词配置") {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            FunctionalPromptConfigScreen(
                onBackPressed = onGoBack,
                onNavigateToModelPrompts = { navigateTo(ModelPromptsSettings) }
            )
        }
    }

    data object ModelPromptsSettings :
        Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = "模型提示词设置") {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            ModelPromptsSettingsScreen(
                onBackPressed = onGoBack,
                onNavigateToFunctionalPrompts = { navigateTo(FunctionalPromptConfig) }
            )
        }
    }

    data object FunctionalConfig :
        Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = "功能模型配置") {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            FunctionalConfigScreen(
                onBackPressed = onGoBack,
                onNavigateToModelConfig = { navigateTo(ModelConfig) }
            )
        }
    }

    data object ThemeSettings :
        Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = "主题设置") {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            ThemeSettingsScreen()
        }
    }

    data object ChatHistorySettings :
        Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = "聊天记录管理") {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            ChatHistorySettingsScreen()
        }
    }

    data object LanguageSettings :
        Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = "语言设置") {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            LanguageSettingsScreen(onBackPressed = onGoBack)
        }
    }

    // Toolbox secondary screens
    data object FormatConverter :
        Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = "万能格式转换") {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            FormatConverterToolScreen(navController = navController)
        }
    }

    data object FileManager :
        Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = "文件管理器") {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            FileManagerToolScreen(navController = navController)
        }
    }

    data object Terminal :
        Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = "命令终端") {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            TerminalToolScreen(navController = navController)
        }
    }

    data object TerminalAutoConfig :
        Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = "终端自动配置") {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            TerminalAutoConfigToolScreen(navController = navController)
        }
    }

    data object AppPermissions :
        Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = "应用权限管理") {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            AppPermissionsToolScreen(navController = navController)
        }
    }

    data object UIDebugger :
        Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = "UI调试工具") {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            UIDebuggerToolScreen(navController = navController)
        }
    }

    data object ShellExecutor :
        Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = "命令执行器") {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            ShellExecutorToolScreen(navController = navController)
        }
    }

    data object Logcat :
        Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = "日志查看器") {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            LogcatToolScreen(navController = navController)
        }
    }

    data object FFmpegToolbox :
        Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = "FFmpeg工具箱") {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            FFmpegToolboxScreen(navController = navController)
        }
    }

    data object MarkdownDemo :
        Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = "流式Markdown演示") {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            StreamMarkdownDemoScreen(onBackClick = onGoBack)
        }
    }

    data object ToolTester :
        Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = "工具测试中心") {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            ToolTesterScreen(navController = navController)
        }
    }

    data object TextToSpeech :
        Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = "文本转语音") {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            TextToSpeechToolScreen(navController = navController)
        }
    }

    data object SpeechToText :
        Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = "语音识别") {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            SpeechToTextToolScreen(navController = navController)
        }
    }

    // 获取屏幕标题
    fun getTitle(): String = titleRes ?: ""

    // 判断是否为二级屏幕
    val isSecondaryScreen: Boolean
        get() = parentScreen != null
}

// 路由管理器
object XiheRouter {
    // 处理返回导航
    fun handleBackNavigation(currentScreen: Screen): Screen? {
        return currentScreen.parentScreen
    }

    // 根据NavItem获取对应的Screen
    fun getScreenForNavItem(navItem: NavItem): Screen {
        return when (navItem) {
            NavItem.AiChat -> Screen.AiChat
            NavItem.MemoryBase -> Screen.MemoryBase
            NavItem.Packages -> Screen.Packages
            NavItem.Toolbox -> Screen.Toolbox
            NavItem.ShizukuCommands -> Screen.ShizukuCommands
            NavItem.Settings -> Screen.Settings
            NavItem.Help -> Screen.Help
            NavItem.About -> Screen.About
            NavItem.TokenConfig -> Screen.TokenConfig
            NavItem.UserPreferencesGuide -> Screen.UserPreferencesGuide()
            NavItem.AssistantConfig -> Screen.AssistantConfig
            NavItem.Agreement -> Screen.Agreement
            else -> Screen.AiChat
        }
    }
}

// 全局的手势状态持有者，用于在不同组件间共享手势状态
object GestureStateHolder {
    // 聊天界面手势是否被消费的状态
    var isChatScreenGestureConsumed: Boolean = false
}