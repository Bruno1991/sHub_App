package com.saile.shub.gis.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.saile.shub.gis.data.entities.GisFeatureEntity
import com.saile.shub.gis.data.entities.ImportedPackageEntity

@Dao
interface GisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPackage(pkg: ImportedPackageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeatures(features: List<GisFeatureEntity>)

    @Query("UPDATE imported_packages SET status = :status, feature_count = :count WHERE id = :packageId")
    suspend fun updatePackageStatus(packageId: Long, status: String, count: Int)

    @Query("""
        SELECT * FROM gis_features 
        WHERE 
            code_norm LIKE :query || '%' 
            OR search_content LIKE '%' || :query || '%'
            OR code_raw LIKE '%' || :query || '%'
        ORDER BY 
            CASE 
                WHEN code_norm = :query THEN 0 
                WHEN code_norm LIKE :query || '%' THEN 1 
                ELSE 2 
            END,
            id ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun searchFeatures(query: String, limit: Int = 100, offset: Int = 0): List<GisFeatureEntity>

    @Query("""
        SELECT * FROM gis_features 
        WHERE centroid_lat IS NOT NULL 
          AND centroid_lon IS NOT NULL
          AND centroid_lat BETWEEN :minLat AND :maxLat
          AND centroid_lon BETWEEN :minLon AND :maxLon
        ORDER BY 
            ((centroid_lat - :targetLat) * (centroid_lat - :targetLat) + 
             (centroid_lon - :targetLon) * (centroid_lon - :targetLon)) ASC
        LIMIT :limit
    """)
    suspend fun findNearestFeatures(
        targetLat: Double,
        targetLon: Double,
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        limit: Int = 20
    ): List<GisFeatureEntity>

    @Query("SELECT * FROM imported_packages ORDER BY imported_at DESC")
    suspend fun getAllPackages(): List<ImportedPackageEntity>

    @Query("SELECT * FROM imported_packages WHERE status = 'ready' ORDER BY id DESC LIMIT 1")
    suspend fun getActivePackage(): ImportedPackageEntity?

    @Query("SELECT * FROM imported_packages WHERE id = :packageId LIMIT 1")
    suspend fun getPackageById(packageId: Long): ImportedPackageEntity?

    @Query("SELECT COUNT(*) FROM gis_features WHERE package_id = :packageId")
    suspend fun getFeatureCountByPackage(packageId: Long): Int
}
