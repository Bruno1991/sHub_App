package com.saile.shub.gis.domain

import com.saile.shub.core.geo.GeoCoordinate
import com.saile.shub.core.geo.Hemisphere
import com.saile.shub.core.geo.UtmCoordinate
import com.saile.shub.core.geo.UtmConverter
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class BoundingBox(
    val minLon: Double,
    val maxLon: Double,
    val minLat: Double,
    val maxLat: Double
) {
    val centroidLat: Double get() = (minLat + maxLat) / 2.0
    val centroidLon: Double get() = (minLon + maxLon) / 2.0
}

object GeometryHeaderParser {

    fun parseBoundingBox(blob: ByteArray): BoundingBox? {
        if (blob.size < 8) return null

        val magic0 = blob[0]
        val magic1 = blob[1]
        // "GP" ASCII = 0x47, 0x50
        if (magic0 != 0x47.toByte() || magic1 != 0x50.toByte()) {
            return null
        }

        val flags = blob[3].toInt()
        val emptyGeometry = (flags and 0b00010000) != 0
        if (emptyGeometry) return null

        val envelopeIndicator = (flags shr 1) and 0b111
        val endianness = flags and 0b1
        val headerByteOrder = if (endianness == 1) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN

        // Read 32-bit SRS_ID at offset 4
        val srsIdBuffer = ByteBuffer.wrap(blob, 4, 4).order(headerByteOrder)
        val srsId = srsIdBuffer.int

        var rawMinX: Double? = null
        var rawMaxX: Double? = null
        var rawMinY: Double? = null
        var rawMaxY: Double? = null

        if (envelopeIndicator in 1..4) {
            // Envelope starts at byte 8; need at least 32 bytes for 4 doubles
            if (blob.size >= 8 + 32) {
                val envBuffer = ByteBuffer.wrap(blob, 8, 32).order(headerByteOrder)
                rawMinX = envBuffer.double
                rawMaxX = envBuffer.double
                rawMinY = envBuffer.double
                rawMaxY = envBuffer.double
            }
        } else if (envelopeIndicator == 0) {
            // No envelope header: parse WKB starting at byte 8
            if (blob.size >= 8 + 21) {
                val wkbEndianByte = blob[8].toInt()
                val wkbByteOrder = if (wkbEndianByte == 1) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
                val wkbBuffer = ByteBuffer.wrap(blob, 9, blob.size - 9).order(wkbByteOrder)
                val geomType = wkbBuffer.int
                // 1 = Point, 1001 = PointZ, 2001 = PointM, 3001 = PointZM
                if (geomType == 1 || geomType == 1001 || geomType == 2001 || geomType == 3001) {
                    val x = wkbBuffer.double
                    val y = wkbBuffer.double
                    rawMinX = x
                    rawMaxX = x
                    rawMinY = y
                    rawMaxY = y
                }
            }
        }

        if (rawMinX == null || rawMaxX == null || rawMinY == null || rawMaxY == null) {
            return null
        }

        // Coordinate transformation if coordinates are UTM (e.g. EPSG:31980 / SIRGAS 2000 UTM Zone 20S)
        return if (srsId == 31980 || (rawMinX > 100_000.0 && rawMinY > 1_000_000.0)) {
            val zone = if (srsId in 31960..31985) srsId - 31960 else 20
            val p1 = runCatching {
                UtmConverter.utmToGeo(
                    UtmCoordinate(
                        zone = zone,
                        hemisphere = Hemisphere.SOUTH,
                        easting = rawMinX,
                        northing = rawMinY
                    )
                )
            }.getOrNull()

            val p2 = runCatching {
                UtmConverter.utmToGeo(
                    UtmCoordinate(
                        zone = zone,
                        hemisphere = Hemisphere.SOUTH,
                        easting = rawMaxX,
                        northing = rawMaxY
                    )
                )
            }.getOrNull()

            if (p1 != null && p2 != null) {
                BoundingBox(
                    minLon = minOf(p1.longitude, p2.longitude),
                    maxLon = maxOf(p1.longitude, p2.longitude),
                    minLat = minOf(p1.latitude, p2.latitude),
                    maxLat = maxOf(p1.latitude, p2.latitude)
                )
            } else if (p1 != null) {
                BoundingBox(minLon = p1.longitude, maxLon = p1.longitude, minLat = p1.latitude, maxLat = p1.latitude)
            } else {
                null
            }
        } else {
            // Already Geographic (Lon/Lat)
            BoundingBox(
                minLon = rawMinX,
                maxLon = rawMaxX,
                minLat = rawMinY,
                maxLat = rawMaxY
            )
        }
    }
}
