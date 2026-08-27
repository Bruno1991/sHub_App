package com.saile.shub.normative.engine

enum class ComplianceStatus {
    COMPLIANT,
    NON_COMPLIANT,
    PRELIMINARY_CANDIDATE_ONLY,
    INSUFFICIENT_DATA,
    NO_APPLICABLE_RULE,
    REQUIRES_ENGINEERING_REVIEW,
    SOURCE_NOT_EFFECTIVE
}

data class EvaluationReport(
    val status: ComplianceStatus,
    val isApprovedExecution: Boolean,
    val summary: String,
    val evidenceText: String?,
    val sourceDocumentId: String?,
    val sourceClauseId: String?
)

object RuleEngine {

    fun evaluateRuleStatus(
        ruleStatus: String,
        isCompliant: Boolean,
        evidence: String,
        docId: String,
        clauseId: String?
    ): EvaluationReport {
        // Regra Inegociável #10 & #11: Registros candidatos NÃO podem produzir aprovação/reprovação automatizada final.
        if (ruleStatus != "engineering_approved") {
            return EvaluationReport(
                status = ComplianceStatus.PRELIMINARY_CANDIDATE_ONLY,
                isApprovedExecution = false,
                summary = "Regra em estado CANDIDATA ('$ruleStatus'). Decisão automatizada não permitida sem revisão técnica aprovada.",
                evidenceText = evidence,
                sourceDocumentId = docId,
                sourceClauseId = clauseId
            )
        }

        val finalStatus = if (isCompliant) ComplianceStatus.COMPLIANT else ComplianceStatus.NON_COMPLIANT
        return EvaluationReport(
            status = finalStatus,
            isApprovedExecution = true,
            summary = if (isCompliant) "Conforme com os requisitos normativos." else "Não conforme com os requisitos normativos.",
            evidenceText = evidence,
            sourceDocumentId = docId,
            sourceClauseId = clauseId
        )
    }
}
