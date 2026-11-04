package com.saile.shub.gis

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.saile.shub.gis.data.AppDatabase
import com.saile.shub.gis.data.entities.GisFeatureEntity
import com.saile.shub.gis.data.entities.ImportedPackageEntity
import com.saile.shub.gis.domain.BoundingBox
import com.saile.shub.gis.domain.GisElementType
import com.saile.shub.gis.domain.GisLayerSummary
import com.saile.shub.gis.domain.GisRenderElement
import com.saile.shub.gis.domain.GpkgReader
import com.saile.shub.gis.usecase.GisSearchResult
import com.saile.shub.gis.usecase.ImportGpkgUseCase
import com.saile.shub.gis.usecase.ImportProgress
import com.saile.shub.gis.usecase.SearchFeatureUseCase
import com.saile.shub.gis.usecase.SearchItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant

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

    private val _visibleElements = MutableStateFlow<List<GisRenderElement>>(emptyList())
    val visibleElements: StateFlow<List<GisRenderElement>> = _visibleElements.asStateFlow()

    private val _datasetExtent = MutableStateFlow<BoundingBox?>(null)
    val datasetExtent: StateFlow<BoundingBox?> = _datasetExtent.asStateFlow()

    private val _availableLayers = MutableStateFlow<List<GisLayerSummary>>(emptyList())
    val availableLayers: StateFlow<List<GisLayerSummary>> = _availableLayers.asStateFlow()

    private val _activeLayerFilter = MutableStateFlow<Set<GisElementType>>(GisElementType.values().toSet())
    val activeLayerFilter: StateFlow<Set<GisElementType>> = _activeLayerFilter.asStateFlow()

    private val _zoomToExtentEvent = MutableSharedFlow<BoundingBox>(extraBufferCapacity = 1)
    val zoomToExtentEvent: SharedFlow<BoundingBox> = _zoomToExtentEvent.asSharedFlow()

    private val _offlineMbtilesPath = MutableStateFlow<String?>(null)
    val offlineMbtilesPath: StateFlow<String?> = _offlineMbtilesPath.asStateFlow()

    private var searchJob: Job? = null
    private var viewportJob: Job? = null
    private var lastViewportParams: ViewportParams? = null

    private data class ViewportParams(
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double,
        val zoom: Double
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            loadActiveDataset(autoZoom = false)
            checkForOfflineMbtiles()
        }
    }

    fun loadActiveDataset(autoZoom: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = getActiveGpkgFile() ?: return@launch
            runCatching {
                val reader = SearchFeatureUseCase.getReader(file.absolutePath) ?: GpkgReader(file.absolutePath)
                val extent = reader.getDatasetExtent()
                val layers = reader.getLayerSummaries()

                _datasetExtent.value = extent
                _availableLayers.value = layers

                if (autoZoom && extent != null) {
                    _zoomToExtentEvent.tryEmit(extent)
                }

                // If we have viewport coordinates or extent, refresh viewport
                val params = lastViewportParams
                if (params != null) {
                    queryViewport(params.minLat, params.maxLat, params.minLon, params.maxLon, params.zoom)
                } else if (extent != null) {
                    queryViewport(extent.minLat, extent.maxLat, extent.minLon, extent.maxLon, 9.0)
                }
            }.onFailure {
                Log.e("ShubGis", "Failed to load active dataset: ${it.message}", it)
            }
        }
    }

    private fun getActiveGpkgFile(): File? {
        val gpkgDir = File(getApplication<Application>().filesDir, "gpkg")
        val files = gpkgDir.listFiles { f -> f.extension.equals("gpkg", ignoreCase = true) }
        val file = files?.maxByOrNull { it.length() }
        if (file != null && file.exists()) {
            return file
        }
        return null
    }

    fun toggleLayer(type: GisElementType) {
        val current = _activeLayerFilter.value.toMutableSet()
        if (current.contains(type)) {
            current.remove(type)
        } else {
            current.add(type)
        }
        _activeLayerFilter.value = current
        _availableLayers.value = _availableLayers.value.map {
            if (it.type == type) it.copy(isVisible = current.contains(type)) else it
        }

        // Re-query current viewport with new layer filter
        lastViewportParams?.let {
            viewModelScope.launch(Dispatchers.IO) {
                queryViewport(it.minLat, it.maxLat, it.minLon, it.maxLon, it.zoom)
            }
        }
    }

    fun zoomToProjectExtent() {
        val extent = _datasetExtent.value ?: return
        _zoomToExtentEvent.tryEmit(extent)
    }

    private fun checkForOfflineMbtiles() {
        val downloadDir = File("/sdcard/Download")
        val downloadMbtiles = downloadDir.listFiles { f -> f.extension.equals("mbtiles", ignoreCase = true) }?.firstOrNull()
        if (downloadMbtiles != null && downloadMbtiles.exists()) {
            _offlineMbtilesPath.value = downloadMbtiles.absolutePath
            return
        }

        val appDir = File(getApplication<Application>().filesDir, "mbtiles")
        val appMbtiles = appDir.listFiles { f -> f.extension.equals("mbtiles", ignoreCase = true) }?.firstOrNull()
        if (appMbtiles != null && appMbtiles.exists()) {
            _offlineMbtilesPath.value = appMbtiles.absolutePath
        }
    }

    fun onViewportChanged(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, zoom: Double) {
        lastViewportParams = ViewportParams(minLat, maxLat, minLon, maxLon, zoom)
        viewportJob?.cancel()
        if (zoom < 7.0) {
            _visibleElements.value = emptyList()
            return
        }
        viewportJob = viewModelScope.launch(Dispatchers.IO) {
            delay(100)
            queryViewport(minLat, maxLat, minLon, maxLon, zoom)
        }
    }

    private suspend fun queryViewport(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, zoom: Double) {
        val file = getActiveGpkgFile() ?: return
        val reader = SearchFeatureUseCase.getReader(file.absolutePath)
        val elements = reader?.getFeaturesInBoundingBox(
            minLat = minLat,
            maxLat = maxLat,
            minLon = minLon,
            maxLon = maxLon,
            zoomLevel = zoom,
            enabledTypes = _activeLayerFilter.value
        )
        _visibleElements.value = elements ?: emptyList()
    }

    fun selectRenderElement(element: GisRenderElement) {
        val (lat, lon) = when (element) {
            is GisRenderElement.Point -> Pair(element.latitude, element.longitude)
            is GisRenderElement.Line -> element.points.firstOrNull() ?: return
        }

        val entity = GisFeatureEntity(
            id = element.fid,
            packageId = 1L,
            sourceLayer = element.layerName,
            featureSourceId = element.fid.toString(),
            codeRaw = element.title,
            codeNorm = element.title.lowercase(),
            geometryType = if (element is GisRenderElement.Point) "Point" else "LineString",
            centroidLat = lat,
            centroidLon = lon,
            minLat = lat,
            minLon = lon,
            maxLat = lat,
            maxLon = lon,
            metadataJson = element.metadataJson
        )

        _selectedTarget.value = MapTarget(
            latitude = lat,
            longitude = lon,
            title = element.title,
            subtitle = element.layerName,
            zoomLevel = 18.5,
            feature = entity
        )
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
                        loadActiveDataset(autoZoom = true)
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
