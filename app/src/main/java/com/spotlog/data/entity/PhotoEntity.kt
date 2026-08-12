package com.spotlog.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PhotoSource { GALLERY, CAMERA }

@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = PlaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["placeId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = VisitEntity::class,
            parentColumns = ["id"],
            childColumns = ["visitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("placeId"), Index("visitId")]
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placeId: Long,
    val visitId: Long? = null,
    val filePath: String,
    val isCover: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val source: PhotoSource
)