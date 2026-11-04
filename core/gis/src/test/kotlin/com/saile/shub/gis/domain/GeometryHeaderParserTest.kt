package com.saile.shub.gis.domain

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GeometryHeaderParserTest {

    @Test
    fun testParseBoundingBoxGeographic() {
        val buffer = ByteBuffer.allocate(40)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // Magic "GP"
        buffer.put(0x47.toByte())
        buffer.put(0x50.toByte())
        // Version
        buffer.put(0)
        // Flags (Little Endian = 1, Envelope 1 = 2) -> 3
        buffer.put(3.toByte())
        // SRS_ID = 4326
        buffer.putInt(4326)
        // Envelope (minX, maxX, minY, maxY)
        buffer.putDouble(-60.0)
        buffer.putDouble(-50.0)
        buffer.putDouble(-20.0)
        buffer.putDouble(-10.0)

        val blob = buffer.array()
        val bbox = GeometryHeaderParser.parseBoundingBox(blob)

        assertNotNull(bbox)
        assertEquals(-60.0, bbox.minLon)
        assertEquals(-50.0, bbox.maxLon)
        assertEquals(-20.0, bbox.minLat)
        assertEquals(-10.0, bbox.maxLat)
    }

    @Test
    fun testParsePointWithZeroEnvelopeUtmZone20S() {
        // 8 bytes header + 1 byte endian + 4 bytes geom type (1 = Point) + 16 bytes (X, Y) = 29 bytes
        val buffer = ByteBuffer.allocate(29)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(0x47.toByte())
        buffer.put(0x50.toByte())
        buffer.put(0.toByte()) // version
        buffer.put(1.toByte()) // flags: little endian = 1, envelope = 0
        buffer.putInt(31980)   // SRS_ID = 31980 (SIRGAS 2000 UTM 20S)

        // WKB Point
        buffer.put(1.toByte()) // Little endian WKB
        buffer.putInt(1)       // Geometry type 1 = Point
        buffer.putDouble(569402.6)   // Easting
        buffer.putDouble(8767709.64)  // Northing

        val blob = buffer.array()
        val bbox = GeometryHeaderParser.parseBoundingBox(blob)

        assertNotNull(bbox)
        // Check converted lat/lon in Rondonia
        assertTrue(bbox.centroidLat < -10.0 && bbox.centroidLat > -13.0, "Latitude should be ~ -11.14")
        assertTrue(bbox.centroidLon < -59.0 && bbox.centroidLon > -63.0, "Longitude should be ~ -60.36")
    }

    @Test
    fun testParseGeometryPointWgs84() {
        val buffer = ByteBuffer.allocate(29)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(0x47.toByte())
        buffer.put(0x50.toByte())
        buffer.put(0.toByte())
        buffer.put(1.toByte())
        buffer.putInt(4326)

        buffer.put(1.toByte())
        buffer.putInt(1)
        buffer.putDouble(-62.366) // X (lon)
        buffer.putDouble(-11.127) // Y (lat)

        val geom = GeometryHeaderParser.parseGeometry(buffer.array())
        assertNotNull(geom)
        assertTrue(geom is ParsedGeometry.Point)
        assertEquals(-11.127, (geom as ParsedGeometry.Point).lat, 0.001)
        assertEquals(-62.366, geom.lon, 0.001)
    }

    @Test
    fun testParseGeometryLineStringGeographic() {
        // 8 header + 32 env + 1 wkb endian + 4 geom type + 4 num pts + 32 pts = 81 bytes
        val buffer = ByteBuffer.allocate(81)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(0x47.toByte())
        buffer.put(0x50.toByte())
        buffer.put(0.toByte())
        buffer.put(3.toByte()) // envelope 1
        buffer.putInt(4674)

        // 32 bytes env
        buffer.putDouble(-62.0)
        buffer.putDouble(-61.9)
        buffer.putDouble(-10.0)
        buffer.putDouble(-9.9)

        // WKB LineString
        buffer.put(1.toByte())
        buffer.putInt(2) // LineString
        buffer.putInt(2) // 2 points
        buffer.putDouble(-62.0) // pt1 lon
        buffer.putDouble(-10.0) // pt1 lat
        buffer.putDouble(-61.9) // pt2 lon
        buffer.putDouble(-9.9)  // pt2 lat

        val geom = GeometryHeaderParser.parseGeometry(buffer.array())
        assertNotNull(geom)
        assertTrue(geom is ParsedGeometry.Line)
        assertEquals(2, (geom as ParsedGeometry.Line).points.size)
        assertEquals(-10.0, geom.points[0].first, 0.001)
        assertEquals(-62.0, geom.points[0].second, 0.001)
    }

    @Test
    fun testParseGeometryPolygonGeographic() {
        // 8 header + 1 wkb endian + 4 geom type (3) + 4 num rings (1) + 4 num pts (3) + 48 pts = 69 bytes
        val buffer = ByteBuffer.allocate(69)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(0x47.toByte())
        buffer.put(0x50.toByte())
        buffer.put(0.toByte())
        buffer.put(1.toByte()) // envelope 0
        buffer.putInt(4674)

        buffer.put(1.toByte())
        buffer.putInt(3) // Polygon
        buffer.putInt(1) // 1 ring
        buffer.putInt(3) // 3 pts
        buffer.putDouble(-62.0)
        buffer.putDouble(-10.0)
        buffer.putDouble(-61.9)
        buffer.putDouble(-9.9)
        buffer.putDouble(-62.0)
        buffer.putDouble(-10.0)

        val geom = GeometryHeaderParser.parseGeometry(buffer.array())
        assertNotNull(geom)
        assertTrue(geom is ParsedGeometry.MultiLine)
        assertEquals(1, (geom as ParsedGeometry.MultiLine).lines.size)
        assertEquals(3, geom.lines[0].size)
    }

    @Test
    fun testParseLineStringZUtm31980() {
        // 8 header + 1 wkb endian + 4 type (1002) + 4 num pts (2) + 48 pts (X, Y, Z each 24 bytes) = 65 bytes
        val buffer = ByteBuffer.allocate(65).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(0x47.toByte())
        buffer.put(0x50.toByte())
        buffer.put(0.toByte())
        buffer.put(1.toByte()) // envelope 0
        buffer.putInt(31980)

        buffer.put(1.toByte())
        buffer.putInt(1002) // LineStringZ
        buffer.putInt(2)
        // pt 1
        buffer.putDouble(569402.6)
        buffer.putDouble(8767709.64)
        buffer.putDouble(150.0) // Z
        // pt 2
        buffer.putDouble(569500.0)
        buffer.putDouble(8767800.0)
        buffer.putDouble(152.0) // Z

        val geom = GeometryHeaderParser.parseGeometry(buffer.array())
        assertNotNull(geom)
        assertTrue(geom is ParsedGeometry.Line)
        assertEquals(2, (geom as ParsedGeometry.Line).points.size)
        assertTrue(geom.points[0].first < -10.0 && geom.points[0].first > -13.0)
    }

    @Test
    fun testParseMultiPointGeographic() {
        // 8 header + 1 wkb endian + 4 type (4) + 4 num pts (2) + 2*(1 endian + 4 type + 16 XY = 21) = 59 bytes
        val buffer = ByteBuffer.allocate(59).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(0x47.toByte())
        buffer.put(0x50.toByte())
        buffer.put(0.toByte())
        buffer.put(1.toByte()) // envelope 0
        buffer.putInt(4674)

        buffer.put(1.toByte())
        buffer.putInt(4) // MultiPoint
        buffer.putInt(2) // 2 points

        // Subpoint 1
        buffer.put(1.toByte())
        buffer.putInt(1)
        buffer.putDouble(-62.1)
        buffer.putDouble(-10.1)

        // Subpoint 2
        buffer.put(1.toByte())
        buffer.putInt(1)
        buffer.putDouble(-62.2)
        buffer.putDouble(-10.2)

        val geom = GeometryHeaderParser.parseGeometry(buffer.array())
        assertNotNull(geom)
        assertTrue(geom is ParsedGeometry.MultiPoint)
        assertEquals(2, (geom as ParsedGeometry.MultiPoint).points.size)
        assertEquals(-10.1, geom.points[0].first, 0.001)
        assertEquals(-62.1, geom.points[0].second, 0.001)
    }

    @Test
    fun testTruncatedBlobDoesNotThrow() {
        val truncated = byteArrayOf(0x47, 0x50, 0, 1, 0, 0, 0x7C.toByte(), 0xFC.toByte(), 1, 1, 0, 0)
        val geom = GeometryHeaderParser.parseGeometry(truncated)
        assertNull(geom)
    }
}
