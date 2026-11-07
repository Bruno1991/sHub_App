package com.saile.shub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saile.shub.gis.GisScreen
import com.saile.shub.gis.GisViewModel
import com.saile.shub.photo.PhotoScreen
import com.saile.shub.photo.PhotoViewModel

/**
 * SHubApp.kt
 *
 * Responsabilidade Única:
 * Orquestrador principal de navegação entre os módulos do ecossistema sHub (sFoto e sGIS),
 * preservando o estado dos ViewModels na Activity e permitindo fluxo integrado sGIS ⇄ sFoto.
 */
enum class Destination { HOME, PHOTO, GIS }

@Composable
fun SHubApp() {
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    var returnToGisFromPhoto by rememberSaveable { mutableStateOf(false) }

    // Activity-scoped ViewModels preserve map camera, selection, and camera stamp state across module switches
    val gisViewModel: GisViewModel = viewModel()
    val photoViewModel: PhotoViewModel = viewModel()

    BackHandler(enabled = destination != Destination.HOME) {
        if (destination == Destination.PHOTO && returnToGisFromPhoto) {
            returnToGisFromPhoto = false
            destination = Destination.GIS
        } else {
            returnToGisFromPhoto = false
            destination = Destination.HOME
        }
    }

    when (destination) {
        Destination.HOME -> HomeScreen(
            onNavigate = {
                returnToGisFromPhoto = false
                destination = it
            }
        )
        Destination.PHOTO -> PhotoScreen(
            onBack = {
                if (returnToGisFromPhoto) {
                    returnToGisFromPhoto = false
                    destination = Destination.GIS
                } else {
                    destination = Destination.HOME
                }
            },
            viewModel = photoViewModel
        )
        Destination.GIS -> GisScreen(
            onBack = {
                returnToGisFromPhoto = false
                destination = Destination.HOME
            },
            onCaptureAssetPhoto = { assetStampLabel ->
                photoViewModel.prepareForGisAssetCapture(assetStampLabel)
                returnToGisFromPhoto = true
                destination = Destination.PHOTO
            },
            viewModel = gisViewModel
        )
    }
}
