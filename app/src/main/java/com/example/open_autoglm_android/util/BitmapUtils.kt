package com.example.open_autoglm_android.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object BitmapUtils {
    /**
     * 检查位图是否全黑或几乎全黑
     * @param bitmap 要检查的位图
     * @param threshold 阈值，如果黑色像素比例超过此值，认为是全黑（默认 0.98，即 98%）
     * @return true 如果位图是全黑的
     */
    fun isBitmapBlack(bitmap: Bitmap, threshold: Double = 0.98): Boolean {
        if (bitmap.width == 0 || bitmap.height == 0) {
            Log.w("BitmapUtils", "Bitmap 尺寸为 0")
            return true
        }

        // 如果 Bitmap 是 HARDWARE 格式，需要先转换
        val accessibleBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
            Log.d("BitmapUtils", "转换 HARDWARE Bitmap 为 ARGB_8888")
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            null
        }

        val targetBitmap = accessibleBitmap ?: bitmap

        try {
            // 采样检查，使用更多的采样点以获得更准确的结果
            // 在 1080x1920 的屏幕上，采样约 100 个点
            val samplePoints = 100
            val stepX = maxOf(1, targetBitmap.width / 10)
            val stepY = maxOf(1, targetBitmap.height / 10)

            var blackPixels = 0
            var totalPixels = 0

            for (y in 0 until targetBitmap.height step stepY) {
                for (x in 0 until targetBitmap.width step stepX) {
                    val pixel = targetBitmap.getPixel(x, y)
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF

                    // 如果 RGB 值都很低（小于 10），认为是黑色
                    if (r < 10 && g < 10 && b < 10) {
                        blackPixels++
                    }
                    totalPixels++
                }
            }

            val blackRatio = blackPixels.toDouble() / totalPixels
            val isBlack = blackRatio >= threshold

            return isBlack
        } finally {
            // 如果创建了临时 Bitmap，需要回收
            accessibleBitmap?.recycle()
        }
    }

    /**
     * 等比缩放 Bitmap
     */
    fun scaleBitmap(bitmap: Bitmap, scale: Float): Bitmap {
        if (scale >= 1.0f) return bitmap
        val width = (bitmap.width * scale).toInt()
        val height = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    /**
     * 在图片上绘制点击点（渐变透明圆点）
     */
    fun drawTapMarker(bitmap: Bitmap, x: Float, y: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        val radius = maxOf(bitmap.width, bitmap.height) * 0.03f
        
        // 绘制多层渐变圆点
        // 1. 外圈淡红色
        paint.shader = RadialGradient(
            x, y, radius,
            intArrayOf(Color.argb(200, 255, 0, 0), Color.argb(0, 255, 0, 0)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x, y, radius, paint)
        
        // 2. 内圈亮红色
        paint.shader = null
        paint.color = Color.argb(255, 255, 0, 0)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        canvas.drawCircle(x, y, radius * 0.4f, paint)
        
        paint.style = Paint.Style.FILL
        canvas.drawCircle(x, y, 10f, paint)
        
        return result
    }

    /**
     * 在图片上绘制滑动线条
     */
    fun drawSwipeMarker(bitmap: Bitmap, startX: Float, startY: Float, endX: Float, endY: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        // 绘制线条
        paint.color = Color.RED
        paint.strokeWidth = 10f
        paint.strokeCap = Paint.Cap.ROUND
        paint.alpha = 180
        canvas.drawLine(startX, startY, endX, endY, paint)
        
        // 绘制起点和终点
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        canvas.drawCircle(startX, startY, 15f, paint)
        
        // 终点画个小箭头或者圆圈
        paint.color = Color.YELLOW
        canvas.drawCircle(endX, endY, 15f, paint)
        
        return result
    }

    /**
     * 保存 Bitmap 到私有目录并返回路径
     */
    fun saveBitmap(context: Context, bitmap: Bitmap): String? {
        val fileName = "action_${UUID.randomUUID()}.jpg"
        val file = File(context.getExternalFilesDir("action_images"), fileName)
        
        return try {
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e("BitmapUtils", "Failed to save bitmap", e)
            null
        }
    }
}
