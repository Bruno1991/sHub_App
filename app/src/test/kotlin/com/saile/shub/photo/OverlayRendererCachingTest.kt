package com.saile.shub.photo

import android.graphics.Paint
import android.location.Location
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class OverlayRendererCachingTest {

    private class FakeLocation(
        private val lat: Double,
        private val lon: Double,
        private val alt: Double? = null,
        private val acc: Float? = null,
    ) : Location("test") {
        override fun getLatitude(): Double = lat
        override fun getLongitude(): Double = lon
        override fun hasAltitude(): Boolean = alt != null
        override fun getAltitude(): Double = alt ?: 0.0
        override fun hasAccuracy(): Boolean = acc != null
        override fun getAccuracy(): Float = acc ?: 0f
    }

    private class FakePaint(
        private val charWidth: Float = 10f,
    ) : Paint() {
        override fun measureText(text: String): Float = text.length * charWidth
    }

    @Test
    fun `date caching reuses formatted string for frames in the same second`() {
        val baseTime = 1753198245000L // arbitrary fixed epoch
        val dateFrame1 = Date(baseTime + 10)  // 10ms into second
        val dateFrame2 = Date(baseTime + 200) // 200ms into second
        val dateFrame3 = Date(baseTime + 950) // 950ms into second

        val formatted1 = OverlayRenderer.getCachedDateText(dateFrame1)
        val formatted2 = OverlayRenderer.getCachedDateText(dateFrame2)
        val formatted3 = OverlayRenderer.getCachedDateText(dateFrame3)

        assertEquals(formatted1, formatted2)
        assertEquals(formatted2, formatted3)
        // Verify same string reference reused (zero allocation)
        assertSame(formatted1, formatted2)
        assertSame(formatted2, formatted3)
    }

    @Test
    fun `date caching invalidates and updates when second ticks`() {
        val baseTime = 1753198245000L
        val dateSec1 = Date(baseTime)
        val dateSec2 = Date(baseTime + 1000L) // +1 second

        val formatted1 = OverlayRenderer.getCachedDateText(dateSec1)
        val formatted2 = OverlayRenderer.getCachedDateText(dateSec2)

        assertNotEquals(formatted1, formatted2)
    }

    @Test
    fun `location caching returns indisponivel for null location`() {
        val (utm, geo) = OverlayRenderer.getCachedLocationTexts(null)
        assertEquals("indisponível", utm)
        assertEquals("indisponível", geo)
    }

    @Test
    fun `location caching reuses UTM and Geo strings for identical coordinates`() {
        val loc1 = FakeLocation(-11.434, -61.447)
        val loc2 = FakeLocation(-11.434, -61.447)

        val pair1 = OverlayRenderer.getCachedLocationTexts(loc1)
        val pair2 = OverlayRenderer.getCachedLocationTexts(loc2)

        assertEquals(pair1.first, pair2.first)
        assertEquals(pair1.second, pair2.second)
        // Verify cached string references
        assertSame(pair1.first, pair2.first)
        assertSame(pair1.second, pair2.second)
    }

    @Test
    fun `location caching updates coordinates when position moves`() {
        val loc1 = FakeLocation(-11.434, -61.447)
        val loc2 = FakeLocation(-12.500, -60.100)

        val pair1 = OverlayRenderer.getCachedLocationTexts(loc1)
        val pair2 = OverlayRenderer.getCachedLocationTexts(loc2)

        assertNotEquals(pair1.first, pair2.first)
        assertNotEquals(pair1.second, pair2.second)
    }

    @Test
    fun `wrapText fast path preserves single line when text fits maxWidth`() {
        val paint = FakePaint(charWidth = 5f)
        val text = "sFoto Campo"
        // 11 chars * 5f = 55f <= 1000f
        val lines = OverlayRenderer.wrapText(text, paint, maxWidth = 1000f)

        assertEquals(1, lines.size)
        assertEquals(text, lines[0])
    }

    @Test
    fun `wrapText wraps long lines into multiple lines`() {
        val paint = FakePaint(charWidth = 10f)
        val text = "Primeira Linha Segunda Linha Terceira Linha"
        // Words wrapped when line exceeds 150f
        val lines = OverlayRenderer.wrapText(text, paint, maxWidth = 150f)

        assertTrue(lines.size > 1)
        assertEquals(text, lines.joinToString(" "))
    }

    @Test
    fun `wrapText returns empty list for empty string`() {
        val paint = FakePaint()
        val lines = OverlayRenderer.wrapText("", paint, maxWidth = 500f)

        assertTrue(lines.isEmpty())
    }

    @Test
    fun `azimuth text returns indisponivel for null azimuth`() {
        val text = OverlayRenderer.getCachedAzimuthText(null)
        assertEquals("indisponível", text)
    }

    @Test
    fun `azimuth text formats degree and cardinal sector accurately`() {
        assertEquals("0° N", OverlayRenderer.getCachedAzimuthText(0f))
        assertEquals("45° NE", OverlayRenderer.getCachedAzimuthText(45f))
        assertEquals("90° L", OverlayRenderer.getCachedAzimuthText(90f))
        assertEquals("135° SE", OverlayRenderer.getCachedAzimuthText(135f))
        assertEquals("180° S", OverlayRenderer.getCachedAzimuthText(180f))
        assertEquals("225° SO", OverlayRenderer.getCachedAzimuthText(225f))
        assertEquals("270° O", OverlayRenderer.getCachedAzimuthText(270f))
        assertEquals("315° NO", OverlayRenderer.getCachedAzimuthText(315f))
    }

    @Test
    fun `azimuth caching reuses string reference for same integer angle`() {
        val text1 = OverlayRenderer.getCachedAzimuthText(90.2f)
        val text2 = OverlayRenderer.getCachedAzimuthText(90.4f)
        assertEquals("90° L", text1)
        assertSame(text1, text2)
    }

    @Test
    fun `altitude text formats altitude and accuracy`() {
        val loc = FakeLocation(-11.434, -61.447, alt = 230.4, acc = 3.5f)
        val altText = OverlayRenderer.getCachedAltitudeText(loc)
        assertEquals("Alt: 230m (±3.5m)", altText)
    }

    @Test
    fun `altitude text formats without accuracy if accuracy is absent`() {
        val loc = FakeLocation(-11.434, -61.447, alt = 230.4, acc = null)
        val altText = OverlayRenderer.getCachedAltitudeText(loc)
        assertEquals("Alt: 230m", altText)
    }

    @Test
    fun `altitude text returns indisponivel for null location`() {
        val altText = OverlayRenderer.getCachedAltitudeText(null)
        assertEquals("indisponível", altText)
    }
}
