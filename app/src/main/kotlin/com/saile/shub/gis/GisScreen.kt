package com.saile.shub.gis

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saile.shub.core.geo.GeoCoordinate
import com.saile.shub.core.geo.Hemisphere
import com.saile.shub.core.geo.UtmCoordinate
import com.saile.shub.core.geo.UtmConverter
import com.saile.shub.gis.domain.GisElementType
import com.saile.shub.gis.usecase.ImportProgress
import com.saile.shub.photo.data.PhotoMediaMetadata
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GisScreen(
    onBack: () -> Unit,
    onCaptureAssetPhoto: (String) -> Unit = {},
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
    val activeGpkgName by viewModel.activeGpkgName.collectAsState()
    val activeGpkgSizeFormatted by viewModel.activeGpkgSizeFormatted.collectAsState()
    val activeGpkgImportDate by viewModel.activeGpkgImportDate.collectAsState()
    val currentBasemap by viewModel.currentBasemap.collectAsState()
    val activeMbtilesMetadata by viewModel.activeMbtilesMetadata.collectAsState()
    val availableMbtiles by viewModel.availableMbtiles.collectAsState()
    val isImportingMbtiles by viewModel.isImportingMbtiles.collectAsState()
    val mbtilesImportProgress by viewModel.mbtilesImportProgress.collectAsState()

    val activeClientsMetadata by viewModel.activeClientsMetadata.collectAsState()
    val isImportingClients by viewModel.isImportingClients.collectAsState()
    val clientsImportCount by viewModel.clientsImportCount.collectAsState()

    val geotaggedPhotos by viewModel.geotaggedPhotos.collectAsState()
    val fieldInspections by viewModel.fieldInspections.collectAsState()
    val isRuralGuidanceActive by viewModel.isRuralGuidanceActive.collectAsState()
    val isSyncingSatelliteCache by viewModel.isSyncingSatelliteCache.collectAsState()
    val satelliteSyncProgressText by viewModel.satelliteSyncProgressText.collectAsState()

    val savedCenterLat by viewModel.savedCenterLat.collectAsState()
    val savedCenterLon by viewModel.savedCenterLon.collectAsState()
    val savedZoomLevel by viewModel.savedZoomLevel.collectAsState()

    val isReticleVisible by viewModel.isReticleVisible.collectAsState()
    val gpsTrackingMode by viewModel.gpsTrackingMode.collectAsState()

    val mbtilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importMbtilesUri(uri)
        }
    }

    val clientsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importClientsUri(uri) { ok ->
                if (ok) {
                    Toast.makeText(context, "Base de clientes (.json) importada com sucesso!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Falha ao importar arquivo JSON de clientes.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val locationTracker = remember { GisLocationTracker(context) }
    val positioningInfo by locationTracker.positioningInfo.collectAsState()
    val compassHeading by locationTracker.compassHeading.collectAsState()
    val isRecordingTracklog by locationTracker.isRecordingTracklog.collectAsState()
    val tracklogPoints by locationTracker.tracklogPoints.collectAsState()

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
        viewModel.refreshPhotosAndInspections()
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
    var showGeoConverterDialog by remember { mutableStateOf(false) }
    var showInspectionFormDialog by remember { mutableStateOf(false) }
    var showInspectionsListDialog by remember { mutableStateOf(false) }
    var selectedPhotoMetadata by remember { mutableStateOf<PhotoMediaMetadata?>(null) }
    var selectedInspectionDetail by remember { mutableStateOf<FieldInspectionRecord?>(null) }

    var measureDist by remember { mutableDoubleStateOf(0.0) }
    var measureArea by remember { mutableDoubleStateOf(0.0) }
    var measurePointsCount by remember { mutableIntStateOf(0) }

    var centerLat by remember { mutableDoubleStateOf(savedCenterLat) }
    var centerLon by remember { mutableDoubleStateOf(savedCenterLon) }
    var currentZoom by remember { mutableDoubleStateOf(savedZoomLevel) }

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
                showInspectionFormDialog = true
            },
            onPhotoTapped = { photo ->
                selectedPhotoMetadata = photo
            },
            onInspectionTapped = { insp ->
                selectedInspectionDetail = insp
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

    // Initialize OsmDroid synchronously before MapView instantiation
    remember {
        val osmConfig = Configuration.getInstance()
        osmConfig.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        osmConfig.userAgentValue = "${context.packageName}/2.2 (Android; sGis)"
        osmConfig.tileFileSystemCacheMaxBytes = 3L * 1024L * 1024L * 1024L // 3 GB
        osmConfig.tileFileSystemCacheTrimBytes = 2L * 1024L * 1024L * 1024L // 2 GB
        osmConfig.expirationExtendedDuration = 1000L * 60L * 60L * 24L * 365L * 10L // 10 years
        true
    }

    // Dynamic Basemap Switcher (MBTiles Offline and Mapa de Ruas OSM)
    LaunchedEffect(currentBasemap, activeMbtilesMetadata, availableMbtiles, mapViewRef) {
        val map = mapViewRef ?: return@LaunchedEffect
        val targetMbtiles = activeMbtilesMetadata ?: availableMbtiles.firstOrNull()
        if (currentBasemap == BasemapType.OFFLINE_MBTILES && targetMbtiles != null) {
            try {
                val provider = GisMbtilesHelper.createTileProvider(context, targetMbtiles, enableFallback = true)
                map.setUseDataConnection(false)
                map.setTileProvider(provider)
                map.setTileSource(provider.tileSource)
                map.overlayManager.tilesOverlay?.loadingBackgroundColor = android.graphics.Color.TRANSPARENT
                map.tileProvider.clearTileCache()
                map.invalidate()
            } catch (e: Throwable) {
                Log.e("ShubGisMbtiles", "Failed to switch to MBTiles provider: ${e.message}", e)
            }
        } else {
            try {
                map.setUseDataConnection(true)
                val tileSource = GisTileSources.getTileSource(BasemapType.STREETS)
                if (map.tileProvider !is MapTileProviderBasic) {
                    val safeReceiver = GisMbtilesHelper.SafeRegisterReceiver(context.applicationContext)
                    val basicProvider = MapTileProviderBasic(
                        safeReceiver,
                        org.osmdroid.tileprovider.modules.NetworkAvailabliltyCheck(context.applicationContext),
                        tileSource,
                        context.applicationContext,
                        org.osmdroid.tileprovider.modules.TileWriter()
                    )
                    map.setTileProvider(basicProvider)
                }
                map.setTileSource(tileSource)
                map.tileProvider.clearTileCache()
            } catch (e: Throwable) {
                Log.e("ShubGisMbtiles", "Failed to switch basemap: ${e.message}", e)
            }
        }
        map.invalidate()
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

    // Update Geotagged Photos & Field Inspections on overlay
    LaunchedEffect(geotaggedPhotos, fieldInspections, mapViewRef) {
        vectorOverlay.updatePhotosAndInspections(geotaggedPhotos, fieldInspections)
        mapViewRef?.postInvalidate()
    }

    // Update Tracklog & Rural Guidance Line on overlay
    LaunchedEffect(tracklogPoints, isRuralGuidanceActive, selectedTarget, mapViewRef) {
        val targetCoords = if (isRuralGuidanceActive && selectedTarget != null) {
            selectedTarget!!.latitude to selectedTarget!!.longitude
        } else null
        vectorOverlay.updateTracklogAndGuidance(tracklogPoints, targetCoords)
        mapViewRef?.postInvalidate()
    }

    // Real-time GPS user position rendering on vector overlay
    LaunchedEffect(positioningInfo, compassHeading) {
        vectorOverlay.updateUserPosition(positioningInfo, compassHeading)
        mapViewRef?.postInvalidate()
    }

    // Enable compass sensor when FOLLOW_BEARING or Rural Guidance is active
    LaunchedEffect(gpsTrackingMode, isRuralGuidanceActive) {
        locationTracker.setCompassEnabled(gpsTrackingMode == GpsTrackingMode.FOLLOW_BEARING || isRuralGuidanceActive)
        if (gpsTrackingMode == GpsTrackingMode.OFF) {
            mapViewRef?.mapOrientation = 0f
        }
    }

    LaunchedEffect(gpsTrackingMode, positioningInfo?.latitude, positioningInfo?.longitude) {
        if (gpsTrackingMode == GpsTrackingMode.OFF) return@LaunchedEffect
        val map = mapViewRef ?: return@LaunchedEffect
        val pos = positioningInfo ?: return@LaunchedEffect
        val targetZoom = if (map.zoomLevelDouble < 14.0) 16.5 else map.zoomLevelDouble
        map.controller.animateTo(GeoPoint(pos.latitude, pos.longitude), targetZoom, 400L)
    }

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
            map.controller.animateTo(geoPoint, target.zoomLevel, 500L)
            viewModel.saveCameraPosition(target.latitude, target.longitude, target.zoomLevel)

            map.overlays.removeAll { it is Marker }
            val marker = Marker(map).apply {
                position = geoPoint
                title = target.title
                subDescription = target.subtitle
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            map.overlays.add(marker)
            map.postInvalidate()

            kotlinx.coroutines.delay(550L)
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

    // File Picker for importing a GeoPackage (.gpkg)
    val gpkgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            var displayName = "elementos_${System.currentTimeMillis()}.gpkg"
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

    // Dashboard Left Navigation Drawer (Opens ONLY when clicking 3 lines in top-left; closes when tapping right side)
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(325.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .statusBarsPadding()
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "sGis Configurações",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = activeGpkgName?.let { "Elementos: $it" } ?: "Elementos (.gpkg): Nenhum importado",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            activeGpkgImportDate?.let { date ->
                                Text(
                                    text = "Data de importação (.gpkg): $date",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = activeMbtilesMetadata?.let { "Satélite: ${it.file.name}" } ?: "Satélite (.mbtiles): Nenhum importado",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            activeMbtilesMetadata?.let { meta ->
                                Text(
                                    text = "Data de importação (.mbtiles): ${meta.importDateFormatted}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = activeClientsMetadata?.let { "Clientes: ${it.sourceFileName} (${it.totalClients} UCs)" }
                                    ?: "Clientes (.json): Nenhum importado",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            activeClientsMetadata?.let { cMeta ->
                                Text(
                                    text = "Data de importação (.json): ${cMeta.importDateFormatted}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }

                    // Layer Tree Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
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
                                        .clickable { viewModel.setActiveEditLayer(layer.name) }
                                        .padding(vertical = 2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = enabledLayers.contains(layer.name),
                                            onCheckedChange = { viewModel.toggleLayer(layer.name) },
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))

                                        val isLineLayer = layer.type in listOf(GisElementType.SPAN_AT, GisElementType.SPAN_MT, GisElementType.SPAN_BT)
                                        val symbolIcon = remember(layer.type) {
                                            if (!isLineLayer) GisSymbolProvider.getSymbol(layer.type, 2.5f)?.bitmap?.asImageBitmap() else null
                                        }

                                        if (symbolIcon != null) {
                                            Image(
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
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                            label = { Text("Importar Elementos (.gpkg)") },
                            selected = false,
                            onClick = {
                                coroutineScope.launch { drawerState.close() }
                                gpkgLauncher.launch("*/*")
                            }
                        )
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                            label = { Text("Importar Camadas Satélite (.mbtiles)") },
                            selected = false,
                            onClick = {
                                coroutineScope.launch { drawerState.close() }
                                mbtilesLauncher.launch(arrayOf("*/*"))
                            }
                        )
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                            label = { Text("Importar Base de Clientes (.json)") },
                            selected = false,
                            onClick = {
                                coroutineScope.launch { drawerState.close() }
                                clientsLauncher.launch(arrayOf("*/*"))
                            }
                        )
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null) },
                            label = { Text("Conversor Geodésico (UTM ⇄ GEO)") },
                            selected = false,
                            onClick = {
                                coroutineScope.launch { drawerState.close() }
                                showGeoConverterDialog = true
                            }
                        )
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Filled.FactCheck, contentDescription = null) },
                            label = { Text("Inspeções (${fieldInspections.size}) & Cache HD") },
                            selected = false,
                            onClick = {
                                coroutineScope.launch { drawerState.close() }
                                showInspectionsListDialog = true
                            }
                        )
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                            label = { Text("Informações do Módulo sGis") },
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
        Scaffold(
            topBar = {
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
                            // 3-lines Hamburger Menu Icon
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "sGis Configurações", tint = MaterialTheme.colorScheme.onSurface)
                            }

                            Spacer(modifier = Modifier.weight(1f))

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

                            // Integrated GeoConverter Quick Action
                            IconButton(onClick = { showGeoConverterDialog = true }) {
                                Icon(Icons.Filled.SwapHoriz, contentDescription = "Conversor UTM / GEO", tint = MaterialTheme.colorScheme.onSurface)
                            }

                            // Unified Search Action
                            IconButton(onClick = { isSearchFocused = !isSearchFocused }) {
                                Icon(Icons.Filled.Search, contentDescription = "Buscar ativo, UC/Medidor ou coordenada", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        // Expandable Unified Search Bar
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
                                        placeholder = {
                                            Text(
                                                "Ativo, UC/CDC, Medidor, Cliente, GEO ou UTM",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
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
                            controller.setZoom(viewModel.savedZoomLevel.value)
                            controller.setCenter(GeoPoint(viewModel.savedCenterLat.value, viewModel.savedCenterLon.value))

                            overlays.add(vectorOverlay)
                            overlays.add(measurementOverlay)

                            addOnFirstLayoutListener { v, _, _, _, _ ->
                                val map = v as? MapView ?: return@addOnFirstLayoutListener
                                mapViewRef = map
                                val extent = viewModel.datasetExtent.value
                                if (!viewModel.hasInitialAutoZoomed && extent != null) {
                                    viewModel.hasInitialAutoZoomed = true
                                    map.zoomToBoundingBox(
                                        org.osmdroid.util.BoundingBox(extent.maxLat, extent.maxLon, extent.minLat, extent.minLon),
                                        false,
                                        40
                                    )
                                } else {
                                    map.controller.setZoom(viewModel.savedZoomLevel.value)
                                    map.controller.setCenter(GeoPoint(viewModel.savedCenterLat.value, viewModel.savedCenterLon.value))
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
                                        val dLat = kotlin.math.abs(c.latitude - lastUpdateLat)
                                        val dLon = kotlin.math.abs(c.longitude - lastUpdateLon)
                                        val dZoom = kotlin.math.abs(z - lastUpdateZoom)
                                        if (dLat > 0.002 || dLon > 0.002 || dZoom > 0.05) {
                                            lastUpdateLat = c.latitude
                                            lastUpdateLon = c.longitude
                                            lastUpdateZoom = z
                                            centerLat = c.latitude
                                            centerLon = c.longitude
                                            currentZoom = z
                                            viewModel.saveCameraPosition(c.latitude, c.longitude, z)
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

                // 2. QField Precision Reticle / Crosshair
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

                            drawCircle(
                                color = Color(0xCC000000),
                                radius = reticleSize,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                            )
                            drawCircle(
                                color = Color(0xEEFF0000),
                                radius = 2.dp.toPx()
                            )
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

                // 3. Rural Guidance Live HUD Banner (when active)
                if (isRuralGuidanceActive && selectedTarget != null) {
                    val target = selectedTarget!!
                    val pos = positioningInfo
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xEE1B263B)),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.Explore, contentDescription = null, tint = Color(0xFFFF9100))
                            if (pos != null) {
                                val metrics = GisNavigationHelper.calculateRuralGuidance(
                                    fromLat = pos.latitude,
                                    fromLon = pos.longitude,
                                    toLat = target.latitude,
                                    toLon = target.longitude
                                )
                                Column {
                                    Text(
                                        text = "Linha Guia Rural: ${metrics.formattedDistance} • Rumo ${metrics.formattedBearing}",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    )
                                    Text(
                                        text = "Alvo: ${target.title} | Sua Bússola: %.0f°".format(Locale.US, compassHeading),
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFFFD180))
                                    )
                                }
                            } else {
                                Text(
                                    text = "Linha Guia Rural ativa (aguardando sinal GPS...)",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White)
                                )
                            }
                            IconButton(onClick = viewModel::toggleRuralGuidance, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Desativar Linha Guia", tint = Color.White)
                            }
                        }
                    }
                }

                // 4. QField Scale Bar & Center Coordinate HUD (Bottom Left)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = if (interactionMode == GisInteractionMode.DIGITIZE || selectedTarget != null) 255.dp else 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val centerUtm = remember(centerLat, centerLon) {
                        UtmConverter.formatUtm(centerLat, centerLon)
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xCC000000),
                        modifier = Modifier.clickable { showGeoConverterDialog = true }
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

                // 5. Floating Right Toolbar
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = if (interactionMode == GisInteractionMode.DIGITIZE || selectedTarget != null) 255.dp else 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    // Basemap Selector FAB
                    SmallFloatingActionButton(
                        onClick = { showBasemapDialog = true },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Icon(Icons.Filled.Layers, contentDescription = "Camadas de Satélite / Mapa Base")
                    }

                    // Tracklog (Rastro da Viatura) FAB
                    SmallFloatingActionButton(
                        onClick = {
                            locationTracker.toggleTracklog()
                            val msg = if (!isRecordingTracklog) "Gravação de rastro GPS iniciada" else "Gravação de rastro GPS pausada"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        },
                        containerColor = if (isRecordingTracklog) Color(0xFF00E676) else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isRecordingTracklog) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Icon(Icons.Filled.Timeline, contentDescription = "Gravar Rastro GPS (Tracklog)")
                    }

                    // GPS Tracking FAB
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

                // 6. Feature / Client Inspection Sheet (Bottom Card)
                selectedTarget?.let { target ->
                    Card(
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .heightIn(max = 320.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = target.title,
                                        style = MaterialTheme.typography.titleMedium.copy(
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
                                    positioningInfo?.let { pos ->
                                        val guidance = GisNavigationHelper.calculateRuralGuidance(
                                            pos.latitude, pos.longitude, target.latitude, target.longitude
                                        )
                                        Text(
                                            text = "Distância do seu GPS: ${guidance.formattedDistance} (${guidance.formattedBearing})",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFE65100)
                                            )
                                        )
                                    }
                                }
                                IconButton(onClick = viewModel::clearSelection) {
                                    Icon(Icons.Filled.Close, contentDescription = "Fechar")
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Row 1: Primary Field Actions (Fotografar no sFoto + Linha Guia Rural)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val stampLabel = buildString {
                                            append(target.title)
                                            target.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
                                                append(" | ")
                                                append(sub.take(35))
                                            }
                                        }
                                        onCaptureAssetPhoto(stampLabel)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF416C)),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Fotografar sFoto", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.labelMedium)
                                }

                                Button(
                                    onClick = viewModel::toggleRuralGuidance,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isRuralGuidanceActive) Color(0xFFFF9100) else Color(0xFF2E7D32)
                                    ),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Filled.Explore, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (isRuralGuidanceActive) "Guia Rural Ativa" else "Linha Guia Rural",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Row 2: Google Maps Route + Registrar Anomalia + Copiar Coordenadas
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Filled.Directions, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Rota Maps", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.labelSmall)
                                }

                                OutlinedButton(
                                    onClick = { showInspectionFormDialog = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Filled.ReportProblem, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFE65100))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Anomalia", style = MaterialTheme.typography.labelSmall)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val utmStr = UtmConverter.formatUtm(target.latitude, target.longitude)
                                        GisNavigationHelper.copyCoordinatesToClipboard(context, target.latitude, target.longitude, utmStr)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar Coordenadas", modifier = Modifier.size(16.dp))
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
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 1.dp),
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

                // 7. Unified Search Results Dropdown Overlay (Coordenadas + Clientes UC/Medidor + Elementos GPKG)
                AnimatedVisibility(visible = (isSearchFocused || searchQuery.isNotBlank()) && state is GisState.SearchResults) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .heightIn(max = 380.dp)
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

                                if (searchResult.clients.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "CLIENTES / UNIDADES CONSUMIDORAS (${searchResult.clients.size})",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1565C0)
                                            ),
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                        )
                                    }
                                    items(searchResult.clients) { clientItem ->
                                        ListItem(
                                            headlineContent = { Text(clientItem.title, fontWeight = FontWeight.SemiBold) },
                                            supportingContent = { Text(clientItem.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            leadingContent = { Icon(Icons.Filled.PersonPinCircle, contentDescription = null, tint = Color(0xFF1565C0)) },
                                            modifier = Modifier.clickable {
                                                viewModel.selectItem(clientItem)
                                                searchQuery = ""
                                                isSearchFocused = false
                                            }
                                        )
                                    }
                                    if (searchResult.features.isNotEmpty()) {
                                        item { HorizontalDivider() }
                                    }
                                }

                                if (searchResult.features.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "ELEMENTOS DA REDE (${searchResult.features.size})",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                        )
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

                                if (searchResult.coordinateMatch == null && searchResult.clients.isEmpty() && searchResult.features.isEmpty()) {
                                    item {
                                        Text(
                                            text = "Nenhum elemento, UC/Medidor ou coordenada encontrado.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.Gray,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 8. Progress Banners (GPKG, MBTiles, Clients JSON, Satellite Sync)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .align(Alignment.TopCenter),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (state is GisState.Importing) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            modifier = Modifier.fillMaxWidth()
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

                    if (isImportingMbtiles) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                            modifier = Modifier.fillMaxWidth()
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

                    if (isImportingClients) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("Indexando base de clientes (.json): $clientsImportCount UCs...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    if (isSyncingSatelliteCache && satelliteSyncProgressText != null) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text(satelliteSyncProgressText!!, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: Integrated sGeoConversor (UTM ⇄ GEO + Localizar no sGis)
    if (showGeoConverterDialog) {
        var isGeoToUtm by remember { mutableStateOf(true) }
        var latInput by remember { mutableStateOf(String.format(Locale.US, "%.6f", centerLat)) }
        var lonInput by remember { mutableStateOf(String.format(Locale.US, "%.6f", centerLon)) }
        val initialUtm = remember(centerLat, centerLon) {
            runCatching { UtmConverter.geoToUtm(GeoCoordinate(centerLat, centerLon)) }.getOrNull()
        }
        var zoneInput by remember { mutableStateOf((initialUtm?.zone ?: 20).toString()) }
        var isSouth by remember { mutableStateOf(initialUtm?.hemisphere != Hemisphere.NORTH) }
        var eastingInput by remember { mutableStateOf(initialUtm?.let { String.format(Locale.US, "%.2f", it.easting) } ?: "569402.00") }
        var northingInput by remember { mutableStateOf(initialUtm?.let { String.format(Locale.US, "%.2f", it.northing) } ?: "8767709.00") }
        var targetZoneInput by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }
        var resultGeo by remember { mutableStateOf<GeoCoordinate?>(GeoCoordinate(centerLat, centerLon)) }
        var resultUtm by remember { mutableStateOf<UtmCoordinate?>(initialUtm) }

        fun runConversion() {
            runCatching {
                val forcedZone = targetZoneInput.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
                if (isGeoToUtm) {
                    val lat = latInput.trim().replace(',', '.').toDouble()
                    val lon = lonInput.trim().replace(',', '.').toDouble()
                    val geo = GeoCoordinate(lat, lon)
                    val utm = UtmConverter.geoToUtm(geo, forcedZone = forcedZone)
                    resultGeo = geo
                    resultUtm = utm
                } else {
                    val z = zoneInput.trim().toInt()
                    val e = eastingInput.trim().replace(',', '.').toDouble()
                    val n = northingInput.trim().replace(',', '.').toDouble()
                    val srcUtm = UtmCoordinate(z, if (isSouth) Hemisphere.SOUTH else Hemisphere.NORTH, e, n)
                    val geo = UtmConverter.utmToGeo(srcUtm)
                    val finalUtm = if (forcedZone != null) UtmConverter.convertUtmZone(srcUtm, forcedZone) else srcUtm
                    resultGeo = geo
                    resultUtm = finalUtm
                }
                errorMsg = null
            }.onFailure {
                errorMsg = it.message ?: "Coordenadas inválidas."
            }
        }

        AlertDialog(
            onDismissRequest = { showGeoConverterDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Conversor Geodésico (UTM ⇄ GEO)")
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = isGeoToUtm,
                            onClick = { isGeoToUtm = true; errorMsg = null },
                            label = { Text("GEO → UTM") }
                        )
                        FilterChip(
                            selected = !isGeoToUtm,
                            onClick = { isGeoToUtm = false; errorMsg = null },
                            label = { Text("UTM → GEO") }
                        )
                    }

                    if (isGeoToUtm) {
                        OutlinedTextField(
                            value = latInput,
                            onValueChange = { latInput = it },
                            label = { Text("Latitude decimal") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = lonInput,
                            onValueChange = { lonInput = it },
                            label = { Text("Longitude decimal") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = zoneInput,
                                onValueChange = { zoneInput = it },
                                label = { Text("Zona UTM") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(selected = isSouth, onClick = { isSouth = true }, label = { Text("Sul (S)") })
                            FilterChip(selected = !isSouth, onClick = { isSouth = false }, label = { Text("Norte (N)") })
                        }
                        OutlinedTextField(
                            value = eastingInput,
                            onValueChange = { eastingInput = it },
                            label = { Text("X / Easting (m)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = northingInput,
                            onValueChange = { northingInput = it },
                            label = { Text("Y / Northing (m)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = targetZoneInput,
                        onValueChange = { targetZoneInput = it },
                        label = { Text("Reprojetar p/ Fuso UTM (opcional: 20 ou 21)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(onClick = ::runConversion, modifier = Modifier.fillMaxWidth()) {
                        Text("Converter Coordenada")
                    }

                    errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                    val g = resultGeo
                    val u = resultUtm
                    if (g != null && u != null) {
                        val band = UtmConverter.getUtmBand(g.latitude)
                        val geoText = String.format(Locale.US, "%.6f, %.6f", g.latitude, g.longitude)
                        val dmsText = UtmConverter.formatDms(g.latitude, g.longitude)
                        val utmText = String.format(Locale.US, "%d%c %.2f E, %.2f N", u.zone, band, u.easting, u.northing)

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("GEO: $geoText", fontWeight = FontWeight.Bold)
                                Text("DMS: $dmsText", style = MaterialTheme.typography.bodySmall)
                                Text("UTM: $utmText", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                                    OutlinedButton(
                                        onClick = { GisNavigationHelper.copyCoordinatesToClipboard(context, g.latitude, g.longitude, utmText) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Copiar", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Button(
                                        onClick = {
                                            viewModel.focusCoordinateTarget(
                                                latitude = g.latitude,
                                                longitude = g.longitude,
                                                title = "UTM $utmText",
                                                subtitle = "GEO: $geoText • DMS: $dmsText"
                                            )
                                            showGeoConverterDialog = false
                                        },
                                        modifier = Modifier.weight(1.3f)
                                    ) {
                                        Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Localizar no sGis", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGeoConverterDialog = false }) { Text("Fechar") }
            }
        )
    }

    // Dialog: Register Field Inspection / Anomaly
    if (showInspectionFormDialog) {
        val target = selectedTarget
        val targetLat = target?.latitude ?: centerLat
        val targetLon = target?.longitude ?: centerLon
        val targetCode = target?.title ?: "Ponto na Mira (%.5f, %.5f)".format(Locale.US, targetLat, targetLon)
        val targetLayer = target?.subtitle ?: activeEditLayer ?: "Inspeção de Campo"

        var selectedAnomaly by remember { mutableStateOf(GisFieldInspectionHelper.ANOMALY_TYPES.first()) }
        var selectedSeverity by remember { mutableStateOf("ALTA") }
        var notes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showInspectionFormDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ReportProblem, contentDescription = null, tint = Color(0xFFE65100))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Registrar Anomalia / Inspeção")
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Alvo: $targetCode", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Coordenada: %.6f, %.6f".format(Locale.US, targetLat, targetLon), style = MaterialTheme.typography.labelSmall)

                    Text("Tipo de Anomalia:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    GisFieldInspectionHelper.ANOMALY_TYPES.forEach { type ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedAnomaly = type }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedAnomaly == type, onClick = { selectedAnomaly = type })
                            Text(type, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Text("Severidade:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GisFieldInspectionHelper.SEVERITIES.forEach { sev ->
                            FilterChip(
                                selected = selectedSeverity == sev,
                                onClick = { selectedSeverity = sev },
                                label = { Text(sev) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Observações técnicas") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addFieldInspection(
                            targetCode = targetCode,
                            targetLayer = targetLayer,
                            anomalyType = selectedAnomaly,
                            severity = selectedSeverity,
                            notes = notes.trim(),
                            latitude = targetLat,
                            longitude = targetLon,
                            photoUri = geotaggedPhotos.firstOrNull()?.uri
                        )
                        showInspectionFormDialog = false
                        Toast.makeText(context, "Anomalia registrada no mapa offline!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Salvar Inspeção")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInspectionFormDialog = false }) { Text("Cancelar") }
            }
        )
    }

    // Dialog: Inspections List, Export (KML/CSV) & Satellite Cache Sync
    if (showInspectionsListDialog) {
        AlertDialog(
            onDismissRequest = { showInspectionsListDialog = false },
            title = { Text("Inspeções de Campo & Cache HD") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Satellite Cache Sync guided by GPKG
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Atualizador de Cache Satélite (.gpkg → .mbtiles)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Baixa em segundo plano os tiles híbridos (Google Satélite + Rodovias) que faltarem para os elementos da região atual do seu .gpkg.",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Button(
                                onClick = {
                                    showInspectionsListDialog = false
                                    viewModel.syncGpkgAreaSatelliteCache(centerLat, centerLon) { count ->
                                        Toast.makeText(context, "Cache satélite sincronizado ($count novos tiles HD).", Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sincronizar Cache Satélite da Região")
                            }
                        }
                    }

                    HorizontalDivider()

                    Text("Pendências / Anomalias Registradas (${fieldInspections.size})", fontWeight = FontWeight.Bold)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { GisFieldInspectionHelper.exportAndShareKml(context, fieldInspections) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Exportar KML", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { GisFieldInspectionHelper.exportAndShareCsv(context, fieldInspections) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Exportar CSV", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (fieldInspections.isEmpty()) {
                        Text("Nenhuma anomalia registrada ainda. Segure o dedo em um ativo no mapa ou use o botão 'Anomalia'.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                            items(fieldInspections, key = { it.id }) { item ->
                                ListItem(
                                    headlineContent = { Text("${item.targetCode} • ${item.anomalyType}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall) },
                                    supportingContent = { Text("${item.severity} • ${item.formattedDate} • ${item.notes}", style = MaterialTheme.typography.labelSmall) },
                                    trailingContent = {
                                        IconButton(onClick = { viewModel.deleteFieldInspection(item.id) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Remover", tint = Color.Red, modifier = Modifier.size(18.dp))
                                        }
                                    },
                                    modifier = Modifier.clickable {
                                        showInspectionsListDialog = false
                                        viewModel.focusCoordinateTarget(item.latitude, item.longitude, item.targetCode, "${item.anomalyType} [${item.severity}]")
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInspectionsListDialog = false }) { Text("Fechar") }
            }
        )
    }

    // Dialog: Tapped Geotagged Photo from sFoto on Map
    selectedPhotoMetadata?.let { photo ->
        val dateStr = remember(photo.capturedAt) {
            SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR")).format(Date(photo.capturedAt))
        }
        AlertDialog(
            onDismissRequest = { selectedPhotoMetadata = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = Color(0xFFFF416C))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Foto Georreferenciada (sFoto)")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Identificação: ${photo.userName}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Data/Hora: $dateStr", style = MaterialTheme.typography.bodySmall)
                    if (photo.latitude != null && photo.longitude != null) {
                        Text("GEO: %.6f, %.6f".format(Locale.US, photo.latitude, photo.longitude), style = MaterialTheme.typography.bodySmall)
                        Text("UTM: ${UtmConverter.formatUtm(photo.latitude, photo.longitude)}", style = MaterialTheme.typography.bodySmall)
                    }
                    photo.address?.takeIf { it.isNotBlank() }?.let {
                        Text("Endereço: $it", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        runCatching {
                            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(photo.uri), "image/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(viewIntent)
                        }
                        selectedPhotoMetadata = null
                    }
                ) {
                    Text("Abrir Foto")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedPhotoMetadata = null }) { Text("Fechar") }
            }
        )
    }

    // Dialog: Tapped Field Inspection Detail
    selectedInspectionDetail?.let { insp ->
        AlertDialog(
            onDismissRequest = { selectedInspectionDetail = null },
            title = { Text("Inspeção: ${insp.targetCode}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Anomalia: ${insp.anomalyType}", fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                    Text("Severidade: ${insp.severity}", fontWeight = FontWeight.Bold)
                    if (insp.notes.isNotBlank()) Text("Observações: ${insp.notes}")
                    Text("UTM: ${insp.utmText}", style = MaterialTheme.typography.bodySmall)
                    Text("Data: ${insp.formattedDate}", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = {
                    onCaptureAssetPhoto("${insp.targetCode} | ${insp.anomalyType}")
                    selectedInspectionDetail = null
                }) {
                    Text("Fotografar no sFoto")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedInspectionDetail = null }) { Text("Fechar") }
            }
        )
    }

    // Dialog: Basemap Selection (MBTiles Offline and Mapa de Ruas OSM)
    if (showBasemapDialog) {
        AlertDialog(
            onDismissRequest = { showBasemapDialog = false },
            title = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Selecionar Mapa Base")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (currentBasemap == BasemapType.OFFLINE_MBTILES) "🔒 Modo Offline (.mbtiles)" else "🗺️ Mapa de Ruas (OSM)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
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
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            },
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
                        Text("Pontos no Rastro (Tracklog): ${tracklogPoints.size}")
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
                    }
                ) {
                    Text("Copiar Coordenadas")
                }
            },
            dismissButton = {
                if (tracklogPoints.isNotEmpty()) {
                    TextButton(onClick = { locationTracker.clearTracklog() }) { Text("Limpar Rastro") }
                } else {
                    TextButton(onClick = { showPositioningDialog = false }) { Text("Fechar") }
                }
            }
        )
    }

    // Dialog: sGis Module Information
    if (showProjectInfoDialog) {
        val totalFeatures = availableLayers.sumOf { it.featureCount }
        AlertDialog(
            onDismissRequest = { showProjectInfoDialog = false },
            title = { Text("Informações do Módulo sGis") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Versão do Módulo: sGis v2.2.0",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Elementos (.gpkg)", fontWeight = FontWeight.Bold)
                    Text("Arquivo: ${activeGpkgName ?: "Nenhum arquivo importado"}")
                    Text("Tamanho: ${activeGpkgSizeFormatted ?: "--"}")
                    Text("Total de Elementos: $totalFeatures (${availableLayers.size} camadas)")
                    activeGpkgImportDate?.let { Text("Data da Importação: $it") }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Camada Satélite (.mbtiles)", fontWeight = FontWeight.Bold)
                    Text("Arquivo: ${activeMbtilesMetadata?.file?.name ?: "Nenhum arquivo importado"}")
                    Text("Tamanho: ${activeMbtilesMetadata?.formattedSize ?: "--"}")
                    activeMbtilesMetadata?.let { meta ->
                        Text("Data da Importação: ${meta.importDateFormatted}")
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Base de Clientes (.json)", fontWeight = FontWeight.Bold)
                    Text("Arquivo: ${activeClientsMetadata?.sourceFileName ?: "Nenhum arquivo importado"}")
                    Text("Tamanho: ${activeClientsMetadata?.formattedSize ?: "--"}")
                    Text("Total de Clientes (UCs): ${activeClientsMetadata?.totalClients ?: 0}")
                    activeClientsMetadata?.let { cMeta ->
                        Text("Data da Importação: ${cMeta.importDateFormatted}")
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showProjectInfoDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}
