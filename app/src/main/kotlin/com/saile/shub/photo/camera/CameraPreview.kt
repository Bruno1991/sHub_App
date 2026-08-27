package com.saile.shub.photo.camera

import android.util.Log
import androidx.camera.core.CameraControl
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.saile.shub.photo.CameraFlashMode
import com.saile.shub.photo.PhotoCapture
import com.saile.shub.photo.PhotoOverlay

@Composable
fun CameraPreview(
    capture: PhotoCapture,
    lensFacingBack: Boolean,
    flashMode: CameraFlashMode,
    isVideoMode: Boolean,
    outputRotation: Int,
    torchEnabled: Boolean,
    retryToken: Int,
    getOverlayInfo: () -> PhotoOverlay,
    onCameraReady: (CameraControl) -> Unit,
    onCameraError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // A transformação corretiva da lente frontal exige TextureView. O modo
    // PERFORMANCE pode usar SurfaceView, cuja composição não acompanha scaleX.
    // A opção deve ser definida antes de atribuir o SurfaceProvider.
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val cameraProviderFuture = remember(context) { ProcessCameraProvider.getInstance(context) }
    val overlayProvider = rememberUpdatedState(getOverlayInfo)
    val torchEnabledState = rememberUpdatedState(torchEnabled)
    val boundSession = remember { mutableStateOf<BoundCameraSession?>(null) }
    val pipelinePolicy = cameraPipelinePolicy(lensFacingBack)
    
    val handlerThread = remember { android.os.HandlerThread("OverlayThread").apply { start() } }
    val handler = remember { android.os.Handler(handlerThread.looper) }
    DisposableEffect(cameraProviderFuture) {
        onDispose {
            runCatching { boundSession.value?.cameraControl?.enableTorch(false) }
            runCatching {
                if (cameraProviderFuture.isDone) cameraProviderFuture.get().unbindAll()
            }
            handlerThread.quitSafely()
        }
    }
    
    LaunchedEffect(lensFacingBack, isVideoMode, retryToken) {
        try {
            val provider = cameraProviderFuture.get()
            capture.preview = null
            capture.imageCapture = null
            capture.videoCapture = null
            boundSession.value = null
            val session = bindCameraSession(
                context = context,
                provider = provider,
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                capture = capture,
                lensFacingBack = lensFacingBack,
                flashMode = flashMode,
                isVideoMode = isVideoMode,
                outputRotation = outputRotation,
                torchEnabled = torchEnabledState.value,
                handler = handler,
                getOverlay = { overlayProvider.value.invoke() },
            )
            capture.preview = session.preview
            capture.imageCapture = session.imageCapture
            capture.videoCapture = session.videoCapture
            boundSession.value = session
            onCameraReady(session.cameraControl)
        } catch (e: Exception) {
            capture.preview = null
            capture.imageCapture = null
            capture.videoCapture = null
            boundSession.value = null
            Log.e("sFotoCamera", "Falha ao vincular casos de uso da câmera", e)
            onCameraError(
                "Não foi possível iniciar a câmera: ${generateSequence(e as Throwable?) { it.cause }.last().message ?: e.javaClass.simpleName}"
            )
        }
    }

    LaunchedEffect(torchEnabled, boundSession.value) {
        runCatching { boundSession.value?.cameraControl?.enableTorch(torchEnabled) }
    }

    LaunchedEffect(outputRotation, boundSession.value) {
        boundSession.value?.imageCapture?.targetRotation = outputRotation
        boundSession.value?.videoCapture?.targetRotation = outputRotation
    }

    LaunchedEffect(flashMode, isVideoMode) {
        if (!isVideoMode) {
            capture.imageCapture?.flashMode = flashMode.toImageCaptureFlashMode()
        }
    }

    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
        val previewScaleX = if (pipelinePolicy.correctFrontPreviewInView) -1f else 1f
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView.apply { scaleX = previewScaleX } },
            update = {
                it.scaleX = previewScaleX
                // Foto e vídeo usam a mesma composição ao vivo.
                it.scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        )
        CameraFocusOverlay(
            previewView = previewView,
            cameraControl = boundSession.value?.cameraControl,
        )
    }
}
