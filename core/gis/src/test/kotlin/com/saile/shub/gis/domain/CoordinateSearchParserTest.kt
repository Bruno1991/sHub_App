package com.saile.shub.gis.domain

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CoordinateSearchParserTest {

    @Test
    fun testParseGeographicDecimalWithComma() {
        val res = CoordinateSearchParser.parse("-11.434, -61.447")
        assertNotNull(res)
        assertEquals(-11.434, res.latitude, 0.0001)
        assertEquals(-61.447, res.longitude, 0.0001)
        assertEquals(false, res.isUtm)
        assertTrue(res.title.contains("-11.434000"))
    }

    @Test
    fun testParseGeographicDecimalWithSpace() {
        val res = CoordinateSearchParser.parse("-11.434 -61.447")
        assertNotNull(res)
        assertEquals(-11.434, res.latitude, 0.0001)
        assertEquals(-61.447, res.longitude, 0.0001)
    }

    @Test
    fun testParseUtmWithZone() {
        val res = CoordinateSearchParser.parse("20S 569402 8767709")
        assertNotNull(res)
        assertEquals(true, res.isUtm)
        assertTrue(res.latitude < -10.0 && res.latitude > -12.0)
        assertTrue(res.longitude < -59.0 && res.longitude > -65.0)
    }

    @Test
    fun testParseUtmRawNumbersDefaultsToZone20S() {
        val res = CoordinateSearchParser.parse("569402, 8767709")
        assertNotNull(res)
        assertEquals(true, res.isUtm)
        assertTrue(res.title.contains("569402"))
        assertTrue(res.latitude < -10.0 && res.latitude > -12.0)
        assertTrue(res.longitude < -59.0 && res.longitude > -65.0)
    }

    @Test
    fun testParseNonCoordinateReturnsNull() {
        val res = CoordinateSearchParser.parse("PTUP0024")
        kotlin.test.assertNull(res)
    }
}

