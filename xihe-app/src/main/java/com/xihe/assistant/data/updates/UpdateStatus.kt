package com.xihe.assistant.data.updates

/**
 * 更新状态
 */
sealed class UpdateStatus {
    data class Available(val newVersion: String, val message: String) : UpdateStatus()
    object UpToDate : UpdateStatus()
    object Error : UpdateStatus()
}