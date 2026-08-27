package com.saile.shub.normative.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.saile.shub.normative.database.dao.NormativeDao
import com.saile.shub.normative.database.entities.*

@Database(
    entities = [
        SourceDocumentEntity::class,
        ClauseEntity::class,
        TransformerPerformanceEntity::class,
        TransformerPbeNewEntity::class,
        FuseSelectionEntity::class,
        ProdistQualityEntity::class,
        NormativeFtsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NormativeDatabase : RoomDatabase() {

    abstract fun normativeDao(): NormativeDao

    companion object {
        @Volatile
        private var INSTANCE: NormativeDatabase? = null

        fun getInstance(context: Context): NormativeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NormativeDatabase::class.java,
                    "normative.db"
                )
                .createFromAsset("databases/normative.db")
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
