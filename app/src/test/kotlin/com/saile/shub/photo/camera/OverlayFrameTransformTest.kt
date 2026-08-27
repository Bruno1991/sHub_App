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
}
