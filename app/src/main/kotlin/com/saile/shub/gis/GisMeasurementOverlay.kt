package com.saile.shub.gis

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.Typeface
import android.view.MotionEvent
import com.saile.shub.core.geo.GeoCoordinate
import com.saile.shub.core.geo.UtmConverter
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import java.util.Locale
import kotlin.math.abs

class GisMeasurementOverlay(
    var onMeasurementChanged: ((totalDistanceMeters: Double, areaSquareMeters: Double, pointCount: Int) -> Unit)? = null
) : Overlay() {

    var isActive: Boolean = false
        set(value) {
            field = value
            if (!value) {
                clear()
            }
        }

    val points = mutableListOf<GeoPoint>()

    private val linePaint = Paint().apply {
        color = Color.parseColor("#FF9100") // Vibrant Amber / Orange
        strokeWidth = 6f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val closingLinePaint = Paint().apply {
        color = Color.parseColor("#FFAB40")
        strokeWidth = 4f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
        isAntiAlias = true
    }

    private val fillPaint = Paint().apply {
        color = Color.parseColor("#33FF9100") // Semi-transparent amber polygon fill
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val pinFillPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val pinBorderPaint = Paint().apply {
        color = Color.parseColor("#FF6D00")
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val pinTextPaint = Paint().apply {
        color = Color.parseColor("#212121")
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val distanceLabelBgPaint = Paint().apply {
        color = Color.parseColor("#E6212121")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val distanceLabelTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val p1 = Point()
    private val p2 = Point()
    private val polyPath = Path()

    fun addPoint(geoPoint: GeoPoint, mapView: MapView? = null) {
        points.add(geoPoint)
        notifyChanged()
        mapView?.postInvalidate()
    }

    fun removeLastPoint(mapView: MapView? = null) {
        if (points.isNotEmpty()) {
            points.removeAt(points.size - 1)
            notifyChanged()
            mapView?.postInvalidate()
        }
    }

    fun clear(mapView: MapView? = null) {
        points.clear()
        notifyChanged()
        mapView?.postInvalidate()
    }

    fun getTotalDistance(): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until points.size - 1) {
            total += points[i].distanceToAsDouble(points[i + 1])
        }
        return total
    }

    fun getArea(): Double {
        if (points.size < 3) return 0.0
        // Shoelace formula with UTM projection
        val utmCoords = points.mapNotNull {
            runCatching {
                UtmConverter.geoToUtm(GeoCoordinate(it.latitude, it.longitude))
            }.getOrNull()
        }
        if (utmCoords.size < 3) return 0.0

        var sum = 0.0
        val n = utmCoords.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            sum += utmCoords[i].easting * utmCoords[j].northing
            sum -= utmCoords[j].easting * utmCoords[i].northing
        }
        return abs(sum) / 2.0
    }

    private fun notifyChanged() {
        onMeasurementChanged?.invoke(getTotalDistance(), getArea(), points.size)
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || !isActive || points.isEmpty()) return
        val pj = mapView.projection ?: return

        // 1. Draw polygon fill if 3 or more points
        if (points.size >= 3) {
            polyPath.reset()
            pj.toPixels(points[0], p1)
            polyPath.moveTo(p1.x.toFloat(), p1.y.toFloat())
            for (i in 1 until points.size) {
                pj.toPixels(points[i], p2)
                polyPath.lineTo(p2.x.toFloat(), p2.y.toFloat())
            }
            polyPath.close()
            canvas.drawPath(polyPath, fillPaint)

            // Closing line
            pj.toPixels(points.last(), p1)
            pj.toPixels(points.first(), p2)
            canvas.drawLine(p1.x.toFloat(), p1.y.toFloat(), p2.x.toFloat(), p2.y.toFloat(), closingLinePaint)
        }

        // 2. Draw lines between sequential vertices
        for (i in 0 until points.size - 1) {
            pj.toPixels(points[i], p1)
            pj.toPixels(points[i + 1], p2)
            canvas.drawLine(p1.x.toFloat(), p1.y.toFloat(), p2.x.toFloat(), p2.y.toFloat(), linePaint)

            // Segment distance label in center of segment
            val segDist = points[i].distanceToAsDouble(points[i + 1])
            val label = formatDistance(segDist)
            val midX = (p1.x + p2.x) / 2f
            val midY = (p1.y + p2.y) / 2f

            canvas.drawRoundRect(midX - 42f, midY - 20f, midX + 42f, midY + 12f, 10f, 10f, distanceLabelBgPaint)
            canvas.drawText(label, midX, midY + 4f, distanceLabelTextPaint)
        }

        // 3. Draw numbered pin markers at each vertex
        for ((idx, pt) in points.withIndex()) {
            pj.toPixels(pt, p1)
            val cx = p1.x.toFloat()
            val cy = p1.y.toFloat()
            canvas.drawCircle(cx, cy, 18f, pinFillPaint)
            canvas.drawCircle(cx, cy, 18f, pinBorderPaint)
            canvas.drawText("${idx + 1}", cx, cy + 8f, pinTextPaint)
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        if (!isActive) return false
        val pj = mapView.projection ?: return false
        val geoPoint = pj.fromPixels(e.x.toInt(), e.y.toInt()) as? GeoPoint ?: return false
        addPoint(geoPoint, mapView)
        return true
    }

    companion object {
        fun formatDistance(meters: Double): String {
            return if (meters >= 1000.0) {
                String.format(Locale.US, "%.2f km", meters / 1000.0)
            } else {
                String.format(Locale.US, "%.1f m", meters)
            }
        }

        fun formatArea(sqMeters: Double): String {
            return if (sqMeters >= 10000.0) {
                val ha = sqMeters / 10000.0
                String.format(Locale.US, "%.2f ha (%.0f m²)", ha, sqMeters)
            } else {
                String.format(Locale.US, "%.1f m²", sqMeters)
            }
        }
    }
}

