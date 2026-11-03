package com.saile.shub.photo.camera

import android.graphics.Canvas

/**
 * Converte as coordenadas do overlay para o buffer antes de CameraX aplicar a
 * rotação e, quando houver, o espelhamento da saída.
 *
 * A fonte de verdade é sempre [androidx.camera.effects.Frame]. Assim a
 * prévia e o arquivo usam exatamente a mesma geometria do pipeline CameraX.
 */
internal data class OverlayFrameTransform(
    val rotationDegrees: Int,
    val willBeMirrored: Boolean,
) {
    init {
        require(rotationDegrees in setOf(0, 90, 180, 270)) {
            "Rotação CameraX inválida: $rotationDegrees"
        }
    }

    fun logicalWidth(width: Float, height: Float): Float =
        if (rotationDegrees == 90 || rotationDegrees == 270) height else width

    fun logicalHeight(width: Float, height: Float): Float =
        if (rotationDegrees == 90 || rotationDegrees == 270) width else height

    fun logicalSize(width: Float, height: Float): Pair<Float, Float> =
        if (rotationDegrees == 90 || rotationDegrees == 270) height to width else width to height

    /**
     * Salva o canvas e aplica a transformação inversa da saída CameraX.
     * O chamador deve restaurar o canvas após desenhar o overlay.
     */
    fun applyTo(canvas: Canvas, width: Float, height: Float) {
        canvas.save()
        when (rotationDegrees) {
            90 -> {
                canvas.translate(0f, height)
                canvas.rotate(-90f)
            }
            180 -> {
                canvas.translate(width, height)
                canvas.rotate(180f)
            }
            270 -> {
                canvas.translate(width, 0f)
                canvas.rotate(90f)
            }
        }
        if (willBeMirrored) {
            val logWidth = logicalWidth(width, height)
            canvas.translate(logWidth, 0f)
            canvas.scale(-1f, 1f)
        }
    }
}

private val cachedTransformInstances = Array(4) { rIdx ->
    Array(2) { mIdx ->
        OverlayFrameTransform(
            rotationDegrees = rIdx * 90,
            willBeMirrored = mIdx == 1,
        )
    }
}

internal fun overlayFrameTransform(
    rotationDegrees: Int,
    willBeMirrored: Boolean,
): OverlayFrameTransform {
    val rIdx = when (rotationDegrees) {
        0 -> 0
        90 -> 1
        180 -> 2
        270 -> 3
        else -> throw IllegalArgumentException("Rotação CameraX inválida: $rotationDegrees")
    }
    val mIdx = if (willBeMirrored) 1 else 0
    return cachedTransformInstances[rIdx][mIdx]
}
