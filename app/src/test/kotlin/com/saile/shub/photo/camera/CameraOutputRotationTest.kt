package com.saile.shub.photo.camera

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraOutputRotationTest {
    @Test
    fun `maps portrait orientations directly`() {
        assertEquals(Surface.ROTATION_0, cameraOutputRotation(0))
        assertEquals(Surface.ROTATION_180, cameraOutputRotation(180))
        assertEquals(Surface.ROTATION_0, cameraOutputRotation(359))
    }

    @Test
    fun `inverts quarter turns from orientation sensor to CameraX surface rotation`() {
        assertEquals(Surface.ROTATION_270, cameraOutputRotation(90))
        assertEquals(Surface.ROTATION_90, cameraOutputRotation(270))
    }

    @Test
    fun `uses stable sensor boundaries`() {
        assertEquals(Surface.ROTATION_0, cameraOutputRotation(44))
        assertEquals(Surface.ROTATION_270, cameraOutputRotation(45))
        assertEquals(Surface.ROTATION_180, cameraOutputRotation(135))
        assertEquals(Surface.ROTATION_90, cameraOutputRotation(225))
        assertEquals(Surface.ROTATION_0, cameraOutputRotation(315))
    }
}
