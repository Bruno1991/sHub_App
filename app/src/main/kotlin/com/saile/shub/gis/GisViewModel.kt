package com.saile.shub.gis

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.saile.shub.gis.data.AppDatabase
import com.saile.shub.gis.data.entities.GisFeatureEntity
import com.saile.shub.gis.usecase.GisSearchResult
import com.saile.shub.gis.usecase.ImportGpkgUseCase
import com.saile.shub.gis.usecase.ImportProgress
import com.saile.shub.gis.usecase.SearchFeatureUseCase
import com.saile.shub.gis.usecase.SearchItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class GisState {
    object Idle : GisState()
    data class Importing(val progress: ImportProgress) : GisState()
    data class SearchResults(val result: GisSearchResult) : GisState()
    data class Error(val message: String) : GisState()
}

data class MapTarget(
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val subtitle: String?,
    val zoomLevel: Double = 18.0,
    val feature: GisFeatureEntity? = null
)

class GisViewModel(application: Application) : AndroidViewModel(application) {
    
    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "shub-gis-db"
    ).fallbackToDestructiveMigration().build()
    
    private val importUseCase = ImportGpkgUseCase(application, db.gisDao())
    private val searchUseCase = SearchFeatureUseCase(db.gisDao())
    
    private val _state = MutableStateFlow<GisState>(GisState.Idle)
    val state: StateFlow<GisState> = _state.asStateFlow()

    private val _isSatelliteMode = MutableStateFlow(false)
    val isSatelliteMode: StateFlow<Boolean> = _isSatelliteMode.asStateFlow()

    private val _selectedTarget = MutableStateFlow<MapTarget?>(null)
    val selectedTarget: StateFlow<MapTarget?> = _selectedTarget.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val active = db.gisDao().getActivePackage()
            if (active == null) {
                val gpkgDir = java.io.File(getApplication<Application>().filesDir, "gpkg")
                val files = gpkgDir.listFiles { f -> f.extension.equals("gpkg", ignoreCase = true) }
                val firstGpkg = files?.firstOrNull()
                if (firstGpkg != null && firstGpkg.exists()) {
                    runCatching {
                        com.saile.shub.gis.domain.GpkgReader(firstGpkg.absolutePath).use { reader ->
                            val totalFeatures = reader.countTotalFeatures()
                            reader.createSearchIndexes()
                            val pkg = com.saile.shub.gis.data.entities.ImportedPackageEntity(
                                displayName = firstGpkg.name,
                                fileUri = firstGpkg.absolutePath,
                                importedAt = java.time.Instant.now().toString(),
                                status = "ready",
                                featureCount = totalFeatures
                            )
                            db.gisDao().insertPackage(pkg)
                        }
                    }
                }
            }
        }
    }

    fun toggleSatelliteMode() {
        _isSatelliteMode.value = !_isSatelliteMode.value
    }

    fun importGpkg(uri: Uri, displayName: String) {
        viewModelScope.launch {
            try {
                importUseCase.execute(uri, displayName).collect { progress ->
                    _state.value = GisState.Importing(progress)
                    if (progress is ImportProgress.Completed) {
                        _state.value = GisState.Idle
                    }
                }
            } catch (e: Exception) {
                _state.value = GisState.Error(e.message ?: "Erro ao importar arquivo.")
            }
        }
    }
    
    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.value = GisState.Idle
            return
        }
        searchJob = viewModelScope.launch {
            delay(150)
            try {
                val results = searchUseCase.execute(query)
                _state.value = GisState.SearchResults(results)
            } catch (e: Exception) {
                _state.value = GisState.Error(e.message ?: "Erro na busca.")
            }
        }
    }

    fun selectItem(item: SearchItem) {
        when (item) {
            is SearchItem.CoordinateItem -> {
                _selectedTarget.value = MapTarget(
                    latitude = item.latitude,
                    longitude = item.longitude,
                    title = item.title,
                    subtitle = item.subtitle,
                    zoomLevel = 18.0
                )
            }
            is SearchItem.FeatureItem -> {
                val lat = item.latitude
                val lon = item.longitude
                if (lat != null && lon != null) {
                    _selectedTarget.value = MapTarget(
                        latitude = lat,
                        longitude = lon,
                        title = item.title,
                        subtitle = item.subtitle,
                        zoomLevel = 18.5,
                        feature = item.entity
                    )
                }
            }
        }
    }

    fun clearSelection() {
        _selectedTarget.value = null
    }
}
