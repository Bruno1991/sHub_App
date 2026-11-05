package com.saile.shub.gis

import android.util.Log
import org.osmdroid.tileprovider.modules.IArchiveFile
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.util.MapTileIndex
import java.io.File
import java.io.InputStream

/**
 * ControlledFallbackArchive.kt
 *
 * Responsabilidade Única:
 * Decorador para IArchiveFile que aplica uma política estrita e limitada de fallback de zoom.
 *
 * Regras da Política:
 * - z17 pode alimentar z18: escala máxima 2x (diferença de 1 nível de zoom).
 * - z16 pode alimentar z18: escala máxima 4x (diferença de 2 níveis de zoom).
 * - z15 NÃO DEVE alimentar z18 (diferença de 3 níveis de zoom é bloqueada).
 * - z10 NUNCA deve alimentar z18 (bloqueia quadriculados esticados em 256x).
 * - Ausência de ladrilho dentro dos limites permitidos resulta estritamente em fundo neutro (null).
 */
class ControlledFallbackArchive(
    private val delegate: IArchiveFile,
    private val maxDownscaleLevels: Int = 2,
    private val enableFallback: Boolean = true
) : IArchiveFile {

    companion object {
        private const val TAG = "ControlledFallback"
    }

    override fun init(pFile: File) {
        delegate.init(pFile)
    }

    override fun getInputStream(pTileSource: ITileSource?, pMapTileIndex: Long): InputStream? {
        // 1. Tenta obter o ladrilho exato na resolução nativa solicitada
        val exactStream = delegate.getInputStream(pTileSource, pMapTileIndex)
        if (exactStream != null) {
            return exactStream
        }

        // 2. Se não encontrou e o fallback controlado estiver desativado, retorna nulo imediatamente
        if (!enableFallback || maxDownscaleLevels <= 0) {
            return null
        }

        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)

        // 3. Fallback estrito: permite no máximo `maxDownscaleLevels` (ex: 2 níveis = escala máx 4x)
        for (diff in 1..maxDownscaleLevels) {
            val parentZoom = zoom - diff
            if (parentZoom < 0) break

            val parentX = x shr diff
            val parentY = y shr diff
            val parentIndex = MapTileIndex.getTileIndex(parentZoom, parentX, parentY)

            val fallbackStream = delegate.getInputStream(pTileSource, parentIndex)
            if (fallbackStream != null) {
                Log.d(TAG, "Fallback controlado permitido: z=$zoom -> z=$parentZoom (escala ${1 shl diff}x) para x=$x, y=$y")
                return fallbackStream
            }
        }

        // 4. Bloqueio estrito: qualquer nível inferior (ex: salto de z10 para z18) é rejeitado
        return null
    }

    override fun close() {
        delegate.close()
    }

    override fun getTileSources(): Set<String> = delegate.tileSources

    override fun setIgnoreTileSource(pIgnoreTileSource: Boolean) {
        delegate.setIgnoreTileSource(pIgnoreTileSource)
    }
}

