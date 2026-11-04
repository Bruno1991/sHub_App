package com.saile.shub.gis

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saile.shub.core.geo.GeoCoordinate
import com.saile.shub.core.geo.UtmConverter
import com.saile.shub.gis.data.entities.GisFeatureEntity
import com.saile.shub.gis.usecase.ImportProgress
import com.saile.shub.gis.usecase.SearchItem
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.Locale

object GisTileSources {
    val ESRI_SATELLITE = object : OnlineTileSourceBase(
        "EsriWorldImagery",
        0,
        19,
        256,
        ".jpg",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val zoom = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "$baseUrl$zoom/$y/$x"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GisScreen(
    onBack: () -> Unit,
    viewModel: GisViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val isSatelliteMode by viewModel.isSatelliteMode.collectAsState()
    val selectedTarget by viewModel.selectedTarget.collectAsState()
    val visibleElements by viewModel.visibleElements.collectAsState()
    val datasetExtent by viewModel.datasetExtent.collectAsState()
    val availableLayers by viewModel.availableLayers.collectAsState()
    val activeLayerFilter by viewModel.activeLayerFilter.collectAsState()
    val offlineMbtilesPath by viewModel.offlineMbtilesPath.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    var showLayersSheet by remember { mutableStateOf(false) }

    val vectorOverlay = remember {
        GisVectorOverlay(
            onElementTapped = { elem ->
                viewModel.selectRenderElement(elem)
            }
        )
    }

    // Keep reference to MapView for camera animations
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    // Initialize osmdroid configuration
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = context.packageName
    }

    // React to auto-zoom events (e.g. dataset loaded or user clicked "Ver Tudo")
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

    // React to visibleElements or selected target changes
    LaunchedEffect(visibleElements, selectedTarget) {
        val map = mapViewRef
        val zoom = map?.zoomLevelDouble ?: 9.0
        val selId = selectedTarget?.feature?.id
        vectorOverlay.updateElements(visibleElements, zoom, selId)
        map?.postInvalidate()
    }

    // React to selected target changes by animating map view
    LaunchedEffect(selectedTarget) {
        val target = selectedTarget
        val map = mapViewRef
        if (target != null && map != null) {
            val geoPoint = GeoPoint(target.latitude, target.longitude)
            map.controller.animateTo(geoPoint, target.zoomLevel, 600L)
            
            // Update marker
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

    // React to satellite mode toggle
    LaunchedEffect(isSatelliteMode) {
        val map = mapViewRef ?: return@LaunchedEffect
        map.setTileSource(if (isSatelliteMode) GisTileSources.ESRI_SATELLITE else TileSourceFactory.MAPNIK)
        map.invalidate()
    }

    val launcher = rememberLauncherForActivityResult(
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
        if (selectedTarget != null) {
            viewModel.clearSelection()
        } else {
            onBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Full-screen OsmDroid MapView
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setMultiTouchControls(true)
                    isTilesScaledToDpi = true
                    setTileSource(if (isSatelliteMode) GisTileSources.ESRI_SATELLITE else TileSourceFactory.MAPNIK)
                    controller.setZoom(8.5)
                    // Center on Rondonia grid center
                    controller.setCenter(GeoPoint(-10.83, -63.28))
                    overlays.add(vectorOverlay)

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
                            triggerViewport()
                            return false
                        }
                        override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                            triggerViewport()
                            return false
                        }
                        private fun triggerViewport() {
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

        // 2. Floating Top Header: Search Bar & Actions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
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

                    IconButton(onClick = { launcher.launch("*/*") }) {
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
                            // Coordinate match item if found
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

                            // Asset matches
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

            // Dynamic Active Assets Counter Pill
            AnimatedVisibility(
                visible = visibleElements.isNotEmpty() && !isSearchFocused,
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shadowElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00F200))
                        )
                        Text(
                            text = "${visibleElements.size} ativos elétricos na tela",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
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

        // 3. Floating Action Buttons (Right Side Controls)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = if (selectedTarget != null) 250.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Layer Control Button (Camadas QField)
            FloatingActionButton(
                onClick = { showLayersSheet = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
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

            // Satellite / Street Mode Switcher
            ExtendedFloatingActionButton(
                onClick = viewModel::toggleSatelliteMode,
                icon = {
                    Icon(
                        imageVector = if (isSatelliteMode) Icons.Filled.Map else Icons.Filled.SatelliteAlt,
                        contentDescription = "Alternar Camada"
                    )
                },
                text = { Text(if (isSatelliteMode) "Mapa" else "Satélite") },
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 4. Layer Control Modal Bottom Sheet (Camadas do QField)
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
                        text = "Ative ou oculte as camadas de transmissão e distribuição:",
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

        // 5. Asset Details Card (When a Feature or Coordinate is Selected)
        selectedTarget?.let { target ->
            Card(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .heightIn(max = 240.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
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

                    // Key properties from feature metadataJson if available
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
                                list.take(8)
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
    }
}
