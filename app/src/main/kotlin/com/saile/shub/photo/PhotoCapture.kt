package com.saile.shub.photo

import android.content.ContentValues
import android.content.Context
import android.location.Location
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import com.saile.shub.photo.data.PhotoMediaMetadata
import com.saile.shub.photo.data.PhotoMediaMetadataDbHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors

data class PhotoOverlay(
    val color: OverlayColor,
    val location: Location?,
    val capturedAt: Date = Date(),
    val showUtm: Boolean = true,
    val showGeo: Boolean = true,
    val showDateTime: Boolean = true,
    val showAddress: Boolean = true,
    val showName: Boolean = false,
    val userName: String = "sFoto",
    val position: OverlayPosition = OverlayPosition.BOTTOM_RIGHT,
    val resolvedAddress: String? = null,
    val selectedLogo: String? = null,
)

class PhotoCapture(private val executor: Executor) {
    var preview: Preview? = null
    var imageCapture: ImageCapture? = null
    var videoCapture: VideoCapture<Recorder>? = null
    @Volatile internal var pendingPhotoOverlay: PhotoOverlay? = null
    private var recording: Recording? = null
    private val metadataExecutor = Executors.newSingleThreadExecutor()

    fun startRecording(
        context: Context,
        getOverlayInfo: () -> PhotoOverlay,
        onRecordingChanged: (Boolean) -> Unit,
        onStatus: (String, android.net.Uri?) -> Unit
    ) {
        val capture = videoCapture ?: run {
            onRecordingChanged(false)
            onStatus("Câmera de vídeo ainda não está pronta.", null)
            return
        }

        if (recording != null) {
            onRecordingChanged(false)
            onStatus("Já existe uma gravação em andamento.", null)
            return
        }

        val recordingOverlay = getOverlayInfo()
        val baseName = "sHub_VID_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(recordingOverlay.capturedAt)}"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, baseName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/sHub")
            }
        }
        val options = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        try {
            val pendingRecording = capture.output.prepareRecording(context, options)
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                pendingRecording.withAudioEnabled()
            }
            
            recording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        onRecordingChanged(true)
                    }
                    is VideoRecordEvent.Finalize -> {
                        recording = null
                        onRecordingChanged(false)
                        if (!event.hasError()) {
                            val uri = event.outputResults.outputUri
                            if (uri != android.net.Uri.EMPTY) {
                                executor.execute {
                                    saveMetadata(context, uri, recordingOverlay)
                                    ContextCompat.getMainExecutor(context).execute {
                                        onStatus("Vídeo salvo com sucesso!", uri)
                                    }
                                }
                            } else {
                                onStatus("Vídeo salvo, mas o URI não foi informado pelo sistema.", null)
                            }
                        } else {
                            onStatus("Erro ao salvar vídeo: ${event.error}", null)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            onRecordingChanged(false)
            onStatus("Não foi possível iniciar a gravação: ${e.message}", null)
        }
    }

    fun stopRecording() {
        recording?.stop()
    }
    
    fun capture(
        context: Context,
        overlay: PhotoOverlay,
        playSound: Boolean,
        isFrontCamera: Boolean = false,
        onStatus: (String, android.net.Uri?) -> Unit
    ) {
        val capture = imageCapture ?: run {
            onStatus("Câmera ainda não está pronta.", null)
            return
        }
        if (playSound) {
            runCatching {
                android.media.MediaActionSound().apply {
                    load(android.media.MediaActionSound.SHUTTER_CLICK)
                    play(android.media.MediaActionSound.SHUTTER_CLICK)
                }
            }
        }
        val name = "sHub_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(overlay.capturedAt)}"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/sHub")
            }
        }
        val options = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values,
        ).build()
        pendingPhotoOverlay = overlay
        runCatching {
            capture.takePicture(options, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    pendingPhotoOverlay = null
                    val uri = result.savedUri
                    if (uri == null) {
                        ContextCompat.getMainExecutor(context).execute {
                            onStatus("Foto salva, mas o URI não foi informado pelo sistema.", null)
                        }
                        return
                    }
                    runCatching {
                        metadataExecutor.execute {
                            if (isFrontCamera) {
                                runCatching { flipJpegHorizontally(context, uri) }
                            }
                            runCatching { saveMetadata(context, uri, overlay) }.onFailure {
                                ContextCompat.getMainExecutor(context).execute {
                                    onStatus("Foto salva, mas não foi possível registrar os metadados.", uri)
                                }
                            }
                            ContextCompat.getMainExecutor(context).execute {
                                onStatus("Foto salva em Pictures/sHub.", uri)
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    pendingPhotoOverlay = null
                    onStatus("Não foi possível capturar a foto: ${exception.message ?: "erro da câmera"}", null)
                }
            })
        }.onFailure { exception ->
            pendingPhotoOverlay = null
            ContextCompat.getMainExecutor(context).execute {
                onStatus("Não foi possível iniciar a captura: ${exception.message ?: "erro da câmera"}", null)
            }
        }
    }

    private fun flipJpegHorizontally(context: Context, uri: android.net.Uri) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream) ?: return
            val matrix = android.graphics.Matrix().apply { postScale(-1f, 1f) }
            val flippedBitmap = android.graphics.Bitmap.createBitmap(
                originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
            )
            originalBitmap.recycle()
            context.contentResolver.openOutputStream(uri, "rwt")?.use { outputStream ->
                flippedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, outputStream)
            }
            flippedBitmap.recycle()
        }
    }

    private fun saveMetadata(context: Context, uri: android.net.Uri, overlay: PhotoOverlay) {
        val helper = PhotoMediaMetadataDbHelper(context.applicationContext)
        try {
            helper.save(PhotoMediaMetadata.from(uri, overlay))
        } finally {
            helper.close()
        }
    }

    fun close() {
        stopRecording()
        metadataExecutor.shutdown()
    }
    
}
