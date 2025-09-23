package com.xihe.assistant.ui.main.layout

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
fun TabletLayout(
    currentScreen: Screen,
    selectedItem: NavItem,
    isTabletSidebarExpanded: Boolean,
    isLoading: Boolean,
    navGroups: List<com.xihe.assistant.ui.main.NavGroup>,
    navItems: List<NavItem>,
    isNetworkAvailable: Boolean,
    networkType: String,
    navController: NavController,
    scope: kotlinx.coroutines.CoroutineScope,
    drawerState: androidx.compose.material3.DrawerState,
    showFpsCounter: Boolean,
    tabletSidebarWidth: androidx.compose.ui.unit.Dp,
    collapsedTabletSidebarWidth: androidx.compose.ui.unit.Dp,
    onScreenChange: (Screen) -> Unit,
    onNavItemChange: (NavItem) -> Unit,
    onToggleSidebar: () -> Unit,
    navigateToTokenConfig: () -> Unit,
    canGoBack: Boolean,
    onGoBack: () -> Unit,
    isNavigatingBack: Boolean,
    topBarActions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    val context = LocalContext.current

    // Animate sidebar width
    val animatedSidebarWidth by animateDpAsState(
        targetValue = if (isTabletSidebarExpanded) tabletSidebarWidth else collapsedTabletSidebarWidth,
        animationSpec = tween(durationMillis = 300),
        label = "sidebar_width"
    )

    Row(
        modifier = Modifier.fillMaxSize()
    ) {
        // Sidebar
        Card(
            modifier = Modifier
                .width(animatedSidebarWidth)
                .fillMaxHeight(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Sidebar header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isTabletSidebarExpanded) {
                        Text(
                            text = "羲和助手",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    
                    IconButton(
                        onClick = onToggleSidebar
                    ) {
                        Icon(
                            imageVector = if (isTabletSidebarExpanded) {
                                Icons.Default.KeyboardArrowLeft
                            } else {
                                Icons.Default.KeyboardArrowRight
                            },
                            contentDescription = if (isTabletSidebarExpanded) "收起侧边栏" else "展开侧边栏"
                        )
                    }
                }

                Divider()

                // Navigation items
                if (isTabletSidebarExpanded) {
                    // Expanded sidebar content
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
                                onClick = { onNavItemChange(item) },
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
                } else {
                    // Collapsed sidebar content - only icons
                    navItems.forEach { item ->
                        NavigationDrawerItem(
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title
                                )
                            },
                            label = { Text("") },
                            selected = selectedItem == item,
                            onClick = { onNavItemChange(item) },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }

        // Main content area
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top app bar
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (canGoBack) {
                            IconButton(
                                onClick = onGoBack
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "返回"
                                )
                            }
                        }
                        Text(
                            text = currentScreen.getTitle().ifEmpty { selectedItem.title }
                        )
                    }
                },
                actions = {
                    topBarActions()
                }
            )

            // Screen content
            Box(
                modifier = Modifier.fillMaxSize()
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
}