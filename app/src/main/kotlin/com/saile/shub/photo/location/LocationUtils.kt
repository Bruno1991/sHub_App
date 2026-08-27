package com.saile.shub.photo.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import java.util.Locale

object LocationUtils {
    fun lastKnownLocation(context: Context): Location? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstNotNullOfOrNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
    }

    fun getStateAcronym(state: String?): String? {
        if (state == null) return null
        if (state.length <= 2) return state.uppercase(Locale("pt", "BR"))
        val map = mapOf(
            "Acre" to "AC", "Alagoas" to "AL", "Amapá" to "AP", "Amazonas" to "AM",
            "Bahia" to "BA", "Ceará" to "CE", "Distrito Federal" to "DF", "Espírito Santo" to "ES",
            "Goiás" to "GO", "Maranhão" to "MA", "Mato Grosso" to "MT", "Mato Grosso do Sul" to "MS",
            "Minas Gerais" to "MG", "Pará" to "PA", "Paraíba" to "PB", "Paraná" to "PR",
            "Pernambuco" to "PE", "Piauí" to "PI", "Rio de Janeiro" to "RJ", "Rio Grande do Norte" to "RN",
            "Rio Grande do Sul" to "RS", "Rondônia" to "RO", "Roraima" to "RR", "Santa Catarina" to "SC",
            "São Paulo" to "SP", "Sergipe" to "SE", "Tocantins" to "TO"
        )
        return map[state] ?: state
    }

    fun isBetterLocation(location: Location, currentBestLocation: Location?): Boolean {
        if (currentBestLocation == null) return true

        val timeDelta = location.time - currentBestLocation.time
        val isSignificantlyNewer = timeDelta > 30000
        val isSignificantlyOlder = timeDelta < -30000
        val isNewer = timeDelta > 0

        if (isSignificantlyNewer) {
            return true
        } else if (isSignificantlyOlder) {
            return false
        }

        val accuracyDelta = (location.accuracy - currentBestLocation.accuracy).toInt()
        val isLessAccurate = accuracyDelta > 0
        val isMoreAccurate = accuracyDelta < 0
        val isSignificantlyLessAccurate = accuracyDelta > 50

        val isFromSameProvider = location.provider == currentBestLocation.provider

        if (isMoreAccurate) {
            return true
        } else if (isNewer && !isLessAccurate) {
            return true
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true
        }
        return false
    }
}
