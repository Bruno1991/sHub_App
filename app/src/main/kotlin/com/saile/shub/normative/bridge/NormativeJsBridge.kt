package com.saile.shub.normative.bridge

import android.webkit.JavascriptInterface
import com.saile.shub.normative.engine.CalculationEngine
import com.saile.shub.normative.repository.NormativeRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

class NormativeJsBridge(private val repository: NormativeRepository) {

    @JavascriptInterface
    fun searchNorms(query: String, limit: Int): String {
        return try {
            val results = runBlocking { repository.searchNorms(query, limit) }
            val jsonArray = JSONArray()
            results.forEach { clause ->
                val item = JSONObject().apply {
                    put("id", clause.id)
                    put("documentId", clause.documentId)
                    put("pageNumber", clause.pageNumber)
                    put("clauseNumber", clause.clauseNumber ?: "")
                    put("rawText", clause.rawText)
                    put("sectionPath", clause.sectionPath ?: "")
                }
                jsonArray.put(item)
            }
            JSONObject().apply {
                put("status", "SUCCESS")
                put("query", query)
                put("count", results.size)
                put("data", jsonArray)
            }.toString()
        } catch (e: Exception) {
            JSONObject().apply {
                put("status", "ERROR")
                put("message", e.message ?: "Erro desconhecido")
            }.toString()
        }
    }

    @JavascriptInterface
    fun getClause(clauseId: String): String {
        return try {
            val clause = runBlocking { repository.getClauseById(clauseId) }
            if (clause != null) {
                JSONObject().apply {
                    put("status", "SUCCESS")
                    put("data", JSONObject().apply {
                        put("id", clause.id)
                        put("documentId", clause.documentId)
                        put("pageNumber", clause.pageNumber)
                        put("clauseNumber", clause.clauseNumber ?: "")
                        put("rawText", clause.rawText)
                        put("deonticType", clause.deonticType ?: "")
                        put("sectionPath", clause.sectionPath ?: "")
                    })
                }.toString()
            } else {
                JSONObject().apply {
                    put("status", "NOT_FOUND")
                    put("message", "Cláusula não encontrada")
                }.toString()
            }
        } catch (e: Exception) {
            JSONObject().apply {
                put("status", "ERROR")
                put("message", e.message ?: "Erro desconhecido")
            }.toString()
        }
    }

    @JavascriptInterface
    fun calculateTransformerEfficiency(noLoadLossesW: Double, totalLossesW: Double, loadFactor: Double, ratedPowerKva: Double): String {
        val result = CalculationEngine.calculateTransformerEfficiency(noLoadLossesW, totalLossesW, loadFactor, ratedPowerKva)
        return JSONObject().apply {
            put("status", if (result.isSuccess) "SUCCESS" else "CALCULATION_ERROR")
            put("efficiencyPercentage", result.value)
            put("unit", result.unit)
            put("message", result.errorMessage ?: "")
        }.toString()
    }

    @JavascriptInterface
    fun findFuseReference(powerKva: Double, voltageKv: Double): String {
        return try {
            val fuse = runBlocking { repository.getFuseSelection(powerKva, voltageKv) }
            if (fuse != null) {
                JSONObject().apply {
                    put("status", "SUCCESS")
                    put("powerKva", fuse.transformerPowerKva)
                    put("voltageKv", fuse.primaryVoltageKv)
                    put("recommendedFuseType", fuse.recommendedFuseType)
                    put("recommendedFuseCurrentA", fuse.recommendedFuseCurrentA)
                    put("notes", fuse.notes ?: "")
                }.toString()
            } else {
                JSONObject().apply {
                    put("status", "NOT_FOUND")
                    put("message", "Nenhuma regra de fusível encontrada para a potência e tensão informadas")
                }.toString()
            }
        } catch (e: Exception) {
            JSONObject().apply {
                put("status", "ERROR")
                put("message", e.message ?: "Erro desconhecido")
            }.toString()
        }
    }
}
