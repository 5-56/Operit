package com.ai.assistance.operit.core.tools.defaultTool

import android.content.Context
import com.ai.assistance.operit.core.tools.FFmpegResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.ui.permissions.ToolCategory
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File

/** FFmpeg工具执行器 提供媒体文件处理能力，包括转换、裁剪、合并等功能 */
class FFmpegToolExecutor(private val context: Context) : ToolExecutor {
    companion object {
        private const val TAG = "FFmpegToolExecutor"
    }

    override fun invoke(tool: AITool): ToolResult {
        val command = tool.parameters.find { it.name == "command" }?.value ?: ""

        if (command.isEmpty()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "命令不能为空"
            )
        }

        return try {
            val startTime = System.currentTimeMillis()

            // 执行FFmpeg命令
            val session = FFmpegKit.execute(command)
            val returnCode = session.returnCode
            val output = session.output ?: ""
            val duration = System.currentTimeMillis() - startTime

            if (ReturnCode.isSuccess(returnCode)) {
                ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                FFmpegResultData(
                                        command = command,
                                        returnCode = returnCode.value,
                                        output = output,
                                        duration = duration
                                )
                )
            } else if (ReturnCode.isCancel(returnCode)) {
                ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "FFmpeg命令被取消"
                )
            } else {
                ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "FFmpeg执行失败，返回码: ${returnCode.value}\n输出:\n$output"
                )
            }
        } catch (e: Exception) {
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "FFmpeg执行异常: ${e.message}"
            )
        }
    }

    override fun validateParameters(tool: AITool): ToolValidationResult {
        val command = tool.parameters.find { it.name == "command" }?.value

        if (command.isNullOrEmpty()) {
            return ToolValidationResult(valid = false, errorMessage = "必须提供command参数")
        }

        return ToolValidationResult(valid = true)
    }

    override fun getCategory(): ToolCategory {
        return ToolCategory.FILE_WRITE
    }
}

/** FFmpeg信息工具执行器 获取有关系统FFmpeg配置的信息 */
class FFmpegInfoToolExecutor : ToolExecutor {
    companion object {
        private const val TAG = "FFmpegInfoToolExecutor"
    }

    override fun invoke(tool: AITool): ToolResult {
        return try {
            val info = StringBuilder()
            val startTime = System.currentTimeMillis()

            // 获取FFmpeg版本信息
            info.appendLine("FFmpeg版本: ${FFmpegKitConfig.getVersion()}")
            info.appendLine("构建配置: ${FFmpegKitConfig.getBuildDate()}")

            // 列出支持的编解码器
            val codecsSession = FFmpegKit.execute("-codecs")
            val codecsOutput = codecsSession.output ?: ""
            val duration = System.currentTimeMillis() - startTime

            info.appendLine("\n支持的编解码器:")
            info.appendLine(codecsOutput)

            ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                            FFmpegResultData(
                                    command = "-codecs",
                                    returnCode = codecsSession.returnCode.value,
                                    output = info.toString(),
                                    duration = duration
                            )
            )
        } catch (e: Exception) {
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "获取FFmpeg信息失败: ${e.message}"
            )
        }
    }

    override fun validateParameters(tool: AITool): ToolValidationResult {
        // 不需要参数
        return ToolValidationResult(valid = true)
    }

    override fun getCategory(): ToolCategory {
        return ToolCategory.FILE_READ
    }
}

/** FFmpeg转换视频工具执行器 提供一个简化的接口用于常见的视频转换操作 */
class FFmpegConvertToolExecutor(private val context: Context) : ToolExecutor {
    companion object {
        private const val TAG = "FFmpegConvertToolExecutor"
    }

    override fun invoke(tool: AITool): ToolResult {
        val inputPath = tool.parameters.find { it.name == "input_path" }?.value ?: ""
        val outputPath = tool.parameters.find { it.name == "output_path" }?.value ?: ""
        val format = tool.parameters.find { it.name == "format" }?.value
        val resolution = tool.parameters.find { it.name == "resolution" }?.value
        val bitrate = tool.parameters.find { it.name == "bitrate" }?.value
        val audioCodec = tool.parameters.find { it.name == "audio_codec" }?.value
        val videoCodec = tool.parameters.find { it.name == "video_codec" }?.value

        if (inputPath.isEmpty() || outputPath.isEmpty()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "输入路径和输出路径不能为空"
            )
        }

