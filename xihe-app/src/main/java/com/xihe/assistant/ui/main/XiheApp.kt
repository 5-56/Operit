package com.xihe.assistant.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.xihe.assistant.core.tools.AIToolHandler
import com.xihe.assistant.data.preferences.ApiPreferences
import com.xihe.assistant.data.preferences.UserPreferencesManager
import com.xihe.assistant.ui.common.NavItem
import com.xihe.assistant.ui.main.layout.PhoneLayout
import com.xihe.assistant.ui.main.layout.TabletLayout
import com.xihe.assistant.ui.main.screens.XiheRouter
import com.xihe.assistant.ui.main.screens.Screen
import com.xihe.assistant.util.NetworkUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider

// 为TopAppBar的actions提供CompositionLocal
val LocalTopBarActions = compositionLocalOf<(@Composable (RowScope.() -> Unit)) -> Unit> { {} }

data class NavGroup(val title: String, val items: List<NavItem>)

/**
 * 羲和智能助手主应用组件
 * 提供更智能、更自动化的AI助手体验
 */
@Composable
fun XiheApp(initialNavItem: NavItem = NavItem.AiChat, toolHandler: AIToolHandler? = null) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 导航状态
    var selectedItem by remember { mutableStateOf(initialNavItem) }
    var currentScreen by remember {
        mutableStateOf(XiheRouter.getScreenForNavItem(initialNavItem))
    }
    val backStack = remember { mutableStateListOf<Screen>() }
    
    var isNavigatingBack by remember { mutableStateOf(false) }
    var topBarActions by remember { mutableStateOf<@Composable RowScope.() -> Unit>({}) }

    // 当currentScreen改变时，检查是否需要清空TopBarActions
    LaunchedEffect(currentScreen) {
        if (currentScreen !is Screen.AiChat && currentScreen !is Screen.TokenConfig) {
            topBarActions = {}
        }
    }

    // 导航函数
    fun navigateTo(newScreen: Screen, fromDrawer: Boolean = false) {
        if (newScreen == currentScreen) return

        isNavigatingBack = false

        if (fromDrawer) {
            backStack.clear()
        } else {
            if (!newScreen.isSecondaryScreen) {
                if (backStack.isNotEmpty()) {
                    val aiChatScreen = backStack.find { it is Screen.AiChat }
                    backStack.clear()
                    if (aiChatScreen != null && currentScreen !is Screen.AiChat) {
                        backStack.add(aiChatScreen)
                    }
                }
                if (currentScreen is Screen.AiChat) {
                    backStack.add(currentScreen)
                }
            } else {
                backStack.add(currentScreen)
            }
        }
        currentScreen = newScreen
        newScreen.navItem?.let { navItem -> selectedItem = navItem }
    }

    fun goBack() {
        if (backStack.isNotEmpty()) {
            isNavigatingBack = true
            val previousScreen = backStack.removeLast()
            currentScreen = previousScreen
            previousScreen.navItem?.let { navItem -> selectedItem = navItem }
        }
    }

    fun navigateToTokenConfig() {
        navigateTo(Screen.TokenConfig)
    }

    // 注册系统返回处理器
    BackHandler(enabled = backStack.isNotEmpty() && currentScreen !is Screen.AiChat, onBack = { goBack() })

    val canGoBack = currentScreen.isSecondaryScreen

    var isLoading by remember { mutableStateOf(false) }

    // 平板模式侧边栏状态
    var isTabletSidebarExpanded by remember { mutableStateOf(true) }
    var tabletSidebarWidth by remember { mutableStateOf(280.dp) }
    val collapsedTabletSidebarWidth = 64.dp

    // 设备屏幕尺寸计算
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp

    // 根据屏幕宽度确定是否使用平板布局
    val useTabletLayout = screenWidthDp >= 600

    // 导航项目分组
    val navGroups = listOf(
        NavGroup(
            "AI功能",
            listOf(
                NavItem.AiChat,
                NavItem.AssistantConfig,
                NavItem.SmartAutomation,
                NavItem.MemoryBase,
                NavItem.TokenConfig
            )
        ),
        NavGroup("智能工具", listOf(NavItem.Toolbox, NavItem.VoiceControl)),
        NavGroup("系统", listOf(NavItem.Settings, NavItem.Help, NavItem.About))
    )

    val navItems = navGroups.flatMap { it.items }

    // 网络状态监控
    var isNetworkAvailable by remember { mutableStateOf(NetworkUtils.isNetworkAvailable(context)) }
    var networkType by remember { mutableStateOf(NetworkUtils.getNetworkType(context)) }

    // 定期检查网络状态
    LaunchedEffect(Unit) {
        while (true) {
            isNetworkAvailable = NetworkUtils.isNetworkAvailable(context)
            networkType = NetworkUtils.getNetworkType(context)
            delay(10000)
        }
    }

    // 获取FPS计数器显示设置
    val apiPreferences = remember { ApiPreferences.getInstance(context) }
    val showFpsCounter = apiPreferences.showFpsCounterFlow.collectAsState(initial = false).value

    // 计算手机模式抽屉宽度
    val drawerWidth = (screenWidthDp * 0.75).dp

    // 主应用容器
    Box(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
        CompositionLocalProvider(LocalTopBarActions provides { actions: @Composable RowScope.() -> Unit -> 
            topBarActions = actions 
        }) {
            if (useTabletLayout) {
                // 平板布局
                TabletLayout(
                    currentScreen = currentScreen,
                    selectedItem = selectedItem,
                    isTabletSidebarExpanded = isTabletSidebarExpanded,
                    isLoading = isLoading,
                    navGroups = navGroups,
                    navItems = navItems,
                    isNetworkAvailable = isNetworkAvailable,
                    networkType = networkType,
                    navController = navController,
                    scope = scope,
                    drawerState = drawerState,
                    showFpsCounter = showFpsCounter,
                    tabletSidebarWidth = tabletSidebarWidth,
                    collapsedTabletSidebarWidth = collapsedTabletSidebarWidth,
                    onScreenChange = { screen -> navigateTo(screen) },
                    onNavItemChange = { item ->
                        navigateTo(
                            XiheRouter.getScreenForNavItem(item),
                            fromDrawer = true
                        )
                    },
                    onToggleSidebar = {
                        isTabletSidebarExpanded = !isTabletSidebarExpanded
                    },
                    navigateToTokenConfig = ::navigateToTokenConfig,
                    canGoBack = canGoBack,
                    onGoBack = ::goBack,
                    isNavigatingBack = isNavigatingBack,
                    topBarActions = { topBarActions() }
                )
            } else {
                // 手机布局
                PhoneLayout(
                    currentScreen = currentScreen,
                    selectedItem = selectedItem,
                    isLoading = isLoading,
                    navGroups = navGroups,
                    isNetworkAvailable = isNetworkAvailable,
                    networkType = networkType,
                    drawerWidth = drawerWidth,
                    navController = navController,
                    scope = scope,
                    drawerState = drawerState,
                    showFpsCounter = showFpsCounter,
                    onScreenChange = { screen -> navigateTo(screen) },
                    onNavItemChange = { item ->
                        navigateTo(
                            XiheRouter.getScreenForNavItem(item),
                            fromDrawer = true
                        )
                    },
                    navigateToTokenConfig = ::navigateToTokenConfig,
                    canGoBack = canGoBack,
                    onGoBack = ::goBack,
                    isNavigatingBack = isNavigatingBack,
                    topBarActions = { topBarActions() }
                )
            }
        }
    }
}