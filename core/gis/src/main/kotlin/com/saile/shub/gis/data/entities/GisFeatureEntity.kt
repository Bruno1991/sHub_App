package com.saile.shub.gis.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "gis_features",
    foreignKeys = [
        ForeignKey(
            entity = ImportedPackageEntity::class,
            parentColumns = ["id"],
            childColumns = ["package_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["code_norm"], name = "idx_gis_features_code_norm"),
        Index(value = ["search_content"], name = "idx_gis_features_search_content"),
        Index(value = ["source_layer"], name = "idx_gis_features_layer"),
        Index(value = ["centroid_lat", "centroid_lon"], name = "idx_gis_features_centroid"),
        Index(value = ["min_lat", "min_lon", "max_lat", "max_lon"], name = "idx_gis_features_bbox")
    ]
)
data class GisFeatureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "package_id", index = true) val packageId: Long,
    @ColumnInfo(name = "source_layer") val sourceLayer: String,
    @ColumnInfo(name = "feature_source_id") val featureSourceId: String?,
    @ColumnInfo(name = "code_raw") val codeRaw: String?,
    @ColumnInfo(name = "code_norm") val codeNorm: String?,
    @ColumnInfo(name = "search_content") val searchContent: String? = null,
    @ColumnInfo(name = "geometry_type") val geometryType: String?,
    @ColumnInfo(name = "centroid_lat") val centroidLat: Double?,
    @ColumnInfo(name = "centroid_lon") val centroidLon: Double?,
    @ColumnInfo(name = "min_lat") val minLat: Double?,
    @ColumnInfo(name = "min_lon") val minLon: Double?,
    @ColumnInfo(name = "max_lat") val maxLat: Double?,
    @ColumnInfo(name = "max_lon") val maxLon: Double?,
    @ColumnInfo(name = "metadata_json") val metadataJson: String
)
