package com.resizelab.app.util

import com.resizelab.app.data.ResizeMode
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class PixelSize(val width: Int, val height: Int) {
    val isValid: Boolean
        get() = width > 0 && height > 0

    val megapixels: Double
        get() = width.toLong() * height / 1_000_000.0

    fun toEven(): PixelSize = PixelSize(
        width = ResolutionMath.makeEven(width),
        height = ResolutionMath.makeEven(height)
    )
}

object ResolutionMath {
    const val MIN_DIMENSION = 16
    const val MAX_IMAGE_DIMENSION = 6144
    const val MAX_VIDEO_DIMENSION = 4096
    const val MAX_IMAGE_PIXELS = 32_000_000L

    fun maxDimension(isVideo: Boolean): Int =
        if (isVideo) MAX_VIDEO_DIMENSION else MAX_IMAGE_DIMENSION

    fun makeEven(value: Int): Int = (value / 2 * 2).coerceAtLeast(2)

    fun sizeForLongEdge(
        source: PixelSize,
        longEdge: Int,
        isVideo: Boolean = false
    ): PixelSize {
        if (!source.isValid) return PixelSize(0, 0)
        val safeEdge = longEdge.coerceAtLeast(MIN_DIMENSION)
        val scale = safeEdge.toDouble() / max(source.width, source.height)
        var width = (source.width * scale).roundToInt().coerceAtLeast(MIN_DIMENSION)
        var height = (source.height * scale).roundToInt().coerceAtLeast(MIN_DIMENSION)
        if (isVideo) {
            width = makeEven(width)
            height = makeEven(height)
        }
        return PixelSize(width, height)
    }

    fun sizeForWidth(
        source: PixelSize,
        width: Int,
        isVideo: Boolean = false
    ): PixelSize {
        if (!source.isValid || width <= 0) return PixelSize(width.coerceAtLeast(0), 0)
        var height = (width.toDouble() * source.height / source.width).roundToInt()
            .coerceAtLeast(1)
        var normalizedWidth = width
        if (isVideo) {
            normalizedWidth = makeEven(width)
            height = makeEven(height)
        }
        return PixelSize(normalizedWidth, height)
    }

    fun sizeForHeight(
        source: PixelSize,
        height: Int,
        isVideo: Boolean = false
    ): PixelSize {
        if (!source.isValid || height <= 0) return PixelSize(0, height.coerceAtLeast(0))
        var width = (height.toDouble() * source.width / source.height).roundToInt()
            .coerceAtLeast(1)
        var normalizedHeight = height
        if (isVideo) {
            width = makeEven(width)
            normalizedHeight = makeEven(height)
        }
        return PixelSize(width, normalizedHeight)
    }

    fun clampToMax(size: PixelSize, isVideo: Boolean): PixelSize {
        if (!size.isValid) return size
        val maxDim = maxDimension(isVideo)
        if (size.width <= maxDim && size.height <= maxDim) return size
        val scale = min(maxDim.toDouble() / size.width, maxDim.toDouble() / size.height)
        var width = (size.width * scale).roundToInt().coerceAtLeast(MIN_DIMENSION)
        var height = (size.height * scale).roundToInt().coerceAtLeast(MIN_DIMENSION)
        if (isVideo) {
            width = makeEven(width)
            height = makeEven(height)
        }
        return PixelSize(width, height)
    }

    fun validateTarget(size: PixelSize, isVideo: Boolean): String? {
        val maxDim = maxDimension(isVideo)
        if (size.width < MIN_DIMENSION || size.height < MIN_DIMENSION) return "min"
        if (size.width > maxDim || size.height > maxDim) return "max"
        if (!isVideo && size.width.toLong() * size.height > MAX_IMAGE_PIXELS) return "pixels"
        return null
    }

    fun fittingRect(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
        mode: ResizeMode
    ): FloatArray {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            return floatArrayOf(0f, 0f, 0f, 0f)
        }
        val scale = when (mode) {
            ResizeMode.FIT -> min(
                targetWidth.toDouble() / sourceWidth,
                targetHeight.toDouble() / sourceHeight
            )
            ResizeMode.CROP, ResizeMode.STRETCH -> max(
                targetWidth.toDouble() / sourceWidth,
                targetHeight.toDouble() / sourceHeight
            )
        }
        val drawWidth = (sourceWidth * scale).toFloat()
        val drawHeight = (sourceHeight * scale).toFloat()
        return floatArrayOf(
            (targetWidth - drawWidth) / 2f,
            (targetHeight - drawHeight) / 2f,
            drawWidth,
            drawHeight
        )
    }
}
