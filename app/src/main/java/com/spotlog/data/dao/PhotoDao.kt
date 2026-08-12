package com.spotlog.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.spotlog.data.entity.PhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: PhotoEntity)

    @Query("SELECT * FROM photos WHERE placeId = :placeId ORDER BY createdAt DESC")
    fun getPhotosForPlace(placeId: Long): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE id = :photoId")
    suspend fun getPhotoById(photoId: Long): PhotoEntity?

    @Query("DELETE FROM photos WHERE id = :photoId")
    suspend fun deletePhoto(photoId: Long)

    @Query("UPDATE photos SET isCover = 0 WHERE placeId = :placeId AND isCover = 1")
    suspend fun clearCover(placeId: Long)

    @Query("UPDATE photos SET isCover = 1 WHERE id = :photoId")
    suspend fun setCover(photoId: Long)

    @Query("SELECT * FROM photos WHERE placeId = :placeId AND isCover = 1 LIMIT 1")
    suspend fun getCoverPhoto(placeId: Long): PhotoEntity?

    // Новый метод: снять обложку с конкретного фото
    @Query("UPDATE photos SET isCover = 0 WHERE id = :photoId")
    suspend fun clearCoverForPhoto(photoId: Long)
}