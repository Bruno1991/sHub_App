package com.saile.shub.photo

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayLayoutTest {
    @Test
    fun `photo and portrait video use the same layout for the same short edge`() {
        val photo = overlayLayoutMetrics(1080f, 1440f, 0.02f)
        val video = overlayLayoutMetrics(1080f, 1920f, 0.02f)

        assertEquals(photo, video)
    }

    @Test
    fun `layout preserves the configured lateral inset`() {
        val layout = overlayLayoutMetrics(1080f, 1920f, 0.02f)

        assertEquals(21.6f, layout.horizontalEdgeInset, 0.001f)
    }

    @Test
    fun `layout applies vertical safe inset without changing the overlay scale`() {
        val photo = overlayLayoutMetrics(1440f, 1080f, 0.02f)
        val video = overlayLayoutMetrics(1440f, 1080f, 0.02f, 0.125f)

        assertEquals(photo.baseTextSize, video.baseTextSize, 0.001f)
        assertEquals(photo.logoHeight, video.logoHeight, 0.001f)
        assertEquals(135f, video.verticalEdgeInset, 0.001f)
    }

    @Test
    fun `photo and video share the full four by three overlay ruler`() {
        val photo = overlayLayoutMetrics(1980f, 2640f, 0.02f)
        val video = overlayLayoutMetrics(1980f, 2640f, 0.02f)

        assertEquals(photo, video)
    }
}
