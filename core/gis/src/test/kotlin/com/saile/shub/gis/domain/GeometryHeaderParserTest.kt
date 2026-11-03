package com.saile.shub.gis.domain

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
}
