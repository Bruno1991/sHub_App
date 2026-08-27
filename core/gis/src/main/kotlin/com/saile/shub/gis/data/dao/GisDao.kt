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

    // Example search query prioritizing prefix/exact matches and paginating
    @Query("""
        SELECT * FROM gis_features 
        WHERE code_norm LIKE :query || '%' 
        ORDER BY 
            CASE WHEN code_norm = :query THEN 0 ELSE 1 END,
            code_norm ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun searchFeatures(query: String, limit: Int, offset: Int): List<GisFeatureEntity>
    
    @Query("SELECT * FROM imported_packages ORDER BY imported_at DESC")
    suspend fun getAllPackages(): List<ImportedPackageEntity>
}
