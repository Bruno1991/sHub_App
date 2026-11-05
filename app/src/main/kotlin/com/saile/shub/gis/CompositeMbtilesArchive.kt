package com.saile.shub.gis

import android.util.Log
import org.osmdroid.tileprovider.modules.IArchiveFile
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.util.MapTileIndex
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * CompositeMbtilesArchive.kt
 *
 * Responsabilidade Única:
 * Provedor composto de arquivos MBTiles que implementa a interface IArchiveFile do osmdroid,
 * garantindo ordem determinística de precedência em cascata (Corredores HD -> Municipal -> Regional -> Mundial),
 * eliminando a inversão de ordem nativa do MapTileFileArchiveProvider e provendo pooling thread-safe de leitura.
 */
class CompositeMbtilesArchive(
    val archives: List<IArchiveFile>
) : IArchiveFile {

    companion object {
        private const val TAG = "CompositeMbtilesArchive"
    }

    private var ignoreTileSource: Boolean = true
    private val queryCount = AtomicLong(0)
    private val hitCountsPerFile = ConcurrentHashMap<String, AtomicLong>()

    private fun getArchiveName(archive: IArchiveFile): String {
        return if (archive is GisMbtilesHelper.RobustMbtilesArchive) {
            archive.file.name
        } else {
            archive.toString()
        }
    }

    init {
        for (a in archives) {
            a.setIgnoreTileSource(true)
            hitCountsPerFile[getArchiveName(a)] = AtomicLong(0)
        }
    }

    override fun init(pFile: File) {
        // Inicialização individual gerenciada por cada IArchiveFile
    }

    override fun getInputStream(pTileSource: ITileSource?, pMapTileIndex: Long): InputStream? {
        val count = queryCount.incrementAndGet()
        val t0 = System.nanoTime()

        // Consulta estritamente em ordem de prioridade (0 = Maior prioridade até N-1)
        for (archive in archives) {
            val stream = archive.getInputStream(pTileSource, pMapTileIndex)
            if (stream != null) {
                val archiveName = getArchiveName(archive)
                hitCountsPerFile[archiveName]?.incrementAndGet()
                if (count % 30L == 0L) {
                    val durationMs = (System.nanoTime() - t0) / 1_000_000.0
                    val zoom = MapTileIndex.getZoom(pMapTileIndex)
                    val x = MapTileIndex.getX(pMapTileIndex)
                    val y = MapTileIndex.getY(pMapTileIndex)
                    Log.d(TAG, "HIT: z=$zoom x=$x y=$y [$archiveName] (${String.format(java.util.Locale.US, "%.2f", durationMs)} ms)")
                }
                return stream
            }
        }
        return null
    }

    override fun getTileSources(): Set<String> = emptySet()

    override fun setIgnoreTileSource(pIgnoreTileSource: Boolean) {
        this.ignoreTileSource = pIgnoreTileSource
        for (a in archives) {
            a.setIgnoreTileSource(pIgnoreTileSource)
        }
    }

    override fun close() {
        for (archive in archives) {
            runCatching { archive.close() }
        }
        Log.i(TAG, "CompositeMbtilesArchive closed all ${archives.size} underlying archives.")
    }

    fun getMetricsReport(): Map<String, Long> {
        val map = mutableMapOf<String, Long>()
        for ((k, v) in hitCountsPerFile) {
            map[k] = v.get()
        }
        return map
    }
}
