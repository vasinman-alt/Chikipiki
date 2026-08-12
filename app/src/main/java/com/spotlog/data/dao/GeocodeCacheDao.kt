package com.spotlog.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.spotlog.data.entity.GeocodeCacheEntity

@Dao
interface GeocodeCacheDao {
    @Query("SELECT * FROM geocode_cache WHERE cellKey = :cellKey")
    suspend fun getByCellKey(cellKey: String): GeocodeCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: GeocodeCacheEntity)

    @Query("DELETE FROM geocode_cache WHERE cachedAt < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)
}