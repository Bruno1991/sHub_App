package com.saile.shub.gis.domain

import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe provider and lifecycle manager for [GpkgReader] instances.
 * Caches active readers by canonical file path, ensuring single-instance access
 * across background queries, searches, and dataset loading.
 */
object GpkgReaderProvider {
    private val readers = ConcurrentHashMap<String, GpkgReader>()

    @Synchronized
    fun getOrOpen(path: String): GpkgReader? {
        val file = File(path)
        if (!file.exists() || !file.canRead() || file.length() == 0L) {
            Log.w("ShubGis", "GpkgReaderProvider: file at '$path' is not accessible (exists=${file.exists()}, length=${file.length()})")
            return null
        }
        val canonical = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        readers[canonical]?.let { return it }

        return try {
            val reader = GpkgReader(canonical)
            readers[canonical] = reader
            Log.i("ShubGis", "GpkgReaderProvider: opened GpkgReader for '$canonical' (${file.length()} bytes)")
            reader
        } catch (e: Exception) {
            Log.e("ShubGis", "GpkgReaderProvider: failed to open GpkgReader for '$canonical': ${e.message}", e)
            null
        }
    }

    @Synchronized
    fun close(path: String) {
        val file = File(path)
        val canonical = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        readers.remove(canonical)?.let {
            runCatching { it.close() }
            Log.i("ShubGis", "GpkgReaderProvider: closed GpkgReader for '$canonical'")
        }
    }

    @Synchronized
    fun closeAll() {
        for ((path, reader) in readers) {
            runCatching { reader.close() }
            Log.i("ShubGis", "GpkgReaderProvider: closed reader for '$path'")
        }
        readers.clear()
    }
}
