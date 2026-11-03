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
import org.json.JSONObject
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
                        val (rawCode, searchContent) = extractIdentifiers(f.metadataJson, f.sourceLayer, f.featureSourceId)
                        val normCode = normalize(rawCode)
                        
                        GisFeatureEntity(
                            packageId = packageId,
                            sourceLayer = f.sourceLayer,
                            featureSourceId = f.featureSourceId,
                            codeRaw = rawCode,
                            codeNorm = normCode,
                            searchContent = searchContent,
                            geometryType = f.geometryType,
                            centroidLat = f.bbox?.centroidLat,
                            centroidLon = f.bbox?.centroidLon,
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

    private fun extractIdentifiers(json: String, layerName: String, featureId: String?): Pair<String, String> {
        val priorityKeys = listOf(
            "numero_operativo",
            "name",
            "nome",
            "codigo_do_",
            "codigo",
            "cod_id",
            "sigla_da_s",
            "circuito",
            "circuit_id",
            "circuit_2",
            "circuit_1",
            "id",
            "spec_name",
            "alias",
            "tipo_de_es"
        )

        var primaryCode: String? = null
        val searchTokens = mutableListOf<String>()
        searchTokens.add(layerName)
        if (featureId != null) searchTokens.add(featureId)

        try {
            val obj = JSONObject(json)
            val keys = obj.keys()
            val keyList = mutableListOf<String>()
            while (keys.hasNext()) keyList.add(keys.next())

            // 1. Determine primary code by priority key match
            for (pKey in priorityKeys) {
                val matchingKey = keyList.firstOrNull { 
                    val k = it.lowercase()
                    k == pKey || k.startsWith(pKey) || k.contains(pKey)
                }
                if (matchingKey != null) {
                    val value = obj.optString(matchingKey).trim()
                    if (value.isNotBlank() && value != "null" && value != "None") {
                        primaryCode = value
                        break
                    }
                }
            }

            // 2. Aggregate all attribute values for 100% full-text coverage
            for (k in keyList) {
                val v = obj.optString(k).trim()
                if (v.isNotBlank() && v != "null" && v != "None" && v.length < 120) {
                    searchTokens.add(v)
                }
            }
        } catch (_: Exception) {}

        val rawCode = primaryCode ?: featureId ?: "Elemento"
        val searchContent = searchTokens.joinToString(" ") { normalize(it) }
        return Pair(rawCode, searchContent)
    }
}
