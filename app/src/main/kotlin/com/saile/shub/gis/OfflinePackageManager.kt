package com.saile.shub.gis

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * OfflinePackageManager.kt
 *
 * Responsabilidade Única:
 * Gerenciador de ciclo de vida, validação criptográfica (SHA-256), integridade SQLite
 * e instalação atômica de pacotes de satélite MBTiles para o modo 100% offline do sGis.
 */
object OfflinePackageManager {

    private const val TAG = "OfflinePackageManager"

    data class ValidationResult(
        val isValid: Boolean,
        val sha256: String,
        val tileCount: Long,
        val minZoom: Int,
        val maxZoom: Int,
        val format: String,
        val errors: List<String>
    )

    data class InstallResult(
        val success: Boolean,
        val installedFile: File?,
        val metadata: MbtilesMetadata?,
        val errorMessage: String? = null
    )

    /**
     * Calcula o hash criptográfico SHA-256 do arquivo.
     */
    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(1024 * 1024)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        val bytes = digest.digest()
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    /**
     * Valida integralmente a estrutura do arquivo MBTiles (SQLite, metadados, SHA-256 e tiles).
     */
    fun validatePackage(file: File, expectedSha256: String? = null): ValidationResult {
        val errors = mutableListOf<String>()
        if (!file.exists() || !file.canRead()) {
            return ValidationResult(false, "", 0, 0, 0, "", listOf("Arquivo não existe ou não legível"))
        }

        val sha256 = try {
            computeSha256(file)
        } catch (e: Exception) {
            errors.add("Falha no cálculo de SHA-256: ${e.message}")
            ""
        }

        if (!expectedSha256.isNullOrBlank() && !sha256.equals(expectedSha256.trim(), ignoreCase = true)) {
            errors.add("Divergência de Checksum SHA-256. Esperado: $expectedSha256, Obtido: $sha256")
        }

        var db: SQLiteDatabase? = null
        var tileCount = 0L
        var minZoom = 0
        var maxZoom = 0
        var format = "jpg"

        try {
            db = SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )

            // 1. Verificação de integridade estrutural
            db.rawQuery("PRAGMA quick_check", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    val status = cursor.getString(0)
                    if (!status.equals("ok", ignoreCase = true)) {
                        errors.add("Falha no PRAGMA quick_check: $status")
                    }
                } else {
                    errors.add("PRAGMA quick_check não retornou resposta")
                }
            }

