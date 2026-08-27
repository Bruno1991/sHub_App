package com.saile.shub.core.geo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UtmConverterTest {
    @Test
    fun `converts Rio de Janeiro geo coordinate to expected UTM`() {
        val utm = UtmConverter.geoToUtm(GeoCoordinate(-22.951761169561568, -43.21047133691749))

        assertEquals(23, utm.zone)
        assertEquals(Hemisphere.SOUTH, utm.hemisphere)
        assertEquals(683_479.47, utm.easting, absoluteTolerance = 0.5)
        // A fonte de referência arredonda o northing; a conversão WGS84 aceita 1 metro.
        assertEquals(7_460_703.39, utm.northing, absoluteTolerance = 1.0)
    }

    @Test
    fun `round trip preserves southern hemisphere coordinate`() {
        val original = GeoCoordinate(-22.951761169561568, -43.21047133691749)
        val restored = UtmConverter.utmToGeo(UtmConverter.geoToUtm(original))

        assertEquals(original.latitude, restored.latitude, absoluteTolerance = 0.000001)
        assertEquals(original.longitude, restored.longitude, absoluteTolerance = 0.000001)
    }

    @Test
    fun `rejects coordinates outside the UTM latitude range`() {
        assertFailsWith<IllegalArgumentException> {
            UtmConverter.geoToUtm(GeoCoordinate(85.0, 0.0))
        }
    }

    @Test
    fun `rejects invalid UTM zone`() {
        assertFailsWith<IllegalArgumentException> {
            UtmConverter.utmToGeo(UtmCoordinate(61, Hemisphere.NORTH, 500_000.0, 0.0))
        }
    }
}
