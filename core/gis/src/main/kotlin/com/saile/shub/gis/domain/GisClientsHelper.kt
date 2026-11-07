package com.saile.shub.gis.domain

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.OpenableColumns
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot

/**
 * GisClientsHelper.kt
 *
 * Responsabilidade Única:
 * Gerenciar a importação em streaming de arquivos JSON de clientes/unidades consumidoras
 * (ex: dashboard_rota.json com +175 mil registros) para uma base SQLite indexada local
 * e executar buscas sub-milissegundo por UC/CDC, Número do Medidor, Nome do Cliente ou proximidade espacial.
 */
data class GisClientRecord(
    val id: Long = 0L,
    val cdc: String,
    val cliente: String,
    val medidor: String,
    val latitude: Double?,
    val longitude: Double?,
    val precisao: Double?,
    val livro: String,
    val rota: String,
    val conta: String
) {
    fun toMetadataJson(): String {
        val obj = JSONObject()
        obj.put("UC / CDC", cdc.ifBlank { "-" })
        obj.put("Cliente", cliente.ifBlank { "-" })
        obj.put("Medidor", medidor.ifBlank { "-" })
        obj.put("Livro", livro.ifBlank { "-" })
        obj.put("Rota", rota.ifBlank { "-" })
        obj.put("Conta", conta.ifBlank { "-" })
        if (precisao != null && !precisao.isNaN()) {
            obj.put("Precisão GPS", String.format(Locale.US, "± %.1f m", precisao))
        }
        if (latitude != null && longitude != null) {
            obj.put("Latitude", String.format(Locale.US, "%.6f", latitude))
            obj.put("Longitude", String.format(Locale.US, "%.6f", longitude))
        }
        return obj.toString()
    }
}

data class ClientsPackageMetadata(
    val sourceFileName: String,
    val dbFile: File,
    val sourceSizeBytes: Long,
    val totalClients: Int,
    val clientsWithCoords: Int,
    val importTimestamp: Long
) {
    val formattedSize: String
        get() {
            val bytes = if (sourceSizeBytes > 0L) sourceSizeBytes else dbFile.length()
            val mb = bytes / (1024.0 * 1024.0)
            return if (mb >= 1024.0) {
                String.format(Locale.US, "%.2f GB", mb / 1024.0)
            } else {
                String.format(Locale.US, "%.1f MB", mb)
            }
        }

    val importDateFormatted: String
        get() = runCatching {
            val ts = if (importTimestamp > 0L) importTimestamp else dbFile.lastModified()
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ts))
        }.getOrDefault("--/--/----")
}

object GisClientsHelper {

    private const val TAG = "GisClientsHelper"
    private const val DB_FILE_NAME = "gis_clients.db"

    fun getClientsDir(context: Context): File {
        return File(context.filesDir, "clients").apply { mkdirs() }
    }

    fun getActiveClientsDbFile(context: Context): File? {
        val internalFile = File(getClientsDir(context), DB_FILE_NAME)
        if (internalFile.exists() && internalFile.length() > 4096L) {
            return internalFile
        }
        val externalDir = context.getExternalFilesDir(null)?.let { File(it, "clients") }
        val externalFile = externalDir?.let { File(it, DB_FILE_NAME) }
        if (externalFile != null && externalFile.exists() && externalFile.length() > 4096L) {
            return externalFile
        }
        return null
    }

    fun readActiveMetadata(context: Context): ClientsPackageMetadata? {
        val dbFile = getActiveClientsDbFile(context) ?: return null
        var db: SQLiteDatabase? = null
        return try {
            db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )
            var sourceName = "dashboard_rota.json"
            var sourceSize = dbFile.length()
            var total = 0
            var withCoords = 0
            var importTs = dbFile.lastModified()

            runCatching {
                db.rawQuery(
                    "SELECT source_file_name, source_size_bytes, total_clients, clients_with_coords, import_timestamp FROM clients_meta LIMIT 1",
                    null
                ).use { c ->
                    if (c.moveToFirst()) {
                        sourceName = c.getString(0) ?: sourceName
                        sourceSize = c.getLong(1)
                        total = c.getInt(2)
                        withCoords = c.getInt(3)
                        importTs = c.getLong(4)
                    }
                }
            }

            if (total == 0) {
                db.rawQuery("SELECT COUNT(*), COUNT(latitude) FROM clients", null).use { c ->
                    if (c.moveToFirst()) {
                        total = c.getInt(0)
                        withCoords = c.getInt(1)
                    }
                }
            }

            if (total <= 0) null
            else ClientsPackageMetadata(
                sourceFileName = sourceName,
                dbFile = dbFile,
                sourceSizeBytes = sourceSize,
                totalClients = total,
                clientsWithCoords = withCoords,
                importTimestamp = importTs
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read clients metadata: ${e.message}", e)
            null
        } finally {
            runCatching { db?.close() }
        }
    }

