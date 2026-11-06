package com.saile.shub.gis

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import android.util.LruCache
import org.osmdroid.tileprovider.modules.IArchiveFile
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.util.MapTileIndex
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/**
 * ControlledFallbackArchive.kt
 *
 * Responsabilidade Única:
 * Decorador para IArchiveFile que aplica fallback controlado de zoom com recorte exato
 * do sub-quadrante geográfico (crop + bilinear scale 2^diff), evitando repetição de ladrilhos
 * inteiros ou buracos sem resolução quando o operador aproxima o zoom além do nível nativo.
 */
class ControlledFallbackArchive(
    private val delegates: List<IArchiveFile>,
    private val maxDownscaleLevels: Int = 2,
    private val enableFallback: Boolean = true
) : IArchiveFile {

    constructor(
        delegate: IArchiveFile,
        maxDownscaleLevels: Int = 2,
        enableFallback: Boolean = true
    ) : this(listOf(delegate), maxDownscaleLevels, enableFallback)

    companion object {
        private const val TAG = "ControlledFallback"
    }

    private val parentBytesCache = object : LruCache<Long, ByteArray>(32) {}

    override fun init(pFile: File) {
        delegates.firstOrNull()?.init(pFile)
    }

    override fun getInputStream(pTileSource: ITileSource?, pMapTileIndex: Long): InputStream? {
        // 1. Tenta obter o ladrilho exato na resolução nativa solicitada em qualquer arquivo delegado
        for (delegate in delegates) {
            val exactStream = delegate.getInputStream(pTileSource, pMapTileIndex)
            if (exactStream != null) {
                return exactStream
            }
        }

        // 2. Se não encontrou e o fallback controlado estiver desativado, retorna nulo imediatamente
        if (!enableFallback || maxDownscaleLevels <= 0) {
            return null
        }

        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)

        // 3. Fallback controlado: busca o ancestral mais próximo dentro de maxDownscaleLevels
        //    e recorta o sub-quadrante exato correspondente à coordenada (zoom, x, y).
        for (diff in 1..maxDownscaleLevels) {
            val parentZoom = zoom - diff
            if (parentZoom < 0) break

            val parentX = x shr diff
            val parentY = y shr diff
            val parentIndex = MapTileIndex.getTileIndex(parentZoom, parentX, parentY)

            var parentBytes = synchronized(parentBytesCache) {
                parentBytesCache.get(parentIndex)
            }
            if (parentBytes == null) {
                for (delegate in delegates) {
                    val stream = delegate.getInputStream(pTileSource, parentIndex)
                    if (stream != null) {
                        val bytes = stream.use { it.readBytes() }
                        if (bytes.isNotEmpty()) {
                            parentBytes = bytes
                            synchronized(parentBytesCache) {
                                parentBytesCache.put(parentIndex, bytes)
                            }
                            break
                        }
                    }
                }
            }

            if (parentBytes != null && parentBytes.isNotEmpty()) {
                val cropped = cropAndScaleQuadrant(parentBytes, x, y, diff)
                Log.d(TAG, "Fallback controlado: z=$zoom -> z=$parentZoom (escala ${1 shl diff}x) para x=$x, y=$y")
                return ByteArrayInputStream(cropped)
            }
        }

        return null
    }

    /**
     * Recorta matematicamente o sub-quadrante (subX, subY) do ladrilho ancestral e o
     * dimensiona suavemente para 256x256 com filtragem bilinear.
     * Em ambiente de teste unitário JVM (onde BitmapFactory retorna null), devolve os bytes originais.
     */
    private fun cropAndScaleQuadrant(parentBytes: ByteArray, childX: Int, childY: Int, diff: Int): ByteArray {
        return try {
            val parentBitmap = BitmapFactory.decodeByteArray(parentBytes, 0, parentBytes.size)
                ?: return parentBytes

            val tileW = parentBitmap.width.coerceAtLeast(1)
            val tileH = parentBitmap.height.coerceAtLeast(1)
            val scale = 1 shl diff
            val mask = scale - 1
            val subX = childX and mask
            val subY = childY and mask

            val srcLeft = (subX * tileW).toFloat() / scale.toFloat()
            val srcTop = (subY * tileH).toFloat() / scale.toFloat()

            val outBitmap = Bitmap.createBitmap(tileW, tileH, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(outBitmap)
                val matrix = Matrix().apply {
                    postTranslate(-srcLeft, -srcTop)
                    postScale(scale.toFloat(), scale.toFloat())
                }
                val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
                canvas.drawBitmap(parentBitmap, matrix, paint)

                val out = ByteArrayOutputStream(16 * 1024)
                outBitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                out.toByteArray()
            } finally {
                outBitmap.recycle()
                parentBitmap.recycle()
            }
        } catch (t: Throwable) {
            parentBytes
        }
    }

    override fun close() {
        synchronized(parentBytesCache) {
            parentBytesCache.evictAll()
        }
        delegates.forEach { it.close() }
    }

    override fun getTileSources(): Set<String> = delegates.flatMap { it.tileSources }.toSet()

    override fun setIgnoreTileSource(pIgnoreTileSource: Boolean) {
        delegates.forEach { it.setIgnoreTileSource(pIgnoreTileSource) }
    }
}
