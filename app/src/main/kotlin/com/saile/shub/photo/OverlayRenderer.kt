package com.saile.shub.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.caverock.androidsvg.SVG
import com.saile.shub.R
import com.saile.shub.core.geo.GeoCoordinate
import com.saile.shub.core.geo.UtmConverter
import java.text.SimpleDateFormat
import java.util.Locale

object OverlayRenderer {

    private data class LogoCacheKey(val assetName: String, val width: Int, val height: Int)
    private data class LogoSource(val svg: SVG, val width: Float, val height: Float)

    private object LogoBitmapCache : LruCache<LogoCacheKey, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: LogoCacheKey, value: Bitmap): Int = value.allocationByteCount
    }

    private val logoSourceCache = LruCache<String, LogoSource>(24)

    private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

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

        val layout = overlayLayoutMetrics(
            width = logicalWidth,
            height = logicalHeight,
            horizontalEdgeInsetFraction = horizontalEdgeInsetFraction,
            verticalEdgeInsetFraction = verticalEdgeInsetFraction,
        )
        val minDim = layout.minDimension
        val baseSize = layout.baseTextSize
        val padding = layout.padding
        val horizontalEdgeInset = layout.horizontalEdgeInset
        val verticalEdgeInset = layout.verticalEdgeInset
        val iconSize = layout.iconSize
        val spacing = layout.spacing

        val location = overlay.location
        val geo = location?.let { GeoCoordinate(it.latitude, it.longitude) }
        val utm = geo?.let(UtmConverter::geoToUtm)

        val utmText = if (location != null && utm != null) {
            val bands = "CDEFGHJKLMNPQRSTUVWX"
            val bandIndex = ((location.latitude + 80) / 8).toInt().coerceIn(0, bands.length - 1)
            val band = bands[bandIndex]
            "${utm.zone}$band %.0f %.0f".format(Locale.US, utm.easting, utm.northing)
        } else {
            "indisponível"
        }

        val geoText = location?.let { "%.7f, %.7f".format(Locale.US, it.latitude, it.longitude) } ?: "indisponível"
        val dateText = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR")).format(overlay.capturedAt)

        val colorArgb = overlay.color.color.toArgb()
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = colorArgb
            textSize = baseSize
            typeface = Typeface.DEFAULT
            isLinearText = true
            setShadowLayer(3f, 1.5f, 1.5f, Color.BLACK)
        }

        val maxTextWidth = layout.maxTextWidth

        fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
            if (text.isEmpty()) return emptyList()
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

        val wrappedUtm = wrapText(utmText, textPaint, maxTextWidth)
        val wrappedGeo = wrapText(geoText, textPaint, maxTextWidth)
        val wrappedDate = wrapText(dateText, textPaint, maxTextWidth)
        val wrappedAddress = if (!overlay.resolvedAddress.isNullOrEmpty()) wrapText(overlay.resolvedAddress!!, textPaint, maxTextWidth) else emptyList()
        val wrappedName = wrapText(overlay.userName, textPaint, maxTextWidth)

        var maxWidth = 0f
        var totalHeight = 0f

        fun measureRow(lines: List<String>, iconCount: Int) {
            if (lines.isEmpty()) return
            val maxLineWidth = lines.maxOfOrNull { textPaint.measureText(it) } ?: 0f
            val rowWidth = maxLineWidth + (iconSize.toFloat() * iconCount) + (spacing * iconCount)
            maxWidth = maxOf(maxWidth, rowWidth)
            totalHeight += (baseSize + spacing) * lines.size
        }

        if (overlay.showUtm) measureRow(wrappedUtm, 1)
        if (overlay.showGeo) measureRow(wrappedGeo, 1)
        if (overlay.showDateTime) measureRow(wrappedDate, 2)
        if (overlay.showAddress) measureRow(wrappedAddress, 1)
        if (overlay.showName) measureRow(wrappedName, 1)

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
        var currentY = top + padding + baseSize

        fun drawRow(resIds: List<Int>, lines: List<String>, paint: Paint) {
            if (lines.isEmpty()) return

            val count = resIds.size.toFloat()
            val iconSpace = (iconSize.toFloat() * count) + (spacing * count)

            if (isRightAligned) {
                var ix = right - padding - iconSize.toFloat()
                for (resId in resIds.reversed()) {
                    ContextCompat.getDrawable(context, resId)?.let { drawable ->
                        drawable.setTint(colorArgb)
                        val iy = (currentY - iconSize * 0.85f).toInt()
                        drawable.setBounds(ix.toInt(), iy, (ix + iconSize).toInt(), iy + iconSize)
                        drawable.draw(canvas)
                    }
                    ix -= (iconSize.toFloat() + spacing)
                }

                for (line in lines) {
                    val textWidth = paint.measureText(line)
                    val textX = right - padding - iconSpace - textWidth
                    canvas.drawText(line, textX, currentY, paint)
                    currentY += baseSize + spacing
                }
            } else if (isCenterAligned) {
                val rowMaxWidth = lines.maxOfOrNull { paint.measureText(it) } ?: 0f
                val rowWidth = rowMaxWidth + iconSpace
                val rowStartX = left + padding + (maxWidth - rowWidth) / 2f

                var ix = rowStartX
                for (resId in resIds) {
                    ContextCompat.getDrawable(context, resId)?.let { drawable ->
                        drawable.setTint(colorArgb)
                        val iy = (currentY - iconSize * 0.85f).toInt()
                        drawable.setBounds(ix.toInt(), iy, (ix + iconSize).toInt(), iy + iconSize)
                        drawable.draw(canvas)
                    }
                    ix += (iconSize.toFloat() + spacing)
                }

                for (line in lines) {
                    val lineWidth = paint.measureText(line)
                    val lineX = ix + (rowMaxWidth - lineWidth) / 2f
                    canvas.drawText(line, lineX, currentY, paint)
                    currentY += baseSize + spacing
                }
            } else {
                var ix = left + padding
                for (resId in resIds) {
                    ContextCompat.getDrawable(context, resId)?.let { drawable ->
                        drawable.setTint(colorArgb)
                        val iy = (currentY - iconSize * 0.85f).toInt()
                        drawable.setBounds(ix.toInt(), iy, (ix + iconSize).toInt(), iy + iconSize)
                        drawable.draw(canvas)
                    }
                    ix += (iconSize.toFloat() + spacing)
                }

                val textX = left + padding + iconSpace
                for (line in lines) {
                    canvas.drawText(line, textX, currentY, paint)
                    currentY += baseSize + spacing
                }
            }
        }

        if (totalHeight > 0f) {
            if (overlay.showName) drawRow(listOf(R.drawable.ic_badge), wrappedName, textPaint)
            if (overlay.showUtm) drawRow(listOf(R.drawable.ic_globe), wrappedUtm, textPaint)
            if (overlay.showGeo) drawRow(listOf(R.drawable.ic_location_on), wrappedGeo, textPaint)
            if (overlay.showDateTime) drawRow(listOf(R.drawable.ic_calendar, R.drawable.ic_clock), wrappedDate, textPaint)
            if (overlay.showAddress) drawRow(listOf(R.drawable.ic_near_me), wrappedAddress, textPaint)
        }

        // Draw SVG Logo on the opposite corner
        if (overlay.selectedLogo != null) {
            loadLogoSource(context, overlay.selectedLogo)?.let { source ->
                val vw = source.width
                val vh = source.height

                val dims = calculateStandardizedLogoDimensions(vw, vh, layout.logoHeight)
                val targetW = dims.width
                val targetH = dims.height

                val logoX = when (overlay.position) {
                    OverlayPosition.BOTTOM_LEFT -> logicalWidth - targetW - padding - horizontalEdgeInset
                    OverlayPosition.BOTTOM_RIGHT -> padding + horizontalEdgeInset
                    OverlayPosition.BOTTOM_CENTER -> (logicalWidth - targetW) / 2f
                    OverlayPosition.TOP_LEFT -> logicalWidth - targetW - padding - horizontalEdgeInset
                    OverlayPosition.TOP_RIGHT -> padding + horizontalEdgeInset
                }
                
                val logoY = when (overlay.position) {
                    OverlayPosition.BOTTOM_LEFT -> padding + verticalEdgeInset
                    OverlayPosition.BOTTOM_RIGHT -> padding + verticalEdgeInset
                    OverlayPosition.BOTTOM_CENTER -> padding + verticalEdgeInset
                    OverlayPosition.TOP_LEFT -> logicalHeight - targetH - padding - verticalEdgeInset
                    OverlayPosition.TOP_RIGHT -> logicalHeight - targetH - padding - verticalEdgeInset
                }

                val logoBitmap = renderLogoBitmap(
                    assetName = overlay.selectedLogo,
                    svg = source.svg,
                    targetWidth = targetW,
                    targetHeight = targetH,
                )
                if (logoBitmap != null) {
                    canvas.drawBitmap(logoBitmap, logoX, logoY, logoPaint)
                }
            }
        }

        canvas.restore()
    }

    private fun loadLogoSource(context: Context, assetName: String): LogoSource? {
        logoSourceCache.get(assetName)?.let { return it }
        return runCatching {
            SVG.getFromAsset(context.assets, assetName).let { svg ->
                // Logos exportados por ferramentas diferentes frequentemente declaram
                // width/height quadrados, mas trazem o desenho real no viewBox.
                // O viewBox é a proporção que deve orientar o tamanho final.
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
