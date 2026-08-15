package com.dustvalve.next.android.data.network

import android.content.Context
import com.dustvalve.next.android.util.NetworkUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opportunistic artist/playlist revalidation is allowed only when the
 * active network is not metered. A missing ConnectivityManager is treated
 * as metered so we never surprise-fetch on an unknown radio.
 */
@Singleton
class UnmeteredRefreshGate @Inject constructor(@ApplicationContext private val context: Context) : OpportunisticRefreshGate {
    override fun allowRefresh(): Boolean = !NetworkUtils.isMeteredConnection(context)
}
