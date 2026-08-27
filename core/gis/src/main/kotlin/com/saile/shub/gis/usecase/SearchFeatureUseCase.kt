package com.saile.shub.gis.usecase

import com.saile.shub.gis.data.dao.GisDao
import com.saile.shub.gis.data.entities.GisFeatureEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer

class SearchFeatureUseCase(private val gisDao: GisDao) {
    suspend fun execute(query: String, limit: Int = 50, offset: Int = 0): List<GisFeatureEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        
        val normalizedQuery = normalize(query)
        gisDao.searchFeatures(normalizedQuery, limit, offset)
    }
    
    private fun normalize(text: String): String {
        val noAccents = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noAccents.lowercase()
            .replace(Regex("[^a-z0-9]"), "")
    }
}
