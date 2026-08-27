package com.saile.shub.core.geo

/** Geographic position in decimal degrees, referenced to WGS84. */
data class GeoCoordinate(
    val latitude: Double,
    val longitude: Double,
)

enum class Hemisphere { NORTH, SOUTH }

/** UTM position in metres, referenced to WGS84. */
data class UtmCoordinate(
    val zone: Int,
    val hemisphere: Hemisphere,
    val easting: Double,
    val northing: Double,
)
