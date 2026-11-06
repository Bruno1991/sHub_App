package com.saile.shub.gis

import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory

/**
 * GisBasemapHelper.kt
 *
 * Responsabilidade Única:
 * Definir as opções de mapa base suportadas pelo módulo sGis:
 * - MBTiles Offline (.mbtiles): pacote raster local 100% offline.
 * - Mapa de Ruas (OSM): camada cartográfica padrão OpenStreetMap (Mapnik).
 */
enum class BasemapType(val displayName: String, val subtitle: String) {
    OFFLINE_MBTILES("MBTiles Offline (.mbtiles)", "Pacote raster de satélite/mapa 100% offline"),
    STREETS("Mapa de Ruas (OSM)", "Vias, edificações e mapa cartográfico OpenStreetMap")
}

object GisTileSources {
    fun getTileSource(type: BasemapType): ITileSource {
        return when (type) {
            BasemapType.STREETS -> TileSourceFactory.MAPNIK
            BasemapType.OFFLINE_MBTILES -> TileSourceFactory.MAPNIK
        }
    }
}
