package com.saile.shub.gis.domain

import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject

data class GpkgFeature(
    val sourceLayer: String,
    val featureSourceId: String?,
    val geometryType: String?,
    val bbox: BoundingBox?,
    val metadataJson: String
)

class GpkgReader(private val gpkgFilePath: String) : AutoCloseable {
    private var db: SQLiteDatabase? = null

    init {
        db = SQLiteDatabase.openDatabase(
            gpkgFilePath,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        )
    }

    fun getLayers(): List<String> {
        val layers = mutableListOf<String>()
        val cursor = db?.rawQuery("SELECT table_name FROM gpkg_contents WHERE data_type = 'features'", null)
        cursor?.use {
            while (it.moveToNext()) {
                layers.add(it.getString(0))
            }
        }
        return layers
    }

    suspend fun readFeatures(layerName: String, onFeatureBatch: suspend (List<GpkgFeature>) -> Unit) {
        val database = db ?: return
        
        // Find geometry column name
        var geomColumnName = "geom"
        database.rawQuery("SELECT column_name FROM gpkg_geometry_columns WHERE table_name = ?", arrayOf(layerName)).use { cursor ->
            if (cursor.moveToFirst()) {
                geomColumnName = cursor.getString(0)
            }
        }
        
        val batchSize = 500
        val batch = mutableListOf<GpkgFeature>()
        
        database.rawQuery("SELECT * FROM \"$layerName\"", null).use { cursor ->
            val columnNames = cursor.columnNames
            val geomIndex = columnNames.indexOf(geomColumnName)
            val pkIndex = columnNames.indexOfFirst { it.equals("fid", ignoreCase = true) || it.equals("id", ignoreCase = true) }
            
            while (cursor.moveToNext()) {
                var featureId: String? = null
                if (pkIndex >= 0) {
                    featureId = cursor.getString(pkIndex)
                }
                
                var bbox: BoundingBox? = null
                if (geomIndex >= 0 && !cursor.isNull(geomIndex)) {
                    val blob = cursor.getBlob(geomIndex)
                    bbox = GeometryHeaderParser.parseBoundingBox(blob)
                }
                
                val properties = JSONObject()
                for (i in 0 until cursor.columnCount) {
                    if (i != geomIndex) {
                        properties.put(columnNames[i], cursor.getString(i))
                    }
                }
                
                batch.add(
                    GpkgFeature(
                        sourceLayer = layerName,
                        featureSourceId = featureId,
                        geometryType = null, 
                        bbox = bbox,
                        metadataJson = properties.toString()
                    )
                )
                
                if (batch.size >= batchSize) {
                    onFeatureBatch(batch.toList())
                    batch.clear()
                }
            }
        }
        
        if (batch.isNotEmpty()) {
            onFeatureBatch(batch)
        }
    }

    override fun close() {
        db?.close()
        db = null
    }
}
