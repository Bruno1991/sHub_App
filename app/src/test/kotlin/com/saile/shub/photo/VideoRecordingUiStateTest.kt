package com.saile.shub.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoRecordingUiStateTest {
    @Test
    fun successfulRecordingFollowsRecorderEvents() {
        val starting = VideoRecordingUiState.IDLE.requestStart()
        assertEquals(VideoRecordingUiState.STARTING, starting)
        assertFalse(starting.captureEnabled)

        val recording = starting.recorderChanged(isActive = true)
        assertEquals(VideoRecordingUiState.RECORDING, recording)
        assertTrue(recording.showsRecording)
        assertTrue(recording.captureEnabled)

        val stopping = recording.requestStop()
        assertEquals(VideoRecordingUiState.STOPPING, stopping)
        assertFalse(stopping.captureEnabled)

        assertEquals(VideoRecordingUiState.IDLE, stopping.recorderChanged(isActive = false))
    }

    @Test
    fun failedStartAlwaysReturnsToIdle() {
        val starting = VideoRecordingUiState.IDLE.requestStart()
        assertEquals(VideoRecordingUiState.IDLE, starting.recorderChanged(isActive = false))
    }

    @Test
    fun duplicateRequestsDoNotCreateInvalidTransitions() {
        assertEquals(
            VideoRecordingUiState.RECORDING,
            VideoRecordingUiState.RECORDING.requestStart()
        )
        assertEquals(
            VideoRecordingUiState.STARTING,
            VideoRecordingUiState.STARTING.requestStop()
        )
    }
}
