package com.saile.shub.gis.usecase

import android.content.Context
import com.saile.shub.gis.data.dao.GisDao
import com.saile.shub.gis.data.entities.GisFeatureEntity
import com.saile.shub.gis.domain.CoordinateSearchParser
import com.saile.shub.gis.domain.GisClientRecord
import com.saile.shub.gis.domain.GisClientsHelper
import com.saile.shub.gis.domain.GpkgReader
import com.saile.shub.gis.domain.GpkgReaderProvider
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

    data class ClientItem(
        val client: GisClientRecord,
        val title: String,
        val subtitle: String,
        val latitude: Double?,
        val longitude: Double?
    ) : SearchItem()
}

data class GisSearchResult(
    val query: String,
    val coordinateMatch: SearchItem.CoordinateItem?,
    val features: List<SearchItem.FeatureItem>,
    val clients: List<SearchItem.ClientItem> = emptyList(),
    val coordinateMatches: List<SearchItem.CoordinateItem> = listOfNotNull(coordinateMatch)
)

class SearchFeatureUseCase(private val gisDao: GisDao) {

    companion object {
        fun getReader(path: String): GpkgReader? {
            return GpkgReaderProvider.getOrOpen(path)
        }

        fun closeCachedReader() {
            GpkgReaderProvider.closeAll()
        }
    }

    suspend fun execute(
        query: String,
        gpkgPath: String? = null,
        context: Context? = null,
        limit: Int = 100,
        offset: Int = 0
    ): GisSearchResult = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return@withContext GisSearchResult(query, null, emptyList(), emptyList())
        }

        // 1. Check for coordinate pattern (Geographic Lat/Lon, DMS, or UTM Zone 20S/21S/etc.)
        val coords = CoordinateSearchParser.parseAll(trimmed)
        val coordItems = coords.map {
            SearchItem.CoordinateItem(
                latitude = it.latitude,
                longitude = it.longitude,
                title = it.title,
                subtitle = it.description,
                isUtm = it.isUtm
            )
        }
        val coordItem = coordItems.firstOrNull()
        val coord = coords.firstOrNull()

        val activePackage = runCatching { gisDao.getActivePackage() }.getOrNull()
        val path = gpkgPath ?: activePackage?.fileUri
        val reader = path?.let { getReader(it) }

        val featuresList = if (coord != null) {
            // 2. If it's a coordinate, find nearby assets within ~2 km radius
            val gpkgNearby = reader?.findNearbyFeatures(
                targetLat = coord.latitude,
                targetLon = coord.longitude,
                packageId = activePackage?.id ?: 1L,
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

        // 4. Search Clients database (UC/CDC, Medidor, Nome do Cliente or nearby coordinates)
        val clientsList = if (context != null) {
            val rawClients = if (coord != null) {
                GisClientsHelper.findNearbyClients(
                    context = context,
                    targetLat = coord.latitude,
                    targetLon = coord.longitude,
                    radiusMeters = 500.0,
                    limit = 20
                )
            } else {
                GisClientsHelper.searchClients(
                    context = context,
                    query = trimmed,
                    limit = 40
                )
            }
            rawClients.map { toClientItem(it) }
        } else {
            emptyList()
        }

        GisSearchResult(
            query = query,
            coordinateMatch = coordItem,
            features = featuresList,
            clients = clientsList,
            coordinateMatches = coordItems
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

    private fun toClientItem(client: GisClientRecord): SearchItem.ClientItem {
        val ucPart = if (client.cdc.isNotBlank()) "UC: ${client.cdc}" else "Sem UC"
        val medPart = if (client.medidor.isNotBlank()) " • Med: ${client.medidor}" else ""
        val title = "${client.cliente.ifBlank { "Cliente" }} ($ucPart$medPart)"
        val rotaInfo = listOfNotNull(
            client.livro.takeIf { it.isNotBlank() }?.let { "Liv: $it" },
            client.rota.takeIf { it.isNotBlank() }?.let { "Rota: $it" },
            client.conta.takeIf { it.isNotBlank() }?.let { "Cta: $it" }
        ).joinToString(" | ")
        val coordStr = if (client.latitude != null && client.longitude != null) {
            " • %.5f, %.5f".format(Locale.US, client.latitude, client.longitude)
        } else " • Sem coordenada GPS"
        val subtitle = listOf(rotaInfo, coordStr.removePrefix(" • ")).filter { it.isNotBlank() }.joinToString(" • ")

        return SearchItem.ClientItem(
            client = client,
            title = title,
            subtitle = subtitle,
            latitude = client.latitude,
            longitude = client.longitude
        )
    }

    private fun normalize(text: String): String {
        val noAccents = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noAccents.lowercase()
            .replace(Regex("[^a-z0-9]"), "")
    }
}
