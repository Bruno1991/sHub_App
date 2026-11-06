package com.saile.shub.gis.usecase

import android.content.Context
import android.net.Uri
import com.saile.shub.gis.data.dao.GisDao
import com.saile.shub.gis.data.entities.ImportedPackageEntity
import com.saile.shub.gis.domain.GpkgReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant

sealed class ImportProgress {
    object CopyingFile : ImportProgress()
    data class Indexing(val featuresImported: Int) : ImportProgress()
    data class Completed(val packageId: Long, val totalFeatures: Int) : ImportProgress()
}

class ImportGpkgUseCase(
    private val context: Context,
    private val gisDao: GisDao
) {
    fun execute(uri: Uri, displayName: String): Flow<ImportProgress> = channelFlow {
        send(ImportProgress.CopyingFile)

        // Close any active SQLite reader locks before replacing package files
        SearchFeatureUseCase.closeCachedReader()

        // 1. Clean up old packages from Room and disk to guarantee non-accumulative, overwriting storage
        gisDao.deleteAllPackages()
        gisDao.deleteAllFeatures()

        val gpkgDir = File(context.filesDir, "gpkg")
        if (gpkgDir.exists()) {
            gpkgDir.listFiles()?.forEach { oldFile ->
                runCatching { oldFile.delete() }
            }
        } else {
            gpkgDir.mkdirs()
        }

        val safeName = displayName.replace(Regex("[^a-zA-Z0-9.-]"), "_").ifBlank {
            "elementos_${System.currentTimeMillis()}.gpkg"
        }
        val destFile = File(gpkgDir, safeName)
        if (destFile.exists()) {
            runCatching { destFile.delete() }
        }

        val transferredSuccessfully = runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).channel.use { inChannel ->
                    FileOutputStream(destFile).channel.use { outChannel ->
                        var transferred: Long = 0
                        val size = inChannel.size()
                        while (transferred < size) {
                            val count = outChannel.transferFrom(inChannel, transferred, size - transferred)
                            if (count <= 0) break
                            transferred += count
                        }
                        true
                    }
                }
            } ?: false
        }.getOrDefault(false)

        if (!transferredSuccessfully) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output, bufferSize = 128 * 1024)
                }
            } ?: throw Exception("Não foi possível ler o arquivo selecionado.")
        }

        send(ImportProgress.Indexing(0))

        // 2. Perform direct in-place indexing on SQLite GeoPackage
        var totalFeatures = 0
        GpkgReader(destFile.absolutePath).use { reader ->
            totalFeatures = reader.countTotalFeatures()

            reader.createSearchIndexes { _, layerIdx, totalLayers ->
                val progressCount = ((layerIdx.toDouble() / totalLayers) * totalFeatures).toInt()
                trySend(ImportProgress.Indexing(progressCount))
            }
        }

        send(ImportProgress.Indexing(totalFeatures))

        // 3. Register package in Room metadata
        val pkgEntity = ImportedPackageEntity(
            displayName = displayName,
            fileUri = destFile.absolutePath,
            importedAt = Instant.now().toString(),
            status = "ready",
            featureCount = totalFeatures
        )
        val packageId = gisDao.insertPackage(pkgEntity)

        send(ImportProgress.Completed(packageId, totalFeatures))
    }.flowOn(Dispatchers.IO)
}
