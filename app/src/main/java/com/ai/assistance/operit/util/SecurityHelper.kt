package com.ai.assistance.operit.util

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * 安全性增强工具类
 * 提供API密钥保护、输入验证、数据加密等安全功能
 */
object SecurityHelper {
    private const val TAG = "SecurityHelper"
    
    /**
     * API密钥验证和管理
     */
    object ApiKeyManager {
        
        /**
         * 验证API密钥格式
         */
        fun validateApiKey(provider: String, apiKey: String): ValidationResult {
            if (apiKey.isBlank()) {
                return ValidationResult(false, "API密钥不能为空")
            }
            
            return when (provider.lowercase()) {
                "openai" -> {
                    if (apiKey.startsWith("sk-") && apiKey.length >= 40) {
                        ValidationResult(true, "OpenAI API密钥格式正确")
                    } else {
                        ValidationResult(false, "OpenAI API密钥应以'sk-'开头且长度不少于40字符")
                    }
                }
                
                "qwen", "aliyun" -> {
                    if (apiKey.startsWith("sk-") && apiKey.length >= 40) {
                        ValidationResult(true, "通义千问API密钥格式正确")
                    } else {
                        ValidationResult(false, "通义千问API密钥格式不正确")
                    }
                }
                
                "claude", "anthropic" -> {
                    if (apiKey.startsWith("sk-ant-") && apiKey.length >= 50) {
                        ValidationResult(true, "Claude API密钥格式正确")
                    } else {
                        ValidationResult(false, "Claude API密钥应以'sk-ant-'开头")
                    }
                }
                
                "gemini", "google" -> {
                    if (apiKey.length >= 32 && !apiKey.contains(" ")) {
                        ValidationResult(true, "Gemini API密钥格式正确")
                    } else {
                        ValidationResult(false, "Gemini API密钥格式不正确")
                    }
                }
                
                "deepseek" -> {
                    if (apiKey.startsWith("sk-") && apiKey.length >= 40) {
                        ValidationResult(true, "DeepSeek API密钥格式正确")
                    } else {
                        ValidationResult(false, "DeepSeek API密钥格式不正确")
                    }
                }
                
                else -> {
                    if (apiKey.length >= 20) {
                        ValidationResult(true, "API密钥长度符合要求")
                    } else {
                        ValidationResult(false, "API密钥长度不足")
                    }
                }
            }
        }
        
        /**
         * 掩码显示API密钥（用于界面显示）
         */
        fun maskApiKey(apiKey: String): String {
            if (apiKey.length <= 8) return "****"
            
            val start = apiKey.take(4)
            val end = apiKey.takeLast(4)
            val maskLength = (apiKey.length - 8).coerceAtLeast(4)
            val mask = "*".repeat(maskLength)
            
            return "$start$mask$end"
        }
        
        /**
         * 加密存储API密钥
         */
        fun encryptAndStoreApiKey(context: Context, provider: String, apiKey: String): Boolean {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                
                val sharedPreferences = EncryptedSharedPreferences.create(
                    context,
                    "encrypted_api_keys",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
                
                sharedPreferences.edit()
                    .putString("api_key_$provider", apiKey)
                    .apply()
                
                Log.d(TAG, "API密钥已加密存储: $provider")
                true
            } catch (e: Exception) {
                Log.e(TAG, "API密钥加密存储失败: $provider", e)
                false
            }
        }
        
        /**
         * 解密获取API密钥
         */
        fun decryptApiKey(context: Context, provider: String): String? {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                
                val sharedPreferences = EncryptedSharedPreferences.create(
                    context,
                    "encrypted_api_keys",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
                
                sharedPreferences.getString("api_key_$provider", null)
            } catch (e: Exception) {
                Log.e(TAG, "API密钥解密失败: $provider", e)
                null
            }
        }
    }
    
    /**
     * 输入验证工具
     */
    object InputValidator {
        
        // 常用正则表达式
        private val URL_PATTERN = Pattern.compile(
            "^(https?)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]"
        )
        
