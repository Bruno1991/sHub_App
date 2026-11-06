package com.saile.shub.ui

import androidx.compose.ui.graphics.Color

/**
 * Metadata contract describing a field tool module registered in sHub.
 *
 * Designed so new domain tools (e.g., sEletrica, sGPS, sInventario, sMedicao,
 * sRelatorio, sInspecao) can be registered declaratively without coupling HomeScreen
 * to individual feature implementations.
 */
data class HubToolDescriptor(
    val id: String,
    val destination: Destination,
    val title: String,
    val badge: String,
    val description: String,
    val assetPath: String,
    val accentGradient: List<Color>,
    val enabled: Boolean = true,
)

object HubToolRegistry {
    val tools: List<HubToolDescriptor> = listOf(
        HubToolDescriptor(
            id = "sgeoconversor",
            destination = Destination.CONVERTER,
            title = "sGeoConversor",
            badge = "UTM ⇄ GEO",
            description = "Conversão rápida UTM/GEO, re-projeção entre fusos (20S/21S), DMS e rotas.",
            assetPath = "app_hub/sgeoconversor.svg",
            accentGradient = listOf(Color(0xFF6366F1), Color(0xFF3B82F6), Color(0xFF06B6D4)),
        ),
        HubToolDescriptor(
            id = "sfoto",
            destination = Destination.PHOTO,
            title = "sFoto",
            badge = "Câmera & Timestamp",
            description = "Câmera de campo com carimbo de coordenadas UTM, GEO, data/hora, endereço e logo oficial.",
            assetPath = "app_hub/sfoto.svg",
            accentGradient = listOf(Color(0xFFFF416C), Color(0xFFFF4B2B)),
        ),
        HubToolDescriptor(
            id = "sgis",
            destination = Destination.GIS,
            title = "sGIS",
            badge = "QField • GPKG & MBTiles",
            description = "Motor espacial offline GeoPackage R-Tree, basemap satélite MBTiles multinível, GPS e medição.",
            assetPath = "app_hub/sgis.svg",
            accentGradient = listOf(Color(0xFF4E731B), Color(0xFF7AA136)),
        ),
    )
}

