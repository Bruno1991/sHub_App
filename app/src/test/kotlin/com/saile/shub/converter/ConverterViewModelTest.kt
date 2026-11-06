package com.saile.shub.converter

import com.saile.shub.core.geo.Hemisphere
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConverterViewModelTest {

    @Test
    fun `default GEO_TO_UTM conversion succeeds`() {
        val viewModel = ConverterViewModel()
        viewModel.convert()

        val state = viewModel.state.value
        assertNull(state.error)
        assertNotNull(state.geoResult)
        assertNotNull(state.utmResult)
        assertEquals(23, state.utmResult?.zone)
        assertEquals(Hemisphere.SOUTH, state.utmResult?.hemisphere)
    }

    @Test
    fun `supports comma decimal separator and forced target UTM zone`() {
        val viewModel = ConverterViewModel()
        viewModel.updateLatitude("-12,150000")
        viewModel.updateLongitude("-60,050000")
        viewModel.updateTargetZone("21")
        viewModel.convert()

        val state = viewModel.state.value
        assertNull(state.error)
        assertEquals(21, state.utmResult?.zone)
    }

    @Test
    fun `supports UTM_TO_GEO mode and inter-zone target conversion`() {
        val viewModel = ConverterViewModel()
        viewModel.changeMode(ConversionMode.UTM_TO_GEO)
        viewModel.updateZone("20")
        viewModel.updateHemisphere(Hemisphere.SOUTH)
        viewModel.updateEasting("821000.0")
        viewModel.updateNorthing("8655000.0")
        viewModel.updateTargetZone("21")
        viewModel.convert()

        val state = viewModel.state.value
        assertNull(state.error)
        assertNotNull(state.geoResult)
        assertEquals(21, state.utmResult?.zone)
    }

    @Test
    fun `surfaces clear error message on invalid input`() {
        val viewModel = ConverterViewModel()
        viewModel.updateLatitude("invalid_number")
        viewModel.convert()

        val state = viewModel.state.value
        assertNotNull(state.error)
        assertTrue(state.error!!.contains("coordenada numérica válida"))
    }
}

