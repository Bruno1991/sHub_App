package com.saile.shub.gis.domain

import com.saile.shub.core.geo.Hemisphere
import com.saile.shub.core.geo.UtmCoordinate
import com.saile.shub.core.geo.UtmConverter
import java.util.Locale

data class ParsedCoordinate(
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val description: String,
    val isUtm: Boolean
)

object CoordinateSearchParser {

    /**
     * Attempts to parse input string as either geographic or UTM coordinates.
     * Returns ParsedCoordinate if recognized, or null otherwise.
     */
    fun parse(input: String): ParsedCoordinate? {
        val trimmed = input.trim()
        if (trimmed.length < 3) return null

        // 1. Try parsing UTM format
        val utm = parseUtm(trimmed)
        if (utm != null) return utm

        // 2. Try parsing Geographic Decimal (Lat, Lon)
        val geo = parseGeographicDecimal(trimmed)
        if (geo != null) return geo

        return null
    }

    private fun parseUtm(input: String): ParsedCoordinate? {
        // Pattern 1: Zone + Hemisphere + Easting + Northing (e.g., "20S 569402 8767709" or "20L 569402.6 8767709.64")
        val withZoneRegex = Regex(
            """^(?:utm\s*)?(\d{1,2})\s*([C-XNSns])\s*[:=]?\s*(\d{5,7}(?:[.,]\d+)?)\s*[,;\s]\s*(\d{6,8}(?:[.,]\d+)?)$""",
            RegexOption.IGNORE_CASE
        )
        val matchWithZone = withZoneRegex.find(input)
        if (matchWithZone != null) {
            val zone = matchWithZone.groupValues[1].toIntOrNull() ?: 20
            val bandOrHemi = matchWithZone.groupValues[2].uppercase()
            val hemisphere = if (bandOrHemi in listOf("N", "P", "Q", "R", "S", "T", "U", "V", "W", "X") && bandOrHemi == "N") {
                Hemisphere.NORTH
            } else {
                Hemisphere.SOUTH
            }
            val easting = matchWithZone.groupValues[3].replace(',', '.').toDoubleOrNull() ?: return null
            val northing = matchWithZone.groupValues[4].replace(',', '.').toDoubleOrNull() ?: return null

            if (easting in 100_000.0..1_000_000.0 && northing in 0.0..10_000_000.0) {
                val geo = runCatching {
                    UtmConverter.utmToGeo(UtmCoordinate(zone, hemisphere, easting, northing))
                }.getOrNull()

                if (geo != null) {
                    val title = "Coordenada UTM: ${zone}S %.0fm E, %.0fm N".format(Locale.US, easting, northing)
                    val desc = "Localização: %.6f, %.6f".format(Locale.US, geo.latitude, geo.longitude)
                    return ParsedCoordinate(geo.latitude, geo.longitude, title, desc, isUtm = true)
                }
            }
        }

        // Pattern 2: Two numbers without zone, where Easting is 100k..999k and Northing is 1M..10M
        // (Defaults to Zone 20S - Standard grid for Rondonia and Western Amazon)
        val rawNumbersRegex = Regex(
            """^(?:x\s*[:=]\s*)?(\d{5,7}(?:[.,]\d+)?)\s*[,;\s]\s*(?:y\s*[:=]\s*)?(\d{6,8}(?:[.,]\d+)?)$""",
            RegexOption.IGNORE_CASE
        )
        val matchRaw = rawNumbersRegex.find(input)
        if (matchRaw != null) {
            val num1 = matchRaw.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
            val num2 = matchRaw.groupValues[2].replace(',', '.').toDoubleOrNull() ?: return null

            // Determine which is easting and which is northing
            val easting: Double
            val northing: Double
            if (num1 in 100_000.0..999_999.0 && num2 in 1_000_000.0..10_000_000.0) {
                easting = num1
                northing = num2
            } else if (num2 in 100_000.0..999_999.0 && num1 in 1_000_000.0..10_000_000.0) {
                easting = num2
                northing = num1
            } else {
                return null
            }

            val geo = runCatching {
                UtmConverter.utmToGeo(UtmCoordinate(20, Hemisphere.SOUTH, easting, northing))
            }.getOrNull()

            if (geo != null) {
                val title = "Coordenada UTM (Zona 20S): %.0fm E, %.0fm N".format(Locale.US, easting, northing)
                val desc = "Localização Geográfica: %.6f, %.6f".format(Locale.US, geo.latitude, geo.longitude)
                return ParsedCoordinate(geo.latitude, geo.longitude, title, desc, isUtm = true)
            }
        }

        return null
    }

    private fun parseGeographicDecimal(input: String): ParsedCoordinate? {
        // Decimal coordinates, e.g. "-11.434, -61.447" or "-11.434 -61.447" or "11.434S, 61.447W"
        val regex = Regex(
            """^([+-]?\d{1,2}(?:[.,]\d+)?)\s*([NSns])?\s*[,;\s]\s*([+-]?\d{1,3}(?:[.,]\d+)?)\s*([EOWLeowl])?$"""
        )
        val match = regex.find(input) ?: return null

        var lat = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val latHemi = match.groupValues[2].uppercase()
        if (latHemi == "S" && lat > 0) lat = -lat

        var lon = match.groupValues[3].replace(',', '.').toDoubleOrNull() ?: return null
        val lonHemi = match.groupValues[4].uppercase()
        if ((lonHemi == "W" || lonHemi == "O") && lon > 0) lon = -lon

        if (lat in -90.0..90.0 && lon in -180.0..180.0) {
            val title = "Coordenada Geográfica: %.6f, %.6f".format(Locale.US, lat, lon)
            val utm = runCatching { UtmConverter.geoToUtm(com.saile.shub.core.geo.GeoCoordinate(lat, lon)) }.getOrNull()
            val desc = if (utm != null) {
                "UTM: ${utm.zone}S %.0fm E, %.0fm N".format(Locale.US, utm.easting, utm.northing)
            } else {
                "Latitude: %.6f | Longitude: %.6f".format(Locale.US, lat, lon)
            }
            return ParsedCoordinate(lat, lon, title, desc, isUtm = false)
        }

        return null
    }
}

