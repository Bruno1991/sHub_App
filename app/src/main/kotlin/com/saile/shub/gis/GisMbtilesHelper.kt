package com.saile.shub.gis

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.ContextCompat
import com.saile.shub.gis.domain.BoundingBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.IRegisterReceiver
import org.osmdroid.tileprovider.MapTileProviderArray
import org.osmdroid.tileprovider.modules.IArchiveFile
import org.osmdroid.tileprovider.modules.MapTileApproximater
import org.osmdroid.tileprovider.modules.MapTileFileArchiveProvider
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class MbtilesMetadata(
    val file: File,
    val name: String,
    val format: String,
    val minZoom: Int,
    val maxZoom: Int,
    val bounds: BoundingBox?,
    val sizeBytes: Long
) {
    val formattedSize: String
        get() {
            val mb = sizeBytes / (1024.0 * 1024.0)
            return if (mb >= 1024.0) {
                String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0)
            } else {
                String.format(java.util.Locale.US, "%.1f MB", mb)
            }
        }
}

object GisMbtilesHelper {

    private const val TAG = "ShubGisMbtiles"

    /**
     * Safe implementation of IRegisterReceiver for Android 14+ (API 34+).
     * Prevents SecurityException from unexported BroadcastReceivers in Osmdroid.
     */
    class SafeRegisterReceiver(private val context: Context) : IRegisterReceiver {
        override fun registerReceiver(receiver: BroadcastReceiver, filter: IntentFilter): Intent? {
            return try {
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            } catch (e: Exception) {
                Log.w(TAG, "SafeRegisterReceiver: ignored broadcast registration: ${e.message}")
                null
            }
        }

        override fun unregisterReceiver(receiver: BroadcastReceiver) {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Ignore if not registered or already removed
            }
        }

