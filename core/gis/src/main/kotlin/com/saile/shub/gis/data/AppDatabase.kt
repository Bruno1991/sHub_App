package com.saile.shub.gis.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.saile.shub.gis.data.entities.GisFeatureEntity
import com.saile.shub.gis.data.entities.ImportedPackageEntity
import com.saile.shub.gis.data.dao.GisDao

@Database(
    entities = [ImportedPackageEntity::class, GisFeatureEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gisDao(): GisDao
}
