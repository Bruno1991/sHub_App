package com.saile.shub.gis

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.saile.shub.gis.data.AppDatabase
import com.saile.shub.gis.data.entities.GisFeatureEntity
import com.saile.shub.gis.data.entities.ImportedPackageEntity
import com.saile.shub.gis.domain.GisElementType
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    private val _offlineMbtilesPath = MutableStateFlow<String?>(null)
    val offlineMbtilesPath: StateFlow<String?> = _offlineMbtilesPath.asStateFlow()

    private var searchJob: Job? = null
    private var viewportJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Check if there is already a ready package with file present
            val active = db.gisDao().getActivePackage()
            if (active != null && File(active.fileUri).exists()) {
                runCatching {
                    GpkgReader(active.fileUri).use { it.createSearchIndexes() }
                }
                checkForOfflineMbtiles()
                return@launch
            }

            // 2. Check if there are existing package records in Room whose file exists
            val packages = db.gisDao().getAllPackages()
            val existingPkg = packages.firstOrNull { File(it.fileUri).exists() }
            if (existingPkg != null) {
                runCatching {
                    GpkgReader(existingPkg.fileUri).use { reader ->
                        val count = reader.countTotalFeatures()
                        reader.createSearchIndexes()
                        db.gisDao().updatePackageStatus(existingPkg.id, "ready", count)
                    }
                }
                checkForOfflineMbtiles()
                return@launch
            }

            // 3. Auto-discover any .gpkg already present in files/gpkg/
            val gpkgDir = File(getApplication<Application>().filesDir, "gpkg")
            val files = gpkgDir.listFiles { f -> f.extension.equals("gpkg", ignoreCase = true) }
            val firstGpkg = files?.maxByOrNull { it.length() }
            if (firstGpkg != null && firstGpkg.exists()) {
                runCatching {
                    GpkgReader(firstGpkg.absolutePath).use { reader ->
                        val totalFeatures = reader.countTotalFeatures()
                        reader.createSearchIndexes()
                        val pkg = ImportedPackageEntity(
                            displayName = firstGpkg.name,
                            fileUri = firstGpkg.absolutePath,
                            importedAt = Instant.now().toString(),
                            status = "ready",
                            featureCount = totalFeatures
                        )
                        db.gisDao().insertPackage(pkg)
                    }
                }
            }

            checkForOfflineMbtiles()
        }
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
        viewportJob?.cancel()
        if (zoom < 8.0) {
            _visibleElements.value = emptyList()
            return
        }
        viewportJob = viewModelScope.launch(Dispatchers.IO) {
            delay(100)
            val active = db.gisDao().getActivePackage()
            var fileUri = active?.fileUri
            if (fileUri == null || !File(fileUri).exists()) {
                val gpkgDir = File(getApplication<Application>().filesDir, "gpkg")
                val f = gpkgDir.listFiles { file -> file.extension.equals("gpkg", ignoreCase = true) }?.firstOrNull()
                fileUri = f?.absolutePath
            }
            val reader = fileUri?.let { SearchFeatureUseCase.getReader(it) }
            val elements = reader?.getFeaturesInBoundingBox(minLat, maxLat, minLon, maxLon, zoom)
            _visibleElements.value = elements ?: emptyList()
        }
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
