package com.ai.assistance.operit.core.agent

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

/**
 * AgentScriptSaver 负责自动保存 agent 生成/优化的脚本，并自动 git 操作。
 */
object AgentScriptSaver {
    private val scriptDir = File("agent_scripts")
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss")

    /**
     * 保存脚本到本地目录
     * @param script 脚本内容
     * @param userRequest 用户需求摘要
     * @return 保存的文件路径
     */
    fun saveScript(script: String, userRequest: String): String {
        if (!scriptDir.exists()) scriptDir.mkdirs()
        val safeName = userRequest.take(20).replace("[\\s/\\\\]".toRegex(), "_")
        val fileName = "agent_${dateFormat.format(Date())}_$safeName.js"
        val file = File(scriptDir, fileName)
        file.writeText(script)
        return file.absolutePath
    }

    /**
     * 自动 git add/commit/push
     * @param filePath 脚本文件路径
     * @param message 提交信息
     */
    fun autoGitUpload(filePath: String, message: String = "auto: agent 脚本更新") {
        try {
            val dir = scriptDir.absolutePath
            Runtime.getRuntime().exec("git add $filePath", null, File(dir)).waitFor()
            Runtime.getRuntime().exec("git commit -m '$message' $filePath", null, File(dir)).waitFor()
            Runtime.getRuntime().exec("git push", null, File(dir)).waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 获取所有历史脚本文件（按时间倒序）
     */
    fun listHistory(): List<File> = scriptDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()

    /**
     * 读取指定历史脚本内容
     */
    fun readScript(file: File): String = file.readText()

    /**
     * 删除指定历史脚本
     */
    fun deleteScript(file: File) { file.delete() }

    /**
     * 回滚到指定历史脚本（复制为最新）
     */
    fun rollbackTo(file: File): String {
        val newFile = File(scriptDir, "agent_rollback_${System.currentTimeMillis()}.js")
        file.copyTo(newFile)
        return newFile.absolutePath
    }
}