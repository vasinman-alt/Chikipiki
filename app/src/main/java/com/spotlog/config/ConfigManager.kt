// ==== ФАЙЛ: ConfigManager.kt ====
package com.spotlog.config

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Конфигурация POI‑поиска.
 *
 * Загружает `spotlog_config.json` из assets и предоставляет типизированный
 * доступ к активному источнику данных и фильтрам.
 *
 * Схема (см. `spotlog_config.json`):
 *  - `active_source` — имя активного источника (должно совпадать с одним из `sources[*].name`)
 *  - `sources[]`     — список источников с зеркалами, шаблоном запроса, маппингом ответа
 *  - `filters`       — правила исключения/включения POI
 */
class ConfigManager private constructor(context: Context) {

    private val config: JSONObject

    init {
        config = context.assets
            .open(ASSET_NAME)
            .bufferedReader()
            .use { it.readText() }
            .let { JSONObject(it) }
    }

    /** Активный источник (полностью развёрнутая структура). */
    fun getActiveSource(): SourceConfig {
        val activeName = config.optString(FIELD_ACTIVE_SOURCE, "")
        val sourcesArray = config.optJSONArray(FIELD_SOURCES) ?: JSONArray()
        for (i in 0 until sourcesArray.length()) {
            val obj = sourcesArray.optJSONObject(i) ?: continue
            if (obj.optString(FIELD_NAME) == activeName) {
                return SourceConfig.fromJson(obj)
            }
        }
        // Если ничего не нашли — берём первый источник как fallback,
        // чтобы приложение не падало, если в конфиге что-то поменяли.
        if (sourcesArray.length() > 0) {
            return SourceConfig.fromJson(sourcesArray.getJSONObject(0))
        }
        // Совсем ничего нет — возвращаем дефолт
        return SourceConfig.defaults()
    }

    /** Фильтры (exclude_keys, exclude_tags, include_*). */
    fun getFilters(): FilterConfig {
        val obj = config.optJSONObject(FIELD_FILTERS) ?: JSONObject()
        return FilterConfig.fromJson(obj)
    }

    /** Имя активного источника (без всей структуры). */
    fun getActiveSourceName(): String = getActiveSource().name

    /** Список зеркал активного источника. */
    fun getMirrorUrls(): List<String> = getActiveSource().mirrors

    /** Шаблон запроса активного источника. */
    fun getQueryTemplate(): String = getActiveSource().queryTemplate

    /** Маппинг полей ответа. */
    fun getResponseMapping(): ResponseMapping = getActiveSource().responseMapping

    /** Требуется ли User-Agent (по умолчанию — да, чтобы пройти проверку зеркал). */
    fun isUserAgentRequired(): Boolean = getActiveSource().requiresUserAgent

    /** User-Agent, который передаётся в запросах. */
    fun getUserAgent(): String =
        if (isUserAgentRequired()) USER_AGENT else "Chikipiki/1.0 (Android)"

    companion object {
        private const val ASSET_NAME = "spotlog_config.json"

        // Ключи верхнего уровня
        private const val FIELD_ACTIVE_SOURCE = "active_source"
        private const val FIELD_SOURCES = "sources"
        private const val FIELD_FILTERS = "filters"

        // Поля внутри source
        private const val FIELD_NAME = "name"
        private const val FIELD_DISPLAY_NAME = "display_name"
        private const val FIELD_MIRRORS = "mirrors"
        private const val FIELD_QUERY_TEMPLATE = "query_template"
        private const val FIELD_RESPONSE_MAPPING = "response_mapping"
        private const val FIELD_REQUIRES_USER_AGENT = "requires_user_agent"

        // Поля внутри mapping
        private const val FIELD_ELEMENTS_ARRAY = "elements_array"
        private const val FIELD_NAME_FIELD = "name_field"
        private const val FIELD_LAT_FIELD = "lat_field"
        private const val FIELD_LON_FIELD = "lon_field"
        private const val FIELD_CATEGORY_FIELD = "category_field"

        // Поля внутри filters
        private const val FIELD_EXCLUDE_KEYS = "exclude_keys"
        private const val FIELD_EXCLUDE_TAGS = "exclude_tags"
        private const val FIELD_INCLUDE_PLACE_NODES = "include_place_nodes"
        private const val FIELD_PLACE_VALUES = "place_values"
        private const val FIELD_INCLUDE_BUILDINGS_WITH_NAME = "include_buildings_with_name"
        private const val FIELD_INCLUDE_OFFICES = "include_offices"
        private const val FIELD_INCLUDE_INDUSTRIAL = "include_industrial"

        /** User-Agent для HTTP‑запросов (см. policy Nominatim / Overpass). */
        private const val USER_AGENT =
            "Chikipiki/1.0 (Android; contact: dev@example.com)"

        @Volatile
        private var instance: ConfigManager? = null

        fun getInstance(context: Context): ConfigManager =
            instance ?: synchronized(this) {
                instance ?: ConfigManager(context.applicationContext).also { instance = it }
            }
    }
}