    suspend fun importClientsFromUri(
        context: Context,
        uri: Uri,
        onProgress: (Int) -> Unit = {}
    ): ClientsPackageMetadata? = withContext(Dispatchers.IO) {
        var displayName = "clientes_rota.json"
        var sourceSize = 0L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) displayName = c.getString(nameIdx) ?: displayName
                    val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !c.isNull(sizeIdx)) sourceSize = c.getLong(sizeIdx)
                }
            }
        }

        context.contentResolver.openInputStream(uri)?.use { rawStream ->
            importClientsFromStream(
                context = context,
                inputStream = rawStream,
                sourceFileName = displayName,
                sourceSizeBytes = sourceSize,
                onProgress = onProgress
            )
        }
    }

    suspend fun importClientsFromFile(
        context: Context,
        jsonFile: File,
        onProgress: (Int) -> Unit = {}
    ): ClientsPackageMetadata? = withContext(Dispatchers.IO) {
        if (!jsonFile.exists()) return@withContext null
        FileInputStream(jsonFile).use { fis ->
            importClientsFromStream(
                context = context,
                inputStream = fis,
                sourceFileName = jsonFile.name,
                sourceSizeBytes = jsonFile.length(),
                onProgress = onProgress
            )
        }
    }

    private fun importClientsFromStream(
        context: Context,
        inputStream: InputStream,
        sourceFileName: String,
        sourceSizeBytes: Long,
        onProgress: (Int) -> Unit
    ): ClientsPackageMetadata? {
        val targetDir = getClientsDir(context)
        val tempDbFile = File(targetDir, "gis_clients_temp_${System.currentTimeMillis()}.db")
        val finalDbFile = File(targetDir, DB_FILE_NAME)

        var db: SQLiteDatabase? = null
        try {
            if (tempDbFile.exists()) tempDbFile.delete()
            db = SQLiteDatabase.openOrCreateDatabase(tempDbFile, null)
            db.execSQL("PRAGMA synchronous = OFF")
            db.execSQL("PRAGMA journal_mode = MEMORY")
            db.execSQL("PRAGMA temp_store = MEMORY")

            db.execSQL(
                """
                CREATE TABLE clients (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    cdc TEXT NOT NULL,
                    cliente TEXT NOT NULL,
                    medidor TEXT NOT NULL,
                    latitude REAL,
                    longitude REAL,
                    precisao REAL,
                    livro TEXT,
                    rota TEXT,
                    conta TEXT,
                    cdc_norm TEXT NOT NULL,
                    medidor_norm TEXT NOT NULL,
                    cliente_norm TEXT NOT NULL
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE clients_meta (
                    source_file_name TEXT,
                    source_size_bytes INTEGER,
                    total_clients INTEGER,
                    clients_with_coords INTEGER,
                    import_timestamp INTEGER
                )
                """.trimIndent()
            )

            val insertSql = """
                INSERT INTO clients (
                    cdc, cliente, medidor, latitude, longitude, precisao,
                    livro, rota, conta, cdc_norm, medidor_norm, cliente_norm
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            val stmt = db.compileStatement(insertSql)

            val reader = JsonReader(InputStreamReader(BufferedInputStream(inputStream, 64 * 1024), Charsets.UTF_8))
            reader.isLenient = true

            var totalCount = 0
            var coordsCount = 0

            db.beginTransaction()
            try {
                // Support both top-level array `[...]` or object `{"data": [...]}`
                if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        reader.nextName()
                        if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                            break
                        } else {
                            reader.skipValue()
                        }
                    }
                }

                reader.beginArray()
                while (reader.hasNext()) {
                    var cdc = ""
                    var cliente = ""
                    var medidor = ""
                    var lat: Double? = null
                    var lon: Double? = null
                    var precisao: Double? = null
                    var livro = ""
                    var rota = ""
                    var conta = ""

                    reader.beginObject()
                    while (reader.hasNext()) {
                        val key = reader.nextName()
                        if (reader.peek() == JsonToken.NULL) {
                            reader.nextNull()
                            continue
                        }
                        when (key.lowercase(Locale.ROOT)) {
                            "cdc", "numcdc", "uc", "codigo_uc" -> cdc = readNextStringSafe(reader)
                            "cliente", "nomecsd", "nome", "nome_cliente" -> cliente = readNextStringSafe(reader)
                            "medidor", "num_medidor", "numero_medidor" -> medidor = readNextStringSafe(reader)
                            "latitude", "vlr_coordenada_latitude", "lat" -> lat = readNextDoubleSafe(reader)
                            "longitude", "vlr_coordenada_longitude", "lon", "lng" -> lon = readNextDoubleSafe(reader)
                            "precisão", "precisao", "vlr_precisao_coordenada" -> precisao = readNextDoubleSafe(reader)
                            "livro", "numliv" -> livro = readNextStringSafe(reader)
                            "rota", "numrota" -> rota = readNextStringSafe(reader)
                            "conta", "numcta" -> conta = readNextStringSafe(reader)
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()

                    if (cdc.isBlank() && cliente.isBlank() && medidor.isBlank()) continue

                    stmt.clearBindings()
                    stmt.bindString(1, cdc)
                    stmt.bindString(2, cliente)
                    stmt.bindString(3, medidor)
                    if (lat != null && !lat.isNaN() && lon != null && !lon.isNaN()) {
                        stmt.bindDouble(4, lat)
                        stmt.bindDouble(5, lon)
                        coordsCount++
                    } else {
                        stmt.bindNull(4)
                        stmt.bindNull(5)
                    }
                    if (precisao != null && !precisao.isNaN()) {
                        stmt.bindDouble(6, precisao)
                    } else {
                        stmt.bindNull(6)
                    }
                    stmt.bindString(7, livro)
                    stmt.bindString(8, rota)
                    stmt.bindString(9, conta)
                    stmt.bindString(10, normalizeToken(cdc))
                    stmt.bindString(11, normalizeToken(medidor))
                    stmt.bindString(12, normalizeName(cliente))
                    stmt.executeInsert()

                    totalCount++
                    if (totalCount % 5000 == 0) {
                        db.setTransactionSuccessful()
                        db.endTransaction()
                        db.beginTransaction()
                        onProgress(totalCount)
                    }
                }
                reader.endArray()
                db.setTransactionSuccessful()
            } finally {
                if (db.inTransaction()) {
                    db.endTransaction()
                }
                runCatching { reader.close() }
            }

            if (totalCount == 0) {
                db.close()
                tempDbFile.delete()
                return null
            }

            val importTs = System.currentTimeMillis()
            val actualSize = if (sourceSizeBytes > 0L) sourceSizeBytes else tempDbFile.length()
            db.execSQL(
                "INSERT INTO clients_meta VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any>(sourceFileName, actualSize, totalCount, coordsCount, importTs)
            )

            // Create fast lookup indexes
            db.execSQL("CREATE INDEX idx_clients_cdc ON clients(cdc_norm)")
            db.execSQL("CREATE INDEX idx_clients_medidor ON clients(medidor_norm)")
            db.execSQL("CREATE INDEX idx_clients_cliente ON clients(cliente_norm)")
            db.execSQL("CREATE INDEX idx_clients_coords ON clients(latitude, longitude)")

            db.close()
            db = null

            if (finalDbFile.exists()) finalDbFile.delete()
            if (!tempDbFile.renameTo(finalDbFile)) {
                tempDbFile.copyTo(finalDbFile, overwrite = true)
                tempDbFile.delete()
            }

            return ClientsPackageMetadata(
                sourceFileName = sourceFileName,
                dbFile = finalDbFile,
                sourceSizeBytes = actualSize,
                totalClients = totalCount,
                clientsWithCoords = coordsCount,
                importTimestamp = importTs
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error importing clients JSON: ${e.message}", e)
            runCatching { db?.close() }
            runCatching { tempDbFile.delete() }
            return null
        }
    }

    fun searchClients(
        context: Context,
        query: String,
        limit: Int = 50
    ): List<GisClientRecord> {
        val dbFile = getActiveClientsDbFile(context) ?: return emptyList()
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

        val tokenNorm = normalizeToken(trimmed)
        val nameNorm = normalizeName(trimmed)
        if (tokenNorm.isBlank() && nameNorm.isBlank()) return emptyList()

        var db: SQLiteDatabase? = null
        val results = mutableListOf<GisClientRecord>()
        val seenIds = HashSet<Long>()

        try {
            db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )

            // 1. Fast indexed prefix search on CDC (UC) and Medidor first
            if (tokenNorm.isNotEmpty()) {
                val prefixSql = """
                    SELECT id, cdc, cliente, medidor, latitude, longitude, precisao, livro, rota, conta
                    FROM clients
                    WHERE cdc_norm LIKE ? OR medidor_norm LIKE ?
                    ORDER BY CASE WHEN cdc_norm = ? OR medidor_norm = ? THEN 0 ELSE 1 END, id ASC
                    LIMIT ?
                """.trimIndent()
                db.rawQuery(
                    prefixSql,
                    arrayOf("$tokenNorm%", "$tokenNorm%", tokenNorm, tokenNorm, limit.toString())
                ).use { c ->
                    while (c.moveToNext()) {
                        val rec = readCursorRecord(c)
                        if (seenIds.add(rec.id)) {
                            results.add(rec)
                        }
                    }
                }
            }

            // 2. If room remains, search by client name or substring in medidor/cdc
            if (results.size < limit && nameNorm.length >= 2) {
                val rem = limit - results.size
                val nameSql = """
                    SELECT id, cdc, cliente, medidor, latitude, longitude, precisao, livro, rota, conta
                    FROM clients
                    WHERE cliente_norm LIKE ? OR medidor_norm LIKE ?
                    LIMIT ?
                """.trimIndent()
                db.rawQuery(
                    nameSql,
                    arrayOf("%$nameNorm%", "%$tokenNorm%", (rem + 20).toString())
                ).use { c ->
                    while (c.moveToNext() && results.size < limit) {
                        val rec = readCursorRecord(c)
                        if (seenIds.add(rec.id)) {
                            results.add(rec)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching clients: ${e.message}", e)
        } finally {
            runCatching { db?.close() }
        }

        return results
    }

    fun findNearbyClients(
        context: Context,
        targetLat: Double,
        targetLon: Double,
        radiusMeters: Double = 500.0,
        limit: Int = 20
    ): List<GisClientRecord> {
        val dbFile = getActiveClientsDbFile(context) ?: return emptyList()
        val degDelta = radiusMeters / 111_320.0
        val minLat = targetLat - degDelta
        val maxLat = targetLat + degDelta
        val minLon = targetLon - degDelta
        val maxLon = targetLon + degDelta

        var db: SQLiteDatabase? = null
        val candidates = mutableListOf<Pair<Double, GisClientRecord>>()
        try {
            db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )
            val sql = """
                SELECT id, cdc, cliente, medidor, latitude, longitude, precisao, livro, rota, conta
                FROM clients
                WHERE latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ?
                LIMIT 200
            """.trimIndent()
            db.rawQuery(
                sql,
                arrayOf(minLat.toString(), maxLat.toString(), minLon.toString(), maxLon.toString())
            ).use { c ->
                val cosLat = cos(Math.toRadians(targetLat))
                while (c.moveToNext()) {
                    val rec = readCursorRecord(c)
                    val lat = rec.latitude ?: continue
                    val lon = rec.longitude ?: continue
                    val dy = (lat - targetLat) * 111_320.0
                    val dx = (lon - targetLon) * 111_320.0 * cosLat
                    val dist = hypot(dx, dy)
                    if (dist <= radiusMeters) {
                        candidates.add(dist to rec)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding nearby clients: ${e.message}", e)
        } finally {
            runCatching { db?.close() }
        }

        return candidates.sortedBy { it.first }.take(limit).map { it.second }
    }

    private fun readCursorRecord(c: android.database.Cursor): GisClientRecord {
        val lat = if (c.isNull(4)) null else c.getDouble(4)
        val lon = if (c.isNull(5)) null else c.getDouble(5)
        val prec = if (c.isNull(6)) null else c.getDouble(6)
        return GisClientRecord(
            id = c.getLong(0),
            cdc = c.getString(1) ?: "",
            cliente = c.getString(2) ?: "",
            medidor = c.getString(3) ?: "",
            latitude = lat,
            longitude = lon,
            precisao = prec,
            livro = c.getString(7) ?: "",
            rota = c.getString(8) ?: "",
            conta = c.getString(9) ?: ""
        )
    }

    private fun readNextStringSafe(reader: JsonReader): String {
        return when (reader.peek()) {
            JsonToken.STRING -> reader.nextString().trim()
            JsonToken.NUMBER -> reader.nextString().trim()
            JsonToken.BOOLEAN -> reader.nextBoolean().toString()
            else -> {
                reader.skipValue()
                ""
            }
        }
    }

    private fun readNextDoubleSafe(reader: JsonReader): Double? {
        return when (reader.peek()) {
            JsonToken.NUMBER -> runCatching { reader.nextDouble() }.getOrNull()
            JsonToken.STRING -> {
                val s = reader.nextString().trim().replace(',', '.')
                s.toDoubleOrNull()
            }
            else -> {
                reader.skipValue()
                null
            }
        }
    }

    fun normalizeToken(text: String): String {
        val noAccents = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noAccents.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
    }

    fun normalizeName(text: String): String {
        val noAccents = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noAccents.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()
    }
}

