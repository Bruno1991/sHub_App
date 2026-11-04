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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saile.shub.core.geo.GeoCoordinate
import com.saile.shub.core.geo.UtmConverter
import com.saile.shub.gis.domain.GisElementType
import com.saile.shub.gis.usecase.ImportProgress
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlinx.coroutines.launch

// QField Signature Palette
val QfGreenPrimary = Color(0xFF4E731B)
val QfGreenLight = Color(0xFF7AA136)
val QfGreenDark = Color(0xFF385313)
val QfAccentOrange = Color(0xFFFF9100)
val QfSurfaceDark = Color(0xFF222621)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GisScreen(
    onBack: () -> Unit,
    viewModel: GisViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val state by viewModel.state.collectAsState()
    val interactionMode by viewModel.interactionMode.collectAsState()
    val activeEditLayer by viewModel.activeEditLayer.collectAsState()
    val isLayersExpanded by viewModel.isLayersExpanded.collectAsState()
    val selectedTarget by viewModel.selectedTarget.collectAsState()
    val visibleElements by viewModel.visibleElements.collectAsState()
    val datasetExtent by viewModel.datasetExtent.collectAsState()
    val availableLayers by viewModel.availableLayers.collectAsState()
    val activeLayerFilter by viewModel.activeLayerFilter.collectAsState()

    val isReticleVisible by viewModel.isReticleVisible.collectAsState()
    val isMeasurementMode by viewModel.isMeasurementMode.collectAsState()
    val gpsTrackingMode by viewModel.gpsTrackingMode.collectAsState()
    val assetPhotosVersion by viewModel.assetPhotosVersion.collectAsState()

    // GPS & GNSS Tracker powered by mature sFoto location utilities
    val locationTracker = remember { GisLocationTracker(context) }
    val positioningInfo by locationTracker.positioningInfo.collectAsState()
    val compassHeading by locationTracker.compassHeading.collectAsState()

    LaunchedEffect(Unit) {
        locationTracker.startTracking()
    }
    DisposableEffect(Unit) {
        onDispose { locationTracker.stopTracking() }
    }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    var showPositioningDialog by remember { mutableStateOf(false) }
    var showProjectInfoDialog by remember { mutableStateOf(false) }
    var showActiveLayerSelector by remember { mutableStateOf(false) }

    // Measurement HUD state
    var measureDist by remember { mutableDoubleStateOf(0.0) }
    var measureArea by remember { mutableDoubleStateOf(0.0) }
    var measurePointsCount by remember { mutableIntStateOf(0) }

    // Map Center coordinates for reticle & scale bar
    var centerLat by remember { mutableDoubleStateOf(-10.828) }
    var centerLon by remember { mutableDoubleStateOf(-63.284) }
    var currentZoom by remember { mutableDoubleStateOf(8.5) }

    // Dialog states
    var showAddFeatureDialog by remember { mutableStateOf(false) }
    var showEditAttrsDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var previewPhotoPath by remember { mutableStateOf<String?>(null) }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

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

    // Initialize OsmDroid with High-Performance Cache
    LaunchedEffect(Unit) {
        val osmConfig = Configuration.getInstance()
        osmConfig.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        osmConfig.userAgentValue = context.packageName
        osmConfig.tileFileSystemCacheMaxBytes = 3L * 1024L * 1024L * 1024L // 3 GB
        osmConfig.tileFileSystemCacheTrimBytes = 2L * 1024L * 1024L * 1024L // 2 GB
        osmConfig.expirationExtendedDuration = 1000L * 60L * 60L * 24L * 365L * 10L // 10 years
    }

    // Auto-zoom to dataset extent
    LaunchedEffect(datasetExtent, mapViewRef) {
        val extent = datasetExtent ?: return@LaunchedEffect
        val map = mapViewRef ?: return@LaunchedEffect
        map.zoomToBoundingBox(
            org.osmdroid.util.BoundingBox(extent.maxLat, extent.maxLon, extent.minLat, extent.minLon),
            false,
            50
        )
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

    // Update overlay elements
    LaunchedEffect(visibleElements, selectedTarget, mapViewRef) {
        val map = mapViewRef
        val zoom = map?.zoomLevelDouble ?: currentZoom
        val selId = selectedTarget?.feature?.id
        vectorOverlay.updateElements(visibleElements, zoom, selId)
        map?.postInvalidate()
    }

    // Measurement mode toggle
    LaunchedEffect(isMeasurementMode) {
        measurementOverlay.isActive = isMeasurementMode
        mapViewRef?.postInvalidate()
    }

    // GPS Tracking Follower
    LaunchedEffect(gpsTrackingMode, positioningInfo, compassHeading) {
        val map = mapViewRef ?: return@LaunchedEffect
        val pos = positioningInfo ?: return@LaunchedEffect
        when (gpsTrackingMode) {
            GpsTrackingMode.OFF -> { /* Free pan */ }
            GpsTrackingMode.FOLLOW -> {
                map.controller.animateTo(GeoPoint(pos.latitude, pos.longitude))
            }
            GpsTrackingMode.FOLLOW_BEARING -> {
                map.controller.animateTo(GeoPoint(pos.latitude, pos.longitude))
                map.mapOrientation = -compassHeading
            }
        }
    }

    // Target Selection Animation
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

    // File Picker for importing another GPKG
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
            drawerState.isOpen -> coroutineScope.launch { drawerState.close() }
            previewPhotoPath != null -> previewPhotoPath = null
            selectedTarget != null -> viewModel.clearSelection()
            isMeasurementMode -> viewModel.toggleMeasurementMode()
            interactionMode == GisInteractionMode.DIGITIZE -> viewModel.setInteractionMode(GisInteractionMode.BROWSE)
            else -> onBack()
        }
    }

    // QField Dashboard Left Navigation Drawer
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(320.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // QField Green Header Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(QfGreenPrimary)
                            .statusBarsPadding()
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Map, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "QField - sGIS",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Projeto: BASE.gpkg",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White)
                            )
                            Text(
                                text = "SIRGAS 2000 / UTM 20S (EPSG:31980 / 4674)",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFE8F5E9))
                            )
                        }
                    }

                    // Layer Tree Header with Expand / Collapse
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Camadas e Legenda",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        IconButton(onClick = viewModel::toggleLayersExpanded) {
                            Icon(
                                imageVector = if (isLayersExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = "Expandir / Recolher Camadas"
                            )
                        }
                    }

                    HorizontalDivider()

                    // Layers List with Authentic QField Symbology and Feature Counts
                    if (isLayersExpanded) {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        ) {
                            items(availableLayers) { layer ->
                                val isSelectedEditLayer = activeEditLayer == layer.name
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelectedEditLayer) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.setActiveEditLayer(layer.name)
                                        }
                                        .padding(vertical = 2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = activeLayerFilter.contains(layer.type),
                                            onCheckedChange = { viewModel.toggleLayer(layer.type) },
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))

                                        // Symbology Icon / Color Swatch
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(if (layer.type == GisElementType.SPAN_AT || layer.type == GisElementType.SPAN_MT || layer.type == GisElementType.SPAN_BT) RoundedCornerShape(2.dp) else CircleShape)
                                                .background(
                                                    runCatching { Color(android.graphics.Color.parseColor(layer.colorHex)) }.getOrDefault(Color.Gray)
                                                )
                                                .border(1.dp, Color.DarkGray, if (layer.type == GisElementType.SPAN_AT || layer.type == GisElementType.SPAN_MT || layer.type == GisElementType.SPAN_BT) RoundedCornerShape(2.dp) else CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = layer.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isSelectedEditLayer) FontWeight.Bold else FontWeight.Medium),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${layer.featureCount} elementos",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.Gray
                                            )
                                        }

                                        if (isSelectedEditLayer) {
                                            Icon(
                                                imageVector = Icons.Filled.Edit,
                                                contentDescription = "Camada ativa para edição",
                                                tint = QfGreenPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    HorizontalDivider()

                    // Bottom Drawer Actions
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Filled.FileUpload, contentDescription = null) },
                            label = { Text("Importar Outro GeoPackage") },
                            selected = false,
                            onClick = {
                                coroutineScope.launch { drawerState.close() }
                                gpkgLauncher.launch("*/*")
                            }
                        )
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                            label = { Text("Informações do Projeto") },
                            selected = false,
                            onClick = {
                                coroutineScope.launch { drawerState.close() }
                                showProjectInfoDialog = true
                            }
                        )
                    }
                }
            }
        }
    ) {
        // Main Screen Scaffold
        Scaffold(
            topBar = {
                // Authentic QField Topbar
                Surface(
                    color = QfGreenPrimary,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.statusBarsPadding()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Hamburger Menu Icon
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Painel QField", tint = Color.White)
                            }

                            // Mode Switcher Toggle: Navegar vs Digitalizar
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (interactionMode == GisInteractionMode.DIGITIZE) Color(0xFFD32F2F) else QfGreenDark,
                                modifier = Modifier
                                    .clickable { viewModel.toggleInteractionMode() }
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (interactionMode == GisInteractionMode.DIGITIZE) Icons.Filled.Edit else Icons.Filled.Explore,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = interactionMode.label,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Project Name
                            Text(
                                text = "BASE.gpkg",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            // Search Action
                            IconButton(onClick = { isSearchFocused = !isSearchFocused }) {
                                Icon(Icons.Filled.Search, contentDescription = "Buscar ativo", tint = Color.White)
                            }

                            // GNSS Positioning Status Pill (QField GNSS Widget)
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = QfGreenDark,
                                modifier = Modifier
                                    .clickable { showPositioningDialog = true }
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (positioningInfo != null) Color(0xFF00E676) else Color.Red)
                                    )
                                    Icon(Icons.Filled.GpsFixed, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = positioningInfo?.accuracyText ?: "± --",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                    )
                                }
                            }

                            // Overflow Menu
                            IconButton(onClick = { showProjectInfoDialog = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções", tint = Color.White)
                            }
                        }

                        // Expandable Search Bar
                        AnimatedVisibility(visible = isSearchFocused) {
                            Surface(
                                color = Color.White,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp),
                                shadowElevation = 4.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Search, contentDescription = null, tint = Color.Gray)
                                    TextField(
                                        value = searchQuery,
                                        onValueChange = {
                                            searchQuery = it
                                            viewModel.search(it)
                                        },
                                        placeholder = { Text("Buscar ativo, código, lat/lon ou UTM") },
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent
                                        ),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        isSearchFocused = false
                                        viewModel.search("")
                                    }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Fechar busca", tint = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // 1. Fullscreen Map Canvas
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setMultiTouchControls(true)
                            isTilesScaledToDpi = true
                            setTileSource(TileSourceFactory.MAPNIK)
                            controller.setZoom(8.5)
                            controller.setCenter(GeoPoint(-10.828, -63.284))

                            overlays.add(vectorOverlay)
                            overlays.add(measurementOverlay)

                            addOnFirstLayoutListener { v, _, _, _, _ ->
                                val map = v as? MapView ?: return@addOnFirstLayoutListener
                                mapViewRef = map
                                val extent = viewModel.datasetExtent.value
                                if (extent != null) {
                                    map.zoomToBoundingBox(
                                        org.osmdroid.util.BoundingBox(extent.maxLat, extent.maxLon, extent.minLat, extent.minLon),
                                        false,
                                        40
                                    )
                                }
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

                            addMapListener(object : org.osmdroid.events.MapListener {
                                override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                                    update()
                                    return false
                                }
                                override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                                    update()
                                    return false
                                }
                                private fun update() {
                                    val c = mapCenter
                                    if (c != null) {
                                        centerLat = c.latitude
                                        centerLon = c.longitude
                                    }
                                    currentZoom = zoomLevelDouble
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

                // 2. QField Precision Reticle / Crosshair (Always accurate at map center)
                if (isReticleVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .align(Alignment.Center)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val reticleSize = 18.dp.toPx()

                            // Outer thin ring
                            drawCircle(
                                color = Color(0xCC000000),
                                radius = reticleSize,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                            )
                            // Center dot
                            drawCircle(
                                color = Color(0xEEFF0000),
                                radius = 2.dp.toPx()
                            )
                            // Cross lines
                            drawLine(
                                color = Color(0xCC000000),
                                start = androidx.compose.ui.geometry.Offset(cx - reticleSize - 10.dp.toPx(), cy),
                                end = androidx.compose.ui.geometry.Offset(cx - reticleSize, cy),
                                strokeWidth = 1.5.dp.toPx()
                            )
                            drawLine(
                                color = Color(0xCC000000),
                                start = androidx.compose.ui.geometry.Offset(cx + reticleSize, cy),
                                end = androidx.compose.ui.geometry.Offset(cx + reticleSize + 10.dp.toPx(), cy),
                                strokeWidth = 1.5.dp.toPx()
                            )
                            drawLine(
                                color = Color(0xCC000000),
                                start = androidx.compose.ui.geometry.Offset(cx, cy - reticleSize - 10.dp.toPx()),
                                end = androidx.compose.ui.geometry.Offset(cx, cy - reticleSize),
                                strokeWidth = 1.5.dp.toPx()
                            )
                            drawLine(
                                color = Color(0xCC000000),
                                start = androidx.compose.ui.geometry.Offset(cx, cy + reticleSize),
                                end = androidx.compose.ui.geometry.Offset(cx, cy + reticleSize + 10.dp.toPx()),
                                strokeWidth = 1.5.dp.toPx()
                            )
                        }
                    }
                }

                // 3. QField Scale Bar (Bottom Left)
                GisScaleBar(
                    latitude = centerLat,
                    zoomLevel = currentZoom,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = if (interactionMode == GisInteractionMode.DIGITIZE || isMeasurementMode || selectedTarget != null) 210.dp else 24.dp)
                )

                // 4. Floating Right Toolbar (QField Side Tool Controls)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = if (interactionMode == GisInteractionMode.DIGITIZE || isMeasurementMode || selectedTarget != null) 210.dp else 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    // GPS Tracking FAB (QField MyLocation)
                    SmallFloatingActionButton(
                        onClick = viewModel::cycleGpsTrackingMode,
                        containerColor = when (gpsTrackingMode) {
                            GpsTrackingMode.OFF -> MaterialTheme.colorScheme.surface
                            GpsTrackingMode.FOLLOW -> QfGreenPrimary
                            GpsTrackingMode.FOLLOW_BEARING -> Color(0xFF00E676)
                        },
                        contentColor = when (gpsTrackingMode) {
                            GpsTrackingMode.OFF -> MaterialTheme.colorScheme.onSurface
                            else -> Color.White
                        }
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

                    // Zoom In (+)
                    SmallFloatingActionButton(
                        onClick = { mapViewRef?.controller?.zoomIn() },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Aproximar")
                    }

                    // Zoom Out (-)
                    SmallFloatingActionButton(
                        onClick = { mapViewRef?.controller?.zoomOut() },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "Afastar")
                    }

                    // Full Extent
                    SmallFloatingActionButton(
                        onClick = viewModel::zoomToProjectExtent,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Icon(Icons.Filled.ZoomOutMap, contentDescription = "Extensão Total")
                    }

                    // QField Measuring Tool (Régua)
                    SmallFloatingActionButton(
                        onClick = viewModel::toggleMeasurementMode,
                        containerColor = if (isMeasurementMode) QfAccentOrange else MaterialTheme.colorScheme.surface,
                        contentColor = if (isMeasurementMode) Color.Black else MaterialTheme.colorScheme.onSurface
                    ) {
                        Icon(Icons.Filled.Straighten, contentDescription = "Régua de Medição")
                    }
                }

                // 5. QField Confirmation Toolbar (Bottom Digitizing Bar)
                if (interactionMode == GisInteractionMode.DIGITIZE) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 12.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            // Active Layer Selector
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable { showActiveLayerSelector = true }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Layers, contentDescription = null, tint = QfGreenPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = activeEditLayer ?: "Selecione a camada",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Action Buttons: Cancel, Undo, Add Point, Save
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Cancel
                                OutlinedButton(
                                    onClick = { viewModel.setInteractionMode(GisInteractionMode.BROWSE) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Cancelar")
                                }

                                // Add Point at Reticle (+)
                                Button(
                                    onClick = { showAddFeatureDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = QfGreenPrimary),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.AddLocationAlt, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Adicionar na Mira", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 6. QField Measuring Tool Bar
                if (isMeasurementMode) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 12.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Straighten, contentDescription = null, tint = QfAccentOrange)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Régua de Medição QField", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                }
                                IconButton(onClick = viewModel::toggleMeasurementMode) {
                                    Icon(Icons.Filled.Close, contentDescription = "Fechar régua")
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Distância", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Text(
                                        GisMeasurementOverlay.formatDistance(measureDist),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = QfGreenPrimary)
                                    )
                                }
                                if (measurePointsCount >= 3) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Área Polígono", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        Text(
                                            GisMeasurementOverlay.formatArea(measureArea),
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = QfAccentOrange)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val map = mapViewRef ?: return@Button
                                        measurementOverlay.addPoint(GeoPoint(centerLat, centerLon), map)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = QfAccentOrange),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.AddLocationAlt, contentDescription = null, tint = Color.Black)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Ponto na Mira", color = Color.Black, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { measurementOverlay.removeLastPoint(mapViewRef) },
                                    enabled = measurePointsCount > 0
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Desfazer")
                                }

                                OutlinedButton(
                                    onClick = { measurementOverlay.clear(mapViewRef) },
                                    enabled = measurePointsCount > 0
                                ) {
                                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Limpar")
                                }
                            }
                        }
                    }
                }

                // 7. QField Feature Form / Inspection Sheet
                selectedTarget?.let { target ->
                    Card(
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
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
                            // Header with Layer & Title
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = target.title,
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = QfGreenPrimary)
                                    )
                                    target.subtitle?.let {
                                        Text(text = it, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                }
                                IconButton(onClick = viewModel::clearSelection) {
                                    Icon(Icons.Filled.Close, contentDescription = "Fechar")
                                }
                            }

                            // Google Maps Turn-by-Turn Route Button
                            Spacer(modifier = Modifier.height(8.dp))
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
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Filled.Directions, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Traçar Rota no Google Maps", fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            // Coordinates and Photo Actions
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val utm = runCatching { UtmConverter.geoToUtm(GeoCoordinate(target.latitude, target.longitude)) }.getOrNull()
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

                                if (interactionMode == GisInteractionMode.DIGITIZE) {
                                    IconButton(onClick = { showEditAttrsDialog = true }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Editar", tint = QfGreenPrimary)
                                    }
                                    IconButton(onClick = { showDeleteConfirmDialog = true }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Excluir", tint = Color.Red)
                                    }
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            // Attached Photos
                            val photos = remember(target.feature?.id, assetPhotosVersion) {
                                target.feature?.id?.let { viewModel.getAssetPhotos(it) } ?: emptyList()
                            }
                            if (photos.isNotEmpty()) {
                                Text("Fotos Anexadas:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    items(photos) { path ->
                                        val bmp = remember(path) { runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
                                        bmp?.let { b ->
                                            Image(
                                                bitmap = b.asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(60.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                                                    .clickable { previewPhotoPath = path },
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // Attributes Table
                            target.feature?.let { f ->
                                val props = remember(f.metadataJson) {
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
                                        list
                                    }.getOrDefault(emptyList())
                                }

                                if (props.isNotEmpty()) {
                                    Text("Atributos:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    props.forEach { (k, v) ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(v, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 8. Search Results Dropdown Overlay
                AnimatedVisibility(visible = isSearchFocused && state is GisState.SearchResults) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .heightIn(max = 350.dp)
                    ) {
                        val searchResult = (state as? GisState.SearchResults)?.result
                        if (searchResult != null) {
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                searchResult.coordinateMatch?.let { coord ->
                                    item {
                                        ListItem(
                                            headlineContent = { Text(coord.title, fontWeight = FontWeight.Bold, color = QfGreenPrimary) },
                                            supportingContent = { Text(coord.subtitle) },
                                            leadingContent = { Icon(Icons.Filled.LocationOn, contentDescription = null, tint = QfGreenPrimary) },
                                            modifier = Modifier.clickable {
                                                viewModel.selectItem(coord)
                                                isSearchFocused = false
                                            }
                                        )
                                        HorizontalDivider()
                                    }
                                }

                                items(searchResult.features) { feat ->
                                    ListItem(
                                        headlineContent = { Text(feat.title, fontWeight = FontWeight.SemiBold) },
                                        supportingContent = { Text(feat.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        leadingContent = { Icon(Icons.Filled.ElectricBolt, contentDescription = null, tint = QfGreenPrimary) },
                                        modifier = Modifier.clickable {
                                            viewModel.selectItem(feat)
                                            isSearchFocused = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 9. Import Progress Banner
                if (state is GisState.Importing) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth().padding(12.dp).align(Alignment.TopCenter)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            when (val p = (state as GisState.Importing).progress) {
                                is ImportProgress.CopyingFile -> Text("Copiando arquivo GPKG...")
                                is ImportProgress.Indexing -> Text("Indexando: ${p.featuresImported} elementos")
                                is ImportProgress.Completed -> Text("Importação concluída! ${p.totalFeatures} elementos indexados.")
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: Active Layer Selector
    if (showActiveLayerSelector) {
        AlertDialog(
            onDismissRequest = { showActiveLayerSelector = false },
            title = { Text("Selecione a Camada de Edição") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(availableLayers) { layer ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.setActiveEditLayer(layer.name)
                                    showActiveLayerSelector = false
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = activeEditLayer == layer.name, onClick = {
                                viewModel.setActiveEditLayer(layer.name)
                                showActiveLayerSelector = false
                            })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(layer.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showActiveLayerSelector = false }) { Text("Fechar") }
            }
        )
    }

    // Dialog: Positioning Information (QField GNSS Information View)
    if (showPositioningDialog) {
        val pos = positioningInfo
        AlertDialog(
            onDismissRequest = { showPositioningDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.GpsFixed, contentDescription = null, tint = QfGreenPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Posicionamento GNSS")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (pos != null) {
                        Text("Coordenadas GEO: ${pos.geoText}", fontWeight = FontWeight.Medium)
                        Text("Coordenadas UTM: ${pos.utmText}", fontWeight = FontWeight.Medium)
                        Text("Precisão: ${pos.accuracyText}", color = if ((pos.accuracyMeters ?: 99f) < 5f) Color(0xFF2E7D32) else Color(0xFFE65100))
                        pos.altitudeMeters?.let { Text("Altitude: %.1f m".format(it)) }
                        pos.speedKmh?.let { Text("Velocidade: %.1f km/h".format(it)) }
                        Text("Bússola / Azimute: %.1f°".format(compassHeading))
                        Text("Provedor: ${pos.provider}")
                    } else {
                        Text("Buscando sinal GNSS / GPS...")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pos?.let {
                            GisNavigationHelper.copyCoordinatesToClipboard(context, it.latitude, it.longitude, it.utmText)
                        }
                        showPositioningDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = QfGreenPrimary)
                ) {
                    Text("Copiar Coordenadas")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPositioningDialog = false }) { Text("Fechar") }
            }
        )
    }

    // Dialog: Project Information
    if (showProjectInfoDialog) {
        AlertDialog(
            onDismissRequest = { showProjectInfoDialog = false },
            title = { Text("Propriedades do Projeto") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Arquivo: BASE.gpkg", fontWeight = FontWeight.Bold)
                    Text("Projeção: SIRGAS 2000 / UTM 20S (EPSG 31980 / 4674)")
                    Text("Camadas Ativas: ${availableLayers.size}")
                    val totalFeatures = availableLayers.sumOf { it.featureCount }
                    Text("Total de Ativos: $totalFeatures")
                }
            },
            confirmButton = {
                Button(onClick = { showProjectInfoDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = QfGreenPrimary)) {
                    Text("OK")
                }
            }
        )
    }

    // Dialog: Add Feature at Reticle
    if (showAddFeatureDialog) {
        var code by remember { mutableStateOf("") }
        var obs by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddFeatureDialog = false },
            title = { Text("Adicionar Ativo na Mira") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Camada: ${activeEditLayer ?: "Ponto"}", fontWeight = FontWeight.Bold, color = QfGreenPrimary)
                    Text("Coordenadas: %.6f, %.6f".format(Locale.US, centerLat, centerLon), style = MaterialTheme.typography.bodySmall)

                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Código / Número Operativo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = obs,
                        onValueChange = { obs = it },
                        label = { Text("Observações") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val layer = activeEditLayer ?: availableLayers.firstOrNull()?.name ?: "Poste"
                        if (code.isNotBlank()) {
                            viewModel.addPointFeature(
                                layerName = layer,
                                latitude = centerLat,
                                longitude = centerLon,
                                code = code.trim(),
                                extraAttributes = if (obs.isNotBlank()) mapOf("obs" to obs.trim()) else emptyMap()
                            )
                            showAddFeatureDialog = false
                            Toast.makeText(context, "Ativo salvo no GeoPackage!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = QfGreenPrimary)
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFeatureDialog = false }) { Text("Cancelar") }
            }
        )
    }

    // Dialog: Edit Attributes
    if (showEditAttrsDialog && selectedTarget != null) {
        val target = selectedTarget!!
        var editCode by remember { mutableStateOf(target.title) }

        AlertDialog(
            onDismissRequest = { showEditAttrsDialog = false },
            title = { Text("Editar Atributos") },
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
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = QfGreenPrimary)
                ) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(onClick = { showEditAttrsDialog = false }) { Text("Cancelar") }
            }
        )
    }

    // Dialog: Delete Feature
    if (showDeleteConfirmDialog && selectedTarget != null) {
        val target = selectedTarget!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Excluir Ativo") },
            text = { Text("Deseja realmente remover o elemento '${target.title}' do GeoPackage?") },
            confirmButton = {
                Button(
                    onClick = {
                        target.feature?.let { f -> viewModel.deleteFeature(f.sourceLayer, f.id) }
                        showDeleteConfirmDialog = false
                        Toast.makeText(context, "Elemento excluído com sucesso!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancelar") }
            }
        )
    }

    // Fullscreen Photo Preview
    previewPhotoPath?.let { path ->
        Dialog(onDismissRequest = { previewPhotoPath = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                modifier = Modifier.fillMaxWidth().height(420.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val bitmap = remember(path) { runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
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
