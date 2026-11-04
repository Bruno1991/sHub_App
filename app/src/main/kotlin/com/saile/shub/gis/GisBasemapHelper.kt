package com.saile.shub.gis

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

enum class BasemapType(val displayName: String, val iconName: String) {
    SATELLITE("Satélite Alta Resolução", "satellite"),
    STREETS("Mapa de Ruas (OSM)", "streets"),
    TOPO("Topografia e Relevo", "topo"),
    OFFLINE_MBTILES("MBTiles Offline", "offline")
}

object GisTileSources {
    val ESRI_SATELLITE = object : OnlineTileSourceBase(
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

    val OPEN_TOPO = object : OnlineTileSourceBase(
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
}

