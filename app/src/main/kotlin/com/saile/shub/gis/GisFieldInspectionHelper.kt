package com.saile.shub.gis

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.widget.Toast
import androidx.core.content.FileProvider
import com.saile.shub.core.geo.UtmConverter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * GisFieldInspectionHelper.kt
 *
 * Responsabilidade Única:
 * Persistência SQLite offline de apontamentos/anomalias de inspeção de campo no sGis,
 * vinculação com fotos do sFoto e exportação/compartilhamento em KML e CSV.
 */
data class FieldInspectionRecord(
    val id: Long = 0L,
    val targetCode: String,
    val targetLayer: String,
    val anomalyType: String,
    val severity: String,
    val notes: String,
    val latitude: Double,
    val longitude: Double,
    val utmText: String,
    val photoUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val formattedDate: String
        get() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(createdAt))
}

class GisFieldInspectionHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_INSPECTIONS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                target_code TEXT NOT NULL,
                target_layer TEXT NOT NULL,
                anomaly_type TEXT NOT NULL,
                severity TEXT NOT NULL,
                notes TEXT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                utm_text TEXT NOT NULL,
                photo_uri TEXT,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_INSPECTIONS")
        onCreate(db)
    }

    fun insertInspection(record: FieldInspectionRecord): Long {
        val cv = ContentValues().apply {
            put("target_code", record.targetCode)
            put("target_layer", record.targetLayer)
            put("anomaly_type", record.anomalyType)
            put("severity", record.severity)
            put("notes", record.notes)
            put("latitude", record.latitude)
            put("longitude", record.longitude)
            put("utm_text", record.utmText)
            if (record.photoUri != null) put("photo_uri", record.photoUri) else putNull("photo_uri")
            put("created_at", record.createdAt)
        }
        return writableDatabase.insert(TABLE_INSPECTIONS, null, cv)
    }

    fun getAllInspections(): List<FieldInspectionRecord> {
        val list = mutableListOf<FieldInspectionRecord>()
        runCatching {
            readableDatabase.rawQuery(
                "SELECT id, target_code, target_layer, anomaly_type, severity, notes, latitude, longitude, utm_text, photo_uri, created_at FROM $TABLE_INSPECTIONS ORDER BY created_at DESC",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    list.add(
                        FieldInspectionRecord(
                            id = c.getLong(0),
                            targetCode = c.getString(1) ?: "",
                            targetLayer = c.getString(2) ?: "",
                            anomalyType = c.getString(3) ?: "",
                            severity = c.getString(4) ?: "NORMAL",
                            notes = c.getString(5) ?: "",
                            latitude = c.getDouble(6),
                            longitude = c.getDouble(7),
                            utmText = c.getString(8) ?: "",
                            photoUri = if (c.isNull(9)) null else c.getString(9),
                            createdAt = c.getLong(10)
                        )
                    )
                }
            }
        }
        return list
    }

    fun deleteInspection(id: Long) {
        writableDatabase.delete(TABLE_INSPECTIONS, "id = ?", arrayOf(id.toString()))
    }

    companion object {
        private const val DB_NAME = "sgis_field_inspections.db"
        private const val DB_VERSION = 1
        private const val TABLE_INSPECTIONS = "field_inspections"

        val ANOMALY_TYPES = listOf(
            "Poste abalroado / inclinado",
            "Transformador vazando / avariado",
            "Isolador quebrado / descarga",
            "Poda necessária na rede",
            "Chave fusível / faca com defeito",
            "Cabo partido / barriga baixa",
            "Medidor / Ramal irregular",
            "Outros / Observação técnica"
        )

        val SEVERITIES = listOf("NORMAL", "ALTA", "CRÍTICA")

        fun exportAndShareCsv(context: Context, records: List<FieldInspectionRecord>) {
            if (records.isEmpty()) {
                Toast.makeText(context, "Nenhuma inspeção registrada para exportar.", Toast.LENGTH_SHORT).show()
                return
            }
            runCatching {
                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val file = File(exportDir, "inspecoes_sgis_${System.currentTimeMillis()}.csv")
                val sb = StringBuilder()
                sb.append("\uFEFF") // UTF-8 BOM for Excel
                sb.append("ID;Ativo_UC;Camada;Anomalia;Severidade;Observacoes;Latitude;Longitude;UTM;Data_Hora\n")
                for (r in records) {
                    val cleanNotes = r.notes.replace(";", ",").replace("\n", " ")
                    sb.append("${r.id};${r.targetCode};${r.targetLayer};${r.anomalyType};${r.severity};$cleanNotes;${r.latitude};${r.longitude};${r.utmText};${r.formattedDate}\n")
                }
                file.writeText(sb.toString(), Charsets.UTF_8)
                shareFile(context, file, "text/csv", "Exportar Inspeções (CSV)")
            }.onFailure {
                Toast.makeText(context, "Erro ao exportar CSV: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }

        fun exportAndShareKml(context: Context, records: List<FieldInspectionRecord>) {
            if (records.isEmpty()) {
                Toast.makeText(context, "Nenhuma inspeção registrada para exportar.", Toast.LENGTH_SHORT).show()
                return
            }
            runCatching {
                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val file = File(exportDir, "inspecoes_sgis_${System.currentTimeMillis()}.kml")
                val sb = StringBuilder()
                sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append("\n")
                sb.append("""<kml xmlns="http://www.opengis.net/kml/2.2"><Document><name>Inspeções de Campo sGis</name>""").append("\n")
                for (r in records) {
                    val title = escapeXml("${r.targetCode} - ${r.anomalyType} [${r.severity}]")
                    val desc = escapeXml("Camada: ${r.targetLayer}\nUTM: ${r.utmText}\nObs: ${r.notes}\nData: ${r.formattedDate}")
                    sb.append("<Placemark><name>$title</name><description>$desc</description>")
                    sb.append("<Point><coordinates>${r.longitude},${r.latitude},0</coordinates></Point></Placemark>\n")
                }
                sb.append("</Document></kml>")
                file.writeText(sb.toString(), Charsets.UTF_8)
                shareFile(context, file, "application/vnd.google-earth.kml+xml", "Exportar Inspeções (KML)")
            }.onFailure {
                Toast.makeText(context, "Erro ao exportar KML: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }

        private fun escapeXml(s: String): String {
            return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
        }

        private fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