            // 2. Contagem e limites de zoom
            db.rawQuery("SELECT COUNT(*), MIN(zoom_level), MAX(zoom_level) FROM tiles", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    tileCount = cursor.getLong(0)
                    minZoom = cursor.getInt(1)
                    maxZoom = cursor.getInt(2)
                }
            }

            if (tileCount <= 0L) {
                errors.add("O pacote MBTiles não contém nenhum ladrilho na tabela 'tiles'")
            }

            // 3. Formato nos metadados
            runCatching {
                db.rawQuery("SELECT value FROM metadata WHERE name = 'format' LIMIT 1", null).use { cursor ->
                    if (cursor.moveToFirst()) {
                        format = cursor.getString(0) ?: "jpg"
                    }
                }
            }

        } catch (e: Exception) {
            errors.add("Erro na análise SQLite: ${e.message}")
        } finally {
            runCatching { db?.close() }
        }

        val isValid = errors.isEmpty()
        return ValidationResult(isValid, sha256, tileCount, minZoom, maxZoom, format, errors)
    }

    /**
     * Executa instalação atômica e segura de um novo pacote MBTiles a partir de uma URI.
     */
    suspend fun installPackageFromUri(
        context: Context,
        uri: Uri,
        targetFileName: String,
        expectedSha256: String? = null,
        onProgress: (Int) -> Unit = {}
    ): InstallResult = withContext(Dispatchers.IO) {
        val targetDir = File(context.filesDir, "mbtiles").apply { mkdirs() }
        val targetFile = File(targetDir, targetFileName)
        val tempFile = File(targetDir, "$targetFileName.downloading_${System.currentTimeMillis()}.tmp")

        try {
            // 1. Copiar bytes para arquivo temporário
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                    }
                    output.flush()
                }
            } ?: return@withContext InstallResult(false, null, null, "Não foi possível abrir URI de entrada")

            // 2. Validação estrita do arquivo temporário
            val validation = validatePackage(tempFile, expectedSha256)
            if (!validation.isValid) {
                tempFile.delete()
                val err = validation.errors.joinToString("; ")
                Log.e(TAG, "Instalação rejeitada por falha de validação: $err")
                return@withContext InstallResult(false, null, null, err)
            }

            // 3. Substituição atômica: preserva pacote anterior se substituição falhar
            val backupFile = File(targetDir, "$targetFileName.backup_${System.currentTimeMillis()}")
            if (targetFile.exists()) {
                if (!targetFile.renameTo(backupFile)) {
                    tempFile.delete()
                    return@withContext InstallResult(false, null, null, "Não foi possível criar backup do pacote existente")
                }
            }

            if (!tempFile.renameTo(targetFile)) {
                // Restaura o backup
                backupFile.renameTo(targetFile)
                tempFile.delete()
                return@withContext InstallResult(false, null, null, "Falha na transição atômica do arquivo temporário")
            }

            // Sucesso: descarta backup antigo
            backupFile.delete()

            val meta = GisMbtilesHelper.readMetadata(targetFile)
            Log.i(TAG, "Pacote instalado com sucesso: ${targetFile.name} (${targetFile.length()} bytes, SHA-256=${validation.sha256})")
            return@withContext InstallResult(true, targetFile, meta)

        } catch (e: Exception) {
            tempFile.delete()
            Log.e(TAG, "Erro na instalação do pacote: ${e.message}", e)
            return@withContext InstallResult(false, null, null, e.message)
        }
    }

    /**
     * Converte Latitude/Longitude em coordenadas Slippy Map (X, Y) para o nível de zoom informado.
     */
    fun latLonToTileXY(latDeg: Double, lonDeg: Double, zoom: Int): Pair<Int, Int> {
        val latRad = Math.toRadians(latDeg.coerceIn(-85.0511, 85.0511))
        val n = (1 shl zoom).toDouble()
        val x = (((lonDeg + 180.0) / 360.0) * n).toInt().coerceIn(0, (1 shl zoom) - 1)
        val y = (((1.0 - kotlin.math.ln(kotlin.math.tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / Math.PI) / 2.0) * n)
            .toInt()
            .coerceIn(0, (1 shl zoom) - 1)
        return x to y
    }

    /**
     * Sincroniza e baixa em segundo plano os ladrilhos de satélite híbrido (lyrs=y&hl=pt-BR)
     * que ainda faltam no arquivo .mbtiles local para cobrir a região do .gpkg ativo.
     */
    suspend fun syncGpkgAreaTilesToMbtiles(
        gpkgFile: File,
        mbtilesFile: File,
        centerLat: Double,
        centerLon: Double,
        onProgress: (downloaded: Int, total: Int) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        if (!gpkgFile.exists() || !mbtilesFile.exists()) return@withContext 0
        val reader = com.saile.shub.gis.domain.GpkgReaderProvider.getOrOpen(gpkgFile.absolutePath) ?: return@withContext 0
        val extent = reader.getDatasetExtent() ?: return@withContext 0

        val targetTiles = LinkedHashSet<Triple<Int, Int, Int>>()

        // 1. Cobertura regional da caixa do GPKG nos zooms 10 e 11 (limitado para execução rápida em campo)
        for (z in 10..11) {
            val (x1, y2) = latLonToTileXY(extent.minLat, extent.minLon, z)
            val (x2, y1) = latLonToTileXY(extent.maxLat, extent.maxLon, z)
            val minX = minOf(x1, x2)
            val maxX = maxOf(x1, x2)
            val minY = minOf(y1, y2)
            val maxY = maxOf(y1, y2)
            if ((maxX - minX + 1L) * (maxY - minY + 1L) <= 600L) {
                for (x in minX..maxX) {
                    for (y in minY..maxY) {
                        targetTiles.add(Triple(z, x, y))
                    }
                }
            }
        }

        // 2. Cobertura HD (zooms 12 a 17) ao redor do foco atual e dos elementos visíveis do GPKG
        val sampleElements = reader.getFeaturesInBoundingBox(
            minLat = centerLat - 0.08,
            maxLat = centerLat + 0.08,
            minLon = centerLon - 0.08,
            maxLon = centerLon + 0.08,
            zoomLevel = 15.0
        )

        val focusPoints = mutableListOf(centerLat to centerLon)
        for (el in sampleElements.take(300)) {
            when (el) {
                is com.saile.shub.gis.domain.GisRenderElement.Point -> focusPoints.add(el.latitude to el.longitude)
                is com.saile.shub.gis.domain.GisRenderElement.Line -> el.points.firstOrNull()?.let { focusPoints.add(it) }
            }
        }

        for ((lat, lon) in focusPoints) {
            for (z in 12..16) {
                val (cx, cy) = latLonToTileXY(lat, lon, z)
                for (dx in -1..1) {
                    for (dy in -1..1) {
                        targetTiles.add(Triple(z, cx + dx, cy + dy))
                    }
                }
            }
            val (x17, y17) = latLonToTileXY(lat, lon, 17)
            targetTiles.add(Triple(17, x17, y17))
        }

        var db: SQLiteDatabase? = null
        var downloadedCount = 0
        try {
            db = SQLiteDatabase.openDatabase(mbtilesFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            val existing = HashSet<Triple<Int, Int, Int>>()
            for ((z, x, y) in targetTiles) {
                val tmsY = (1 shl z) - 1 - y
                db.rawQuery(
                    "SELECT 1 FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ? LIMIT 1",
                    arrayOf(z.toString(), x.toString(), tmsY.toString())
                ).use { c ->
                    if (c.moveToFirst()) {
                        existing.add(Triple(z, x, y))
                    }
                }
            }

            val missing = targetTiles.filterNot { existing.contains(it) }
            val totalMissing = missing.size
            onProgress(0, totalMissing)
            if (totalMissing == 0) return@withContext 0

            val stmt = db.compileStatement("INSERT OR IGNORE INTO tiles (zoom_level, tile_column, tile_row, tile_data) VALUES (?, ?, ?, ?)")
            for ((idx, tile) in missing.withIndex()) {
                val (z, x, y) = tile
                val bytes = downloadHybridTile(z, x, y)
                if (bytes != null && bytes.size > 100) {
                    val tmsY = (1 shl z) - 1 - y
                    stmt.clearBindings()
                    stmt.bindLong(1, z.toLong())
                    stmt.bindLong(2, x.toLong())
                    stmt.bindLong(3, tmsY.toLong())
                    stmt.bindBlob(4, bytes)
                    stmt.executeInsert()
                    downloadedCount++
                }
                onProgress(idx + 1, totalMissing)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao sincronizar cache satélite do GPKG: ${e.message}", e)
        } finally {
            runCatching { db?.close() }
        }
        return@withContext downloadedCount
    }

    private fun downloadHybridTile(z: Int, x: Int, y: Int): ByteArray? {
        val server = (x + y) and 3
        val urlStr = "https://mt$server.google.com/vt/lyrs=y&hl=pt-BR&x=$x&y=$y&z=$z"
        return runCatching {
            val conn = (java.net.URL(urlStr).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; sGis/2.2)")
            }
            conn.inputStream.use { it.readBytes() }
        }.getOrNull()
    }
}

