package com.saile.shub.gis

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Typeface
import android.view.MotionEvent
import com.saile.shub.gis.domain.GisElementType
import com.saile.shub.gis.domain.GisRenderElement
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.hypot

class GisVectorOverlay(
    private val onElementTapped: (GisRenderElement) -> Unit,
    var onElementLongPressed: ((GisRenderElement) -> Unit)? = null
) : Overlay() {

    // Lines - Authentic QGIS / QField layer colors
    private val linePaintAT = Paint().apply {
        color = Color.parseColor("#FC07E3") // Magenta for High Voltage (Vão AT)
        strokeWidth = 5.5f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val linePaintMT = Paint().apply {
        color = Color.parseColor("#052EF9") // Electric Blue for Medium Voltage (Vão MT)
        strokeWidth = 3.8f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val linePaintBT = Paint().apply {
        color = Color.parseColor("#19F805") // Lime Green for Low Voltage (Vão BT)
        strokeWidth = 2.6f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    // Points - Authentic QGIS / QField symbology colors
    private val subPaint = Paint().apply {
        color = Color.parseColor("#00F200") // Bright Green for Substations
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val towerPaint = Paint().apply {
        color = Color.WHITE // White for Towers
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val trafoPaint = Paint().apply {
        color = Color.parseColor("#0B0503") // Dark Brown for Transformers
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val switchPaint = Paint().apply {
        color = Color.parseColor("#FB0313") // Red for Switches
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val regulatorPaint = Paint().apply {
        color = Color.parseColor("#F59207") // Orange for Regulators
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val capacitorPaint = Paint().apply {
        color = Color.parseColor("#01DDFF") // Cyan for Capacitors
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val polePaint = Paint().apply {
        color = Color.WHITE // White for Poles
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

    private var currentElements: List<GisRenderElement> = emptyList()
    private var currentZoom: Double = 9.0
    private var selectedFid: Long? = null

    private val p1 = Point()
    private val p2 = Point()

    fun updateElements(elements: List<GisRenderElement>, zoom: Double = currentZoom, selectedId: Long? = null) {
        currentElements = elements
        currentZoom = zoom
        selectedFid = selectedId
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val pj = mapView.projection ?: return

        // 1. Draw Lines First (AT, MT, BT)
        for (elem in currentElements) {
            if (elem is GisRenderElement.Line) {
                val paint = when (elem.type) {
                    GisElementType.SPAN_AT -> linePaintAT
                    GisElementType.SPAN_MT -> linePaintMT
                    GisElementType.SPAN_BT -> linePaintBT
                    else -> linePaintMT
                }

                val pts = elem.points
                for (i in 0 until pts.size - 1) {
                    val ptA = pts[i]
                    val ptB = pts[i + 1]
                    pj.toPixels(GeoPoint(ptA.first, ptA.second), p1)
                    pj.toPixels(GeoPoint(ptB.first, ptB.second), p2)
                    canvas.drawLine(p1.x.toFloat(), p1.y.toFloat(), p2.x.toFloat(), p2.y.toFloat(), paint)
                }

                // If selected line, draw glow overlay
                if (elem.fid == selectedFid) {
                    for (i in 0 until pts.size - 1) {
                        pj.toPixels(GeoPoint(pts[i].first, pts[i].second), p1)
                        pj.toPixels(GeoPoint(pts[i + 1].first, pts[i + 1].second), p2)
                        canvas.drawLine(p1.x.toFloat(), p1.y.toFloat(), p2.x.toFloat(), p2.y.toFloat(), selectionRingPaint)
                    }
                }
            }
        }

        // 2. Draw Point Assets (Substations, Towers, Transformers, Switches, Poles)
        for (elem in currentElements) {
            if (elem is GisRenderElement.Point) {
                pj.toPixels(GeoPoint(elem.latitude, elem.longitude), p1)
                val cx = p1.x.toFloat()
                val cy = p1.y.toFloat()

                val (paint, radius, isWhite) = when (elem.type) {
                    GisElementType.SUBSTATION -> Triple(subPaint, 13f, false)
                    GisElementType.TOWER -> Triple(towerPaint, 8.5f, true)
                    GisElementType.TRANSFORMER -> Triple(trafoPaint, 7.5f, false)
                    GisElementType.SWITCH -> Triple(switchPaint, 7f, false)
                    GisElementType.REGULATOR -> Triple(regulatorPaint, 7f, false)
                    GisElementType.CAPACITOR -> Triple(capacitorPaint, 7f, false)
                    GisElementType.POLE -> Triple(polePaint, 4.5f, true)
                    else -> Triple(polePaint, 4.5f, true)
                }

                // Outer border for contrast on satellite & vector maps
                val border = if (isWhite) darkBorderPaint else whiteBorderPaint
                canvas.drawCircle(cx, cy, radius + 1.5f, border)
                canvas.drawCircle(cx, cy, radius, paint)

                // Highlight ring if element is selected
                if (elem.fid == selectedFid) {
                    canvas.drawCircle(cx, cy, radius + 8f, selectionRingPaint)
                }

                // Text labels when zoomed in (zoom >= 15.0) like QField
                if (currentZoom >= 15.0 && elem.type != GisElementType.POLE) {
                    val label = elem.title
                    if (label.isNotBlank()) {
                        val textY = cy - radius - 8f
                        canvas.drawText(label, cx + 6f, textY, labelOutlinePaint)
                        canvas.drawText(label, cx + 6f, textY, labelTextPaint)
                    }
                }
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
                pj.toPixels(GeoPoint(elem.latitude, elem.longitude), p1)
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
                    pj.toPixels(GeoPoint(pts[i].first, pts[i].second), p1)
                    pj.toPixels(GeoPoint(pts[i + 1].first, pts[i + 1].second), p2)
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
