package com.spotlog

import com.spotlog.data.ImportParser
import com.spotlog.data.ImportResult
import com.spotlog.data.ImportValidationError
import org.junit.Assert.*
import org.junit.Test

class ImportParserTest {

    @Test
    fun parseValidJson_returnsSuccess() {
        val json = """
            {
                "version": 1,
                "places": [
                    {
                        "name": "Eiffel Tower",
                        "lat": 48.8584,
                        "lon": 2.2945,
                        "category": "monument",
                        "comment": "Paris landmark",
                        "visits": [
                            { "timestamp": "2024-01-15T10:30:00", "comment": "First visit" }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val result = ImportParser.parse(json)
        assertTrue(result is ImportResult.Success)
        val success = result as ImportResult.Success
        assertEquals(1, success.places.size)
        val place = success.places.first()
        assertEquals("Eiffel Tower", place.name)
        assertEquals(48.8584, place.lat, 0.0001)
        assertEquals(2.2945, place.lon, 0.0001)
        assertEquals("monument", place.category)
        assertEquals("Paris landmark", place.comment)
        assertEquals(1, place.visits.size)
        val visit = place.visits.first()
        assertTrue(visit.timestamp > 0)
        assertEquals("First visit", visit.comment)
    }

    @Test
    fun parseInvalidVersion_returnsError() {
        val json = """
            {
                "version": 99,
                "places": []
            }
        """.trimIndent()

        val result = ImportParser.parse(json)
        assertTrue(result is ImportResult.Error)
        val error = (result as ImportResult.Error).error
        assertTrue(error is ImportValidationError.UnsupportedVersion)
        assertEquals(99, (error as ImportValidationError.UnsupportedVersion).version)
    }

    @Test
    fun parseInvalidJson_returnsError() {
        val json = "not a json"

        val result = ImportParser.parse(json)
        assertTrue(result is ImportResult.Error)
        val error = (result as ImportResult.Error).error
        assertTrue(error is ImportValidationError.InvalidJson)
    }

    @Test
    fun parseEmptyPlaces_returnsEmptySuccess() {
        val json = """
            {
                "version": 1,
                "places": []
            }
        """.trimIndent()

        val result = ImportParser.parse(json)
        assertTrue(result is ImportResult.Success)
        assertEquals(0, (result as ImportResult.Success).places.size)
    }

    @Test
    fun parseMissingTimestamp_returnsError() {
        val json = """
            {
                "version": 1,
                "places": [
                    {
                        "name": "Place",
                        "lat": 0.0,
                        "lon": 0.0,
                        "category": "other",
                        "visits": [
                            { "comment": "no timestamp" }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val result = ImportParser.parse(json)
        assertTrue(result is ImportResult.PartialSuccess)
        val partial = result as ImportResult.PartialSuccess
        assertTrue(partial.errors.isNotEmpty())
        assertTrue(partial.errors.any { it is ImportValidationError.InvalidTimestamp })
    }
}