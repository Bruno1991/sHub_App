package com.saile.shub.gis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.pow

@Composable
fun GisScaleBar(
    latitude: Double,
    zoomLevel: Double,
    modifier: Modifier = Modifier
) {
    if (zoomLevel < 1.0) return

    val latRad = Math.toRadians(latitude)
    val metersPerPixel = (156543.03392 * cos(latRad)) / 2.0.pow(zoomLevel)

    // Desired bar pixel width around 100-140 dp
    val targetPixels = 120.0
    val rawMeters = targetPixels * metersPerPixel

    // Find nearest round distance
    val candidates = listOf(
        1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0,
        1000.0, 2000.0, 5000.0, 10000.0, 20000.0, 50000.0, 100000.0, 200000.0, 500000.0
    )
    val chosenMeters = candidates.minByOrNull { kotlin.math.abs(it - rawMeters) } ?: 100.0
    val actualPixels = (chosenMeters / metersPerPixel).coerceIn(40.0, 220.0).toFloat()

    val labelText = if (chosenMeters >= 1000.0) {
        "${(chosenMeters / 1000.0).toInt()} km"
    } else {
        "${chosenMeters.toInt()} m"
    }

    Box(
        modifier = modifier
            .background(Color(0xCCFFFFFF), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(actualPixels.dp)
        ) {
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            ) {
                val w = size.width
                val h = size.height

                // Baseline
                drawLine(
                    color = Color.Black,
                    start = Offset(0f, h),
                    end = Offset(w, h),
                    strokeWidth = 2.dp.toPx()
                )
                // Left tick
                drawLine(
                    color = Color.Black,
                    start = Offset(0f, 0f),
                    end = Offset(0f, h),
                    strokeWidth = 2.dp.toPx()
                )
                // Middle tick
                drawLine(
                    color = Color.Black,
                    start = Offset(w / 2f, h / 2f),
                    end = Offset(w / 2f, h),
                    strokeWidth = 2.dp.toPx()
                )
                // Right tick
                drawLine(
                    color = Color.Black,
                    start = Offset(w, 0f),
                    end = Offset(w, h),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
    }
}

