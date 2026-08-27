package com.saile.shub.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogoSizeStandardizerTest {

    private val baseHeight = 100f

    @Test
    fun `reference logo aspect ratio 3_07 matches base height`() {
        val result = calculateStandardizedLogoDimensions(1000f, 325.7f, baseHeight)
        assertEquals(baseHeight, result.height, 1.0f)
        assertEquals(307f, result.width, 3.0f)
    }

    @Test
    fun `square logo height is increased to normalize area`() {
        val result = calculateStandardizedLogoDimensions(200f, 200f, baseHeight)
        assertEquals(baseHeight * 1.35f, result.height, 0.001f)
        assertEquals(baseHeight * 1.35f, result.width, 0.001f)
        // Area is now 1.82 * baseHeight^2 instead of 1.0 * baseHeight^2
        assertTrue(result.width * result.height > baseHeight * baseHeight * 1.5f)
    }

    @Test
    fun `extra wide logo width is capped at maximum allowed boundary`() {
        val result = calculateStandardizedLogoDimensions(400f, 100f, baseHeight)
        assertEquals(baseHeight * 3.25f, result.width, 0.001f)
        assertEquals((baseHeight * 3.25f) / 4.0f, result.height, 0.001f)
    }

    @Test
    fun `compact logo scaling increases both height and width proportionately`() {
        val cpfl = calculateStandardizedLogoDimensions(283.5f, 207.8f, baseHeight)
        val square = calculateStandardizedLogoDimensions(192.8f, 192.8f, baseHeight)

        // Compact logos should have comparable visual area to reference logo
        assertTrue(cpfl.width * cpfl.height > baseHeight * baseHeight * 2.0f)
        assertTrue(square.width * square.height > baseHeight * baseHeight * 1.5f)
    }

    @Test
    fun `handles edge cases gracefully`() {
        val resultZero = calculateStandardizedLogoDimensions(0f, 0f, baseHeight)
        assertEquals(baseHeight, resultZero.height, 0.001f)
        assertEquals(baseHeight, resultZero.width, 0.001f)
    }
}
