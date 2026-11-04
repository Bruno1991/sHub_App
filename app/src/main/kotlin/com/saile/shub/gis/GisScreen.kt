package com.saile.shub.gis

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saile.shub.core.geo.GeoCoordinate
import com.saile.shub.core.geo.UtmConverter
import com.saile.shub.gis.data.entities.GisFeatureEntity
import com.saile.shub.gis.domain.GisElementType
import com.saile.shub.gis.usecase.ImportProgress
import com.saile.shub.gis.usecase.SearchItem
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GisScreen(
    onBack: () -> Unit,
    viewModel: GisViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val currentBasemap by viewModel.currentBasemap.collectAsState()
    val selectedTarget by viewModel.selectedTarget.collectAsState()
    val visibleElements by viewModel.visibleElements.collectAsState()
    val datasetExtent by viewModel.datasetExtent.collectAsState()
    val availableLayers by viewModel.availableLayers.collectAsState()
    val activeLayerFilter by viewModel.activeLayerFilter.collectAsState()
    val offlineMbtilesPath by viewModel.offlineMbtilesPath.collectAsState()

    val isReticleVisible by viewModel.isReticleVisible.collectAsState()
    val isMeasurementMode by viewModel.isMeasurementMode.collectAsState()
    val isEditMode by viewModel.isEditMode.collectAsState()
    val gpsTrackingMode by viewModel.gpsTrackingMode.collectAsState()
    val assetPhotosVersion by viewModel.assetPhotosVersion.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    var showLayersSheet by remember { mutableStateOf(false) }
    var showBasemapDialog by remember { mutableStateOf(false) }

    // Measurement HUD state
    var measureDist by remember { mutableDoubleStateOf(0.0) }
    var measureArea by remember { mutableDoubleStateOf(0.0) }
    var measurePointsCount by remember { mutableIntStateOf(0) }

    // Center map coordinates for Reticle
    var centerLat by remember { mutableDoubleStateOf(-10.83) }
    var centerLon by remember { mutableDoubleStateOf(-63.28) }

    // Add Feature Dialog State
    var showAddFeatureDialog by remember { mutableStateOf(false) }
    var showEditAttrsDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Fullscreen photo preview
    var previewPhotoPath by remember { mutableStateOf<String?>(null) }

    // Keep reference to MapView
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var myLocationOverlayRef by remember { mutableStateOf<MyLocationNewOverlay?>(null) }

    // Haptic feedback helper
    fun triggerHapticFeedback() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(50)
                }
            }
        }
    }

    // Overlays
    val vectorOverlay = remember {
        GisVectorOverlay(
            onElementTapped = { elem ->
                viewModel.selectRenderElement(elem)
            },
            onElementLongPressed = { elem ->
                triggerHapticFeedback()
                viewModel.selectRenderElement(elem)
                val (lat, lon) = when (elem) {
                    is com.saile.shub.gis.domain.GisRenderElement.Point -> Pair(elem.latitude, elem.longitude)
                    is com.saile.shub.gis.domain.GisRenderElement.Line -> elem.points.firstOrNull() ?: Pair(0.0, 0.0)
                }
                GisNavigationHelper.launchGoogleMapsNavigation(context, lat, lon, elem.title)
            }
        )
    }

    val measurementOverlay = remember {
        GisMeasurementOverlay { dist, area, count ->
            measureDist = dist
            measureArea = area
            measurePointsCount = count
        }
    }

    // Initialize OsmDroid with High-Performance Persistent Cache
    LaunchedEffect(Unit) {
        val osmConfig = Configuration.getInstance()
        osmConfig.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        osmConfig.userAgentValue = context.packageName

        // 100% Offline & Instant Loading Cache configuration
        osmConfig.tileFileSystemCacheMaxBytes = 3L * 1024L * 1024L * 1024L // 3 GB
        osmConfig.tileFileSystemCacheTrimBytes = 2L * 1024L * 1024L * 1024L // 2 GB
        osmConfig.expirationExtendedDuration = 1000L * 60L * 60L * 24L * 365L * 10L // 10 years (never expires)
    }

    // React to auto-zoom events
    LaunchedEffect(Unit) {
        viewModel.zoomToExtentEvent.collect { extent ->
            val map = mapViewRef ?: return@collect
            map.zoomToBoundingBox(
                org.osmdroid.util.BoundingBox(extent.maxLat, extent.maxLon, extent.minLat, extent.minLon),
                true,
                60
            )
            viewModel.onViewportChanged(
                minLat = extent.minLat,
                maxLat = extent.maxLat,
                minLon = extent.minLon,
                maxLon = extent.maxLon,
                zoom = map.zoomLevelDouble
            )
        }
    }

    // Update vector overlay elements
    LaunchedEffect(visibleElements, selectedTarget) {
        val map = mapViewRef
        val zoom = map?.zoomLevelDouble ?: 9.0
        val selId = selectedTarget?.feature?.id
        vectorOverlay.updateElements(visibleElements, zoom, selId)
        map?.postInvalidate()
    }

    // Measurement mode toggle
    LaunchedEffect(isMeasurementMode) {
        measurementOverlay.isActive = isMeasurementMode
        mapViewRef?.postInvalidate()
    }

    // Basemap toggle
    LaunchedEffect(currentBasemap) {
        val map = mapViewRef ?: return@LaunchedEffect
        when (currentBasemap) {
            BasemapType.SATELLITE -> map.setTileSource(GisTileSources.ESRI_SATELLITE)
            BasemapType.STREETS -> map.setTileSource(TileSourceFactory.MAPNIK)
            BasemapType.TOPO -> map.setTileSource(GisTileSources.OPEN_TOPO)
            BasemapType.OFFLINE_MBTILES -> map.setTileSource(GisTileSources.ESRI_SATELLITE)
        }
        map.invalidate()
    }

    // GPS Tracking Mode toggle
    LaunchedEffect(gpsTrackingMode) {
        val map = mapViewRef ?: return@LaunchedEffect
        val locationOverlay = myLocationOverlayRef ?: return@LaunchedEffect
        when (gpsTrackingMode) {
            GpsTrackingMode.OFF -> {
                locationOverlay.disableFollowLocation()
                locationOverlay.disableMyLocation()
            }
            GpsTrackingMode.FOLLOW -> {
                locationOverlay.enableMyLocation()
                locationOverlay.enableFollowLocation()
            }
            GpsTrackingMode.FOLLOW_BEARING -> {
                locationOverlay.enableMyLocation()
                locationOverlay.enableFollowLocation()
                val loc = locationOverlay.myLocation
                if (loc != null) {
                    map.controller.animateTo(loc)
                }
            }
        }
    }

    // Selected target animation
    LaunchedEffect(selectedTarget) {
        val target = selectedTarget
        val map = mapViewRef
        if (target != null && map != null) {
            val geoPoint = GeoPoint(target.latitude, target.longitude)
            map.controller.animateTo(geoPoint, target.zoomLevel, 600L)

            map.overlays.removeAll { it is Marker }
            val marker = Marker(map).apply {
                position = geoPoint
                title = target.title
                subDescription = target.subtitle
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            map.overlays.add(marker)
            map.postInvalidate()

            kotlinx.coroutines.delay(650L)
            val bbox = map.boundingBox
            if (bbox != null) {
                viewModel.onViewportChanged(
                    minLat = bbox.latSouth,
                    maxLat = bbox.latNorth,
                    minLon = bbox.lonWest,
                    maxLon = bbox.lonEast,
                    zoom = target.zoomLevel
                )
            }
        }
    }

    // GPKG File Picker
    val gpkgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            var displayName = "BASE.gpkg"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        displayName = cursor.getString(nameIndex)
                    }
                }
            }
            viewModel.importGpkg(it, displayName)
        }
    }

    // Photo Attachment Picker
    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val target = selectedTarget ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            runCatching {
                val photosDir = File(context.filesDir, "photos")
                if (!photosDir.exists()) photosDir.mkdirs()
                val photoFile = File(photosDir, "asset_${target.feature?.id ?: System.currentTimeMillis()}_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(photoFile).use { output ->
                        input.copyTo(output)
                    }
                }
                viewModel.addAssetPhoto(target.feature?.id ?: 0L, photoFile.absolutePath)
                Toast.makeText(context, "Foto anexada com sucesso!", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "Erro ao anexar foto: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler {
        when {
            previewPhotoPath != null -> previewPhotoPath = null
            selectedTarget != null -> viewModel.clearSelection()
            isMeasurementMode -> viewModel.toggleMeasurementMode()
            isEditMode -> viewModel.toggleEditMode()
            else -> onBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. OsmDroid Hardware-Accelerated MapView
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setMultiTouchControls(true)
                    isTilesScaledToDpi = true
                    setTileSource(GisTileSources.ESRI_SATELLITE)
                    controller.setZoom(8.5)
                    controller.setCenter(GeoPoint(-10.83, -63.28))

                    // Location Overlay
                    val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this).apply {
                        setDrawAccuracyEnabled(true)
                    }
                    overlays.add(locationOverlay)
                    myLocationOverlayRef = locationOverlay

                    // Vector and Measurement overlays
                    overlays.add(vectorOverlay)
                    overlays.add(measurementOverlay)

                    addOnFirstLayoutListener { v, _, _, _, _ ->
                        val map = v as? MapView ?: return@addOnFirstLayoutListener
                        val extent = viewModel.datasetExtent.value
                        if (extent != null) {
                            map.zoomToBoundingBox(
                                org.osmdroid.util.BoundingBox(extent.maxLat, extent.maxLon, extent.minLat, extent.minLon),
                                false,
                                50
                            )
                            viewModel.onViewportChanged(
                                minLat = extent.minLat,
                                maxLat = extent.maxLat,
                                minLon = extent.minLon,
                                maxLon = extent.maxLon,
                                zoom = map.zoomLevelDouble
                            )
                        } else {
                            val bbox = map.boundingBox
                            if (bbox != null) {
                                viewModel.onViewportChanged(
                                    minLat = bbox.latSouth,
                                    maxLat = bbox.latNorth,
                                    minLon = bbox.lonWest,
                                    maxLon = bbox.lonEast,
                                    zoom = map.zoomLevelDouble
                                )
                            }
                        }
                    }

                    addMapListener(object : org.osmdroid.events.MapListener {
                        override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                            updateViewport()
                            return false
                        }
                        override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                            updateViewport()
                            return false
                        }
                        private fun updateViewport() {
                            val center = mapCenter
                            if (center != null) {
                                centerLat = center.latitude
                                centerLon = center.longitude
                            }
                            val bbox = boundingBox ?: return
                            viewModel.onViewportChanged(
                                minLat = bbox.latSouth,
                                maxLat = bbox.latNorth,
                                minLon = bbox.lonWest,
                                maxLon = bbox.lonEast,
                                zoom = zoomLevelDouble
                            )
                        }
                    })
                    mapViewRef = this
                }
            },
            update = { map ->
                mapViewRef = map
            }
        )

        // 2. QField Precision Reticle / Crosshairs Overlay
        if (isReticleVisible || isMeasurementMode || isEditMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
            ) {
                // Crosshairs lines
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val reticleRadius = 24.dp.toPx()
                    val lineLength = 36.dp.toPx()

                    // Center ring
                    drawCircle(
                        color = androidx.compose.ui.graphics.Color(0xFF00E5FF),
                        radius = reticleRadius,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                    )
                    drawCircle(
                        color = androidx.compose.ui.graphics.Color.White,
                        radius = 3.dp.toPx()
                    )

                    // Cross arms
                    drawLine(
                        color = androidx.compose.ui.graphics.Color(0xFF00E5FF),
                        start = androidx.compose.ui.geometry.Offset(cx, cy - reticleRadius - lineLength),
                        end = androidx.compose.ui.geometry.Offset(cx, cy - reticleRadius),
                        strokeWidth = 3.dp.toPx()
                    )
                    drawLine(
                        color = androidx.compose.ui.graphics.Color(0xFF00E5FF),
                        start = androidx.compose.ui.geometry.Offset(cx, cy + reticleRadius),
                        end = androidx.compose.ui.geometry.Offset(cx, cy + reticleRadius + lineLength),
                        strokeWidth = 3.dp.toPx()
                    )
                    drawLine(
                        color = androidx.compose.ui.graphics.Color(0xFF00E5FF),
                        start = androidx.compose.ui.geometry.Offset(cx - reticleRadius - lineLength, cy),
                        end = androidx.compose.ui.geometry.Offset(cx - reticleRadius, cy),
                        strokeWidth = 3.dp.toPx()
                    )
                    drawLine(
                        color = androidx.compose.ui.graphics.Color(0xFF00E5FF),
                        start = androidx.compose.ui.geometry.Offset(cx + reticleRadius, cy),
                        end = androidx.compose.ui.geometry.Offset(cx + reticleRadius + lineLength, cy),
                        strokeWidth = 3.dp.toPx()
                    )
                }
            }
        }

        // 3. Floating Top Bar: Search, Mode Badges & Fast Actions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }

                    TextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            isSearchFocused = it.isNotBlank()
                            viewModel.search(it)
                        },
                        placeholder = {
                            Text(
                                "Buscar ativo, código, lat/lon ou UTM",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            isSearchFocused = false
                            viewModel.search("")
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Limpar busca")
                        }
                    }

                    IconButton(onClick = { gpkgLauncher.launch("*/*") }) {
                        Icon(Icons.Filled.FileUpload, contentDescription = "Importar GPKG")
                    }
                }
            }

            // Dropdown Search Results Overlay
            AnimatedVisibility(visible = isSearchFocused && state is GisState.SearchResults) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .heightIn(max = 350.dp)
                ) {
                    val searchResult = (state as? GisState.SearchResults)?.result
                    if (searchResult != null) {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            searchResult.coordinateMatch?.let { coord ->
                                item {
                                    ListItem(
                                        headlineContent = {
                                            Text(coord.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        },
                                        supportingContent = { Text(coord.subtitle) },
                                        leadingContent = {
                                            Icon(
                                                imageVector = if (coord.isUtm) Icons.Filled.Explore else Icons.Filled.LocationOn,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        modifier = Modifier.clickable {
                                            viewModel.selectItem(coord)
                                            isSearchFocused = false
                                        }
                                    )
                                    HorizontalDivider()
                                }
                            }

                            if (searchResult.features.isEmpty() && searchResult.coordinateMatch == null) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        Text("Nenhum elemento encontrado para esta busca.", color = Color.Gray)
                                    }
                                }
                            } else {
                                items(searchResult.features) { featureItem ->
                                    ListItem(
                                        headlineContent = {
                                            Text(featureItem.title, fontWeight = FontWeight.SemiBold)
                                        },
                                        supportingContent = {
                                            Text(featureItem.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        },
                                        leadingContent = {
                                            Icon(
                                                imageVector = Icons.Filled.ElectricBolt,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary
                                            )
                                        },
                                        modifier = Modifier.clickable {
                                            viewModel.selectItem(featureItem)
                                            isSearchFocused = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Status Pills Row: Elements Count, Edit Mode Badge, Measurement Badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (visibleElements.isNotEmpty() && !isSearchFocused) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        shadowElevation = 3.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00F200))
                            )
                            Text(
                                text = "${visibleElements.size} ativos",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (isEditMode) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shadowElevation = 3.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                            Text(
                                text = "Modo de Edição QField",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                if (isMeasurementMode) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFFF9100),
                        shadowElevation = 3.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.Straighten, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.Black)
                            Text(
                                text = "Régua QField",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.Black
                            )
                        }
                    }
                }

                // 100% Offline indicator badge
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                    shadowElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.CloudDone, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF00E676))
                        Text(
                            text = "Cache Offline Ativo",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Progress Banner during Import
            if (state is GisState.Importing) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        when (val p = (state as GisState.Importing).progress) {
                            is ImportProgress.CopyingFile -> Text("Copiando arquivo GPKG...")
                            is ImportProgress.Indexing -> Text("Indexando ativos... ${p.featuresImported} elementos")
                            is ImportProgress.Completed -> Text("Importação concluída! ${p.totalFeatures} elementos indexados.")
                        }
                    }
                }
            }
        }

        // 4. Center Coordinate HUD (when Reticle is visible)
        if (isReticleVisible || isEditMode) {
            val centerUtm = remember(centerLat, centerLon) {
                runCatching {
                    UtmConverter.geoToUtm(GeoCoordinate(centerLat, centerLon))
                }.getOrNull()
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (selectedTarget != null || isMeasurementMode) 260.dp else 90.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column {
                        Text(
                            "%.6f, %.6f".format(Locale.US, centerLat, centerLon),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        centerUtm?.let {
                            Text(
                                "${it.zone}S %.0f %.0f".format(Locale.US, it.easting, it.northing),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            val utmStr = centerUtm?.let { "${it.zone}S %.0f %.0f".format(Locale.US, it.easting, it.northing) }
                            GisNavigationHelper.copyCoordinatesToClipboard(context, centerLat, centerLon, utmStr)
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar coordenadas", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // 5. Floating Action Buttons (Right Side Toolbar)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = if (selectedTarget != null || isMeasurementMode) 270.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Edit Mode Toggle (Lápis de Edição QField)
            FloatingActionButton(
                onClick = viewModel::toggleEditMode,
                containerColor = if (isEditMode) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
                contentColor = if (isEditMode) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(46.dp)
            ) {
                Icon(Icons.Filled.Edit, contentDescription = "Modo de Edição")
            }

            // Measurement Tool (Régua QField)
            FloatingActionButton(
                onClick = viewModel::toggleMeasurementMode,
                containerColor = if (isMeasurementMode) Color(0xFFFF9100) else MaterialTheme.colorScheme.surface,
                contentColor = if (isMeasurementMode) Color.Black else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(46.dp)
            ) {
                Icon(Icons.Filled.Straighten, contentDescription = "Régua de Medição")
            }

            // Reticle / Precision Crosshairs Toggle
            SmallFloatingActionButton(
                onClick = viewModel::toggleReticle,
                containerColor = if (isReticleVisible) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                contentColor = if (isReticleVisible) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.Filled.FilterCenterFocus, contentDescription = "Mira de Precisão")
            }

            // GPS Tracking FAB
            FloatingActionButton(
                onClick = viewModel::cycleGpsTrackingMode,
                containerColor = when (gpsTrackingMode) {
                    GpsTrackingMode.OFF -> MaterialTheme.colorScheme.surface
                    GpsTrackingMode.FOLLOW -> MaterialTheme.colorScheme.primary
                    GpsTrackingMode.FOLLOW_BEARING -> Color(0xFF00E676)
                },
                contentColor = when (gpsTrackingMode) {
                    GpsTrackingMode.OFF -> MaterialTheme.colorScheme.onSurface
                    else -> Color.White
                },
                modifier = Modifier.size(46.dp)
            ) {
                Icon(
                    imageVector = when (gpsTrackingMode) {
                        GpsTrackingMode.OFF -> Icons.Filled.MyLocation
                        GpsTrackingMode.FOLLOW -> Icons.Filled.GpsFixed
                        GpsTrackingMode.FOLLOW_BEARING -> Icons.Filled.Navigation
                    },
                    contentDescription = "GPS Localização"
                )
            }

            // Layer Control Button (Camadas QField)
            FloatingActionButton(
                onClick = { showLayersSheet = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(46.dp)
            ) {
                Icon(Icons.Filled.Layers, contentDescription = "Controle de Camadas")
            }

            // Zoom to Extent ("Ver Todo o Projeto")
            SmallFloatingActionButton(
                onClick = viewModel::zoomToProjectExtent,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.Filled.ZoomOutMap, contentDescription = "Ver Todo o Projeto")
            }

            // Basemap Switcher FAB
            ExtendedFloatingActionButton(
                onClick = { showBasemapDialog = true },
                icon = {
                    Icon(
                        imageVector = when (currentBasemap) {
                            BasemapType.SATELLITE -> Icons.Filled.SatelliteAlt
                            BasemapType.STREETS -> Icons.Filled.Map
                            BasemapType.TOPO -> Icons.Filled.Terrain
                            BasemapType.OFFLINE_MBTILES -> Icons.Filled.SdCard
                        },
                        contentDescription = "Alternar Basemap"
                    )
                },
                text = { Text(currentBasemap.displayName.substringBefore(" ")) },
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 6. QField Measurement Mode HUD & Controls
        if (isMeasurementMode) {
            Card(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Straighten, contentDescription = null, tint = Color(0xFFFF9100))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Régua de Medição QField", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = viewModel::toggleMeasurementMode) {
                            Icon(Icons.Filled.Close, contentDescription = "Fechar régua")
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Distância Total", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text(
                                GisMeasurementOverlay.formatDistance(measureDist),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (measurePointsCount >= 3) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Área Polígono", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                Text(
                                    GisMeasurementOverlay.formatArea(measureArea),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF9100)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Button: Add Point at Center Reticle
                        Button(
                            onClick = {
                                val map = mapViewRef ?: return@Button
                                measurementOverlay.addPoint(GeoPoint(centerLat, centerLon), map)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.AddLocationAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ponto na Mira")
                        }

                        // Button: Undo Last Point
                        OutlinedButton(
                            onClick = { measurementOverlay.removeLastPoint(mapViewRef) },
                            enabled = measurePointsCount > 0
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Desfazer ponto")
                        }

                        // Button: Clear All Points
                        OutlinedButton(
                            onClick = { measurementOverlay.clear(mapViewRef) },
                            enabled = measurePointsCount > 0
                        ) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Limpar medição")
                        }
                    }
                }
            }
        }

        // 7. Edit Mode: "Adicionar Ativo na Mira" Bottom Action
        if (isEditMode && !isMeasurementMode && selectedTarget == null) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            ) {
                Button(
                    onClick = { showAddFeatureDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Adicionar Ativo na Mira de Precisão", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 8. Layer Control Modal Bottom Sheet (Camadas do QField)
        if (showLayersSheet) {
            ModalBottomSheet(
                onDismissRequest = { showLayersSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Camadas do GeoPackage",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = viewModel::zoomToProjectExtent) {
                            Icon(Icons.Filled.ZoomOutMap, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ver Tudo")
                        }
                    }

                    Text(
                        text = "Ative ou oculte as camadas da rede elétrica de Rondônia:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    HorizontalDivider()

                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                        items(availableLayers) { layer ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clickable { viewModel.toggleLayer(layer.type) },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(
                                                runCatching { Color(android.graphics.Color.parseColor(layer.colorHex)) }
                                                    .getOrDefault(Color.Gray)
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = layer.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "${layer.featureCount} elementos",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray
                                        )
                                    }
                                }

                                Switch(
                                    checked = activeLayerFilter.contains(layer.type),
                                    onCheckedChange = { viewModel.toggleLayer(layer.type) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 9. Basemap Switcher Dialog
        if (showBasemapDialog) {
            AlertDialog(
                onDismissRequest = { showBasemapDialog = false },
                title = { Text("Selecione a Camada de Fundo (Basemap)") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BasemapType.values().forEach { basemap ->
                            val isAvailable = basemap != BasemapType.OFFLINE_MBTILES || offlineMbtilesPath != null
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = isAvailable) {
                                        viewModel.setBasemap(basemap)
                                        showBasemapDialog = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = currentBasemap == basemap,
                                    onClick = {
                                        if (isAvailable) {
                                            viewModel.setBasemap(basemap)
                                            showBasemapDialog = false
                                        }
                                    },
                                    enabled = isAvailable
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = basemap.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (currentBasemap == basemap) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (basemap == BasemapType.OFFLINE_MBTILES && !isAvailable) {
                                        Text("Nenhum arquivo .mbtiles encontrado em Download", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showBasemapDialog = false }) {
                        Text("Fechar")
                    }
                }
            )
        }

        // 10. Asset Details Card (QField Inspection & Google Maps Route)
        selectedTarget?.let { target ->
            Card(
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .heightIn(max = 280.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header: Title & Close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = target.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            target.subtitle?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Filled.Close, contentDescription = "Fechar detalhes")
                        }
                    }

                    // Key Action: Traçar Rota no Google Maps
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            GisNavigationHelper.launchGoogleMapsNavigation(
                                context = context,
                                latitude = target.latitude,
                                longitude = target.longitude,
                                title = target.title
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Directions, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Traçar Rota no Google Maps", fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    // Actions Row: Copiar Coord, Anexar Foto, Editar, Excluir
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val utm = runCatching {
                                    UtmConverter.geoToUtm(GeoCoordinate(target.latitude, target.longitude))
                                }.getOrNull()
                                val utmStr = utm?.let { "${it.zone}S %.0f %.0f".format(Locale.US, it.easting, it.northing) }
                                GisNavigationHelper.copyCoordinatesToClipboard(context, target.latitude, target.longitude, utmStr)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Coordenadas")
                        }

                        OutlinedButton(
                            onClick = { photoLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Foto")
                        }

                        if (isEditMode) {
                            IconButton(onClick = { showEditAttrsDialog = true }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Editar Atributos", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { showDeleteConfirmDialog = true }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Excluir Ativo", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Coordinates display: Lat/Long and UTM
                    val utm = remember(target.latitude, target.longitude) {
                        runCatching {
                            UtmConverter.geoToUtm(GeoCoordinate(target.latitude, target.longitude))
                        }.getOrNull()
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Coordenadas GEO:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text(
                                "%.6f, %.6f".format(Locale.US, target.latitude, target.longitude),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        utm?.let {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Coordenadas UTM:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                Text(
                                    "${it.zone}S %.0f %.0f".format(Locale.US, it.easting, it.northing),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Attached Photos Gallery
                    val photos = remember(target.feature?.id, assetPhotosVersion) {
                        target.feature?.id?.let { viewModel.getAssetPhotos(it) } ?: emptyList()
                    }

                    if (photos.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Fotos do Ativo:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            items(photos) { photoPath ->
                                val bitmap = remember(photoPath) {
                                    runCatching { BitmapFactory.decodeFile(photoPath) }.getOrNull()
                                }
                                bitmap?.let { b ->
                                    Image(
                                        bitmap = b.asImageBitmap(),
                                        contentDescription = "Foto do ativo",
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                                            .clickable { previewPhotoPath = photoPath },
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }

                    // Properties from metadataJson
                    target.feature?.let { f ->
                        Spacer(modifier = Modifier.height(8.dp))
                        val properties = remember(f.metadataJson) {
                            runCatching {
                                val obj = JSONObject(f.metadataJson)
                                val keys = obj.keys()
                                val list = mutableListOf<Pair<String, String>>()
                                while (keys.hasNext()) {
                                    val k = keys.next()
                                    val v = obj.optString(k)
                                    if (v.isNotBlank() && v != "null" && v != "None" && !k.equals("fid", true) && !k.equals("geom", true)) {
                                        list.add(k to v)
                                    }
                                }
                                list.take(10)
                            }.getOrDefault(emptyList())
                        }

                        if (properties.isNotEmpty()) {
                            Text("Atributos do Elemento:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            properties.forEach { (key, value) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(key, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 11. Add Feature Dialog (Modo de Edição)
        if (showAddFeatureDialog) {
            var selectedLayer by remember { mutableStateOf(availableLayers.firstOrNull()?.name ?: "Poste de Distribuição") }
            var assetCode by remember { mutableStateOf("") }
            var assetNotes by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showAddFeatureDialog = false },
                title = { Text("Adicionar Ativo na Mira") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Local: %.6f, %.6f".format(Locale.US, centerLat, centerLon),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text("Selecione a Camada:", style = MaterialTheme.typography.labelMedium)
                        LazyColumn(modifier = Modifier.heightIn(max = 140.dp)) {
                            items(availableLayers) { layer ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { selectedLayer = layer.name }
                                        .padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = selectedLayer == layer.name, onClick = { selectedLayer = layer.name })
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(layer.name, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = assetCode,
                            onValueChange = { assetCode = it },
                            label = { Text("Código / Número Operativo") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = assetNotes,
                            onValueChange = { assetNotes = it },
                            label = { Text("Observações") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (assetCode.isNotBlank()) {
                                viewModel.addPointFeature(
                                    layerName = selectedLayer,
                                    latitude = centerLat,
                                    longitude = centerLon,
                                    code = assetCode.trim(),
                                    extraAttributes = if (assetNotes.isNotBlank()) mapOf("obs" to assetNotes.trim()) else emptyMap()
                                )
                                showAddFeatureDialog = false
                                Toast.makeText(context, "Ativo adicionado com sucesso!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Salvar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddFeatureDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // 12. Edit Attributes Dialog
        if (showEditAttrsDialog && selectedTarget != null) {
            val target = selectedTarget!!
            var editCode by remember { mutableStateOf(target.title) }

            AlertDialog(
                onDismissRequest = { showEditAttrsDialog = false },
                title = { Text("Editar Atributos do Ativo") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Camada: ${target.subtitle}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        OutlinedTextField(
                            value = editCode,
                            onValueChange = { editCode = it },
                            label = { Text("Código / Nome") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            target.feature?.let { f ->
                                viewModel.updateFeatureAttributes(
                                    layerName = f.sourceLayer,
                                    fid = f.id,
                                    attributes = mapOf("codigo" to editCode.trim(), "name" to editCode.trim(), "numero_operativo" to editCode.trim())
                                )
                            }
                            showEditAttrsDialog = false
                            Toast.makeText(context, "Atributos atualizados!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Salvar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditAttrsDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // 13. Delete Confirmation Dialog
        if (showDeleteConfirmDialog && selectedTarget != null) {
            val target = selectedTarget!!
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text("Excluir Ativo") },
                text = { Text("Deseja realmente remover o elemento '${target.title}' do GeoPackage?") },
                confirmButton = {
                    Button(
                        onClick = {
                            target.feature?.let { f ->
                                viewModel.deleteFeature(f.sourceLayer, f.id)
                            }
                            showDeleteConfirmDialog = false
                            Toast.makeText(context, "Elemento excluído com sucesso!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Excluir")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // 14. Fullscreen Photo Preview
        previewPhotoPath?.let { path ->
            Dialog(onDismissRequest = { previewPhotoPath = null }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    modifier = Modifier.fillMaxWidth().height(420.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val bitmap = remember(path) {
                            runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Foto ampliada",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                        IconButton(
                            onClick = { previewPhotoPath = null },
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Fechar", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}
