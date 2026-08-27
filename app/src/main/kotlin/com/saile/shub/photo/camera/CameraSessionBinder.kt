package com.saile.shub.photo.camera

import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.saile.shub.photo.CameraFlashMode
import com.saile.shub.photo.PhotoCapture
import com.saile.shub.photo.PhotoOverlay

internal data class BoundCameraSession(
    val cameraControl: CameraControl,
    val preview: Preview,
    val imageCapture: ImageCapture? = null,
    val videoCapture: VideoCapture<Recorder>? = null,
)

internal fun CameraFlashMode.toImageCaptureFlashMode(): Int = when (this) {
    CameraFlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
    CameraFlashMode.ON -> ImageCapture.FLASH_MODE_ON
    CameraFlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
}

/** Monta e vincula a sessão CameraX, mantendo as diferenças nativas de foto/vídeo isoladas. */
internal fun bindCameraSession(
    context: Context,
    provider: ProcessCameraProvider,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    capture: PhotoCapture,
    lensFacingBack: Boolean,
    flashMode: CameraFlashMode,
    isVideoMode: Boolean,
    outputRotation: Int,
    torchEnabled: Boolean,
    handler: Handler,
    getOverlay: () -> PhotoOverlay,
): BoundCameraSession {
    val pipelinePolicy = cameraPipelinePolicy(lensFacingBack)
    val continuousFocusMode = if (isVideoMode) {
        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
    } else {
        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
    }
    val resolutionSelector = ResolutionSelector.Builder()
        .setAspectRatioStrategy(
            AspectRatioStrategy(
                AspectRatio.RATIO_4_3,
                AspectRatioStrategy.FALLBACK_RULE_AUTO,
            )
        )
        .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
        .build()
    val previewBuilder = Preview.Builder()
        .setResolutionSelector(resolutionSelector)
        .setMirrorMode(
            if (pipelinePolicy.mirrorPreviewInCameraX) {
                MirrorMode.MIRROR_MODE_ON
            } else {
                MirrorMode.MIRROR_MODE_OFF
            }
        )
    Camera2Interop.Extender(previewBuilder).setCaptureRequestOption(
        CaptureRequest.CONTROL_AF_MODE,
        continuousFocusMode,
    )
    val preview = previewBuilder.build().also {
        it.surfaceProvider = previewView.surfaceProvider
    }
    val cameraSelector = if (lensFacingBack) {
        CameraSelector.DEFAULT_BACK_CAMERA
    } else {
        CameraSelector.DEFAULT_FRONT_CAMERA
    }
    fun bindSession(effect: CameraEffect, vararg useCases: UseCase): Camera {
        val groupBuilder = UseCaseGroup.Builder()
        useCases.forEach(groupBuilder::addUseCase)
        val useCaseGroup = groupBuilder
            .addEffect(effect)
            .build()
        return provider.bindToLifecycle(lifecycleOwner, cameraSelector, useCaseGroup)
    }

    provider.unbindAll()
    return if (isVideoMode) {
        val overlayEffect = createCameraOverlayEffect(
            context = context,
            handler = handler,
            capture = capture,
            lensFacingBack = lensFacingBack,
            targets = CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE,
            usePendingPhotoOverlay = false,
            getOverlay = getOverlay,
        )
        val recorder = Recorder.Builder()
            .setAspectRatio(AspectRatio.RATIO_4_3)
            .setQualitySelector(
                QualitySelector.fromOrderedList(
                    listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD),
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                )
            )
            .build()
        val videoCapture = VideoCapture.Builder(recorder)
            .setTargetRotation(outputRotation)
            .setMirrorMode(
                if (pipelinePolicy.mirrorSavedMedia) {
                    MirrorMode.MIRROR_MODE_ON
                } else {
                    MirrorMode.MIRROR_MODE_OFF
                }
            )
            .build()
        val camera = bindSession(overlayEffect, preview, videoCapture)
        runCatching { camera.cameraControl.enableTorch(torchEnabled) }
        BoundCameraSession(
            cameraControl = camera.cameraControl,
            preview = preview,
            videoCapture = videoCapture,
        )
    } else {
        val overlayEffect = createCameraOverlayEffect(
            context = context,
            handler = handler,
            capture = capture,
            lensFacingBack = lensFacingBack,
            targets = CameraEffect.IMAGE_CAPTURE or CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE,
            usePendingPhotoOverlay = true,
            getOverlay = getOverlay,
        )
        val imageCapture = ImageCapture.Builder()
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(outputRotation)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(flashMode.toImageCaptureFlashMode())
            .build()
        val camera = bindSession(overlayEffect, preview, imageCapture)
        runCatching { camera.cameraControl.enableTorch(false) }
        BoundCameraSession(
            cameraControl = camera.cameraControl,
            preview = preview,
            imageCapture = imageCapture,
        )
    }
}
