package com.xihe.assistant.ui.main.layout

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.xihe.assistant.ui.common.NavItem
import com.xihe.assistant.ui.main.screens.Screen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneLayout(
    currentScreen: Screen,
    selectedItem: NavItem,
    isLoading: Boolean,
    navGroups: List<com.xihe.assistant.ui.main.NavGroup>,
    isNetworkAvailable: Boolean,
    networkType: String,
    drawerWidth: androidx.compose.ui.unit.Dp,
    navController: NavController,
    scope: kotlinx.coroutines.CoroutineScope,
    drawerState: androidx.compose.material3.DrawerState,
    showFpsCounter: Boolean,
    onScreenChange: (Screen) -> Unit,
    onNavItemChange: (NavItem) -> Unit,
    navigateToTokenConfig: () -> Unit,
    canGoBack: Boolean,
    onGoBack: () -> Unit,
    isNavigatingBack: Boolean,
    topBarActions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    val context = LocalContext.current

    // Drawer content
    val drawerContent = @Composable {
        NavigationDrawerContent(
            selectedItem = selectedItem,
            navGroups = navGroups,
            onNavItemClick = { navItem ->
                onNavItemChange(navItem)
                scope.launch {
                    drawerState.close()
                }
            },
            navigateToTokenConfig = {
                navigateToTokenConfig()
                scope.launch {
                    drawerState.close()
                }
            }
        )
    }

    // Main content
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentScreen.getTitle().ifEmpty { selectedItem.title }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                drawerState.open()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "打开菜单"
                        )
                    }
                },
                actions = {
                    topBarActions()
                }
            )
        },
        drawerContent = {
            drawerContent()
        },
        drawerState = drawerState,
        drawerWidth = drawerWidth
    ) { paddingValues ->
        // Screen content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            currentScreen.Content(
                navController = navController,
                navigateTo = onScreenChange,
                updateNavItem = onNavItemChange,
                onGoBack = onGoBack,
                hasBackgroundImage = false,
                onLoading = { /* Handle loading state */ },
                onError = { /* Handle error state */ },
                onGestureConsumed = { /* Handle gesture consumption */ }
            )

            // Loading overlay
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // Network status indicator
            if (!isNetworkAvailable) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        text = "网络连接不可用",
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onError
                    )
                }
            }

            // FPS counter
            if (showFpsCounter) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                    )
                ) {
                    Text(
                        text = "FPS: 60",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
fun NavigationDrawerContent(
    selectedItem: NavItem,
    navGroups: List<com.xihe.assistant.ui.main.NavGroup>,
    onNavItemClick: (NavItem) -> Unit,
    navigateToTokenConfig: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "羲和助手",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    text = "AI智能助手",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )
            }
        }

        // Navigation groups
        navGroups.forEach { group ->
            Text(
                text = group.title,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            
            group.items.forEach { item ->
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title
                        )
                    },
                    label = {
                        Text(item.title)
                    },
                    selected = selectedItem == item,
                    onClick = { onNavItemClick(item) },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Token config button
        Divider(modifier = Modifier.padding(horizontal = 16.dp))
        NavigationDrawerItem(
            icon = {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = "Token配置"
                )
            },
            label = {
                Text("Token配置")
            },
            selected = false,
            onClick = navigateToTokenConfig,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}