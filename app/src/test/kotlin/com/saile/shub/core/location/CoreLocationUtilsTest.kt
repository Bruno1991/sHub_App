package com.saile.shub.core.location

import com.saile.shub.ui.Destination
import com.saile.shub.ui.HubToolRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLocationUtilsTest {

    @Test
    fun `resolves Brazilian state names and acronyms accurately`() {
        assertEquals("RO", CoreLocationUtils.getStateAcronym("Rondônia"))
        assertEquals("AC", CoreLocationUtils.getStateAcronym("Acre"))
        assertEquals("AM", CoreLocationUtils.getStateAcronym("Amazonas"))
        assertEquals("SP", CoreLocationUtils.getStateAcronym("São Paulo"))
        assertEquals("RO", CoreLocationUtils.getStateAcronym("ro"))
        assertNull(CoreLocationUtils.getStateAcronym(null))
    }

    @Test
    fun `HubToolRegistry registers all core field modules`() {
        val destinations = HubToolRegistry.tools.map { it.destination }
        assertTrue(destinations.contains(Destination.CONVERTER))
        assertTrue(destinations.contains(Destination.PHOTO))
        assertTrue(destinations.contains(Destination.GIS))
        assertEquals(3, HubToolRegistry.tools.size)
    }
}

