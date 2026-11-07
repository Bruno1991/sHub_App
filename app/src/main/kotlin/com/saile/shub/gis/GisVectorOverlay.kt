package com.saile.shub.gis

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import android.view.MotionEvent
import com.saile.shub.gis.domain.GisElementType
import com.saile.shub.gis.domain.GisRenderElement
import com.saile.shub.photo.data.PhotoMediaMetadata
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * GisVectorOverlay.kt
 *
 * Responsabilidade Única:
 * Motor de renderização vetorial de alta performance (120 FPS com Offscreen Bitmap Layer Caching),
 * exibindo redes elétricas (AT/MT/BT), ativos pontuais, fotos georreferenciadas do sFoto,
 * apontamentos de inspeção de campo, rastro GPS (tracklog) e Linha Guia Rural dinâmica.
 */
class GisVectorOverlay(
    private val onElementTapped: (GisRenderElement) -> Unit,
    var onElementLongPressed: ((GisRenderElement) -> Unit)? = null,
    var onPhotoTapped: ((PhotoMediaMetadata) -> Unit)? = null,
    var onInspectionTapped: ((FieldInspectionRecord) -> Unit)? = null
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

    private val polePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

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

    // Rural Guidance Line ("Fora de Estrada")
    private val guidanceOutlinePaint = Paint().apply {
        color = Color.argb(210, 0, 0, 0)
        strokeWidth = 8f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val guidanceDashPaint = Paint().apply {
        color = Color.parseColor("#FF9100") // High-visibility safety orange
        strokeWidth = 4.5f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(22f, 14f), 0f)
        isAntiAlias = true
    }

    // Tracklog Path Paint
    private val tracklogPaint = Paint().apply {
        color = Color.parseColor("#00E676")
        strokeWidth = 4.5f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    // sFoto Geotagged Photo Marker Paint
    private val photoBadgeFillPaint = Paint().apply {
        color = Color.parseColor("#FF416C")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Field Inspection Anomaly Marker Paint
    private val inspectionBadgeFillPaint = Paint().apply {
        color = Color.parseColor("#FFD600")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Authentic QField GNSS / GPS Position Marker
    private val userAccuracyFillPaint = Paint().apply {
        color = Color.argb(35, 33, 150, 243)
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
        color = Color.parseColor("#1976D2")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val userHeadingConePaint = Paint().apply {
        color = Color.argb(70, 33, 150, 243)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private var currentElements: List<GisRenderElement> = emptyList()
    private var elementsVersion: Long = 0L
    private var currentZoom: Double = 9.0
    private var selectedFid: Long? = null

    private var geotaggedPhotos: List<PhotoMediaMetadata> = emptyList()
    private var fieldInspections: List<FieldInspectionRecord> = emptyList()
    private var tracklogPoints: List<Pair<Double, Double>> = emptyList()
    private var guidanceTarget: Pair<Double, Double>? = null

    // Offscreen Bitmap Cache for 120 FPS rendering
    private var cachedBitmap: Bitmap? = null
    private var cachedCanvas: Canvas? = null
    private var cachedElementsVersion: Long = -1L
    private var cachedSelectedFid: Long? = null
    private var cachedZoom: Double = -1.0
    private var cachedNorth: Double = Double.NaN
    private var cachedSouth: Double = Double.NaN
    private var cachedEast: Double = Double.NaN
    private var cachedWest: Double = Double.NaN
    private var cachedOrientation: Float = Float.NaN

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
    private val trackPath = Path()
    private val badgeRect = RectF()

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
        guidanceOutlinePaint.strokeWidth = 7f * density
        guidanceDashPaint.strokeWidth = 4f * density
        guidanceDashPaint.pathEffect = DashPathEffect(floatArrayOf(18f * density, 12f * density), 0f)
        tracklogPaint.strokeWidth = 4f * density
        densityConfigured = true
    }

    fun updateElements(elements: List<GisRenderElement>, zoom: Double = currentZoom, selectedId: Long? = null) {
        if (currentElements !== elements) {
            currentElements = elements
            elementsVersion++
        }
        currentZoom = zoom
        selectedFid = selectedId
    }

    fun updatePhotosAndInspections(
        photos: List<PhotoMediaMetadata>,
        inspections: List<FieldInspectionRecord>
    ) {
        geotaggedPhotos = photos
        fieldInspections = inspections
    }

    fun updateTracklogAndGuidance(
        tracklog: List<Pair<Double, Double>>,
        guidanceTargetCoords: Pair<Double, Double>?
    ) {
        tracklogPoints = tracklog
        guidanceTarget = guidanceTargetCoords
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
        val w = mapView.width
        val h = mapView.height
        if (w <= 0 || h <= 0) return

        val screenW = w.toFloat()
        val screenH = h.toFloat()
        val bbox = mapView.boundingBox

        val now = System.currentTimeMillis()
        if (now - lastDrawLogTime > 2000L) {
            lastDrawLogTime = now
            Log.i("ShubGis", "Overlay drawn: ${currentElements.size} elements, zoom=$zoomDouble")
        }

        // 1. Offscreen Bitmap Cache check for static GPKG vector layers
        val isCacheValid = cachedBitmap != null &&
            cachedBitmap?.width == w &&
            cachedBitmap?.height == h &&
            cachedElementsVersion == elementsVersion &&
            cachedSelectedFid == selectedFid &&
            abs(cachedZoom - zoomDouble) < 1e-4 &&
            abs(cachedOrientation - mapView.mapOrientation) < 0.5f &&
            bbox != null &&
            abs(cachedNorth - bbox.latNorth) < 1e-7 &&
            abs(cachedSouth - bbox.latSouth) < 1e-7 &&
            abs(cachedEast - bbox.lonEast) < 1e-7 &&
            abs(cachedWest - bbox.lonWest) < 1e-7

        if (!isCacheValid) {
            if (cachedBitmap == null || cachedBitmap?.width != w || cachedBitmap?.height != h) {
                cachedBitmap?.recycle()
                cachedBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                cachedCanvas = Canvas(cachedBitmap!!)
            }
            val offCanvas = cachedCanvas!!
            offCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            drawVectorElementsToCanvas(offCanvas, pj, zoomDouble, screenW, screenH)

            cachedElementsVersion = elementsVersion
            cachedSelectedFid = selectedFid
            cachedZoom = zoomDouble
            cachedOrientation = mapView.mapOrientation
            if (bbox != null) {
                cachedNorth = bbox.latNorth
                cachedSouth = bbox.latSouth
                cachedEast = bbox.lonEast
                cachedWest = bbox.lonWest
            }
        }

        cachedBitmap?.let { bmp ->
            canvas.drawBitmap(bmp, 0f, 0f, null)
        }

        // 2. Draw Tracklog (Vehicle/Field Breadcrumb Path)
        if (tracklogPoints.size >= 2) {
            trackPath.reset()
            var started = false
            for (pt in tracklogPoints) {
                tempGeoPoint.setCoords(pt.first, pt.second)
                pj.toPixels(tempGeoPoint, p1)
                if (!started) {
                    trackPath.moveTo(p1.x.toFloat(), p1.y.toFloat())
                    started = true
                } else {
                    trackPath.lineTo(p1.x.toFloat(), p1.y.toFloat())
                }
            }
            canvas.drawPath(trackPath, tracklogPaint)
        }

        // 3. Draw Geotagged Photos from sFoto
        val badgeRadius = 11f * density
        for (photo in geotaggedPhotos) {
            val lat = photo.latitude ?: continue
            val lon = photo.longitude ?: continue
            tempGeoPoint.setCoords(lat, lon)
            pj.toPixels(tempGeoPoint, p1)
            val cx = p1.x.toFloat()
            val cy = p1.y.toFloat()
            if (cx < -40f || cx > screenW + 40f || cy < -40f || cy > screenH + 40f) continue

            canvas.drawCircle(cx, cy, badgeRadius, photoBadgeFillPaint)
            canvas.drawCircle(cx, cy, badgeRadius, whiteBorderPaint)
            // Camera lens inner ring
            canvas.drawCircle(cx, cy, 4.5f * density, whiteBorderPaint)
        }

        // 4. Draw Field Inspections / Anomalies
        for (insp in fieldInspections) {
            tempGeoPoint.setCoords(insp.latitude, insp.longitude)
            pj.toPixels(tempGeoPoint, p1)
            val cx = p1.x.toFloat()
            val cy = p1.y.toFloat()
            if (cx < -40f || cx > screenW + 40f || cy < -40f || cy > screenH + 40f) continue

            badgeRect.set(cx - badgeRadius, cy - badgeRadius, cx + badgeRadius, cy + badgeRadius)
            canvas.drawRoundRect(badgeRect, 4f * density, 4f * density, inspectionBadgeFillPaint)
            canvas.drawRoundRect(badgeRect, 4f * density, 4f * density, darkBorderPaint)
            canvas.drawText("!", cx - 3.5f * density, cy + 5f * density, labelTextPaint)
        }

        // 5. Draw Rural Guidance Line (From GPS User Position to Active Target)
        val pos = userPosition
        val gTarget = guidanceTarget
        if (pos != null && gTarget != null) {
            tempGeoPoint.setCoords(pos.latitude, pos.longitude)
            pj.toPixels(tempGeoPoint, p1)
            tempGeoPoint.setCoords(gTarget.first, gTarget.second)
            pj.toPixels(tempGeoPoint, p2)

            canvas.drawLine(p1.x.toFloat(), p1.y.toFloat(), p2.x.toFloat(), p2.y.toFloat(), guidanceOutlinePaint)
            canvas.drawLine(p1.x.toFloat(), p1.y.toFloat(), p2.x.toFloat(), p2.y.toFloat(), guidanceDashPaint)
        }

        // 6. Draw QField GNSS User Location Marker with Accuracy Ring on TOP
        if (pos != null) {
            tempGeoPoint.setCoords(pos.latitude, pos.longitude)
            pj.toPixels(tempGeoPoint, userPosPoint)

            val ux = userPosPoint.x.toFloat()
            val uy = userPosPoint.y.toFloat()

            if (ux >= -200f && ux <= screenW + 200f && uy >= -200f && uy <= screenH + 200f) {
                val acc = pos.accuracyMeters ?: 12f
                val accRadiusPixels = (pj.metersToEquatorPixels(acc)).coerceIn(16f, 500f)

                canvas.drawCircle(ux, uy, accRadiusPixels, userAccuracyFillPaint)
                canvas.drawCircle(ux, uy, accRadiusPixels, userAccuracyStrokePaint)

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

                canvas.drawCircle(ux, uy, 12f * density, userWhiteBorderPaint)
                canvas.drawCircle(ux, uy, 8.5f * density, userBlueDotPaint)
                canvas.drawCircle(ux, uy, 3.5f * density, userWhiteBorderPaint)
            }
        }
    }

    private fun drawVectorElementsToCanvas(
        canvas: Canvas,
        pj: org.osmdroid.views.Projection,
        zoomDouble: Double,
        screenW: Float,
        screenH: Float
    ) {
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

                    if ((p1.x < -100 && p2.x < -100) || (p1.x > screenW + 100 && p2.x > screenW + 100) ||
                        (p1.y < -100 && p2.y < -100) || (p1.y > screenH + 100 && p2.y > screenH + 100)
                    ) {
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

                if (cx < -60f || cx > screenW + 60f || cy < -60f || cy > screenH + 60f) {
                    continue
                }

                val symbol = GisSymbolProvider.getSymbol(elem.type, density)
                if (symbol != null) {
                    canvas.drawBitmap(symbol.bitmap, cx - symbol.halfWidth, cy - symbol.halfHeight, null)

                    if (elem.fid == selectedFid) {
                        canvas.drawCircle(cx, cy, symbol.touchRadius + 4f * density, selectionRingPaint)
                    }

                    if (zoomDouble >= 15.0 && elem.type != GisElementType.POLE) {
                        val label = elem.title
                        if (label.isNotBlank()) {
                            val textY = cy - symbol.halfHeight - 6f * density
                            canvas.drawText(label, cx + 6f * density, textY, labelOutlinePaint)
                            canvas.drawText(label, cx + 6f * density, textY, labelTextPaint)
                        }
                    }
                } else {
                    canvas.drawCircle(cx, cy, 5f * density, polePaint)
                }
            }
        }
    }

    private fun findNearestElement(tapX: Double, tapY: Double, mapView: MapView, touchThreshold: Double = 42.0): GisRenderElement? {
        val pj = mapView.projection ?: return null

        var closestPoint: GisRenderElement.Point? = null
        var closestPointDist = Double.MAX_VALUE

        for (elem in currentElements) {
            if (elem is GisRenderElement.Point) {
                tempGeoPoint.setCoords(elem.latitude, elem.longitude)
                pj.toPixels(tempGeoPoint, p1)
                val symbol = GisSymbolProvider.getSymbol(elem.type, density)
                val threshold = (symbol?.touchRadius?.toDouble() ?: touchThreshold).coerceAtLeast(36.0)
                val dist = hypot(p1.x.toDouble() - tapX, p1.y.toDouble() - tapY)
                if (dist < threshold && dist < closestPointDist) {
                    closestPoint = elem
                    closestPointDist = dist
                }
            }
        }

        if (closestPoint != null) {
            return closestPoint
        }

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
        val pj = mapView.projection ?: return false
        val tapX = e.x.toDouble()
        val tapY = e.y.toDouble()

        // 1. Check tapped Field Inspection badge first
        for (insp in fieldInspections) {
            tempGeoPoint.setCoords(insp.latitude, insp.longitude)
            pj.toPixels(tempGeoPoint, p1)
            if (hypot(p1.x.toDouble() - tapX, p1.y.toDouble() - tapY) <= 36.0) {
                onInspectionTapped?.invoke(insp)
                return true
            }
        }

        // 2. Check tapped sFoto Geotagged Photo marker
        for (photo in geotaggedPhotos) {
            val lat = photo.latitude ?: continue
            val lon = photo.longitude ?: continue
            tempGeoPoint.setCoords(lat, lon)
            pj.toPixels(tempGeoPoint, p1)
            if (hypot(p1.x.toDouble() - tapX, p1.y.toDouble() - tapY) <= 36.0) {
                onPhotoTapped?.invoke(photo)
                return true
            }
        }

        // 3. Check GPKG vector elements
        val hit = findNearestElement(tapX, tapY, mapView)
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
