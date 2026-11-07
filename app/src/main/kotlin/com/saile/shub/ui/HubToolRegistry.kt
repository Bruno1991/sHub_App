package com.saile.shub.ui

import androidx.compose.ui.graphics.Color

/**
 * HubToolRegistry.kt
 *
 * Responsabilidade Única:
 * Contrato declarativo dos módulos ativos na tela inicial do sHub (sFoto e sGIS).
 * O conversor geodésico (UTM ⇄ GEO / DMS / reprojeção de fusos) agora está 100% integrado ao sGIS.
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
            id = "sfoto",
            destination = Destination.PHOTO,
            title = "sFoto",
            badge = "Câmera & Timestamp",
            description = "Câmera técnica de campo com carimbo de ativo/UC, coordenadas UTM/GEO, azimute, data/hora e logo oficial.",
            assetPath = "app_hub/sfoto.svg",
            accentGradient = listOf(Color(0xFFFF416C), Color(0xFFFF4B2B)),
        ),
        HubToolDescriptor(
            id = "sgis",
            destination = Destination.GIS,
            title = "sGIS",
            badge = "GPKG • MBTiles • UC • UTM",
            description = "Motor espacial offline GeoPackage R-Tree, satélite MBTiles HD, busca de clientes (UC/Medidor), conversor UTM ⇄ GEO e inspeção de campo.",
            assetPath = "app_hub/sgis.svg",
            accentGradient = listOf(Color(0xFF4E731B), Color(0xFF7AA136)),
        ),
    )
}
