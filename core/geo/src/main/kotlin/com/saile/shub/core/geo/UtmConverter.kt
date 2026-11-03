package com.saile.shub.core.geo

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Pure WGS84 UTM converter. It intentionally has no Android dependency so it can be
 * verified by JVM tests and reused by camera and GIS features.
 */
object UtmConverter {
    private const val semiMajorAxis = 6_378_137.0
    private const val eccentricitySquared = 0.00669438
    private const val scaleFactor = 0.9996
    private const val falseEasting = 500_000.0
    private const val falseNorthing = 10_000_000.0
    private const val minUtmLatitude = -80.0
    private const val maxUtmLatitude = 84.0

    fun geoToUtm(coordinate: GeoCoordinate, forcedZone: Int? = null): UtmCoordinate {
        require(coordinate.latitude in minUtmLatitude..maxUtmLatitude) {
            "Latitude deve estar entre $minUtmLatitude e $maxUtmLatitude para UTM."
        }
        require(coordinate.longitude in -180.0..180.0) {
            "Longitude deve estar entre -180 e 180."
        }

        val zone = forcedZone ?: if (coordinate.longitude == 180.0) 60 else {
            floor((coordinate.longitude + 180.0) / 6.0).toInt() + 1
        }
        val longitudeOrigin = (zone - 1) * 6 - 180 + 3
        val latitudeRad = coordinate.latitude.toRadians()
        val longitudeRad = coordinate.longitude.toRadians()
        val longitudeOriginRad = longitudeOrigin.toDouble().toRadians()
        val eccentricityPrimeSquared = eccentricitySquared / (1 - eccentricitySquared)

        val n = semiMajorAxis / sqrt(1 - eccentricitySquared * sin(latitudeRad).pow(2))
        val t = tan(latitudeRad).pow(2)
        val c = eccentricityPrimeSquared * cos(latitudeRad).pow(2)
        val a = cos(latitudeRad) * (longitudeRad - longitudeOriginRad)
        val m = meridionalArc(latitudeRad)

        val easting = scaleFactor * n * (
            a + (1 - t + c) * a.pow(3) / 6 +
                (5 - 18 * t + t.pow(2) + 72 * c - 58 * eccentricityPrimeSquared) * a.pow(5) / 120
            ) + falseEasting
        var northing = scaleFactor * (
            m + n * tan(latitudeRad) * (
                a.pow(2) / 2 + (5 - t + 9 * c + 4 * c.pow(2)) * a.pow(4) / 24 +
                    (61 - 58 * t + t.pow(2) + 600 * c - 330 * eccentricityPrimeSquared) * a.pow(6) / 720
                )
            )
        val hemisphere = if (coordinate.latitude < 0.0) Hemisphere.SOUTH else Hemisphere.NORTH
        if (hemisphere == Hemisphere.SOUTH) northing += falseNorthing

        return UtmCoordinate(zone, hemisphere, easting, northing)
    }

    fun utmToGeo(coordinate: UtmCoordinate): GeoCoordinate {
        require(coordinate.zone in 1..60) { "Zona UTM deve estar entre 1 e 60." }
        require(coordinate.easting in 100_000.0..1_000_000.0) {
            "Easting deve estar entre 100000 e 1000000 metros."
        }
        require(coordinate.northing in 0.0..10_000_000.0) {
            "Northing deve estar entre 0 e 10000000 metros."
        }

        val eccentricityPrimeSquared = eccentricitySquared / (1 - eccentricitySquared)
        val e1 = (1 - sqrt(1 - eccentricitySquared)) / (1 + sqrt(1 - eccentricitySquared))
        val x = coordinate.easting - falseEasting
        var y = coordinate.northing
        if (coordinate.hemisphere == Hemisphere.SOUTH) y -= falseNorthing

        val m = y / scaleFactor
        val mu = m / (semiMajorAxis * (
            1 - eccentricitySquared / 4 - 3 * eccentricitySquared.pow(2) / 64 -
                5 * eccentricitySquared.pow(3) / 256
            ))
        val phi1 = mu +
            (3 * e1 / 2 - 27 * e1.pow(3) / 32) * sin(2 * mu) +
            (21 * e1.pow(2) / 16 - 55 * e1.pow(4) / 32) * sin(4 * mu) +
            (151 * e1.pow(3) / 96) * sin(6 * mu)
        val n1 = semiMajorAxis / sqrt(1 - eccentricitySquared * sin(phi1).pow(2))
        val t1 = tan(phi1).pow(2)
        val c1 = eccentricityPrimeSquared * cos(phi1).pow(2)
        val r1 = semiMajorAxis * (1 - eccentricitySquared) /
            (1 - eccentricitySquared * sin(phi1).pow(2)).pow(1.5)
        val d = x / (n1 * scaleFactor)

        val latitude = phi1 - (n1 * tan(phi1) / r1) * (
            d.pow(2) / 2 - (5 + 3 * t1 + 10 * c1 - 4 * c1.pow(2) - 9 * eccentricityPrimeSquared) * d.pow(4) / 24 +
                (61 + 90 * t1 + 298 * c1 + 45 * t1.pow(2) - 252 * eccentricityPrimeSquared - 3 * c1.pow(2)) * d.pow(6) / 720
            )
        val longitudeOrigin = (coordinate.zone - 1) * 6 - 180 + 3
        val longitude = (d - (1 + 2 * t1 + c1) * d.pow(3) / 6 +
            (5 - 2 * c1 + 28 * t1 - 3 * c1.pow(2) + 8 * eccentricityPrimeSquared + 24 * t1.pow(2)) * d.pow(5) / 120
            ) / cos(phi1)

        return GeoCoordinate(latitude.toDegrees(), longitudeOrigin + longitude.toDegrees())
    }

    private fun meridionalArc(latitudeRad: Double): Double = semiMajorAxis * (
        (1 - eccentricitySquared / 4 - 3 * eccentricitySquared.pow(2) / 64 - 5 * eccentricitySquared.pow(3) / 256) * latitudeRad -
            (3 * eccentricitySquared / 8 + 3 * eccentricitySquared.pow(2) / 32 + 45 * eccentricitySquared.pow(3) / 1024) * sin(2 * latitudeRad) +
            (15 * eccentricitySquared.pow(2) / 256 + 45 * eccentricitySquared.pow(3) / 1024) * sin(4 * latitudeRad) -
            (35 * eccentricitySquared.pow(3) / 3072) * sin(6 * latitudeRad)
        )

    private fun Double.toRadians(): Double = this / 180.0 * PI
    private fun Double.toDegrees(): Double = this * 180.0 / PI
}
