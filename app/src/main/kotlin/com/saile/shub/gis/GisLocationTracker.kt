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
        get() = utm?.let { "${it.zone}S %.0f %.0f".format(Locale.US, it.easting, it.northing) } ?: "--"
}

class GisLocationTracker(private val context: Context) : LocationListener, SensorEventListener {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val _positioningInfo = MutableStateFlow<GisPositioningInfo?>(null)
    val positioningInfo: StateFlow<GisPositioningInfo?> = _positioningInfo.asStateFlow()

    private val _compassHeading = MutableStateFlow(0f)
    val compassHeading: StateFlow<Float> = _compassHeading.asStateFlow()

    private var isTracking = false

    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    fun startTracking() {
        if (isTracking) return
        isTracking = true

        // 1. Initial location from sFoto LocationUtils
        val initialLoc = LocationUtils.lastKnownLocation(context)
        if (initialLoc != null) {
            updateLocation(initialLoc)
        }

        // 2. Request fine location updates
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

        // 3. Register sensors for compass heading
        sensorManager?.let { sm ->
            sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
            sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let {
                sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    fun stopTracking() {
        if (!isTracking) return
        isTracking = false
        runCatching { locationManager?.removeUpdates(this) }
        runCatching { sensorManager?.unregisterListener(this) }
    }

    override fun onLocationChanged(location: Location) {
        val current = _positioningInfo.value?.location
        if (LocationUtils.isBetterLocation(location, current)) {
            updateLocation(location)
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
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelerometerReading, 0, 3)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetometerReading, 0, 3)
            }
        }

        if (SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            val azimuthRad = orientationAngles[0]
            val azimuthDeg = (Math.toDegrees(azimuthRad.toDouble()).toFloat() + 360f) % 360f
            _compassHeading.value = azimuthDeg
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
}

