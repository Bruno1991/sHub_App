package com.saile.shub.gis.usecase

import com.saile.shub.gis.data.dao.GisDao
import com.saile.shub.gis.data.entities.GisFeatureEntity
import com.saile.shub.gis.domain.CoordinateSearchParser
import com.saile.shub.gis.domain.GpkgReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer
import java.util.Locale

sealed class SearchItem {
    data class CoordinateItem(
        val latitude: Double,
        val longitude: Double,
        val title: String,
        val subtitle: String,
        val isUtm: Boolean
    ) : SearchItem()

    data class FeatureItem(
        val entity: GisFeatureEntity,
        val title: String,
        val subtitle: String,
        val latitude: Double?,
        val longitude: Double?
    ) : SearchItem()
}

data class GisSearchResult(
    val query: String,
    val coordinateMatch: SearchItem.CoordinateItem?,
    val features: List<SearchItem.FeatureItem>
)

class SearchFeatureUseCase(private val gisDao: GisDao) {

    companion object {
        @Volatile
        private var cachedReader: GpkgReader? = null
        private var cachedPath: String? = null
        private val lock = Any()

        fun getReader(path: String): GpkgReader? {
            synchronized(lock) {
                if (cachedPath == path && cachedReader != null) {
                    return cachedReader
                }
                val file = File(path)
                if (!file.exists() || !file.canRead()) return null

                cachedReader?.close()
                return try {
                    val reader = GpkgReader(path)
                    cachedReader = reader
                    cachedPath = path
                    reader
                } catch (_: Exception) {
                    null
                }
            }
        }

        fun closeCachedReader() {
            synchronized(lock) {
                cachedReader?.close()
                cachedReader = null
                cachedPath = null
            }
        }
    }

    suspend fun execute(query: String, limit: Int = 100, offset: Int = 0): GisSearchResult = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return@withContext GisSearchResult(query, null, emptyList())
        }

        // 1. Check for coordinate pattern (Geographic Lat/Lon or UTM Zone 20S/etc)
        val coord = CoordinateSearchParser.parse(trimmed)
        val coordItem = coord?.let {
            SearchItem.CoordinateItem(
                latitude = it.latitude,
                longitude = it.longitude,
                title = it.title,
                subtitle = it.description,
                isUtm = it.isUtm
            )
        }

        val activePackage = runCatching { gisDao.getActivePackage() }.getOrNull()
        val reader = activePackage?.let { getReader(it.fileUri) }

        val featuresList = if (coord != null) {
            // 2. If it's a coordinate, find nearby assets within ~2 km radius
            val gpkgNearby = reader?.findNearbyFeatures(
                targetLat = coord.latitude,
                targetLon = coord.longitude,
                packageId = activePackage.id,
                radiusMeters = 2000.0,
                limit = 30
            ) ?: emptyList()

            if (gpkgNearby.isNotEmpty()) {
                gpkgNearby
            } else {
                val delta = 0.02
                val dbNearby = gisDao.findNearestFeatures(
                    targetLat = coord.latitude,
                    targetLon = coord.longitude,
                    minLat = coord.latitude - delta,
                    maxLat = coord.latitude + delta,
                    minLon = coord.longitude - delta,
                    maxLon = coord.longitude + delta,
                    limit = 30
                )
                dbNearby.map { toFeatureItem(it) }
            }
        } else {
            // 3. Search database by code / name / attributes
            val gpkgFeatures = reader?.searchFeatures(
                query = trimmed,
                packageId = activePackage?.id ?: 1L,
                limit = limit
            ) ?: emptyList()

            if (gpkgFeatures.isNotEmpty()) {
                gpkgFeatures
            } else {
                val normalizedQuery = normalize(trimmed)
                val dbFeatures = gisDao.searchFeatures(normalizedQuery, limit, offset)
                dbFeatures.map { toFeatureItem(it) }
            }
        }

        GisSearchResult(
            query = query,
            coordinateMatch = coordItem,
            features = featuresList
        )
    }

    private fun toFeatureItem(entity: GisFeatureEntity): SearchItem.FeatureItem {
        val title = entity.codeRaw ?: "Elemento sem código"
        val coordStr = if (entity.centroidLat != null && entity.centroidLon != null) {
            " • %.5f, %.5f".format(Locale.US, entity.centroidLat, entity.centroidLon)
        } else ""
        val subtitle = "${entity.sourceLayer}$coordStr"

        return SearchItem.FeatureItem(
            entity = entity,
            title = title,
            subtitle = subtitle,
            latitude = entity.centroidLat,
            longitude = entity.centroidLon
        )
    }

    private fun normalize(text: String): String {
        val noAccents = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noAccents.lowercase()
            .replace(Regex("[^a-z0-9]"), "")
    }
}
