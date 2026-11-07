package com.saile.shub.gis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GisRuralGuidanceTest {

    @Test
    fun `calculateRuralGuidance computes accurate distance and cardinal bearing`() {
        // From a point to ~1.11 km North
        val guidanceNorth = GisNavigationHelper.calculateRuralGuidance(
            fromLat = -10.880000,
            fromLon = -61.950000,
            toLat = -10.870000,
            toLon = -61.950000
        )
        assertTrue(guidanceNorth.distanceMeters in 1100.0..1125.0)
        assertEquals("N", guidanceNorth.cardinalDirection)
        assertEquals(0.0f, guidanceNorth.bearingDegrees, 1.0f)
        assertTrue(guidanceNorth.formattedDistance.endsWith("km"))

        // From a point to ~109 m East
        val guidanceEast = GisNavigationHelper.calculateRuralGuidance(
            fromLat = -10.880000,
            fromLon = -61.950000,
            toLat = -10.880000,
            toLon = -61.949000
        )
        assertTrue(guidanceEast.distanceMeters in 105.0..115.0)
        assertEquals("L", guidanceEast.cardinalDirection)
        assertEquals(90.0f, guidanceEast.bearingDegrees, 2.0f)
        assertTrue(guidanceEast.formattedDistance.endsWith("m"))
    }
}
