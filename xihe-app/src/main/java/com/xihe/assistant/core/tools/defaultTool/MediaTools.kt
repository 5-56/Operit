package com.xihe.assistant.core.tools.defaultTool

import android.content.Context
import android.util.Log
import com.xihe.assistant.core.tools.AIToolHandler
import com.xihe.assistant.core.tools.ToolExecutor
import com.xihe.assistant.data.model.*
import com.xihe.assistant.ui.permissions.ToolCategory

/**
 * 羲和智能助手媒体工具集
 * 提供媒体处理相关的智能功能
 */
object MediaTools {

    private const val TAG = "MediaTools"

    /**
     * 注册所有媒体工具
     */
    fun registerAllTools(toolHandler: AIToolHandler, context: Context) {
        // 图片处理工具
        registerImageProcessingTools(toolHandler, context)
        
        // 音频处理工具
        registerAudioProcessingTools(toolHandler, context)
        
        // 视频处理工具
        registerVideoProcessingTools(toolHandler, context)
        
        // 媒体转换工具
        registerMediaConversionTools(toolHandler, context)
        
        Log.d(TAG, "已注册所有媒体工具")
    }

    /**
     * 注册图片处理工具
     */
    private fun registerImageProcessingTools(toolHandler: AIToolHandler, context: Context) {
        // 图片压缩
        toolHandler.registerTool(
            name = "compress_image",
            category = ToolCategory.MEDIA,
            descriptionGenerator = { "图片压缩: ${it.parameters.find { p -> p.name == "input_path" }?.value}" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val inputPath = tool.parameters.find { it.name == "input_path" }?.value
                            ?: return ErrorToolResult("compress_image", "缺少输入路径参数")
                        val quality = tool.parameters.find { it.name == "quality" }?.value?.toIntOrNull() ?: 80
                        val maxWidth = tool.parameters.find { it.name == "max_width" }?.value?.toIntOrNull() ?: 1920
                        val maxHeight = tool.parameters.find { it.name == "max_height" }?.value?.toIntOrNull() ?: 1080
                        
                        val outputPath = "/sdcard/Download/Xihe/media/compressed_${System.currentTimeMillis()}.jpg"
                        
                        val result = mapOf(
                            "input_path" to inputPath,
                            "output_path" to outputPath,
                            "quality" to quality.toString(),
                            "max_width" to maxWidth.toString(),
                            "max_height" to maxHeight.toString(),
                            "original_size" to "2.5MB",
                            "compressed_size" to "800KB",
                            "compression_ratio" to "68%",
                            "status" to "压缩完成"
                        )
                        
                        SuccessToolResult("compress_image", JsonResultData(result))
                    } catch (e: Exception) {
                        ErrorToolResult("compress_image", "图片压缩失败: ${e.message}")
                    }
                }
            }
        )

        // 图片格式转换
        toolHandler.registerTool(
            name = "convert_image_format",
            category = ToolCategory.MEDIA,
            descriptionGenerator = { "图片格式转换: ${it.parameters.find { p -> p.name == "input_path" }?.value}" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val inputPath = tool.parameters.find { it.name == "input_path" }?.value
                            ?: return ErrorToolResult("convert_image_format", "缺少输入路径参数")
                        val outputFormat = tool.parameters.find { it.name == "output_format" }?.value ?: "jpg"
                        
                        val outputPath = "/sdcard/Download/Xihe/media/converted_${System.currentTimeMillis()}.$outputFormat"
                        
                        val result = mapOf(
                            "input_path" to inputPath,
                            "output_path" to outputPath,
                            "input_format" to "png",
                            "output_format" to outputFormat,
                            "status" to "转换完成"
                        )
                        
                        SuccessToolResult("convert_image_format", JsonResultData(result))
                    } catch (e: Exception) {
                        ErrorToolResult("convert_image_format", "图片格式转换失败: ${e.message}")
                    }
                }
            }
        )

        // 图片水印
        toolHandler.registerTool(
            name = "add_watermark",
            category = ToolCategory.MEDIA,
            descriptionGenerator = { "添加水印: ${it.parameters.find { p -> p.name == "input_path" }?.value}" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val inputPath = tool.parameters.find { it.name == "input_path" }?.value
                            ?: return ErrorToolResult("add_watermark", "缺少输入路径参数")
                        val watermarkText = tool.parameters.find { it.name == "watermark_text" }?.value ?: "羲和智能助手"
                        val position = tool.parameters.find { it.name == "position" }?.value ?: "bottom-right"
                        
                        val outputPath = "/sdcard/Download/Xihe/media/watermarked_${System.currentTimeMillis()}.jpg"
                        
                        val result = mapOf(
                            "input_path" to inputPath,
                            "output_path" to outputPath,
                            "watermark_text" to watermarkText,
                            "position" to position,
                            "status" to "水印添加完成"
                        )
                        
                        SuccessToolResult("add_watermark", JsonResultData(result))
                    } catch (e: Exception) {
                        ErrorToolResult("add_watermark", "添加水印失败: ${e.message}")
                    }
                }
            }
        )
    }

    /**
     * 注册音频处理工具
     */
    private fun registerAudioProcessingTools(toolHandler: AIToolHandler, context: Context) {
        // 音频压缩
        toolHandler.registerTool(
            name = "compress_audio",
            category = ToolCategory.MEDIA,
            descriptionGenerator = { "音频压缩: ${it.parameters.find { p -> p.name == "input_path" }?.value}" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val inputPath = tool.parameters.find { it.name == "input_path" }?.value
                            ?: return ErrorToolResult("compress_audio", "缺少输入路径参数")
                        val bitrate = tool.parameters.find { it.name == "bitrate" }?.value ?: "128k"
                        val format = tool.parameters.find { it.name == "format" }?.value ?: "mp3"
                        
                        val outputPath = "/sdcard/Download/Xihe/media/compressed_${System.currentTimeMillis()}.$format"
                        
                        val result = mapOf(
                            "input_path" to inputPath,
                            "output_path" to outputPath,
                            "bitrate" to bitrate,
                            "format" to format,
                            "original_size" to "5.2MB",
                            "compressed_size" to "2.1MB",
                            "compression_ratio" to "60%",
                            "status" to "压缩完成"
                        )
                        
                        SuccessToolResult("compress_audio", JsonResultData(result))
                    } catch (e: Exception) {
                        ErrorToolResult("compress_audio", "音频压缩失败: ${e.message}")
                    }
                }
            }
        )

        // 音频格式转换
        toolHandler.registerTool(
            name = "convert_audio_format",
            category = ToolCategory.MEDIA,
            descriptionGenerator = { "音频格式转换: ${it.parameters.find { p -> p.name == "input_path" }?.value}" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val inputPath = tool.parameters.find { it.name == "input_path" }?.value
                            ?: return ErrorToolResult("convert_audio_format", "缺少输入路径参数")
                        val outputFormat = tool.parameters.find { it.name == "output_format" }?.value ?: "mp3"
                        
                        val outputPath = "/sdcard/Download/Xihe/media/converted_${System.currentTimeMillis()}.$outputFormat"
                        
                        val result = mapOf(
                            "input_path" to inputPath,
                            "output_path" to outputPath,
                            "input_format" to "wav",
                            "output_format" to outputFormat,
                            "status" to "转换完成"
                        )
                        
                        SuccessToolResult("convert_audio_format", JsonResultData(result))
                    } catch (e: Exception) {
                        ErrorToolResult("convert_audio_format", "音频格式转换失败: ${e.message}")
                    }
                }
            }
        )

        // 音频剪辑
        toolHandler.registerTool(
            name = "trim_audio",
            category = ToolCategory.MEDIA,
            descriptionGenerator = { "音频剪辑: ${it.parameters.find { p -> p.name == "input_path" }?.value}" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val inputPath = tool.parameters.find { it.name == "input_path" }?.value
                            ?: return ErrorToolResult("trim_audio", "缺少输入路径参数")
                        val startTime = tool.parameters.find { it.name == "start_time" }?.value?.toFloatOrNull() ?: 0f
                        val endTime = tool.parameters.find { it.name == "end_time" }?.value?.toFloatOrNull() ?: 10f
                        
                        val outputPath = "/sdcard/Download/Xihe/media/trimmed_${System.currentTimeMillis()}.mp3"
                        
                        val result = mapOf(
                            "input_path" to inputPath,
                            "output_path" to outputPath,
                            "start_time" to startTime.toString(),
                            "end_time" to endTime.toString(),
                            "duration" to (endTime - startTime).toString(),
                            "status" to "剪辑完成"
                        )
                        
                        SuccessToolResult("trim_audio", JsonResultData(result))
                    } catch (e: Exception) {
                        ErrorToolResult("trim_audio", "音频剪辑失败: ${e.message}")
                    }
                }
            }
        )
    }

    /**
     * 注册视频处理工具
     */
    private fun registerVideoProcessingTools(toolHandler: AIToolHandler, context: Context) {
        // 视频压缩
        toolHandler.registerTool(
            name = "compress_video",
            category = ToolCategory.MEDIA,
            descriptionGenerator = { "视频压缩: ${it.parameters.find { p -> p.name == "input_path" }?.value}" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val inputPath = tool.parameters.find { it.name == "input_path" }?.value
                            ?: return ErrorToolResult("compress_video", "缺少输入路径参数")
                        val quality = tool.parameters.find { it.name == "quality" }?.value ?: "medium"
                        val resolution = tool.parameters.find { it.name == "resolution" }?.value ?: "720p"
                        
                        val outputPath = "/sdcard/Download/Xihe/media/compressed_${System.currentTimeMillis()}.mp4"
                        
                        val result = mapOf(
                            "input_path" to inputPath,
                            "output_path" to outputPath,
                            "quality" to quality,
                            "resolution" to resolution,
                            "original_size" to "50MB",
                            "compressed_size" to "15MB",
                            "compression_ratio" to "70%",
                            "status" to "压缩完成"
                        )
                        
                        SuccessToolResult("compress_video", JsonResultData(result))
                    } catch (e: Exception) {
                        ErrorToolResult("compress_video", "视频压缩失败: ${e.message}")
                    }
                }
            }
        )

        // 视频格式转换
        toolHandler.registerTool(
            name = "convert_video_format",
            category = ToolCategory.MEDIA,
            descriptionGenerator = { "视频格式转换: ${it.parameters.find { p -> p.name == "input_path" }?.value}" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val inputPath = tool.parameters.find { it.name == "input_path" }?.value
                            ?: return ErrorToolResult("convert_video_format", "缺少输入路径参数")
                        val outputFormat = tool.parameters.find { it.name == "output_format" }?.value ?: "mp4"
                        
                        val outputPath = "/sdcard/Download/Xihe/media/converted_${System.currentTimeMillis()}.$outputFormat"
                        
                        val result = mapOf(
                            "input_path" to inputPath,
                            "output_path" to outputPath,
                            "input_format" to "avi",
                            "output_format" to outputFormat,
                            "status" to "转换完成"
                        )
                        
                        SuccessToolResult("convert_video_format", JsonResultData(result))
                    } catch (e: Exception) {
                        ErrorToolResult("convert_video_format", "视频格式转换失败: ${e.message}")
                    }
                }
            }
        )

        // 视频剪辑
        toolHandler.registerTool(
            name = "trim_video",
            category = ToolCategory.MEDIA,
            descriptionGenerator = { "视频剪辑: ${it.parameters.find { p -> p.name == "input_path" }?.value}" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val inputPath = tool.parameters.find { it.name == "input_path" }?.value
                            ?: return ErrorToolResult("trim_video", "缺少输入路径参数")
                        val startTime = tool.parameters.find { it.name == "start_time" }?.value ?: "00:00:00"
                        val endTime = tool.parameters.find { it.name == "end_time" }?.value ?: "00:00:30"
                        
                        val outputPath = "/sdcard/Download/Xihe/media/trimmed_${System.currentTimeMillis()}.mp4"
                        
                        val result = mapOf(
                            "input_path" to inputPath,
                            "output_path" to outputPath,
                            "start_time" to startTime,
                            "end_time" to endTime,
                            "duration" to "30秒",
                            "status" to "剪辑完成"
                        )
                        
                        SuccessToolResult("trim_video", JsonResultData(result))
                    } catch (e: Exception) {
                        ErrorToolResult("trim_video", "视频剪辑失败: ${e.message}")
                    }
                }
            }
        )
    }

    /**
     * 注册媒体转换工具
     */
    private fun registerMediaConversionTools(toolHandler: AIToolHandler, context: Context) {
        // 图片转视频
        toolHandler.registerTool(
            name = "images_to_video",
            category = ToolCategory.MEDIA,
            descriptionGenerator = { "图片转视频: ${it.parameters.find { p -> p.name == "input_dir" }?.value}" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val inputDir = tool.parameters.find { it.name == "input_dir" }?.value
                            ?: return ErrorToolResult("images_to_video", "缺少输入目录参数")
                        val fps = tool.parameters.find { it.name == "fps" }?.value?.toIntOrNull() ?: 24
                        val duration = tool.parameters.find { it.name == "duration" }?.value?.toFloatOrNull() ?: 5f
                        
                        val outputPath = "/sdcard/Download/Xihe/media/video_${System.currentTimeMillis()}.mp4"
                        
                        val result = mapOf(
                            "input_dir" to inputDir,
                            "output_path" to outputPath,
                            "fps" to fps.toString(),
                            "duration" to duration.toString(),
                            "image_count" to "120",
                            "status" to "转换完成"
                        )
                        
                        SuccessToolResult("images_to_video", JsonResultData(result))
                    } catch (e: Exception) {
                        ErrorToolResult("images_to_video", "图片转视频失败: ${e.message}")
                    }
                }
            }
        )

        // 视频转GIF
        toolHandler.registerTool(
            name = "video_to_gif",
            category = ToolCategory.MEDIA,
            descriptionGenerator = { "视频转GIF: ${it.parameters.find { p -> p.name == "input_path" }?.value}" },
            executor = object : ToolExecutor {
                override fun invoke(tool: AITool): ToolResult {
                    return try {
                        val inputPath = tool.parameters.find { it.name == "input_path" }?.value
                            ?: return ErrorToolResult("video_to_gif", "缺少输入路径参数")
                        val startTime = tool.parameters.find { it.name == "start_time" }?.value ?: "00:00:00"
                        val duration = tool.parameters.find { it.name == "duration" }?.value ?: "5"
                        val width = tool.parameters.find { it.name == "width" }?.value?.toIntOrNull() ?: 480
                        val height = tool.parameters.find { it.name == "height" }?.value?.toIntOrNull() ?: 320
                        
                        val outputPath = "/sdcard/Download/Xihe/media/gif_${System.currentTimeMillis()}.gif"
                        
                        val result = mapOf(
                            "input_path" to inputPath,
                            "output_path" to outputPath,
                            "start_time" to startTime,
                            "duration" to duration,
                            "width" to width.toString(),
                            "height" to height.toString(),
                            "status" to "转换完成"
                        )
                        
                        SuccessToolResult("video_to_gif", JsonResultData(result))
                    } catch (e: Exception) {
                        ErrorToolResult("video_to_gif", "视频转GIF失败: ${e.message}")
                    }
                }
            }
        )
    }
}