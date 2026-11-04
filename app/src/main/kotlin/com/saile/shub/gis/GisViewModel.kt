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
import com.saile.shub.gis.domain.GpkgReaderProvider
import com.saile.shub.gis.usecase.GisSearchResult
import com.saile.shub.gis.usecase.ImportGpkgUseCase
import com.saile.shub.gis.usecase.ImportProgress
import com.saile.shub.gis.usecase.SearchFeatureUseCase
import com.saile.shub.gis.usecase.SearchItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.util.concurrent.atomic.AtomicLong

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

enum class GisInteractionMode(val label: String) {
    BROWSE("Navegar"),
    DIGITIZE("Digitalizar")
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

    // Default to Google Hybrid for high-contrast engineering satellite view with roads
    private val _currentBasemap = MutableStateFlow(BasemapType.GOOGLE_HYBRID)
    val currentBasemap: StateFlow<BasemapType> = _currentBasemap.asStateFlow()

    // QField Interaction Mode: BROWSE vs DIGITIZE
    private val _interactionMode = MutableStateFlow(GisInteractionMode.BROWSE)
    val interactionMode: StateFlow<GisInteractionMode> = _interactionMode.asStateFlow()

    private val _activeEditLayer = MutableStateFlow<String?>("BASE ERO – Poste de Distribuição")
    val activeEditLayer: StateFlow<String?> = _activeEditLayer.asStateFlow()

    private val _isLayersExpanded = MutableStateFlow(true)
    val isLayersExpanded: StateFlow<Boolean> = _isLayersExpanded.asStateFlow()

    private val _selectedTarget = MutableStateFlow<MapTarget?>(null)
    val selectedTarget: StateFlow<MapTarget?> = _selectedTarget.asStateFlow()

    private val _visibleElements = MutableStateFlow<List<GisRenderElement>>(emptyList())
    val visibleElements: StateFlow<List<GisRenderElement>> = _visibleElements.asStateFlow()

    private val _datasetExtent = MutableStateFlow<BoundingBox?>(null)
    val datasetExtent: StateFlow<BoundingBox?> = _datasetExtent.asStateFlow()

    private val _availableLayers = MutableStateFlow<List<GisLayerSummary>>(emptyList())
    val availableLayers: StateFlow<List<GisLayerSummary>> = _availableLayers.asStateFlow()

    private val _enabledLayers = MutableStateFlow<Set<String>>(emptySet())
    val enabledLayers: StateFlow<Set<String>> = _enabledLayers.asStateFlow()

    private val _zoomToExtentEvent = MutableSharedFlow<BoundingBox>(replay = 1, extraBufferCapacity = 1)
    val zoomToExtentEvent: SharedFlow<BoundingBox> = _zoomToExtentEvent.asSharedFlow()

    private val _offlineMbtilesPath = MutableStateFlow<String?>(null)
    val offlineMbtilesPath: StateFlow<String?> = _offlineMbtilesPath.asStateFlow()

    // Precision Reticle
    private val _isReticleVisible = MutableStateFlow(true)
    val isReticleVisible: StateFlow<Boolean> = _isReticleVisible.asStateFlow()

    // Measurement Tool (Régua de Medição)
    private val _isMeasurementMode = MutableStateFlow(false)
    val isMeasurementMode: StateFlow<Boolean> = _isMeasurementMode.asStateFlow()

    // GNSS / GPS Tracking
    private val _gpsTrackingMode = MutableStateFlow(GpsTrackingMode.OFF)
    val gpsTrackingMode: StateFlow<GpsTrackingMode> = _gpsTrackingMode.asStateFlow()

    private var searchJob: Job? = null
    private var viewportJob: Job? = null
    private var lastViewportParams: ViewportParams? = null
    private val queryGeneration = AtomicLong(0)
    private var layersInitialized = false

    @OptIn(ExperimentalCoroutinesApi::class)
    private val queryDispatcher = Dispatchers.IO.limitedParallelism(1)

