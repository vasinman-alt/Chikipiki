package com.spotlog.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.spotlog.data.dao.*
import com.spotlog.data.entity.*

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys = ON;")
        db.execSQL("""
            CREATE TABLE photos (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                placeId INTEGER NOT NULL,
                visitId INTEGER,
                filePath TEXT NOT NULL,
                isCover INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL,
                source TEXT NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX index_photos_placeId ON photos(placeId)")
        db.execSQL("CREATE INDEX index_photos_visitId ON photos(visitId)")
        db.execSQL("ALTER TABLE places ADD COLUMN comment TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys = ON;")
        db.execSQL("""
            CREATE TABLE visits_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                placeId INTEGER NOT NULL,
                timestamp INTEGER NOT NULL,
                comment TEXT NOT NULL DEFAULT '',
                FOREIGN KEY (placeId) REFERENCES places(id) ON DELETE CASCADE
            )
        """)
        db.execSQL("""
            INSERT INTO visits_new (id, placeId, timestamp, comment)
            SELECT id, placeId, timestamp, comment FROM visits
        """)
        db.execSQL("DROP TABLE visits")
        db.execSQL("ALTER TABLE visits_new RENAME TO visits")
        db.execSQL("CREATE INDEX index_visits_placeId ON visits(placeId)")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys = ON;")
        db.execSQL("ALTER TABLE visits ADD COLUMN systemNote TEXT")
        db.execSQL("ALTER TABLE visits ADD COLUMN source TEXT NOT NULL DEFAULT 'MANUAL'")
    }
}

@Database(
    entities = [PlaceEntity::class, VisitEntity::class, PhotoEntity::class, GeocodeCacheEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun placeDao(): PlaceDao
    abstract fun visitDao(): VisitDao
    abstract fun photoDao(): PhotoDao
    abstract fun geocodeCacheDao(): GeocodeCacheDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "chikipiki_database"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { instance = it }
            }
    }
}