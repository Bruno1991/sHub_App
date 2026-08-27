package com.saile.shub.photo.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.saile.shub.photo.CameraFlashMode
import com.saile.shub.photo.OverlayColor
import com.saile.shub.photo.OverlayPosition
import com.saile.shub.photo.PhotoUiState

class PhotoPreferencesDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_VERSION = 2
        const val DATABASE_NAME = "sfoto_prefs.db"

        const val TABLE_PREFS = "photo_prefs"
        const val COL_ID = "id"
        const val COL_OVERLAY_COLOR = "overlayColor"
        const val COL_SHOW_UTM = "showUtm"
        const val COL_SHOW_GEO = "showGeo"
        const val COL_SHOW_DATETIME = "showDateTime"
        const val COL_SHOW_ADDRESS = "showAddress"
        const val COL_SHOW_NAME = "showName"
        const val COL_USER_NAME = "userName"
        const val COL_POSITION = "position"
        const val COL_LENS_FACING_BACK = "lensFacingBack"
        const val COL_FLASH_MODE = "flashMode"
        const val COL_PLAY_SHUTTER_SOUND = "playShutterSound"
        const val COL_SELECTED_LOGO = "selectedLogo"

        const val SQL_CREATE_ENTRIES = """
            CREATE TABLE $TABLE_PREFS (
                $COL_ID INTEGER PRIMARY KEY,
                $COL_OVERLAY_COLOR TEXT,
                $COL_SHOW_UTM INTEGER,
                $COL_SHOW_GEO INTEGER,
                $COL_SHOW_DATETIME INTEGER,
                $COL_SHOW_ADDRESS INTEGER,
                $COL_SHOW_NAME INTEGER,
                $COL_USER_NAME TEXT,
                $COL_POSITION TEXT,
                $COL_LENS_FACING_BACK INTEGER,
                $COL_FLASH_MODE TEXT,
                $COL_PLAY_SHUTTER_SOUND INTEGER,
                $COL_SELECTED_LOGO TEXT
            )
        """
        
        const val SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS $TABLE_PREFS"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_ENTRIES)
        // Inserir linha padrão com ID = 1
        val defaultState = PhotoUiState()
        val values = ContentValues().apply {
            put(COL_ID, 1)
            put(COL_OVERLAY_COLOR, defaultState.overlayColor.name)
            put(COL_SHOW_UTM, if (defaultState.showUtm) 1 else 0)
            put(COL_SHOW_GEO, if (defaultState.showGeo) 1 else 0)
            put(COL_SHOW_DATETIME, if (defaultState.showDateTime) 1 else 0)
            put(COL_SHOW_ADDRESS, if (defaultState.showAddress) 1 else 0)
            put(COL_SHOW_NAME, if (defaultState.showName) 1 else 0)
            put(COL_USER_NAME, defaultState.userName)
            put(COL_POSITION, defaultState.position.name)
            put(COL_LENS_FACING_BACK, if (defaultState.lensFacingBack) 1 else 0)
            put(COL_FLASH_MODE, defaultState.flashMode.name)
            put(COL_PLAY_SHUTTER_SOUND, if (defaultState.playShutterSound) 1 else 0)
            put(COL_SELECTED_LOGO, defaultState.selectedLogo)
        }
        db.insert(TABLE_PREFS, null, values)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL(SQL_DELETE_ENTRIES)
        onCreate(db)
    }

    fun getPreferences(): PhotoUiState {
        val db = readableDatabase
        val cursor = db.query(TABLE_PREFS, null, "$COL_ID = ?", arrayOf("1"), null, null, null)
        var state = PhotoUiState()
        if (cursor.moveToFirst()) {
            state = PhotoUiState(
                overlayColor = runCatching { OverlayColor.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(COL_OVERLAY_COLOR))) }.getOrDefault(OverlayColor.YELLOW),
                showUtm = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SHOW_UTM)) == 1,
                showGeo = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SHOW_GEO)) == 1,
                showDateTime = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SHOW_DATETIME)) == 1,
                showAddress = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SHOW_ADDRESS)) == 1,
                showName = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SHOW_NAME)) == 1,
                userName = cursor.getString(cursor.getColumnIndexOrThrow(COL_USER_NAME)) ?: "sFoto",
                position = runCatching { OverlayPosition.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(COL_POSITION))) }.getOrDefault(OverlayPosition.BOTTOM_RIGHT),
                lensFacingBack = cursor.getInt(cursor.getColumnIndexOrThrow(COL_LENS_FACING_BACK)) == 1,
                flashMode = runCatching { CameraFlashMode.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(COL_FLASH_MODE))) }.getOrDefault(CameraFlashMode.AUTO),
                playShutterSound = cursor.getInt(cursor.getColumnIndexOrThrow(COL_PLAY_SHUTTER_SOUND)) == 1,
                selectedLogo = runCatching { cursor.getString(cursor.getColumnIndexOrThrow(COL_SELECTED_LOGO)) }.getOrNull(),
            )
        }
        cursor.close()
        return state
    }

    fun savePreferences(state: PhotoUiState) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_OVERLAY_COLOR, state.overlayColor.name)
            put(COL_SHOW_UTM, if (state.showUtm) 1 else 0)
            put(COL_SHOW_GEO, if (state.showGeo) 1 else 0)
            put(COL_SHOW_DATETIME, if (state.showDateTime) 1 else 0)
            put(COL_SHOW_ADDRESS, if (state.showAddress) 1 else 0)
            put(COL_SHOW_NAME, if (state.showName) 1 else 0)
            put(COL_USER_NAME, state.userName)
            put(COL_POSITION, state.position.name)
            put(COL_LENS_FACING_BACK, if (state.lensFacingBack) 1 else 0)
            put(COL_FLASH_MODE, state.flashMode.name)
            put(COL_PLAY_SHUTTER_SOUND, if (state.playShutterSound) 1 else 0)
            put(COL_SELECTED_LOGO, state.selectedLogo)
        }
        db.update(TABLE_PREFS, values, "$COL_ID = ?", arrayOf("1"))
    }
}
