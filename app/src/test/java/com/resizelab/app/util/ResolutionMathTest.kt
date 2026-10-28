package com.resizelab.app.util

import com.resizelab.app.data.ResizeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolutionMathTest {

    @Test
    fun longEdgePreservesAspectRatio() {
        val result = ResolutionMath.sizeForLongEdge(
            PixelSize(3840, 2160),
            longEdge = 1920
        )
        assertEquals(1920, result.width)
        assertEquals(1080, result.height)
    }

    @Test
    fun portraitLongEdgeStaysPortrait() {
        val result = ResolutionMath.sizeForLongEdge(
            PixelSize(2160, 3840),
            longEdge = 1920
        )
        assertEquals(1080, result.width)
        assertEquals(1920, result.height)
    }

    @Test
    fun widthLockKeepsRatio() {
        val result = ResolutionMath.sizeForWidth(
            PixelSize(4000, 3000),
            width = 1000
        )
        assertEquals(1000, result.width)
        assertEquals(750, result.height)
    }

    @Test
    fun videoDimensionsAreMadeEven() {
        val result = ResolutionMath.sizeForLongEdge(
            PixelSize(2161, 3841),
            longEdge = 1921,
            isVideo = true
        )
        assertEquals(0, result.width % 2)
        assertEquals(0, result.height % 2)
        assertTrue(result.width > 0)
        assertTrue(result.height > 0)
    }

    @Test
    fun clampToVideoMaximumPreservesRatio() {
        val result = ResolutionMath.clampToMax(
            PixelSize(8000, 4000),
            isVideo = true
        )
        assertEquals(4096, result.width)
        assertEquals(2048, result.height)
    }

    @Test
    fun imagePixelLimitIsEnforced() {
        assertEquals(
            "pixels",
            ResolutionMath.validateTarget(PixelSize(6144, 6144), isVideo = false)
        )
        assertNull(
            ResolutionMath.validateTarget(PixelSize(6000, 4000), isVideo = false)
        )
    }

    @Test
    fun validationRejectsOutOfRangeSizes() {
        assertEquals("min", ResolutionMath.validateTarget(PixelSize(8, 100), false))
        assertEquals("max", ResolutionMath.validateTarget(PixelSize(7000, 1000), false))
        assertNull(ResolutionMath.validateTarget(PixelSize(1920, 1080), false))
    }

    @Test
    fun fitRectLetterboxesLandscapeSourceIntoSquarTarget() {
        val rect = ResolutionMath.fittingRect(1920, 1080, 1080, 1080, ResizeMode.FIT)
        assertEquals(0f, rect[0], 0.01f)
        assertEquals(1080f, rect[2], 0.01f)
        assertEquals(607.5f, rect[3], 0.01f)
        assertEquals(236.25f, rect[1], 0.01f)
    }

    @Test
    fun cropRectOverflowsTargetToFillIt() {
        val rect = ResolutionMath.fittingRect(1920, 1080, 1080, 1080, ResizeMode.CROP)
        assertEquals(1920f, rect[2], 0.01f)
        assertEquals(1080f, rect[3], 0.01f)
        assertEquals(-420f, rect[0], 0.01f)
        assertEquals(0f, rect[1], 0.01f)
    }

    @Test
    fun outputNameIsSanitizedAndCarriesTargetSize() {
        val name = buildOutputName("My Photo?.JPG", 1920, 1080, "jpg")
        assertTrue(name.startsWith("My Photo__1920x1080_"))
        assertTrue(name.endsWith(".jpg"))
        assertFalse(name.contains("?"))
    }

    @Test
    fun megapixelsAreFormattedWithOneDecimal() {
        assertEquals("8.3", formatMegapixels(3840, 2160))
    }
}
