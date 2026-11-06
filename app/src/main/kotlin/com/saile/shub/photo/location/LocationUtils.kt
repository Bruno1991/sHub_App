package com.saile.shub.photo.location

import android.content.Context
import android.location.Location
import com.saile.shub.core.location.CoreLocationUtils

/**
 * Delegates to [CoreLocationUtils] for backward compatibility within sFoto.
 */
object LocationUtils {
    fun lastKnownLocation(context: Context): Location? = CoreLocationUtils.lastKnownLocation(context)
    fun getStateAcronym(state: String?): String? = CoreLocationUtils.getStateAcronym(state)
    fun isBetterLocation(location: Location, currentBestLocation: Location?): Boolean =
        CoreLocationUtils.isBetterLocation(location, currentBestLocation)
}
