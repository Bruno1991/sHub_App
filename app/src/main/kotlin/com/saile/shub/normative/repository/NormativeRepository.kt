package com.saile.shub.normative.repository

import com.saile.shub.normative.database.dao.NormativeDao
import com.saile.shub.normative.database.entities.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NormativeRepository(private val dao: NormativeDao) {

    suspend fun getAllDocuments(): List<SourceDocumentEntity> = withContext(Dispatchers.IO) {
        dao.getAllDocuments()
    }

    suspend fun getDocumentById(id: String): SourceDocumentEntity? = withContext(Dispatchers.IO) {
        dao.getDocumentById(id)
    }

    suspend fun getClauseById(clauseId: String): ClauseEntity? = withContext(Dispatchers.IO) {
        dao.getClauseById(clauseId)
    }

    suspend fun searchNorms(query: String, limit: Int = 50): List<ClauseEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank()) emptyList()
        else dao.searchClausesFts(query.trim(), limit)
    }

    suspend fun getTransformerPerformance(phaseSystem: String, powerKva: Double): TransformerPerformanceEntity? = withContext(Dispatchers.IO) {
        dao.findTransformerLimit(phaseSystem, powerKva)
    }

    suspend fun getPbeTransformers(phaseSystem: String, powerKva: Double): List<TransformerPbeNewEntity> = withContext(Dispatchers.IO) {
        dao.findPbeNewTransformers(phaseSystem, powerKva)
    }

    suspend fun getFuseSelection(powerKva: Double, voltageKv: Double): FuseSelectionEntity? = withContext(Dispatchers.IO) {
        dao.findFuseSelection(powerKva, voltageKv)
    }

    suspend fun getProdistQuality(indicatorName: String): ProdistQualityEntity? = withContext(Dispatchers.IO) {
        dao.getProdistIndicator(indicatorName)
    }
}
