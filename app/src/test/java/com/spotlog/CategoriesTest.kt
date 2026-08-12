package com.spotlog

import com.spotlog.util.Categories
import org.junit.Test
import org.junit.Assert.*

class CategoriesTest {
    @Test
    fun `resolveIcon returns expected icon for known category`() {
        val icon = Categories.resolveIcon("cafe")
        // Проверяем, что иконка не null (конкретный ImageVector сравнивать не будем)
        assertNotNull(icon)
    }

    @Test
    fun `mapOsmTagToId maps amenity=cafe to cafe`() {
        assertEquals("cafe", Categories.mapOsmTagToId("amenity=cafe"))
    }

    @Test
    fun `mapOsmTagToId returns custom for unknown tag`() {
        assertEquals("custom", Categories.mapOsmTagToId("unknown=value"))
    }
}