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

    @Test
    fun overlayTextIncludesAltitudeAndAzimuthWhenPresentAndEnabled() {
        val metadata = PhotoMediaMetadata(
            uri = "content://media/example/3",
            latitude = -11.434,
            longitude = -61.447,
            capturedAt = 1000L,
            address = null,
            userName = "Topografo",
            showUtm = true,
            showGeo = true,
            showDateTime = false,
            showAddress = false,
            showName = true,
            altitude = 250.0,
            accuracy = 4.2f,
            azimuth = 185f,
            showAltitude = true,
            showAzimuth = true,
        )

        val text = metadata.overlayText()

        assertTrue(text.contains("Alt: 250m (±4.2m)"))
        assertTrue(text.contains("AZ: 185° S"))
        assertTrue(text.contains("Nome: Topografo"))
    }

    @Test
    fun overlayTextSuppressesAltitudeAndAzimuthWhenToggledOff() {
        val metadata = PhotoMediaMetadata(
            uri = "content://media/example/4",
            latitude = -11.434,
            longitude = -61.447,
            capturedAt = 1000L,
            address = null,
            userName = "Topografo",
            showUtm = true,
            showGeo = true,
            showDateTime = false,
            showAddress = false,
            showName = true,
            altitude = 250.0,
            accuracy = 4.2f,
            azimuth = 185f,
            showAltitude = false,
            showAzimuth = false,
        )

        val text = metadata.overlayText()

        assertFalse(text.contains("Alt:"))
        assertFalse(text.contains("AZ:"))
    }

    @Test
    fun getCardinalDirectionCoversAllEightSectors() {
        org.junit.Assert.assertEquals("N", PhotoMediaMetadata.getCardinalDirection(0f))
        org.junit.Assert.assertEquals("NE", PhotoMediaMetadata.getCardinalDirection(45f))
        org.junit.Assert.assertEquals("L", PhotoMediaMetadata.getCardinalDirection(90f))
        org.junit.Assert.assertEquals("SE", PhotoMediaMetadata.getCardinalDirection(135f))
        org.junit.Assert.assertEquals("S", PhotoMediaMetadata.getCardinalDirection(180f))
        org.junit.Assert.assertEquals("SO", PhotoMediaMetadata.getCardinalDirection(225f))
        org.junit.Assert.assertEquals("O", PhotoMediaMetadata.getCardinalDirection(270f))
        org.junit.Assert.assertEquals("NO", PhotoMediaMetadata.getCardinalDirection(315f))
        org.junit.Assert.assertEquals("N", PhotoMediaMetadata.getCardinalDirection(359f))
    }
}
