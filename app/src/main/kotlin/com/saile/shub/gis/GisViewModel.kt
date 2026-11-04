package com.saile.shub.gis

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.saile.shub.gis.data.AppDatabase
import com.saile.shub.gis.data.entities.GisFeatureEntity
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

enum class GpsTrackingMode {
    OFF,
    FOLLOW,
    FOLLOW_BEARING
}

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

    // Basemaps: Default to Satellite
    private val _currentBasemap = MutableStateFlow(BasemapType.SATELLITE)
    val currentBasemap: StateFlow<BasemapType> = _currentBasemap.asStateFlow()

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

    // QField Precision Reticle
    private val _isReticleVisible = MutableStateFlow(false)
    val isReticleVisible: StateFlow<Boolean> = _isReticleVisible.asStateFlow()

    // QField Measurement Tool (Régua de Medição)
    private val _isMeasurementMode = MutableStateFlow(false)
    val isMeasurementMode: StateFlow<Boolean> = _isMeasurementMode.asStateFlow()

    // QField Digitizing / Editing Mode
    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    // GNSS / GPS Tracking
    private val _gpsTrackingMode = MutableStateFlow(GpsTrackingMode.OFF)
    val gpsTrackingMode: StateFlow<GpsTrackingMode> = _gpsTrackingMode.asStateFlow()

    // Photo Attachments refresh trigger
    private val _assetPhotosVersion = MutableStateFlow(0)
    val assetPhotosVersion: StateFlow<Int> = _assetPhotosVersion.asStateFlow()

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

    fun getActiveGpkgFile(): File? {
        val gpkgDir = File(getApplication<Application>().filesDir, "gpkg")
        val files = gpkgDir.listFiles { f -> f.extension.equals("gpkg", ignoreCase = true) }
        val file = files?.maxByOrNull { it.length() }
        if (file != null && file.exists()) {
            return file
        }
        return null
    }

    fun setBasemap(type: BasemapType) {
        _currentBasemap.value = type
    }

    fun toggleBasemap() {
        _currentBasemap.value = when (_currentBasemap.value) {
            BasemapType.SATELLITE -> BasemapType.STREETS
            BasemapType.STREETS -> BasemapType.TOPO
            BasemapType.TOPO -> if (_offlineMbtilesPath.value != null) BasemapType.OFFLINE_MBTILES else BasemapType.SATELLITE
            BasemapType.OFFLINE_MBTILES -> BasemapType.SATELLITE
        }
    }

    fun toggleReticle() {
        _isReticleVisible.value = !_isReticleVisible.value
    }

    fun toggleMeasurementMode() {
        _isMeasurementMode.value = !_isMeasurementMode.value
    }

    fun toggleEditMode() {
        _isEditMode.value = !_isEditMode.value
    }

    fun cycleGpsTrackingMode() {
        _gpsTrackingMode.value = when (_gpsTrackingMode.value) {
            GpsTrackingMode.OFF -> GpsTrackingMode.FOLLOW
            GpsTrackingMode.FOLLOW -> GpsTrackingMode.FOLLOW_BEARING
            GpsTrackingMode.FOLLOW_BEARING -> GpsTrackingMode.OFF
        }
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
        val searchDirs = listOf(
            File("/sdcard/Download"),
            File("/sdcard/Documents"),
            File("/sdcard/s_hub"),
            File("/sdcard/s_hub/maps"),
            File(getApplication<Application>().filesDir, "mbtiles"),
            getApplication<Application>().getExternalFilesDir(null)
        )

        for (dir in searchDirs) {
            if (dir != null && dir.exists()) {
                val found = dir.listFiles { f -> f.extension.equals("mbtiles", ignoreCase = true) }?.firstOrNull()
                if (found != null && found.exists()) {
                    _offlineMbtilesPath.value = found.absolutePath
                    return
                }
            }
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

    // --- Digitizing / Editing Methods ---
    fun addPointFeature(
        layerName: String,
        latitude: Double,
        longitude: Double,
        code: String,
        extraAttributes: Map<String, Any?> = emptyMap()
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = getActiveGpkgFile() ?: return@launch
            val reader = SearchFeatureUseCase.getReader(file.absolutePath) ?: return@launch
            val writer = reader.getWriter() ?: return@launch

            val cols = reader.getLayerColumns(layerName)
            val codeCol = cols.firstOrNull {
                it.contains("codigo", ignoreCase = true) ||
                it.contains("operativo", ignoreCase = true) ||
                it.contains("name", ignoreCase = true) ||
                it.contains("nome", ignoreCase = true)
            } ?: "codigo"

            val attrs = mutableMapOf<String, Any?>()
            attrs[codeCol] = code
            attrs.putAll(extraAttributes)

            val newFid = writer.insertPointFeature(layerName, latitude, longitude, attrs)
            if (newFid > 0) {
                lastViewportParams?.let {
                    queryViewport(it.minLat, it.maxLat, it.minLon, it.maxLon, it.zoom)
                }
            }
        }
    }

    fun updateFeatureAttributes(layerName: String, fid: Long, attributes: Map<String, Any?>) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = getActiveGpkgFile() ?: return@launch
            val reader = SearchFeatureUseCase.getReader(file.absolutePath) ?: return@launch
            val writer = reader.getWriter() ?: return@launch

            val success = writer.updateFeatureAttributes(layerName, fid, attributes)
            if (success) {
                lastViewportParams?.let {
                    queryViewport(it.minLat, it.maxLat, it.minLon, it.maxLon, it.zoom)
                }
            }
        }
    }

    fun deleteFeature(layerName: String, fid: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = getActiveGpkgFile() ?: return@launch
            val reader = SearchFeatureUseCase.getReader(file.absolutePath) ?: return@launch
            val writer = reader.getWriter() ?: return@launch

            val success = writer.deleteFeature(layerName, fid)
            if (success) {
                clearSelection()
                lastViewportParams?.let {
                    queryViewport(it.minLat, it.maxLat, it.minLon, it.maxLon, it.zoom)
                }
            }
        }
    }

    // --- Photo Attachments ---
    fun getAssetPhotos(fid: Long): List<String> {
        val prefs = getApplication<Application>().getSharedPreferences("gis_photos", Context.MODE_PRIVATE)
        val set = prefs.getStringSet("photos_$fid", emptySet()) ?: emptySet()
        return set.toList()
    }

    fun addAssetPhoto(fid: Long, photoPath: String) {
        val prefs = getApplication<Application>().getSharedPreferences("gis_photos", Context.MODE_PRIVATE)
        val set = (prefs.getStringSet("photos_$fid", emptySet()) ?: emptySet()).toMutableSet()
        set.add(photoPath)
        prefs.edit().putStringSet("photos_$fid", set).apply()
        _assetPhotosVersion.value++
    }
}
