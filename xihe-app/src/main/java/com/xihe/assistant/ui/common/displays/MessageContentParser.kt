package com.xihe.assistant.ui.common.displays

import kotlin.text.Regex

/**
 * 消息内容解析器
 * 解析和处理消息内容
 */
object MessageContentParser {
    val namePattern: Regex = Regex("<tool name=\"([^\"]+)\"")
    val toolParamPattern: Regex = Regex("<parameter name=\"([^\"]+)\">([^<]*)</parameter>")
}