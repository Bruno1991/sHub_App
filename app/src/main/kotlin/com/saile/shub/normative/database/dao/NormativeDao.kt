package com.saile.shub.normative.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.saile.shub.normative.database.entities.*

@Dao
interface NormativeDao {

    @Query("SELECT * FROM source_documents")
    suspend fun getAllDocuments(): List<SourceDocumentEntity>

    @Query("SELECT * FROM source_documents WHERE id = :docId LIMIT 1")
    suspend fun getDocumentById(docId: String): SourceDocumentEntity?

    @Query("SELECT * FROM clauses WHERE id = :clauseId LIMIT 1")
    suspend fun getClauseById(clauseId: String): ClauseEntity?

    @Query("SELECT * FROM clauses WHERE document_id = :docId ORDER BY order_in_document ASC LIMIT :limit OFFSET :offset")
    suspend fun getClausesByDocument(docId: String, limit: Int = 100, offset: Int = 0): List<ClauseEntity>

    @Query("""
        SELECT c.* FROM clauses c
        JOIN normative_fts fts ON c.id = fts.clause_id
        WHERE normative_fts MATCH :query
        LIMIT :limit OFFSET :offset
    """)
    suspend fun searchClausesFts(query: String, limit: Int = 50, offset: Int = 0): List<ClauseEntity>

    @Query("""
        SELECT * FROM transformer_performance_limits
        WHERE phase_system = :phaseSystem AND rated_power_kva = :ratedPowerKva
        LIMIT 1
    """)
    suspend fun findTransformerLimit(phaseSystem: String, ratedPowerKva: Double): TransformerPerformanceEntity?

    @Query("""
        SELECT * FROM transformer_pbe_new
        WHERE phase_system = :phaseSystem AND rated_power_kva = :ratedPowerKva
        ORDER BY energy_efficiency_class ASC
    """)
    suspend fun findPbeNewTransformers(phaseSystem: String, ratedPowerKva: Double): List<TransformerPbeNewEntity>

    @Query("""
        SELECT * FROM fuse_selection_rules
        WHERE transformer_power_kva = :powerKva AND primary_voltage_kv = :voltageKv
        LIMIT 1
    """)
    suspend fun findFuseSelection(powerKva: Double, voltageKv: Double): FuseSelectionEntity?

    @Query("SELECT * FROM prodist_quality_indicators WHERE indicator_name = :indicatorName LIMIT 1")
    suspend fun getProdistIndicator(indicatorName: String): ProdistQualityEntity?
}
