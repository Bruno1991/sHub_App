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
          <circle cx="24" cy="24" r="21" fill="#1C1B1F" stroke="#FFFFFF" stroke-width="3.5"/>
          <!-- Núcleo magnético laminado central -->
          <line x1="22.5" y1="12" x2="22.5" y2="36" stroke="#FFFFFF" stroke-width="2" stroke-linecap="round"/>
          <line x1="25.5" y1="12" x2="25.5" y2="36" stroke="#FFFFFF" stroke-width="2" stroke-linecap="round"/>
          <!-- Bobina Esquerda (MT): espiras curvadas em direção ao núcleo -->
          <path d="M 13 12 C 20 12, 20 18, 13 18 C 20 18, 20 24, 13 24 C 20 24, 20 30, 13 30 C 20 30, 20 36, 13 36" fill="none" stroke="#FFD600" stroke-width="2.8" stroke-linecap="round"/>
          <!-- Bobina Direita (BT): espiras curvadas em direção ao núcleo -->
          <path d="M 35 12 C 28 12, 28 18, 35 18 C 28 18, 28 24, 35 24 C 28 24, 28 30, 35 30 C 28 30, 28 36, 35 36" fill="none" stroke="#FFD600" stroke-width="2.8" stroke-linecap="round"/>
        </svg>
    """.trimIndent()

    private val SWITCH_NF_SVG = """
        <svg viewBox="0 0 48 48" width="48" height="48" xmlns="http://www.w3.org/2000/svg">
          <circle cx="24" cy="24" r="21" fill="#D50000" stroke="#FFFFFF" stroke-width="3.5"/>
          <circle cx="13" cy="24" r="4" fill="#FFFFFF"/>
          <circle cx="35" cy="24" r="4" fill="#FFFFFF"/>
          <line x1="13" y1="20" x2="13" y2="28" stroke="#FFFFFF" stroke-width="2.5" stroke-linecap="round"/>
          <line x1="35" y1="20" x2="35" y2="28" stroke="#FFFFFF" stroke-width="2.5" stroke-linecap="round"/>
          <line x1="13" y1="24" x2="35" y2="24" stroke="#FFFFFF" stroke-width="4.5" stroke-linecap="round"/>
        </svg>
    """.trimIndent()

    private val SWITCH_NA_SVG = """
        <svg viewBox="0 0 48 48" width="48" height="48" xmlns="http://www.w3.org/2000/svg">
          <circle cx="24" cy="24" r="21" fill="#00C853" stroke="#FFFFFF" stroke-width="3.5"/>
          <circle cx="13" cy="24" r="4" fill="#FFFFFF"/>
          <circle cx="35" cy="24" r="4" fill="#FFFFFF"/>
          <line x1="13" y1="20" x2="13" y2="28" stroke="#FFFFFF" stroke-width="2.5" stroke-linecap="round"/>
          <line x1="35" y1="20" x2="35" y2="28" stroke="#FFFFFF" stroke-width="2.5" stroke-linecap="round"/>
          <line x1="13" y1="24" x2="30" y2="10" stroke="#FFFFFF" stroke-width="4.5" stroke-linecap="round"/>
        </svg>
    """.trimIndent()

    // Regulador de Tensão comutador de TAP sob carga: Badge RT com setas Buck / Boost
    private val REGULATOR_SVG = """
        <svg viewBox="0 0 48 48" width="48" height="48" xmlns="http://www.w3.org/2000/svg">
          <circle cx="24" cy="24" r="21" fill="#FF6D00" stroke="#FFFFFF" stroke-width="3.5"/>
          <!-- Letra R -->
          <path d="M 16 17 V 31 M 16 17 H 21 C 24 17 24 23 21 23 H 16 M 20 23 L 24 31" fill="none" stroke="#FFFFFF" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"/>
          <!-- Letra T -->
          <path d="M 26 17 H 34 M 30 17 V 31" fill="none" stroke="#FFFFFF" stroke-width="2.8" stroke-linecap="round"/>
          <!-- Setas de Elevação (Boost) e Rebaixamento (Buck) de Tensão -->
          <path d="M 19 12 L 24 8 L 29 12 M 19 36 L 24 40 L 29 36" fill="none" stroke="#FFFFFF" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
    """.trimIndent()

    // Banco de Capacitores em Média Tensão: Placas Verticais em Linha
    private val CAPACITOR_SVG = """
        <svg viewBox="0 0 48 48" width="48" height="48" xmlns="http://www.w3.org/2000/svg">
          <circle cx="24" cy="24" r="21" fill="#0091EA" stroke="#FFFFFF" stroke-width="3.5"/>
          <line x1="10" y1="24" x2="20" y2="24" stroke="#FFFFFF" stroke-width="3.5" stroke-linecap="round"/>
          <line x1="20" y1="13" x2="20" y2="35" stroke="#FFFFFF" stroke-width="4" stroke-linecap="round"/>
          <line x1="28" y1="13" x2="28" y2="35" stroke="#FFFFFF" stroke-width="4" stroke-linecap="round"/>
          <line x1="28" y1="24" x2="38" y2="24" stroke="#FFFFFF" stroke-width="3.5" stroke-linecap="round"/>
        </svg>
    """.trimIndent()

    // Torre de Transmissão (Linhas AT): Treliça Metálica Autoportante com Cruzetas
    private val TOWER_SVG = """
        <svg viewBox="0 0 48 48" width="48" height="48" xmlns="http://www.w3.org/2000/svg">
          <circle cx="24" cy="24" r="21" fill="#212121" stroke="#FFFFFF" stroke-width="3"/>
          <!-- Ponta para cabo OPGW / para-raios -->
          <line x1="24" y1="8" x2="24" y2="13" stroke="#FFFFFF" stroke-width="2.5" stroke-linecap="round"/>
          <!-- Braços / Cruzeta da torre (suporte das cadeias de isoladores) -->
          <line x1="10" y1="17" x2="38" y2="17" stroke="#FFFFFF" stroke-width="3" stroke-linecap="round"/>
          <line x1="13" y1="23" x2="35" y2="23" stroke="#FFFFFF" stroke-width="2.5" stroke-linecap="round"/>
          <!-- Tronco piramidal da torre -->
          <path d="M 24 13 L 15 39 M 24 13 L 33 39" stroke="#FFFFFF" stroke-width="2.5" stroke-linecap="round"/>
          <!-- Diagonais em X da treliça -->
          <line x1="17" y1="27" x2="31" y2="35" stroke="#FFFFFF" stroke-width="1.8"/>
          <line x1="31" y1="27" x2="17" y2="35" stroke="#FFFFFF" stroke-width="1.8"/>
          <!-- Base dos pés da torre -->
          <line x1="14" y1="39" x2="34" y2="39" stroke="#FFFFFF" stroke-width="2.5" stroke-linecap="round"/>
        </svg>
    """.trimIndent()

    // Authentic concrete Double-T pole profile (Poste Duplo T - ABNT / Concessionárias)
    private val POLE_DT_SVG = """
        <svg viewBox="0 0 32 32" width="32" height="32" xmlns="http://www.w3.org/2000/svg">
          <path d="M 6 6 H 26 V 11 H 19 V 21 H 26 V 26 H 6 V 21 H 13 V 11 H 6 Z" fill="#FFFFFF" stroke="#212121" stroke-width="2.5" stroke-linejoin="round"/>
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
        return iconCache[type] ?: iconCache[GisElementType.SWITCH]?.takeIf { type == GisElementType.SWITCH_NF || type == GisElementType.SWITCH_NA } ?: iconCache[GisElementType.OTHER]
    }

    private fun initIcons(density: Float) {
        currentDensity = density
        iconCache.clear()

        // Base display sizes in dp (optimized for clear map readability)
        val sizeMap = mapOf(
            GisElementType.SUBSTATION to (26f to SUBSTATION_SVG),
            GisElementType.TRANSFORMER to (19f to TRANSFORMER_SVG),
            GisElementType.SWITCH to (18f to SWITCH_NF_SVG),
            GisElementType.SWITCH_NF to (18f to SWITCH_NF_SVG),
            GisElementType.SWITCH_NA to (18f to SWITCH_NA_SVG),
            GisElementType.REGULATOR to (18f to REGULATOR_SVG),
            GisElementType.CAPACITOR to (18f to CAPACITOR_SVG),
            GisElementType.TOWER to (21f to TOWER_SVG),
            GisElementType.POLE to (14f to POLE_DT_SVG),
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

