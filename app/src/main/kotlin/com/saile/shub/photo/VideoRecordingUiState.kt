package com.saile.shub.photo

enum class VideoRecordingUiState {
    IDLE,
    STARTING,
    RECORDING,
    STOPPING;

    val locksControls: Boolean get() = this != IDLE
    val showsRecording: Boolean get() = this == RECORDING || this == STOPPING
    val captureEnabled: Boolean get() = this == IDLE || this == RECORDING

    fun requestStart(): VideoRecordingUiState = if (this == IDLE) STARTING else this
    fun requestStop(): VideoRecordingUiState = if (this == RECORDING) STOPPING else this

    fun recorderChanged(isActive: Boolean): VideoRecordingUiState =
        if (isActive) RECORDING else IDLE
}
