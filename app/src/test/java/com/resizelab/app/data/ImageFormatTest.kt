package com.resizelab.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageFormatTest {
    @Test
    fun gifUsesGifMimeAndExtension() {
        assertEquals("image/gif", ImageFormat.GIF.mimeType)
        assertEquals("gif", ImageFormat.GIF.extension)
    }

    @Test
    fun gifDoesNotShowGenericQualitySlider() {
        assertFalse(ImageFormat.GIF.supportsQuality)
        assertTrue(ImageFormat.JPEG.supportsQuality)
        assertTrue(ImageFormat.WEBP.supportsQuality)
    }
}
