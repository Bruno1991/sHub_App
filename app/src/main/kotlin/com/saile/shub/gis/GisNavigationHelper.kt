package com.saile.shub.gis

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * GisNavigationHelper.kt
 *
 * Responsabilidade Única:
 * Utilitário de navegação externa (Google Maps / Geo Intent) e motor de cálculo geodésico
 * para a Linha Guia Rural ("Fora de Estrada"), fornecendo distância exata e azimute (rumo)
 * em tempo real entre a posição GNSS do técnico e o ativo/cliente selecionado.
 */
data class RuralGuidanceMetrics(
    val distanceMeters: Double,
    val bearingDegrees: Float,
    val cardinalDirection: String,
    val formattedDistance: String,
    val formattedBearing: String
)

object GisNavigationHelper {

    fun calculateRuralGuidance(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double
    ): RuralGuidanceMetrics {
        val dist = calculateDistanceMeters(fromLat, fromLon, toLat, toLon)
        val bearing = calculateBearingDegrees(fromLat, fromLon, toLat, toLon)
        val cardinal = getCardinalDirection(bearing)
        val distText = formatDistance(dist)
        val bearingText = String.format(Locale.US, "%.0f° %s", bearing, cardinal)
        return RuralGuidanceMetrics(
            distanceMeters = dist,
            bearingDegrees = bearing,
            cardinalDirection = cardinal,
            formattedDistance = distText,
            formattedBearing = bearingText
        )
    }

    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371008.8 // Earth mean radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2.0) * sin(dLon / 2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt((1.0 - a).coerceAtLeast(0.0)))
        return r * c
    }

    fun calculateBearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        val theta = Math.toDegrees(atan2(y, x))
        return ((theta + 360.0) % 360.0).toFloat()
    }

    fun formatDistance(meters: Double): String {
        return if (meters >= 1000.0) {
            String.format(Locale.US, "%.2f km", meters / 1000.0)
        } else {
            String.format(Locale.US, "%.0f m", meters)
        }
    }

    fun getCardinalDirection(bearingDegrees: Float): String {
        val normalized = (bearingDegrees % 360f + 360f) % 360f
        return when {
            normalized >= 337.5f || normalized < 22.5f -> "N"
            normalized < 67.5f -> "NE"
            normalized < 112.5f -> "L"
            normalized < 157.5f -> "SE"
            normalized < 202.5f -> "S"
            normalized < 247.5f -> "SO"
            normalized < 292.5f -> "O"
            else -> "NO"
        }
    }

    fun launchGoogleMapsNavigation(
        context: Context,
        latitude: Double,
        longitude: Double,
        title: String
    ) {
        val gmapsNavUri = Uri.parse("google.navigation:q=$latitude,$longitude&mode=d")
        val navIntent = Intent(Intent.ACTION_VIEW, gmapsNavUri).apply {
            setPackage("com.google.android.apps.maps")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        if (navIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(navIntent)
            Toast.makeText(context, "Iniciando navegação no Google Maps...", Toast.LENGTH_SHORT).show()
            return
        }

        val geoUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(title)})")
        val geoIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        if (geoIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(geoIntent)
            Toast.makeText(context, "Abrindo mapa...", Toast.LENGTH_SHORT).show()
            return
        }

        val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$latitude,$longitude")
        val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching {
            context.startActivity(webIntent)
            Toast.makeText(context, "Abrindo rota no navegador...", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Não foi possível abrir o Google Maps", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyCoordinatesToClipboard(context: Context, latitude: Double, longitude: Double, utmText: String? = null) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = buildString {
            append("GEO: $latitude, $longitude")
            if (!utmText.isNullOrBlank()) {
                append("\nUTM: $utmText")
            }
        }
        val clip = ClipData.newPlainText("Coordenadas", text)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "Coordenadas copiadas para a área de transferência", Toast.LENGTH_SHORT).show()
    }
}
