package com.xihe.assistant.ui.main

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.xihe.assistant.R
import com.xihe.assistant.core.automation.SmartAutomationManager
import com.xihe.assistant.core.tools.AIToolHandler
import com.xihe.assistant.data.preferences.UserPreferencesManager
import com.xihe.assistant.data.preferences.androidPermissionPreferences
import com.xihe.assistant.data.updates.UpdateManager
import com.xihe.assistant.data.updates.UpdateStatus
import com.xihe.assistant.ui.common.NavItem
import com.xihe.assistant.ui.features.permission.screens.PermissionGuideScreen
import com.xihe.assistant.ui.features.startup.screens.PluginLoadingScreenWithState
import com.xihe.assistant.ui.features.startup.screens.PluginLoadingState
import com.xihe.assistant.ui.theme.XiheTheme
import com.xihe.assistant.util.AnrMonitor
import com.xihe.assistant.util.LocaleUtils
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 羲和智能助手主活动
 * 提供更智能、更自动化的AI助手体验
 */
class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"

    // 工具和管理器
    private lateinit var toolHandler: AIToolHandler
    private lateinit var preferencesManager: UserPreferencesManager
    private lateinit var automationManager: SmartAutomationManager
    private var updateCheckPerformed = false
    private lateinit var anrMonitor: AnrMonitor

    // 对话框状态
    private var showConfirmationDialogState by mutableStateOf<String?>(null)

    // 导航状态
    private var showPreferencesGuide by mutableStateOf(false)

    // MCP插件状态
    private val pluginLoadingState = PluginLoadingState()

    // 双击返回退出相关变量
    private var backPressedTime: Long = 0
    private val backPressedInterval: Long = 2000

    // UpdateManager实例
    private lateinit var updateManager: UpdateManager

    // 是否显示权限引导界面
    private var showPermissionGuide by mutableStateOf(false)

    // 是否已完成权限检查
    private var initialChecksDone = false

    override fun attachBaseContext(newBase: Context) {
        val code = LocaleUtils.getCurrentLanguage(newBase)
        val locale = Locale(code)
        val config = Configuration(newBase.resources.configuration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            config.setLocales(localeList)
        } else {
            config.locale = locale
            Locale.setDefault(locale)
        }

        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
        Log.d(TAG, "MainActivity应用语言设置: $code")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Android SDK version: ${Build.VERSION.SDK_INT}")

        window.setBackgroundDrawableResource(android.R.color.black)

        initializeComponents()
        cleanTemporaryFiles()
        anrMonitor.start()
        setupPreferencesListener()
        configureDisplaySettings()

        // 设置插件加载状态
        pluginLoadingState.setAppContext(applicationContext)
        pluginLoadingState.setOnSkipCallback {
            Log.d(TAG, "用户跳过了插件加载过程")
            Toast.makeText(this, getString(R.string.plugin_loading_skipped), Toast.LENGTH_SHORT).show()
        }

        setAppContent()
        setupUpdateManager()

        if (savedInstanceState == null) {
            performInitialChecks()
        } else {
            initialChecksDone = true
        }

        setupBackPressHandler()
    }

    private fun performInitialChecks() {
        lifecycleScope.launch {
            checkPermissionLevelSet()
            initialChecksDone = true
            setAppContent()
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val currentTime = System.currentTimeMillis()

                    if (currentTime - backPressedTime > backPressedInterval) {
                        backPressedTime = currentTime
                        Toast.makeText(this@MainActivity, getString(R.string.press_back_again_to_exit), Toast.LENGTH_SHORT).show()
                    } else {
                        finish()
                    }
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")
        pluginLoadingState.hide()
        anrMonitor.stop()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "onConfigurationChanged: orientation=${newConfig.orientation}")
        pluginLoadingState.hide()
    }

    private fun initializeComponents() {
        toolHandler = AIToolHandler.getInstance(this)
        toolHandler.registerDefaultTools()

        automationManager = SmartAutomationManager.getInstance(this)

        anrMonitor = AnrMonitor(this, lifecycleScope)

        preferencesManager = UserPreferencesManager(this)
        showPreferencesGuide = !preferencesManager.isPreferencesInitialized()
        Log.d(TAG, "初始化检查: 用户偏好已初始化=${!showPreferencesGuide}")
    }

    private fun checkPermissionLevelSet() {
        val permissionLevel = androidPermissionPreferences.getPreferredPermissionLevel()
        Log.d(TAG, "当前权限级别: $permissionLevel")
        showPermissionGuide = permissionLevel == null
        Log.d(TAG, "权限级别检查: 已设置=${!showPermissionGuide}")
    }

    private fun setupPreferencesListener() {
        lifecycleScope.launch {
            preferencesManager.getUserPreferencesFlow().collect { profile ->
                val newValue = !profile.isInitialized
                if (showPreferencesGuide != newValue) {
                    Log.d(TAG, "偏好变更: 从 $showPreferencesGuide 变为 $newValue")
                    showPreferencesGuide = newValue
                    setAppContent()
                }
            }
        }
    }

    private fun configureDisplaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                window.setSustainedPerformanceMode(true)
                Log.d(TAG, "已成功请求持续高性能模式。")
            } catch (e: Exception) {
                Log.w(TAG, "请求持续高性能模式失败。", e)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val highestMode = getHighestRefreshRate()
            if (highestMode > 0) {
                window.attributes.preferredDisplayModeId = highestMode
                Log.d(TAG, "设置窗口首选显示模式ID: $highestMode")
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val refreshRate = getDeviceRefreshRate()
            if (refreshRate > 60f) {
                window.attributes.preferredRefreshRate = refreshRate
                Log.d(TAG, "设置窗口首选刷新率: $refreshRate Hz")
            }
        }

        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
    }

    private fun setAppContent() {
        setContent {
            XiheTheme {
                Box {
                    if (!initialChecksDone) {
                        // 显示加载占位符
                    } else {
                        if (showPermissionGuide) {
                            PermissionGuideScreen(
                                onComplete = {
                                    showPermissionGuide = false
                                    setAppContent()
                                }
                            )
                        } else {
                            // 显示主应用界面
                            XiheApp(
                                initialNavItem = NavItem.AiChat,
                                toolHandler = toolHandler
                            )
                        }
                    }
                    
                    // 插件加载界面
                    PluginLoadingScreenWithState(
                        loadingState = pluginLoadingState,
                        modifier = Modifier.zIndex(10f)
                    )
                }
            }
        }
    }

    private fun setupUpdateManager() {
        updateManager = UpdateManager.getInstance(this)

        updateManager.updateStatus.observe(
            this,
            Observer { status ->
                if (status is UpdateStatus.Available) {
                    showUpdateNotification(status)
                }
            }
        )

        lifecycleScope.launch {
            delay(3000)
            checkForUpdates()
        }
    }

    private fun checkForUpdates() {
        if (updateCheckPerformed) return
        updateCheckPerformed = true

        val appVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            "未知"
        }

        lifecycleScope.launch {
            try {
                updateManager.checkForUpdates(appVersion)
            } catch (e: Exception) {
                Log.e(TAG, "更新检查失败: ${e.message}")
            }
        }
    }

    private fun showUpdateNotification(updateInfo: UpdateStatus.Available) {
        val currentVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "未知"
        }

        Log.d(TAG, "发现新版本: ${updateInfo.newVersion}，当前版本: $currentVersion")
        val updateMessage = "发现新版本 ${updateInfo.newVersion}，请前往「关于」页面查看详情"
        Toast.makeText(this, updateMessage, Toast.LENGTH_LONG).show()
    }

    private fun getHighestRefreshRate(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val displayModes = display?.supportedModes ?: return 0
            var maxRefreshRate = 60f
            var highestModeId = 0

            for (mode in displayModes) {
                if (mode.refreshRate > maxRefreshRate) {
                    maxRefreshRate = mode.refreshRate
                    highestModeId = mode.modeId
                }
            }
            Log.d(TAG, "Selected display mode with refresh rate: $maxRefreshRate Hz")
            return highestModeId
        }
        return 0
    }

    private fun getDeviceRefreshRate(): Float {
        val windowManager = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION") windowManager.defaultDisplay
        }

        var refreshRate = 60f

        if (display != null) {
            try {
                @Suppress("DEPRECATION") val modes = display.supportedModes
                for (mode in modes) {
                    if (mode.refreshRate > refreshRate) {
                        refreshRate = mode.refreshRate
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting refresh rate", e)
            }
        }

        Log.d(TAG, "Selected refresh rate: $refreshRate Hz")
        return refreshRate
    }

    private fun cleanTemporaryFiles() {
        lifecycleScope.launch {
            try {
                val tempDir = java.io.File("/sdcard/Download/Xihe/cleanOnExit")
                if (tempDir.exists() && tempDir.isDirectory) {
                    val noMediaFile = java.io.File(tempDir, ".nomedia")
                    if (!noMediaFile.exists()) {
                        noMediaFile.createNewFile()
                    }
                    
                    Log.d(TAG, "开始清理临时文件目录: ${tempDir.absolutePath}")
                    val files = tempDir.listFiles()
                    var deletedCount = 0

                    files?.forEach { file ->
                        if (file.isFile && file.name != ".nomedia" && file.delete()) {
                            deletedCount++
                        }
                    }

                    Log.d(TAG, "已删除${deletedCount}个临时文件")
                }
            } catch (e: Exception) {
                Log.e(TAG, "清理临时文件失败", e)
            }
        }
    }
}