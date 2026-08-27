package com.saile.shub.gis.domain

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class BoundingBox(val minLon: Double, val maxLon: Double, val minLat: Double, val maxLat: Double)

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
        if (envelopeIndicator == 0) return null // No envelope
        
        val endianness = flags and 0b1
        val byteOrder = if (endianness == 1) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
        
        // The envelope starts at byte 8 (after GP, version, flags, and 32-bit SRS_ID)
        if (blob.size < 8 + 32) return null // Need at least 32 bytes for the envelope (4 doubles)
        
        val buffer = ByteBuffer.wrap(blob, 8, blob.size - 8)
        buffer.order(byteOrder)
        
        return try {
            val minX = buffer.double // min_lon
            val maxX = buffer.double // max_lon
            val minY = buffer.double // min_lat
            val maxY = buffer.double // max_lat
            
            BoundingBox(
                minLon = minX,
                maxLon = maxX,
                minLat = minY,
                maxLat = maxY
            )
        } catch (e: Exception) {
            null
        }
    }
}
