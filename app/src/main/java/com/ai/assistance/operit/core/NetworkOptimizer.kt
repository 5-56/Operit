package com.ai.assistance.operit.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 * 网络层性能优化组件
 * 提供智能缓存、请求优化、网络状态监控等功能
 */
class NetworkOptimizer private constructor(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "NetworkOptimizer"
        private const val CACHE_SIZE = 50L * 1024 * 1024 // 50MB
        private const val MAX_STALE_SECONDS = 7 * 24 * 60 * 60 // 7天
        private const val MAX_AGE_SECONDS = 5 * 60 // 5分钟
        
        @Volatile
        private var INSTANCE: NetworkOptimizer? = null
        
        fun getInstance(context: Context): NetworkOptimizer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkOptimizer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val requestMutex = Mutex()
    
    // 网络状态管理
    private val _networkState = MutableStateFlow(NetworkState.UNKNOWN)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()
    
    // 请求统计
    private val requestStats = ConcurrentHashMap<String, RequestStats>()
    private val _networkMetrics = MutableStateFlow(NetworkMetrics())
    val networkMetrics: StateFlow<NetworkMetrics> = _networkMetrics.asStateFlow()
    
    // HTTP缓存
    private val httpCache: Cache by lazy {
        val cacheDir = File(context.cacheDir, "http_cache")
        Cache(cacheDir, CACHE_SIZE)
    }
    
    // 优化的OkHttpClient
    val optimizedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cache(httpCache)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(CacheInterceptor())
            .addInterceptor(CompressionInterceptor())
            .addInterceptor(MetricsInterceptor())
            .addNetworkInterceptor(OnlineCacheInterceptor())
            .addInterceptor(OfflineCacheInterceptor())
            .addInterceptor(RetryInterceptor())
            .addInterceptor(RequestOptimizationInterceptor())
            .build()
    }
    
    init {
        initializeNetworkMonitoring()
        startMetricsCollection()
    }
    
    /**
     * 初始化网络监控
     */
    @RequiresApi(21)
    private fun initializeNetworkMonitoring() {
        try {
            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    updateNetworkState()
                    Log.d(TAG, "网络连接可用: $network")
                }
                
                override fun onLost(network: Network) {
                    updateNetworkState()
                    Log.d(TAG, "网络连接丢失: $network")
                }
                
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    updateNetworkState()
                }
            }
            
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build()
            
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            
            // 初始状态更新
            updateNetworkState()
            
        } catch (e: Exception) {
            Log.e(TAG, "网络监控初始化失败", e)
            _networkState.value = NetworkState.UNKNOWN
        }
    }
    
    /**
     * 更新网络状态
     */
    private fun updateNetworkState() {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        
        val state = when {
            capabilities == null -> NetworkState.OFFLINE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkState.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                when {
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> NetworkState.CELLULAR_UNLIMITED
                    else -> NetworkState.CELLULAR_METERED
                }
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkState.ETHERNET
            else -> NetworkState.OTHER
        }
        
        _networkState.value = state
        Log.d(TAG, "网络状态更新: $state")
    }
    
    /**
     * 开始指标收集
     */
    private fun startMetricsCollection() {
        scope.launch {
            // 每30秒更新一次网络指标
            while (true) {
                updateNetworkMetrics()
                kotlinx.coroutines.delay(30_000)
            }
        }
    }
    
    /**
     * 更新网络指标
     */
    private fun updateNetworkMetrics() {
        val totalRequests = requestStats.values.sumOf { it.count }
        val totalSuccessful = requestStats.values.sumOf { it.successCount }
        val totalFailed = requestStats.values.sumOf { it.failureCount }
        val averageLatency = if (totalRequests > 0) {
            requestStats.values.sumOf { it.totalLatency } / totalRequests
        } else 0.0
        
        val successRate = if (totalRequests > 0) {
            (totalSuccessful.toDouble() / totalRequests * 100).toFloat()
        } else 0f
        
        _networkMetrics.value = NetworkMetrics(
            totalRequests = totalRequests,
            successfulRequests = totalSuccessful,
            failedRequests = totalFailed,
            averageLatency = averageLatency,
            successRate = successRate,
            cacheHitRate = calculateCacheHitRate(),
            bytesSent = calculateBytesSent(),
            bytesReceived = calculateBytesReceived()
        )
    }
    
    /**
     * 计算缓存命中率
     */
    private fun calculateCacheHitRate(): Float {
        return try {
            val hits = httpCache.hitCount()
            val total = httpCache.requestCount()
            if (total > 0) (hits.toFloat() / total * 100) else 0f
        } catch (e: Exception) {
            0f
        }
    }
    
    /**
     * 计算发送字节数
     */
    private fun calculateBytesSent(): Long {
        return requestStats.values.sumOf { it.bytesSent }
    }
    
    /**
     * 计算接收字节数
     */
    private fun calculateBytesReceived(): Long {
        return requestStats.values.sumOf { it.bytesReceived }
    }
    
    /**
     * 记录请求统计
     */
    private suspend fun recordRequestStats(
        url: String,
        success: Boolean,
        latency: Long,
        bytesSent: Long = 0,
        bytesReceived: Long = 0
    ) {
        requestMutex.withLock {
            val host = try {
                java.net.URL(url).host
            } catch (e: Exception) {
                "unknown"
            }
            
            val stats = requestStats.getOrPut(host) { RequestStats() }
            stats.count++
            stats.totalLatency += latency
            stats.bytesSent += bytesSent
            stats.bytesReceived += bytesReceived
            
            if (success) {
                stats.successCount++
            } else {
                stats.failureCount++
            }
        }
    }
    
    /**
     * 获取请求建议
     */
    fun getRequestRecommendations(): RequestRecommendations {
        val currentState = _networkState.value
        
        return when (currentState) {
            NetworkState.WIFI, NetworkState.ETHERNET -> RequestRecommendations(
                enableImageLoading = true,
                enableVideoLoading = true,
                enableBackgroundSync = true,
                enablePrefetch = true,
                maxConcurrentRequests = 6,
                retryAttempts = 3
            )
            
            NetworkState.CELLULAR_UNLIMITED -> RequestRecommendations(
                enableImageLoading = true,
                enableVideoLoading = true,
                enableBackgroundSync = true,
                enablePrefetch = false,
                maxConcurrentRequests = 4,
                retryAttempts = 3
            )
            
            NetworkState.CELLULAR_METERED -> RequestRecommendations(
                enableImageLoading = true,
                enableVideoLoading = false,
                enableBackgroundSync = false,
                enablePrefetch = false,
                maxConcurrentRequests = 2,
                retryAttempts = 2
            )
            
            NetworkState.OFFLINE -> RequestRecommendations(
                enableImageLoading = false,
                enableVideoLoading = false,
                enableBackgroundSync = false,
                enablePrefetch = false,
                maxConcurrentRequests = 0,
                retryAttempts = 0
            )
            
            else -> RequestRecommendations()
        }
    }
    
    /**
     * 清理缓存
     */
    suspend fun clearCache() {
        try {
            httpCache.evictAll()
            Log.d(TAG, "HTTP缓存已清理")
        } catch (e: Exception) {
            Log.e(TAG, "清理HTTP缓存失败", e)
        }
    }
    
    /**
     * 获取缓存大小
     */
    fun getCacheSize(): Long {
        return try {
            httpCache.size()
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * 优化网络请求
     */
    fun optimizeForNetwork(request: Request.Builder): Request.Builder {
        val recommendations = getRequestRecommendations()
        
        return when (_networkState.value) {
            NetworkState.CELLULAR_METERED -> {
                // 计费网络：优化请求
                request.cacheControl(
                    CacheControl.Builder()
                        .maxAge(MAX_AGE_SECONDS, TimeUnit.SECONDS)
                        .maxStale(MAX_STALE_SECONDS, TimeUnit.SECONDS)
                        .build()
                )
            }
            
            NetworkState.OFFLINE -> {
                // 离线模式：仅使用缓存
                request.cacheControl(
                    CacheControl.Builder()
                        .onlyIfCached()
                        .maxStale(Integer.MAX_VALUE, TimeUnit.SECONDS)
                        .build()
                )
            }
            
            else -> {
                // 其他网络：正常缓存策略
                request.cacheControl(
                    CacheControl.Builder()
                        .maxAge(MAX_AGE_SECONDS, TimeUnit.SECONDS)
                        .build()
                )
            }
        }
    }
    
    // ==================== 拦截器实现 ====================
    
    /**
     * 缓存拦截器
     */
    private inner class CacheInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val response = chain.proceed(request)
            
            // 根据网络状态动态调整缓存策略
            val cacheControl = when (_networkState.value) {
                NetworkState.WIFI, NetworkState.ETHERNET -> {
                    CacheControl.Builder()
                        .maxAge(5, TimeUnit.MINUTES)
                        .build()
                }
                NetworkState.CELLULAR_METERED -> {
                    CacheControl.Builder()
                        .maxAge(30, TimeUnit.MINUTES)
                        .maxStale(24, TimeUnit.HOURS)
                        .build()
                }
                else -> {
                    CacheControl.Builder()
                        .maxAge(10, TimeUnit.MINUTES)
                        .build()
                }
            }
            
            return response.newBuilder()
                .header("Cache-Control", cacheControl.toString())
                .build()
        }
    }
    
    /**
     * 压缩拦截器
     */
    private inner class CompressionInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request().newBuilder()
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Accept", "application/json, text/plain, */*")
                .build()
            
            return chain.proceed(request)
        }
    }
    
    /**
     * 指标收集拦截器
     */
    private inner class MetricsInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val startTime = System.currentTimeMillis()
            
            return try {
                val response = chain.proceed(request)
                val latency = System.currentTimeMillis() - startTime
                
                scope.launch {
                    recordRequestStats(
                        url = request.url.toString(),
                        success = response.isSuccessful,
                        latency = latency,
                        bytesSent = request.body?.contentLength() ?: 0,
                        bytesReceived = response.body?.contentLength() ?: 0
                    )
                }
                
                response
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - startTime
                scope.launch {
                    recordRequestStats(
                        url = request.url.toString(),
                        success = false,
                        latency = latency
                    )
                }
                throw e
            }
        }
    }
    
    /**
     * 在线缓存拦截器
     */
    private inner class OnlineCacheInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            
            return if (_networkState.value != NetworkState.OFFLINE) {
                val maxAge = when (_networkState.value) {
                    NetworkState.WIFI, NetworkState.ETHERNET -> 60 // 1分钟
                    NetworkState.CELLULAR_UNLIMITED -> 300 // 5分钟
                    NetworkState.CELLULAR_METERED -> 1800 // 30分钟
                    else -> 300
                }
                
                response.newBuilder()
                    .header("Cache-Control", "public, max-age=$maxAge")
                    .build()
            } else {
                response
            }
        }
    }
    
    /**
     * 离线缓存拦截器
     */
    private inner class OfflineCacheInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            return if (_networkState.value == NetworkState.OFFLINE) {
                val request = chain.request().newBuilder()
                    .cacheControl(
                        CacheControl.Builder()
                            .onlyIfCached()
                            .maxStale(7, TimeUnit.DAYS)
                            .build()
                    )
                    .build()
                chain.proceed(request)
            } else {
                chain.proceed(chain.request())
            }
        }
    }
    
    /**
     * 重试拦截器
     */
    private inner class RetryInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val recommendations = getRequestRecommendations()
            val maxRetries = recommendations.retryAttempts
            var attempt = 0
            
            while (attempt <= maxRetries) {
                try {
                    val response = chain.proceed(chain.request())
                    if (response.isSuccessful || attempt == maxRetries) {
                        return response
                    }
                    response.close()
                } catch (e: Exception) {
                    if (attempt == maxRetries) {
                        throw e
                    }
                }
                
                attempt++
                // 指数退避
                val delay = min(1000L * (1L shl attempt), 10000L)
                Thread.sleep(delay)
            }
            
            throw RuntimeException("重试次数已达上限")
        }
    }
    
    /**
     * 请求优化拦截器
     */
    private inner class RequestOptimizationInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            val optimizedRequest = optimizeForNetwork(originalRequest.newBuilder()).build()
            
            return chain.proceed(optimizedRequest)
        }
    }
    
    // ==================== 数据类定义 ====================
    
    enum class NetworkState {
        WIFI,
        CELLULAR_METERED,
        CELLULAR_UNLIMITED,
        ETHERNET,
        OFFLINE,
        OTHER,
        UNKNOWN
    }
    
    data class NetworkMetrics(
        val totalRequests: Long = 0,
        val successfulRequests: Long = 0,
        val failedRequests: Long = 0,
        val averageLatency: Double = 0.0,
        val successRate: Float = 0f,
        val cacheHitRate: Float = 0f,
        val bytesSent: Long = 0,
        val bytesReceived: Long = 0
    )
    
    data class RequestStats(
        var count: Long = 0,
        var successCount: Long = 0,
        var failureCount: Long = 0,
        var totalLatency: Long = 0,
        var bytesSent: Long = 0,
        var bytesReceived: Long = 0
    )
    
    data class RequestRecommendations(
        val enableImageLoading: Boolean = true,
        val enableVideoLoading: Boolean = true,
        val enableBackgroundSync: Boolean = true,
        val enablePrefetch: Boolean = false,
        val maxConcurrentRequests: Int = 4,
        val retryAttempts: Int = 3
    )
    
    /**
     * 清理资源
     */
    fun cleanup() {
        try {
            httpCache.close()
            requestStats.clear()
            Log.d(TAG, "网络优化器资源已清理")
        } catch (e: Exception) {
            Log.e(TAG, "清理网络优化器资源失败", e)
        }
    }
}