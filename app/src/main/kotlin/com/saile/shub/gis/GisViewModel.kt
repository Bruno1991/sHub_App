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
import kotlinx.coroutines.withContext
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
    private val _networkMode = MutableStateFlow(GisNetworkMode.OFFLINE_ONLY)
    val networkMode: StateFlow<GisNetworkMode> = _networkMode.asStateFlow()

    private val _currentBasemap = MutableStateFlow(BasemapType.OFFLINE_MBTILES)
    val currentBasemap: StateFlow<BasemapType> = _currentBasemap.asStateFlow()

    // QField Interaction Mode: BROWSE vs DIGITIZE
    private val _interactionMode = MutableStateFlow(GisInteractionMode.BROWSE)
    val interactionMode: StateFlow<GisInteractionMode> = _interactionMode.asStateFlow()

    private val _activeEditLayer = MutableStateFlow<String?>(null)
    val activeEditLayer: StateFlow<String?> = _activeEditLayer.asStateFlow()

    private val _activeGpkgName = MutableStateFlow<String?>(null)
    val activeGpkgName: StateFlow<String?> = _activeGpkgName.asStateFlow()

    private val _activeGpkgSizeFormatted = MutableStateFlow<String?>(null)
    val activeGpkgSizeFormatted: StateFlow<String?> = _activeGpkgSizeFormatted.asStateFlow()

    private val _activeGpkgImportDate = MutableStateFlow<String?>(null)
    val activeGpkgImportDate: StateFlow<String?> = _activeGpkgImportDate.asStateFlow()

    private val _activeGpkgSrs = MutableStateFlow<String?>(null)
    val activeGpkgSrs: StateFlow<String?> = _activeGpkgSrs.asStateFlow()

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

    private val _activeMbtilesMetadata = MutableStateFlow<MbtilesMetadata?>(null)
    val activeMbtilesMetadata: StateFlow<MbtilesMetadata?> = _activeMbtilesMetadata.asStateFlow()

    private val _availableMbtiles = MutableStateFlow<List<MbtilesMetadata>>(emptyList())
    val availableMbtiles: StateFlow<List<MbtilesMetadata>> = _availableMbtiles.asStateFlow()

    private val _isImportingMbtiles = MutableStateFlow(false)
    val isImportingMbtiles: StateFlow<Boolean> = _isImportingMbtiles.asStateFlow()

    private val _mbtilesImportProgress = MutableStateFlow(0)
    val mbtilesImportProgress: StateFlow<Int> = _mbtilesImportProgress.asStateFlow()

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
            refreshAvailableMbtiles()
        }
    }

    fun loadActiveDataset(autoZoom: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = getActiveGpkgFile()
            if (file == null) {
                _activeGpkgName.value = null
                _activeGpkgSizeFormatted.value = null
                _activeGpkgImportDate.value = null
                _activeGpkgSrs.value = null
                Log.w("ShubGis", "loadActiveDataset: No active GPKG file found")
                return@launch
            }
            runCatching {
                val reader = GpkgReaderProvider.getOrOpen(file.absolutePath)
                if (reader == null) {
                    Log.w("ShubGis", "loadActiveDataset: could not open reader for ${file.absolutePath}")
                    return@launch
                }

                _activeGpkgName.value = file.name
                val mb = file.length() / (1024.0 * 1024.0)
                _activeGpkgSizeFormatted.value = if (mb >= 1024.0) {
                    String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0)
                } else {
                    String.format(java.util.Locale.US, "%.1f MB", mb)
                }
                _activeGpkgImportDate.value = runCatching {
                    val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                    sdf.format(java.util.Date(file.lastModified()))
                }.getOrDefault("--/--/----")
                _activeGpkgSrs.value = reader.getDatasetSrsDescription()

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

    fun setNetworkMode(mode: GisNetworkMode) {
        _networkMode.value = mode
        if (mode == GisNetworkMode.OFFLINE_ONLY) {
            setBasemap(BasemapType.OFFLINE_MBTILES)
        }
    }

    fun setBasemap(type: BasemapType) {
        _currentBasemap.value = type
        if (type == BasemapType.OFFLINE_MBTILES) {
            _networkMode.value = GisNetworkMode.OFFLINE_ONLY
            if (_activeMbtilesMetadata.value == null) {
                val list = _availableMbtiles.value
                if (list.isNotEmpty()) {
                    val best = list.minByOrNull { it.category.priority } ?: list.first()
                    _activeMbtilesMetadata.value = best
                    _offlineMbtilesPath.value = best.file.absolutePath
                } else {
                    refreshAvailableMbtiles()
                }
            }
        } else {
            _networkMode.value = GisNetworkMode.ONLINE_ALLOWED
        }
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

    fun refreshAvailableMbtiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = GisMbtilesHelper.scanAvailableMbtiles(getApplication())
            _availableMbtiles.value = list
            if (list.isNotEmpty()) {
                val best = list.minByOrNull { it.category.priority } ?: list.first()
                if (_activeMbtilesMetadata.value == null || _activeMbtilesMetadata.value?.file?.exists() != true) {
                    _activeMbtilesMetadata.value = best
                    _offlineMbtilesPath.value = best.file.absolutePath
                }
                if (_networkMode.value == GisNetworkMode.OFFLINE_ONLY) {
                    _currentBasemap.value = BasemapType.OFFLINE_MBTILES
                }
            }
        }
    }

    fun selectMbtiles(meta: MbtilesMetadata) {
        _activeMbtilesMetadata.value = meta
        _offlineMbtilesPath.value = meta.file.absolutePath
        _currentBasemap.value = BasemapType.OFFLINE_MBTILES
    }

    fun importMbtilesUri(uri: Uri, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _isImportingMbtiles.value = true
            _mbtilesImportProgress.value = 0
            val meta = withContext(Dispatchers.IO) {
                GisMbtilesHelper.importMbtilesFromUri(
                    context = getApplication(),
                    uri = uri,
                    onProgress = { percent, _, _ ->
                        _mbtilesImportProgress.value = percent
                    }
                )
            }
            _isImportingMbtiles.value = false
            if (meta != null) {
                refreshAvailableMbtiles()
                _activeMbtilesMetadata.value = meta
                _offlineMbtilesPath.value = meta.file.absolutePath
                _currentBasemap.value = BasemapType.OFFLINE_MBTILES
                meta.bounds?.let { _zoomToExtentEvent.tryEmit(it) }
                onResult(true)
            } else {
                onResult(false)
            }
        }
    }

    fun zoomToMbtilesExtent() {
        val bounds = _activeMbtilesMetadata.value?.bounds ?: return
        _zoomToExtentEvent.tryEmit(bounds)
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
            delay(80)
            try {
                val gpkgPath = getActiveGpkgFile()?.absolutePath
                val results = searchUseCase.execute(query, gpkgPath = gpkgPath)
                Log.i("ShubGisSearch", "Search '$query' -> coordMatch=${results.coordinateMatch != null}, features=${results.features.size}")
                _state.value = GisState.SearchResults(results)
            } catch (e: Exception) {
                Log.e("ShubGisSearch", "Search error: ${e.message}", e)
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
