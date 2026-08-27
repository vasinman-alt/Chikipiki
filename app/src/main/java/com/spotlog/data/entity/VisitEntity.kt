package com.spotlog.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class VisitSource { MANUAL, IMPORTED_FILE, IMPORTED_MANUAL_OLD }

@Entity(
    tableName = "visits",
    foreignKeys = [ForeignKey(
        entity = PlaceEntity::class,
        parentColumns = ["id"],
        childColumns = ["placeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("placeId"),
        Index("timestamp"), // Добавлен индекс для сортировки по времени
        Index(value = ["placeId", "timestamp"]) // Композитный индекс для частых запросов
    ]
)
data class VisitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placeId: Long,
    val timestamp: Long,
    val comment: String = "",
    val systemNote: String? = null,
    val source: VisitSource = VisitSource.MANUAL
)