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
import org.osmdroid.tileprovider.modules.MapTileFileArchiveProvider
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * GisMbtilesHelper.kt
 *
 * Responsabilidade Única:
 * Núcleo de leitura, decodificação SQLite, conversão TMS/XYZ e construção de provedores
 * de ladrilhos osmdroid (MapTileProviderArray) a partir de pacotes raster .mbtiles locais,
 * de forma 100% imparcial ao nome do arquivo importado.
 */

data class MbtilesMetadata(
    val file: File,
    val name: String,
    val format: String,
    val minZoom: Int,
    val maxZoom: Int,
    val bounds: BoundingBox?,
    val sizeBytes: Long,
    val category: PackageCategory = PackageCategory.CUSTOM
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

    val importDateFormatted: String
        get() {
            return runCatching {
                val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                sdf.format(java.util.Date(file.lastModified()))
            }.getOrDefault("--/--/----")
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
     * Uses strict OGC MBTiles 1.3 TMS row orientation unless metadata explicitly declares scheme=xyz.
     */
    class RobustMbtilesArchive(val file: File) : IArchiveFile {
        private var database: SQLiteDatabase? = null
        private val lock = Any()
        private var hitCounter = 0L
        private var isXyzScheme = false

        init {
            openDb()
        }

        private fun openDb(): Boolean {
            synchronized(lock) {
                if (database?.isOpen == true) return true
                return try {
                    val db = SQLiteDatabase.openDatabase(
                        file.absolutePath,
                        null,
                        SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
                    )
                    database = db
                    isXyzScheme = detectXyzScheme(db)
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open MBTiles database ${file.name}: ${e.message}")
                    false
                }
            }
        }

        private fun detectXyzScheme(db: SQLiteDatabase): Boolean {
            return runCatching {
                db.rawQuery(
                    "SELECT value FROM metadata WHERE LOWER(name) = 'scheme' LIMIT 1",
                    null
                ).use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) {
                        cursor.getString(0)?.trim()?.equals("xyz", ignoreCase = true) == true
                    } else false
                }
            }.getOrDefault(false)
        }

        override fun init(pFile: File) {
            synchronized(lock) {
                runCatching { database?.close() }
                val db = SQLiteDatabase.openDatabase(
                    pFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
                )
                database = db
                isXyzScheme = detectXyzScheme(db)
            }
        }

        override fun getInputStream(pTileSource: ITileSource?, pMapTileIndex: Long): InputStream? {
            val zoom = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)

            // MBTiles 1.3 spec: tile_row is inverted TMS coordinate -> (2^zoom - 1) - y
            val targetRow = if (isXyzScheme) y else ((1 shl zoom) - 1 - y)

            synchronized(lock) {
                if (database?.isOpen != true) {
                    if (!openDb()) return null
                }
                val db = database ?: return null

                val stream = queryTile(db, zoom, x, targetRow)
                if (stream != null && (hitCounter++ % 25L == 0L)) {
                    Log.d(TAG, "MBTiles HIT: z=$zoom x=$x y=$y (row=$targetRow) [${file.name}]")
                }
                return stream
            }
        }

        private fun queryTile(db: SQLiteDatabase, zoom: Int, x: Int, row: Int): InputStream? {
            return try {
                db.rawQuery(
                    "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ? LIMIT 1",
                    arrayOf(zoom.toString(), x.toString(), row.toString())
                ).use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) {
                        val bytes = cursor.getBlob(0)
                        if (bytes != null && bytes.isNotEmpty()) {
                            ByteArrayInputStream(bytes)
                        } else null
                    } else null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error querying tile z=$zoom x=$x row=$row: ${e.message}")
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
     * Infers operational package category purely from zoom levels and metadata,
     * completely impartial to specific filenames or city names.
     */
    fun inferCategory(minZoom: Int, maxZoom: Int, bounds: BoundingBox?): PackageCategory {
        val lonSpan = bounds?.let { kotlin.math.abs(it.maxLon - it.minLon) } ?: 360.0
        return when {
            minZoom >= 14 && maxZoom >= 17 -> PackageCategory.NETWORK_CORRIDORS
            maxZoom >= 17 && lonSpan < 5.0 -> PackageCategory.MUNICIPAL_HD
            maxZoom >= 12 && lonSpan < 30.0 -> PackageCategory.REGIONAL
            minZoom == 0 && lonSpan >= 180.0 -> PackageCategory.WORLD_BASE
            else -> PackageCategory.CUSTOM
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

            runCatching {
                db.rawQuery("SELECT MIN(zoom_level), MAX(zoom_level) FROM tiles", null).use { cursor ->
                    if (cursor.moveToFirst()) {
                        if (!cursor.isNull(0)) minZoom = cursor.getInt(0)
                        if (!cursor.isNull(1)) maxZoom = cursor.getInt(1)
                    }
                }
            }

            val ext = if (format.startsWith(".")) format else ".$format"
            val category = inferCategory(minZoom, maxZoom, bounds)
            MbtilesMetadata(
                file = file,
                name = name,
                format = ext.removePrefix("."),
                minZoom = minZoom,
                maxZoom = maxZoom,
                bounds = bounds,
                sizeBytes = file.length(),
                category = category
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error reading MBTiles metadata from ${file.name}: ${e.message}")
            null
        } finally {
            runCatching { db?.close() }
        }
    }

    /**
     * Builds an authentic offline osmdroid MapTileProviderArray pointing to a single MBTiles archive,
     * wrapped in ControlledFallbackArchive with quadrant cropping up to 8 zoom levels above maxZoom.
     */
    fun createTileProvider(
        context: Context,
        meta: MbtilesMetadata,
        enableFallback: Boolean = true
    ): MapTileProviderArray {
        val rawArchive = RobustMbtilesArchive(meta.file)
        rawArchive.setIgnoreTileSource(true)

        val controlledArchive = ControlledFallbackArchive(
            delegate = rawArchive,
            maxDownscaleLevels = 8,
            enableFallback = enableFallback
        )

        val cleanName = "mbtiles_${meta.file.nameWithoutExtension.replace(Regex("[^a-zA-Z0-9_]"), "_")}"
            .trim('_')
            .ifBlank { "offline_mbtiles" }

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
            arrayOf(controlledArchive),
            true
        )

        return MapTileProviderArray(
            tileSource,
            registerReceiver,
            arrayOf(archiveProvider)
        )
    }

    /**
     * Builds a composite multi-archive tile provider placing the active selected package first.
     */
    fun createCompositeTileProvider(
        context: Context,
        packages: List<MbtilesMetadata>,
        primaryMeta: MbtilesMetadata? = null,
        enableFallback: Boolean = true
    ): MapTileProviderArray {
        if (packages.isEmpty()) {
            throw IllegalArgumentException("Nenhum pacote MBTiles fornecido para provedor composto")
        }

        val sorted = packages.sortedWith(
            compareByDescending<MbtilesMetadata> { it.file.absolutePath == primaryMeta?.file?.absolutePath }
                .thenBy { it.category.priority }
                .thenByDescending { it.sizeBytes }
        )

        val rawArchives = sorted.map { RobustMbtilesArchive(it.file) }
        val composite = CompositeMbtilesArchive(rawArchives)

        val controlledArchive = ControlledFallbackArchive(
            delegate = composite,
            maxDownscaleLevels = 8,
            enableFallback = enableFallback
        )

        val primary = sorted.first()
        val cleanName = "composite_${primary.file.nameWithoutExtension.replace(Regex("[^a-zA-Z0-9_]"), "_")}"
        val formatExt = if (primary.format.startsWith(".")) primary.format else ".${primary.format}"

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
            arrayOf(controlledArchive),
            true
        )

        return MapTileProviderArray(
            tileSource,
            registerReceiver,
            arrayOf(archiveProvider)
        )
    }

    /**
     * Scans app-scoped directories for .mbtiles files, prioritizing internal imported files first.
     */
    fun scanAvailableMbtiles(context: Context): List<MbtilesMetadata> {
        val internalDir = File(context.filesDir, "mbtiles")
        val extRoot = context.getExternalFilesDir(null)
        val searchDirs = listOfNotNull(
            internalDir,
            extRoot,
            extRoot?.let { File(it, "mbtiles") },
            context.getExternalFilesDir("mbtiles")
        )

        val results = mutableListOf<MbtilesMetadata>()
        val seenPaths = mutableSetOf<String>()

        for (dir in searchDirs) {
            runCatching {
                if (!dir.exists() || !dir.isDirectory) return@runCatching
                val files = dir.listFiles { f -> f.extension.equals("mbtiles", ignoreCase = true) } ?: return@runCatching
                for (f in files) {
                    if (f.isFile && f.canRead() && seenPaths.add(f.absolutePath)) {
                        val meta = readMetadata(f)
                        if (meta != null) {
                            results.add(meta)
                        }
                    }
                }
            }
        }

        return results.sortedWith(
            compareByDescending<MbtilesMetadata> { it.file.parentFile?.absolutePath == internalDir.absolutePath }
                .thenByDescending { it.file.lastModified() }
                .thenByDescending { it.sizeBytes }
        )
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
            var fileName = "mapa_${System.currentTimeMillis()}.mbtiles"
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
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank {
                "mapa_${System.currentTimeMillis()}.mbtiles"
            }
            val targetFile = File(targetDir, safeName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(128 * 1024)
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

            targetFile.setLastModified(System.currentTimeMillis())
            targetDir.listFiles()?.forEach { f ->
                if (f.isFile && f.absolutePath != targetFile.absolutePath) {
                    runCatching { f.delete() }
                }
            }

            readMetadata(targetFile)
        }.getOrNull()
    }
}
