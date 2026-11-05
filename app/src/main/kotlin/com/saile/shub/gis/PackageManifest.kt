package com.saile.shub.gis

import com.saile.shub.gis.domain.BoundingBox
import java.io.File

/**
 * PackageCategory.kt / PackageManifest.kt
 *
 * Responsabilidade Única:
 * Define os metadados estruturados, integridade criptográfica (SHA-256),
 * origem de licenciamento e prioridade operacional de pacotes MBTiles no sGis.
 * Implementação pura em Kotlin sem dependência de stubs android.jar para execução em testes JVM.
 */
enum class PackageCategory(val priority: Int, val displayName: String) {
    NETWORK_CORRIDORS(1, "Corredores & Ativos Elétricos HD"),
    MUNICIPAL_HD(2, "Polo Municipal HD"),
    REGIONAL(3, "Cobertura Regional"),
    WORLD_BASE(4, "Base Mundial"),
    CUSTOM(5, "Personalizado")
}

data class PackageManifest(
    val id: String,
    val name: String,
    val version: String,
    val category: PackageCategory,
    val format: String,
    val minZoom: Int,
    val maxZoom: Int,
    val bounds: BoundingBox?,
    val sizeBytes: Long,
    val sha256: String,
    val sourceOrigin: String,
    val license: String,
    val attribution: String
) {
    fun toJson(): String {
        val boundsPart = if (bounds != null) {
            ",\n  \"bounds\": \"${bounds.minLon},${bounds.minLat},${bounds.maxLon},${bounds.maxLat}\""
        } else ""

        return """{
  "id": "$id",
  "name": "$name",
  "version": "$version",
  "category": "${category.name}",
  "format": "$format",
  "minZoom": $minZoom,
  "maxZoom": $maxZoom,
  "sizeBytes": $sizeBytes,
  "sha256": "$sha256",
  "sourceOrigin": "$sourceOrigin",
  "license": "$license",
  "attribution": "$attribution"$boundsPart
}"""
    }

    companion object {
        fun fromJson(jsonStr: String): PackageManifest? = runCatching {
            fun extractString(key: String): String {
                val pattern = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"")
                return pattern.find(jsonStr)?.groupValues?.get(1) ?: ""
            }
            fun extractLong(key: String): Long {
                val pattern = Regex("\"$key\"\\s*:\\s*(\\d+)")
                return pattern.find(jsonStr)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            }
            fun extractInt(key: String): Int {
                val pattern = Regex("\"$key\"\\s*:\\s*(\\d+)")
                return pattern.find(jsonStr)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            }

            val id = extractString("id").ifBlank { return null }
            val name = extractString("name")
            val version = extractString("version").ifBlank { "1.0.0" }
            val catStr = extractString("category")
            val category = runCatching { PackageCategory.valueOf(catStr) }.getOrDefault(PackageCategory.CUSTOM)
            val format = extractString("format").ifBlank { "jpg" }
            val minZoom = extractInt("minZoom")
            val maxZoom = extractInt("maxZoom")
            val sizeBytes = extractLong("sizeBytes")
            val sha256 = extractString("sha256")
            val sourceOrigin = extractString("sourceOrigin")
            val license = extractString("license")
            val attribution = extractString("attribution")
            val boundsStr = extractString("bounds")

            var bounds: BoundingBox? = null
            if (boundsStr.isNotBlank()) {
                val parts = boundsStr.split(",").mapNotNull { it.trim().toDoubleOrNull() }
                if (parts.size == 4) {
                    bounds = BoundingBox(parts[0], parts[2], parts[1], parts[3])
                }
            }

            PackageManifest(
                id = id,
                name = name,
                version = version,
                category = category,
                format = format,
                minZoom = minZoom,
                maxZoom = maxZoom,
                bounds = bounds,
                sizeBytes = sizeBytes,
                sha256 = sha256,
                sourceOrigin = sourceOrigin,
                license = license,
                attribution = attribution
            )
        }.getOrNull()
    }
}
