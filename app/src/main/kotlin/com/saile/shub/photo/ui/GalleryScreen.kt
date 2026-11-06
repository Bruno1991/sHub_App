package com.saile.shub.photo.ui

import android.graphics.Bitmap
import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.saile.shub.photo.camera.MediaItem
import com.saile.shub.photo.camera.MediaUtils
import com.saile.shub.photo.data.PhotoMediaMetadata
import com.saile.shub.photo.data.PhotoMediaMetadataDbHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

private const val THUMBNAIL_EDGE_PX = 320
private const val GALLERY_COLUMNS = 3
private val GalleryThumbnailDispatcher = Dispatchers.IO.limitedParallelism(2)

private fun openGoogleMapsRoute(context: android.content.Context, metadata: PhotoMediaMetadata) {
    val latitude = metadata.latitude ?: return
    val longitude = metadata.longitude ?: return
    val coordinates = "${latitude},${longitude}"
    val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$coordinates")).apply {
        setPackage("com.google.android.apps.maps")
    }
    try {
        context.startActivity(appIntent)
    } catch (_: ActivityNotFoundException) {
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$coordinates")
        )
        try {
            context.startActivity(webIntent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Google Maps não está disponível.", Toast.LENGTH_SHORT).show()
        }
    }
}

/** Keeps recently visible cells out of ContentResolver while the grid is scrolled back and forth. */
private object VideoThumbnailCache : android.util.LruCache<String, Bitmap>(32 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
}

private fun loadVideoPreviewFrame(
    context: android.content.Context,
    uri: Uri,
    maxWidthPx: Int,
    maxHeightPx: Int
): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val sourceWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull()?.coerceAtLeast(1) ?: maxWidthPx
        val sourceHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull()?.coerceAtLeast(1) ?: maxHeightPx
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull() ?: 0
        val displayWidth = if (rotation == 90 || rotation == 270) sourceHeight else sourceWidth
        val displayHeight = if (rotation == 90 || rotation == 270) sourceWidth else sourceHeight
        val scale = min(
            maxWidthPx.toFloat() / displayWidth,
            maxHeightPx.toFloat() / displayHeight
        ).coerceAtMost(1f)
        val targetWidth = (displayWidth * scale).toInt().coerceAtLeast(1)
        val targetHeight = (displayHeight * scale).toInt().coerceAtLeast(1)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(
                0L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                targetWidth,
                targetHeight
            )
        } else {
            retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }
    } finally {
        retriever.release()
    }
}

@Composable
private fun rememberVideoThumbnail(uri: Uri, widthPx: Int, heightPx: Int): Bitmap? {
    val context = LocalContext.current
    val cacheKey = remember(uri, widthPx, heightPx) { "$uri@$widthPx x $heightPx" }
    var bitmap by remember(cacheKey) { mutableStateOf(VideoThumbnailCache.get(cacheKey)) }
    LaunchedEffect(cacheKey) {
        if (bitmap != null) return@LaunchedEffect

        val loadedBitmap = withContext(GalleryThumbnailDispatcher) {
            try {
                VideoThumbnailCache.get(cacheKey)?.let { return@withContext it }
                if (widthPx > THUMBNAIL_EDGE_PX || heightPx > THUMBNAIL_EDGE_PX) {
                    loadVideoPreviewFrame(context, uri, widthPx, heightPx)
                } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(
                        uri,
                        android.util.Size(widthPx, heightPx),
                        null
                    )
                } else {
                    val id = android.content.ContentUris.parseId(uri)
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Video.Thumbnails.getThumbnail(
                        context.contentResolver, id, android.provider.MediaStore.Video.Thumbnails.MINI_KIND, null
                    )
                }
            } catch (e: Exception) {
                null
            }
        }
        if (loadedBitmap != null) {
            VideoThumbnailCache.put(cacheKey, loadedBitmap)
            bitmap = loadedBitmap
        }
    }
    return bitmap
}

@Composable
private fun VideoThumbnail(
    uri: Uri,
    requestWidthPx: Int,
    requestHeightPx: Int,
    contentScale: ContentScale,
    modifier: Modifier = Modifier
) {
    val bitmap = rememberVideoThumbnail(uri, requestWidthPx, requestHeightPx)
    if (bitmap == null) {
        Box(modifier = modifier.background(Color.DarkGray))
        return
    }

    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    Image(
        bitmap = imageBitmap,
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier
    )
}

