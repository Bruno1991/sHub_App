package com.saile.shub.photo

import kotlin.math.sqrt

data class StandardizedLogoDimensions(
    val width: Float,
    val height: Float,
)

/**
 * Standardizes logo dimensions across varying aspect ratios (from 1:1 square to ~4:1 wide),
 * using Energisa (~3.07:1) as the visual reference.
 *
 * Normalizes visual area so that square/compact logos do not appear microscopic and
 * wide logos do not appear excessively huge or stretch across the screen.
 */
fun calculateStandardizedLogoDimensions(
    vw: Float,
    vh: Float,
    baseLogoHeight: Float,
): StandardizedLogoDimensions {
    if (vw <= 0f || vh <= 0f || baseLogoHeight <= 0f) {
        return StandardizedLogoDimensions(baseLogoHeight, baseLogoHeight)
    }

    val aspectRatio = vw / vh
    val referenceAspectRatio = 3.07f

    // Calculate area-equivalent height to equalize visual weight
    val areaEquivalentHeight = baseLogoHeight * sqrt(referenceAspectRatio / aspectRatio)

    // Clamp height to avoid oversized square logos or microscopic wide logos
    var targetH = areaEquivalentHeight.coerceIn(baseLogoHeight * 0.75f, baseLogoHeight * 1.35f)
    var targetW = targetH * aspectRatio

    // Cap max width to prevent wide logos from extending beyond overlay boundaries
    val maxW = baseLogoHeight * 3.25f
    if (targetW > maxW) {
        targetW = maxW
        targetH = targetW / aspectRatio
    }

    return StandardizedLogoDimensions(width = targetW, height = targetH)
}
