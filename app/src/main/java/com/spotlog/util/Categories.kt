package com.spotlog.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.*

data class Category(val id: String, val label: String, val icon: ImageVector)

object Categories {
    val PREDEFINED = listOf(
        Category("cafe", "Кафе", Icons.Filled.LocalCafe),
        Category("restaurant", "Ресторан", Icons.Filled.Restaurant),
        Category("bar", "Бар", Icons.Filled.LocalBar),
        Category("shop", "Магазин", Icons.Filled.Store),
        Category("hotel", "Отель", Icons.Filled.Hotel),
        Category("park", "Парк", Icons.Filled.Park),
        Category("museum", "Музей", Icons.Filled.AccountBalance),
        Category("viewpoint", "Смотровая", Icons.Filled.Visibility),
        Category("factory", "Завод", Icons.Filled.Factory),
        Category("office", "Офис", Icons.Filled.BusinessCenter),
        Category("religion", "Храм", Icons.Filled.Church),
        Category("transport", "Транспорт", Icons.Filled.DirectionsBus),
        Category("nature", "Природа", Icons.Filled.Forest),
        Category("custom", "Другое", Icons.Filled.Place)
    )

    fun mapOsmTagToId(raw: String): String {
        val parts = raw.split("=", limit = 2)
        val key = parts.getOrElse(0) { "" }
        val value = parts.getOrElse(1) { "" }
        return when {
            key == "amenity" && value == "cafe" -> "cafe"
            key == "amenity" && value == "restaurant" -> "restaurant"
            key == "amenity" && value == "bar" -> "bar"
            key == "amenity" && value == "pub" -> "bar"
            key == "amenity" && value == "place_of_worship" -> "religion"
            key == "amenity" && value in listOf("bus_station") -> "transport"
            key == "shop" -> "shop"
            key == "tourism" && value == "hotel" -> "hotel"
            key == "tourism" && value == "museum" -> "museum"
            key == "tourism" && value == "viewpoint" -> "viewpoint"
            key == "leisure" && value == "park" -> "park"
            key == "office" -> "office"
            key == "man_made" && value == "works" -> "factory"
            key == "landuse" && value == "industrial" -> "factory"
            key == "railway" && value == "station" -> "transport"
            key == "highway" && value == "bus_stop" -> "transport"
            key == "natural" -> "nature"
            key == "place" -> "custom"
            else -> "custom"
        }
    }

    fun resolveIcon(categoryRaw: String): ImageVector {
        val predefined = PREDEFINED.find { it.id == categoryRaw }
        if (predefined != null) return predefined.icon

        // попытка маппинга из OSM-тега
        val mappedId = mapOsmTagToId(categoryRaw)
        val mapped = PREDEFINED.find { it.id == mappedId }
        if (mapped != null && mapped.id != "custom") return mapped.icon

        // эвристика по ключевым словам для произвольного текста
        val lower = categoryRaw.lowercase(Locale.getDefault())
        return when {
            "кафе" in lower || "coffee" in lower -> Icons.Filled.LocalCafe
            "ресторан" in lower -> Icons.Filled.Restaurant
            "бар" in lower || "паб" in lower -> Icons.Filled.LocalBar
            "магазин" in lower || "shop" in lower -> Icons.Filled.Store
            "отель" in lower || "гостиниц" in lower -> Icons.Filled.Hotel
            "парк" in lower -> Icons.Filled.Park
            "музей" in lower -> Icons.Filled.AccountBalance
            "завод" in lower || "фабрик" in lower -> Icons.Filled.Factory
            "храм" in lower || "церковь" in lower -> Icons.Filled.Church
            else -> Icons.Filled.Place
        }
    }
}