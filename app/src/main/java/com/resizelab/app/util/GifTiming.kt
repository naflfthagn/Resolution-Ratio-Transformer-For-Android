package com.resizelab.app.util

import kotlin.math.roundToInt

object GifTiming {
    private const val DEFAULT_FRAME_DELAY_MS = 100
    private const val MIN_FRAME_DELAY_MS = 10
    private const val MAX_FRAME_DELAY_MS = 60_000

    /**
     * Scales one GIF frame delay by playback speed and quantizes it to the
     * 10 ms precision used by the GIF format. A 2x speed halves every delay.
     */
    fun scaleDelayMs(originalDelayMs: Int, speedMultiplier: Float): Int {
        val baseDelay = if (originalDelayMs <= 0) DEFAULT_FRAME_DELAY_MS else originalDelayMs
        val speed = speedMultiplier.coerceAtLeast(0.01f)
        val scaled = baseDelay / speed
        val quantized = (scaled / 10f).roundToInt() * 10
        return quantized.coerceIn(MIN_FRAME_DELAY_MS, MAX_FRAME_DELAY_MS)
    }
}
