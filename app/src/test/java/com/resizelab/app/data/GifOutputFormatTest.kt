package com.resizelab.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GifOutputFormatTest {
    @Test
    fun bothOutputFormatsAreAvailable() {
        assertEquals(2, GifOutputFormat.entries.size)
        assertEquals(GifOutputFormat.GIF, GifOutputFormat.valueOf("GIF"))
        assertEquals(GifOutputFormat.MP4, GifOutputFormat.valueOf("MP4"))
    }
}
