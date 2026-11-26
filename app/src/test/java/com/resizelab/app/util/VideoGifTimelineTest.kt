package com.resizelab.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoGifTimelineTest {
    @Test
    fun shortVideoUsesTenFps() {
        val plan = VideoGifTimeline.plan(1_000)
        assertEquals(10, plan.frameCount)
        assertEquals(100, plan.frameDelayMs)
    }

    @Test
    fun tenSecondsUsesHundredFrames() {
        val plan = VideoGifTimeline.plan(10_000)
        assertEquals(100, plan.frameCount)
        assertEquals(100, plan.frameDelayMs)
    }

    @Test
    fun longVideoIsCappedAtThreeHundredFrames() {
        val plan = VideoGifTimeline.plan(60_000)
        assertEquals(300, plan.frameCount)
        assertEquals(200, plan.frameDelayMs)
    }

    @Test
    fun veryShortVideoStillGetsOneFrame() {
        val plan = VideoGifTimeline.plan(100)
        assertEquals(1, plan.frameCount)
        assertEquals(100, plan.frameDelayMs)
    }

    @Test
    fun frameTimestampsStayInsideDuration() {
        val durationMs = 12_345L
        val plan = VideoGifTimeline.plan(durationMs)
        val lastUs = VideoGifTimeline.frameTimeUs(
            plan.frameCount - 1,
            durationMs,
            plan.frameCount
        )
        assertTrue(lastUs < durationMs * 1000L)
        assertEquals(0L, VideoGifTimeline.frameTimeUs(0, durationMs, plan.frameCount))
    }
}
