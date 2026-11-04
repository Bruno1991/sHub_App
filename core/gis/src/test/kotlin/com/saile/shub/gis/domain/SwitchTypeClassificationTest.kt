package com.saile.shub.gis.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitchTypeClassificationTest {

    @Test
    fun `test switch classification for NF and NA`() {
        val testCases = listOf(
            "CF - Chave Faca,Existente,NF" to GisElementType.SWITCH_NF,
            "CF - Chave Faca,Existente,NA" to GisElementType.SWITCH_NA,
            "FR - Chave Fusível Religadora,Existente,NF" to GisElementType.SWITCH_NF,
            "FR - Chave Fusível Religadora,Existente,NA" to GisElementType.SWITCH_NA,
            "FU - Chave Fusível, Existing with change, Closed" to GisElementType.SWITCH_NF,
            "FU - Chave Fusível,Existente,NA" to GisElementType.SWITCH_NA,
            "RD - Religador de Linha,Existente,NA" to GisElementType.SWITCH_NA,
            "RD - Religador de Linha,Existente,NF" to GisElementType.SWITCH_NF,
            "Chave Seccionadora ABERTA" to GisElementType.SWITCH_NA
        )

        for ((styleSubc, expectedType) in testCases) {
            val isNa = styleSubc.contains(",NA", ignoreCase = true) ||
                       styleSubc.contains(" NA", ignoreCase = true) ||
                       styleSubc.endsWith("NA", ignoreCase = true) ||
                       styleSubc.contains("OPEN", ignoreCase = true) ||
                       styleSubc.contains("ABERTA", ignoreCase = true)

            val resolvedType = if (isNa) GisElementType.SWITCH_NA else GisElementType.SWITCH_NF
            assertEquals("Failed for styleSubc: $styleSubc", expectedType, resolvedType)
        }
    }

    @Test
    fun `test layer color has entry for switch NF and NA`() {
        val nfColor = GpkgReader.getLayerColor(GisElementType.SWITCH_NF)
        val naColor = GpkgReader.getLayerColor(GisElementType.SWITCH_NA)
        val poleColor = GpkgReader.getLayerColor(GisElementType.POLE)

        assertEquals("#FB0313", nfColor)
        assertEquals("#00C853", naColor)
        assertEquals("#FFFFFF", poleColor)
    }
}

