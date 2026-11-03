package com.saile.shub.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.location.Location
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.caverock.androidsvg.SVG
import com.saile.shub.R
import com.saile.shub.core.geo.GeoCoordinate
import com.saile.shub.core.geo.UtmConverter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object OverlayRenderer {

    private data class LogoCacheKey(val assetName: String, val width: Int, val height: Int)
    private data class LogoSource(val svg: SVG, val width: Float, val height: Float)

    private object LogoBitmapCache : LruCache<LogoCacheKey, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: LogoCacheKey, value: Bitmap): Int = value.allocationByteCount
    }

    private val logoSourceCache = LruCache<String, LogoSource>(24)

    private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // Reusable ThreadLocal Paint avoiding reallocation on every frame
    private val threadLocalTextPaint = object : ThreadLocal<Paint>() {
        override fun initialValue(): Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = Typeface.DEFAULT
            isLinearText = true
            setShadowLayer(3f, 1.5f, 1.5f, Color.BLACK)
        }
    }

    // ThreadLocal DateFormatter avoiding reallocation of SimpleDateFormat on every frame
    private val threadLocalDateFormatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat =
            SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR"))
    }

    // Cache of Drawables to avoid repeated ContextCompat.getDrawable queries at 60 FPS
    private val drawableStateCache = ConcurrentHashMap<Int, Drawable.ConstantState>()
    private val threadLocalDrawableCache = object : ThreadLocal<HashMap<Int, Drawable>>() {
        override fun initialValue(): HashMap<Int, Drawable> = HashMap()
    }

    private fun getCachedDrawable(context: Context, resId: Int): Drawable? {
        val localMap = threadLocalDrawableCache.get() ?: return null
        localMap[resId]?.let { return it }

        val state = drawableStateCache[resId]
        if (state != null) {
            val newDrawable = state.newDrawable().mutate()
            localMap[resId] = newDrawable
            return newDrawable
        }

        val loaded = ContextCompat.getDrawable(context.applicationContext, resId)?.mutate()
        if (loaded != null) {
            loaded.constantState?.let { drawableStateCache[resId] = it }
            localMap[resId] = loaded
        }
        return loaded
    }

    // Date text memoization (invalidates once per second)
    private class DateCacheEntry(
        val secondEpoch: Long,
        val dateText: String,
    )

    @Volatile
    private var lastDateCache: DateCacheEntry? = null

    internal fun getCachedDateText(capturedAt: Date): String {
        val sec = capturedAt.time / 1000
        val current = lastDateCache
        if (current != null && current.secondEpoch == sec) {
            return current.dateText
        }
        val formatter = threadLocalDateFormatter.get()
            ?: SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR"))
        val text = formatter.format(capturedAt)
        lastDateCache = DateCacheEntry(sec, text)
        return text
    }

    // Location (UTM / Geo) text memoization
    private class LocationCacheEntry(
        val latitude: Double,
        val longitude: Double,
        val utmText: String,
        val geoText: String,
    )

    @Volatile
    private var lastLocationCache: LocationCacheEntry? = null

    internal fun getCachedLocationTexts(location: Location?): Pair<String, String> {
        if (location == null) {
            return "indisponível" to "indisponível"
        }
        val lat = location.latitude
        val lon = location.longitude
        val current = lastLocationCache
        if (current != null && current.latitude == lat && current.longitude == lon) {
            return current.utmText to current.geoText
        }

        val geo = GeoCoordinate(lat, lon)
        val utm = UtmConverter.geoToUtm(geo)
        val bands = "CDEFGHJKLMNPQRSTUVWX"
        val bandIndex = ((lat + 80) / 8).toInt().coerceIn(0, bands.length - 1)
        val band = bands[bandIndex]
        val utmText = "${utm.zone}$band %.0f %.0f".format(Locale.US, utm.easting, utm.northing)
        val geoText = "%.7f, %.7f".format(Locale.US, lat, lon)
        lastLocationCache = LocationCacheEntry(lat, lon, utmText, geoText)
        return utmText to geoText
    }

    // Cached pre-measured row layout
    private class CachedRow(
        val resIds: List<Int>,
        val lines: List<String>,
        val lineWidths: FloatArray,
        val maxLineWidth: Float,
    )

    // Complete snapshot of layout calculations
    private class CachedOverlayLayout(
        val logicalWidth: Float,
        val logicalHeight: Float,
        val horizontalEdgeInsetFraction: Float,
        val verticalEdgeInsetFraction: Float,
        val position: OverlayPosition,
        val colorArgb: Int,
        val showName: Boolean,
        val showUtm: Boolean,
        val showGeo: Boolean,
        val showDateTime: Boolean,
        val showAddress: Boolean,
        val userName: String,
        val utmText: String,
        val geoText: String,
        val dateText: String,
        val resolvedAddress: String?,
        val selectedLogo: String?,
        // Pre-computed metrics & coordinates:
        val padding: Float,
        val baseSize: Float,
        val iconSize: Int,
        val spacing: Float,
        val maxWidth: Float,
        val totalHeight: Float,
        val boxWidth: Float,
        val boxHeight: Float,
        val left: Float,
        val top: Float,
        val right: Float,
        val isRightAligned: Boolean,
        val isCenterAligned: Boolean,
        val rows: List<CachedRow>,
        // Pre-computed logo:
        val logoBitmap: Bitmap?,
        val logoX: Float,
        val logoY: Float,
    )

    @Volatile
    private var cachedLayout: CachedOverlayLayout? = null

    internal fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return emptyList()
        // Fast path: line fits entirely within maxWidth without word splitting
        if (paint.measureText(text) <= maxWidth) {
            return listOf(text)
        }
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                    currentLine = word
                } else {
                    lines.add(word)
                    currentLine = ""
                }
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }
        return lines
    }

    private fun getOrComputeLayout(
        context: Context,
        logicalWidth: Float,
        logicalHeight: Float,
        horizontalEdgeInsetFraction: Float,
        verticalEdgeInsetFraction: Float,
        overlay: PhotoOverlay,
        colorArgb: Int,
        utmText: String,
        geoText: String,
        dateText: String,
    ): CachedOverlayLayout {
        val current = cachedLayout
        if (current != null &&
            current.logicalWidth == logicalWidth &&
            current.logicalHeight == logicalHeight &&
            current.horizontalEdgeInsetFraction == horizontalEdgeInsetFraction &&
            current.verticalEdgeInsetFraction == verticalEdgeInsetFraction &&
            current.position == overlay.position &&
            current.colorArgb == colorArgb &&
            current.showName == overlay.showName &&
            current.showUtm == overlay.showUtm &&
            current.showGeo == overlay.showGeo &&
            current.showDateTime == overlay.showDateTime &&
            current.showAddress == overlay.showAddress &&
            current.userName == overlay.userName &&
            current.utmText == utmText &&
            current.geoText == geoText &&
            current.dateText == dateText &&
            current.resolvedAddress == overlay.resolvedAddress &&
            current.selectedLogo == overlay.selectedLogo
        ) {
            return current
        }

        return synchronized(this) {
            val locked = cachedLayout
            if (locked != null &&
                locked.logicalWidth == logicalWidth &&
                locked.logicalHeight == logicalHeight &&
                locked.horizontalEdgeInsetFraction == horizontalEdgeInsetFraction &&
                locked.verticalEdgeInsetFraction == verticalEdgeInsetFraction &&
                locked.position == overlay.position &&
                locked.colorArgb == colorArgb &&
                locked.showName == overlay.showName &&
                locked.showUtm == overlay.showUtm &&
                locked.showGeo == overlay.showGeo &&
                locked.showDateTime == overlay.showDateTime &&
                locked.showAddress == overlay.showAddress &&
                locked.userName == overlay.userName &&
                locked.utmText == utmText &&
                locked.geoText == geoText &&
                locked.dateText == dateText &&
                locked.resolvedAddress == overlay.resolvedAddress &&
                locked.selectedLogo == overlay.selectedLogo
            ) {
                return locked
            }

            val metrics = overlayLayoutMetrics(
                width = logicalWidth,
                height = logicalHeight,
                horizontalEdgeInsetFraction = horizontalEdgeInsetFraction,
                verticalEdgeInsetFraction = verticalEdgeInsetFraction,
            )
            val baseSize = metrics.baseTextSize
            val padding = metrics.padding
            val horizontalEdgeInset = metrics.horizontalEdgeInset
            val verticalEdgeInset = metrics.verticalEdgeInset
            val iconSize = metrics.iconSize
            val spacing = metrics.spacing
            val maxTextWidth = metrics.maxTextWidth

            val textPaint = threadLocalTextPaint.get() ?: Paint().apply {
                typeface = Typeface.DEFAULT
                isLinearText = true
                setShadowLayer(3f, 1.5f, 1.5f, Color.BLACK)
            }
            textPaint.color = colorArgb
            textPaint.textSize = baseSize

            val wrappedUtm = if (overlay.showUtm) wrapText(utmText, textPaint, maxTextWidth) else emptyList()
            val wrappedGeo = if (overlay.showGeo) wrapText(geoText, textPaint, maxTextWidth) else emptyList()
            val wrappedDate = if (overlay.showDateTime) wrapText(dateText, textPaint, maxTextWidth) else emptyList()
            val wrappedAddress = if (overlay.showAddress && !overlay.resolvedAddress.isNullOrEmpty()) {
                wrapText(overlay.resolvedAddress, textPaint, maxTextWidth)
            } else emptyList()
            val wrappedName = if (overlay.showName) wrapText(overlay.userName, textPaint, maxTextWidth) else emptyList()

            fun buildRow(resIds: List<Int>, lines: List<String>): CachedRow? {
                if (lines.isEmpty()) return null
                val lineWidths = FloatArray(lines.size) { i -> textPaint.measureText(lines[i]) }
                val maxLineWidth = if (lineWidths.isNotEmpty()) lineWidths.maxOrNull() ?: 0f else 0f
                return CachedRow(resIds, lines, lineWidths, maxLineWidth)
            }

            val rows = mutableListOf<CachedRow>()
            if (overlay.showName) buildRow(listOf(R.drawable.ic_badge), wrappedName)?.let { rows.add(it) }
            if (overlay.showUtm) buildRow(listOf(R.drawable.ic_globe), wrappedUtm)?.let { rows.add(it) }
            if (overlay.showGeo) buildRow(listOf(R.drawable.ic_location_on), wrappedGeo)?.let { rows.add(it) }
            if (overlay.showDateTime) buildRow(listOf(R.drawable.ic_calendar, R.drawable.ic_clock), wrappedDate)?.let { rows.add(it) }
            if (overlay.showAddress) buildRow(listOf(R.drawable.ic_near_me), wrappedAddress)?.let { rows.add(it) }

            var maxWidth = 0f
            var totalHeight = 0f

            for (row in rows) {
                val iconCount = row.resIds.size
                val rowWidth = row.maxLineWidth + (iconSize.toFloat() * iconCount) + (spacing * iconCount)
                maxWidth = maxOf(maxWidth, rowWidth)
                totalHeight += (baseSize + spacing) * row.lines.size
            }

            val boxWidth = maxWidth + padding * 2
            val boxHeight = totalHeight + padding * 2

            val left = when (overlay.position) {
                OverlayPosition.TOP_LEFT, OverlayPosition.BOTTOM_LEFT -> padding + horizontalEdgeInset
                OverlayPosition.TOP_RIGHT, OverlayPosition.BOTTOM_RIGHT -> logicalWidth - boxWidth - padding - horizontalEdgeInset
                OverlayPosition.BOTTOM_CENTER -> (logicalWidth - boxWidth) / 2f
            }
            val top = when (overlay.position) {
                OverlayPosition.TOP_LEFT, OverlayPosition.TOP_RIGHT -> padding + verticalEdgeInset
                OverlayPosition.BOTTOM_LEFT, OverlayPosition.BOTTOM_RIGHT, OverlayPosition.BOTTOM_CENTER ->
                    logicalHeight - boxHeight - padding - verticalEdgeInset
            }
            val right = left + boxWidth
            val isRightAligned = overlay.position == OverlayPosition.TOP_RIGHT || overlay.position == OverlayPosition.BOTTOM_RIGHT
            val isCenterAligned = overlay.position == OverlayPosition.BOTTOM_CENTER

            var logoBitmap: Bitmap? = null
            var logoX = 0f
            var logoY = 0f

            if (overlay.selectedLogo != null) {
                loadLogoSource(context, overlay.selectedLogo)?.let { source ->
                    val vw = source.width
                    val vh = source.height
                    val dims = calculateStandardizedLogoDimensions(vw, vh, metrics.logoHeight)
                    val targetW = dims.width
                    val targetH = dims.height

                    logoX = when (overlay.position) {
                        OverlayPosition.BOTTOM_LEFT -> logicalWidth - targetW - padding - horizontalEdgeInset
                        OverlayPosition.BOTTOM_RIGHT -> padding + horizontalEdgeInset
                        OverlayPosition.BOTTOM_CENTER -> (logicalWidth - targetW) / 2f
                        OverlayPosition.TOP_LEFT -> logicalWidth - targetW - padding - horizontalEdgeInset
                        OverlayPosition.TOP_RIGHT -> padding + horizontalEdgeInset
                    }
                    logoY = when (overlay.position) {
                        OverlayPosition.BOTTOM_LEFT -> padding + verticalEdgeInset
                        OverlayPosition.BOTTOM_RIGHT -> padding + verticalEdgeInset
                        OverlayPosition.BOTTOM_CENTER -> padding + verticalEdgeInset
                        OverlayPosition.TOP_LEFT -> logicalHeight - targetH - padding - verticalEdgeInset
                        OverlayPosition.TOP_RIGHT -> logicalHeight - targetH - padding - verticalEdgeInset
                    }

                    logoBitmap = renderLogoBitmap(
                        assetName = overlay.selectedLogo,
                        svg = source.svg,
                        targetWidth = targetW,
                        targetHeight = targetH,
                    )
                }
            }

            val computed = CachedOverlayLayout(
                logicalWidth = logicalWidth,
                logicalHeight = logicalHeight,
                horizontalEdgeInsetFraction = horizontalEdgeInsetFraction,
                verticalEdgeInsetFraction = verticalEdgeInsetFraction,
                position = overlay.position,
                colorArgb = colorArgb,
                showName = overlay.showName,
                showUtm = overlay.showUtm,
                showGeo = overlay.showGeo,
                showDateTime = overlay.showDateTime,
                showAddress = overlay.showAddress,
                userName = overlay.userName,
                utmText = utmText,
                geoText = geoText,
                dateText = dateText,
                resolvedAddress = overlay.resolvedAddress,
                selectedLogo = overlay.selectedLogo,
                padding = padding,
                baseSize = baseSize,
                iconSize = iconSize,
                spacing = spacing,
                maxWidth = maxWidth,
                totalHeight = totalHeight,
                boxWidth = boxWidth,
                boxHeight = boxHeight,
                left = left,
                top = top,
                right = right,
                isRightAligned = isRightAligned,
                isCenterAligned = isCenterAligned,
                rows = rows,
                logoBitmap = logoBitmap,
                logoX = logoX,
                logoY = logoY,
            )
            cachedLayout = computed
            computed
        }
    }

    fun draw(
        context: Context,
        canvas: Canvas,
        width: Float,
        height: Float,
        overlay: PhotoOverlay,
        rotationDegrees: Int = 0,
        horizontalEdgeInsetFraction: Float = 0f,
        verticalEdgeInsetFraction: Float = 0f,
    ) {
        var logicalWidth = width
        var logicalHeight = height

        canvas.save()
        when (rotationDegrees) {
            90 -> {
                logicalWidth = height
                logicalHeight = width
                canvas.translate(0f, height)
                canvas.rotate(270f)
            }
            180 -> {
                canvas.translate(width, height)
                canvas.rotate(180f)
            }
            270 -> {
                logicalWidth = height
                logicalHeight = width
                canvas.translate(width, 0f)
                canvas.rotate(90f)
            }
        }

        val colorArgb = overlay.color.color.toArgb()
        val (utmText, geoText) = getCachedLocationTexts(overlay.location)
        val dateText = getCachedDateText(overlay.capturedAt)

        val layout = getOrComputeLayout(
            context = context,
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
            horizontalEdgeInsetFraction = horizontalEdgeInsetFraction,
            verticalEdgeInsetFraction = verticalEdgeInsetFraction,
            overlay = overlay,
            colorArgb = colorArgb,
            utmText = utmText,
            geoText = geoText,
            dateText = dateText,
        )

        val textPaint = threadLocalTextPaint.get() ?: Paint().apply {
            typeface = Typeface.DEFAULT
            isLinearText = true
            setShadowLayer(3f, 1.5f, 1.5f, Color.BLACK)
        }
        if (textPaint.color != colorArgb) textPaint.color = colorArgb
        if (textPaint.textSize != layout.baseSize) textPaint.textSize = layout.baseSize

        if (layout.totalHeight > 0f) {
            val padding = layout.padding
            val baseSize = layout.baseSize
            val iconSize = layout.iconSize
            val spacing = layout.spacing
            val left = layout.left
            val right = layout.right
            val maxWidth = layout.maxWidth
            val isRightAligned = layout.isRightAligned
            val isCenterAligned = layout.isCenterAligned

            var currentY = layout.top + padding + baseSize

            for (row in layout.rows) {
                val resIds = row.resIds
                val lines = row.lines
                if (lines.isEmpty()) continue

                val lineWidths = row.lineWidths
                val count = resIds.size.toFloat()
                val iconSpace = (iconSize.toFloat() * count) + (spacing * count)

                if (isRightAligned) {
                    var ix = right - padding - iconSize.toFloat()
                    for (i in resIds.indices.reversed()) {
                        val resId = resIds[i]
                        getCachedDrawable(context, resId)?.let { drawable ->
                            drawable.setTint(colorArgb)
                            val iy = (currentY - iconSize * 0.85f).toInt()
                            drawable.setBounds(ix.toInt(), iy, (ix + iconSize).toInt(), iy + iconSize)
                            drawable.draw(canvas)
                        }
                        ix -= (iconSize.toFloat() + spacing)
                    }

                    for (i in lines.indices) {
                        val line = lines[i]
                        val textWidth = lineWidths[i]
                        val textX = right - padding - iconSpace - textWidth
                        canvas.drawText(line, textX, currentY, textPaint)
                        currentY += baseSize + spacing
                    }
                } else if (isCenterAligned) {
                    val rowMaxWidth = row.maxLineWidth
                    val rowWidth = rowMaxWidth + iconSpace
                    val rowStartX = left + padding + (maxWidth - rowWidth) / 2f

                    var ix = rowStartX
                    for (i in resIds.indices) {
                        val resId = resIds[i]
                        getCachedDrawable(context, resId)?.let { drawable ->
                            drawable.setTint(colorArgb)
                            val iy = (currentY - iconSize * 0.85f).toInt()
                            drawable.setBounds(ix.toInt(), iy, (ix + iconSize).toInt(), iy + iconSize)
                            drawable.draw(canvas)
                        }
                        ix -= (iconSize.toFloat() + spacing)
                    }

                    for (i in lines.indices) {
                        val line = lines[i]
                        val lineWidth = lineWidths[i]
                        val lineX = ix + (rowMaxWidth - lineWidth) / 2f
                        canvas.drawText(line, lineX, currentY, textPaint)
                        currentY += baseSize + spacing
                    }
                } else {
                    var ix = left + padding
                    for (i in resIds.indices) {
                        val resId = resIds[i]
                        getCachedDrawable(context, resId)?.let { drawable ->
                            drawable.setTint(colorArgb)
                            val iy = (currentY - iconSize * 0.85f).toInt()
                            drawable.setBounds(ix.toInt(), iy, (ix + iconSize).toInt(), iy + iconSize)
                            drawable.draw(canvas)
                        }
                        ix -= (iconSize.toFloat() + spacing)
                    }

                    val textX = left + padding + iconSpace
                    for (i in lines.indices) {
                        val line = lines[i]
                        canvas.drawText(line, textX, currentY, textPaint)
                        currentY += baseSize + spacing
                    }
                }
            }
        }

        // Draw SVG Logo on the opposite corner
        if (layout.logoBitmap != null) {
            canvas.drawBitmap(layout.logoBitmap, layout.logoX, layout.logoY, logoPaint)
        }

        canvas.restore()
    }

    private fun loadLogoSource(context: Context, assetName: String): LogoSource? {
        logoSourceCache.get(assetName)?.let { return it }
        return runCatching {
            SVG.getFromAsset(context.assets, assetName).let { svg ->
                val viewBox = svg.documentViewBox
                val width = viewBox?.width()?.takeIf { it > 0f }
                    ?: svg.documentWidth.takeIf { it > 0f }
                    ?: 100f
                val height = viewBox?.height()?.takeIf { it > 0f }
                    ?: svg.documentHeight.takeIf { it > 0f }
                    ?: 100f
                LogoSource(svg, width, height).also { logoSourceCache.put(assetName, it) }
            }
        }.onFailure { error ->
            Log.w("sFotoOverlay", "Não foi possível abrir o logo $assetName", error)
        }.getOrNull()
    }

    private fun renderLogoBitmap(
        assetName: String,
        svg: SVG,
        targetWidth: Float,
        targetHeight: Float,
    ): Bitmap? {
        val width = targetWidth.toInt().coerceAtLeast(1)
        val height = targetHeight.toInt().coerceAtLeast(1)
        val key = LogoCacheKey(assetName, width, height)
        LogoBitmapCache.get(key)?.let { return it }

        return runCatching {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                synchronized(svg) {
                    val originalWidth = svg.documentWidth
                    val originalHeight = svg.documentHeight
                    try {
                        svg.documentWidth = targetWidth
                        svg.documentHeight = targetHeight
                        svg.renderToCanvas(Canvas(bitmap))
                    } finally {
                        svg.documentWidth = originalWidth
                        svg.documentHeight = originalHeight
                    }
                }
                LogoBitmapCache.put(key, bitmap)
            }
        }.onFailure { error ->
            Log.w("sFotoOverlay", "Não foi possível renderizar o logo $assetName", error)
        }.getOrNull()
    }
}
