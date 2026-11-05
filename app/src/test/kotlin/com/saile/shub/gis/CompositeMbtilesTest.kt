package com.saile.shub.gis

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.osmdroid.tileprovider.modules.IArchiveFile
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.util.MapTileIndex
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

class CompositeMbtilesTest {

    private class PriorityMockArchive(
        val fileId: String,
        private val tiles: Map<Triple<Int, Int, Int>, ByteArray>
    ) : IArchiveFile {
        override fun init(pFile: File) {}
        override fun getInputStream(pTileSource: ITileSource?, pMapTileIndex: Long): InputStream? {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            val bytes = tiles[Triple(z, x, y)] ?: return null
            return ByteArrayInputStream(bytes)
        }
        override fun close() {}
        override fun getTileSources(): Set<String> = emptySet()
        override fun setIgnoreTileSource(pIgnoreTileSource: Boolean) {}
        override fun toString(): String = fileId
    }

    @Test
    fun testCascadePriorityPrecedence() {
        // High priority: Corredores elétricos
        val corridorsBytes = byteArrayOf(10, 20, 30)
        val corridorsArchive = PriorityMockArchive(
            "corridors_hd",
            mapOf(Triple(16, 500, 600) to corridorsBytes)
        )

        // Low priority: Base mundial
        val worldBytes = byteArrayOf(99, 99, 99)
        val worldArchive = PriorityMockArchive(
            "world_base",
            mapOf(
                Triple(16, 500, 600) to worldBytes, // Conflito proposital: mesma coordenada
                Triple(4, 10, 12) to worldBytes
            )
        )

        val composite = CompositeMbtilesArchive(listOf(corridorsArchive, worldArchive))

        // 1. Em caso de conflito, a prioridade MAIS ALTA deve vencer
        val conflictStream = composite.getInputStream(null, MapTileIndex.getTileIndex(16, 500, 600))
        assertNotNull(conflictStream)
        val resultBytes = conflictStream!!.readBytes()
        assertArrayEquals("O pacote de corredores HD deve ter precedência sobre a base mundial", corridorsBytes, resultBytes)

        // 2. Coordenada exclusiva da base mundial deve ser atendida normalmente
        val worldOnlyStream = composite.getInputStream(null, MapTileIndex.getTileIndex(4, 10, 12))
        assertNotNull(worldOnlyStream)
        assertArrayEquals(worldBytes, worldOnlyStream!!.readBytes())
    }
}
