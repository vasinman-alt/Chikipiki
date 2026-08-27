package com.spotlog.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.spotlog.data.entity.PlaceEntity
import kotlinx.coroutines.flow.Flow

data class CountryStatRaw(
    val country: String,
    val visitCount: Int,
    val lastVisit: Long
)

data class RegionStatRaw(
    val country: String,
    val region: String,
    val visitCount: Int,
    val lastVisit: Long
)

data class PlaceCardWithCover(
    val placeId: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val category: String,
    val country: String?,
    val region: String?,
    val comment: String,
    val visitCount: Int,
    val lastVisitTimestamp: Long?,
    val coverPhotoPath: String?
)

@Dao
interface PlaceDao {

    @Query("SELECT * FROM places ORDER BY id DESC")
    fun getAllPlaces(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places WHERE id = :placeId")
    suspend fun getPlaceById(placeId: Long): PlaceEntity?

    @Query("SELECT * FROM places WHERE id = :placeId")
    fun getPlaceFlow(placeId: Long): Flow<PlaceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlace(place: PlaceEntity): Long

    @Query("""
        SELECT * FROM places 
        WHERE abs(latitude - :lat) < 0.001 AND abs(longitude - :lon) < 0.001
    """)
    suspend fun findNearby(lat: Double, lon: Double): List<PlaceEntity>

    @Query("UPDATE places SET name = :name, category = :category WHERE id = :placeId")
    suspend fun updatePlaceInfo(placeId: Long, name: String, category: String)

    @Query("UPDATE places SET comment = :comment WHERE id = :placeId")
    suspend fun updatePlaceComment(placeId: Long, comment: String)

    @Query("UPDATE places SET country = :country, region = :region WHERE id = :placeId")
    suspend fun updatePlaceCountry(placeId: Long, country: String?, region: String?)

    @Query("DELETE FROM places WHERE id = :placeId")
    suspend fun deletePlace(placeId: Long)

    @Query("""
        SELECT places.country AS country, COUNT(visits.id) AS visitCount, MAX(visits.timestamp) AS lastVisit
        FROM places
        INNER JOIN visits ON visits.placeId = places.id
        WHERE places.country IS NOT NULL AND places.country != ''
        GROUP BY places.country
        ORDER BY lastVisit DESC
    """)
    fun getCountryStats(): Flow<List<CountryStatRaw>>

    @Query("""
        SELECT places.country AS country, places.region AS region, COUNT(visits.id) AS visitCount, MAX(visits.timestamp) AS lastVisit
        FROM places
        INNER JOIN visits ON visits.placeId = places.id
        WHERE places.country IS NOT NULL AND places.country != '' AND places.region IS NOT NULL AND places.region != ''
        GROUP BY places.country, places.region
        ORDER BY lastVisit DESC
    """)
    fun getRegionStats(): Flow<List<RegionStatRaw>>

    @Query("""
        SELECT 
            places.id AS placeId,
            places.name,
            places.latitude,
            places.longitude,
            places.category,
            places.country,
            places.region,
            places.comment,
            COUNT(visits.id) AS visitCount,
            MAX(visits.timestamp) AS lastVisitTimestamp,
            (SELECT filePath FROM photos WHERE photos.placeId = places.id AND photos.isCover = 1 LIMIT 1) AS coverPhotoPath
        FROM places
        LEFT JOIN visits ON visits.placeId = places.id
        GROUP BY places.id
        ORDER BY lastVisitTimestamp DESC
    """)
    fun getPlaceCardsWithCover(): Flow<List<PlaceCardWithCover>>
}