@Composable
private fun GridThumbnail(item: MediaItem) {
    if (item.isVideo) {
        VideoThumbnail(
            uri = item.uri,
            requestWidthPx = THUMBNAIL_EDGE_PX,
            requestHeightPx = THUMBNAIL_EDGE_PX,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    val context = LocalContext.current
    val request = remember(item.uri) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .size(THUMBNAIL_EDGE_PX)
            .scale(Scale.FILL)
            .precision(Precision.INEXACT)
            .allowHardware(true)
            .dispatcher(GalleryThumbnailDispatcher)
            .memoryCacheKey("gallery-grid:${item.uri}")
            .crossfade(false)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.Low,
        modifier = Modifier.fillMaxSize().background(Color.DarkGray)
    )
}

@Composable
private fun PhotoPagerPage(item: MediaItem) {
    val context = LocalContext.current
    val request = remember(item.uri) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .crossfade(false)
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize().background(Color.Black)
    )
}

/** A touch-friendly fast-scroll rail for long, three-column media grids. */
@Composable
private fun GalleryFastScroller(
    mediaItems: List<MediaItem>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    modifier: Modifier = Modifier
) {
    val itemCount = mediaItems.size
    if (itemCount <= GALLERY_COLUMNS * 4) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val totalRows = (itemCount + GALLERY_COLUMNS - 1) / GALLERY_COLUMNS
    val visibleRows by remember(gridState) {
        derivedStateOf {
            gridState.layoutInfo.visibleItemsInfo
                .map { it.index / GALLERY_COLUMNS }
                .distinct()
                .size
                .coerceAtLeast(1)
        }
    }
    val maxScrollableRow = (totalRows - visibleRows).coerceAtLeast(1)
    val naturalScrollFraction by remember(gridState, maxScrollableRow) {
        derivedStateOf {
            val firstItem = gridState.layoutInfo.visibleItemsInfo.firstOrNull()
            val itemHeight = firstItem?.size?.height?.coerceAtLeast(1) ?: 1
            val row = gridState.firstVisibleItemIndex / GALLERY_COLUMNS
            val rowOffset = gridState.firstVisibleItemScrollOffset.toFloat() / itemHeight
            ((row + rowOffset) / maxScrollableRow).coerceIn(0f, 1f)
        }
    }
    var isDragging by remember { mutableStateOf(false) }
    var draggedFraction by remember { mutableFloatStateOf(0f) }
    val pendingScroll = remember { java.util.concurrent.atomic.AtomicReference<Job?>(null) }
    val monthFormatter = remember { SimpleDateFormat("MMM yyyy", Locale("pt", "BR")) }

    BoxWithConstraints(modifier = modifier) {
        val trackHeightPx = with(density) { maxHeight.toPx() }
        val thumbHeight = 44.dp
        val thumbHeightPx = with(density) { thumbHeight.toPx() }
        val maxThumbOffsetPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
        val displayFraction = if (isDragging) draggedFraction else naturalScrollFraction
        val thumbOffsetPx = displayFraction * maxThumbOffsetPx
        val targetIndex = ((displayFraction * maxScrollableRow).roundToInt() * GALLERY_COLUMNS)
            .coerceIn(0, itemCount - 1)
        val dateLabel = remember(mediaItems, targetIndex) {
            mediaItems[targetIndex].dateAdded
                .takeIf { it > 0L }
                ?.let { monthFormatter.format(Date(it * 1000L)).replaceFirstChar(Char::uppercase) }
                ?: ""
        }

        fun scrollTo(y: Float) {
            val fraction = if (maxThumbOffsetPx <= 0f) {
                0f
            } else {
                ((y - thumbHeightPx / 2f) / maxThumbOffsetPx).coerceIn(0f, 1f)
            }
            draggedFraction = fraction
            val targetIndex = (fraction * maxScrollableRow).roundToInt() * GALLERY_COLUMNS
            val job = scope.launch(start = CoroutineStart.LAZY) {
                gridState.scrollToItem(targetIndex.coerceAtMost(itemCount - 1))
            }
            pendingScroll.getAndSet(job)?.cancel()
            job.start()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(itemCount, maxScrollableRow, trackHeightPx) {
                    detectVerticalDragGestures(
                        onDragStart = { position ->
                            isDragging = true
                            scrollTo(position.y)
                        },
                        onVerticalDrag = { change, _ ->
                            scrollTo(change.position.y)
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    )
                },
            contentAlignment = Alignment.TopEnd
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                    .size(thumbHeight)
                    .background(
                        if (isDragging) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.72f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val arrowsColor = if (isDragging) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        Color.Black.copy(alpha = 0.82f)
                    }
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = null,
                        tint = arrowsColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = arrowsColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (isDragging && dateLabel.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = with(density) { (-52).dp.roundToPx() },
                                y = (thumbOffsetPx + (thumbHeightPx - with(density) { 36.dp.toPx() }) / 2f).roundToInt()
                            )
                        }
                        .height(36.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(dateLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var mediaList by remember { mutableStateOf(emptyList<MediaItem>()) }
    var selectedItems by remember { mutableStateOf(setOf<Uri>()) }
    var initialPage by remember { mutableStateOf<Int?>(null) }
    var preparedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var isVideoPlaying by remember { mutableStateOf(false) }
    var hasRenderedVideoFrame by remember { mutableStateOf(false) }
    var showMetadataFor by remember { mutableStateOf<MediaItem?>(null) }
    var overlayMetadata by remember { mutableStateOf<PhotoMediaMetadata?>(null) }
    var isOverlayMetadataLoading by remember { mutableStateOf(false) }
    var metadataCopied by remember { mutableStateOf(false) }
    var itemsToDelete by remember { mutableStateOf<List<Uri>?>(null) }
    val galleryGridState = rememberLazyGridState()
    val galleryScope = rememberCoroutineScope()
    val metadataDb = remember(context) { PhotoMediaMetadataDbHelper(context.applicationContext) }

    DisposableEffect(metadataDb) {
        onDispose { metadataDb.close() }
    }

    suspend fun refreshMedia() {
        mediaList = withContext(Dispatchers.IO) { MediaUtils.getAllMedia(context) }
    }

    LaunchedEffect(Unit) {
        refreshMedia()
    }

    LaunchedEffect(showMetadataFor?.uri) {
        val item = showMetadataFor
        overlayMetadata = null
        metadataCopied = false
        isOverlayMetadataLoading = item != null
        if (item != null) {
            overlayMetadata = withContext(Dispatchers.IO) { metadataDb.get(item.uri) }
            isOverlayMetadataLoading = false
        }
    }

    LaunchedEffect(metadataCopied) {
        if (metadataCopied) {
            delay(2_000)
            metadataCopied = false
        }
    }

    BackHandler {
        if (showMetadataFor != null) {
            showMetadataFor = null
        } else if (preparedVideoUri != null) {
            isVideoPlaying = false
            hasRenderedVideoFrame = false
            preparedVideoUri = null
        } else if (initialPage != null) {
            initialPage = null
        } else if (selectedItems.isNotEmpty()) {
            selectedItems = emptySet()
        } else {
            onClose()
        }
    }

    if (initialPage != null && mediaList.isNotEmpty()) {
        val pagerState = rememberPagerState(
            initialPage = initialPage!!,
            pageCount = { mediaList.size }
        )
        val currentPage = pagerState.currentPage.coerceAtMost(maxOf(0, mediaList.size - 1))
        val currentItem = mediaList.getOrNull(currentPage)
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        val previewWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
        val previewHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }
        val exoPlayer = remember(context) { ExoPlayer.Builder(context).build() }

        DisposableEffect(exoPlayer) {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        isVideoPlaying = false
                    }
                }

                override fun onRenderedFirstFrame() {
                    hasRenderedVideoFrame = true
                }
            }
            exoPlayer.addListener(listener)
            onDispose {
                exoPlayer.removeListener(listener)
                exoPlayer.release()
            }
        }

        LaunchedEffect(preparedVideoUri) {
            hasRenderedVideoFrame = false
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            preparedVideoUri?.let { uri ->
                exoPlayer.setMediaItem(Media3Item.fromUri(uri))
                exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
                exoPlayer.prepare()
                if (isVideoPlaying) exoPlayer.play()
            }
        }

        LaunchedEffect(currentPage) {
            if (preparedVideoUri != mediaList.getOrNull(currentPage)?.uri) {
                isVideoPlaying = false
                hasRenderedVideoFrame = false
                preparedVideoUri = null
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pagerState,
                key = { mediaList[it].uri.toString() },
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val item = mediaList.getOrNull(page) ?: return@HorizontalPager
                if (item.isVideo) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        VideoThumbnail(
                            uri = item.uri,
                            requestWidthPx = previewWidthPx,
                            requestHeightPx = previewHeightPx,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                        val isPrepared = preparedVideoUri == item.uri
                        if (isPrepared) {
                            AndroidView(
                                factory = { viewContext ->
                                    PlayerView(viewContext).apply {
                                        player = exoPlayer
                                        useController = false
                                        isClickable = false
                                        isFocusable = false
                                        setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
                                        setKeepContentOnPlayerReset(false)
                                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                                    }
                                },
                                update = { it.player = exoPlayer },
                                modifier = Modifier.fillMaxSize()
                            )
                            val previewAlpha by animateFloatAsState(
                                targetValue = if (hasRenderedVideoFrame) 0f else 1f,
                                animationSpec = tween(durationMillis = 160),
                                label = "videoPreviewFade"
                            )
                            if (previewAlpha > 0.001f) {
                                VideoThumbnail(
                                    uri = item.uri,
                                    requestWidthPx = previewWidthPx,
                                    requestHeightPx = previewHeightPx,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().alpha(previewAlpha)
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                if (!isPrepared) {
                                    isVideoPlaying = true
                                    hasRenderedVideoFrame = false
                                    preparedVideoUri = item.uri
                                } else if (isVideoPlaying) {
                                    exoPlayer.pause()
                                    isVideoPlaying = false
                                } else {
                                    if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                        exoPlayer.seekTo(0)
                                    }
                                    exoPlayer.play()
                                    isVideoPlaying = true
                                }
                            },
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color(0x88000000), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isVideoPlaying && isPrepared) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isVideoPlaying && isPrepared) "Pausar" else "Reproduzir",
                                modifier = Modifier.size(44.dp),
                                tint = Color.White
                            )
                        }
                    }
                } else {
                    PhotoPagerPage(item)
                }
            }

            if (currentItem != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x66000000))
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = {
                        isVideoPlaying = false
                        hasRenderedVideoFrame = false
                        preparedVideoUri = null
                        initialPage = null
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                    }
                    Row {
                        IconButton(onClick = { showMetadataFor = currentItem }) {
                            Icon(Icons.Filled.Info, contentDescription = "Informações", tint = Color.White)
                        }
                        IconButton(onClick = {
                            MediaUtils.shareMedia(context, listOf(currentItem.uri))
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Compartilhar", tint = Color.White)
                        }
                        IconButton(onClick = {
                            itemsToDelete = listOf(currentItem.uri)
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Apagar", tint = Color.White)
                        }
                    }
                }
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (selectedItems.isNotEmpty()) {
                            Text("${selectedItems.size} selecionados")
                        } else {
                            Text("Galeria sHub")
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (selectedItems.isNotEmpty()) {
                                selectedItems = emptySet()
                            } else {
                                onClose()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                        }
                    },
                    actions = {
                        if (selectedItems.isNotEmpty()) {
                            val allMediaSelected = mediaList.isNotEmpty() && selectedItems.size == mediaList.size
                            Checkbox(
                                checked = allMediaSelected,
                                onCheckedChange = { selectAll ->
                                    selectedItems = if (selectAll) mediaList.mapTo(linkedSetOf()) { it.uri } else emptySet()
                                },
                                modifier = Modifier.semantics {
                                    contentDescription = if (allMediaSelected) "Desmarcar todas as mídias" else "Selecionar todas as mídias"
                                }
                            )
                            IconButton(onClick = {
                                MediaUtils.shareMedia(context, selectedItems.toList())
                            }) {
                                Icon(Icons.Filled.Share, contentDescription = "Compartilhar")
                            }
                            IconButton(onClick = {
                                itemsToDelete = selectedItems.toList()
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Apagar")
                            }
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(GALLERY_COLUMNS),
                    state = galleryGridState,
                    contentPadding = paddingValues,
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = mediaList,
                        key = { _, item -> item.uri.toString() },
                        contentType = { _, item -> if (item.isVideo) "video" else "image" }
                    ) { index, item ->
                        val isSelected = selectedItems.contains(item.uri)
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .padding(1.dp)
                                .combinedClickable(
                                    onClick = {
                                        if (selectedItems.isNotEmpty()) {
                                            selectedItems = if (isSelected) {
                                                selectedItems - item.uri
                                            } else {
                                                selectedItems + item.uri
                                            }
                                        } else {
                                            isVideoPlaying = false
                                            hasRenderedVideoFrame = false
                                            preparedVideoUri = null
                                            initialPage = index
                                        }
                                    },
                                    onLongClick = {
                                        selectedItems = if (isSelected) {
                                            selectedItems - item.uri
                                        } else {
                                            selectedItems + item.uri
                                        }
                                    }
                                )
                        ) {
                            GridThumbnail(item)
                            
                            if (item.isVideo) {
                                val sec = item.duration / 1000
                                val text = String.format("%02d:%02d", sec / 60, sec % 60)
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .background(Color(0x88000000))
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Text(text, color = Color.White, style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0x88FFFFFF))
                                        .border(2.dp, MaterialTheme.colorScheme.primary)
                                )
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = "Selecionado",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(24.dp)
                                        .background(Color.White, CircleShape)
                                )
                            }
                        }
                    }
                }

                GalleryFastScroller(
                    mediaItems = mediaList,
                    gridState = galleryGridState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(52.dp)
                        .padding(
                            top = paddingValues.calculateTopPadding() + 8.dp,
                            bottom = paddingValues.calculateBottomPadding() + 8.dp
                        )
                )
            }
        }
    }

    if (showMetadataFor != null) {
        val item = showMetadataFor!!
        val itemOverlayMetadata = overlayMetadata?.takeIf { it.uri == item.uri.toString() }
        AlertDialog(
            onDismissRequest = { showMetadataFor = null },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Metadados") },
            text = {
                val sizeMb = item.size / (1024f * 1024f)
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                val dateStr = sdf.format(Date(item.dateAdded * 1000))
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Nome: ${item.name}", fontWeight = FontWeight.Bold)
                    Text("Tipo: ${if (item.isVideo) "Vídeo" else "Imagem"}")
                    Text("Tamanho: ${String.format(Locale.US, "%.2f", sizeMb)} MB")
                    Text("Data: $dateStr")
                    if (item.isVideo) {
                        val sec = item.duration / 1000
                        Text("Duração: ${String.format("%02d:%02d", sec / 60, sec % 60)}")
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Dados do overlay", fontWeight = FontWeight.Bold)
                    when {
                        isOverlayMetadataLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        itemOverlayMetadata != null -> Text(itemOverlayMetadata.overlayText())
                        else -> Text(
                            "Metadados do overlay indisponíveis para esta mídia anterior à atualização.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val text = itemOverlayMetadata?.overlayText().orEmpty()
                            clipboard.setText(AnnotatedString(text))
                            metadataCopied = true
                        },
                        enabled = itemOverlayMetadata != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Copiar metadados")
                    }
                    if (metadataCopied) {
                        IconMessageSurface(
                            message = "Metadados copiados.",
                            icon = Icons.Filled.CheckCircle,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Button(
                        onClick = { itemOverlayMetadata?.let { openGoogleMapsRoute(context, it) } },
                        enabled = itemOverlayMetadata?.latitude != null && itemOverlayMetadata?.longitude != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Navigation,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Gerar rota no Google Maps")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMetadataFor = null }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Fechar")
                }
            }
        )
    }

    if (itemsToDelete != null) {
        val list = itemsToDelete!!
        val count = list.size
        AlertDialog(
            onDismissRequest = { itemsToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Apagar Mídia") },
            text = { Text(if (count == 1) "Tem certeza que deseja apagar este arquivo? Esta ação não pode ser desfeita." else "Tem certeza que deseja apagar $count arquivos? Esta ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(onClick = {
                    itemsToDelete = null
                    galleryScope.launch {
                        if (withContext(Dispatchers.IO) { MediaUtils.deleteMedia(context, list) }) {
                            withContext(Dispatchers.IO) { metadataDb.delete(list) }
                            selectedItems = emptySet()
                            initialPage = null
                            isVideoPlaying = false
                            hasRenderedVideoFrame = false
                            preparedVideoUri = null
                            refreshMedia()
                        }
                    }
                }) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Sim", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemsToDelete = null }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Cancelar")
                }
            }
        )
    }
}