/* -------------------------------------------------------------------------
 *  Источник данных
 * ------------------------------------------------------------------------- */
data class SourceConfig(
    val name: String,
    val displayName: String,
    val mirrors: List<String>,
    val queryTemplate: String,
    val responseMapping: ResponseMapping,
    val requiresUserAgent: Boolean
) {
    companion object {
        fun fromJson(obj: JSONObject): SourceConfig {
            val mirrorsArray = obj.optJSONArray("mirrors") ?: JSONArray()
            val mirrors = (0 until mirrorsArray.length())
                .mapNotNull { i -> mirrorsArray.optString(i, "").takeIf { it.isNotBlank() } }

            return SourceConfig(
                name = obj.optString("name", "overpass"),
                displayName = obj.optString("display_name", obj.optString("name", "overpass")),
                mirrors = mirrors,
                queryTemplate = obj.optString(
                    "query_template",
                    "[out:json];(node[\"name\"](around:500,{lat},{lon}););out;"
                ),
                responseMapping = ResponseMapping.fromJson(obj.optJSONObject("response_mapping")),
                requiresUserAgent = obj.optBoolean("requires_user_agent", true)
            )
        }

        /** Дефолтный источник, если в конфиге вообще ничего нет. */
        fun defaults(): SourceConfig = SourceConfig(
            name = "overpass",
            displayName = "Overpass API",
            mirrors = listOf("https://overpass-api.de/api/interpreter"),
            queryTemplate = "[out:json];(node[\"name\"](around:500,{lat},{lon}););out;",
            responseMapping = ResponseMapping.defaults(),
            requiresUserAgent = true
        )
    }
}

/* -------------------------------------------------------------------------
 *  Маппинг полей JSON‑ответа на наши модели
 * ------------------------------------------------------------------------- */
data class ResponseMapping(
    val elementsArray: String,
    val nameField: String,
    val latField: String,
    val lonField: String,
    val categoryField: String?
) {
    companion object {
        fun fromJson(obj: JSONObject?): ResponseMapping {
            if (obj == null) return defaults()
            return ResponseMapping(
                elementsArray = obj.optString("elements_array", "elements"),
                nameField = obj.optString("name_field", "tags.name"),
                latField = obj.optString("lat_field", "lat"),
                lonField = obj.optString("lon_field", "lon"),
                categoryField = obj.optString("category_field", "tags.amenity").takeIf { it.isNotBlank() }
            )
        }

        fun defaults(): ResponseMapping = ResponseMapping(
            elementsArray = "elements",
            nameField = "tags.name",
            latField = "lat",
            lonField = "lon",
            categoryField = "tags.amenity"
        )
    }
}

/* -------------------------------------------------------------------------
 *  Фильтры (исключения/включения)
 * ------------------------------------------------------------------------- */
data class FilterConfig(
    val excludeKeys: Set<String>,
    val excludeTags: Map<String, Set<String>>,
    val includePlaceNodes: Boolean,
    val placeValues: Set<String>,
    val includeBuildingsWithName: Boolean,
    val includeOffices: Boolean,
    val includeIndustrial: Boolean
) {
    companion object {
        fun fromJson(obj: JSONObject): FilterConfig {
            val excludeKeys = obj.optJSONArray("exclude_keys")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it, "").takeIf { s -> s.isNotBlank() } }
            }.orEmpty().toSet()

            val excludeTags = mutableMapOf<String, Set<String>>()
            obj.optJSONObject("exclude_tags")?.let { tagsObj ->
                val keys = tagsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val values = tagsObj.optJSONArray(k)
                    val set = if (values != null) {
                        (0 until values.length())
                            .mapNotNull { values.optString(it, "").takeIf { it.isNotBlank() } }
                            .toSet()
                    } else emptySet()
                    excludeTags[k] = set
                }
            }

            val placeValues = obj.optJSONArray("place_values")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it, "").takeIf { it.isNotBlank() } }
            }.orEmpty().toSet()

            return FilterConfig(
                excludeKeys = excludeKeys,
                excludeTags = excludeTags,
                includePlaceNodes = obj.optBoolean("include_place_nodes", true),
                placeValues = placeValues,
                includeBuildingsWithName = obj.optBoolean("include_buildings_with_name", true),
                includeOffices = obj.optBoolean("include_offices", true),
                includeIndustrial = obj.optBoolean("include_industrial", true)
            )
        }
    }
}
