package com.saile.shub.converter

import androidx.lifecycle.ViewModel
import com.saile.shub.core.geo.GeoCoordinate
import com.saile.shub.core.geo.Hemisphere
import com.saile.shub.core.geo.UtmConverter
import com.saile.shub.core.geo.UtmCoordinate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ConversionMode { GEO_TO_UTM, UTM_TO_GEO }

data class ConverterUiState(
    val mode: ConversionMode = ConversionMode.GEO_TO_UTM,
    val latitude: String = "-22.951761169561568",
    val longitude: String = "-43.21047133691749",
    val zone: String = "23",
    val targetZone: String = "",
    val hemisphere: Hemisphere = Hemisphere.SOUTH,
    val easting: String = "683479.47",
    val northing: String = "7460703.39",
    val geoResult: GeoCoordinate? = null,
    val utmResult: UtmCoordinate? = null,
    val error: String? = null,
)

class ConverterViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(ConverterUiState())
    val state = mutableState.asStateFlow()

    fun changeMode(mode: ConversionMode) = mutableState.update { it.copy(mode = mode, error = null) }
    fun updateLatitude(value: String) = mutableState.update { it.copy(latitude = value) }
    fun updateLongitude(value: String) = mutableState.update { it.copy(longitude = value) }
    fun updateZone(value: String) = mutableState.update { it.copy(zone = value) }
    fun updateTargetZone(value: String) = mutableState.update { it.copy(targetZone = value) }
    fun updateEasting(value: String) = mutableState.update { it.copy(easting = value) }
    fun updateNorthing(value: String) = mutableState.update { it.copy(northing = value) }
    fun updateHemisphere(value: Hemisphere) = mutableState.update { it.copy(hemisphere = value) }

    fun convert() {
        val current = mutableState.value
        runCatching {
            val forcedTargetZone = current.targetZone.trim().takeIf { it.isNotEmpty() }?.let {
                it.toIntOrNull()
                    ?: throw IllegalArgumentException("Zona UTM de destino deve ser um número entre 1 e 60.")
            }

            when (current.mode) {
                ConversionMode.GEO_TO_UTM -> {
                    val geo = GeoCoordinate(current.latitude.toCoordinate(), current.longitude.toCoordinate())
                    geo to UtmConverter.geoToUtm(geo, forcedZone = forcedTargetZone)
                }
                ConversionMode.UTM_TO_GEO -> {
                    val sourceUtm = UtmCoordinate(
                        zone = current.zone.trim().toIntOrNull()
                            ?: throw IllegalArgumentException("Zona UTM deve ser um número entre 1 e 60."),
                        hemisphere = current.hemisphere,
                        easting = current.easting.toCoordinate(),
                        northing = current.northing.toCoordinate(),
                    )
                    val geo = UtmConverter.utmToGeo(sourceUtm)
                    val resultUtm = if (forcedTargetZone != null) {
                        UtmConverter.convertUtmZone(sourceUtm, forcedTargetZone)
                    } else {
                        sourceUtm
                    }
                    geo to resultUtm
                }
            }
        }.onSuccess { (geo, utm) ->
            mutableState.update { it.copy(geoResult = geo, utmResult = utm, error = null) }
        }.onFailure { error ->
            mutableState.update {
                it.copy(error = error.message ?: "Não foi possível converter as coordenadas.")
            }
        }
    }

    private fun String.toCoordinate(): Double = trim().replace(',', '.').toDoubleOrNull()
        ?: throw IllegalArgumentException("Informe uma coordenada numérica válida.")
}
