package com.saile.shub.normative.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Entity(tableName = "source_documents")
data class SourceDocumentEntity(
    @PrimaryKey val id: String,
    val code: String,
    val title: String,
    val version: String?,
    @ColumnInfo(name = "publication_date") val publicationDate: String?,
    @ColumnInfo(name = "effective_status") val effectiveStatus: String,
    val organization: String,
    val category: String,
    @ColumnInfo(name = "source_filename") val sourceFilename: String,
    @ColumnInfo(name = "source_sha256") val sourceSha256: String,
    @ColumnInfo(name = "source_size_bytes") val sourceSizeBytes: Long,
    @ColumnInfo(name = "page_count") val pageCount: Int,
    @ColumnInfo(name = "extraction_status") val extractionStatus: String?
)

@Entity(tableName = "clauses")
data class ClauseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "document_id") val documentId: String,
    @ColumnInfo(name = "section_id_candidate") val sectionIdCandidate: String?,
    @ColumnInfo(name = "page_number") val pageNumber: Int,
    @ColumnInfo(name = "clause_number") val clauseNumber: String?,
    @ColumnInfo(name = "raw_text") val rawText: String,
    @ColumnInfo(name = "normalized_text") val normalizedText: String,
    @ColumnInfo(name = "order_in_document") val orderInDocument: Int,
    @ColumnInfo(name = "deontic_type") val deonticType: String?,
    @ColumnInfo(name = "section_path") val sectionPath: String?
)

@Entity(tableName = "transformer_performance_limits")
data class TransformerPerformanceEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "document_id") val documentId: String,
    val category: String,
    val condition: String,
    @ColumnInfo(name = "phase_system") val phaseSystem: String,
    @ColumnInfo(name = "rated_power_kva") val ratedPowerKva: Double,
    @ColumnInfo(name = "primary_voltage_kv") val primaryVoltageKv: Double,
    @ColumnInfo(name = "secondary_voltage_v") val secondaryVoltageV: Double,
    @ColumnInfo(name = "max_no_load_losses_w") val maxNoLoadLossesW: Double?,
    @ColumnInfo(name = "max_total_losses_w") val maxTotalLossesW: Double?,
    @ColumnInfo(name = "max_excitation_current_percent") val maxExcitationCurrentPercent: Double?,
    @ColumnInfo(name = "min_impedance_percent") val minImpedancePercent: Double?,
    @ColumnInfo(name = "max_impedance_percent") val maxImpedancePercent: Double?,
    @ColumnInfo(name = "min_efficiency_percent") val minEfficiencyPercent: Double?
)

@Entity(tableName = "transformer_pbe_new")
data class TransformerPbeNewEntity(
    @PrimaryKey val id: String,
    val manufacturer: String,
    @ColumnInfo(name = "model_code") val modelCode: String,
    @ColumnInfo(name = "phase_system") val phaseSystem: String,
    @ColumnInfo(name = "rated_power_kva") val ratedPowerKva: Double,
    @ColumnInfo(name = "energy_efficiency_class") val energyEfficiencyClass: String,
    @ColumnInfo(name = "no_load_losses_w") val noLoadLossesW: Double?,
    @ColumnInfo(name = "total_losses_w") val totalLossesW: Double?,
    @ColumnInfo(name = "efficiency_percentage") val efficiencyPercentage: Double?
)

@Entity(tableName = "fuse_selection_rules")
data class FuseSelectionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "document_id") val documentId: String,
    @ColumnInfo(name = "transformer_power_kva") val transformerPowerKva: Double,
    @ColumnInfo(name = "primary_voltage_kv") val primaryVoltageKv: Double,
    @ColumnInfo(name = "phase_system") val phaseSystem: String,
    @ColumnInfo(name = "recommended_fuse_type") val recommendedFuseType: String,
    @ColumnInfo(name = "recommended_fuse_current_a") val recommendedFuseCurrentA: Double,
    val notes: String?
)

@Entity(tableName = "prodist_quality_indicators")
data class ProdistQualityEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "indicator_name") val indicatorName: String,
    @ColumnInfo(name = "system_type") val systemType: String?,
    @ColumnInfo(name = "limit_value") val limitValue: Double,
    @ColumnInfo(name = "unit_symbol") val unitSymbol: String,
    @ColumnInfo(name = "eval_period") val evalPeriod: String
)

@Fts4
@Entity(tableName = "normative_fts")
data class NormativeFtsEntity(
    @ColumnInfo(name = "clause_id") val clauseId: String,
    @ColumnInfo(name = "document_id") val documentId: String,
    @ColumnInfo(name = "document_code") val documentCode: String,
    @ColumnInfo(name = "document_title") val documentTitle: String,
    @ColumnInfo(name = "section_path") val sectionPath: String,
    @ColumnInfo(name = "clause_number") val clauseNumber: String,
    val content: String
)
