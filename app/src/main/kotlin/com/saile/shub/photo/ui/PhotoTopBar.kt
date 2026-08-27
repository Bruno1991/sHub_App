package com.saile.shub.photo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.saile.shub.photo.CameraFlashMode

@Composable
fun PhotoTopBar(
    flashMode: CameraFlashMode,
    isRecording: Boolean,
    controlsLocked: Boolean,
    iconRotation: Float,
    onCycleFlash: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(80.dp)
            .background(Color.Black)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCycleFlash, enabled = !controlsLocked) {
            val icon = when (flashMode) {
                CameraFlashMode.AUTO -> Icons.Filled.FlashAuto
                CameraFlashMode.ON -> Icons.Filled.FlashOn
                CameraFlashMode.OFF -> Icons.Filled.FlashOff
            }
            Icon(icon, contentDescription = "Flash", tint = Color.White, modifier = Modifier.rotate(iconRotation))
        }
        
        if (isRecording) {
            var recordingSeconds by remember { mutableStateOf(0) }
            LaunchedEffect(isRecording) {
                recordingSeconds = 0
                while (true) {
                    delay(1000)
                    recordingSeconds++
                }
            }
            val min = recordingSeconds / 60
            val sec = recordingSeconds % 60
            val timeStr = "%02d:%02d".format(min, sec)
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.background(Color(0x88000000), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Box(modifier = Modifier.size(8.dp).background(Color.Red, CircleShape))
                Spacer(modifier = Modifier.width(8.dp))
                Text(timeStr, color = Color.White, fontWeight = FontWeight.Bold)
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        
        
        IconButton(onClick = onOpenSettings, enabled = !controlsLocked) {
            Icon(Icons.Filled.Settings, contentDescription = "Configurações", tint = Color.White, modifier = Modifier.rotate(iconRotation))
        }
    }
}
