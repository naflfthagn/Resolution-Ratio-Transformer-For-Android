package com.resizelab.app.util

data class VideoGifPlan(
    val frameCount: Int,
    val frameDelayMs: Int
)

/**
 * Chooses a GIF frame plan for a video. The target is 10 fps, but long videos
 * are capped at 300 frames and sampled at a lower frame rate so the whole
 * duration is preserved instead of silently truncating it.
 */
object VideoGifTimeline {
    const val TARGET_FPS = 10
    const val MAX_FRAMES = 300

    fun plan(durationMs: Long): VideoGifPlan {
        require(durationMs > 0L) { "Video duration must be positive" }
        val idealFrames = (durationMs * TARGET_FPS + 999L) / 1000L
        val frameCount = idealFrames.coerceIn(1L, MAX_FRAMES.toLong()).toInt()
        val rawDelay = durationMs / frameCount
        val delayMs = (((rawDelay + 5L) / 10L) * 10L).coerceIn(10L, 60_000L).toInt()
        return VideoGifPlan(frameCount = frameCount, frameDelayMs = delayMs)
    }

    fun frameTimeUs(index: Int, durationMs: Long, frameCount: Int): Long {
        require(index in 0 until frameCount) { "Frame index out of range" }
        return index.toLong() * durationMs * 1000L / frameCount
    }
}
