package com.saile.shub.gis

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.view.MotionEvent
import com.saile.shub.gis.domain.GisElementType
import com.saile.shub.gis.domain.GisRenderElement
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.hypot

class GisVectorOverlay(
    private val onElementTapped: (GisRenderElement) -> Unit
) : Overlay() {

    private val linePaintMT = Paint().apply {
        color = Color.parseColor("#E53935") // Red for Medium Voltage
        strokeWidth = 5f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val linePaintBT = Paint().apply {
        color = Color.parseColor("#1E88E5") // Blue for Low Voltage
        strokeWidth = 3.5f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val linePaintAT = Paint().apply {
        color = Color.parseColor("#FB8C00") // Orange for High Voltage
        strokeWidth = 7f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val polePaint = Paint().apply {
        color = Color.parseColor("#43A047") // Green for Poles
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val trafoPaint = Paint().apply {
        color = Color.parseColor("#8E24AA") // Purple for Transformers
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val switchPaint = Paint().apply {
        color = Color.parseColor("#FF6F00") // Deep Orange for Switches
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val subPaint = Paint().apply {
        color = Color.parseColor("#D32F2F") // Crimson for Substations
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val towerPaint = Paint().apply {
        color = Color.parseColor("#00838F") // Cyan / Steel for Towers
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val regulatorPaint = Paint().apply {
        color = Color.parseColor("#00897B") // Teal for Regulators
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val capacitorPaint = Paint().apply {
        color = Color.parseColor("#3949AB") // Indigo for Capacitors
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val borderPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private var currentElements: List<GisRenderElement> = emptyList()
    private val p1 = Point()
    private val p2 = Point()

    fun updateElements(elements: List<GisRenderElement>) {
        currentElements = elements
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val pj = mapView.projection ?: return

        // 1. Draw lines first so points render on top
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
            }
        }

        // 2. Draw point assets (transformers, switches, poles, substations)
        for (elem in currentElements) {
            if (elem is GisRenderElement.Point) {
                pj.toPixels(GeoPoint(elem.latitude, elem.longitude), p1)
                val (paint, radius) = when (elem.type) {
                    GisElementType.SUBSTATION -> Pair(subPaint, 13f)
                    GisElementType.TOWER -> Pair(towerPaint, 9f)
                    GisElementType.TRANSFORMER -> Pair(trafoPaint, 8f)
                    GisElementType.SWITCH -> Pair(switchPaint, 7f)
                    GisElementType.REGULATOR -> Pair(regulatorPaint, 7f)
                    GisElementType.CAPACITOR -> Pair(capacitorPaint, 7f)
                    GisElementType.POLE -> Pair(polePaint, 5f)
                    else -> Pair(polePaint, 5f)
                }

                canvas.drawCircle(p1.x.toFloat(), p1.y.toFloat(), radius + 1.5f, borderPaint)
                canvas.drawCircle(p1.x.toFloat(), p1.y.toFloat(), radius, paint)
            }
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val pj = mapView.projection ?: return false
        val tapX = e.x
        val tapY = e.y
        val touchThreshold = 35.0 // pixels

        // Find nearest point first
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
            onElementTapped(closestPoint)
            return true
        }

        // Find nearest line
        for (elem in currentElements) {
            if (elem is GisRenderElement.Line) {
                val pts = elem.points
                for (i in 0 until pts.size - 1) {
                    pj.toPixels(GeoPoint(pts[i].first, pts[i].second), p1)
                    pj.toPixels(GeoPoint(pts[i + 1].first, pts[i + 1].second), p2)
                    val dist = distanceToSegment(tapX.toDouble(), tapY.toDouble(), p1.x.toDouble(), p1.y.toDouble(), p2.x.toDouble(), p2.y.toDouble())
                    if (dist < touchThreshold) {
                        onElementTapped(elem)
                        return true
                    }
                }
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

