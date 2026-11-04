package com.saile.shub.gis

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.saile.shub.core.geo.GeoCoordinate
import com.saile.shub.core.geo.UtmCoordinate
import com.saile.shub.core.geo.UtmConverter
import com.saile.shub.photo.location.LocationUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.abs

data class GisPositioningInfo(
    val location: Location?,
    val latitude: Double,
    val longitude: Double,
    val utm: UtmCoordinate?,
    val altitudeMeters: Double?,
    val accuracyMeters: Float?,
    val speedKmh: Float?,
    val bearingDegrees: Float?,
    val provider: String,
    val isMock: Boolean = false
) {
    val accuracyText: String
        get() = if (accuracyMeters != null) "± %.1f m".format(Locale.US, accuracyMeters) else "± -- m"

    val geoText: String
        get() = "%.6f, %.6f".format(Locale.US, latitude, longitude)

    val utmText: String
        get() = utm?.let {
            val band = UtmConverter.getUtmBand(latitude)
            "${it.zone}$band %.0f %.0f".format(Locale.US, it.easting, it.northing)
        } ?: "--"
}

class GisLocationTracker(private val context: Context) : LocationListener, SensorEventListener {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val _positioningInfo = MutableStateFlow<GisPositioningInfo?>(null)
    val positioningInfo: StateFlow<GisPositioningInfo?> = _positioningInfo.asStateFlow()

    private val _compassHeading = MutableStateFlow(0f)
    val compassHeading: StateFlow<Float> = _compassHeading.asStateFlow()

    private var isTracking = false
    private var isCompassActive = false

    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var lastEmittedHeading = 0f

    fun startTracking() {
        if (isTracking) return
        isTracking = true

        // 1. Initial location from sFoto LocationUtils
        val initialLoc = LocationUtils.lastKnownLocation(context)
        if (initialLoc != null) {
            updateLocation(initialLoc)
        }

        // 2. Request fine location updates (authoritative GPS + fallback Network)
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            runCatching {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    1f,
                    this
                )
            }
            runCatching {
                locationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2000L,
                    2f,
                    this
                )
            }
        }
    }

    fun setCompassEnabled(enabled: Boolean) {
        if (isCompassActive == enabled) return
        isCompassActive = enabled
        if (enabled) {
            sensorManager?.let { sm ->
                sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                    sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
                }
                sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let {
                    sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
                }
            }
        } else {
            runCatching { sensorManager?.unregisterListener(this) }
        }
    }

    fun stopTracking() {
        if (!isTracking) return
        isTracking = false
        setCompassEnabled(false)
        runCatching { locationManager?.removeUpdates(this) }
    }

    override fun onLocationChanged(location: Location) {
        val current = _positioningInfo.value?.location
        if (current == null) {
            updateLocation(location)
            return
        }

        // Field navigation rule:
        // Always accept fresh GPS fixes. Only accept Network if GPS is older than 8s.
        val isNewer = location.time >= current.time - 1000
        val isGps = location.provider == LocationManager.GPS_PROVIDER
        val currentIsGps = current.provider == LocationManager.GPS_PROVIDER
        val currentAgeMs = System.currentTimeMillis() - current.time

        if (isGps) {
            if (isNewer || currentAgeMs > 4000) {
                updateLocation(location)
            }
        } else {
            // Network provider
            if (!currentIsGps || currentAgeMs > 8000) {
                updateLocation(location)
            }
        }
    }

    private fun updateLocation(loc: Location) {
        val utm = runCatching {
            UtmConverter.geoToUtm(GeoCoordinate(loc.latitude, loc.longitude))
        }.getOrNull()

        _positioningInfo.value = GisPositioningInfo(
            location = loc,
            latitude = loc.latitude,
            longitude = loc.longitude,
            utm = utm,
            altitudeMeters = if (loc.hasAltitude()) loc.altitude else null,
            accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else null,
            speedKmh = if (loc.hasSpeed()) loc.speed * 3.6f else null,
            bearingDegrees = if (loc.hasBearing()) loc.bearing else null,
            provider = loc.provider ?: "GPS",
            isMock = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) loc.isMock else loc.isFromMockProvider
        )
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!isCompassActive) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                // Low-pass filter for accelerometer
                for (i in 0..2) {
                    accelerometerReading[i] = accelerometerReading[i] * 0.8f + event.values[i] * 0.2f
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                // Low-pass filter for magnetometer
                for (i in 0..2) {
                    magnetometerReading[i] = magnetometerReading[i] * 0.8f + event.values[i] * 0.2f
                }
            }
        }

        if (SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            val azimuthRad = orientationAngles[0]
            val azimuthDeg = (Math.toDegrees(azimuthRad.toDouble()).toFloat() + 360f) % 360f

            // Deadband: only emit if heading delta > 2 degrees to avoid unnecessary redraws
            val diff = abs(azimuthDeg - lastEmittedHeading)
            val minDiff = if (diff > 180f) 360f - diff else diff
            if (minDiff >= 2.0f) {
                lastEmittedHeading = azimuthDeg
                _compassHeading.value = azimuthDeg
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
}
