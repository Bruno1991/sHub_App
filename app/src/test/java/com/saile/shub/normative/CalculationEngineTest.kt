package com.saile.shub.normative

import com.saile.shub.normative.engine.CalculationEngine
import com.saile.shub.normative.engine.ComplianceStatus
import com.saile.shub.normative.engine.Expression
import com.saile.shub.normative.engine.MathOperator
import com.saile.shub.normative.engine.RuleEngine
import org.junit.Assert.*
import org.junit.Test

class CalculationEngineTest {

    @Test
    fun testTransformerEfficiencyCalculation() {
        val res = CalculationEngine.calculateTransformerEfficiency(
            noLoadLossesW = 100.0,
            totalLossesW = 500.0,
            loadFactor = 1.0,
            ratedPowerKva = 45.0
        )
        assertTrue(res.isSuccess)
        assertTrue(res.value > 98.0 && res.value < 100.0)
    }

    @Test
    fun testDivisionByZeroHandled() {
        val expr = Expression.BinaryOp(
            MathOperator.DIVIDE,
            Expression.Number(100.0),
            Expression.Number(0.0)
        )
        val res = CalculationEngine.evaluate(expr, emptyMap(), "%")
        assertFalse(res.isSuccess)
        assertEquals("Divisão por zero", res.errorMessage)
    }

    @Test
    fun testCandidateRuleEvaluationRefusesFinalDecision() {
        val report = RuleEngine.evaluateRuleStatus(
            ruleStatus = "candidate",
            isCompliant = true,
            evidence = "Evidência de teste",
            docId = "etu_109_1",
            clauseId = "cl_1"
        )
        assertFalse(report.isApprovedExecution)
        assertEquals(ComplianceStatus.PRELIMINARY_CANDIDATE_ONLY, report.status)
    }
}
