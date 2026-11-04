package com.saile.shub.gis

import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex

enum class BasemapType(val displayName: String, val subtitle: String) {
    GOOGLE_HYBRID("Google Satélite Híbrido", "Satélite com vias e rótulos (Online / Cache)"),
    GOOGLE_SATELLITE("Google Satélite Puro", "Imagens aéreas de alta definição (Online / Cache)"),
    OFFLINE_MBTILES("MBTiles Offline (Local)", "Pacote raster de satélite/mapa 100% offline"),
    ESRI_SATELLITE("Esri World Imagery", "Satélite global de alta definição ArcGIS (Online / Cache)"),
    STREETS("Mapa de Ruas (OSM)", "Vias, edificações e mapa cartográfico (Online / Cache)"),
    TOPO("Topografia e Relevo", "Curvas de nível e relevo sombreado OpenTopoMap (Online / Cache)")
}

object GisTileSources {

    // Google Maps Satélite Híbrido (Satélite com estradas e nomes de cidades)
    val GOOGLE_HYBRID: OnlineTileSourceBase = object : OnlineTileSourceBase(
        "GoogleHybrid",
        0,
        21,
        256,
        ".jpg",
        arrayOf(
            "https://mt0.google.com/vt/lyrs=y",
            "https://mt1.google.com/vt/lyrs=y",
            "https://mt2.google.com/vt/lyrs=y",
            "https://mt3.google.com/vt/lyrs=y"
        )
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val zoom = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "$baseUrl&x=$x&y=$y&z=$zoom"
        }
    }

    // Google Maps Satélite Puro (Apenas imagem ortofoto sem sobreposições)
    val GOOGLE_SATELLITE: OnlineTileSourceBase = object : OnlineTileSourceBase(
        "GoogleSatellite",
        0,
        21,
        256,
        ".jpg",
        arrayOf(
            "https://mt0.google.com/vt/lyrs=s",
            "https://mt1.google.com/vt/lyrs=s",
            "https://mt2.google.com/vt/lyrs=s",
            "https://mt3.google.com/vt/lyrs=s"
        )
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val zoom = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "$baseUrl&x=$x&y=$y&z=$zoom"
        }
    }

    // Esri World Imagery (ArcGIS Online)
    val ESRI_SATELLITE: OnlineTileSourceBase = object : OnlineTileSourceBase(
        "EsriWorldImagery",
        0,
        19,
        256,
        ".jpg",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val zoom = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "$baseUrl$zoom/$y/$x"
        }
    }

    // OpenTopoMap (Relevo sombreado e curvas de nível)
    val OPEN_TOPO: OnlineTileSourceBase = object : OnlineTileSourceBase(
        "OpenTopoMap",
        0,
        17,
        256,
        ".png",
        arrayOf("https://a.tile.opentopomap.org/", "https://b.tile.opentopomap.org/", "https://c.tile.opentopomap.org/")
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val zoom = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "$baseUrl$zoom/$x/$y.png"
        }
    }

    fun getTileSource(type: BasemapType): ITileSource {
        return when (type) {
            BasemapType.GOOGLE_HYBRID -> GOOGLE_HYBRID
            BasemapType.GOOGLE_SATELLITE -> GOOGLE_SATELLITE
            BasemapType.ESRI_SATELLITE -> ESRI_SATELLITE
            BasemapType.STREETS -> TileSourceFactory.MAPNIK
            BasemapType.TOPO -> OPEN_TOPO
            BasemapType.OFFLINE_MBTILES -> TileSourceFactory.MAPNIK
        }
    }
}
