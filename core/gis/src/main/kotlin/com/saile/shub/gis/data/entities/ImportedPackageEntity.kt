package com.saile.shub.gis.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "imported_packages")
data class ImportedPackageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "file_uri") val fileUri: String,
    @ColumnInfo(name = "imported_at") val importedAt: String,
    @ColumnInfo(name = "feature_count") val featureCount: Int = 0,
    @ColumnInfo(name = "status") val status: String
)
