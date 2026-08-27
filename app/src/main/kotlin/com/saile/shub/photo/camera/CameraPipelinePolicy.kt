package com.saile.shub.photo.camera

/**
 * Política única de orientação do sFoto.
 *
 * O modo de campo mantém a orientação real da cena em todas as lentes, na prévia
 * e na mídia persistida. O aplicativo não aplica escala, inversão ou espelho de
 * superfície: a orientação é entregue pelo CameraX, como na câmera Android.
 */
internal data class CameraPipelinePolicy(
    val mirrorPreviewInCameraX: Boolean = false,
    val mirrorSavedMedia: Boolean = false,
    /**
     * Alguns dispositivos continuam espelhando a Surface da lente frontal no
     * PreviewView. Esta correção visual é exclusiva da prévia e não alcança o
     * JPEG ou o MP4.
     */
    val correctFrontPreviewInView: Boolean = false,
)

internal fun cameraPipelinePolicy(lensFacingBack: Boolean): CameraPipelinePolicy =
    if (lensFacingBack) {
        CameraPipelinePolicy(
            mirrorPreviewInCameraX = false,
            mirrorSavedMedia = false,
            correctFrontPreviewInView = false,
        )
    } else {
        CameraPipelinePolicy(
            mirrorPreviewInCameraX = true,
            mirrorSavedMedia = true,
            correctFrontPreviewInView = false,
        )
    }
