package com.spotlog.data.dao

import androidx.room.*
import com.spotlog.data.entity.VisitEntity
import kotlinx.coroutines.flow.Flow

data class VisitWithPlace(
    val visitId: Long,
    val placeId: Long,
    val placeName: String,
    val placeCategory: String,
    val placeCountry: String?,
    val placeRegion: String?,
    val timestamp: Long,
    val comment: String,
    val systemNote: String?,
    val source: String
)

@Dao
interface VisitDao {

    @Query("""
        SELECT visits.id AS visitId, visits.placeId, places.name AS placeName,
               places.category AS placeCategory,
               places.country AS placeCountry, places.region AS placeRegion,
               visits.timestamp, visits.comment,
               visits.systemNote, visits.source
        FROM visits INNER JOIN places ON visits.placeId = places.id
        ORDER BY visits.timestamp DESC
    """)
    fun getAllVisitsWithPlace(): Flow<List<VisitWithPlace>>

    @Query("""
        SELECT visits.id AS visitId, visits.placeId, places.name AS placeName,
               places.category AS placeCategory,
               places.country AS placeCountry, places.region AS placeRegion,
               visits.timestamp, visits.comment,
               visits.systemNote, visits.source
        FROM visits INNER JOIN places ON visits.placeId = places.id
        WHERE visits.placeId = :placeId
        ORDER BY visits.timestamp DESC
    """)
    fun getVisitsForPlace(placeId: Long): Flow<List<VisitWithPlace>>

    @Query("SELECT * FROM visits WHERE id = :id")
    suspend fun getVisitById(id: Long): VisitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisit(visit: VisitEntity): Long

    @Update
    suspend fun updateVisit(visit: VisitEntity)

    @Query("DELETE FROM visits WHERE id = :visitId")
    suspend fun deleteVisit(visitId: Long)
}