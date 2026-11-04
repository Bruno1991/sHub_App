package com.saile.shub.gis

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import com.caverock.androidsvg.SVG
import com.saile.shub.gis.domain.GisElementType

data class GisSymbolIcon(
    val bitmap: Bitmap,
    val halfWidth: Float,
    val halfHeight: Float,
    val touchRadius: Float
)

object GisSymbolProvider {

    private var currentDensity = -1f
    private val iconCache = mutableMapOf<GisElementType, GisSymbolIcon>()

    // High-contrast, authentic electrical engineering SVG vector symbols (ABNT / QField / QGIS standards)
    private val SUBSTATION_SVG = """
        <svg viewBox="0 0 48 48" width="48" height="48" xmlns="http://www.w3.org/2000/svg">
          <rect x="3" y="3" width="42" height="42" rx="10" fill="#00C853" stroke="#FFFFFF" stroke-width="4"/>
          <path d="M 27 9 L 16 26 L 24 26 L 21 39 L 32 23 L 24 23 Z" fill="#FFFFFF"/>
        </svg>
    """.trimIndent()

    private val TRANSFORMER_SVG = """
        <svg viewBox="0 0 48 48" width="48" height="48" xmlns="http://www.w3.org/2000/svg">
          <circle cx="24" cy="24" r="21" fill="#1C1B1F" stroke="#FFFFFF" stroke-width="4"/>
          <circle cx="18" cy="24" r="8.5" fill="none" stroke="#FFD600" stroke-width="3"/>
          <circle cx="30" cy="24" r="8.5" fill="none" stroke="#FFD600" stroke-width="3"/>
        </svg>
    """.trimIndent()

    private val SWITCH_SVG = """
        <svg viewBox="0 0 48 48" width="48" height="48" xmlns="http://www.w3.org/2000/svg">
          <circle cx="24" cy="24" r="21" fill="#D50000" stroke="#FFFFFF" stroke-width="4"/>
          <circle cx="14" cy="24" r="3.5" fill="#FFFFFF"/>
          <circle cx="34" cy="24" r="3.5" fill="#FFFFFF"/>
          <line x1="14" y1="24" x2="31" y2="15" stroke="#FFFFFF" stroke-width="3.5" stroke-linecap="round"/>
        </svg>
    """.trimIndent()

    private val REGULATOR_SVG = """
        <svg viewBox="0 0 48 48" width="48" height="48" xmlns="http://www.w3.org/2000/svg">
          <circle cx="24" cy="24" r="21" fill="#FF6D00" stroke="#FFFFFF" stroke-width="4"/>
          <path d="M 18 14 L 18 34 M 18 14 L 26 14 C 30 14 32 17 32 20 C 32 23 30 26 26 26 L 18 26 M 25 26 L 31 34" stroke="#FFFFFF" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
        </svg>
    """.trimIndent()

    private val CAPACITOR_SVG = """
        <svg viewBox="0 0 48 48" width="48" height="48" xmlns="http://www.w3.org/2000/svg">
          <circle cx="24" cy="24" r="21" fill="#0091EA" stroke="#FFFFFF" stroke-width="4"/>
          <line x1="12" y1="24" x2="20" y2="24" stroke="#FFFFFF" stroke-width="3.5" stroke-linecap="round"/>
          <line x1="20" y1="14" x2="20" y2="34" stroke="#FFFFFF" stroke-width="3.5" stroke-linecap="round"/>
          <line x1="28" y1="14" x2="28" y2="34" stroke="#FFFFFF" stroke-width="3.5" stroke-linecap="round"/>
          <line x1="28" y1="24" x2="36" y2="24" stroke="#FFFFFF" stroke-width="3.5" stroke-linecap="round"/>
        </svg>
    """.trimIndent()

    private val TOWER_SVG = """
        <svg viewBox="0 0 48 48" width="48" height="48" xmlns="http://www.w3.org/2000/svg">
          <polygon points="24,4 43,43 5,43" fill="#ECEFF1" stroke="#212121" stroke-width="3.5"/>
          <line x1="13" y1="29" x2="35" y2="29" stroke="#212121" stroke-width="2.5"/>
          <line x1="18" y1="19" x2="30" y2="19" stroke="#212121" stroke-width="2.5"/>
          <line x1="24" y1="4" x2="24" y2="43" stroke="#212121" stroke-width="2"/>
        </svg>
    """.trimIndent()

    private val POLE_SVG = """
        <svg viewBox="0 0 32 32" width="32" height="32" xmlns="http://www.w3.org/2000/svg">
          <circle cx="16" cy="16" r="13" fill="#FFFFFF" stroke="#212121" stroke-width="3.5"/>
          <circle cx="16" cy="16" r="4.5" fill="#212121"/>
        </svg>
    """.trimIndent()

    private val OTHER_SVG = """
        <svg viewBox="0 0 32 32" width="32" height="32" xmlns="http://www.w3.org/2000/svg">
          <circle cx="16" cy="16" r="13" fill="#9E9E9E" stroke="#FFFFFF" stroke-width="3.5"/>
          <circle cx="16" cy="16" r="4" fill="#FFFFFF"/>
        </svg>
    """.trimIndent()

    @Synchronized
    fun getSymbol(type: GisElementType, density: Float): GisSymbolIcon? {
        if (density != currentDensity) {
            initIcons(density)
        }
        return iconCache[type] ?: iconCache[GisElementType.OTHER]
    }

    private fun initIcons(density: Float) {
        currentDensity = density
        iconCache.clear()

        // Base display sizes in dp
        val sizeMap = mapOf(
            GisElementType.SUBSTATION to (26f to SUBSTATION_SVG),
            GisElementType.TRANSFORMER to (19f to TRANSFORMER_SVG),
            GisElementType.SWITCH to (18f to SWITCH_SVG),
            GisElementType.REGULATOR to (18f to REGULATOR_SVG),
            GisElementType.CAPACITOR to (18f to CAPACITOR_SVG),
            GisElementType.TOWER to (21f to TOWER_SVG),
            GisElementType.POLE to (11f to POLE_SVG),
            GisElementType.OTHER to (11f to OTHER_SVG)
        )

        for ((type, pair) in sizeMap) {
            val (baseDp, svgText) = pair
            val pixelSize = (baseDp * density).toInt().coerceAtLeast(16)
            runCatching {
                val svg = SVG.getFromString(svgText)
                val bitmap = Bitmap.createBitmap(pixelSize, pixelSize, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)

                val origWidth = svg.documentWidth
                val origHeight = svg.documentHeight
                try {
                    svg.documentWidth = pixelSize.toFloat()
                    svg.documentHeight = pixelSize.toFloat()
                    svg.renderToCanvas(canvas)
                } finally {
                    svg.documentWidth = origWidth
                    svg.documentHeight = origHeight
                }

                val half = pixelSize / 2f
                iconCache[type] = GisSymbolIcon(
                    bitmap = bitmap,
                    halfWidth = half,
                    halfHeight = half,
                    touchRadius = half + 4f * density
                )
            }.onFailure { e ->
                Log.e("ShubGis", "Failed to render SVG symbol for $type: ${e.message}")
            }
        }
    }
}
