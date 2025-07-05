package com.ai.assistance.operit.auraflow.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.ai.assistance.operit.auraflow.R
import com.ai.assistance.operit.auraflow.core.AuraFlowAgentManager
import com.ai.assistance.operit.auraflow.protocol.ConnectionStatus
import com.ai.assistance.operit.auraflow.ui.floating.*
import com.ai.assistance.operit.auraflow.ui.theme.AuraFlowTheme
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.util.Log

/**
 * 浮动窗口服务
 * 管理系统级悬浮窗的显示和交互
 */
class FloatingWindowService : LifecycleService() {
    
    companion object {
        private const val TAG = "FloatingWindowService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "floating_window_channel"
        private const val ACTION_CLOSE = "action_close"
        private const val ACTION_TOGGLE_MODE = "action_toggle_mode"
        private const val ACTION_PLAY_PAUSE = "action_play_pause"
        
        fun start(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java)
            context.stopService(intent)
        }
    }
    
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    
    // 浮动窗口状态
    private val _windowState = MutableStateFlow(
        FloatingWindowState(
            mode = FloatingWindowMode.FULL,
            connectionStatus = ConnectionStatus.DISCONNECTED,
            isTaskRunning = false
        )
    )
    private val windowState: StateFlow<FloatingWindowState> = _windowState.asStateFlow()
    
    // 窗口拖拽状态
    private var isDragging = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "浮动窗口服务创建")
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        // 监听Agent状态变化
        observeAgentStatus()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        when (intent?.action) {
            ACTION_CLOSE -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_MODE -> {
                toggleWindowMode()
            }
            ACTION_PLAY_PAUSE -> {
                toggleTaskExecution()
            }
            else -> {
                if (floatingView == null) {
                    createFloatingWindow()
                }
            }
        }
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        removeFloatingWindow()
        Log.d(TAG, "浮动窗口服务销毁")
    }
    
    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
    
    /**
     * 创建浮动窗口
     */
    private fun createFloatingWindow() {
        if (floatingView != null) return
        
        try {
            // 创建布局参数
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            
            layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 100
                y = 100
            }
            
            // 创建Compose视图
            floatingView = ComposeView(this).apply {
                setContent {
                    AuraFlowTheme {
                        val state by windowState.collectAsState()
                        
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            AuraFlowFloatingWindow(
                                state = state,
                                onPlayPause = { toggleTaskExecution() },
                                onStop = { stopTask() },
                                onMinimize = { switchToCompactMode() },
                                onExpand = { switchToFullMode() },
                                onOpenMainApp = { openMainApp() },
                                onClose = { stopSelf() }
                            )
                        }
                    }
                }
            }
            
            // 添加触摸监听器
            setupTouchListener()
            
            // 添加到WindowManager
            windowManager?.addView(floatingView, layoutParams)
            
            Log.d(TAG, "浮动窗口创建成功")
            
        } catch (e: Exception) {
            Log.e(TAG, "创建浮动窗口失败", e)
            Toast.makeText(this, "创建浮动窗口失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 移除浮动窗口
     */
    private fun removeFloatingWindow() {
        try {
            floatingView?.let { view ->
                windowManager?.removeView(view)
                floatingView = null
                layoutParams = null
                Log.d(TAG, "浮动窗口移除成功")
            }
        } catch (e: Exception) {
            Log.e(TAG, "移除浮动窗口失败", e)
        }
    }
    
    /**
     * 设置触摸监听器
     */
    private fun setupTouchListener() {
        floatingView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    initialX = layoutParams?.x ?: 0
                    initialY = layoutParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    
                    // 判断是否开始拖拽
                    if (!isDragging && (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10)) {
                        isDragging = true
                        _windowState.value = _windowState.value.copy(isDragging = true)
                    }
                    
                    if (isDragging) {
                        layoutParams?.apply {
                            x = (initialX + deltaX).toInt()
                            y = (initialY + deltaY).toInt()
                        }
                        windowManager?.updateViewLayout(floatingView, layoutParams)
                    }
                    true
                }
                
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        isDragging = false
                        _windowState.value = _windowState.value.copy(isDragging = false)
                        
                        // 窗口边缘吸附
                        snapToEdge()
                    }
                    false
                }
                
                else -> false
            }
        }
    }
    
    /**
     * 窗口边缘吸附
     */
    private fun snapToEdge() {
        layoutParams?.let { params ->
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            
            // 水平吸附
            if (params.x < screenWidth / 2) {
                params.x = 20 // 吸附到左边
            } else {
                params.x = screenWidth - 180 // 吸附到右边（假设窗口宽度约160dp）
            }
            
            // 垂直边界限制
            if (params.y < 0) params.y = 0
            if (params.y > screenHeight - 200) params.y = screenHeight - 200
            
            windowManager?.updateViewLayout(floatingView, params)
        }
    }
    
    /**
     * 切换窗口模式
     */
    private fun toggleWindowMode() {
        val currentMode = _windowState.value.mode
        val newMode = when (currentMode) {
            FloatingWindowMode.FULL -> FloatingWindowMode.COMPACT
            FloatingWindowMode.COMPACT -> FloatingWindowMode.MINI
            FloatingWindowMode.MINI -> FloatingWindowMode.FULL
        }
        
        _windowState.value = _windowState.value.copy(mode = newMode)
        updateNotification()
    }
    
    /**
     * 切换到紧凑模式
     */
    private fun switchToCompactMode() {
        _windowState.value = _windowState.value.copy(mode = FloatingWindowMode.COMPACT)
    }
    
    /**
     * 切换到完整模式
     */
    private fun switchToFullMode() {
        _windowState.value = _windowState.value.copy(mode = FloatingWindowMode.FULL)
    }
    
    /**
     * 切换任务执行状态
     */
    private fun toggleTaskExecution() {
        lifecycleScope.launch {
            try {
                val agentManager = AuraFlowAgentManager.getInstance(this@FloatingWindowService)
                
                if (_windowState.value.isTaskRunning) {
                    agentManager.pauseTask()
                } else {
                    agentManager.resumeTask()
                }
                
                Log.d(TAG, "任务执行状态切换")
            } catch (e: Exception) {
                Log.e(TAG, "切换任务状态失败", e)
            }
        }
    }
    
    /**
     * 停止任务
     */
    private fun stopTask() {
        lifecycleScope.launch {
            try {
                val agentManager = AuraFlowAgentManager.getInstance(this@FloatingWindowService)
                agentManager.stopTask()
                Log.d(TAG, "任务已停止")
            } catch (e: Exception) {
                Log.e(TAG, "停止任务失败", e)
            }
        }
    }
    
    /**
     * 打开主应用
     */
    private fun openMainApp() {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开主应用失败", e)
        }
    }
    
    /**
     * 监听Agent状态变化
     */
    private fun observeAgentStatus() {
        lifecycleScope.launch {
            try {
                val agentManager = AuraFlowAgentManager.getInstance(this@FloatingWindowService)
                
                // 监听连接状态
                // TODO: 实际实现中需要从AgentManager获取状态流
                // agentManager.connectionStatus.collect { status ->
                //     _windowState.value = _windowState.value.copy(connectionStatus = status)
                //     updateNotification()
                // }
                
                // 模拟状态更新
                kotlinx.coroutines.delay(2000)
                _windowState.value = _windowState.value.copy(
                    connectionStatus = ConnectionStatus.CONNECTED
                )
                updateNotification()
                
            } catch (e: Exception) {
                Log.e(TAG, "监听Agent状态失败", e)
            }
        }
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "浮动窗口服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "AuraFlow Agent 浮动窗口服务"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 创建通知
     */
    private fun createNotification(): Notification {
        val state = _windowState.value
        
        // 主应用Intent
        val mainIntent = packageManager.getLaunchIntentForPackage(packageName)
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 关闭Intent
        val closeIntent = Intent(this, FloatingWindowService::class.java).apply {
            action = ACTION_CLOSE
        }
        val closePendingIntent = PendingIntent.getService(
            this, 1, closeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 播放/暂停Intent
        val playPauseIntent = Intent(this, FloatingWindowService::class.java).apply {
            action = ACTION_PLAY_PAUSE
        }
        val playPausePendingIntent = PendingIntent.getService(
            this, 2, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val statusText = when {
            state.connectionStatus != ConnectionStatus.CONNECTED -> "未连接"
            state.isTaskRunning -> "运行中"
            else -> "待命"
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AuraFlow Agent")
            .setContentText("浮动窗口 - $statusText")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(mainPendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(
                R.drawable.ic_launcher_foreground,
                if (state.isTaskRunning) "暂停" else "播放",
                playPausePendingIntent
            )
            .addAction(
                R.drawable.ic_launcher_foreground,
                "关闭",
                closePendingIntent
            )
            .build()
    }
    
    /**
     * 更新通知
     */
    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }
}