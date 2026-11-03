package com.saile.shub.photo.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayFrameTransformTest {
    @Test
    fun `uses CameraX rotation to calculate landscape logical size`() {
        val transform = overlayFrameTransform(rotationDegrees = 90, willBeMirrored = false)

        assertEquals(1080f to 1920f, transform.logicalSize(width = 1920f, height = 1080f))
    }

    @Test
    fun `keeps portrait logical size without rotation`() {
        val transform = overlayFrameTransform(rotationDegrees = 0, willBeMirrored = false)

        assertEquals(1080f to 1920f, transform.logicalSize(width = 1080f, height = 1920f))
        assertFalse(transform.willBeMirrored)
    }

    @Test
    fun `retains frame mirror metadata for inverse overlay compensation`() {
        val transform = overlayFrameTransform(rotationDegrees = 270, willBeMirrored = true)

        assertTrue(transform.willBeMirrored)
    }

    @Test
    fun `returns cached singleton instances without heap allocation`() {
        val first = overlayFrameTransform(rotationDegrees = 90, willBeMirrored = true)
        val second = overlayFrameTransform(rotationDegrees = 90, willBeMirrored = true)

        // Verifies reference equality (same cached instance)
        assertTrue(first === second)
    }

    @Test
    fun `calculates logical width and height directly without allocating Pair`() {
        val landscape = overlayFrameTransform(rotationDegrees = 90, willBeMirrored = false)
        assertEquals(1080f, landscape.logicalWidth(1920f, 1080f), 0.001f)
        assertEquals(1920f, landscape.logicalHeight(1920f, 1080f), 0.001f)

        val portrait = overlayFrameTransform(rotationDegrees = 0, willBeMirrored = false)
        assertEquals(1080f, portrait.logicalWidth(1080f, 1920f), 0.001f)
        assertEquals(1920f, portrait.logicalHeight(1080f, 1920f), 0.001f)
    }
}
