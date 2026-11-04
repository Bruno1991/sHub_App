package com.saile.shub.gis

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.Typeface
import android.util.Log
import android.view.MotionEvent
import com.saile.shub.gis.domain.GisElementType
import com.saile.shub.gis.domain.GisRenderElement
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class GisVectorOverlay(
    private val onElementTapped: (GisRenderElement) -> Unit,
    var onElementLongPressed: ((GisRenderElement) -> Unit)? = null
) : Overlay() {

    // Lines - Authentic QGIS / QField layer colors
    private val linePaintAT = Paint().apply {
        color = Color.parseColor("#FC07E3") // High Voltage (Vão AT)
        strokeWidth = 5.5f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val linePaintMT = Paint().apply {
        color = Color.parseColor("#052EF9") // Medium Voltage (Vão MT)
        strokeWidth = 3.8f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val linePaintBT = Paint().apply {
        color = Color.parseColor("#19F805") // Low Voltage (Vão BT)
        strokeWidth = 2.6f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    // Points - Authentic QGIS / QField symbology colors
    private val subPaint = Paint().apply {
        color = Color.parseColor("#00F200") // Substations
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val towerPaint = Paint().apply {
        color = Color.WHITE // Towers
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val trafoPaint = Paint().apply {
        color = Color.parseColor("#0B0503") // Transformers
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val switchPaint = Paint().apply {
        color = Color.parseColor("#FB0313") // Switches
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val regulatorPaint = Paint().apply {
        color = Color.parseColor("#F59207") // Regulators
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val capacitorPaint = Paint().apply {
        color = Color.parseColor("#01DDFF") // Capacitors
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val polePaint = Paint().apply {
        color = Color.WHITE // Poles
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // High-contrast outlines
    private val darkBorderPaint = Paint().apply {
        color = Color.parseColor("#212121")
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val whiteBorderPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    // Selected highlight ring
    private val selectionRingPaint = Paint().apply {
        color = Color.parseColor("#00E5FF")
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    // Canvas Text Labels (active at zoom >= 15.0)
    private val labelOutlinePaint = Paint().apply {
        color = Color.WHITE
        textSize = 28f
        typeface = Typeface.DEFAULT_BOLD
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    private val labelTextPaint = Paint().apply {
        color = Color.parseColor("#1A1A1A")
        textSize = 28f
        typeface = Typeface.DEFAULT_BOLD
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Authentic QField GNSS / GPS Position Marker
    private val userAccuracyFillPaint = Paint().apply {
        color = Color.argb(35, 33, 150, 243) // Translucent light blue
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val userAccuracyStrokePaint = Paint().apply {
        color = Color.argb(130, 33, 150, 243)
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val userWhiteBorderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
        setShadowLayer(6f, 0f, 2f, Color.argb(100, 0, 0, 0))
    }

    private val userBlueDotPaint = Paint().apply {
        color = Color.parseColor("#1976D2") // QField GNSS Blue
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val userHeadingConePaint = Paint().apply {
        color = Color.argb(70, 33, 150, 243)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private var currentElements: List<GisRenderElement> = emptyList()
    private var currentZoom: Double = 9.0
    private var selectedFid: Long? = null

    // Density and metrics cache
    private var density = 1.0f
    private var densityConfigured = false
    private var lastDrawLogTime = 0L

    // Real-time GPS Location state
    private var userPosition: GisPositioningInfo? = null
    private var userHeadingDegrees: Float = 0f

    // Reusable objects to eliminate per-frame GC allocations
    private val p1 = Point()
    private val p2 = Point()
    private val userPosPoint = Point()
    private val tempGeoPoint = GeoPoint(0.0, 0.0)
    private val headingPath = Path()

    private fun ensureDensity(mapView: MapView) {
        if (densityConfigured) return
        density = mapView.context.resources.displayMetrics.density
        linePaintAT.strokeWidth = 5.5f * density
        linePaintMT.strokeWidth = 3.8f * density
        linePaintBT.strokeWidth = 2.6f * density
        darkBorderPaint.strokeWidth = 2f * density
        whiteBorderPaint.strokeWidth = 2f * density
        selectionRingPaint.strokeWidth = 4f * density
        labelOutlinePaint.textSize = 14f * density
        labelOutlinePaint.strokeWidth = 3.5f * density
        labelTextPaint.textSize = 14f * density
        userAccuracyStrokePaint.strokeWidth = 2f * density
        densityConfigured = true
    }

    fun updateElements(elements: List<GisRenderElement>, zoom: Double = currentZoom, selectedId: Long? = null) {
        currentElements = elements
        currentZoom = zoom
        selectedFid = selectedId
    }

    fun updateUserPosition(info: GisPositioningInfo?, heading: Float = userHeadingDegrees) {
        userPosition = info
        userHeadingDegrees = heading
    }

    fun updateHeading(heading: Float) {
        userHeadingDegrees = heading
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val pj = mapView.projection ?: return

        ensureDensity(mapView)
        val zoomDouble = mapView.zoomLevelDouble
        val screenW = mapView.width.toFloat()
        val screenH = mapView.height.toFloat()

        val now = System.currentTimeMillis()
        if (now - lastDrawLogTime > 1000L) {
            lastDrawLogTime = now
            Log.i("ShubGis", "Overlay drawn: ${currentElements.size} elements, zoom=$zoomDouble")
        }

        // 1. Draw Lines First (AT, MT, BT) with viewport clipping
        for (elem in currentElements) {
            if (elem is GisRenderElement.Line) {
                val paint = when (elem.type) {
                    GisElementType.SPAN_AT -> linePaintAT
                    GisElementType.SPAN_MT -> linePaintMT
                    GisElementType.SPAN_BT -> linePaintBT
                    else -> linePaintMT
                }

                val pts = elem.points
                val count = pts.size
                if (count < 2) continue

                for (i in 0 until count - 1) {
                    val ptA = pts[i]
                    val ptB = pts[i + 1]

                    tempGeoPoint.setCoords(ptA.first, ptA.second)
                    pj.toPixels(tempGeoPoint, p1)

                    tempGeoPoint.setCoords(ptB.first, ptB.second)
                    pj.toPixels(tempGeoPoint, p2)

                    // Fast screen bounding box clipping
                    if ((p1.x < -100 && p2.x < -100) || (p1.x > screenW + 100 && p2.x > screenW + 100) ||
                        (p1.y < -100 && p2.y < -100) || (p1.y > screenH + 100 && p2.y > screenH + 100)) {
                        continue
                    }

                    canvas.drawLine(p1.x.toFloat(), p1.y.toFloat(), p2.x.toFloat(), p2.y.toFloat(), paint)

                    if (elem.fid == selectedFid) {
                        canvas.drawLine(p1.x.toFloat(), p1.y.toFloat(), p2.x.toFloat(), p2.y.toFloat(), selectionRingPaint)
                    }
                }
            }
        }

        // 2. Draw Point Assets (Substations, Towers, Transformers, Switches, Poles)
        for (elem in currentElements) {
            if (elem is GisRenderElement.Point) {
                tempGeoPoint.setCoords(elem.latitude, elem.longitude)
                pj.toPixels(tempGeoPoint, p1)

                val cx = p1.x.toFloat()
                val cy = p1.y.toFloat()

                // Skip points outside screen viewport
                if (cx < -60f || cx > screenW + 60f || cy < -60f || cy > screenH + 60f) {
                    continue
                }

                val paint: Paint
                val radius: Float
                val isWhite: Boolean

                when (elem.type) {
                    GisElementType.SUBSTATION -> { paint = subPaint; radius = 13f * density; isWhite = false }
                    GisElementType.TOWER -> { paint = towerPaint; radius = 8.5f * density; isWhite = true }
                    GisElementType.TRANSFORMER -> { paint = trafoPaint; radius = 7.5f * density; isWhite = false }
                    GisElementType.SWITCH -> { paint = switchPaint; radius = 7f * density; isWhite = false }
                    GisElementType.REGULATOR -> { paint = regulatorPaint; radius = 7f * density; isWhite = false }
                    GisElementType.CAPACITOR -> { paint = capacitorPaint; radius = 7f * density; isWhite = false }
                    GisElementType.POLE -> { paint = polePaint; radius = 4.5f * density; isWhite = true }
                    else -> { paint = polePaint; radius = 4.5f * density; isWhite = true }
                }

                // Outer border for contrast on satellite & vector maps
                val border = if (isWhite) darkBorderPaint else whiteBorderPaint
                canvas.drawCircle(cx, cy, radius + 1.5f * density, border)
                canvas.drawCircle(cx, cy, radius, paint)

                // Highlight ring if element is selected
                if (elem.fid == selectedFid) {
                    canvas.drawCircle(cx, cy, radius + 8f * density, selectionRingPaint)
                }

                // Text labels when zoomed in (zoom >= 15.0) like QField
                if (zoomDouble >= 15.0 && elem.type != GisElementType.POLE) {
                    val label = elem.title
                    if (label.isNotBlank()) {
                        val textY = cy - radius - 8f * density
                        canvas.drawText(label, cx + 6f * density, textY, labelOutlinePaint)
                        canvas.drawText(label, cx + 6f * density, textY, labelTextPaint)
                    }
                }
            }
        }

        // 3. Draw QField GNSS User Location Marker with Accuracy Ring on TOP
        val pos = userPosition
        if (pos != null) {
            tempGeoPoint.setCoords(pos.latitude, pos.longitude)
            pj.toPixels(tempGeoPoint, userPosPoint)

            val ux = userPosPoint.x.toFloat()
            val uy = userPosPoint.y.toFloat()

            // If user location is within visible screen bounds
            if (ux >= -200f && ux <= screenW + 200f && uy >= -200f && uy <= screenH + 200f) {
                // Accuracy circle
                val acc = pos.accuracyMeters ?: 12f
                val accRadiusPixels = (pj.metersToEquatorPixels(acc)).coerceIn(16f, 500f)

                canvas.drawCircle(ux, uy, accRadiusPixels, userAccuracyFillPaint)
                canvas.drawCircle(ux, uy, accRadiusPixels, userAccuracyStrokePaint)

                // Heading / Bearing cone if available
                val bearing = pos.bearingDegrees ?: (if (userHeadingDegrees > 0f) userHeadingDegrees else null)
                if (bearing != null) {
                    val rad = Math.toRadians((bearing - 90.0).toDouble())
                    val coneLength = 36f * density
                    val coneSpread = Math.toRadians(25.0)

                    val x1 = ux + (coneLength * cos(rad - coneSpread)).toFloat()
                    val y1 = uy + (coneLength * sin(rad - coneSpread)).toFloat()
                    val x2 = ux + (coneLength * cos(rad + coneSpread)).toFloat()
                    val y2 = uy + (coneLength * sin(rad + coneSpread)).toFloat()

                    headingPath.reset()
                    headingPath.moveTo(ux, uy)
                    headingPath.lineTo(x1, y1)
                    headingPath.lineTo(x2, y2)
                    headingPath.close()

                    canvas.drawPath(headingPath, userHeadingConePaint)
                }

                // Outer white circle
                canvas.drawCircle(ux, uy, 12f * density, userWhiteBorderPaint)
                // Inner vibrant blue dot
                canvas.drawCircle(ux, uy, 8.5f * density, userBlueDotPaint)
                // White center core
                canvas.drawCircle(ux, uy, 3.5f * density, userWhiteBorderPaint)
            }
        }
    }

    private fun findNearestElement(tapX: Double, tapY: Double, mapView: MapView, touchThreshold: Double = 42.0): GisRenderElement? {
        val pj = mapView.projection ?: return null

        // 1. Check points first (higher touch priority)
        var closestPoint: GisRenderElement.Point? = null
        var closestPointDist = Double.MAX_VALUE

        for (elem in currentElements) {
            if (elem is GisRenderElement.Point) {
                tempGeoPoint.setCoords(elem.latitude, elem.longitude)
                pj.toPixels(tempGeoPoint, p1)
                val dist = hypot(p1.x.toDouble() - tapX, p1.y.toDouble() - tapY)
                if (dist < touchThreshold && dist < closestPointDist) {
                    closestPoint = elem
                    closestPointDist = dist
                }
            }
        }

        if (closestPoint != null) {
            return closestPoint
        }

        // 2. Check lines
        var closestLine: GisRenderElement.Line? = null
        var closestLineDist = Double.MAX_VALUE
        for (elem in currentElements) {
            if (elem is GisRenderElement.Line) {
                val pts = elem.points
                for (i in 0 until pts.size - 1) {
                    tempGeoPoint.setCoords(pts[i].first, pts[i].second)
                    pj.toPixels(tempGeoPoint, p1)
                    tempGeoPoint.setCoords(pts[i + 1].first, pts[i + 1].second)
                    pj.toPixels(tempGeoPoint, p2)
                    val dist = distanceToSegment(tapX, tapY, p1.x.toDouble(), p1.y.toDouble(), p2.x.toDouble(), p2.y.toDouble())
                    if (dist < touchThreshold && dist < closestLineDist) {
                        closestLine = elem
                        closestLineDist = dist
                    }
                }
            }
        }

        return closestLine
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val hit = findNearestElement(e.x.toDouble(), e.y.toDouble(), mapView)
        if (hit != null) {
            selectedFid = hit.fid
            onElementTapped(hit)
            return true
        }
        return false
    }

    override fun onLongPress(e: MotionEvent, mapView: MapView): Boolean {
        val hit = findNearestElement(e.x.toDouble(), e.y.toDouble(), mapView, touchThreshold = 48.0)
        if (hit != null) {
            selectedFid = hit.fid
            val callback = onElementLongPressed
            if (callback != null) {
                callback(hit)
                return true
            }
        }
        return false
    }

    private fun distanceToSegment(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        val l2 = dx * dx + dy * dy
        if (l2 == 0.0) return hypot(px - x1, py - y1)
        val t = ((px - x1) * dx + (py - y1) * dy) / l2
        val clampedT = t.coerceIn(0.0, 1.0)
        val projX = x1 + clampedT * dx
        val projY = y1 + clampedT * dy
        return hypot(px - projX, py - projY)
    }
}
