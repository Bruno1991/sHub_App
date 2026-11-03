package com.saile.shub.photo

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saile.shub.photo.data.PhotoPreferencesDbHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OverlayColor(val label: String, val color: Color) {
    YELLOW("Amarelo", Color(0xFFFFFF00)),
    GREEN("Verde", Color(0xFF00FF00)),
    CYAN("Ciano", Color(0xFF00FFFF)),
    MAGENTA("Magenta", Color(0xFFFF00FF)),
    RED("Vermelho", Color(0xFFFF5252)), // Lighter red
    ORANGE("Laranja", Color(0xFFFF9800)),
    WHITE("Branco", Color(0xFFFFFFFF)),
}

enum class OverlayPosition(val label: String) {
    TOP_LEFT("Canto superior esquerdo"),
    TOP_RIGHT("Canto superior direito"),
    BOTTOM_LEFT("Canto inferior esquerdo"),
    BOTTOM_RIGHT("Canto inferior direito"),
    BOTTOM_CENTER("Meio inferior"),
}

enum class CameraFlashMode { AUTO, ON, OFF }

data class PhotoUiState(
    val overlayColor: OverlayColor = OverlayColor.YELLOW,
    val status: String? = null,
    val showUtm: Boolean = true,
    val showGeo: Boolean = true,
    val showDateTime: Boolean = true,
    val showAddress: Boolean = true,
    val showName: Boolean = false,
    val showAltitude: Boolean = true,
    val showAzimuth: Boolean = true,
    val showBackgroundBox: Boolean = false,
    val showGrid: Boolean = false,
    val userName: String = "sFoto",
    val position: OverlayPosition = OverlayPosition.BOTTOM_RIGHT,
    val lensFacingBack: Boolean = true,
    val flashMode: CameraFlashMode = CameraFlashMode.AUTO,
    val playShutterSound: Boolean = true,
    val selectedLogo: String? = null,
)

class PhotoViewModel(application: Application) : AndroidViewModel(application) {
    private val dbHelper = PhotoPreferencesDbHelper(application)
    private val mutableState = MutableStateFlow(dbHelper.getPreferences())
    val state = mutableState.asStateFlow()

    private fun updateState(updater: (PhotoUiState) -> PhotoUiState) {
        mutableState.update(updater)
    }

    fun savePreferences() {
        viewModelScope.launch(Dispatchers.IO) {
            dbHelper.savePreferences(mutableState.value)
        }
    }

    fun selectColor(color: OverlayColor) = updateState { it.copy(overlayColor = color) }
    fun setStatus(message: String?) = mutableState.update { it.copy(status = message) }
    
    fun toggleUtm(show: Boolean) = updateState { it.copy(showUtm = show) }
    fun toggleGeo(show: Boolean) = updateState { it.copy(showGeo = show) }
    fun toggleDateTime(show: Boolean) = updateState { it.copy(showDateTime = show) }
    fun toggleAddress(show: Boolean) = updateState { it.copy(showAddress = show) }
    fun toggleName(show: Boolean) = updateState { it.copy(showName = show) }
    fun toggleAltitude(show: Boolean) = updateState { it.copy(showAltitude = show) }
    fun toggleAzimuth(show: Boolean) = updateState { it.copy(showAzimuth = show) }
    fun toggleBackgroundBox(show: Boolean) = updateState { it.copy(showBackgroundBox = show) }
    fun toggleGrid(show: Boolean) = updateState { it.copy(showGrid = show) }
    fun setUserName(name: String) = updateState { it.copy(userName = name) }
    fun setPosition(pos: OverlayPosition) = updateState { it.copy(position = pos) }
    fun toggleLens() = updateState { it.copy(lensFacingBack = !it.lensFacingBack) }
    fun cycleFlash() = updateState { 
        val nextFlash = when(it.flashMode) {
            CameraFlashMode.AUTO -> CameraFlashMode.ON
            CameraFlashMode.ON -> CameraFlashMode.OFF
            CameraFlashMode.OFF -> CameraFlashMode.AUTO
        }
        it.copy(flashMode = nextFlash)
    }
    fun toggleShutterSound(play: Boolean) = updateState { it.copy(playShutterSound = play) }
    fun setLogo(logo: String?) = updateState { it.copy(selectedLogo = logo) }
}
