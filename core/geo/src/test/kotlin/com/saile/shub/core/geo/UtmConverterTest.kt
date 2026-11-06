package com.saile.shub.core.geo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UtmConverterTest {
    @Test
    fun `converts Rio de Janeiro geo coordinate to expected UTM`() {
        val utm = UtmConverter.geoToUtm(GeoCoordinate(-22.951761169561568, -43.21047133691749))

        assertEquals(23, utm.zone)
        assertEquals(Hemisphere.SOUTH, utm.hemisphere)
        assertEquals(683_479.47, utm.easting, absoluteTolerance = 0.5)
        assertEquals(7_460_703.39, utm.northing, absoluteTolerance = 1.0)
    }

    @Test
    fun `converts Porto Velho Rondonia geo coordinate to Zone 20S`() {
        val portoVelho = GeoCoordinate(-8.761944, -63.903889)
        val utm = UtmConverter.geoToUtm(portoVelho)

        assertEquals(20, utm.zone)
        assertEquals(Hemisphere.SOUTH, utm.hemisphere)
        assertEquals('L', UtmConverter.getUtmBand(portoVelho.latitude))

        val restored = UtmConverter.utmToGeo(utm)
        assertEquals(portoVelho.latitude, restored.latitude, absoluteTolerance = 1e-6)
        assertEquals(portoVelho.longitude, restored.longitude, absoluteTolerance = 1e-6)
    }

    @Test
    fun `round trip preserves southern and northern hemisphere coordinates`() {
        val coordinates = listOf(
            GeoCoordinate(-22.951761169561568, -43.21047133691749), // Rio de Janeiro (23S)
            GeoCoordinate(-10.885, -61.951), // Ji-Paraná, RO (20S)
            GeoCoordinate(-12.740, -60.145), // Vilhena, RO near 20S/21S boundary
            GeoCoordinate(2.8235, -60.6758), // Boa Vista, RR (20N)
            GeoCoordinate(0.0, -51.066), // Macapá Equator
        )

        for (original in coordinates) {
            val utm = UtmConverter.geoToUtm(original)
            val restored = UtmConverter.utmToGeo(utm)
            assertEquals(original.latitude, restored.latitude, absoluteTolerance = 1e-5)
            assertEquals(original.longitude, restored.longitude, absoluteTolerance = 1e-5)
        }
    }

    @Test
    fun `supports inter-zone UTM conversion between Zone 20S and Zone 21S in Rondonia`() {
        // Coordinate near the -60.0 meridian boundary in Rondônia (Vilhena / Pimenta Bueno region)
        val boundaryPoint = GeoCoordinate(-12.15, -60.05)
        val utm20 = UtmConverter.geoToUtm(boundaryPoint)
        assertEquals(20, utm20.zone)

        val utm21 = UtmConverter.convertUtmZone(utm20, targetZone = 21)
        assertEquals(21, utm21.zone)
        assertEquals(Hemisphere.SOUTH, utm21.hemisphere)

        // Converting back from Zone 21S to Geo must recover the original Lat/Lon
        val restoredGeo = UtmConverter.utmToGeo(utm21)
        assertEquals(boundaryPoint.latitude, restoredGeo.latitude, absoluteTolerance = 1e-4)
        assertEquals(boundaryPoint.longitude, restoredGeo.longitude, absoluteTolerance = 1e-4)
    }

    @Test
    fun `rejects coordinates outside the UTM latitude range`() {
        assertFailsWith<IllegalArgumentException> {
            UtmConverter.geoToUtm(GeoCoordinate(85.0, 0.0))
        }
        assertFailsWith<IllegalArgumentException> {
            UtmConverter.geoToUtm(GeoCoordinate(-81.0, 0.0))
        }
    }

    @Test
    fun `rejects invalid UTM zone in both utmToGeo and forcedZone`() {
        assertFailsWith<IllegalArgumentException> {
            UtmConverter.utmToGeo(UtmCoordinate(61, Hemisphere.NORTH, 500_000.0, 0.0))
        }
        assertFailsWith<IllegalArgumentException> {
            UtmConverter.geoToUtm(GeoCoordinate(-10.0, -63.0), forcedZone = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            UtmConverter.geoToUtm(GeoCoordinate(-10.0, -63.0), forcedZone = 61)
        }
    }

    @Test
    fun `rejects NaN coordinates`() {
        assertFailsWith<IllegalArgumentException> {
            UtmConverter.geoToUtm(GeoCoordinate(Double.NaN, -63.0))
        }
        assertFailsWith<IllegalArgumentException> {
            UtmConverter.utmToGeo(UtmCoordinate(20, Hemisphere.SOUTH, Double.NaN, 8_800_000.0))
        }
    }

    @Test
    fun `formats Rondonia coordinates in sFoto UTM mode with zone and band`() {
        val band = UtmConverter.getUtmBand(-10.828)
        assertEquals('L', band)

        val utmText = UtmConverter.formatUtm(-10.828, -63.284)
        assertTrue(utmText.startsWith("20L "))
    }

    @Test
    fun `formats decimal degrees into DMS accurately`() {
        val dms = UtmConverter.formatDms(-8.761944, -63.903889)
        assertTrue(dms.contains("8°45'"))
        assertTrue(dms.contains("S"))
        assertTrue(dms.contains("63°54'"))
        assertTrue(dms.contains("W"))
    }
}
