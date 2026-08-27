package com.saile.shub.photo.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage

@Composable
fun PhotoBottomBar(
    lastPhotoUri: Uri?,
    imageLoader: ImageLoader,
    iconRotation: Float,
    isVideoMode: Boolean,
    isRecording: Boolean,
    controlsLocked: Boolean,
    captureEnabled: Boolean,
    onOpenGallery: () -> Unit,
    onToggleVideoMode: () -> Unit,
    onCapture: () -> Unit,
    onPhotoPressStart: () -> Unit,
    onPhotoPressEnd: () -> Unit,
    onToggleLens: () -> Unit,
    onUpdateLocation: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 32.dp)
            .height(120.dp)
            .background(Color.Black)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Gallery Thumbnail
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray)
                .clickable(enabled = !controlsLocked) { onOpenGallery() },
            contentAlignment = Alignment.Center
        ) {
            if (lastPhotoUri != null) {
                AsyncImage(
                    model = lastPhotoUri,
                    imageLoader = imageLoader,
                    contentDescription = "Galeria",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().rotate(iconRotation)
                )
            } else {
                Icon(Icons.Outlined.Photo, contentDescription = "Galeria", tint = Color.White, modifier = Modifier.rotate(iconRotation))
            }
        }
        
        
        IconButton(onClick = onToggleVideoMode, enabled = !controlsLocked) {
            Icon(if (isVideoMode) Icons.Filled.CameraAlt else Icons.Filled.Videocam, contentDescription = "Modo", tint = Color.White, modifier = Modifier.size(32.dp).rotate(iconRotation))
        }
        
        // Capture Button (Round)
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(if (isRecording) Color.Red else Color.White, CircleShape)
                .pointerInput(captureEnabled, isVideoMode) {
                    detectTapGestures(
                        onPress = {
                            if (captureEnabled) {
                                if (isVideoMode) {
                                    onCapture()
                                    tryAwaitRelease()
                                } else {
                                    onPhotoPressStart()
                                    try {
                                        tryAwaitRelease()
                                    } finally {
                                        onPhotoPressEnd()
                                    }
                                }
                            }
                        }
                    )
                }
                .semantics {
                    contentDescription = if (isVideoMode) "Gravar Vídeo" else "Capturar foto"
                    onClick {
                        if (captureEnabled) {
                            onCapture()
                            true
                        } else {
                            false
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (isRecording) {
                Box(modifier = Modifier.size(24.dp).background(Color.White, RoundedCornerShape(4.dp)))
            } else {
                Box(modifier = Modifier.size(60.dp).background(if (isVideoMode) Color.Red else Color.White, CircleShape).border(2.dp, Color.Black, CircleShape))
            }
        }
        
        IconButton(onClick = onToggleLens, enabled = !controlsLocked) {
            Icon(Icons.Filled.Cameraswitch, contentDescription = "Inverter", tint = Color.White, modifier = Modifier.size(32.dp).rotate(iconRotation))
        }

        IconButton(onClick = onUpdateLocation, enabled = !controlsLocked) {
            Icon(Icons.Filled.GpsFixed, contentDescription = "Forçar Localização", tint = Color.White, modifier = Modifier.size(32.dp).rotate(iconRotation))
        }
    }
}
