package com.resizelab.app.util

import com.resizelab.app.data.GifSpeed
import org.junit.Assert.assertEquals
import org.junit.Test

class GifTimingTest {
    @Test
    fun normalSpeedKeepsDelay() {
        assertEquals(100, GifTiming.scaleDelayMs(100, GifSpeed.NORMAL.multiplier))
    }

    @Test
    fun doubleSpeedHalvesDelay() {
        assertEquals(50, GifTiming.scaleDelayMs(100, GifSpeed.DOUBLE.multiplier))
        assertEquals(100, GifTiming.scaleDelayMs(200, GifSpeed.DOUBLE.multiplier))
    }

    @Test
    fun halfSpeedDoublesDelay() {
        assertEquals(200, GifTiming.scaleDelayMs(100, GifSpeed.HALF.multiplier))
    }

    @Test
    fun delaysAreQuantizedToTenMilliseconds() {
        assertEquals(70, GifTiming.scaleDelayMs(100, GifSpeed.ONE_AND_HALF.multiplier))
    }

    @Test
    fun zeroDelayUsesDefaultAndClamps() {
        assertEquals(50, GifTiming.scaleDelayMs(0, GifSpeed.DOUBLE.multiplier))
        assertEquals(10, GifTiming.scaleDelayMs(10, GifSpeed.DOUBLE.multiplier))
        assertEquals(60_000, GifTiming.scaleDelayMs(60_000, GifSpeed.HALF.multiplier))
    }
}
