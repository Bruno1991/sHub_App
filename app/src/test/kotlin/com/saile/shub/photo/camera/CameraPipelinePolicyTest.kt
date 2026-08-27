package com.saile.shub.photo.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraPipelinePolicyTest {
    @Test
    fun `front camera keeps real orientation in preview and saved media`() {
        val policy = cameraPipelinePolicy(lensFacingBack = false)

        assertTrue(policy.mirrorPreviewInCameraX)
        assertTrue(policy.mirrorSavedMedia)
        assertFalse(policy.correctFrontPreviewInView)
    }

    @Test
    fun `back camera does not mirror preview or saved media`() {
        val policy = cameraPipelinePolicy(lensFacingBack = true)

        assertFalse(policy.mirrorPreviewInCameraX)
        assertFalse(policy.mirrorSavedMedia)
        assertFalse(policy.correctFrontPreviewInView)
    }
}
