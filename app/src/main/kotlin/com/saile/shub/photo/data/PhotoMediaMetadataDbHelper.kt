package com.saile.shub.photo.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import com.saile.shub.core.geo.GeoCoordinate
import com.saile.shub.core.geo.UtmConverter
import com.saile.shub.photo.PhotoOverlay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PhotoMediaMetadata(
    val uri: String,
    val latitude: Double?,
    val longitude: Double?,
    val capturedAt: Long,
    val address: String?,
    val userName: String,
    val showUtm: Boolean,
    val showGeo: Boolean,
    val showDateTime: Boolean,
    val showAddress: Boolean,
    val showName: Boolean,
) {
    fun overlayText(): String {
        val lines = mutableListOf<String>()
        val hasCoordinates = latitude != null && longitude != null

        if (showUtm) {
            val utmText = if (hasCoordinates) {
                runCatching {
                    val utm = UtmConverter.geoToUtm(GeoCoordinate(latitude!!, longitude!!))
                    val bands = "CDEFGHJKLMNPQRSTUVWX"
                    val bandIndex = ((latitude!! + 80) / 8).toInt().coerceIn(0, bands.lastIndex)
                    "${utm.zone}${bands[bandIndex]} %.0f %.0f".format(Locale.US, utm.easting, utm.northing)
                }.getOrDefault("indisponível")
            } else {
                "indisponível"
            }
            lines += "UTM: $utmText"
        }
        if (showGeo) {
            val geoText = if (hasCoordinates) {
                "%.7f, %.7f".format(Locale.US, latitude, longitude)
            } else {
                "indisponível"
            }
            lines += "GEO: $geoText"
        }
        if (showDateTime) {
            val dateText = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR"))
                .format(Date(capturedAt))
            lines += "Data e Hora: $dateText"
        }
        if (showAddress && !address.isNullOrBlank()) lines += "Endereço: $address"
        if (showName && userName.isNotBlank()) lines += "Nome: $userName"

        return lines.joinToString("\n")
    }

    companion object {
        fun from(uri: Uri, overlay: PhotoOverlay): PhotoMediaMetadata = PhotoMediaMetadata(
            uri = uri.toString(),
            latitude = overlay.location?.latitude,
            longitude = overlay.location?.longitude,
            capturedAt = overlay.capturedAt.time,
            address = overlay.resolvedAddress,
            userName = overlay.userName,
            showUtm = overlay.showUtm,
            showGeo = overlay.showGeo,
            showDateTime = overlay.showDateTime,
            showAddress = overlay.showAddress,
            showName = overlay.showName,
        )
    }
}

class PhotoMediaMetadataDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_METADATA (
                $COL_URI TEXT PRIMARY KEY,
                $COL_LATITUDE REAL,
                $COL_LONGITUDE REAL,
                $COL_CAPTURED_AT INTEGER NOT NULL,
                $COL_ADDRESS TEXT,
                $COL_USER_NAME TEXT NOT NULL,
                $COL_SHOW_UTM INTEGER NOT NULL,
                $COL_SHOW_GEO INTEGER NOT NULL,
                $COL_SHOW_DATETIME INTEGER NOT NULL,
                $COL_SHOW_ADDRESS INTEGER NOT NULL,
                $COL_SHOW_NAME INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun save(metadata: PhotoMediaMetadata) {
        val values = ContentValues().apply {
            put(COL_URI, metadata.uri)
            if (metadata.latitude != null) put(COL_LATITUDE, metadata.latitude) else putNull(COL_LATITUDE)
            if (metadata.longitude != null) put(COL_LONGITUDE, metadata.longitude) else putNull(COL_LONGITUDE)
            put(COL_CAPTURED_AT, metadata.capturedAt)
            if (metadata.address != null) put(COL_ADDRESS, metadata.address) else putNull(COL_ADDRESS)
            put(COL_USER_NAME, metadata.userName)
            put(COL_SHOW_UTM, metadata.showUtm.asInt())
            put(COL_SHOW_GEO, metadata.showGeo.asInt())
            put(COL_SHOW_DATETIME, metadata.showDateTime.asInt())
            put(COL_SHOW_ADDRESS, metadata.showAddress.asInt())
            put(COL_SHOW_NAME, metadata.showName.asInt())
        }
        writableDatabase.insertWithOnConflict(
            TABLE_METADATA,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun get(uri: Uri): PhotoMediaMetadata? {
        readableDatabase.query(
            TABLE_METADATA,
            null,
            "$COL_URI = ?",
            arrayOf(uri.toString()),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val latitudeIndex = cursor.getColumnIndexOrThrow(COL_LATITUDE)
            val longitudeIndex = cursor.getColumnIndexOrThrow(COL_LONGITUDE)
            return PhotoMediaMetadata(
                uri = cursor.getString(cursor.getColumnIndexOrThrow(COL_URI)),
                latitude = if (cursor.isNull(latitudeIndex)) null else cursor.getDouble(latitudeIndex),
                longitude = if (cursor.isNull(longitudeIndex)) null else cursor.getDouble(longitudeIndex),
                capturedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CAPTURED_AT)),
                address = cursor.getString(cursor.getColumnIndexOrThrow(COL_ADDRESS)),
                userName = cursor.getString(cursor.getColumnIndexOrThrow(COL_USER_NAME)),
                showUtm = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SHOW_UTM)) == 1,
                showGeo = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SHOW_GEO)) == 1,
                showDateTime = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SHOW_DATETIME)) == 1,
                showAddress = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SHOW_ADDRESS)) == 1,
                showName = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SHOW_NAME)) == 1,
            )
        }
    }

    fun delete(uris: Collection<Uri>) {
        if (uris.isEmpty()) return
        writableDatabase.beginTransaction()
        try {
            uris.forEach { uri ->
                writableDatabase.delete(TABLE_METADATA, "$COL_URI = ?", arrayOf(uri.toString()))
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    private fun Boolean.asInt(): Int = if (this) 1 else 0

    companion object {
        private const val DATABASE_NAME = "sfoto_media_metadata.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_METADATA = "media_overlay_metadata"
        private const val COL_URI = "uri"
        private const val COL_LATITUDE = "latitude"
        private const val COL_LONGITUDE = "longitude"
        private const val COL_CAPTURED_AT = "capturedAt"
        private const val COL_ADDRESS = "address"
        private const val COL_USER_NAME = "userName"
        private const val COL_SHOW_UTM = "showUtm"
        private const val COL_SHOW_GEO = "showGeo"
        private const val COL_SHOW_DATETIME = "showDateTime"
        private const val COL_SHOW_ADDRESS = "showAddress"
        private const val COL_SHOW_NAME = "showName"
    }
}