        val inputFile = File(inputPath)
        if (!inputFile.exists()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "输入文件不存在: $inputPath"
            )
        }

        // 构建FFmpeg命令
        val commandBuilder = StringBuilder("-i \"$inputPath\"")

        // 添加可选参数
        if (!videoCodec.isNullOrEmpty()) {
            commandBuilder.append(" -c:v $videoCodec")
        }

        if (!audioCodec.isNullOrEmpty()) {
            commandBuilder.append(" -c:a $audioCodec")
        }

        if (!resolution.isNullOrEmpty()) {
            commandBuilder.append(" -s $resolution")
        }

        if (!bitrate.isNullOrEmpty()) {
            commandBuilder.append(" -b:v $bitrate")
        }

        // 添加输出文件
        commandBuilder.append(" \"$outputPath\"")

        val command = commandBuilder.toString()

        return try {
            val startTime = System.currentTimeMillis()

            // 执行FFmpeg命令
            val session = FFmpegKit.execute(command)
            val returnCode = session.returnCode
            val output = session.output ?: ""
            val duration = System.currentTimeMillis() - startTime

            if (ReturnCode.isSuccess(returnCode)) {
                // 获取输出文件的媒体信息
                val mediaSession = FFprobeKit.getMediaInformation(outputPath)
                val mediaInfo = mediaSession?.mediaInformation

                val ffmpegResult =
                        if (mediaInfo != null) {
                            val videoStreams =
                                    mediaInfo
                                            .streams
                                            .filter { it.type.equals("video", ignoreCase = true) }
                                            .map { stream ->
                                                FFmpegResultData.StreamInfo(
                                                        index = stream.index?.toInt() ?: 0,
                                                        codecType = stream.type ?: "unknown",
                                                        codecName = stream.codec ?: "unknown",
                                                        resolution =
                                                                "${stream.width}x${stream.height}",
                                                        frameRate =
                                                                null // We'll get this from FFprobe
                                                        // if needed
                                                        )
                                            }
                                            .toMutableList()

                            val audioStreams =
                                    mediaInfo
                                            .streams
                                            .filter { it.type.equals("audio", ignoreCase = true) }
                                            .map { stream ->
                                                FFmpegResultData.StreamInfo(
                                                        index = stream.index?.toInt() ?: 0,
                                                        codecType = stream.type ?: "unknown",
                                                        codecName = stream.codec ?: "unknown",
                                                        sampleRate =
                                                                null, // We'll get this from FFprobe
                                                        // if needed
                                                        channels =
                                                                null // We'll get this from FFprobe
                                                        // if needed
                                                        )
                                            }
                                            .toMutableList()

                            // Get additional media information using FFprobe
                            val ffprobeSession = FFprobeKit.getMediaInformation(outputPath)
                            val ffprobeInfo = ffprobeSession?.mediaInformation

                            if (ffprobeInfo != null) {
                                // Update stream information with FFprobe data
                                ffprobeInfo.streams.forEach { probeStream ->
                                    when (probeStream.type) {
                                        "video" -> {
                                            val index =
                                                    videoStreams.indexOfFirst {
                                                        it.index == probeStream.index?.toInt()
                                                    }
                                            if (index != -1) {
                                                val stream = videoStreams[index]
                                                videoStreams[index] =
                                                        stream.copy(
                                                                frameRate =
                                                                        probeStream
                                                                                .allProperties
                                                                                ?.get(
                                                                                        "r_frame_rate"
                                                                                )
                                                                                ?.toString()
                                                        )
                                            }
                                        }
                                        "audio" -> {
                                            val index =
                                                    audioStreams.indexOfFirst {
                                                        it.index == probeStream.index?.toInt()
                                                    }
                                            if (index != -1) {
                                                val stream = audioStreams[index]
                                                audioStreams[index] =
                                                        stream.copy(
                                                                sampleRate =
                                                                        probeStream
                                                                                .allProperties
                                                                                ?.get("sample_rate")
                                                                                ?.toString(),
                                                                channels =
                                                                        probeStream
                                                                                .allProperties
                                                                                ?.get("channels")
                                                                                ?.toString()
                                                                                ?.toIntOrNull()
                                                        )
                                            }
                                        }
                                    }
                                }
                            }

                            FFmpegResultData(
                                    command = command,
                                    returnCode = returnCode.value,
                                    output = output,
                                    duration = duration,
                                    outputFile = outputPath,
                                    mediaInfo =
                                            FFmpegResultData.MediaInfo(
                                                    format = mediaInfo.format ?: "unknown",
                                                    duration = mediaInfo.duration ?: "0",
                                                    bitrate = mediaInfo.bitrate ?: "0",
                                                    videoStreams = videoStreams,
                                                    audioStreams = audioStreams
                                            )
                            )
                        } else {
                            FFmpegResultData(
                                    command = command,
                                    returnCode = returnCode.value,
                                    output = output,
                                    duration = duration,
                                    outputFile = outputPath
                            )
                        }

                ToolResult(toolName = tool.name, success = true, result = ffmpegResult)
            } else {
                ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "视频转换失败，返回码: ${returnCode.value}\n命令: $command\n输出:\n$output"
                )
            }
        } catch (e: Exception) {
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "视频转换异常: ${e.message}\n命令: $command"
            )
        }
    }

    override fun validateParameters(tool: AITool): ToolValidationResult {
        val inputPath = tool.parameters.find { it.name == "input_path" }?.value
        val outputPath = tool.parameters.find { it.name == "output_path" }?.value

        if (inputPath.isNullOrEmpty()) {
            return ToolValidationResult(valid = false, errorMessage = "必须提供input_path参数")
        }

        if (outputPath.isNullOrEmpty()) {
            return ToolValidationResult(valid = false, errorMessage = "必须提供output_path参数")
        }

        return ToolValidationResult(valid = true)
    }

    override fun getCategory(): ToolCategory {
        return ToolCategory.FILE_WRITE
    }
}
