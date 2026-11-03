package com.saile.shub.gis.usecase

import com.saile.shub.gis.data.dao.GisDao
import com.saile.shub.gis.data.entities.GisFeatureEntity
import com.saile.shub.gis.domain.CoordinateSearchParser
import com.saile.shub.gis.domain.ParsedCoordinate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    suspend fun execute(query: String, limit: Int = 100, offset: Int = 0): GisSearchResult = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return@withContext GisSearchResult(query, null, emptyList())
        }

        // 1. Check for coordinate pattern (Geographic or UTM)
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

        // 2. If it's a coordinate, find nearby assets within ~2 km radius
        val featuresList = if (coord != null) {
            val delta = 0.02 // ~2.2 km
            val nearby = gisDao.findNearestFeatures(
                targetLat = coord.latitude,
                targetLon = coord.longitude,
                minLat = coord.latitude - delta,
                maxLat = coord.latitude + delta,
                minLon = coord.longitude - delta,
                maxLon = coord.longitude + delta,
                limit = 30
            )
            nearby.map { toFeatureItem(it) }
        } else {
            // 3. Search database by code / name / attributes
            val normalizedQuery = normalize(trimmed)
            val dbFeatures = gisDao.searchFeatures(normalizedQuery, limit, offset)
            dbFeatures.map { toFeatureItem(it) }
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
