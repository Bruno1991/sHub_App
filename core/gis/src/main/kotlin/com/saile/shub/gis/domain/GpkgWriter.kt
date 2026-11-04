package com.saile.shub.gis.domain

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.saile.shub.core.geo.GeoCoordinate
import com.saile.shub.core.geo.UtmConverter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GpkgWriter(private val database: SQLiteDatabase) {

    /**
     * Builds a standard OGC GeoPackage binary geometry blob for a 2D Point.
     * Header (8 bytes) + Envelope (32 bytes) + WKB Point (21 bytes) = 61 bytes.
     */
    fun createPointBlob(srsId: Int, x: Double, y: Double): ByteArray {
        val totalSize = 8 + 32 + 21
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // Magic GP
        buffer.put(0x47.toByte()) // 'G'
        buffer.put(0x50.toByte()) // 'P'
        buffer.put(0x00.toByte()) // version 0
        // Flags: Little-endian (bit 0 = 1), envelope indicator = 1 (bits 1-3 = 001) -> (1 << 1) | 1 = 3
        buffer.put(0x03.toByte())
        buffer.putInt(srsId)

        // Envelope: minx, maxx, miny, maxy
        buffer.putDouble(x)
        buffer.putDouble(x)
        buffer.putDouble(y)
        buffer.putDouble(y)

        // WKB Point
        buffer.put(0x01.toByte()) // Little-endian WKB
        buffer.putInt(1)          // WKB Point type = 1
        buffer.putDouble(x)
        buffer.putDouble(y)

        return buffer.array()
    }

    /**
     * Inserts a new point asset into a GeoPackage layer.
     * Updates both the feature table and spatial R-Tree index.
     */
    fun insertPointFeature(
        layerName: String,
        latitude: Double,
        longitude: Double,
        attributes: Map<String, Any?>
    ): Long {
        var geomColumn = "geom"
        var srsId = 4326

        database.rawQuery(
            "SELECT column_name, srs_id FROM gpkg_geometry_columns WHERE table_name = ?",
            arrayOf(layerName)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                geomColumn = cursor.getString(0)
                srsId = cursor.getInt(1)
            }
        }

        // Determine coordinates according to layer CRS
        val (coordX, coordY) = if (srsId == 31980 || srsId in 31960..31985) {
            val zone = if (srsId in 31960..31985) srsId - 31960 else 20
            val utm = UtmConverter.geoToUtm(GeoCoordinate(latitude, longitude), forcedZone = zone)
            Pair(utm.easting, utm.northing)
        } else {
            Pair(longitude, latitude)
        }

        val geomBlob = createPointBlob(srsId, coordX, coordY)

        val cv = ContentValues()
        cv.put(geomColumn, geomBlob)
        for ((key, value) in attributes) {
            if (key.equals("fid", ignoreCase = true) || key.equals(geomColumn, ignoreCase = true)) continue
            when (value) {
                null -> cv.putNull(key)
                is String -> cv.put(key, value)
                is Int -> cv.put(key, value)
                is Long -> cv.put(key, value)
                is Double -> cv.put(key, value)
                is Float -> cv.put(key, value)
                is Boolean -> cv.put(key, if (value) 1 else 0)
                else -> cv.put(key, value.toString())
            }
        }

        val newFid = database.insertOrThrow("\"$layerName\"", null, cv)

        // Insert into spatial R-Tree if present
        val rtreeName = "rtree_${layerName}_${geomColumn}"
        var hasRtree = false
        database.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(rtreeName)
        ).use { cursor ->
            if (cursor.moveToFirst()) hasRtree = true
        }

        if (hasRtree && newFid > 0) {
            val rtreeCv = ContentValues().apply {
                put("id", newFid)
                put("minx", coordX)
                put("maxx", coordX)
                put("miny", coordY)
                put("maxy", coordY)
            }
            runCatching {
                database.insert(rtreeName, null, rtreeCv)
            }
        }

        return newFid
    }

    /**
     * Updates non-geometry attributes of an existing feature.
     */
    fun updateFeatureAttributes(
        layerName: String,
        fid: Long,
        attributes: Map<String, Any?>
    ): Boolean {
        var geomColumn = "geom"
        database.rawQuery(
            "SELECT column_name FROM gpkg_geometry_columns WHERE table_name = ?",
            arrayOf(layerName)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                geomColumn = cursor.getString(0)
            }
        }

        val cv = ContentValues()
        for ((key, value) in attributes) {
            if (key.equals("fid", ignoreCase = true) || key.equals(geomColumn, ignoreCase = true)) continue
            when (value) {
                null -> cv.putNull(key)
                is String -> cv.put(key, value)
                is Int -> cv.put(key, value)
                is Long -> cv.put(key, value)
                is Double -> cv.put(key, value)
                is Float -> cv.put(key, value)
                is Boolean -> cv.put(key, if (value) 1 else 0)
                else -> cv.put(key, value.toString())
            }
        }

        val rows = database.update("\"$layerName\"", cv, "fid = ?", arrayOf(fid.toString()))
        return rows > 0
    }

    /**
     * Deletes a feature from the layer table and its spatial R-Tree index.
     */
    fun deleteFeature(layerName: String, fid: Long): Boolean {
        var geomColumn = "geom"
        database.rawQuery(
            "SELECT column_name FROM gpkg_geometry_columns WHERE table_name = ?",
            arrayOf(layerName)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                geomColumn = cursor.getString(0)
            }
        }

        val rtreeName = "rtree_${layerName}_${geomColumn}"
        runCatching {
            database.delete(rtreeName, "id = ?", arrayOf(fid.toString()))
        }

        val rows = database.delete("\"$layerName\"", "fid = ?", arrayOf(fid.toString()))
        return rows > 0
    }
}

