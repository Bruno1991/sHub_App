package com.saile.shub.gis

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
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
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlinx.coroutines.launch

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
    val enabledLayers by viewModel.enabledLayers.collectAsState()
    val currentBasemap by viewModel.currentBasemap.collectAsState()
    val activeMbtilesMetadata by viewModel.activeMbtilesMetadata.collectAsState()
    val availableMbtiles by viewModel.availableMbtiles.collectAsState()
    val isImportingMbtiles by viewModel.isImportingMbtiles.collectAsState()
    val mbtilesImportProgress by viewModel.mbtilesImportProgress.collectAsState()

    val mbtilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importMbtilesUri(uri)
        }
    }

    val isReticleVisible by viewModel.isReticleVisible.collectAsState()
    val isMeasurementMode by viewModel.isMeasurementMode.collectAsState()
    val gpsTrackingMode by viewModel.gpsTrackingMode.collectAsState()

    // GPS & GNSS Tracker powered by mature sFoto location utilities
    val locationTracker = remember { GisLocationTracker(context) }
    val positioningInfo by locationTracker.positioningInfo.collectAsState()
    val compassHeading by locationTracker.compassHeading.collectAsState()

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fine || coarse) {
            locationTracker.startTracking()
        }
    }

    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        } else {
            locationTracker.startTracking()
        }
    }
    DisposableEffect(Unit) {
        onDispose { locationTracker.stopTracking() }
    }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    var showPositioningDialog by remember { mutableStateOf(false) }
    var showProjectInfoDialog by remember { mutableStateOf(false) }
    var showActiveLayerSelector by remember { mutableStateOf(false) }
    var showBasemapDialog by remember { mutableStateOf(false) }

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

    // Dynamic Basemap Switcher (Google Satellite, Hybrid, Esri, OSM, Topo, MBTiles)
    LaunchedEffect(currentBasemap, activeMbtilesMetadata, mapViewRef) {
        val map = mapViewRef ?: return@LaunchedEffect
        if (currentBasemap == BasemapType.OFFLINE_MBTILES) {
            val meta = activeMbtilesMetadata
            if (meta != null) {
                try {
                    Log.i("ShubGisMbtiles", "Attaching offline MBTiles: ${meta.name} (${meta.file.absolutePath})")
                    val provider = GisMbtilesHelper.createTileProvider(context, meta)
                    map.setTileProvider(provider)
                    map.setTileSource(provider.tileSource)
                    map.tileProvider.clearTileCache()
                    map.invalidate()
                    Log.i("ShubGisMbtiles", "Offline MBTiles provider attached: source=${provider.tileSource.name()}")
                } catch (e: Throwable) {
                    Log.e("ShubGisMbtiles", "Failed to switch to MBTiles provider: ${e.message}", e)
                }
            } else {
                Log.w("ShubGisMbtiles", "OFFLINE_MBTILES selected but activeMbtilesMetadata is null")
            }
        } else {
            try {
                if (map.tileProvider !is MapTileProviderBasic) {
                    val safeReceiver = GisMbtilesHelper.SafeRegisterReceiver(context.applicationContext)
                    val basicProvider = MapTileProviderBasic(
                        safeReceiver,
                        org.osmdroid.tileprovider.modules.NetworkAvailabliltyCheck(context.applicationContext),
                        GisTileSources.getTileSource(currentBasemap),
                        context.applicationContext,
                        null
                    )
                    map.setTileProvider(basicProvider)
                }
                val tileSource = GisTileSources.getTileSource(currentBasemap)
                map.setTileSource(tileSource)
                map.tileProvider.clearTileCache()
            } catch (e: Throwable) {
                Log.e("ShubGisMbtiles", "Failed to switch basemap: ${e.message}", e)
            }
        }
        map.invalidate()
    }

    // Auto-zoom to dataset extent
    LaunchedEffect(datasetExtent, mapViewRef) {
        val extent = datasetExtent ?: return@LaunchedEffect
        val map = mapViewRef ?: return@LaunchedEffect
        val osmbounds = org.osmdroid.util.BoundingBox(extent.maxLat, extent.maxLon, extent.minLat, extent.minLon)
        if (map.width > 0 && map.height > 0) {
            map.zoomToBoundingBox(osmbounds, false, 50)
        } else {
            map.post { map.zoomToBoundingBox(osmbounds, false, 50) }
        }
        kotlinx.coroutines.delay(200)
        val bbox = map.boundingBox
        if (bbox != null) {
            viewModel.onViewportChanged(
                minLat = bbox.latSouth,
                maxLat = bbox.latNorth,
                minLon = bbox.lonWest,
                maxLon = bbox.lonEast,
                zoom = map.zoomLevelDouble
            )
        } else {
            viewModel.onViewportChanged(
                minLat = extent.minLat,
                maxLat = extent.maxLat,
                minLon = extent.minLon,
                maxLon = extent.maxLon,
                zoom = map.zoomLevelDouble
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.zoomToExtentEvent.collect { extent ->
            val map = mapViewRef ?: return@collect
            val osmbounds = org.osmdroid.util.BoundingBox(extent.maxLat, extent.maxLon, extent.minLat, extent.minLon)
            if (map.width > 0 && map.height > 0) {
                map.zoomToBoundingBox(osmbounds, true, 60)
            } else {
                map.post { map.zoomToBoundingBox(osmbounds, true, 60) }
            }
            kotlinx.coroutines.delay(250)
            val bbox = map.boundingBox
            if (bbox != null) {
                viewModel.onViewportChanged(
                    minLat = bbox.latSouth,
                    maxLat = bbox.latNorth,
                    minLon = bbox.lonWest,
                    maxLon = bbox.lonEast,
                    zoom = map.zoomLevelDouble
                )
            } else {
                viewModel.onViewportChanged(
                    minLat = extent.minLat,
                    maxLat = extent.maxLat,
                    minLon = extent.minLon,
                    maxLon = extent.maxLon,
                    zoom = map.zoomLevelDouble
                )
            }
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

    // Real-time GPS user position rendering on vector overlay
    LaunchedEffect(positioningInfo, compassHeading) {
        vectorOverlay.updateUserPosition(positioningInfo, compassHeading)
        mapViewRef?.postInvalidate()
    }

    // Toggle compass sensor only when in FOLLOW_BEARING mode
    LaunchedEffect(gpsTrackingMode) {
        locationTracker.setCompassEnabled(gpsTrackingMode == GpsTrackingMode.FOLLOW_BEARING)
        if (gpsTrackingMode == GpsTrackingMode.OFF) {
            mapViewRef?.mapOrientation = 0f
        }
    }

    // GPS Camera Follower (only animates on location changes, NOT on compass heading!)
    LaunchedEffect(gpsTrackingMode, positioningInfo?.latitude, positioningInfo?.longitude) {
        if (gpsTrackingMode == GpsTrackingMode.OFF) return@LaunchedEffect
        val map = mapViewRef ?: return@LaunchedEffect
        val pos = positioningInfo ?: return@LaunchedEffect
        val targetZoom = if (map.zoomLevelDouble < 14.0) 16.5 else map.zoomLevelDouble
        map.controller.animateTo(GeoPoint(pos.latitude, pos.longitude), targetZoom, 400L)
    }

    // Compass Bearing Map Rotation (only when FOLLOW_BEARING is active)
    LaunchedEffect(gpsTrackingMode, compassHeading) {
        if (gpsTrackingMode != GpsTrackingMode.FOLLOW_BEARING) return@LaunchedEffect
        val map = mapViewRef ?: return@LaunchedEffect
        map.mapOrientation = -compassHeading
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

    BackHandler {
        when {
            drawerState.isOpen -> coroutineScope.launch { drawerState.close() }
            selectedTarget != null -> viewModel.clearSelection()
            isSearchFocused -> isSearchFocused = false
            interactionMode == GisInteractionMode.DIGITIZE -> viewModel.setInteractionMode(GisInteractionMode.BROWSE)
            else -> onBack()
        }
    }

    // Dashboard Left Navigation Drawer (Opens ONLY when clicking hamburger button)
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(320.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Standard Header Banner matching app theme
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .statusBarsPadding()
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "sGis - Cartografia",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Projeto: BASE.gpkg",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                            Text(
                                text = "SIRGAS 2000 / UTM 20S (EPSG:31980 / 4674)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                            items(availableLayers, key = { it.name }) { layer ->
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
                                            checked = enabledLayers.contains(layer.name),
                                            onCheckedChange = { viewModel.toggleLayer(layer.name) },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = MaterialTheme.colorScheme.primary,
                                                checkmarkColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))

                                        // Symbology Icon / Color Swatch
                                        val isLineLayer = layer.type in listOf(GisElementType.SPAN_AT, GisElementType.SPAN_MT, GisElementType.SPAN_BT)
                                        val symbolIcon = remember(layer.type) {
                                            if (!isLineLayer) GisSymbolProvider.getSymbol(layer.type, 2.5f)?.bitmap?.asImageBitmap() else null
                                        }

                                        if (symbolIcon != null) {
                                            androidx.compose.foundation.Image(
                                                bitmap = symbolIcon,
                                                contentDescription = layer.name,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clip(if (isLineLayer) RoundedCornerShape(2.dp) else CircleShape)
                                                    .background(
                                                        runCatching { Color(android.graphics.Color.parseColor(layer.colorHex)) }.getOrDefault(Color.Gray)
                                                    )
                                                    .border(1.dp, Color.DarkGray, if (isLineLayer) RoundedCornerShape(2.dp) else CircleShape)
                                            )
                                        }
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
                                                tint = MaterialTheme.colorScheme.primary,
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
                            icon = { Icon(Icons.Filled.Layers, contentDescription = null) },
                            label = { Text("Mapa Base: ${currentBasemap.displayName}") },
                            selected = false,
                            onClick = {
                                coroutineScope.launch { drawerState.close() }
                                showBasemapDialog = true
                            }
                        )
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Filled.Archive, contentDescription = null) },
                            label = { Text("Importar MBTiles Offline") },
                            selected = false,
                            onClick = {
                                coroutineScope.launch { drawerState.close() }
                                mbtilesLauncher.launch(arrayOf("*/*"))
                            }
                        )
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
                // App Standard Topbar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 3.dp,
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
                                Icon(Icons.Filled.Menu, contentDescription = "Painel de Camadas", tint = MaterialTheme.colorScheme.onSurface)
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Project Name Badge matching sHub Theme
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "sGis",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = "BASE.gpkg",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            // GNSS Positioning Status Pill
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
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
                                            .background(if (positioningInfo != null) Color(0xFF00E676) else Color(0xFFFF5252))
                                    )
                                    Icon(Icons.Filled.GpsFixed, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = positioningInfo?.accuracyText ?: "± --",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Search Action (Lupa sempre no canto superior direito)
                            IconButton(onClick = { isSearchFocused = !isSearchFocused }) {
                                Icon(Icons.Filled.Search, contentDescription = "Buscar ativo", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        // Expandable Search Bar
                        AnimatedVisibility(visible = isSearchFocused) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
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
                                    Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    TextField(
                                        value = searchQuery,
                                        onValueChange = {
                                            searchQuery = it
                                            viewModel.search(it)
                                        },
                                        placeholder = { Text("Buscar ativo, código, lat/lon ou UTM", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) },
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(onSearch = { viewModel.search(searchQuery) }),
                                        colors = TextFieldDefaults.colors(
                                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent
                                        ),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (searchQuery.isNotBlank()) {
                                        IconButton(onClick = {
                                            viewModel.search(searchQuery)
                                        }) {
                                            Icon(Icons.Filled.Search, contentDescription = "Executar busca", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        isSearchFocused = false
                                        viewModel.search("")
                                    }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Fechar busca", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            if (viewModel.currentBasemap.value == BasemapType.OFFLINE_MBTILES && viewModel.activeMbtilesMetadata.value != null) {
                                val offlineProvider = GisMbtilesHelper.createTileProvider(ctx, viewModel.activeMbtilesMetadata.value!!)
                                setTileProvider(offlineProvider)
                                setTileSource(offlineProvider.tileSource)
                            } else {
                                setTileSource(GisTileSources.getTileSource(viewModel.currentBasemap.value))
                            }
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
                                    viewModel.disableGpsFollowing()
                                    update()
                                    return false
                                }
                                override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                                    update()
                                    return false
                                }
                                private var lastUpdateZoom = 0.0
                                private var lastUpdateLat = 0.0
                                private var lastUpdateLon = 0.0

                                private fun update() {
                                    val c = mapCenter
                                    val z = zoomLevelDouble
                                    if (c != null) {
                                        val dLat = Math.abs(c.latitude - lastUpdateLat)
                                        val dLon = Math.abs(c.longitude - lastUpdateLon)
                                        val dZoom = Math.abs(z - lastUpdateZoom)
                                        if (dLat > 0.005 || dLon > 0.005 || dZoom > 0.1) {
                                            lastUpdateLat = c.latitude
                                            lastUpdateLon = c.longitude
                                            lastUpdateZoom = z
                                            centerLat = c.latitude
                                            centerLon = c.longitude
                                            currentZoom = z
                                        }
                                    }
                                    val bbox = boundingBox ?: return
                                    viewModel.onViewportChanged(
                                        minLat = bbox.latSouth,
                                        maxLat = bbox.latNorth,
                                        minLon = bbox.lonWest,
                                        maxLon = bbox.lonEast,
                                        zoom = z
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

                // 3. QField Scale Bar & Center Coordinate HUD (Bottom Left)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = if (interactionMode == GisInteractionMode.DIGITIZE || selectedTarget != null) 210.dp else 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val centerUtm = remember(centerLat, centerLon) {
                        UtmConverter.formatUtm(centerLat, centerLon)
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xCC000000)
                    ) {
                        Text(
                            text = "UTM: $centerUtm",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    GisScaleBar(
                        latitude = centerLat,
                        zoomLevel = currentZoom
                    )
                }

                // 4. Floating Right Toolbar (QField Side Tool Controls)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = if (interactionMode == GisInteractionMode.DIGITIZE || selectedTarget != null) 210.dp else 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    // Basemap Selector FAB (Google Satélite / Esri / OSM / Topo)
                    SmallFloatingActionButton(
                        onClick = { showBasemapDialog = true },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Icon(Icons.Filled.Layers, contentDescription = "Camadas de Satélite / Mapa Base")
                    }
                    // GPS Tracking FAB (QField MyLocation)
                    SmallFloatingActionButton(
                        onClick = viewModel::cycleGpsTrackingMode,
                        containerColor = when (gpsTrackingMode) {
                            GpsTrackingMode.OFF -> MaterialTheme.colorScheme.surfaceVariant
                            GpsTrackingMode.FOLLOW -> MaterialTheme.colorScheme.primary
                            GpsTrackingMode.FOLLOW_BEARING -> Color(0xFF00E676)
                        },
                        contentColor = when (gpsTrackingMode) {
                            GpsTrackingMode.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
                            GpsTrackingMode.FOLLOW -> MaterialTheme.colorScheme.onPrimary
                            GpsTrackingMode.FOLLOW_BEARING -> Color.Black
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

                    // Full Extent
                    SmallFloatingActionButton(
                        onClick = viewModel::zoomToProjectExtent,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Icon(Icons.Filled.ZoomOutMap, contentDescription = "Extensão Total")
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
                                    Icon(Icons.Filled.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
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
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
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

                // 5. Feature Form / Inspection Sheet
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
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    target.subtitle?.let {
                                        Text(text = it, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                    val utmStr = remember(target.latitude, target.longitude) {
                                        UtmConverter.formatUtm(target.latitude, target.longitude)
                                    }
                                    Text(
                                        text = "UTM: $utmStr  |  GEO: %.6f, %.6f".format(Locale.US, target.latitude, target.longitude),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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

                            // Coordinates Action Button
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val utmStr = UtmConverter.formatUtm(target.latitude, target.longitude)
                                        GisNavigationHelper.copyCoordinatesToClipboard(context, target.latitude, target.longitude, utmStr)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copiar Coordenadas")
                                }

                                if (interactionMode == GisInteractionMode.DIGITIZE) {
                                    IconButton(onClick = { showEditAttrsDialog = true }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { showDeleteConfirmDialog = true }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Excluir", tint = Color.Red)
                                    }
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

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
                AnimatedVisibility(visible = (isSearchFocused || searchQuery.isNotBlank()) && state is GisState.SearchResults) {
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
                                            headlineContent = { Text(coord.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                                            supportingContent = { Text(coord.subtitle) },
                                            leadingContent = { Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                            modifier = Modifier.clickable {
                                                viewModel.selectItem(coord)
                                                searchQuery = ""
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
                                        leadingContent = { Icon(Icons.Filled.ElectricBolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                        modifier = Modifier.clickable {
                                            viewModel.selectItem(feat)
                                            searchQuery = ""
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

                // 10. MBTiles Import Progress Banner
                if (isImportingMbtiles) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        modifier = Modifier.fillMaxWidth().padding(12.dp).align(Alignment.TopCenter)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Importando pacote MBTiles... $mbtilesImportProgress%", style = MaterialTheme.typography.bodyMedium)
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

    // Dialog: Basemap Selection (Google Satellite Hybrid, Pure, MBTiles, Esri, OSM, Topo)
    if (showBasemapDialog) {
        AlertDialog(
            onDismissRequest = { showBasemapDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Selecionar Mapa Base")
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(BasemapType.values()) { type ->
                        val isSelected = currentBasemap == type
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setBasemap(type)
                                    if (type != BasemapType.OFFLINE_MBTILES) {
                                        showBasemapDialog = false
                                    }
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            viewModel.setBasemap(type)
                                            if (type != BasemapType.OFFLINE_MBTILES) {
                                                showBasemapDialog = false
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = type.displayName,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        )
                                        Text(
                                            text = type.subtitle,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                }

                                // Dedicated MBTiles Controls
                                if (type == BasemapType.OFFLINE_MBTILES) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (activeMbtilesMetadata != null) {
                                        val meta = activeMbtilesMetadata!!
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            tonalElevation = 2.dp,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "📦 ${meta.file.name}",
                                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Text(
                                                        text = meta.formattedSize,
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "Camada: ${meta.name} • Formato: ${meta.format.uppercase()} • Zooms: ${meta.minZoom} a ${meta.maxZoom}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.Gray
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "Nenhum arquivo .mbtiles ativo. Importe um arquivo do aparelho.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                    }

                                    // Action buttons for MBTiles
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                mbtilesLauncher.launch(arrayOf("*/*"))
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Icon(Icons.Filled.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (activeMbtilesMetadata == null) "Importar .mbtiles" else "Trocar Arquivo", style = MaterialTheme.typography.labelSmall)
                                        }

                                        if (activeMbtilesMetadata?.bounds != null) {
                                            FilledTonalButton(
                                                onClick = {
                                                    viewModel.zoomToMbtilesExtent()
                                                    showBasemapDialog = false
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Icon(Icons.Filled.ZoomOutMap, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Mundi", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }

                                        FilledTonalButton(
                                            onClick = {
                                                viewModel.zoomToProjectExtent()
                                                showBasemapDialog = false
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Focar Ativos", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }

                                    // List other available local MBTiles if any
                                    if (availableMbtiles.size > 1) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Outros pacotes detectados no aparelho:",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        availableMbtiles.filter { it.file.absolutePath != activeMbtilesMetadata?.file?.absolutePath }.forEach { other ->
                                            TextButton(
                                                onClick = {
                                                    viewModel.selectMbtiles(other)
                                                },
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "📁 ${other.file.name} (${other.formattedSize})",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBasemapDialog = false }) { Text("Fechar") }
            }
        )
    }

    // Dialog: Positioning Information
    if (showPositioningDialog) {
        val pos = positioningInfo
        AlertDialog(
            onDismissRequest = { showPositioningDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.GpsFixed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
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
                Button(
                    onClick = { showProjectInfoDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
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
                    Text("Camada: ${activeEditLayer ?: "Ponto"}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancelar") }
            }
        )
    }
}
