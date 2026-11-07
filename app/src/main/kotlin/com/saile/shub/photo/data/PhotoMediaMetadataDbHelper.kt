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
    val altitude: Double? = null,
    val accuracy: Float? = null,
    val azimuth: Float? = null,
    val showAltitude: Boolean = true,
    val showAzimuth: Boolean = true,
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
        if (showAltitude && altitude != null) {
            val altText = if (accuracy != null) {
                "Alt: %.0fm (±%.1fm)".format(Locale.US, altitude, accuracy)
            } else {
                "Alt: %.0fm".format(Locale.US, altitude)
            }
            lines += altText
        }
        if (showAzimuth && azimuth != null) {
            val cardinal = getCardinalDirection(azimuth)
            lines += "AZ: %.0f° %s".format(Locale.US, azimuth, cardinal)
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
        fun getCardinalDirection(azimuthDegrees: Float): String {
            val normalized = (azimuthDegrees % 360f + 360f) % 360f
            return when {
                normalized >= 337.5f || normalized < 22.5f -> "N"
                normalized < 67.5f -> "NE"
                normalized < 112.5f -> "L"
                normalized < 157.5f -> "SE"
                normalized < 202.5f -> "S"
                normalized < 247.5f -> "SO"
                normalized < 292.5f -> "O"
                else -> "NO"
            }
        }

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
            altitude = if (overlay.location?.hasAltitude() == true) overlay.location.altitude else null,
            accuracy = if (overlay.location?.hasAccuracy() == true) overlay.location.accuracy else null,
            azimuth = overlay.azimuthDegrees,
            showAltitude = overlay.showAltitude,
            showAzimuth = overlay.showAzimuth,
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
                $COL_SHOW_NAME INTEGER NOT NULL,
                $COL_ALTITUDE REAL,
                $COL_ACCURACY REAL,
                $COL_AZIMUTH REAL,
                $COL_SHOW_ALTITUDE INTEGER DEFAULT 1,
                $COL_SHOW_AZIMUTH INTEGER DEFAULT 1
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            runCatching { db.execSQL("ALTER TABLE $TABLE_METADATA ADD COLUMN $COL_ALTITUDE REAL") }
            runCatching { db.execSQL("ALTER TABLE $TABLE_METADATA ADD COLUMN $COL_ACCURACY REAL") }
            runCatching { db.execSQL("ALTER TABLE $TABLE_METADATA ADD COLUMN $COL_AZIMUTH REAL") }
            runCatching { db.execSQL("ALTER TABLE $TABLE_METADATA ADD COLUMN $COL_SHOW_ALTITUDE INTEGER DEFAULT 1") }
            runCatching { db.execSQL("ALTER TABLE $TABLE_METADATA ADD COLUMN $COL_SHOW_AZIMUTH INTEGER DEFAULT 1") }
        }
    }

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
            if (metadata.altitude != null) put(COL_ALTITUDE, metadata.altitude) else putNull(COL_ALTITUDE)
            if (metadata.accuracy != null) put(COL_ACCURACY, metadata.accuracy) else putNull(COL_ACCURACY)
            if (metadata.azimuth != null) put(COL_AZIMUTH, metadata.azimuth) else putNull(COL_AZIMUTH)
            put(COL_SHOW_ALTITUDE, metadata.showAltitude.asInt())
            put(COL_SHOW_AZIMUTH, metadata.showAzimuth.asInt())
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
            val altitudeIndex = cursor.getColumnIndex(COL_ALTITUDE)
            val accuracyIndex = cursor.getColumnIndex(COL_ACCURACY)
            val azimuthIndex = cursor.getColumnIndex(COL_AZIMUTH)
            val showAltIndex = cursor.getColumnIndex(COL_SHOW_ALTITUDE)
            val showAzIndex = cursor.getColumnIndex(COL_SHOW_AZIMUTH)

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
                altitude = if (altitudeIndex >= 0 && !cursor.isNull(altitudeIndex)) cursor.getDouble(altitudeIndex) else null,
                accuracy = if (accuracyIndex >= 0 && !cursor.isNull(accuracyIndex)) cursor.getFloat(accuracyIndex) else null,
                azimuth = if (azimuthIndex >= 0 && !cursor.isNull(azimuthIndex)) cursor.getFloat(azimuthIndex) else null,
                showAltitude = if (showAltIndex >= 0) cursor.getInt(showAltIndex) == 1 else true,
                showAzimuth = if (showAzIndex >= 0) cursor.getInt(showAzIndex) == 1 else true,
            )
        }
    }

    fun getAllGeotaggedPhotos(limit: Int = 200): List<PhotoMediaMetadata> {
        val list = mutableListOf<PhotoMediaMetadata>()
        runCatching {
            readableDatabase.query(
                TABLE_METADATA,
                null,
                "$COL_LATITUDE IS NOT NULL AND $COL_LONGITUDE IS NOT NULL",
                null,
                null,
                null,
                "$COL_CAPTURED_AT DESC",
                limit.toString()
            ).use { cursor ->
                val latitudeIndex = cursor.getColumnIndexOrThrow(COL_LATITUDE)
                val longitudeIndex = cursor.getColumnIndexOrThrow(COL_LONGITUDE)
                val altitudeIndex = cursor.getColumnIndex(COL_ALTITUDE)
                val accuracyIndex = cursor.getColumnIndex(COL_ACCURACY)
                val azimuthIndex = cursor.getColumnIndex(COL_AZIMUTH)
                val showAltIndex = cursor.getColumnIndex(COL_SHOW_ALTITUDE)
                val showAzIndex = cursor.getColumnIndex(COL_SHOW_AZIMUTH)

                while (cursor.moveToNext()) {
                    list.add(
                        PhotoMediaMetadata(
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
                            altitude = if (altitudeIndex >= 0 && !cursor.isNull(altitudeIndex)) cursor.getDouble(altitudeIndex) else null,
                            accuracy = if (accuracyIndex >= 0 && !cursor.isNull(accuracyIndex)) cursor.getFloat(accuracyIndex) else null,
                            azimuth = if (azimuthIndex >= 0 && !cursor.isNull(azimuthIndex)) cursor.getFloat(azimuthIndex) else null,
                            showAltitude = if (showAltIndex >= 0) cursor.getInt(showAltIndex) == 1 else true,
                            showAzimuth = if (showAzIndex >= 0) cursor.getInt(showAzIndex) == 1 else true,
                        )
                    )
                }
            }
        }
        return list
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
        private const val DATABASE_VERSION = 2
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
        private const val COL_ALTITUDE = "altitude"
        private const val COL_ACCURACY = "accuracy"
        private const val COL_AZIMUTH = "azimuth"
        private const val COL_SHOW_ALTITUDE = "showAltitude"
        private const val COL_SHOW_AZIMUTH = "showAzimuth"
    }
}
