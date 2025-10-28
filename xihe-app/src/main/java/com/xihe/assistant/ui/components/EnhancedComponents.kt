package com.xihe.assistant.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 增强的UI组件集合
 * 提供更丰富的交互效果和动画
 */

/**
 * 智能按钮 - 带有智能提示和动画效果
 */
@Composable
fun SmartButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    variant: ButtonVariant = ButtonVariant.Primary,
    loading: Boolean = false,
    tooltip: String? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "smart_button")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (loading) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (loading) 0.7f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(modifier = modifier) {
        Button(
            onClick = onClick,
            enabled = enabled && !loading,
            modifier = Modifier
                .scale(if (loading) scale else 1f)
                .alpha(if (loading) alpha else 1f),
            colors = when (variant) {
                ButtonVariant.Primary -> ButtonDefaults.buttonColors()
                ButtonVariant.Secondary -> ButtonDefaults.outlinedButtonColors()
                ButtonVariant.Success -> ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
                ButtonVariant.Warning -> ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF9800)
                )
                ButtonVariant.Danger -> ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF44336)
                )
            }
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            Text(text)
        }

        // 智能提示
        tooltip?.let {
            AnimatedVisibility(
                visible = !enabled,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/**
 * 智能卡片 - 带有悬停效果和智能交互
 */
@Composable
fun SmartCard(
    title: String,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    elevation: CardElevation = CardElevation.Normal,
    gradient: Boolean = false
) {
    var isHovered by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.02f else 1f,
        animationSpec = tween(200),
        label = "scale"
    )

    val elevationValue by animateDpAsState(
        targetValue = when (elevation) {
            CardElevation.None -> 0.dp
            CardElevation.Normal -> if (isHovered) 8.dp else 4.dp
            CardElevation.High -> if (isHovered) 12.dp else 8.dp
        },
        animationSpec = tween(200),
        label = "elevation"
    )

    Card(
        modifier = modifier
            .scale(scale)
            .clickable(
                enabled = onClick != null,
                onClick = { onClick?.invoke() }
            )
            .pointerInput(Unit) {
                // 长按检测
                if (onLongClick != null) {
                    detectTapGestures(
                        onLongPress = { onLongClick() }
                    )
                }
            },
        elevation = CardDefaults.cardElevation(defaultElevation = elevationValue),
        colors = CardDefaults.cardColors(
            containerColor = if (gradient) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        if (gradient) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            ) {
                CardContent(title, content, icon)
            }
        } else {
            CardContent(title, content, icon)
        }
    }
}

@Composable
private fun CardContent(
    title: String,
    content: @Composable () -> Unit,
    icon: ImageVector?
) {
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

/**
 * 智能进度指示器 - 带有智能状态显示
 */
@Composable
fun SmartProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    showPercentage: Boolean = true,
    animated: Boolean = true
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = if (animated) {
            tween(1000, easing = EaseOutCubic)
        } else {
            tween(0)
        },
        label = "progress"
    )

    Column(modifier = modifier) {
        LinearProgressIndicator(
            progress = animatedProgress,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = backgroundColor
        )
        
        if (showPercentage) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${(animatedProgress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 智能浮动操作按钮 - 带有智能动画和状态
 */
@Composable
fun SmartFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector,
    contentDescription: String? = null,
    extended: Boolean = false,
    text: String? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fab")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    AnimatedVisibility(
        visible = true,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut()
    ) {
        FloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            containerColor = backgroundColor,
            contentColor = contentColor
        ) {
            if (extended && text != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = contentDescription,
                        modifier = Modifier.rotate(rotation)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text)
                }
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.rotate(rotation)
                )
            }
        }
    }
}

/**
 * 智能开关 - 带有智能动画和状态提示
 */
@Composable
fun SmartSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    description: String? = null,
    icon: ImageVector? = null
) {
    val animatedColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        },
        animationSpec = tween(300),
        label = "color"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = animatedColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        
        Column(modifier = Modifier.weight(1f)) {
            label?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

/**
 * 智能列表项 - 带有智能交互和动画
 */
@Composable
fun SmartListItem(
    title: String,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    var isPressed by remember { mutableStateOf(false) }
    
    val backgroundColor by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            isPressed -> MaterialTheme.colorScheme.surfaceVariant
            else -> Color.Transparent
        },
        animationSpec = tween(200),
        label = "background"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = onClick != null,
                onClick = { onClick?.invoke() }
            )
            .pointerInput(Unit) {
                if (onLongClick != null) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onLongPress = { onLongClick() }
                    )
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (selected) 4.dp else 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leadingIcon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            trailingIcon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 智能加载指示器 - 带有智能状态和动画
 */
@Composable
fun SmartLoadingIndicator(
    message: String = "加载中...",
    modifier: Modifier = Modifier,
    size: LoadingSize = LoadingSize.Medium
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(
                    when (size) {
                        LoadingSize.Small -> 24.dp
                        LoadingSize.Medium -> 32.dp
                        LoadingSize.Large -> 48.dp
                    }
                )
                .rotate(rotation)
                .alpha(alpha),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = when (size) {
                LoadingSize.Small -> 2.dp
                LoadingSize.Medium -> 3.dp
                LoadingSize.Large -> 4.dp
            }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// 枚举类定义
enum class ButtonVariant {
    Primary, Secondary, Success, Warning, Danger
}

enum class CardElevation {
    None, Normal, High
}

enum class LoadingSize {
    Small, Medium, Large
}