package com.saile.shub.normative.engine

import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

enum class MathOperator {
    ADD, SUBTRACT, MULTIPLY, DIVIDE, POWER, SQRT, ROUND
}

sealed class Expression {
    data class Number(val value: Double) : Expression()
    data class Variable(val name: String) : Expression()
    data class BinaryOp(val op: MathOperator, val left: Expression, val right: Expression) : Expression()
    data class UnaryOp(val op: MathOperator, val operand: Expression) : Expression()
}

data class CalculationResult(
    val value: Double,
    val unit: String,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

object CalculationEngine {

    fun evaluate(
        expr: Expression,
        variables: Map<String, Double>,
        outputUnit: String
    ): CalculationResult {
        return try {
            val valResult = evalNode(expr, variables)
            if (valResult.isNaN() || valResult.isInfinite()) {
                CalculationResult(0.0, outputUnit, false, "Resultado matemático inválido (NaN ou Infinito)")
            } else {
                CalculationResult(valResult, outputUnit, true)
            }
        } catch (e: ArithmeticException) {
            CalculationResult(0.0, outputUnit, false, e.message)
        } catch (e: Exception) {
            CalculationResult(0.0, outputUnit, false, "Erro no cálculo: ${e.message}")
        }
    }

    private fun evalNode(expr: Expression, vars: Map<String, Double>): Double {
        return when (expr) {
            is Expression.Number -> expr.value
            is Expression.Variable -> vars[expr.name]
                ?: throw IllegalArgumentException("Variável ausente: ${expr.name}")
            is Expression.BinaryOp -> {
                val l = evalNode(expr.left, vars)
                val r = evalNode(expr.right, vars)
                when (expr.op) {
                    MathOperator.ADD -> l + r
                    MathOperator.SUBTRACT -> l - r
                    MathOperator.MULTIPLY -> l * r
                    MathOperator.DIVIDE -> {
                        if (r == 0.0) throw ArithmeticException("Divisão por zero")
                        l / r
                    }
                    MathOperator.POWER -> l.pow(r)
                    else -> throw IllegalArgumentException("Operador binário inválido: ${expr.op}")
                }
            }
            is Expression.UnaryOp -> {
                val valOp = evalNode(expr.operand, vars)
                when (expr.op) {
                    MathOperator.SQRT -> {
                        if (valOp < 0) throw ArithmeticException("Raiz quadrada de número negativo")
                        sqrt(valOp)
                    }
                    MathOperator.ROUND -> round(valOp)
                    else -> throw IllegalArgumentException("Operador unário inválido: ${expr.op}")
                }
            }
        }
    }

    fun calculateTransformerEfficiency(noLoadLossesW: Double, totalLossesW: Double, loadFactor: Double, ratedPowerKva: Double): CalculationResult {
        if (ratedPowerKva <= 0) return CalculationResult(0.0, "%", false, "Potência nominal deve ser positiva")
        val pKva = ratedPowerKva * 1000.0 // em Watts
        val loadLossesW = totalLossesW - noLoadLossesW
        val actualLossesW = noLoadLossesW + (loadFactor.pow(2) * loadLossesW)
        val outputPowerW = loadFactor * pKva
        val totalInputPowerW = outputPowerW + actualLossesW
        if (totalInputPowerW <= 0) return CalculationResult(0.0, "%", false, "Entrada total deve ser positiva")
        val effPercent = (outputPowerW / totalInputPowerW) * 100.0
        return CalculationResult(effPercent, "%", true)
    }
}
