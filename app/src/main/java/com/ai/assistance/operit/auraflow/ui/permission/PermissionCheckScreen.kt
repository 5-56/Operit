package com.ai.assistance.operit.auraflow.ui.permission

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.auraflow.permission.*
import kotlinx.coroutines.launch

/**
 * 权限检查主界面
 * 提供完整的权限引导流程
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionCheckScreen(
    onPermissionComplete: () -> Unit = {},
    onSkip: () -> Unit = {},
    viewModel: PermissionCheckViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 状态监听
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionStates by viewModel.permissionStates.collectAsStateWithLifecycle()
    
    // 检查是否完成
    LaunchedEffect(permissionStates) {
        if (viewModel.areAllRequiredPermissionsGranted()) {
            onPermissionComplete()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部进度指示
        PermissionProgressIndicator(
            totalPermissions = PermissionType.values().filter { it.isRequired }.size,
            grantedPermissions = permissionStates.values.count { 
                it.type.isRequired && it.status == PermissionStatus.GRANTED 
            },
            modifier = Modifier.fillMaxWidth()
        )
        
        if (uiState.showWelcome) {
            // 欢迎页面
            WelcomeContent(
                onGetStarted = {
                    scope.launch {
                        viewModel.startPermissionSetup(context)
                    }
                },
                onSkip = onSkip
            )
        } else {
            // 权限列表页面
            PermissionListContent(
                permissionStates = permissionStates,
                isLoading = uiState.isLoading,
                onRequestPermission = { permissionType ->
                    scope.launch {
                        viewModel.requestPermission(context, permissionType)
                    }
                },
                onRequestAllPermissions = {
                    scope.launch {
                        viewModel.requestAllMissingPermissions(context)
                    }
                },
                onSkip = onSkip
            )
        }
    }
}

/**
 * 权限进度指示器
 */
@Composable
private fun PermissionProgressIndicator(
    totalPermissions: Int,
    grantedPermissions: Int,
    modifier: Modifier = Modifier
) {
    val progress = if (totalPermissions > 0) grantedPermissions.toFloat() / totalPermissions else 0f
    
    Card(
        modifier = modifier.padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "权限设置进度",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "$grantedPermissions / $totalPermissions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = when {
                    progress >= 1f -> "🎉 所有必需权限已授权完成！"
                    progress >= 0.7f -> "即将完成权限设置..."
                    progress >= 0.3f -> "正在进行权限设置..."
                    else -> "开始设置AuraFlow Agent权限"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * 欢迎页面内容
 */
@Composable
private fun WelcomeContent(
    onGetStarted: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 主图标
        Icon(
            Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "欢迎使用 AuraFlow Agent",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "为了提供最佳的智能自动化体验，我们需要您授权一些重要权限",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 特性说明
        FeatureCard(
            icon = Icons.Default.TouchApp,
            title = "智能操作执行",
            description = "通过无障碍服务执行自动化操作"
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        FeatureCard(
            icon = Icons.Default.Visibility,
            title = "屏幕内容感知",
            description = "实时捕获屏幕内容为AI提供视觉信息"
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        FeatureCard(
            icon = Icons.Default.PictureInPicture,
            title = "浮动控制窗口",
            description = "随时显示浮动控制面板方便操作"
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // 操作按钮
        Button(
            onClick = onGetStarted,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Start, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("开始权限设置")
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        TextButton(
            onClick = onSkip
        ) {
            Text("暂时跳过")
        }
    }
}

/**
 * 特性卡片
 */
@Composable
private fun FeatureCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 权限列表内容
 */
@Composable
private fun PermissionListContent(
    permissionStates: Map<PermissionType, PermissionState>,
    isLoading: Boolean,
    onRequestPermission: (PermissionType) -> Unit,
    onRequestAllPermissions: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 标题
        Text(
            text = "权限授权",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "请为以下功能授予相应权限",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // 权限列表
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = permissionStates.values.toList(),
                key = { it.type.name }
            ) { permissionState ->
                PermissionCard(
                    permissionState = permissionState,
                    onRequestPermission = { onRequestPermission(permissionState.type) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 底部操作按钮
        val missingPermissions = permissionStates.values.filter { 
            it.type.isRequired && it.status != PermissionStatus.GRANTED 
        }
        
        if (missingPermissions.isNotEmpty()) {
            Button(
                onClick = onRequestAllPermissions,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("设置中...")
                } else {
                    Icon(Icons.Default.Security, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("一键授权所有权限")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("暂时跳过")
        }
    }
}

/**
 * 权限卡片
 */
@Composable
private fun PermissionCard(
    permissionState: PermissionState,
    onRequestPermission: () -> Unit
) {
    val backgroundColor = when (permissionState.status) {
        PermissionStatus.GRANTED -> MaterialTheme.colorScheme.primaryContainer
        PermissionStatus.DENIED -> MaterialTheme.colorScheme.errorContainer
        PermissionStatus.CHECKING -> MaterialTheme.colorScheme.secondaryContainer
        PermissionStatus.NOT_REQUESTED -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = permissionState.canRequest && 
                         permissionState.status != PermissionStatus.GRANTED &&
                         permissionState.status != PermissionStatus.CHECKING
            ) {
                onRequestPermission()
            },
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 权限信息
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = getPermissionIcon(permissionState.type),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = permissionState.type.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        
                        if (permissionState.type.isRequired) {
                            Spacer(modifier = Modifier.width(4.dp))
                            
                            Surface(
                                color = MaterialTheme.colorScheme.error,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Text(
                                    text = "必需",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = permissionState.type.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // 错误信息
                    if (permissionState.errorMessage != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = permissionState.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // 状态指示
                PermissionStatusIndicator(permissionState.status)
            }
            
            // 权限说明（展开状态）
            var expanded by remember { mutableStateOf(false) }
            
            if (permissionState.status == PermissionStatus.DENIED) {
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (expanded) "收起说明" else "查看详细说明")
                }
                
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Text(
                                text = PermissionManager.getInstance().getPermissionRationale(permissionState.type),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 权限状态指示器
 */
@Composable
private fun PermissionStatusIndicator(status: PermissionStatus) {
    val (icon, color, text) = when (status) {
        PermissionStatus.GRANTED -> Triple(
            Icons.Default.CheckCircle,
            Color.Green,
            "已授权"
        )
        PermissionStatus.DENIED -> Triple(
            Icons.Default.Cancel,
            MaterialTheme.colorScheme.error,
            "未授权"
        )
        PermissionStatus.CHECKING -> Triple(
            Icons.Default.HourglassEmpty,
            MaterialTheme.colorScheme.secondary,
            "检查中"
        )
        PermissionStatus.NOT_REQUESTED -> Triple(
            Icons.Default.Info,
            MaterialTheme.colorScheme.outline,
            "待设置"
        )
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (status == PermissionStatus.CHECKING) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = color
            )
        } else {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = color
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

/**
 * 获取权限对应的图标
 */
private fun getPermissionIcon(permissionType: PermissionType): ImageVector {
    return when (permissionType) {
        PermissionType.OVERLAY -> Icons.Default.PictureInPicture
        PermissionType.ACCESSIBILITY -> Icons.Default.Accessibility
        PermissionType.MEDIA_PROJECTION -> Icons.Default.Screenshot
        PermissionType.NOTIFICATION -> Icons.Default.Notifications
        PermissionType.STORAGE -> Icons.Default.Storage
        PermissionType.INTERNET -> Icons.Default.Wifi
    }
}