package com.saile.shub.gis.usecase

import android.content.Context
import android.net.Uri
import com.saile.shub.gis.data.dao.GisDao
import com.saile.shub.gis.data.entities.GisFeatureEntity
import com.saile.shub.gis.data.entities.ImportedPackageEntity
import com.saile.shub.gis.domain.GpkgReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.text.Normalizer
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
    fun execute(uri: Uri, displayName: String): Flow<ImportProgress> = flow {
        emit(ImportProgress.CopyingFile)
        
        // 1. Copy file to internal storage
        val gpkgDir = File(context.filesDir, "gpkg")
        if (!gpkgDir.exists()) gpkgDir.mkdirs()
        
        val fileName = "${System.currentTimeMillis()}_${displayName.replace(Regex("[^a-zA-Z0-9.-]"), "_")}"
        val destFile = File(gpkgDir, fileName)
        
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw Exception("Não foi possível abrir o arquivo selecionado.")
        
        emit(ImportProgress.Indexing(0))
        
        // 2. Insert package record
        val pkgEntity = ImportedPackageEntity(
            displayName = displayName,
            fileUri = destFile.absolutePath,
            importedAt = Instant.now().toString(),
            status = "importing"
        )
        val packageId = gisDao.insertPackage(pkgEntity)
        
        // 3. Read GPKG and index
        var totalFeatures = 0
        GpkgReader(destFile.absolutePath).use { reader ->
            val layers = reader.getLayers()
            for (layer in layers) {
                reader.readFeatures(layer) { batch ->
                    val entities = batch.map { f ->
                        val rawCode = extractCodeFromMetadata(f.metadataJson) ?: f.featureSourceId
                        val normCode = rawCode?.let { normalize(it) }
                        
                        GisFeatureEntity(
                            packageId = packageId,
                            sourceLayer = f.sourceLayer,
                            featureSourceId = f.featureSourceId,
                            codeRaw = rawCode,
                            codeNorm = normCode,
                            geometryType = f.geometryType,
                            centroidLat = f.bbox?.let { (it.minLat + it.maxLat) / 2 },
                            centroidLon = f.bbox?.let { (it.minLon + it.maxLon) / 2 },
                            minLat = f.bbox?.minLat,
                            minLon = f.bbox?.minLon,
                            maxLat = f.bbox?.maxLat,
                            maxLon = f.bbox?.maxLon,
                            metadataJson = f.metadataJson
                        )
                    }
                    gisDao.insertFeatures(entities)
                    totalFeatures += entities.size
                    emit(ImportProgress.Indexing(totalFeatures))
                }
            }
        }
        
        gisDao.updatePackageStatus(packageId, "ready", totalFeatures)
        emit(ImportProgress.Completed(packageId, totalFeatures))
        
    }.flowOn(Dispatchers.IO)

    private fun normalize(text: String): String {
        val noAccents = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noAccents.lowercase()
            .replace(Regex("[^a-z0-9]"), "")
    }

    private fun extractCodeFromMetadata(json: String): String? {
        return try {
            val obj = org.json.JSONObject(json)
            val codeKeys = listOf("codigo", "code", "id", "nome", "name", "identificador")
            val keys = obj.keys()
            
            var bestValue: String? = null
            while (keys.hasNext()) {
                val k = keys.next()
                if (codeKeys.any { k.lowercase().contains(it) }) {
                    bestValue = obj.optString(k)
                    break
                }
            }
            bestValue?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
}
