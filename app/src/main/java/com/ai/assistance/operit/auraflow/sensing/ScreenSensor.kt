package com.ai.assistance.operit.auraflow.sensing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.WindowManager
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * AuraFlow Agent 屏幕感知模块
 * 负责屏幕截图和相关感知功能
 */
class ScreenSensor private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ScreenSensor"
        
        @Volatile
        private var INSTANCE: ScreenSensor? = null
        
        fun getInstance(context: Context): ScreenSensor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScreenSensor(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // 屏幕相关
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val defaultDisplay = windowManager.defaultDisplay
    private val displayMetrics = DisplayMetrics()
    
    // MediaProjection 相关
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    // 后台处理线程
    private val handlerThread = HandlerThread("ScreenSensor").apply { start() }
    private val backgroundHandler = Handler(handlerThread.looper)
    
    // 协程作用域
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // 截图状态
    private var isInitialized = false
    private var lastScreenshotTime = 0L
    private val screenshotCooldown = 500L // 截图冷却时间500ms
    
    init {
        defaultDisplay.getMetrics(displayMetrics)
        Log.d(TAG, "屏幕感知模块初始化: ${displayMetrics.widthPixels}x${displayMetrics.heightPixels}")
    }
    
    /**
     * 初始化屏幕感知（需要MediaProjection权限）
     */
    fun initialize(mediaProjection: MediaProjection) {
        try {
            this.mediaProjection = mediaProjection
            setupImageReader()
            setupVirtualDisplay()
            isInitialized = true
            Log.d(TAG, "屏幕感知初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "屏幕感知初始化失败", e)
        }
    }
    
    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean {
        return isInitialized && mediaProjection != null
    }
    
    /**
     * 捕获屏幕截图
     */
    suspend fun captureScreen(): Bitmap? {
        if (!isInitialized()) {
            Log.w(TAG, "屏幕感知未初始化，无法截图")
            return null
        }
        
        // 检查冷却时间
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastScreenshotTime < screenshotCooldown) {
            Log.d(TAG, "截图冷却中，跳过本次截图")
            return null
        }
        
        return try {
            lastScreenshotTime = currentTime
            captureScreenInternal()
        } catch (e: Exception) {
            Log.e(TAG, "截图失败", e)
            null
        }
    }
    
    /**
     * 内部截图实现
     */
    private suspend fun captureScreenInternal(): Bitmap? = suspendCoroutine { continuation ->
        val imageReader = this.imageReader ?: run {
            continuation.resume(null)
            return@suspendCoroutine
        }
        
        var imageRetrieved = false
        
        val readerListener = object : ImageReader.OnImageAvailableListener {
            override fun onImageAvailable(reader: ImageReader) {
                if (imageRetrieved) return
                imageRetrieved = true
                
                try {
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        val bitmap = convertImageToBitmap(image)
                        image.close()
                        continuation.resume(bitmap)
                    } else {
                        continuation.resume(null)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "处理截图图像失败", e)
                    continuation.resume(null)
                }
            }
        }
        
        imageReader.setOnImageAvailableListener(readerListener, backgroundHandler)
        
        // 设置超时，避免无限等待
        scope.launch {
            delay(3000) // 3秒超时
            if (!imageRetrieved) {
                imageRetrieved = true
                continuation.resume(null)
                Log.w(TAG, "截图超时")
            }
        }
    }
    
    /**
     * 将Image转换为Bitmap
     */
    private fun convertImageToBitmap(image: Image): Bitmap? {
        return try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * displayMetrics.widthPixels
            
            val bitmap = Bitmap.createBitmap(
                displayMetrics.widthPixels + rowPadding / pixelStride,
                displayMetrics.heightPixels,
                Bitmap.Config.ARGB_8888
            )
            
            bitmap.copyPixelsFromBuffer(buffer)
            
            // 如果有行填充，需要裁剪
            if (rowPadding != 0) {
                Bitmap.createBitmap(
                    bitmap,
                    0, 0,
                    displayMetrics.widthPixels,
                    displayMetrics.heightPixels
                )
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "转换图像到Bitmap失败", e)
            null
        }
    }
    
    /**
     * 获取屏幕分辨率信息
     */
    fun getScreenResolution(): Triple<Int, Int, Float> {
        defaultDisplay.getMetrics(displayMetrics)
        return Triple(
            displayMetrics.widthPixels,
            displayMetrics.heightPixels,
            displayMetrics.density
        )
    }
    
    /**
     * 获取屏幕方向
     */
    fun getScreenOrientation(): Int {
        return defaultDisplay.rotation
    }
    
    /**
     * 获取屏幕密度DPI
     */
    fun getScreenDensityDpi(): Int {
        return displayMetrics.densityDpi
    }
    
    /**
     * 检查屏幕是否发生变化
     */
    fun hasScreenChanged(): Boolean {
        val currentMetrics = DisplayMetrics()
        defaultDisplay.getMetrics(currentMetrics)
        
        return currentMetrics.widthPixels != displayMetrics.widthPixels ||
               currentMetrics.heightPixels != displayMetrics.heightPixels ||
               currentMetrics.densityDpi != displayMetrics.densityDpi
    }
    
    /**
     * 更新屏幕参数
     */
    fun updateScreenMetrics() {
        defaultDisplay.getMetrics(displayMetrics)
        
        // 如果屏幕参数变化，需要重新设置VirtualDisplay
        if (isInitialized) {
            try {
                virtualDisplay?.release()
                imageReader?.close()
                setupImageReader()
                setupVirtualDisplay()
                Log.d(TAG, "屏幕参数更新: ${displayMetrics.widthPixels}x${displayMetrics.heightPixels}")
            } catch (e: Exception) {
                Log.e(TAG, "更新屏幕参数失败", e)
            }
        }
    }
    
    // ========== 私有方法 ==========
    
    /**
     * 设置ImageReader
     */
    private fun setupImageReader() {
        imageReader?.close()
        
        imageReader = ImageReader.newInstance(
            displayMetrics.widthPixels,
            displayMetrics.heightPixels,
            PixelFormat.RGBA_8888,
            2 // 缓冲区大小
        )
        
        Log.d(TAG, "ImageReader设置完成: ${displayMetrics.widthPixels}x${displayMetrics.heightPixels}")
    }
    
    /**
     * 设置VirtualDisplay
     */
    private fun setupVirtualDisplay() {
        virtualDisplay?.release()
        
        val projection = mediaProjection ?: return
        
        virtualDisplay = projection.createVirtualDisplay(
            "AuraFlowScreenCapture",
            displayMetrics.widthPixels,
            displayMetrics.heightPixels,
            displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            backgroundHandler
        )
        
        Log.d(TAG, "VirtualDisplay设置完成")
    }
    
    /**
     * 释放资源
     */
    fun cleanup() {
        try {
            isInitialized = false
            
            virtualDisplay?.release()
            virtualDisplay = null
            
            imageReader?.close()
            imageReader = null
            
            mediaProjection?.stop()
            mediaProjection = null
            
            handlerThread.quitSafely()
            scope.cancel()
            
            Log.d(TAG, "屏幕感知模块资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放屏幕感知资源失败", e)
        }
    }
    
    /**
     * 重新启动感知
     */
    fun restart(mediaProjection: MediaProjection) {
        cleanup()
        initialize(mediaProjection)
    }
    
    /**
     * 获取当前显示设备信息
     */
    fun getDisplayInfo(): String {
        val sb = StringBuilder()
        sb.append("屏幕尺寸: ${displayMetrics.widthPixels}x${displayMetrics.heightPixels}\n")
        sb.append("屏幕密度: ${displayMetrics.density} (${displayMetrics.densityDpi} dpi)\n")
        sb.append("屏幕方向: ${getScreenOrientationName()}\n")
        sb.append("物理尺寸: ${String.format("%.1f", displayMetrics.widthPixels / displayMetrics.xdpi)}\" x ${String.format("%.1f", displayMetrics.heightPixels / displayMetrics.ydpi)}\"")
        
        return sb.toString()
    }
    
    /**
     * 获取屏幕方向名称
     */
    private fun getScreenOrientationName(): String {
        return when (defaultDisplay.rotation) {
            0 -> "竖屏 (0°)"
            1 -> "横屏左转 (90°)"
            2 -> "竖屏倒置 (180°)"
            3 -> "横屏右转 (270°)"
            else -> "未知方向"
        }
    }
}