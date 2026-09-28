package com.uglygameface.atmosynq.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeocodingClientTest {
    @Test
    fun parsesGlobalCityAndPostalMetadata() {
        val json =
            """
            {
              "results": [
                {
                  "id": 123,
                  "name": "Windsor",
                  "latitude": 41.8526,
                  "longitude": -72.6437,
                  "elevation": 17.0,
                  "feature_code": "PPL",
                  "country_code": "US",
                  "country": "United States",
                  "admin1": "Connecticut",
                  "population": 29000,
                  "postcodes": ["06095", "06006"]
                },
                {
                  "id": 456,
                  "name": "Tokyo",
                  "latitude": 35.6895,
                  "longitude": 139.6917,
                  "feature_code": "PPLC",
                  "country_code": "JP",
                  "country": "Japan",
                  "admin1": "Tokyo",
                  "population": 14000000,
                  "postcodes": ["100-0001"]
                }
              ]
            }
            """.trimIndent()

        val results = GeocodingClient().parse(json)

        assertEquals(2, results.size)
        assertEquals("Windsor, Connecticut, US", results[0].displayLabel())
        assertEquals("Windsor, Connecticut", results[0].shortLabel())
        assertEquals(listOf("06095", "06006"), results[0].postcodes)
        assertEquals(29_000L, results[0].population)

        assertEquals("Tokyo, JP", results[1].displayLabel())
        assertTrue(results[1].postcodes.contains("100-0001"))
        assertEquals(14_000_000L, results[1].population)
    }

    @Test
    fun missingResultsReturnsEmptyList() {
        assertTrue(
            GeocodingClient()
                .parse("""{"generationtime_ms":0.3}""")
                .isEmpty()
        )
    }
}
