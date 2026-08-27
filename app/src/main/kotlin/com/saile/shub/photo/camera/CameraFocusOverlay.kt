package com.saile.shub.photo.camera

import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.camera.core.CameraControl
import androidx.camera.core.FocusMeteringAction
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Instala foco/exposição por toque e desenha o marcador transitório da ação. */
@Composable
internal fun CameraFocusOverlay(
    previewView: PreviewView,
    cameraControl: CameraControl?,
) {
    val context = LocalContext.current
    var focusMarker by remember { mutableStateOf<Offset?>(null) }

    DisposableEffect(previewView, cameraControl) {
        var downX = 0f
        var downY = 0f
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        val listener = android.view.View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                }

                MotionEvent.ACTION_UP -> {
                    val isTap = abs(event.x - downX) <= touchSlop &&
                        abs(event.y - downY) <= touchSlop
                    if (isTap) {
                        val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
                        val action = FocusMeteringAction.Builder(
                            point,
                            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
                        )
                            .setAutoCancelDuration(3, TimeUnit.SECONDS)
                            .build()
                        cameraControl?.startFocusAndMetering(action)
                        focusMarker = Offset(event.x, event.y)
                    }
                }
            }
            false
        }
        previewView.setOnTouchListener(listener)
        onDispose { previewView.setOnTouchListener(null) }
    }

    LaunchedEffect(focusMarker) {
        if (focusMarker != null) {
            delay(900)
            focusMarker = null
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        focusMarker?.let { marker ->
            drawCircle(
                color = Color.White,
                radius = 24.dp.toPx(),
                center = marker,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}
