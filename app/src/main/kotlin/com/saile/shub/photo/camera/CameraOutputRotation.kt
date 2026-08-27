package com.saile.shub.photo.camera

import android.view.Surface

/**
 * Converte o ângulo físico informado por [android.view.OrientationEventListener]
 * para a rotação de saída esperada pelo CameraX.
 *
 * O sentido é inverso: aparelho em 90° corresponde a ROTATION_270 e vice-versa.
 */
internal fun cameraOutputRotation(orientationDegrees: Int): Int = when (orientationDegrees) {
    in 45 until 135 -> Surface.ROTATION_270
    in 135 until 225 -> Surface.ROTATION_180
    in 225 until 315 -> Surface.ROTATION_90
    else -> Surface.ROTATION_0
}
