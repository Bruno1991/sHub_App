package com.saile.shub.gis.domain

enum class GisElementType {
    POLE,
    TRANSFORMER,
    SWITCH,
    SUBSTATION,
    TOWER,
    REGULATOR,
    CAPACITOR,
    SPAN_MT,
    SPAN_BT,
    SPAN_AT,
    OTHER
}

sealed class GisRenderElement {
    abstract val fid: Long
    abstract val layerName: String
    abstract val title: String
    abstract val type: GisElementType
    abstract val metadataJson: String

    data class Point(
        override val fid: Long,
        override val layerName: String,
        override val title: String,
        override val type: GisElementType,
        override val metadataJson: String,
        val latitude: Double,
        val longitude: Double
    ) : GisRenderElement()

    data class Line(
        override val fid: Long,
        override val layerName: String,
        override val title: String,
        override val type: GisElementType,
        override val metadataJson: String,
        val points: List<Pair<Double, Double>> // (lat, lon)
    ) : GisRenderElement()
}

