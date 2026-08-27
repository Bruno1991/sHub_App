package com.saile.shub.photo

/**
 * Métricas visuais únicas do timestamp. Foto e vídeo usam esta mesma régua,
 * evitando variações de tamanho, margem e posição entre os dois modos.
 */
internal data class OverlayLayoutMetrics(
    val minDimension: Float,
    val baseTextSize: Float,
    val padding: Float,
    val iconSize: Int,
    val spacing: Float,
    val maxTextWidth: Float,
    val logoHeight: Float,
    val horizontalEdgeInset: Float,
    val verticalEdgeInset: Float,
)

internal fun overlayLayoutMetrics(
    width: Float,
    height: Float,
    horizontalEdgeInsetFraction: Float,
    verticalEdgeInsetFraction: Float = 0f,
): OverlayLayoutMetrics {
    val minDimension = minOf(width, height)
    val baseTextSize = minDimension * 0.027f
    return OverlayLayoutMetrics(
        minDimension = minDimension,
        baseTextSize = baseTextSize,
        padding = minDimension * 0.028f,
        iconSize = (baseTextSize * 1.2f).toInt(),
        spacing = minDimension * 0.006f,
        maxTextWidth = minDimension * 0.58f,
        // O logo deve identificar a equipe sem competir com os dados de campo.
        // Esta medida é compartilhada por foto e vídeo.
        logoHeight = minDimension * 0.060f,
        horizontalEdgeInset = minDimension * horizontalEdgeInsetFraction.coerceAtLeast(0f),
        verticalEdgeInset = minDimension * verticalEdgeInsetFraction.coerceAtLeast(0f),
    )
}
