package com.saile.shub.gis

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.osmdroid.tileprovider.modules.IArchiveFile
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.util.MapTileIndex
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

class ControlledFallbackTest {

    private class MockArchive(
        private val availableTiles: Map<Triple<Int, Int, Int>, ByteArray>
    ) : IArchiveFile {
        override fun init(pFile: File) {}
        override fun getInputStream(pTileSource: ITileSource?, pMapTileIndex: Long): InputStream? {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            val bytes = availableTiles[Triple(z, x, y)] ?: return null
            return ByteArrayInputStream(bytes)
        }
        override fun close() {}
        override fun getTileSources(): Set<String> = emptySet()
        override fun setIgnoreTileSource(pIgnoreTileSource: Boolean) {}
    }

    @Test
    fun testExactTileHit() {
        val mockData = mapOf(
            Triple(18, 100, 200) to byteArrayOf(1, 2, 3)
        )
        val fallback = ControlledFallbackArchive(
            delegate = MockArchive(mockData),
            maxDownscaleLevels = 2,
            enableFallback = true
        )

        val stream = fallback.getInputStream(null, MapTileIndex.getTileIndex(18, 100, 200))
        assertNotNull("Ladrilho nativo z18 deve ser retornado diretamente", stream)
    }

    @Test
    fun testLimitedFallbackWithin2LevelsAllowed() {
        // Tile presente apenas em z17 (diff 1 -> escala 2x)
        // Parent de x=100 em z18 é 100 shr 1 = 50
        // Parent de y=200 em z18 é 200 shr 1 = 100
        val mockData = mapOf(
            Triple(17, 50, 100) to byteArrayOf(4, 5, 6)
        )
        val fallback = ControlledFallbackArchive(
            delegate = MockArchive(mockData),
            maxDownscaleLevels = 2,
            enableFallback = true
        )

        val stream = fallback.getInputStream(null, MapTileIndex.getTileIndex(18, 100, 200))
        assertNotNull("Fallback controlado de z18 -> z17 (escala 2x) deve ser permitido", stream)
    }

    @Test
    fun testRejectionBeyond2LevelsBlocksZ10ToZ18() {
        // Tile presente APENAS em z10 (diff 8)
        // z10 nunca deve alimentar z18
        val parentZ10X = 100 shr 8
        val parentZ10Y = 200 shr 8
        val mockData = mapOf(
            Triple(10, parentZ10X, parentZ10Y) to byteArrayOf(7, 8, 9)
        )
        val fallback = ControlledFallbackArchive(
            delegate = MockArchive(mockData),
            maxDownscaleLevels = 2,
            enableFallback = true
        )

        val stream = fallback.getInputStream(null, MapTileIndex.getTileIndex(18, 100, 200))
        assertNull("Fallback de z18 -> z10 (escala 256x) DEVE ser estritamente bloqueado (retornar null)", stream)
    }
}

