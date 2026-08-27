package com.saile.shub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.saile.shub.converter.ConverterScreen
import com.saile.shub.photo.PhotoScreen
import com.saile.shub.gis.GisScreen

enum class Destination { HOME, CONVERTER, PHOTO, GIS }

@Composable
fun SHubApp() {
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    
    BackHandler(enabled = destination != Destination.HOME) {
        destination = Destination.HOME
    }

    when (destination) {
        Destination.HOME -> HomeScreen(onNavigate = { destination = it })
        Destination.CONVERTER -> ConverterScreen(onBack = { destination = Destination.HOME })
        Destination.PHOTO -> PhotoScreen(onBack = { destination = Destination.HOME })
        Destination.GIS -> GisScreen(onBack = { destination = Destination.HOME })
    }
}