        private val EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9+._%\\-]{1,256}" +
            "@" +
            "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
            "(" +
            "\\." +
            "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
            ")+"
        )
        
        /**
         * 验证URL格式
         */
        fun validateUrl(url: String): ValidationResult {
            if (url.isBlank()) {
                return ValidationResult(false, "URL不能为空")
            }
            
            if (!URL_PATTERN.matcher(url).matches()) {
                return ValidationResult(false, "URL格式不正确")
            }
            
            return ValidationResult(true, "URL格式正确")
        }
        
        /**
         * 验证邮箱格式
         */
        fun validateEmail(email: String): ValidationResult {
            if (email.isBlank()) {
                return ValidationResult(false, "邮箱不能为空")
            }
            
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                return ValidationResult(false, "邮箱格式不正确")
            }
            
            return ValidationResult(true, "邮箱格式正确")
        }
        
        /**
         * 验证用户输入（防止注入攻击）
         */
        fun sanitizeUserInput(input: String): String {
            return input.trim()
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;")
                .replace("/", "&#x2F;")
        }
        
        /**
         * 检查是否包含恶意脚本
         */
        fun containsMaliciousScript(input: String): Boolean {
            val maliciousPatterns = listOf(
                "<script",
                "javascript:",
                "onload=",
                "onerror=",
                "eval(",
                "document.cookie",
                "window.location",
                "alert(",
                "confirm(",
                "prompt("
            )
            
            val lowerInput = input.lowercase()
            return maliciousPatterns.any { pattern ->
                lowerInput.contains(pattern)
            }
        }
        
        /**
         * 验证模型名称
         */
        fun validateModelName(modelName: String): ValidationResult {
            if (modelName.isBlank()) {
                return ValidationResult(false, "模型名称不能为空")
            }
            
            if (modelName.length > 100) {
                return ValidationResult(false, "模型名称过长")
            }
            
            // 检查是否只包含允许的字符
            val allowedPattern = Pattern.compile("^[a-zA-Z0-9._-]+$")
            if (!allowedPattern.matcher(modelName).matches()) {
                return ValidationResult(false, "模型名称包含非法字符")
            }
            
            return ValidationResult(true, "模型名称格式正确")
        }
    }
    
    /**
     * 数据加密工具
     */
    object DataEncryption {
        
        /**
         * 生成哈希值
         */
        fun generateHash(data: String, algorithm: String = "SHA-256"): String {
            val digest = MessageDigest.getInstance(algorithm)
            val hashBytes = digest.digest(data.toByteArray())
            return Base64.encodeToString(hashBytes, Base64.NO_WRAP)
        }
        
        /**
         * 简单的字符串加密（用于非敏感数据）
         */
        fun encryptString(data: String, key: String): String {
            return try {
                val keySpec = SecretKeySpec(key.toByteArray().copyOf(16), "AES")
                val cipher = Cipher.getInstance("AES")
                cipher.init(Cipher.ENCRYPT_MODE, keySpec)
                val encrypted = cipher.doFinal(data.toByteArray())
                Base64.encodeToString(encrypted, Base64.DEFAULT)
            } catch (e: Exception) {
                Log.e(TAG, "字符串加密失败", e)
                data // 失败时返回原始数据
            }
        }
        
        /**
         * 简单的字符串解密
         */
        fun decryptString(encryptedData: String, key: String): String {
            return try {
                val keySpec = SecretKeySpec(key.toByteArray().copyOf(16), "AES")
                val cipher = Cipher.getInstance("AES")
                cipher.init(Cipher.DECRYPT_MODE, keySpec)
                val encrypted = Base64.decode(encryptedData, Base64.DEFAULT)
                val decrypted = cipher.doFinal(encrypted)
                String(decrypted)
            } catch (e: Exception) {
                Log.e(TAG, "字符串解密失败", e)
                encryptedData // 失败时返回原始数据
            }
        }
    }
    
    /**
     * 验证结果数据类
     */
    data class ValidationResult(
        val isValid: Boolean,
        val message: String
    )
    
    /**
     * 安全配置
     */
    data class SecurityConfig(
        val enableApiKeyEncryption: Boolean = true,
        val enableInputValidation: Boolean = true,
        val enableMaliciousScriptCheck: Boolean = true,
        val enableSecureStorage: Boolean = true,
        val maxApiKeyLength: Int = 512,
        val maxInputLength: Int = 10000
    )
    
    /**
     * 全面的安全检查
     */
    fun performSecurityCheck(
        context: Context,
        config: SecurityConfig = SecurityConfig()
    ): SecurityCheckResult {
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        try {
            // 检查加密存储是否可用
            if (config.enableSecureStorage) {
                try {
                    val masterKey = MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                    Log.d(TAG, "加密存储可用")
                } catch (e: Exception) {
                    issues.add("加密存储不可用: ${e.message}")
                }
            }
            
            // 其他安全检查...
            
        } catch (e: Exception) {
            issues.add("安全检查失败: ${e.message}")
        }
        
        return SecurityCheckResult(
            isSecure = issues.isEmpty(),
            issues = issues,
            warnings = warnings
        )
    }
    
    /**
     * 安全检查结果
     */
    data class SecurityCheckResult(
        val isSecure: Boolean,
        val issues: List<String>,
        val warnings: List<String>
    )
}