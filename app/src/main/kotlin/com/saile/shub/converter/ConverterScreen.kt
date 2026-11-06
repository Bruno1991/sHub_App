package com.saile.shub.converter

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saile.shub.core.geo.GeoCoordinate
import com.saile.shub.core.geo.Hemisphere
import com.saile.shub.core.geo.UtmConverter
import com.saile.shub.core.geo.UtmCoordinate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen(
    onBack: () -> Unit,
    viewModel: ConverterViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val geo = state.geoResult
    val utm = state.utmResult
    val link = geo?.let(::mapsLink)

    Scaffold(topBar = { TopAppBar(title = { Text("sGeoConversor") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onBack) { Text("Voltar ao sHub") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.mode == ConversionMode.GEO_TO_UTM,
                    onClick = { viewModel.changeMode(ConversionMode.GEO_TO_UTM) },
                    label = { Text("GEO para UTM") },
                )
                FilterChip(
                    selected = state.mode == ConversionMode.UTM_TO_GEO,
                    onClick = { viewModel.changeMode(ConversionMode.UTM_TO_GEO) },
                    label = { Text("UTM para GEO") },
                )
            }
            if (state.mode == ConversionMode.GEO_TO_UTM) {
                GeoInputs(state, viewModel)
            } else {
                UtmInputs(state, viewModel)
            }
            OutlinedTextField(
                value = state.targetZone,
                onValueChange = viewModel::updateTargetZone,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Zona UTM de destino (opcional, ex.: 20 ou 21)") },
                singleLine = true,
            )
            Button(onClick = viewModel::convert, modifier = Modifier.fillMaxWidth()) { Text("Converter") }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (geo != null && utm != null && link != null) {
                ResultCard(
                    geo = formatGeo(geo.latitude, geo.longitude),
                    dms = UtmConverter.formatDms(geo.latitude, geo.longitude),
                    utm = formatUtm(utm, geo.latitude),
                    link = link,
                    onCopy = { clipboard.setText(AnnotatedString(it)) },
                    onRoute = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                    },
                )
            }
        }
    }
}

@Composable
private fun GeoInputs(state: ConverterUiState, viewModel: ConverterViewModel) {
    OutlinedTextField(
        value = state.latitude,
        onValueChange = viewModel::updateLatitude,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Latitude decimal") },
        singleLine = true,
    )
    OutlinedTextField(
        value = state.longitude,
        onValueChange = viewModel::updateLongitude,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Longitude decimal") },
        singleLine = true,
    )
}

@Composable
private fun UtmInputs(state: ConverterUiState, viewModel: ConverterViewModel) {
    OutlinedTextField(
        value = state.zone,
        onValueChange = viewModel::updateZone,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Zona UTM") },
        singleLine = true,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.hemisphere == Hemisphere.NORTH,
            onClick = { viewModel.updateHemisphere(Hemisphere.NORTH) },
            label = { Text("Norte") },
        )
        FilterChip(
            selected = state.hemisphere == Hemisphere.SOUTH,
            onClick = { viewModel.updateHemisphere(Hemisphere.SOUTH) },
            label = { Text("Sul") },
        )
    }
    OutlinedTextField(
        value = state.easting,
        onValueChange = viewModel::updateEasting,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("X / Easting (m)") },
        singleLine = true,
    )
    OutlinedTextField(
        value = state.northing,
        onValueChange = viewModel::updateNorthing,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Y / Northing (m)") },
        singleLine = true,
    )
}

@Composable
private fun ResultCard(
    geo: String,
    dms: String,
    utm: String,
    link: String,
    onCopy: (String) -> Unit,
    onRoute: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Resultado", style = MaterialTheme.typography.titleLarge)
            HorizontalDivider()
            Text("GEO (Decimal)\n$geo")
            Text("GEO (DMS)\n$dms", style = MaterialTheme.typography.bodyMedium)
            Text("UTM\n$utm")
            Text("Link\n$link", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onCopy(geo) }) { Text("Copiar GEO") }
                OutlinedButton(onClick = { onCopy(utm) }) { Text("Copiar UTM") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onCopy(link) }) { Text("Copiar link") }
                Button(onClick = onRoute) { Text("Abrir rota") }
            }
        }
    }
}

private fun formatGeo(latitude: Double, longitude: Double): String = String.format(
    Locale.US,
    "%.8f, %.8f",
    latitude,
    longitude,
)

private fun formatUtm(utm: UtmCoordinate, latitude: Double): String = String.format(
    Locale.US,
    "Zona %d%c (%s) | X: %.2f, Y: %.2f",
    utm.zone,
    UtmConverter.getUtmBand(latitude),
    if (utm.hemisphere == Hemisphere.SOUTH) "S" else "N",
    utm.easting,
    utm.northing,
)

private fun mapsLink(geo: GeoCoordinate): String =
    "https://www.google.com/maps/search/?api=1&query=${geo.latitude},${geo.longitude}"
