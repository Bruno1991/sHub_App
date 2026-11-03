package com.saile.shub.photo

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.location.LocationManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalView
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import com.saile.shub.photo.camera.CameraPreview
import com.saile.shub.photo.camera.MediaUtils
import com.saile.shub.photo.camera.cameraOutputRotation
import com.saile.shub.photo.location.LocationUtils
import com.saile.shub.photo.ui.PhotoBottomBar
import com.saile.shub.photo.ui.IconStatusPopup
import com.saile.shub.photo.ui.PhotoSettingsDrawer
import com.saile.shub.photo.ui.PhotoTopBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

@Composable
fun PhotoScreen(onBack: () -> Unit, viewModel: PhotoViewModel = viewModel()) {
    val context = LocalContext.current
    val view = LocalView.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val executor = remember { Executors.newSingleThreadExecutor() }
    val capture = remember { PhotoCapture(executor) }
    DisposableEffect(capture) {
        onDispose {
            capture.close()
            executor.shutdown()
        }
    }
    
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }

    var isSettingsOpen by remember { mutableStateOf(false) }
    var isGalleryOpen by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    var hasCameraPermission by remember { mutableStateOf(hasPermission(Manifest.permission.CAMERA)) }
    var hasLocationPermission by remember {
        mutableStateOf(
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
                hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }
    var locationPermissionRequested by rememberSaveable { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    var zoomRatio by remember { mutableStateOf(0f) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var lastPhotoUri by remember { mutableStateOf<android.net.Uri?>(null) }
    LaunchedEffect(Unit) {
        val uri = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            MediaUtils.getLastTakenMediaUri(context)
        }
        lastPhotoUri = uri
    }
    var resolvedAddress by remember { mutableStateOf<String?>(null) }
    
    var location by remember { mutableStateOf(LocationUtils.lastKnownLocation(context)) }
    var currentTime by remember { mutableStateOf(Date()) }
    var isVideoMode by remember { mutableStateOf(false) }
    var recordingUiState by remember { mutableStateOf(VideoRecordingUiState.IDLE) }
    var isPhotoCapturing by remember { mutableStateOf(false) }
    var isPhotoPressActive by remember { mutableStateOf(false) }
    var burstCaptureJob by remember { mutableStateOf<Job?>(null) }
    var showLocationUpdatePopup by remember { mutableStateOf(false) }
    var startVideoAfterAudioPermission by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var cameraRetryToken by remember { mutableIntStateOf(0) }
    
    var deviceOrientationAngle by remember { mutableStateOf(0f) }
    var isFrontFlashActive by remember { mutableStateOf(false) }
    val isCaptureScreenVisible = !isSettingsOpen && !isGalleryOpen

    fun currentOverlay(capturedAt: Date = currentTime): PhotoOverlay = PhotoOverlay(
        color = state.overlayColor,
        location = location,
        capturedAt = capturedAt,
        showUtm = state.showUtm,
        showGeo = state.showGeo,
        showDateTime = state.showDateTime,
        showAddress = state.showAddress,
        showName = state.showName,
        userName = state.userName,
        position = state.position,
        resolvedAddress = resolvedAddress,
        selectedLogo = state.selectedLogo
    )

    fun captureSinglePhoto() {
        if (isVideoMode || isPhotoCapturing || cameraError != null || !hasCameraPermission) return
        isPhotoCapturing = true
        val needsFrontFlash = !state.lensFacingBack &&
            (state.flashMode == CameraFlashMode.ON || state.flashMode == CameraFlashMode.AUTO)
        val shouldWaitForFrontFlash = needsFrontFlash && !isFrontFlashActive
        if (needsFrontFlash) isFrontFlashActive = true

        coroutineScope.launch {
            if (shouldWaitForFrontFlash) delay(300)
            capture.capture(
                context = context,
                overlay = currentOverlay(Date()),
                playSound = state.playShutterSound,
                isFrontCamera = !state.lensFacingBack
            ) { msg, uri ->
                isPhotoCapturing = false
                if (!isPhotoPressActive) isFrontFlashActive = false
                viewModel.setStatus(msg)
                if (uri != null) lastPhotoUri = uri
            }
        }
    }

    fun startPhotoPress() {
        if (isVideoMode || isPhotoPressActive) return
        isPhotoPressActive = true
        captureSinglePhoto()
        burstCaptureJob?.cancel()
        burstCaptureJob = coroutineScope.launch {
            delay(android.view.ViewConfiguration.getLongPressTimeout().toLong())
            if (!isPhotoPressActive) return@launch
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            viewModel.setStatus("Disparo contínuo ativo")
            while (isPhotoPressActive) {
                if (!isPhotoCapturing) captureSinglePhoto()
                delay(35)
            }
        }
    }

    fun endPhotoPress() {
        isPhotoPressActive = false
        burstCaptureJob?.cancel()
        burstCaptureJob = null
        if (!isPhotoCapturing) isFrontFlashActive = false
    }

    fun beginVideoRecording() {
        if (recordingUiState != VideoRecordingUiState.IDLE) return
        recordingUiState = recordingUiState.requestStart()
        viewModel.setStatus("Iniciando gravação...")
        capture.startRecording(
            context = context,
            getOverlayInfo = { currentOverlay(Date()) },
            onRecordingChanged = { active ->
                recordingUiState = recordingUiState.recorderChanged(active)
                if (active) viewModel.setStatus(null)
            },
            onStatus = { msg, uri ->
                viewModel.setStatus(msg)
                if (uri != null) lastPhotoUri = uri
            }
        )
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "Vídeo será gravado sem áudio.", Toast.LENGTH_SHORT).show()
        }
        startVideoAfterAudioPermission = true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(hasCameraPermission, hasLocationPermission) {
        if (hasCameraPermission && !hasLocationPermission && !locationPermissionRequested) {
            locationPermissionRequested = true
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(startVideoAfterAudioPermission) {
        if (startVideoAfterAudioPermission) {
            startVideoAfterAudioPermission = false
            beginVideoRecording()
        }
    }

    fun Context.findActivity(): android.app.Activity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is android.app.Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    DisposableEffect(isFrontFlashActive) {
        if (isFrontFlashActive) {
            val window = context.findActivity()?.window
            val params = window?.attributes
            val originalBrightness = params?.screenBrightness
            params?.screenBrightness = 1.0f
            window?.attributes = params
            onDispose {
                val restoreParams = window?.attributes
                restoreParams?.screenBrightness = originalBrightness ?: android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window?.attributes = restoreParams
            }
        } else {
            onDispose {}
        }
    }

    fun updateAddress(loc: android.location.Location?) {
        if (loc == null) return
        if (!com.saile.shub.photo.location.NetworkUtils.isNetworkAvailable(context)) {
            resolvedAddress = null
            return
        }
        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val geocoder = android.location.Geocoder(context, Locale("pt", "BR"))
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                val addr = addresses?.firstOrNull()
                val newAddress = addr?.let { 
                    val city = it.locality ?: it.subAdminArea
                    val uf = LocationUtils.getStateAcronym(it.adminArea)
                    val cityUf = listOfNotNull(city, uf).joinToString("-")
                    listOfNotNull(it.thoroughfare, it.subLocality, cityUf.takeIf { c -> c.isNotEmpty() }).joinToString(" - ") 
                }
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    resolvedAddress = newAddress
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    resolvedAddress = null
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val listener = object : android.view.OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val newRotation = when (orientation) {
                    in 45..134 -> 90f
                    in 135..224 -> 180f
                    in 225..314 -> 270f
                    else -> 0f
                }
                if (newRotation != deviceOrientationAngle) {
                    deviceOrientationAngle = newRotation
                }
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }
    
    val outputRotation = cameraOutputRotation(deviceOrientationAngle.toInt())

    DisposableEffect(hasLocationPermission) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = android.location.LocationListener { loc -> 
            if (LocationUtils.isBetterLocation(loc, location)) {
                location = loc
                updateAddress(loc)
            }
        }
        val hasFineLocation = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarseLocation = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (hasFineLocation) {
            runCatching { manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener) }
        }
        if (hasFineLocation || hasCoarseLocation) {
            runCatching { manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, listener) }
        }
        onDispose { runCatching { manager.removeUpdates(listener) } }
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Date()
            delay(1000)
        }
    }

    LaunchedEffect(location) {
        updateAddress(location)
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission && location == null) {
            location = LocationUtils.lastKnownLocation(context)
        }
    }

    fun forceLocationUpdate() {
        val hasFineLocation = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarseLocation = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!hasFineLocation && !hasCoarseLocation) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            resolvedAddress = "Sem permissão"
            return
        }
        if (!com.saile.shub.photo.location.NetworkUtils.isNetworkAvailable(context)) {
            Toast.makeText(context, "Sem conexão com a internet. Endereço ficará oculto.", Toast.LENGTH_SHORT).show()
            resolvedAddress = null
        } else {
            resolvedAddress = "Buscando localização..."
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                if (hasFineLocation || hasCoarseLocation) {
                    manager.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, context.mainExecutor) { loc -> 
                        if (loc != null) { location = loc; updateAddress(loc) } else { resolvedAddress = "Sem sinal GPS/Rede" }
                    }
                }
                if (hasFineLocation) {
                    manager.getCurrentLocation(LocationManager.GPS_PROVIDER, null, context.mainExecutor) { loc -> 
                        if (loc != null) { location = loc; updateAddress(loc) } 
                    }
                }
            } else {
                if (hasFineLocation || hasCoarseLocation) {
                    @Suppress("DEPRECATION")
                    manager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, { loc -> location = loc; updateAddress(loc) }, null)
                }
                if (hasFineLocation) {
                    @Suppress("DEPRECATION")
                    manager.requestSingleUpdate(LocationManager.GPS_PROVIDER, { loc -> location = loc; updateAddress(loc) }, null)
                }
            }
        }
        showLocationUpdatePopup = true
    }

    LaunchedEffect(showLocationUpdatePopup) {
        if (showLocationUpdatePopup) {
            delay(2_000)
            showLocationUpdatePopup = false
        }
    }

    LaunchedEffect(zoomRatio) {
        cameraControl?.setLinearZoom(zoomRatio)
    }

    val isRecording = recordingUiState.showsRecording
    val controlsLocked = recordingUiState.locksControls || isPhotoCapturing || isPhotoPressActive
    val shouldEnableVideoTorch = isCaptureScreenVisible &&
        isVideoMode &&
        state.lensFacingBack &&
        state.flashMode == CameraFlashMode.ON

    val statusMsg = state.status
    LaunchedEffect(statusMsg) {
        if (statusMsg != null) {
            delay(3000)
            viewModel.setStatus(null)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            PhotoTopBar(
                flashMode = state.flashMode,
                isRecording = isRecording,
                controlsLocked = controlsLocked,
                iconRotation = deviceOrientationAngle,
                onCycleFlash = viewModel::cycleFlash,
                onOpenSettings = { isSettingsOpen = true }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.DarkGray)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            val newZoom = (zoomRatio + (zoom - 1f)).coerceIn(0f, 1f)
                            zoomRatio = newZoom
                        }
                    }
            ) {
                if (hasCameraPermission) {
                    CameraPreview(
                        capture = capture,
                        lensFacingBack = state.lensFacingBack,
                        flashMode = state.flashMode,
                        isVideoMode = isVideoMode,
                        outputRotation = outputRotation,
                        torchEnabled = shouldEnableVideoTorch,
                        retryToken = cameraRetryToken,
                        getOverlayInfo = { currentOverlay() },
                        onCameraReady = {
                            cameraControl = it
                            cameraError = null
                        },
                        onCameraError = { message -> cameraError = message }
                    )
                } else {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("A câmera precisa de permissão para funcionar.", color = Color.White)
                        Button(onClick = {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }) {
                            Text("Permitir câmera")
                        }
                    }
                }

                cameraError?.let { error ->
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color(0xCC000000), RoundedCornerShape(12.dp))
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(error, color = Color.White)
                        Button(onClick = {
                            cameraError = null
                            cameraRetryToken++
                        }) {
                            Text("Tentar novamente")
                        }
                    }
                }
                
                state.status?.let { 
                    Text(it, color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).background(Color(0x88000000), RoundedCornerShape(8.dp)).padding(8.dp)) 
                }
            }

            PhotoBottomBar(
                lastPhotoUri = lastPhotoUri,
                imageLoader = imageLoader,
                iconRotation = deviceOrientationAngle,
                isVideoMode = isVideoMode,
                isRecording = isRecording,
                controlsLocked = controlsLocked,
                captureEnabled = recordingUiState.captureEnabled &&
                    hasCameraPermission &&
                    cameraError == null,
                onOpenGallery = {
                    isGalleryOpen = true
                },
                onToggleVideoMode = {
                    if (!controlsLocked) isVideoMode = !isVideoMode
                },
                onCapture = {
                    if (isVideoMode) {
                        if (recordingUiState == VideoRecordingUiState.RECORDING) {
                            recordingUiState = recordingUiState.requestStop()
                            capture.stopRecording()
                        } else if (recordingUiState == VideoRecordingUiState.IDLE) {
                            if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                                beginVideoRecording()
                            } else {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    } else {
                        captureSinglePhoto()
                    }
                },
                onPhotoPressStart = ::startPhotoPress,
                onPhotoPressEnd = ::endPhotoPress,
                onToggleLens = {
                    if (!controlsLocked) viewModel.toggleLens()
                },
                onUpdateLocation = {
                    if (!controlsLocked) forceLocationUpdate()
                }
            )
        }

        if (isSettingsOpen) {
            PhotoSettingsDrawer(
                state = state,
                viewModel = viewModel,
                onClose = {
                    viewModel.savePreferences()
                    isSettingsOpen = false
                }
            )
        }

        if (isGalleryOpen) {
            com.saile.shub.photo.ui.GalleryScreen(
                onClose = { 
                    isGalleryOpen = false 
                    lastPhotoUri = MediaUtils.getLastTakenMediaUri(context)
                }
            )
        }

        if (showLocationUpdatePopup) {
            IconStatusPopup(
                message = "Atualizando localização...",
                icon = Icons.Filled.LocationSearching
            )
        }
    }
}
