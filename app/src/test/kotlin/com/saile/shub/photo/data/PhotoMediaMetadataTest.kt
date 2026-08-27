package com.saile.shub.photo.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class PhotoMediaMetadataTest {
    @Test
    fun overlayTextContainsOnlyEnabledCaptureFields() {
        val capturedAt = Calendar.getInstance(Locale("pt", "BR")).apply {
            set(2026, Calendar.JULY, 21, 11, 30, 45)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val metadata = PhotoMediaMetadata(
            uri = "content://media/example/1",
            latitude = -11.434,
            longitude = -61.447,
            capturedAt = capturedAt,
            address = "Cacoal - RO",
            userName = "Equipe de campo",
            showUtm = true,
            showGeo = true,
            showDateTime = true,
            showAddress = true,
            showName = false,
        )

        val text = metadata.overlayText()

        assertTrue(text.contains("UTM:"))
        assertTrue(text.contains("GEO: -11.4340000, -61.4470000"))
        assertTrue(text.contains("Data e Hora: 21/07/2026 11:30:45"))
        assertTrue(text.contains("Endereço: Cacoal - RO"))
        assertFalse(text.contains("Nome:"))
    }

    @Test
    fun missingCoordinatesRemainExplicit() {
        val metadata = PhotoMediaMetadata(
            uri = "content://media/example/2",
            latitude = null,
            longitude = null,
            capturedAt = 0L,
            address = null,
            userName = "",
            showUtm = true,
            showGeo = true,
            showDateTime = false,
            showAddress = true,
            showName = true,
        )

        val text = metadata.overlayText()

        assertTrue(text.contains("UTM: indisponível"))
        assertTrue(text.contains("GEO: indisponível"))
        assertFalse(text.contains("Endereço:"))
        assertFalse(text.contains("Nome:"))
    }
}
