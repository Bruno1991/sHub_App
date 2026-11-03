package com.saile.shub.photo.camera

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Handler
import android.util.Log
import androidx.camera.effects.OverlayEffect
import com.saile.shub.photo.OverlayRenderer
import com.saile.shub.photo.PhotoCapture
import com.saile.shub.photo.PhotoOverlay

/** Cria o único renderer de overlay usado pelas saídas de foto e vídeo. */
internal fun createCameraOverlayEffect(
    context: Context,
    handler: Handler,
    capture: PhotoCapture,
    lensFacingBack: Boolean,
    targets: Int,
    usePendingPhotoOverlay: Boolean,
    getOverlay: () -> PhotoOverlay,
): OverlayEffect = OverlayEffect(targets, 0, handler) { error ->
    Log.e("sFotoCamera", "Falha ao renderizar o overlay", error)
}.also { effect ->
    effect.setOnDrawListener { frame ->
        val canvas = frame.overlayCanvas
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val pendingPhotoOverlay = capture.pendingPhotoOverlay
        val overlay = if (usePendingPhotoOverlay) {
            pendingPhotoOverlay ?: getOverlay()
        } else {
            getOverlay()
        }
        val isFrontPhotoCapture =
            !lensFacingBack && usePendingPhotoOverlay && pendingPhotoOverlay != null
        val transform = overlayFrameTransform(
            rotationDegrees = frame.rotationDegrees,
            willBeMirrored = frame.isMirroring || isFrontPhotoCapture,
        )
        val cWidth = canvas.width.toFloat()
        val cHeight = canvas.height.toFloat()
        val logicalWidth = transform.logicalWidth(cWidth, cHeight)
        val logicalHeight = transform.logicalHeight(cWidth, cHeight)

        transform.applyTo(canvas, cWidth, cHeight)
        OverlayRenderer.draw(
            context = context,
            canvas = canvas,
            width = logicalWidth,
            height = logicalHeight,
            overlay = overlay,
            rotationDegrees = 0,
            horizontalEdgeInsetFraction = 0.02f,
        )
        canvas.restore()
        true
    }
}
