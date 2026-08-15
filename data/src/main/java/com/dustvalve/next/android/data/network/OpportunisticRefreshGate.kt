package com.dustvalve.next.android.data.network

/**
 * Decides whether a cache-first catalog can be revalidated in the
 * background. Artist discographies and playlists may grow; everything
 * else is download-once. Production ([UnmeteredRefreshGate]) allows
 * refresh only on unmetered networks so cellular does not silently
 * re-fetch content the user already has.
 */
fun interface OpportunisticRefreshGate {
    fun allowRefresh(): Boolean

    companion object {
        /** Tests and cache-miss paths that must always hit the network. */
        val ALWAYS: OpportunisticRefreshGate = OpportunisticRefreshGate { true }

        /** Tests that assert metered / save-data behaviour. */
        val NEVER: OpportunisticRefreshGate = OpportunisticRefreshGate { false }
    }
}
