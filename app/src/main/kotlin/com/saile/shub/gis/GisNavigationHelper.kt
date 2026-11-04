package com.saile.shub.gis

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object GisNavigationHelper {

    fun launchGoogleMapsNavigation(
        context: Context,
        latitude: Double,
        longitude: Double,
        title: String
    ) {
        // 1. Google Maps turn-by-turn navigation intent
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

        // 2. Generic geo: intent with marker title
        val geoUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(title)})")
        val geoIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        if (geoIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(geoIntent)
            Toast.makeText(context, "Abrindo mapa...", Toast.LENGTH_SHORT).show()
            return
        }

        // 3. Fallback to Google Maps Web URL
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

