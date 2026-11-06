package com.saile.shub.photo.location

import android.content.Context
import com.saile.shub.core.location.CoreNetworkUtils

/**
 * Delegates to [CoreNetworkUtils] for backward compatibility within sFoto.
 */
object NetworkUtils {
    fun isNetworkAvailable(context: Context): Boolean = CoreNetworkUtils.isNetworkAvailable(context)
}
