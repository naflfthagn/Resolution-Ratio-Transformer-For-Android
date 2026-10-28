package com.resizelab.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioQualityTest {
    @Test
    fun autoKeepsOriginalAudio() {
        assertNull(AudioQuality.AUTO.bitrate)
        assertNull(AudioQuality.AUTO.kbps)
    }

    @Test
    fun manualPresetsExposeKbpsValues() {
        assertEquals(96_000, AudioQuality.KBPS_96.bitrate)
        assertEquals(128, AudioQuality.KBPS_128.kbps)
        assertEquals(192, AudioQuality.KBPS_192.kbps)
        assertEquals(256, AudioQuality.KBPS_256.kbps)
        assertEquals(320, AudioQuality.KBPS_320.kbps)
    }
}
