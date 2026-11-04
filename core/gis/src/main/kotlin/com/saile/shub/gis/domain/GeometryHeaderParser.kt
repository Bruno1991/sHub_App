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

sealed class ParsedGeometry {
    data class Point(val lat: Double, val lon: Double) : ParsedGeometry()
    data class Line(val points: List<Pair<Double, Double>>) : ParsedGeometry() // (lat, lon)
    data class MultiLine(val lines: List<List<Pair<Double, Double>>>) : ParsedGeometry()
}

object GeometryHeaderParser {

    fun parseBoundingBox(blob: ByteArray): BoundingBox? {
        if (blob.size < 8) return null

        val magic0 = blob[0]
        val magic1 = blob[1]
        if (magic0 != 0x47.toByte() || magic1 != 0x50.toByte()) {
            return null
        }

        val flags = blob[3].toInt()
        val emptyGeometry = (flags and 0b00010000) != 0
        if (emptyGeometry) return null

        val envelopeIndicator = (flags shr 1) and 0b111
        val endianness = flags and 0b1
        val headerByteOrder = if (endianness == 1) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN

        val srsIdBuffer = ByteBuffer.wrap(blob, 4, 4).order(headerByteOrder)
        val srsId = srsIdBuffer.int

        var rawMinX: Double? = null
        var rawMaxX: Double? = null
        var rawMinY: Double? = null
        var rawMaxY: Double? = null

        if (envelopeIndicator in 1..4) {
            if (blob.size >= 8 + 32) {
                val envBuffer = ByteBuffer.wrap(blob, 8, 32).order(headerByteOrder)
                rawMinX = envBuffer.double
                rawMaxX = envBuffer.double
                rawMinY = envBuffer.double
                rawMaxY = envBuffer.double
            }
        } else if (envelopeIndicator == 0) {
            if (blob.size >= 8 + 21) {
                val wkbEndianByte = blob[8].toInt()
                val wkbByteOrder = if (wkbEndianByte == 1) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
                val wkbBuffer = ByteBuffer.wrap(blob, 9, blob.size - 9).order(wkbByteOrder)
                val geomType = wkbBuffer.int
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

        return if (srsId == 31980 || srsId in 31960..31985 || (rawMinX > 10_000.0 && rawMinY > 1_000_000.0)) {
            val zone = if (srsId in 31960..31985) srsId - 31960 else 20
            val p1 = runCatching {
                UtmConverter.utmToGeo(
                    UtmCoordinate(zone = zone, hemisphere = Hemisphere.SOUTH, easting = rawMinX, northing = rawMinY)
                )
            }.getOrNull()

            val p2 = runCatching {
                UtmConverter.utmToGeo(
                    UtmCoordinate(zone = zone, hemisphere = Hemisphere.SOUTH, easting = rawMaxX, northing = rawMaxY)
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
            BoundingBox(minLon = rawMinX, maxLon = rawMaxX, minLat = rawMinY, maxLat = rawMaxY)
        }
    }

    fun parseGeometry(blob: ByteArray): ParsedGeometry? {
        if (blob.size < 8) return null
        if (blob[0] != 0x47.toByte() || blob[1] != 0x50.toByte()) return null

        val flags = blob[3].toInt()
        if ((flags and 0b00010000) != 0) return null

        val envelopeIndicator = (flags shr 1) and 0b111
        val endianness = flags and 0b1
        val headerByteOrder = if (endianness == 1) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN

        val srsId = ByteBuffer.wrap(blob, 4, 4).order(headerByteOrder).int

        val envLen = when (envelopeIndicator) {
            1 -> 32
            2, 3 -> 48
            4 -> 64
            else -> 0
        }

        val wkbOffset = 8 + envLen
        if (blob.size < wkbOffset + 5) return null

        val wkbEndian = blob[wkbOffset].toInt()
        val wkbByteOrder = if (wkbEndian == 1) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN

        val buffer = ByteBuffer.wrap(blob, wkbOffset + 1, blob.size - (wkbOffset + 1)).order(wkbByteOrder)
        val geomType = buffer.int

        fun toLatLon(x: Double, y: Double): Pair<Double, Double>? {
            return if (srsId == 31980 || srsId in 31960..31985 || (x > 10_000.0 && y > 1_000_000.0)) {
                val zone = if (srsId in 31960..31985) srsId - 31960 else 20
                runCatching {
                    val p = UtmConverter.utmToGeo(
                        UtmCoordinate(zone = zone, hemisphere = Hemisphere.SOUTH, easting = x, northing = y)
                    )
                    Pair(p.latitude, p.longitude)
                }.getOrNull()
            } else {
                Pair(y, x) // y=lat, x=lon
            }
        }

        return when (geomType) {
            1, 1001, 2001, 3001 -> {
                val x = buffer.double
                val y = buffer.double
                val pt = toLatLon(x, y) ?: return null
                ParsedGeometry.Point(pt.first, pt.second)
            }
            2, 1002, 2002, 3002 -> {
                val numPts = buffer.int
                val hasZ = geomType == 1002 || geomType == 3002
                val hasM = geomType == 2002 || geomType == 3002
                val pts = mutableListOf<Pair<Double, Double>>()
                for (i in 0 until numPts) {
                    val x = buffer.double
                    val y = buffer.double
                    if (hasZ) buffer.double
                    if (hasM) buffer.double
                    toLatLon(x, y)?.let { pts.add(it) }
                }
                ParsedGeometry.Line(pts)
            }
            5, 1005, 2005, 3005 -> {
                val numLines = buffer.int
                val lines = mutableListOf<List<Pair<Double, Double>>>()
                for (l in 0 until numLines) {
                    if (buffer.remaining() < 9) break
                    val lineEndianByte = buffer.get().toInt()
                    val lineOrder = if (lineEndianByte == 1) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
                    val lineBuffer = buffer.order(lineOrder)
                    val lineType = lineBuffer.int
                    val numPts = lineBuffer.int
                    val hasZ = lineType == 1002 || lineType == 3002
                    val hasM = lineType == 2002 || lineType == 3002
                    val pts = mutableListOf<Pair<Double, Double>>()
                    for (i in 0 until numPts) {
                        if (lineBuffer.remaining() < 16) break
                        val x = lineBuffer.double
                        val y = lineBuffer.double
                        if (hasZ && lineBuffer.remaining() >= 8) lineBuffer.double
                        if (hasM && lineBuffer.remaining() >= 8) lineBuffer.double
                        toLatLon(x, y)?.let { pts.add(it) }
                    }
                    if (pts.isNotEmpty()) lines.add(pts)
                }
                ParsedGeometry.MultiLine(lines)
            }
            3, 1003, 2003, 3003 -> {
                val numRings = buffer.int
                val lines = mutableListOf<List<Pair<Double, Double>>>()
                for (r in 0 until numRings) {
                    if (buffer.remaining() < 4) break
                    val numPts = buffer.int
                    val hasZ = geomType == 1003 || geomType == 3003
                    val hasM = geomType == 2003 || geomType == 3003
                    val pts = mutableListOf<Pair<Double, Double>>()
                    for (i in 0 until numPts) {
                        if (buffer.remaining() < 16) break
                        val x = buffer.double
                        val y = buffer.double
                        if (hasZ && buffer.remaining() >= 8) buffer.double
                        if (hasM && buffer.remaining() >= 8) buffer.double
                        toLatLon(x, y)?.let { pts.add(it) }
                    }
                    if (pts.isNotEmpty()) lines.add(pts)
                }
                ParsedGeometry.MultiLine(lines)
            }
            6, 1006, 2006, 3006 -> {
                val numPolys = buffer.int
                val lines = mutableListOf<List<Pair<Double, Double>>>()
                for (p in 0 until numPolys) {
                    if (buffer.remaining() < 9) break
                    val polyEndianByte = buffer.get().toInt()
                    val polyOrder = if (polyEndianByte == 1) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
                    val polyBuffer = buffer.order(polyOrder)
                    val polyType = polyBuffer.int
                    val numRings = polyBuffer.int
                    val hasZ = polyType == 1003 || polyType == 3003
                    val hasM = polyType == 2003 || polyType == 3003
                    for (r in 0 until numRings) {
                        if (polyBuffer.remaining() < 4) break
                        val numPts = polyBuffer.int
                        val pts = mutableListOf<Pair<Double, Double>>()
                        for (i in 0 until numPts) {
                            if (polyBuffer.remaining() < 16) break
                            val x = polyBuffer.double
                            val y = polyBuffer.double
                            if (hasZ && polyBuffer.remaining() >= 8) polyBuffer.double
                            if (hasM && polyBuffer.remaining() >= 8) polyBuffer.double
                            toLatLon(x, y)?.let { pts.add(it) }
                        }
                        if (pts.isNotEmpty()) lines.add(pts)
                    }
                }
                ParsedGeometry.MultiLine(lines)
            }
            else -> {
                val bbox = parseBoundingBox(blob) ?: return null
                ParsedGeometry.Point(bbox.centroidLat, bbox.centroidLon)
            }
        }
    }
}
