package com.saile.shub.gis.domain

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GeometryHeaderParserTest {
    @Test
    fun testParseBoundingBox() {
        val buffer = ByteBuffer.allocate(40)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        
        // Magic "GP"
        buffer.put(0x47.toByte())
        buffer.put(0x50.toByte())
        // Version
        buffer.put(0)
        // Flags (Little Endian = bit 0(1), Envelope 1 = bit 1-3(1<<1 = 2)) -> 1 | 2 = 3
        buffer.put(3.toByte())
        // SRS_ID
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
}