        override fun destroy() {
            // no-op
        }
    }

    /**
     * Highly resilient SQLite IArchiveFile implementation for .mbtiles.
     * Prevents connection pool closures and handles both standard TMS and XYZ row coordinates.
     */
    class RobustMbtilesArchive(val file: File) : IArchiveFile {
        private var database: SQLiteDatabase? = null
        private val lock = Any()

        init {
            openDb()
        }

        private fun openDb(): Boolean {
            synchronized(lock) {
                if (database?.isOpen == true) return true
                return try {
                    database = SQLiteDatabase.openDatabase(
                        file.absolutePath,
                        null,
                        SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
                    )
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open MBTiles database ${file.name}: ${e.message}")
                    false
                }
            }
        }

        override fun init(pFile: File) {
            synchronized(lock) {
                runCatching { database?.close() }
                database = SQLiteDatabase.openDatabase(
                    pFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
                )
            }
        }

        override fun getInputStream(pTileSource: ITileSource?, pMapTileIndex: Long): InputStream? {
            val zoom = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)

            // MBTiles 1.3 spec: tile_row is inverted TMS coordinate -> (2^zoom - 1) - y
            val tmsY = (1 shl zoom) - 1 - y

            synchronized(lock) {
                if (database?.isOpen != true) {
                    if (!openDb()) return null
                }
                val db = database ?: return null

                // 1. Try standard MBTiles TMS row
                var stream = queryTile(db, zoom, x, tmsY)
                // 2. Fallback to standard XYZ row if not found and tmsY != y
                if (stream == null && tmsY != y) {
                    stream = queryTile(db, zoom, x, y)
                }
                return stream
            }
        }

        private fun queryTile(db: SQLiteDatabase, zoom: Int, x: Int, y: Int): InputStream? {
            return try {
                db.rawQuery(
                    "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ? LIMIT 1",
                    arrayOf(zoom.toString(), x.toString(), y.toString())
                ).use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) {
                        val bytes = cursor.getBlob(0)
                        if (bytes != null && bytes.isNotEmpty()) {
                            ByteArrayInputStream(bytes)
                        } else null
                    } else null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error querying tile z=$zoom x=$x y=$y: ${e.message}")
                null
            }
        }

        override fun getTileSources(): Set<String> = emptySet()

        override fun setIgnoreTileSource(pIgnoreTileSource: Boolean) {
            // no-op
        }

        override fun close() {
            synchronized(lock) {
                runCatching { database?.close() }
                database = null
            }
        }
    }

    /**
     * Reads metadata and tile information from an SQLite .mbtiles database.
     */
    fun readMetadata(file: File): MbtilesMetadata? {
        if (!file.exists() || !file.canRead()) return null

        var db: SQLiteDatabase? = null
        return try {
            db = SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )

            var name = file.nameWithoutExtension
            var format = "jpg"
            var minZoom = 0
            var maxZoom = 20
            var bounds: BoundingBox? = null

            // Read metadata table if it exists
            runCatching {
                db.rawQuery("SELECT name, value FROM metadata", null).use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name")
                    val valIdx = cursor.getColumnIndex("value")
                    if (nameIdx >= 0 && valIdx >= 0) {
                        while (cursor.moveToNext()) {
                            val key = cursor.getString(nameIdx) ?: continue
                            val value = cursor.getString(valIdx) ?: continue
                            when (key.lowercase()) {
                                "name" -> if (value.isNotBlank()) name = value
                                "format" -> if (value.isNotBlank()) format = value.lowercase().trim()
                                "minzoom" -> minZoom = value.toIntOrNull() ?: minZoom
                                "maxzoom" -> maxZoom = value.toIntOrNull() ?: maxZoom
                                "bounds" -> {
                                    // MBTiles 1.3 spec: bounds = "left,bottom,right,top" (minLon, minLat, maxLon, maxLat)
                                    val parts = value.split(",").mapNotNull { it.trim().toDoubleOrNull() }
                                    if (parts.size == 4) {
                                        val (minLon, minLat, maxLon, maxLat) = parts
                                        bounds = BoundingBox(minLon = minLon, maxLon = maxLon, minLat = minLat, maxLat = maxLat)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Fallback: Check actual tile zoom range if metadata table lacks zoom info
            if (minZoom == 0 && maxZoom == 20) {
                runCatching {
                    db.rawQuery("SELECT MIN(zoom_level), MAX(zoom_level) FROM tiles", null).use { cursor ->
                        if (cursor.moveToFirst()) {
                            if (!cursor.isNull(0)) minZoom = cursor.getInt(0)
                            if (!cursor.isNull(1)) maxZoom = cursor.getInt(1)
                        }
                    }
                }
            }

            val ext = if (format.startsWith(".")) format else ".$format"
            MbtilesMetadata(
                file = file,
                name = name,
                format = ext.removePrefix("."),
                minZoom = minZoom,
                maxZoom = maxZoom,
                bounds = bounds,
                sizeBytes = file.length()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error reading MBTiles metadata from ${file.name}: ${e.message}")
            null
        } finally {
            runCatching { db?.close() }
        }
    }

    /**
     * Builds an authentic offline osmdroid MapTileProviderArray pointing to the MBTiles archive.
     */
    fun createTileProvider(context: Context, meta: MbtilesMetadata): MapTileProviderArray {
        val archive = RobustMbtilesArchive(meta.file)
        archive.setIgnoreTileSource(true)

        val cleanName = meta.name
            .replace(Regex("[^a-zA-Z0-9_]"), "_")
            .trim('_')
            .ifBlank { "mbtiles_${meta.file.nameWithoutExtension}" }

        val formatExt = if (meta.format.startsWith(".")) meta.format else ".${meta.format}"
        val tileSource = XYTileSource(
            cleanName,
            0,
            22,
            256,
            formatExt,
            emptyArray()
        )

        val registerReceiver = SafeRegisterReceiver(context.applicationContext)
        val archiveProvider = MapTileFileArchiveProvider(
            registerReceiver,
            tileSource,
            arrayOf(archive),
            true
        )

        val approximater = MapTileApproximater()
        approximater.addProvider(archiveProvider)

        return MapTileProviderArray(
            tileSource,
            registerReceiver,
            arrayOf(archiveProvider, approximater)
        )
    }

    /**
     * Scans device directories for .mbtiles files.
     */
    fun scanAvailableMbtiles(context: Context): List<MbtilesMetadata> {
        val searchDirs = listOf(
            File(context.filesDir, "mbtiles"),
            context.getExternalFilesDir(null),
            context.getExternalFilesDir("mbtiles"),
            File("/sdcard/Download"),
            File("/sdcard/Documents"),
            File("/sdcard/s_hub"),
            File("/sdcard/s_hub/maps")
        )

        val results = mutableListOf<MbtilesMetadata>()
        val seenPaths = mutableSetOf<String>()

        for (dir in searchDirs) {
            if (dir == null || !dir.exists() || !dir.isDirectory) continue
            val files = dir.listFiles { f -> f.extension.equals("mbtiles", ignoreCase = true) } ?: continue
            for (f in files) {
                if (f.isFile && f.canRead() && seenPaths.add(f.absolutePath)) {
                    val meta = readMetadata(f)
                    if (meta != null) {
                        results.add(meta)
                    }
                }
            }
        }

        return results.sortedByDescending { it.file.lastModified() }
    }

    /**
     * Imports an MBTiles file selected via SAF (Storage Access Framework) into the app's internal storage.
     */
    suspend fun importMbtilesFromUri(
        context: Context,
        uri: Uri,
        onProgress: (Int, Long, Long) -> Unit = { _, _, _ -> }
    ): MbtilesMetadata? = withContext(Dispatchers.IO) {
        runCatching {
            var fileName = "imported_${System.currentTimeMillis()}.mbtiles"
            var fileSize = -1L

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx >= 0) {
                        val name = cursor.getString(nameIdx)
                        if (!name.isNullOrBlank()) fileName = name
                    }
                    if (sizeIdx >= 0) {
                        fileSize = cursor.getLong(sizeIdx)
                    }
                }
            }

            val targetDir = File(context.filesDir, "mbtiles").apply { mkdirs() }
            val targetFile = File(targetDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var totalBytes = 0L
                    var bytesRead: Int
                    var lastPercent = -1

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        if (fileSize > 0) {
                            val percent = ((totalBytes * 100) / fileSize).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent, totalBytes, fileSize)
                            }
                        }
                    }
                    output.flush()
                }
            }

            readMetadata(targetFile)
        }.getOrNull()
    }
}
