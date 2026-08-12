package com.spotlog.viewmodel

data class CategoryFilter(
    val id: String?,
    val label: String,
    val categories: List<String>
) {
    companion object {
        val ALL = CategoryFilter(null, "Все", emptyList())

        val ALL_FILTERS: List<CategoryFilter> = listOf(
            CategoryFilter("food", "Еда", listOf("cafe", "restaurant", "bar", "fast_food", "pub")),
            CategoryFilter("shops", "Магазины", listOf("shop", "supermarket", "convenience")),
            CategoryFilter("culture", "Культура", listOf("museum", "viewpoint", "theatre", "library", "place_of_worship")),
            CategoryFilter("nature", "Природа", listOf("park", "nature", "forest", "beach")),
            CategoryFilter("hotels", "Отели", listOf("hotel", "motel", "hostel")),
            CategoryFilter("other", "Другое", listOf("custom", "factory", "office", "transport", "religion"))
        )
    }
}