    private data class ViewportParams(
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double,
        val zoom: Double
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            loadActiveDataset(autoZoom = true)
            checkForOfflineMbtiles()
        }
    }

    fun loadActiveDataset(autoZoom: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = getActiveGpkgFile()
            if (file == null) {
                Log.w("ShubGis", "loadActiveDataset: No active GPKG file found")
                return@launch
            }
            runCatching {
                val reader = GpkgReaderProvider.getOrOpen(file.absolutePath)
                if (reader == null) {
                    Log.w("ShubGis", "loadActiveDataset: could not open reader for ${file.absolutePath}")
                    return@launch
                }

                // Ensure high-performance SQLite search indexes exist in the background (sub-1ms lookup like QField)
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching {
                        val count = reader.createSearchIndexes()
                        if (count > 0) {
                            Log.i("ShubGis", "Search B-Tree indexes verified/created: $count indexes")
                        }
                    }.onFailure { e ->
                        Log.w("ShubGis", "Index creation notice: ${e.message}")
                    }
                }

                val extent = reader.getDatasetExtent()
                val layers = reader.getLayerSummaries()

                if (_enabledLayers.value.isEmpty()) {
                    _enabledLayers.value = layers.map { it.name }.toSet()
                }
                layersInitialized = true

                _datasetExtent.value = extent
                _availableLayers.value = layers.map {
                    it.copy(isVisible = _enabledLayers.value.contains(it.name))
                }

                if (_activeEditLayer.value == null && layers.isNotEmpty()) {
                    _activeEditLayer.value = layers.firstOrNull { it.type == GisElementType.POLE }?.name ?: layers.first().name
                }

                if (autoZoom && extent != null) {
                    _zoomToExtentEvent.tryEmit(extent)
                }

                val params = lastViewportParams
                val currentGen = queryGeneration.incrementAndGet()
                if (params != null) {
                    queryViewport(params.minLat, params.maxLat, params.minLon, params.maxLon, params.zoom, currentGen)
                } else if (extent != null) {
                    queryViewport(extent.minLat, extent.maxLat, extent.minLon, extent.maxLon, 9.0, currentGen)
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
            Log.i("ShubGis", "Active GPKG: ${file.absolutePath} (${file.length()} bytes)")
            return file
        }
        Log.w("ShubGis", "Active GPKG: no .gpkg found in ${gpkgDir.absolutePath}")
        return null
    }

    fun setInteractionMode(mode: GisInteractionMode) {
        _interactionMode.value = mode
    }

    fun toggleInteractionMode() {
        _interactionMode.value = if (_interactionMode.value == GisInteractionMode.BROWSE) {
            GisInteractionMode.DIGITIZE
        } else {
            GisInteractionMode.BROWSE
        }
    }

    fun setActiveEditLayer(layerName: String) {
        _activeEditLayer.value = layerName
    }

    fun toggleLayersExpanded() {
        _isLayersExpanded.value = !_isLayersExpanded.value
    }

    fun setBasemap(type: BasemapType) {
        _currentBasemap.value = type
    }

    fun toggleReticle() {
        _isReticleVisible.value = !_isReticleVisible.value
    }

    fun toggleMeasurementMode() {
        _isMeasurementMode.value = !_isMeasurementMode.value
    }

    fun cycleGpsTrackingMode() {
        _gpsTrackingMode.value = when (_gpsTrackingMode.value) {
            GpsTrackingMode.OFF -> GpsTrackingMode.FOLLOW
            GpsTrackingMode.FOLLOW -> GpsTrackingMode.FOLLOW_BEARING
            GpsTrackingMode.FOLLOW_BEARING -> GpsTrackingMode.OFF
        }
    }

    fun disableGpsFollowing() {
        if (_gpsTrackingMode.value != GpsTrackingMode.OFF) {
            _gpsTrackingMode.value = GpsTrackingMode.OFF
        }
    }

    fun toggleLayer(layerName: String) {
        val current = _enabledLayers.value.toMutableSet()
        if (current.contains(layerName)) {
            current.remove(layerName)
        } else {
            current.add(layerName)
        }
        _enabledLayers.value = current
        _availableLayers.value = _availableLayers.value.map {
            if (it.name == layerName) it.copy(isVisible = current.contains(layerName)) else it
        }

        lastViewportParams?.let {
            val currentGen = queryGeneration.incrementAndGet()
            viewModelScope.launch(queryDispatcher) {
                queryViewport(it.minLat, it.maxLat, it.minLon, it.maxLon, it.zoom, currentGen)
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
        val isFirst = (lastViewportParams == null)
        lastViewportParams = ViewportParams(minLat, maxLat, minLon, maxLon, zoom)
        viewportJob?.cancel()
        if (zoom < 5.0) {
            _visibleElements.value = emptyList()
            return
        }
        val currentGen = queryGeneration.incrementAndGet()
        viewportJob = viewModelScope.launch(queryDispatcher) {
            if (!isFirst) {
                delay(100)
            }
            queryViewport(minLat, maxLat, minLon, maxLon, zoom, currentGen)
        }
    }

    private suspend fun queryViewport(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        zoom: Double,
        generation: Long = queryGeneration.get()
    ) {
        if (generation != queryGeneration.get()) return
        val file = getActiveGpkgFile()
        if (file == null) {
            Log.w("ShubGis", "queryViewport: active GPKG file is null")
            _state.value = GisState.Error("Nenhum arquivo GeoPackage ativo encontrado.")
            _visibleElements.value = emptyList()
            return
        }
        val reader = GpkgReaderProvider.getOrOpen(file.absolutePath)
        if (reader == null) {
            Log.w("ShubGis", "queryViewport: could not open reader for ${file.absolutePath}")
            _state.value = GisState.Error("Não foi possível abrir o GeoPackage: ${file.name}")
            _visibleElements.value = emptyList()
            return
        }

        val activeEnabledLayers = if (layersInitialized) _enabledLayers.value else null
        val elements = reader.getFeaturesInBoundingBox(
            minLat = minLat,
            maxLat = maxLat,
            minLon = minLon,
            maxLon = maxLon,
            zoomLevel = zoom,
            enabledLayers = activeEnabledLayers,
            shouldContinue = { generation == queryGeneration.get() }
        )

        if (generation != queryGeneration.get()) {
            Log.i("ShubGis", "queryViewport: generation $generation discarded (current: ${queryGeneration.get()})")
            return
        }

        val count = elements.size
        Log.i("ShubGis", "queryViewport [gen=$generation]: bounds=[$minLat, $minLon to $maxLat, $maxLon], zoom=$zoom -> found $count elements")
        _visibleElements.value = elements
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
                        GpkgReaderProvider.closeAll()
                        layersInitialized = false
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
            delay(120)
            try {
                val gpkgPath = getActiveGpkgFile()?.absolutePath
                val results = searchUseCase.execute(query, gpkgPath = gpkgPath)
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
            val reader = GpkgReaderProvider.getOrOpen(file.absolutePath) ?: return@launch
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
                    val gen = queryGeneration.incrementAndGet()
                    queryViewport(it.minLat, it.maxLat, it.minLon, it.maxLon, it.zoom, gen)
                }
            }
        }
    }

    fun updateFeatureAttributes(layerName: String, fid: Long, attributes: Map<String, Any?>) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = getActiveGpkgFile() ?: return@launch
            val reader = GpkgReaderProvider.getOrOpen(file.absolutePath) ?: return@launch
            val writer = reader.getWriter() ?: return@launch

            val success = writer.updateFeatureAttributes(layerName, fid, attributes)
            if (success) {
                lastViewportParams?.let {
                    val gen = queryGeneration.incrementAndGet()
                    queryViewport(it.minLat, it.maxLat, it.minLon, it.maxLon, it.zoom, gen)
                }
            }
        }
    }

    fun deleteFeature(layerName: String, fid: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = getActiveGpkgFile() ?: return@launch
            val reader = GpkgReaderProvider.getOrOpen(file.absolutePath) ?: return@launch
            val writer = reader.getWriter() ?: return@launch

            val success = writer.deleteFeature(layerName, fid)
            if (success) {
                clearSelection()
                lastViewportParams?.let {
                    val gen = queryGeneration.incrementAndGet()
                    queryViewport(it.minLat, it.maxLat, it.minLon, it.maxLon, it.zoom, gen)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        GpkgReaderProvider.closeAll()
    }
}
